package com.childguardian.services.screen

import android.app.Activity
import android.content.Intent
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaProjectionHolder @Inject constructor() {

    // The vault holds the raw ticket data needed by WebRTC's ScreenCapturerAndroid
    var permissionIntent: Intent? = null
    var resultCode: Int = Activity.RESULT_CANCELED

    private val tag = "MediaProjectionHolder"

    fun saveTicket(code: Int, intent: Intent) {
        this.resultCode = code
        this.permissionIntent = intent
        Log.d(tag, ">>> TICKET SECURED IN THE VAULT! Ready for silent streaming. <<<")
    }

    fun hasTicket(): Boolean {
        return permissionIntent != null && resultCode == Activity.RESULT_OK
    }

    fun clearTicket() {
        permissionIntent = null
        resultCode = Activity.RESULT_CANCELED
        Log.d(tag, "Vault cleared.")
    }
}