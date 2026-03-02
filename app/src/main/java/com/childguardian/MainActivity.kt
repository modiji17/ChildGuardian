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

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var socketManager: SocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make window transparent or just finish quickly?
        // For now, set a simple layout (we'll create it)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            // Check if already registered
            val deviceInfo = deviceRepository.getDeviceInfoSync()
            if (deviceInfo?.registered == true) {
                // Already registered, just start services and finish
                Timber.d("Device already registered, starting services")
                socketManager.connect(deviceInfo.deviceId)
                startBackgroundServices()
                finish()
            } else {
                // Not registered, perform registration
                performRegistration()
            }
        }
    }

    private suspend fun performRegistration() {
        val deviceId = DeviceIdGenerator.getDeviceId(this)
        val request = DeviceInfoHelper.getDeviceInfo(this, deviceId)

        Timber.d("Registering device: $request")

        deviceRepository.registerDevice(request).fold(
            onSuccess = {
                Timber.d("Registration successful")
                // Connect socket after registration
                socketManager.connect(deviceId)
                startBackgroundServices()
                finish()
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
}