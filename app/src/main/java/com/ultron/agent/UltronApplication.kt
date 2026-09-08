package com.ultron.agent

import android.app.Application

class UltronApplication : Application() {
    lateinit var shizukuManager: ShizukuManager
        private set

    override fun onCreate() {
        super.onCreate()
        shizukuManager = ShizukuManager(this)
    }
}

