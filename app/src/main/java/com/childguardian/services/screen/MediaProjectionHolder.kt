package com.childguardian.services.screen

import android.app.Activity
import android.content.Intent
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class MediaProjectionHolder @Inject constructor() {

    // The vault holds the raw ticket data needed by WebRTC's ScreenCapturerAndroid
    var permissionIntent: Intent? = null
    var resultCode: Int = Activity.RESULT_CANCELED

    // ADDED: Holds the actual running MediaProjection so we can kill it later to prevent crashes
    private var activeProjection: android.media.projection.MediaProjection? = null

    private val tag = "MediaProjectionHolder"

    fun saveTicket(code: Int, intent: Intent) {
        this.resultCode = code
        this.permissionIntent = intent
        Log.d(tag, ">>> TICKET SECURED IN THE VAULT! Ready for silent streaming. <<<")
    }


    fun hasTicket(): Boolean {
        return permissionIntent != null && resultCode == Activity.RESULT_OK
    }

    // ADDED: Call this inside your WebRTCManager right after you create the MediaProjection!
    fun setActiveProjection(projection: android.media.projection.MediaProjection) {
        activeProjection = projection
    }

    fun getMediaProjection(): android.media.projection.MediaProjection? {
        return activeProjection
    }

    // UPDATED: This wipes everything clean (Ticket + Hardware Reference)
    fun clear() {
        permissionIntent = null
        resultCode = Activity.RESULT_CANCELED
        activeProjection = null
        Timber.d("Vault manually cleared.")
    }
}