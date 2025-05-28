package com.melodee.autoplayer.util

import java.util.concurrent.TimeUnit

/**
 * Format duration in milliseconds to MM:SS or HH:MM:SS format
 */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * Format file size in bytes to human readable format
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    
    return String.format(
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}

/**
 * Format number with thousands separator
 */
fun formatNumber(number: Long): String {
    return String.format("%,d", number)
}

/**
 * Format percentage with one decimal place
 */
fun formatPercentage(value: Float): String {
    return String.format("%.1f%%", value * 100)
} 