package com.example.mymoola.features.auth.data

import android.content.Context

object AuthSession {
    private const val PrefName = "auth_session"
    private const val KeyAccessToken = "access_token"
    private const val KeyRefreshToken = "refresh_token"
    private const val KeySessionPin = "session_pin"
    private const val KeyPendingPin = "pending_pin"

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    @Volatile
    var sessionPin: String? = null
        private set

    @Volatile
    private var pendingPin: String? = null

    fun initialize(context: Context) {
        if (initialized) return
        val safeContext = context.applicationContext
        appContext = safeContext
        val prefs = safeContext.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        accessToken = prefs.getString(KeyAccessToken, null)
        refreshToken = prefs.getString(KeyRefreshToken, null)
        sessionPin = prefs.getString(KeySessionPin, null)
        pendingPin = prefs.getString(KeyPendingPin, null)
        initialized = true
    }

    fun setTokens(newAccessToken: String, newRefreshToken: String) {
        accessToken = newAccessToken
        refreshToken = newRefreshToken
        persist()
    }

    fun setPendingPin(pin: String) {
        pendingPin = pin
        persist()
    }

    fun promotePendingPin() {
        sessionPin = pendingPin
        pendingPin = null
        persist()
    }

    fun clear() {
        accessToken = null
        refreshToken = null
        sessionPin = null
        pendingPin = null
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyAccessToken, accessToken)
            .putString(KeyRefreshToken, refreshToken)
            .putString(KeySessionPin, sessionPin)
            .putString(KeyPendingPin, pendingPin)
            .apply()
    }
}
