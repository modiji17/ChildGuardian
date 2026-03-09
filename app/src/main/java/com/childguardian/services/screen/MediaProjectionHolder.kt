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

    private val tag = "MediaProjectionHolder"

    fun saveTicket(code: Int, intent: Intent) {
        this.resultCode = code
        this.permissionIntent = intent
        Log.d(tag, ">>> TICKET SECURED IN THE VAULT! Ready for silent streaming. <<<")
    }

    fun clear() {
        permissionIntent = null
        // If you saved the result code as a variable (like 'code' or 'resultCode'), set it to 0 or Activity.RESULT_CANCELED here too.
        Timber.d("Vault manually cleared.")
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