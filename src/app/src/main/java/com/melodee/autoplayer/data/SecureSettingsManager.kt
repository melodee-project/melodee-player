package com.melodee.autoplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure settings manager using EncryptedSharedPreferences for sensitive data
 * Falls back to regular SharedPreferences for non-sensitive settings
 */
class SecureSettingsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    // Encrypted preferences for sensitive data (tokens, credentials)
    private val securePrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("SecureSettingsManager", "Failed to create encrypted preferences, falling back to regular", e)
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Regular preferences for non-sensitive data
    private val regularPrefs: SharedPreferences = 
        context.getSharedPreferences(REGULAR_PREFS_NAME, Context.MODE_PRIVATE)

    // Sensitive data - stored in encrypted preferences
    var authToken: String
        get() {
            val token = securePrefs.getString(KEY_AUTH_TOKEN, "") ?: ""
            Log.d("SecureSettingsManager", "Getting auth token: ${if (token.isNotEmpty()) "${token.take(20)}..." else "empty"}")
            return token
        }
        set(value) {
            Log.d("SecureSettingsManager", "Setting auth token: ${if (value.isNotEmpty()) "${value.take(20)}..." else "empty"}")
            securePrefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
        }

    var refreshToken: String
        get() = securePrefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var refreshTokenExpiresAt: String
        get() = securePrefs.getString(KEY_REFRESH_TOKEN_EXPIRES_AT, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_REFRESH_TOKEN_EXPIRES_AT, value).apply()

    // Non-sensitive data - stored in regular preferences
    var serverUrl: String
        get() = regularPrefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = regularPrefs.edit().putString(KEY_SERVER_URL, value).apply()

    var userId: String
        get() = regularPrefs.getString(KEY_USER_ID, "") ?: ""
        set(value) = regularPrefs.edit().putString(KEY_USER_ID, value).apply()

    var userEmail: String
        get() = regularPrefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = regularPrefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var username: String
        get() = regularPrefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = regularPrefs.edit().putString(KEY_USER_NAME, value).apply()

    var userThumbnailUrl: String
        get() = regularPrefs.getString(KEY_USER_THUMBNAIL_URL, "") ?: ""
        set(value) = regularPrefs.edit().putString(KEY_USER_THUMBNAIL_URL, value).apply()

    var userImageUrl: String
        get() = regularPrefs.getString(KEY_USER_IMAGE_URL, "") ?: ""
        set(value) = regularPrefs.edit().putString(KEY_USER_IMAGE_URL, value).apply()

    fun isAuthenticated(): Boolean {
        val hasToken = authToken.isNotEmpty()
        val hasServerUrl = serverUrl.isNotEmpty()
        val result = hasToken && hasServerUrl
        
        Log.d("SecureSettingsManager", "=== AUTHENTICATION CHECK ===")
        Log.d("SecureSettingsManager", "Has auth token: $hasToken")
        Log.d("SecureSettingsManager", "Has server URL: $hasServerUrl")
        Log.d("SecureSettingsManager", "Is authenticated: $result")
        
        return result
    }

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
        Log.i("SecureSettingsManager", "=== SAVING AUTHENTICATION DATA ===")
        Log.i("SecureSettingsManager", "Token: ${if (token.isNotEmpty()) "${token.take(20)}..." else "empty"}")
        Log.i("SecureSettingsManager", "User ID: $userId")
        Log.i("SecureSettingsManager", "Username: $username")
        Log.i("SecureSettingsManager", "Email: $userEmail")
        Log.i("SecureSettingsManager", "Server URL: $serverUrl")
        Log.i("SecureSettingsManager", "Thumbnail URL: $thumbnailUrl")
        Log.i("SecureSettingsManager", "Image URL: $imageUrl")
        Log.i("SecureSettingsManager", "Refresh token present: ${refreshToken.isNotEmpty()}")
        
        // Save sensitive data to encrypted preferences
        val secureEditor = securePrefs.edit()
        secureEditor.putString(KEY_AUTH_TOKEN, token)
        secureEditor.putString(KEY_REFRESH_TOKEN, refreshToken)
        secureEditor.putString(KEY_REFRESH_TOKEN_EXPIRES_AT, refreshTokenExpiresAt)
        val secureSuccess = secureEditor.commit()
        
        // Save non-sensitive data to regular preferences
        val regularEditor = regularPrefs.edit()
        regularEditor.putString(KEY_USER_ID, userId)
        regularEditor.putString(KEY_USER_EMAIL, userEmail)
        regularEditor.putString(KEY_USER_NAME, username)
        regularEditor.putString(KEY_SERVER_URL, serverUrl)
        regularEditor.putString(KEY_USER_THUMBNAIL_URL, thumbnailUrl)
        regularEditor.putString(KEY_USER_IMAGE_URL, imageUrl)
        val regularSuccess = regularEditor.commit()
        
        Log.i("SecureSettingsManager", "Authentication data saved successfully: secure=$secureSuccess, regular=$regularSuccess")
        
        // Verify the save
        Log.d("SecureSettingsManager", "Verification - Token stored: ${authToken.isNotEmpty()}")
        Log.d("SecureSettingsManager", "Verification - Server URL stored: ${this.serverUrl.isNotEmpty()}")
    }

    fun logout() {
        clearUserData()
    }

    fun clearUserData() {
        securePrefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_REFRESH_TOKEN_EXPIRES_AT)
            .apply()
            
        regularPrefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_THUMBNAIL_URL)
            .remove(KEY_USER_IMAGE_URL)
            .apply()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "melodee_secure_prefs"
        private const val REGULAR_PREFS_NAME = "melodee_prefs"
        
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
