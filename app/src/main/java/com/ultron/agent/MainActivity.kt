package com.ultron.agent

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import moe.shizuku.Shizuku
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var startButton: Button

    private lateinit var shizukuManager: ShizukuManager
    private lateinit var uiStateManager: UIStateManager
    private lateinit var actionValidator: ActionValidator
    private lateinit var apiClient: ApiClient

    private val speechServiceIntent: Intent by lazy { Intent(this, SpeechService::class.java) }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Shizuku bridge authorized"
        } else {
            statusText.text = "Shizuku bridge denied"
        }
    }

    private val transcriptionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.TRANSCRIPTION_ACTION) {
                val text = intent.getStringExtra(Constants.EXTRA_TRANSCRIPTION) ?: return
                statusText.text = "Command: $text"
                processCommand(text)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        startButton = findViewById(R.id.start_button)

        val app = application as UltronApplication
        shizukuManager = app.shizukuManager
        uiStateManager = UIStateManager(shizukuManager)
        actionValidator = ActionValidator()

        // Astra API Client initialized here (using the build config key)
        apiClient = ApiClient(BuildConfig.API_KEY)

        requestPermissionsAndShizuku()

        startButton.setOnClickListener {
            startAgent()
        }
    }

    private fun requestPermissionsAndShizuku() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val need = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)
        }

        if (Shizuku.isPreV11() || !Shizuku.pingBinder()) {
            statusText.text = "Shizuku Daemon Offline"
        } else if (!Shizuku.checkSelfPermission()) {
            Shizuku.requestPermission(0)
        }
    }

    private fun startAgent() {
        startService(speechServiceIntent)

        lifecycleScope.launch {
            try {
                statusText.text = "Binding Shizuku IPC..."
                val bound = shizukuManager.bind()
                if (bound) {
                    statusText.text = "Ultron Online. Listening..."
                } else {
                    statusText.text = "IPC Binding failed"
                }
            } catch (e: Exception) {
                statusText.text = "Error: ${e.message}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Shizuku.addRequestPermissionResultListener(permissionListener)
        registerReceiver(transcriptionReceiver, IntentFilter(Constants.TRANSCRIPTION_ACTION))
    }

    override fun onPause() {
        super.onPause()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        unregisterReceiver(transcriptionReceiver)
    }

    private fun processCommand(text: String) {
        lifecycleScope.launch {
            try {
                // 1. Dump UI (The Eye)
                val uiTargets = uiStateManager.dumpUI()
                val uiJson = com.google.gson.Gson().toJson(uiTargets)

                // 2. Send to Astra (The Brain)
                val action = apiClient.sendRequest(text, uiJson)

                // 3. Match Target & Validate Sandbox
                val target = uiTargets.find {
                    it.text == action.targetIdentifier ||
                    it.contentDesc == action.targetIdentifier ||
                    it.resourceId == action.targetIdentifier
                }
                
                val packageName = target?.packageName ?: if (action.actionType.uppercase() == "LAUNCH") action.targetIdentifier else null
                
                if (!actionValidator.validate(action, packageName)) {
                    statusText.text = "Sandbox Blocked: $packageName"
                    return@launch
                }

                // 4. Execute Physical Command (The Muscle)
                executeAction(action, target)
            } catch (e: Exception) {
                statusText.text = "Astra API Error: ${e.message}"
            }
        }
    }

    private suspend fun executeAction(action: ApiClient.Action, target: UIStateManager.UITarget?) {
        when (action.actionType.uppercase()) {
            "CLICK" -> {
                if (target != null) {
                    shizukuManager.executeCommand("input tap ${target.centerX.roundToInt()} ${target.centerY.roundToInt()}")
                    statusText.text = "Clicked: ${action.targetIdentifier}"
                } else {
                    statusText.text = "Target not found"
                }
            }
            "TYPE" -> {
                val text = action.inputText ?: return
                shizukuManager.executeCommand("input text '$text'")
                statusText.text = "Typed: $text"
            }
            "SWIPE" -> {
                if (target != null) {
                    shizukuManager.executeCommand("input swipe ${target.centerX.roundToInt()} ${target.centerY.roundToInt()} ${target.centerX.roundToInt()} ${target.centerY.roundToInt() - 400} 300")
                    statusText.text = "Swiped: ${action.targetIdentifier}"
                }
            }
            "LAUNCH" -> {
                val pkg = target?.packageName ?: action.targetIdentifier
                shizukuManager.executeCommand("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
                statusText.text = "Launched: $pkg"
            }
            else -> {
                statusText.text = "Unknown action: ${action.actionType}"
            }
        }
    }
}

