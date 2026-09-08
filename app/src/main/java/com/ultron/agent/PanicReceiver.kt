package com.ultron.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PanicReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Constants.PANIC_ACTION) {
            Log.w("PanicReceiver", "Emergency stop triggered! Severing IPC bridge.")
            val app = context.applicationContext as? UltronApplication
            app?.shizukuManager?.destroy()
            context.stopService(Intent(context, SpeechService::class.java))
        }
    }
}

