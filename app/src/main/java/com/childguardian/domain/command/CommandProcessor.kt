package com.childguardian.domain.command

import com.childguardian.data.local.dao.CommandDao
import com.childguardian.data.local.entity.CommandEntity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import android.content.Context
import android.os.Build
import android.content.Intent
import com.childguardian.services.screen.ScreenCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class CommandProcessor @Inject constructor(
    private val commandDao: CommandDao,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) {

    fun processCommand(remoteCommand: RemoteCommand) {
        CoroutineScope(Dispatchers.IO).launch {
            // Save to local DB as pending
            val entity = CommandEntity(
                commandId = remoteCommand.commandId,
                commandType = remoteCommand.type,
                payload = gson.toJson(remoteCommand.payload),
                timestamp = remoteCommand.timestamp,
                status = "pending"
            )
            commandDao.insertCommand(entity)

            // Execute based on type
            val type = try {
                CommandType.valueOf(remoteCommand.type)
            } catch (e: IllegalArgumentException) {
                CommandType.UNKNOWN
            }

            when (type) {
                CommandType.ENABLE_ALL -> enableAll()
                CommandType.DISABLE_ALL -> disableAll()
                CommandType.REQUEST_SCREENSHOT -> requestScreenshot(remoteCommand)
                CommandType.START_STREAM -> startStream(remoteCommand) // <<< Right here!
                // ... other commands
                else -> Timber.w("Unknown command type: ${remoteCommand.type}")
            }

            // After execution, update status (if successful)
            // We'll add proper execution later
        }
    }

    private suspend fun enableAll() {
        Timber.d("Enabling all features")
        // TODO: start services
    }

    private suspend fun disableAll() {
        Timber.d("Disabling all features")
        // TODO: stop services
    }

    private suspend fun requestScreenshot(command: RemoteCommand) {
        Timber.d("Screenshot requested: ${command.commandId}")
        // TODO: trigger screenshot capture
    }
    // <<< ADDED: The actual ignition sequence for the video stream
    private suspend fun startStream(command: RemoteCommand) {
        val viewerId = command.payload?.get("viewerId") as? String

        if (viewerId != null) {
            Timber.d("START_STREAM command received for viewerId: $viewerId")

            // Just blindly send the command to the Service!
            val streamIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = "ACTION_START_STREAM"
                putExtra("VIEWER_ID", viewerId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(streamIntent)
            } else {
                context.startService(streamIntent)
            }

        } else {
            Timber.e("START_STREAM failed: payload missing viewerId")
        }
    }

}