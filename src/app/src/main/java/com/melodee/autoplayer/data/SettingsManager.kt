package com.melodee.autoplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
        get() {
            val token = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
            Log.d("SettingsManager", "Getting auth token: ${if (token.isNotEmpty()) "${token.take(20)}..." else "empty"}")
            return token
        }
        set(value) {
            Log.d("SettingsManager", "Setting auth token: ${if (value.isNotEmpty()) "${value.take(20)}..." else "empty"}")
            prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
        }

    // Check if user is currently authenticated
    fun isAuthenticated(): Boolean {
        val hasToken = authToken.isNotEmpty()
        val hasServerUrl = serverUrl.isNotEmpty()
        val result = hasToken && hasServerUrl
        
        Log.d("SettingsManager", "=== AUTHENTICATION CHECK ===")
        Log.d("SettingsManager", "Has auth token: $hasToken")
        Log.d("SettingsManager", "Has server URL: $hasServerUrl")
        Log.d("SettingsManager", "Is authenticated: $result")
        
        return result
    }

    // Store complete authentication data
    fun saveAuthenticationData(
        token: String,
        userId: String,
        userEmail: String,
        username: String,
        serverUrl: String
    ) {
        Log.i("SettingsManager", "=== SAVING AUTHENTICATION DATA ===")
        Log.i("SettingsManager", "Token: ${if (token.isNotEmpty()) "${token.take(20)}..." else "empty"}")
        Log.i("SettingsManager", "User ID: $userId")
        Log.i("SettingsManager", "Username: $username")
        Log.i("SettingsManager", "Email: $userEmail")
        Log.i("SettingsManager", "Server URL: $serverUrl")
        
        val editor = prefs.edit()
        editor.putString(KEY_AUTH_TOKEN, token)
        editor.putString(KEY_USER_ID, userId)
        editor.putString(KEY_USER_EMAIL, userEmail)
        editor.putString(KEY_USER_NAME, username)
        editor.putString(KEY_SERVER_URL, serverUrl)
        val success = editor.commit() // Use commit() instead of apply() for synchronous save
        
        Log.i("SettingsManager", "Authentication data saved successfully: $success")
        
        // Verify the save
        Log.d("SettingsManager", "Verification - Token stored: ${authToken.isNotEmpty()}")
        Log.d("SettingsManager", "Verification - Server URL stored: ${this.serverUrl.isNotEmpty()}")
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