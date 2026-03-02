package com.childguardian.services.base

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.childguardian.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
abstract class StealthService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "childguardian_channel"
        private const val CHANNEL_NAME = "System Service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Return sticky so service restarts if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW   // Low importance = no sound, no popup
            ).apply {
                description = "System service maintenance"
                setSound(null, null)                 // No sound
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET   // Not shown on lock screen
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")                // Generic name
            .setContentText("Maintaining system stability")   // Generic text
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Generic system icon
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }
}