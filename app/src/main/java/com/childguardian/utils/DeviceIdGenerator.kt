package com.childguardian.utils

import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceIdGenerator {
    fun getDeviceId(context: Context): String {
        // Try to use Android ID as base (it's unique per device but may change on factory reset)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        // Combine with a persistent UUID stored in SharedPreferences for stability
        val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            // Generate a new UUID and store it
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return "$androidId-$deviceId"   // combine for uniqueness
    }
}