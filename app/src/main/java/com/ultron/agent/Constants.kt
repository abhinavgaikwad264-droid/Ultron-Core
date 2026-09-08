package com.ultron.agent

object Constants {
    // Shizuku's destroy transaction code
    const val SHIZUKU_AIDL_TRANSACTION_DESTROY = 16777114

    // Broadcast actions
    const val PANIC_ACTION = "com.ultron.agent.PANIC"
    const val TRANSCRIPTION_ACTION = "com.ultron.agent.TRANSCRIPTION"
    const val EXTRA_TRANSCRIPTION = "transcription"

    // Notifications
    const val NOTIFICATION_CHANNEL_ID = "ultron_channel"
    const val NOTIFICATION_ID = 1001
    const val SPEECH_SERVICE_NOTIFICATION_ID = 1002
}

