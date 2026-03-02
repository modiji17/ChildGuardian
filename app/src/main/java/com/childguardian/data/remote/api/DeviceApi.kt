package com.childguardian.data.remote.api

import com.childguardian.data.remote.request.RegisterDeviceRequest
import com.childguardian.data.remote.response.RegisterDeviceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface DeviceApi {
    @POST("api/device/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<RegisterDeviceResponse>
}