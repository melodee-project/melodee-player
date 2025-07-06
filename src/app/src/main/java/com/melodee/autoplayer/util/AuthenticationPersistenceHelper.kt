package com.melodee.autoplayer.util

import android.content.Context
import android.util.Log
import com.melodee.autoplayer.MelodeeApplication
import com.melodee.autoplayer.data.AuthenticationManager
import com.melodee.autoplayer.data.SettingsManager
import com.melodee.autoplayer.data.api.NetworkModule

/**
 * Utility class to demonstrate and test authentication persistence across app restarts
 */
class AuthenticationPersistenceHelper(private val context: Context) {
    
    private val authenticationManager: AuthenticationManager by lazy {
        (context.applicationContext as MelodeeApplication).authenticationManager
    }
    
    private val settingsManager = SettingsManager(context)
    
    /**
     * Check if authentication should persist after app restart
     */
    fun verifyAuthenticationPersistence(): Boolean {
        Log.i("AuthPersistenceHelper", "=== VERIFYING AUTHENTICATION PERSISTENCE ===")
        
        val hasStoredCredentials = settingsManager.isAuthenticated()
        val networkIsConfigured = NetworkModule.isAuthenticated()
        val authManagerState = authenticationManager.isAuthenticated.value
        val currentUser = authenticationManager.getCurrentUser()
        
        Log.i("AuthPersistenceHelper", "Stored credentials present: $hasStoredCredentials")
        Log.i("AuthPersistenceHelper", "NetworkModule configured: $networkIsConfigured")
        Log.i("AuthPersistenceHelper", "AuthManager state: $authManagerState")
        Log.i("AuthPersistenceHelper", "Current user: ${currentUser?.username ?: "null"}")
        
        if (hasStoredCredentials) {
            Log.i("AuthPersistenceHelper", "✅ Authentication data is persisted")
            Log.i("AuthPersistenceHelper", "Server: ${settingsManager.serverUrl}")
            Log.i("AuthPersistenceHelper", "User: ${settingsManager.username}")
            Log.i("AuthPersistenceHelper", "Token: ${if (settingsManager.authToken.isNotEmpty()) "Present" else "Missing"}")
            
            if (authManagerState) {
                Log.i("AuthPersistenceHelper", "✅ Authentication state is properly restored")
                return true
            } else {
                Log.w("AuthPersistenceHelper", "⚠️ Authentication data exists but state not restored")
                // Try to restore
                return attemptAuthenticationRestore()
            }
        } else {
            Log.i("AuthPersistenceHelper", "❌ No authentication data to persist")
            return false
        }
    }
    
    /**
     * Attempt to restore authentication state from stored data
     */
    private fun attemptAuthenticationRestore(): Boolean {
        Log.i("AuthPersistenceHelper", "Attempting to restore authentication state...")
        
        return try {
            // Use the existing restore method
            authenticationManager.restoreAuthenticationFromStorage()
        } catch (e: Exception) {
            Log.e("AuthPersistenceHelper", "Failed to restore authentication", e)
            false
        }
    }
    
    /**
     * Demonstrate the authentication flow and persistence
     */
    fun demonstrateAuthenticationFlow() {
        Log.i("AuthPersistenceHelper", "=== AUTHENTICATION FLOW DEMONSTRATION ===")
        
        // 1. Check initial state
        val initialState = verifyAuthenticationPersistence()
        Log.i("AuthPersistenceHelper", "1. Initial authentication state: $initialState")
        
        // 2. Show what happens on successful login
        Log.i("AuthPersistenceHelper", "2. On successful login:")
        Log.i("AuthPersistenceHelper", "   - Authentication data is saved to SharedPreferences")
        Log.i("AuthPersistenceHelper", "   - NetworkModule is configured with token and base URL")
        Log.i("AuthPersistenceHelper", "   - AuthenticationManager state is updated")
        
        // 3. Show what happens on app restart
        Log.i("AuthPersistenceHelper", "3. On app restart:")
        Log.i("AuthPersistenceHelper", "   - AuthenticationManager checks for stored credentials")
        Log.i("AuthPersistenceHelper", "   - If found, NetworkModule is reconfigured")
        Log.i("AuthPersistenceHelper", "   - Authentication state is restored")
        Log.i("AuthPersistenceHelper", "   - User can continue without re-login")
        
        // 4. Show what happens on logout
        Log.i("AuthPersistenceHelper", "4. On logout:")
        Log.i("AuthPersistenceHelper", "   - All stored authentication data is cleared")
        Log.i("AuthPersistenceHelper", "   - NetworkModule authentication is cleared")
        Log.i("AuthPersistenceHelper", "   - AuthenticationManager state is reset")
        
        Log.i("AuthPersistenceHelper", "=== DEMONSTRATION COMPLETE ===")
    }
    
    /**
     * Get a summary of the current authentication state
     */
    fun getAuthenticationSummary(): String {
        val hasStored = settingsManager.isAuthenticated()
        val isActive = authenticationManager.isAuthenticated.value
        val user = authenticationManager.getCurrentUser()
        
        return buildString {
            appendLine("Authentication Summary:")
            appendLine("- Stored credentials: ${if (hasStored) "Yes" else "No"}")
            appendLine("- Active session: ${if (isActive) "Yes" else "No"}")
            appendLine("- Current user: ${user?.username ?: "None"}")
            if (user != null) {
                appendLine("- Server: ${user.serverUrl}")
                appendLine("- Email: ${user.email}")
            }
        }
    }
}
