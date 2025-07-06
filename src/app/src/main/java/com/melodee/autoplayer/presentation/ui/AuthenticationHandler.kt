package com.melodee.autoplayer.presentation.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.melodee.autoplayer.MelodeeApplication
import android.util.Log

/**
 * Composable that handles authentication-based navigation
 */
@Composable
fun AuthenticationHandler(
    navController: NavHostController,
    content: @Composable (startDestination: String) -> Unit
) {
    val context = LocalContext.current
    val authenticationManager = remember {
        (context.applicationContext as MelodeeApplication).authenticationManager
    }
    
    val isAuthenticated by authenticationManager.isAuthenticated.collectAsStateWithLifecycle()
    
    // Determine start destination based on authentication state
    val startDestination = if (isAuthenticated) {
        Log.d("AuthenticationHandler", "User is authenticated, starting with home screen")
        "home"
    } else {
        Log.d("AuthenticationHandler", "User not authenticated, starting with login screen")
        "login"
    }
    
    content(startDestination)
}
