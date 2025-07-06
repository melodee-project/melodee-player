package com.melodee.autoplayer.presentation.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.melodee.autoplayer.MelodeeApplication
import com.melodee.autoplayer.presentation.ui.home.HomeViewModel
import com.melodee.autoplayer.presentation.ui.playlist.PlaylistViewModel
import com.melodee.autoplayer.domain.model.AuthResponse
import android.util.Log
import java.util.UUID

/**
 * Composable that manages authentication state and sets up ViewModels when user is authenticated
 */
@Composable
fun AuthenticationAwareNavigation(
    homeViewModel: HomeViewModel,
    playlistViewModel: PlaylistViewModel,
    content: @Composable (startDestination: String) -> Unit
) {
    val context = LocalContext.current
    val authenticationManager = remember {
        (context.applicationContext as MelodeeApplication).authenticationManager
    }
    
    val isAuthenticated by authenticationManager.isAuthenticated.collectAsStateWithLifecycle()
    
    // Handle authentication state changes and setup ViewModels
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            val currentUser = authenticationManager.getCurrentUser()
            if (currentUser != null) {
                Log.d("AuthNavigation", "User authenticated on startup, setting up ViewModels")
                
                // Set base URL for both view models  
                homeViewModel.setBaseUrl(currentUser.serverUrl)
                playlistViewModel.setBaseUrl(currentUser.serverUrl)
                
                // Create a mock AuthResponse from stored data for homeViewModel
                val mockAuthResponse = AuthResponse(
                    token = "", // Not needed here as it's already set in NetworkModule
                    serverVersion = "",
                    user = com.melodee.autoplayer.domain.model.User(
                        id = java.util.UUID.fromString(currentUser.userId.ifEmpty { "00000000-0000-0000-0000-000000000000" }),
                        username = currentUser.username,
                        email = currentUser.email,
                        thumbnailUrl = "",
                        imageUrl = ""
                    )
                )
                
                // Set user data (this will automatically load playlists)
                homeViewModel.setUser(mockAuthResponse.user)
                
                Log.d("AuthNavigation", "ViewModels configured for user: ${currentUser.username}")
            }
        }
    }
    
    // Determine start destination based on authentication state
    val startDestination = if (isAuthenticated) {
        Log.d("AuthNavigation", "User is authenticated, starting with home screen")
        "home"
    } else {
        Log.d("AuthNavigation", "User not authenticated, starting with login screen")
        "login"
    }
    
    content(startDestination)
}
