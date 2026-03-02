package com.childguardian.utils

import android.content.Context
import android.os.Build
import com.childguardian.data.remote.request.RegisterDeviceRequest

object DeviceInfoHelper {
    fun getDeviceInfo(context: Context, deviceId: String): RegisterDeviceRequest {
        return RegisterDeviceRequest(
            deviceId = deviceId,
            deviceName = getDeviceName(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT
        )
    }

    private fun getDeviceName(): String {
        // You could let user set a name later, but for now return model
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
}