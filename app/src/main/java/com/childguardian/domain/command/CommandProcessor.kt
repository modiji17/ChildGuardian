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

@Singleton
class CommandProcessor @Inject constructor(
    private val commandDao: CommandDao,
    private val gson: Gson
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
}