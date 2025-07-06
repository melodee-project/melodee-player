package com.melodee.autoplayer.presentation.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodee.autoplayer.data.SettingsManager
import com.melodee.autoplayer.data.repository.MusicRepository
import com.melodee.autoplayer.domain.model.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException

sealed class LoginState {
    object Initial : LoginState()
    object Loading : LoginState()
    data class Success(val response: AuthResponse) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    var serverUrl: String = settingsManager.serverUrl
        private set
    private var repository: MusicRepository? = null
    
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun login(email: String, password: String, serverUrl: String) {
        if (email.isBlank() || password.isBlank() || serverUrl.isBlank()) {
            _loginState.value = LoginState.Error("Please fill in all fields")
            return
        }

        // Normalize the server URL
        val normalizedUrl = normalizeServerUrl(serverUrl)

        this.serverUrl = normalizedUrl
        settingsManager.serverUrl = normalizedUrl
        repository = MusicRepository(normalizedUrl, getApplication())
        _isLoading.value = true
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            try {
                repository?.login(email, password)
                    ?.catch { e ->
                        Log.e("LoginViewModel", "Login error caught in flow", e)
                        _loginState.value = LoginState.Error(e.message ?: "An error occurred")
                        _isLoading.value = false
                    }
                    ?.collect { response ->
                        // Store user information in settings including avatar URLs
                        settingsManager.userId = response.user.id.toString()
                        settingsManager.userEmail = response.user.email
                        settingsManager.username = response.user.username
                        settingsManager.userThumbnailUrl = response.user.thumbnailUrl
                        settingsManager.userImageUrl = response.user.imageUrl
                        settingsManager.authToken = response.token
                        
                        _loginState.value = LoginState.Success(response)
                        _isLoading.value = false
                    }
            } catch (e: IOException) {
                Log.e("LoginViewModel", "IOException during login", e)
                _loginState.value = LoginState.Error(e.message ?: "Network error occurred")
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Unexpected error during login", e)
                _loginState.value = LoginState.Error(e.message ?: "An unexpected error occurred")
                _isLoading.value = false
            }
        }
    }

    private fun normalizeServerUrl(inputUrl: String): String {
        var url = inputUrl.trim()
        Log.d("LoginViewModel", "Input URL: '$inputUrl'")
        Log.d("LoginViewModel", "Trimmed URL: '$url'")
        
        // Ensure the URL has a protocol
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
            Log.d("LoginViewModel", "Added protocol: '$url'")
        }
        
        // Remove any trailing slashes
        url = url.trimEnd('/')
        Log.d("LoginViewModel", "Removed trailing slashes: '$url'")
        
        // Check if the URL already contains the API path
        if (url.contains("/api/v1")) {
            // Remove any existing /api/v1 path and rebuild it properly
            val baseUrl = url.substringBefore("/api/v1")
            val normalizedUrl = "$baseUrl/api/v1/"
            Log.d("LoginViewModel", "Found existing /api/v1, normalized to: '$normalizedUrl'")
            return normalizedUrl
        } else {
            // Add the API path
            val normalizedUrl = "$url/api/v1/"
            Log.d("LoginViewModel", "Added /api/v1/ path: '$normalizedUrl'")
            return normalizedUrl
        }
    }

    fun logout() {
        // Clear user data from settings
        settingsManager.clearUserData()
        
        // Reset login state
        _loginState.value = LoginState.Initial
        _isLoading.value = false
        
        // Clear repository
        repository = null
    }

    fun clearError() {
        if (_loginState.value is LoginState.Error) {
            _loginState.value = LoginState.Initial
        }
    }
} 