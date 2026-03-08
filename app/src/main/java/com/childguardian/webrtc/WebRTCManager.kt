package com.childguardian.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rootEglBase = EglBase.create()
    val eglBaseContext: EglBase.Context = rootEglBase.eglBaseContext
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var videoSource: VideoSource
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper
    private var videoTrack: VideoTrack? = null
    private var peerConnection: PeerConnection? = null
    private var screenCapturer: VideoCapturer? = null

    private val tag = "WebRTCManager"

    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onSessionDescription: ((SessionDescription) -> Unit)? = null

    val inputSurface: Surface
        get() = Surface(surfaceTextureHelper.surfaceTexture)

    init {
        initializeWebRTC()
    }

    private fun initializeWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        // Create these ONCE for the lifetime of the Singleton app
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        videoSource = peerConnectionFactory.createVideoSource(false)
        videoTrack = peerConnectionFactory.createVideoTrack("screen_track", videoSource)
    }

    fun startCapture(permissionData: Intent, width: Int, height: Int) {
        Timber.d(">>> WebRTCManager: Starting official ScreenCapturerAndroid! <<<")

        if (screenCapturer != null) {
            try {
                screenCapturer?.stopCapture()
                screenCapturer?.dispose()
            } catch (e: Exception) {
                Timber.e("Error destroying old capturer: ${e.message}")
            }
            screenCapturer = null
        }

        screenCapturer = ScreenCapturerAndroid(permissionData, object : MediaProjection.Callback() {
            override fun onStop() {
                Timber.d("System stopped capture")
            }
        })

        try {
            screenCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            screenCapturer?.startCapture(width, height, 30)
        } catch (e: Exception) {
            Timber.e("FATAL: Failed to start capture: ${e.message}")
        }
    }

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>, observer: PeerConnection.Observer) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    }

    fun addVideoTrack() {
        videoTrack?.let {
            peerConnection?.addTrack(it, listOf("stream_id"))
        }
    }

    fun createOffer() {
        if (peerConnection == null) return
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        onSessionDescription?.invoke(sessionDescription)
                    }
                    override fun onSetFailure(s: String) {}
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {}
                    override fun onCreateFailure(s: String) {}
                }, sessionDescription)
            }
            override fun onCreateFailure(s: String) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(s: String) {}
        }, constraints)
    }

    fun setRemoteDescription(sessionDescription: SessionDescription) {
        if (peerConnection?.signalingState() == PeerConnection.SignalingState.STABLE) return

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { Log.d(tag, "Remote description set") }
            override fun onSetFailure(s: String) { Log.e(tag, "Set remote description failed: $s") }
            override fun onCreateSuccess(sessionDescription: SessionDescription) {}
            override fun onCreateFailure(s: String) {}
        }, sessionDescription)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun release() {
        try {
            Timber.d(">>> Shutting down WebRTC Engine... <<<")

            // Turn off camera and tunnel, but DO NOT kill the surfaceTextureHelper!
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null

            peerConnection?.close()
            peerConnection = null

            Timber.d(">>> WebRTC Engine successfully powered down. <<<")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing WebRTC")
        }
    }
}