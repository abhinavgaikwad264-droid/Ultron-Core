package com.ultron.agent

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ShizukuManager(private val context: Context) {
    private val TAG = "ShizukuManager"
    private var binder: IUserService? = null
    private var connectionDeferred: CompletableDeferred<Boolean>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // NEW: Shizuku 13.x requires UserServiceArgs to define the daemon process
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, UserServiceImpl::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("ultron_shell")
        .debuggable(BuildConfig.DEBUG)
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = IUserService.Stub.asInterface(service)
            Log.d(TAG, "Shizuku Shell Service connected")
            connectionDeferred?.complete(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Shizuku Shell Service disconnected")
            binder = null
            connectionDeferred?.complete(false)
        }
    }

    suspend fun bind(): Boolean = suspendCoroutine { continuation ->
        if (binder != null) {
            continuation.resume(true)
            return@suspendCoroutine
        }

        connectionDeferred = CompletableDeferred()
        
        try {
            // NEW: Using bindUserService with UserServiceArgs instead of Intent
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (e: Exception) {
            continuation.resumeWithException(IllegalStateException("Shizuku bindUserService failed: ${e.message}"))
            return@suspendCoroutine
        }

        scope.launch {
            try {
                val result = connectionDeferred?.await() ?: false
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun executeCommand(command: String): String {
        val service = binder ?: throw IllegalStateException("Service not bound")
        return try {
            service.executeCommand(command)
        } catch (e: RemoteException) {
            throw RuntimeException("Remote execution failed", e)
        }
    }

    fun destroy() {
        try {
            binder?.destroy()
        } catch (e: RemoteException) {
            Log.e(TAG, "Destroy failed", e)
        } finally {
            try {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            } catch (_: Exception) { /* ignore */ }
            binder = null
        }
    }

    fun isBound(): Boolean = binder != null
}

