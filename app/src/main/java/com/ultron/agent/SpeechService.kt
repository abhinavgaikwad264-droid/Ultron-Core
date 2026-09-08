package com.ultron.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class SpeechService : Service() {
    private val TAG = "SpeechService"
    private lateinit var speechRecognizer: SpeechRecognizer
    private var listener: SpeechRecognitionListener? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Constants.SPEECH_SERVICE_NOTIFICATION_ID, buildNotification())
        
        // Initialize SpeechRecognizer strictly on Main thread
        CoroutineScope(Dispatchers.Main).launch {
            initSpeechRecognizer()
            startListeningLoop()
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        listener = SpeechRecognitionListener()
        speechRecognizer.setRecognitionListener(listener)
    }

    /**
     * Auto-restart loop: forces startListening onto Dispatchers.Main to prevent thread crashes.
     */
    private fun startListeningLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    withContext(Dispatchers.Main) {
                        startListening()
                    }
                    delay(30_000) // Reset cycle every 30s
                } catch (e: Exception) {
                    Log.e(TAG, "Speech loop error", e)
                    delay(1000)
                }
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
    }

    private inner class SpeechRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            Log.w(TAG, "Recognition error code: $error")
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val transcription = matches[0]
                Log.d(TAG, "Captured transcription: $transcription")
                val intent = Intent(Constants.TRANSCRIPTION_ACTION).apply {
                    putExtra(Constants.EXTRA_TRANSCRIPTION, transcription)
                }
                sendBroadcast(intent)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun buildNotification(): Notification {
        val panicIntent = Intent(Constants.PANIC_ACTION)
        val panicPendingIntent = PendingIntent.getBroadcast(
            this, 0, panicIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Ultron Agent")
            .setContentText("Listening for commands...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "EMERGENCY STOP", panicPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "Ultron Agent",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ultron Voice Listening Service"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

