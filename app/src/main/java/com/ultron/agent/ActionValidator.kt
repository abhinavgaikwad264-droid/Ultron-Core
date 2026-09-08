package com.ultron.agent

import android.util.Log

class ActionValidator {
    private val TAG = "ActionValidator"

    // Hardcoded package allowlist. Ultron cannot touch apps outside this list.
    private val allowlist = setOf(
        "com.android.chrome",
        "com.whatsapp",
        "com.instagram.android",
        "com.google.android.youtube",
        "com.android.settings",
        "com.ultron.agent" 
    )

    fun validate(action: ApiClient.Action, targetPackage: String?): Boolean {
        if (targetPackage == null) {
            Log.w(TAG, "No package info – rejecting action")
            return false
        }
        val allowed = targetPackage in allowlist
        if (!allowed) {
            Log.w(TAG, "CRITICAL: Blocked AI from accessing unapproved package $targetPackage")
        } else {
            Log.d(TAG, "Package $targetPackage allowed")
        }
        return allowed
    }
}

