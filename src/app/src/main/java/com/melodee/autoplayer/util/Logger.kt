package com.melodee.autoplayer.util

import android.util.Log

/**
 * Centralized logging utility with consistent formatting and proper log levels
 * Automatically filters logs in release builds for performance and security
 */
object Logger {
    
    // Enable verbose logging - can be configured based on needs
    private val isDebugBuild = true // TODO: Connect to BuildConfig.DEBUG when available
    
    // Log levels for filtering
    enum class Level(val priority: Int) {
        VERBOSE(2),
        DEBUG(3),
        INFO(4),
        WARN(5),
        ERROR(6)
    }
    
    // Minimum log level to output (INFO and above in release, DEBUG in debug)
    private val minLevel = if (isDebugBuild) Level.DEBUG else Level.INFO
    
    /**
     * Log verbose message (debug builds only)
     */
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.VERBOSE)) {
            if (throwable != null) {
                Log.v(formatTag(tag), message, throwable)
            } else {
                Log.v(formatTag(tag), message)
            }
        }
    }
    
    /**
     * Log debug message (debug builds only)
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.DEBUG)) {
            if (throwable != null) {
                Log.d(formatTag(tag), message, throwable)
            } else {
                Log.d(formatTag(tag), message)
            }
        }
    }
    
    /**
     * Log info message
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.INFO)) {
            if (throwable != null) {
                Log.i(formatTag(tag), message, throwable)
            } else {
                Log.i(formatTag(tag), message)
            }
        }
    }
    
    /**
     * Log warning message
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.WARN)) {
            if (throwable != null) {
                Log.w(formatTag(tag), message, throwable)
            } else {
                Log.w(formatTag(tag), message)
            }
        }
    }
    
    /**
     * Log error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.ERROR)) {
            if (throwable != null) {
                Log.e(formatTag(tag), message, throwable)
            } else {
                Log.e(formatTag(tag), message)
            }
        }
    }
    
    /**
     * Log operation lifecycle (start/complete/error) with timing
     */
    class OperationLogger(private val tag: String, private val operation: String) {
        private val startTime = System.currentTimeMillis()
        
        init {
            d(tag, "Starting $operation")
        }
        
        fun complete() {
            val duration = System.currentTimeMillis() - startTime
            d(tag, "$operation completed in ${duration}ms")
        }
        
        fun error(message: String, throwable: Throwable? = null) {
            val duration = System.currentTimeMillis() - startTime
            e(tag, "$operation failed after ${duration}ms: $message", throwable)
        }
    }
    
    /**
     * Start operation logging with automatic timing
     */
    fun startOperation(tag: String, operation: String): OperationLogger {
        return OperationLogger(tag, operation)
    }
    
    /**
     * Log authentication events with proper security filtering
     */
    fun logAuth(tag: String, message: String, level: Level = Level.INFO) {
        // Never log sensitive auth data in production
        val safeMessage = if (isDebugBuild) message else sanitizeAuthMessage(message)
        
        when (level) {
            Level.DEBUG -> d(tag, "[AUTH] $safeMessage")
            Level.INFO -> i(tag, "[AUTH] $safeMessage")
            Level.WARN -> w(tag, "[AUTH] $safeMessage")
            Level.ERROR -> e(tag, "[AUTH] $safeMessage")
            Level.VERBOSE -> v(tag, "[AUTH] $safeMessage")
        }
    }
    
    /**
     * Log network events with request/response info
     */
    fun logNetwork(tag: String, method: String, url: String, statusCode: Int? = null, duration: Long? = null) {
        val message = buildString {
            append("[$method] $url")
            statusCode?.let { append(" -> $it") }
            duration?.let { append(" (${it}ms)") }
        }
        
        when {
            statusCode == null -> d(tag, "[NET] $message")
            statusCode in 200..299 -> d(tag, "[NET] $message")
            statusCode in 400..499 -> w(tag, "[NET] $message")
            statusCode >= 500 -> e(tag, "[NET] $message")
            else -> i(tag, "[NET] $message")
        }
    }
    
    /**
     * Log performance metrics
     */
    fun logPerformance(tag: String, metric: String, value: Any, unit: String = "") {
        if (shouldLog(Level.DEBUG)) {
            d(tag, "[PERF] $metric: $value$unit")
        }
    }
    
    private fun shouldLog(level: Level): Boolean {
        return level.priority >= minLevel.priority
    }
    
    private fun formatTag(tag: String): String {
        return "Melodee.$tag"
    }
    
    private fun sanitizeAuthMessage(message: String): String {
        return message
            .replace(Regex("(token|password|secret)[\"'=\\s:]+[^\\s\"',}]+", RegexOption.IGNORE_CASE), "$1=***")
            .replace(Regex("Bearer\\s+[^\\s]+", RegexOption.IGNORE_CASE), "Bearer ***")
    }
}