package com.ultron.agent

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class UserServiceImpl : Service() {
    private val TAG = "UserServiceImpl"

    private val binder = object : IUserService.Stub() {
        override fun executeCommand(command: String): String {
            Log.d(TAG, "Executing Shell: $command")
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val output = reader.readText()
                val error = errorReader.readText()
                process.waitFor()
                
                if (error.isNotEmpty()) {
                    "ERROR: $error\n$output"
                } else {
                    output
                }
            } catch (e: Exception) {
                "EXCEPTION: ${e.message}"
            }
        }

        override fun destroy() {
            Log.w(TAG, "Destroy called – killing Shizuku daemon.")
            // Completely kill the privileged process when Emergency Stop is hit
            
            stopSelf()
            System.exit(0) 
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}

