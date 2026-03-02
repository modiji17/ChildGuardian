package com.childguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.childguardian.data.local.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Query("SELECT * FROM device_info WHERE id = 1")
    fun getDevice(): Flow<DeviceEntity?>   // Flow to observe changes

    @Query("SELECT * FROM device_info WHERE id = 1")
    suspend fun getDeviceSync(): DeviceEntity?  // One-time fetch
}