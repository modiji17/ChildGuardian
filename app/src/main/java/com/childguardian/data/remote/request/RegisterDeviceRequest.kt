package com.childguardian.data.remote.request

data class RegisterDeviceRequest(
    val deviceId: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int
)