package com.melodee.autoplayer.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var authToken: String
        get() = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    // Check if user is currently authenticated
    fun isAuthenticated(): Boolean {
        return authToken.isNotEmpty() && serverUrl.isNotEmpty()
    }

    // Store complete authentication data
    fun saveAuthenticationData(
        token: String,
        userId: String,
        userEmail: String,
        username: String,
        serverUrl: String
    ) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_EMAIL, userEmail)
            .putString(KEY_USER_NAME, username)
            .putString(KEY_SERVER_URL, serverUrl)
            .apply()
    }

    // Clear all authentication data (logout)
    fun logout() {
        clearUserData()
    }

    fun clearUserData() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_AUTH_TOKEN)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "MelodeePrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AUTH_TOKEN = "auth_token"
    }
}