package com.childguardian.domain.command

import com.google.gson.annotations.SerializedName

data class RemoteCommand(
    @SerializedName("commandId")
    val commandId: String,
    @SerializedName("type")
    val type: String,   // will map to CommandType
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("expiresAt")
    val expiresAt: Long? = null,
    @SerializedName("payload")
    val payload: Map<String, Any>? = null
)