package com.childguardian.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.childguardian.data.local.dao.DeviceDao
import com.childguardian.data.local.dao.CommandDao
import com.childguardian.data.local.entity.DeviceEntity
import com.childguardian.data.local.entity.CommandEntity

@Database(
    entities = [DeviceEntity::class, CommandEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun commandDao(): CommandDao

}