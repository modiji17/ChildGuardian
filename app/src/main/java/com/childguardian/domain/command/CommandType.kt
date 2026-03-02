package com.childguardian.domain.command

enum class CommandType {
    ENABLE_ALL,
    DISABLE_ALL,
    ENABLE_SCREEN,
    DISABLE_SCREEN,
    ENABLE_LOCATION,
    DISABLE_LOCATION,
    ENABLE_CAMERA,
    DISABLE_CAMERA,
    ENABLE_MIC,
    DISABLE_MIC,
    REQUEST_SCREENSHOT,
    UNKNOWN
}