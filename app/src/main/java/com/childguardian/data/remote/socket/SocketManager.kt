package com.childguardian.data.remote.socket

import android.util.Log
import com.childguardian.domain.command.CommandProcessor
import com.childguardian.domain.command.RemoteCommand
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketManager @Inject constructor(
    private val commandProcessor: CommandProcessor,
    private val gson: Gson
) {
    private var socket: Socket? = null

    companion object {
        private const val TAG = "SocketManager"
        private const val SERVER_URL = "http://10.0.2.2:3000"
    }

    fun connect(deviceId: String) {
        try {
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                query = "deviceId=$deviceId"
            }
            socket = IO.socket(SERVER_URL, options)
            socket?.connect()

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected")
                socket?.emit("register-device", mapOf("deviceId" to deviceId))
            }

            socket?.on("command") { args ->
                Log.d(TAG, "Command received: ${args.contentToString()}")
                if (args.isNotEmpty()) {
                    val data = args[0] as? Map<*, *>
                    if (data != null) {
                        try {
                            // Convert map to JSON then to RemoteCommand
                            val json = gson.toJson(data)
                            val command = gson.fromJson(json, RemoteCommand::class.java)
                            commandProcessor.processCommand(command)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse command: ${e.message}")
                        }
                    }
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket disconnected")
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket connect error: ${args.contentToString()}")
            }

        } catch (e: URISyntaxException) {
            Log.e(TAG, "Socket URI error: ${e.message}")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.close()
        socket = null
    }

    fun emit(event: String, data: Any) {
        socket?.emit(event, data)
    }

    fun on(event: String, listener: (Array<Any>) -> Unit) {
        socket?.on(event) { args -> listener(args) }
    }

    // <<< ADD THIS EXACTLY HERE <<<
    fun off(event: String) {
        socket?.off(event)
    }

}