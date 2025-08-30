package com.melodee.autoplayer.util

import android.content.Context
import android.provider.Settings

fun hasNotificationListenerAccess(context: Context): Boolean {
    return try {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        enabled?.contains(context.packageName) == true
    } catch (_: Exception) {
        false
    }
}

