package com.childguardian.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_info")
data class DeviceEntity(
    @PrimaryKey
    val id: Int = 1,  // We'll only have one row for device info
    val deviceId: String,
    val registered: Boolean = false,
    val lastSync: Long = 0
)