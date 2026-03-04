package com.childguardian.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import timber.log.Timber
import android.content.Intent
import android.media.projection.MediaProjection
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.VideoCapturer

@Singleton
class WebRTCManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 1. Create the global graphics engine for the whole class
    private val rootEglBase = EglBase.create()
    val eglBaseContext = rootEglBase.eglBaseContext
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var videoSource: VideoSource
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper
    private var videoTrack: VideoTrack? = null
    private var peerConnection: PeerConnection? = null

    private var screenCapturer: VideoCapturer? = null

    private val tag = "WebRTCManager"

    // Callbacks
    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onSessionDescription: ((SessionDescription) -> Unit)? = null




    // The surface that MediaProjection will write to
    val inputSurface: android.view.Surface
        get() = Surface(surfaceTextureHelper.surfaceTexture)

    init {
        initializeWebRTC()
    }

    fun startCapture(permissionData: Intent, width: Int, height: Int) {
        Timber.d(">>> WebRTCManager: Starting official ScreenCapturerAndroid! <<<")

        screenCapturer = ScreenCapturerAndroid(permissionData, object : MediaProjection.Callback() {
            override fun onStop() {
                Timber.d("System stopped capture")
            }
        })

        // <<< ADD THIS LINE: Tell the pipe to drop any ghost listeners! <<<
        surfaceTextureHelper.stopListening()

        // Connect the capturer to the WebRTC video pipe
        screenCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)

        // Start streaming at 30 FPS!
        screenCapturer?.startCapture(width, height, 30)
    }

    private fun initializeWebRTC() {

        // 1. Clean up the "Ghost Pipe" from previous runs
        if (this::surfaceTextureHelper.isInitialized) {
            surfaceTextureHelper.dispose()
        }

        // Initialize PeerConnectionFactory globals
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        // 1. Create the Video Translators using your EXISTING eglBaseContext
        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        // Create PeerConnectionFactory
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory) // Adds VP8, VP9, H264
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        // Create SurfaceTextureHelper (on a handler thread)
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        videoSource = peerConnectionFactory.createVideoSource(false) // false = not screen capture? Actually it's just a source
        surfaceTextureHelper.startListening { videoFrame ->
            videoSource.capturerObserver.onFrameCaptured(videoFrame)
        }

        // Create video track
        videoTrack = peerConnectionFactory.createVideoTrack("screen_track", videoSource)
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
        Timber.d(">>> WebRTCManager: createOffer() called!")

        if (peerConnection == null) {
            Timber.e(">>> FATAL: peerConnection is NULL! Cannot create offer.")
            return
        }

        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Timber.d(">>> WebRTCManager: onCreateSuccess! Writing Local Description...")

                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Timber.d(">>> WebRTCManager: onSetSuccess! Handing Offer to SocketManager...")
                        onSessionDescription?.invoke(sessionDescription)
                    }
                    override fun onSetFailure(s: String) { Timber.e(">>> FATAL: onSetFailure: $s") }
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {}
                    override fun onCreateFailure(s: String) {}
                }, sessionDescription)
            }
            override fun onCreateFailure(s: String) { Timber.e(">>> FATAL: onCreateFailure: $s") }
            override fun onSetSuccess() {}
            override fun onSetFailure(s: String) {}
        }, constraints)
    }

    fun setRemoteDescription(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(tag, "Remote description set")
            }
            override fun onSetFailure(s: String) { Log.e(tag, "Set remote description failed: $s") }
            override fun onCreateSuccess(sessionDescription: SessionDescription) {}
            override fun onCreateFailure(s: String) {}
        }, sessionDescription)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun release() {
        peerConnection?.close()
        surfaceTextureHelper.dispose()
    }
}