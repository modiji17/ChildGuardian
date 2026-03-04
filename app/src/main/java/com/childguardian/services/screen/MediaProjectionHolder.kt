package com.childguardian.services.screen

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaProjectionHolder @Inject constructor() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val tag = "MediaProjectionHolder"

    fun setMediaProjection(projection: MediaProjection) {
        this.mediaProjection = projection
        Log.d(tag, "MediaProjection set")
    }

    fun createVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        dpi: Int,
        surface: android.view.Surface
    ): VirtualDisplay? {
        if (mediaProjection == null) {
            Log.e(tag, "MediaProjection is null, cannot create virtual display")
            return null
        }
        // If a virtual display already exists, release it first (should not happen normally)
        virtualDisplay?.release()
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            name,
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null, null
        )
        Log.d(tag, "Virtual display created")
        return virtualDisplay
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        Log.d(tag, "Released MediaProjection and virtual display")
    }
}