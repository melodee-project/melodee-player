package com.melodee.autoplayer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

@Composable
fun rememberPermissionState(
    context: Context,
    onPermissionGranted: () -> Unit = {},
    onPermissionDenied: () -> Unit = {}
): PermissionState {
    var hasMediaPermission by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasRequestedPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasMediaPermission = permissions[Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK] ?: false
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
        hasRequestedPermission = true

        if (hasMediaPermission) {
            onPermissionGranted()
        } else {
            Toast.makeText(
                context,
                "Please grant media control permission in Settings to play music",
                Toast.LENGTH_LONG
            ).show()
            onPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        
        // Check media permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
            ) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
        } else {
            hasMediaPermission = true
        }

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                hasNotificationPermission = true
            }
        } else {
            hasNotificationPermission = true
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            onPermissionGranted()
        }
    }

    return PermissionState(
        hasMediaPermission = hasMediaPermission,
        hasNotificationPermission = hasNotificationPermission,
        hasRequestedPermission = hasRequestedPermission,
        requestPermission = { 
            if (!hasRequestedPermission) {
                val permissions = mutableListOf<String>()
                if (!hasMediaPermission) {
                    permissions.add(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (permissions.isNotEmpty()) {
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            } else {
                // If permission was previously denied, guide user to settings
                Toast.makeText(
                    context,
                    "Please enable media control permission in Settings to play music",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )
}

class PermissionState(
    val hasMediaPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val hasRequestedPermission: Boolean,
    val requestPermission: () -> Unit
) 