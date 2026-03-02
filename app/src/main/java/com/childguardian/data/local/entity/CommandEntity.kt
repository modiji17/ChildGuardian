package com.childguardian.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_commands")
data class CommandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commandId: String,
    val commandType: String,
    val payload: String,   // JSON string
    val timestamp: Long,
    val status: String     // "pending", "sent", "executed"
)