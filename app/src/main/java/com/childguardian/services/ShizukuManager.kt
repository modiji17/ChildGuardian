package com.childguardian.services

import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {

    // 1. Check if the Shizuku service is running on the phone
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    // 2. Check if our app has been granted permission to use Shizuku
    fun hasShizukuPermission(): Boolean {
        return if (isShizukuAvailable()) {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    // 3. Request permission (This will pop up a Shizuku prompt ONCE during setup)
    fun requestPermission() {
        if (isShizukuAvailable() && !hasShizukuPermission()) {
            Shizuku.requestPermission(1001)
        }
    }

    // 4. THE PAYLOAD EXECUTOR: Runs raw Linux/ADB shell commands as UID 2000
    fun runShellCommand(command: String): String {
        if (!hasShizukuPermission()) {
            Timber.e(">>> SHIZUKU: Cannot run command, permission denied or not running! <<<")
            return "ERROR: No Permission"
        }

        var output = ""
        try {
            // Android 14 / Shizuku v13+ made newProcess private.
            // We bypass this restriction using Reflection to access the hidden method directly.
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true // Pick the lock

            // Execute the command as the ADB user
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output += line + "\n"
            }

            var errLine: String?
            while (errorReader.readLine().also { errLine = it } != null) {
                Timber.e(">>> SHIZUKU ERROR: $errLine <<<")
            }

            process.waitFor()
            Timber.d(">>> SHIZUKU COMMAND EXECUTED: $command <<<")

        } catch (e: Exception) {
            Timber.e(">>> SHIZUKU CRASH: ${e.message} <<<")
        }
        return output
    }
}