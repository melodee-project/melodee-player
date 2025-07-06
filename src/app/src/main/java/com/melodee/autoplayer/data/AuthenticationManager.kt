package com.melodee.autoplayer.data

import android.content.Context
import android.util.Log
import com.melodee.autoplayer.data.api.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthenticationManager(private val context: Context) {
    private val settingsManager = SettingsManager(context)
    
    // Authentication state
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    private val _authenticationError = MutableStateFlow<String?>(null)
    val authenticationError: StateFlow<String?> = _authenticationError.asStateFlow()
    
    init {
        // Check if user is already authenticated on app start
        checkExistingAuthentication()
        
        // Set up 401 handling callback
        NetworkModule.setAuthenticationFailureCallback {
            handleAuthenticationFailure()
        }
    }
    
    private fun checkExistingAuthentication() {
        Log.d("AuthenticationManager", "Checking existing authentication")
        
        if (settingsManager.isAuthenticated()) {
            Log.d("AuthenticationManager", "Found existing authentication, initializing NetworkModule")
            
            // Initialize NetworkModule with stored credentials
            NetworkModule.setBaseUrl(settingsManager.serverUrl)
            NetworkModule.setAuthToken(settingsManager.authToken)
            
            _isAuthenticated.value = true
            
            Log.d("AuthenticationManager", "Authentication restored successfully")
            Log.d("AuthenticationManager", "Server: ${settingsManager.serverUrl}")
            Log.d("AuthenticationManager", "User: ${settingsManager.username} (${settingsManager.userEmail})")
        } else {
            Log.d("AuthenticationManager", "No existing authentication found")
            _isAuthenticated.value = false
        }
    }
    
    fun saveAuthentication(
        token: String,
        userId: String,
        userEmail: String,
        username: String,
        serverUrl: String
    ) {
        Log.d("AuthenticationManager", "Saving authentication for user: $username")
        
        // Save to persistent storage
        settingsManager.saveAuthenticationData(token, userId, userEmail, username, serverUrl)
        
        // Update NetworkModule
        NetworkModule.setBaseUrl(serverUrl)
        NetworkModule.setAuthToken(token)
        
        // Update state
        _isAuthenticated.value = true
        _authenticationError.value = null
        
        Log.d("AuthenticationManager", "Authentication saved and initialized successfully")
    }
    
    fun logout() {
        Log.d("AuthenticationManager", "Logging out user")
        
        // Clear persistent storage
        settingsManager.logout()
        
        // Clear NetworkModule
        NetworkModule.clearAuthentication()
        
        // Update state
        _isAuthenticated.value = false
        _authenticationError.value = null
        
        Log.d("AuthenticationManager", "Logout completed")
    }
    
    private fun handleAuthenticationFailure() {
        Log.w("AuthenticationManager", "Authentication failed - token expired or invalid")
        
        // Clear stored authentication
        settingsManager.logout()
        
        // Update state
        _isAuthenticated.value = false
        _authenticationError.value = "Your session has expired. Please login again."
        
        Log.d("AuthenticationManager", "Authentication failure handled - user needs to re-authenticate")
    }
    
    fun clearAuthenticationError() {
        _authenticationError.value = null
    }
    
    // Get current authentication info
    fun getCurrentUser(): UserInfo? {
        return if (settingsManager.isAuthenticated()) {
            UserInfo(
                userId = settingsManager.userId,
                username = settingsManager.username,
                email = settingsManager.userEmail,
                serverUrl = settingsManager.serverUrl
            )
        } else {
            null
        }
    }
    
    data class UserInfo(
        val userId: String,
        val username: String,
        val email: String,
        val serverUrl: String
    )
}
