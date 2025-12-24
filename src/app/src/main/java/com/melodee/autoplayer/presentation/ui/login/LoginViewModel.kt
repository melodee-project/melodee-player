package com.melodee.autoplayer.presentation.ui.login

import android.app.Application
import android.util.Log
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodee.autoplayer.data.SettingsManager
import com.melodee.autoplayer.data.repository.MusicRepository
import com.melodee.autoplayer.domain.model.AuthResponse
import com.melodee.autoplayer.service.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import com.melodee.autoplayer.util.UrlParser

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

    fun login(emailOrUsername: String, password: String, serverUrl: String) {
        if (emailOrUsername.isBlank() || password.isBlank() || serverUrl.isBlank()) {
            _loginState.value = LoginState.Error("Please fill in all fields")
            return
        }

        // Normalize the server URL using the new parser
        val normalizedUrl = UrlParser.normalizeServerUrl(serverUrl)

        this.serverUrl = normalizedUrl
        settingsManager.serverUrl = normalizedUrl
        repository = MusicRepository(normalizedUrl, getApplication())
        _isLoading.value = true
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            try {
                repository?.login(emailOrUsername, password)
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
                        settingsManager.refreshToken = response.refreshToken
                        settingsManager.refreshTokenExpiresAt = response.refreshTokenExpiresAt
                        
                        // Inform global AuthenticationManager so app state reflects authenticated
                        try {
                            val app = getApplication<Application>() as com.melodee.autoplayer.MelodeeApplication
                            app.authenticationManager.saveAuthentication(
                                token = response.token,
                                userId = response.user.id.toString(),
                                userEmail = response.user.email,
                                username = response.user.username,
                                serverUrl = this@LoginViewModel.serverUrl,
                                refreshToken = response.refreshToken,
                                refreshTokenExpiresAt = response.refreshTokenExpiresAt,
                                thumbnailUrl = response.user.thumbnailUrl,
                                imageUrl = response.user.imageUrl
                            )
                        } catch (e: Exception) {
                            Log.w("LoginViewModel", "Failed to update AuthenticationManager on login", e)
                        }

                        // After successful authentication, stop any playing song and clear queue
                        try {
                            val ctx = getApplication<Application>()
                            val stopIntent = Intent(ctx, MusicService::class.java).apply {
                                action = MusicService.ACTION_STOP
                            }
                            ctx.startService(stopIntent)
                            val clearIntent = Intent(ctx, MusicService::class.java).apply {
                                action = MusicService.ACTION_CLEAR_QUEUE
                            }
                            ctx.startService(clearIntent)
                        } catch (e: Exception) {
                            Log.w("LoginViewModel", "Failed to stop/clear after login", e)
                        }

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

    // URL normalization is now handled by UrlParser.normalizeServerUrl()

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
