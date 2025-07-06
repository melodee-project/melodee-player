package com.melodee.autoplayer.util

import android.content.Context
import android.util.Log
import com.melodee.autoplayer.MelodeeApplication
import com.melodee.autoplayer.data.AuthenticationManager
import com.melodee.autoplayer.data.api.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Helper class demonstrating how to handle authentication in activities/fragments
 */
class AuthenticationHelper(private val context: Context) {
    
    private val authenticationManager: AuthenticationManager by lazy {
        (context.applicationContext as MelodeeApplication).authenticationManager
    }
    
    /**
     * Example login function
     */
    fun login(
        serverUrl: String,
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d("AuthenticationHelper", "Attempting login for: $email")
                
                // First set the server URL
                NetworkModule.setBaseUrl(serverUrl)
                
                // Attempt authentication
                val musicApi = NetworkModule.getMusicApi()
                val credentials = mapOf(
                    "email" to email,
                    "password" to password
                )
                
                val authResponse = musicApi.login(credentials)
                
                // Save authentication data for persistence
                authenticationManager.saveAuthentication(
                    token = authResponse.token,
                    userId = authResponse.user.id.toString(),
                    userEmail = authResponse.user.email,
                    username = authResponse.user.username,
                    serverUrl = serverUrl
                )
                
                Log.d("AuthenticationHelper", "Login successful for: ${authResponse.user.username}")
                onSuccess()
                
            } catch (e: Exception) {
                Log.e("AuthenticationHelper", "Login failed", e)
                onError(e.message ?: "Login failed")
            }
        }
    }
    
    /**
     * Logout function
     */
    fun logout() {
        Log.d("AuthenticationHelper", "Logging out user")
        authenticationManager.logout()
    }
    
    /**
     * Check if user is currently authenticated
     */
    fun isAuthenticated(): Boolean {
        return authenticationManager.isAuthenticated.value
    }
    
    /**
     * Get current user info
     */
    fun getCurrentUser(): AuthenticationManager.UserInfo? {
        return authenticationManager.getCurrentUser()
    }
    
    /**
     * Get authentication state flow for observing in UI
     */
    fun getAuthenticationStateFlow() = authenticationManager.isAuthenticated
    
    /**
     * Get authentication error flow for observing in UI
     */
    fun getAuthenticationErrorFlow() = authenticationManager.authenticationError
    
    /**
     * Clear authentication error
     */
    fun clearAuthenticationError() {
        authenticationManager.clearAuthenticationError()
    }
}
