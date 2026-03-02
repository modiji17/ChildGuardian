package com.childguardian.data.remote.response

data class RegisterDeviceResponse(
    val success: Boolean,
    val message: String?,
    val deviceId: String?,
    val parentId: String?
)