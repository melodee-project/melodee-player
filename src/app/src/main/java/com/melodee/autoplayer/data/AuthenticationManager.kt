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
        Log.i("AuthenticationManager", "=== CHECKING EXISTING AUTHENTICATION ===")
        
        val hasAuthToken = settingsManager.authToken.isNotEmpty()
        val hasServerUrl = settingsManager.serverUrl.isNotEmpty()
        val hasUserId = settingsManager.userId.isNotEmpty()
        val hasUsername = settingsManager.username.isNotEmpty()
        val hasUserEmail = settingsManager.userEmail.isNotEmpty()
        
        Log.i("AuthenticationManager", "Auth token present: $hasAuthToken")
        Log.i("AuthenticationManager", "Server URL present: $hasServerUrl")
        Log.i("AuthenticationManager", "User ID present: $hasUserId")
        Log.i("AuthenticationManager", "Username present: $hasUsername")
        Log.i("AuthenticationManager", "User email present: $hasUserEmail")
        
        if (hasAuthToken) {
            Log.i("AuthenticationManager", "Auth token: ${settingsManager.authToken.take(20)}...")
        }
        if (hasServerUrl) {
            Log.i("AuthenticationManager", "Server URL: ${settingsManager.serverUrl}")
        }
        if (hasUsername) {
            Log.i("AuthenticationManager", "Username: ${settingsManager.username}")
        }
        if (hasUserEmail) {
            Log.i("AuthenticationManager", "User email: ${settingsManager.userEmail}")
        }
        
        val isAuthenticated = settingsManager.isAuthenticated()
        Log.i("AuthenticationManager", "SettingsManager.isAuthenticated(): $isAuthenticated")
        
        if (isAuthenticated) {
            Log.i("AuthenticationManager", "Found existing authentication, initializing NetworkModule")
            
            // Initialize NetworkModule with stored credentials
            Log.d("AuthenticationManager", "Setting NetworkModule base URL: ${settingsManager.serverUrl}")
            NetworkModule.setBaseUrl(settingsManager.serverUrl)
            
            Log.d("AuthenticationManager", "Setting NetworkModule auth token")
            NetworkModule.setAuthToken(settingsManager.authToken)
            
            // Verify NetworkModule state
            val networkAuthenticated = NetworkModule.isAuthenticated()
            Log.i("AuthenticationManager", "NetworkModule.isAuthenticated(): $networkAuthenticated")
            
            _isAuthenticated.value = true
            
            Log.i("AuthenticationManager", "Authentication restored successfully")
            Log.i("AuthenticationManager", "Server: ${settingsManager.serverUrl}")
            Log.i("AuthenticationManager", "User: ${settingsManager.username} (${settingsManager.userEmail})")
        } else {
            Log.i("AuthenticationManager", "No existing authentication found")
            _isAuthenticated.value = false
        }
        
        Log.i("AuthenticationManager", "Final authentication state: ${_isAuthenticated.value}")
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
        
        // Before clearing everything, check if we actually have stored credentials
        val hasStoredAuth = settingsManager.isAuthenticated()
        
        if (hasStoredAuth) {
            Log.i("AuthenticationManager", "Still have stored credentials - may be temporary network issue")
            // Don't clear stored authentication for temporary issues
            // Just update the state to show we need to re-authenticate
            _isAuthenticated.value = false
            _authenticationError.value = "Authentication failed. Please try again or re-login if the problem persists."
        } else {
            Log.i("AuthenticationManager", "No stored credentials - clearing authentication")
            // Clear stored authentication
            settingsManager.logout()
            
            // Update state
            _isAuthenticated.value = false
            _authenticationError.value = "Your session has expired. Please login again."
        }
        
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
    
    // Method to manually restore authentication state from stored data
    fun restoreAuthenticationFromStorage(): Boolean {
        Log.i("AuthenticationManager", "=== MANUALLY RESTORING AUTHENTICATION ===")
        
        try {
            if (settingsManager.isAuthenticated()) {
                Log.i("AuthenticationManager", "Found stored authentication data")
                
                // Re-initialize NetworkModule with stored credentials
                NetworkModule.setBaseUrl(settingsManager.serverUrl)
                NetworkModule.setAuthToken(settingsManager.authToken)
                
                // Update state
                _isAuthenticated.value = true
                _authenticationError.value = null
                
                Log.i("AuthenticationManager", "Authentication restored from storage successfully")
                Log.i("AuthenticationManager", "User: ${settingsManager.username} at ${settingsManager.serverUrl}")
                
                return true
            } else {
                Log.w("AuthenticationManager", "No stored authentication data to restore")
                return false
            }
        } catch (e: Exception) {
            Log.e("AuthenticationManager", "Failed to restore authentication from storage", e)
            return false
        }
    }
}
