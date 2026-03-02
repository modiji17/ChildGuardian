package com.childguardian.data.repository

import com.childguardian.data.local.dao.DeviceDao
import com.childguardian.data.local.entity.DeviceEntity
import com.childguardian.data.remote.api.DeviceApi
import com.childguardian.data.remote.request.RegisterDeviceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val deviceApi: DeviceApi
) {
    // Observe device info
    fun getDeviceInfo(): Flow<DeviceEntity?> = deviceDao.getDevice()

    // One-time fetch
    suspend fun getDeviceInfoSync(): DeviceEntity? = deviceDao.getDeviceSync()

    // Save or update device locally
    suspend fun saveDevice(device: DeviceEntity) {
        deviceDao.insertDevice(device)
    }

    // Register with server
    suspend fun registerDevice(request: RegisterDeviceRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = deviceApi.registerDevice(request)
            if (response.isSuccessful && response.body()?.success == true) {
                // Save registration flag locally
                saveDevice(DeviceEntity(id = 1, deviceId = request.deviceId, registered = true))
                Result.success(Unit)
            } else {
                Result.failure(Exception("Registration failed: ${response.body()?.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}