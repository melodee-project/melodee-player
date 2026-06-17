@file:Suppress("DEPRECATION")

package com.melodee.autoplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences = createSecurePreferences(appContext)
    private val isUsingSecurePrefsFallback: Boolean
        get() = securePrefs === prefs

    init {
        migrateSensitiveValuesToSecurePrefs()
    }

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

    var userThumbnailUrl: String
        get() = prefs.getString(KEY_USER_THUMBNAIL_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_THUMBNAIL_URL, value).apply()

    var userImageUrl: String
        get() = prefs.getString(KEY_USER_IMAGE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_IMAGE_URL, value).apply()

    var authToken: String
        get() {
            val token = securePrefs.getString(KEY_AUTH_TOKEN, "") ?: ""
            Log.d("SettingsManager", "Getting auth token: ${if (token.isNotEmpty()) "present" else "empty"}")
            return token
        }
        set(value) {
            Log.d("SettingsManager", "Setting auth token: ${if (value.isNotEmpty()) "present" else "empty"}")
            securePrefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
            removePlaintextTokenKey(KEY_AUTH_TOKEN)
        }

    var refreshToken: String
        get() = securePrefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        set(value) {
            securePrefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()
            removePlaintextTokenKey(KEY_REFRESH_TOKEN)
        }

    var refreshTokenExpiresAt: String
        get() = securePrefs.getString(KEY_REFRESH_TOKEN_EXPIRES_AT, "") ?: ""
        set(value) {
            securePrefs.edit().putString(KEY_REFRESH_TOKEN_EXPIRES_AT, value).apply()
            removePlaintextTokenKey(KEY_REFRESH_TOKEN_EXPIRES_AT)
        }

    // Check if user is currently authenticated
    fun isAuthenticated(): Boolean {
        val hasToken = authToken.isNotEmpty() || refreshToken.isNotEmpty()
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
        serverUrl: String,
        refreshToken: String = "",
        refreshTokenExpiresAt: String = "",
        thumbnailUrl: String = "",
        imageUrl: String = ""
    ) {
        Log.i("SettingsManager", "=== SAVING AUTHENTICATION DATA ===")
        Log.i("SettingsManager", "Token present: ${token.isNotEmpty()}")
        Log.i("SettingsManager", "User ID: $userId")
        Log.i("SettingsManager", "Username: $username")
        Log.i("SettingsManager", "Email: $userEmail")
        Log.i("SettingsManager", "Server URL: $serverUrl")
        Log.i("SettingsManager", "Thumbnail URL: $thumbnailUrl")
        Log.i("SettingsManager", "Image URL: $imageUrl")
        Log.i("SettingsManager", "Refresh token present: ${refreshToken.isNotEmpty()}")
        
        val refreshTokenToStore = refreshToken.ifBlank { existingRefreshTokenFor(userId, serverUrl) }
        val refreshTokenExpiresAtToStore = refreshTokenExpiresAt.ifBlank {
            if (refreshTokenToStore.isNotBlank()) this.refreshTokenExpiresAt else ""
        }

        val secureSuccess = securePrefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_REFRESH_TOKEN, refreshTokenToStore)
            .putString(KEY_REFRESH_TOKEN_EXPIRES_AT, refreshTokenExpiresAtToStore)
            .commit()

        val regularEditor = prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_EMAIL, userEmail)
            .putString(KEY_USER_NAME, username)
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_USER_THUMBNAIL_URL, thumbnailUrl)
            .putString(KEY_USER_IMAGE_URL, imageUrl)

        if (!isUsingSecurePrefsFallback) {
            regularEditor
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_REFRESH_TOKEN_EXPIRES_AT)
        }

        val success = regularEditor.commit() // Use commit() instead of apply() for synchronous save
        
        Log.i("SettingsManager", "Authentication data saved successfully: secure=$secureSuccess, regular=$success")
        
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
            .remove(KEY_USER_THUMBNAIL_URL)
            .remove(KEY_USER_IMAGE_URL)
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_REFRESH_TOKEN_EXPIRES_AT)
            .apply()
        securePrefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_REFRESH_TOKEN_EXPIRES_AT)
            .apply()
    }

    private fun createSecurePreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SettingsManager", "Failed to create encrypted preferences; using regular preferences fallback", e)
            prefs
        }
    }

    private fun migrateSensitiveValuesToSecurePrefs() {
        val oldAuthToken = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        val oldRefreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        val oldRefreshExpiresAt = prefs.getString(KEY_REFRESH_TOKEN_EXPIRES_AT, "") ?: ""
        if (oldAuthToken.isBlank() && oldRefreshToken.isBlank() && oldRefreshExpiresAt.isBlank()) return

        securePrefs.edit()
            .putString(KEY_AUTH_TOKEN, oldAuthToken)
            .putString(KEY_REFRESH_TOKEN, oldRefreshToken)
            .putString(KEY_REFRESH_TOKEN_EXPIRES_AT, oldRefreshExpiresAt)
            .apply()
        if (securePrefs !== prefs) {
            prefs.edit()
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_REFRESH_TOKEN_EXPIRES_AT)
                .apply()
        }
        Log.i("SettingsManager", "Migrated authentication tokens to secure preferences")
    }

    private fun removePlaintextTokenKey(key: String) {
        if (!isUsingSecurePrefsFallback) {
            prefs.edit().remove(key).apply()
        }
    }

    private fun existingRefreshTokenFor(userId: String, serverUrl: String): String {
        val sameUser = this.userId == userId && this.serverUrl == serverUrl
        return if (sameUser) refreshToken else ""
    }

    companion object {
        private const val PREFS_NAME = "MelodeePrefs"
        private const val SECURE_PREFS_NAME = "MelodeeSecurePrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_THUMBNAIL_URL = "user_thumbnail_url"
        private const val KEY_USER_IMAGE_URL = "user_image_url"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_REFRESH_TOKEN_EXPIRES_AT = "refresh_token_expires_at"
    }
}
