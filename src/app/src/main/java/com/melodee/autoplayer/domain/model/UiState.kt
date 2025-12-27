package com.melodee.autoplayer.domain.model

/**
 * Sealed class representing UI state for data loading operations
 * Provides type-safe handling of loading, success, and error states
 */
sealed class UiState<out T> {
    /**
     * Initial idle state
     */
    object Idle : UiState<Nothing>()
    
    /**
     * Loading state - operation in progress
     */
    data class Loading(
        val isInitialLoad: Boolean = true,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : UiState<Nothing>()
    
    /**
     * Success state with data
     */
    data class Success<T>(
        val data: T,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : UiState<T>()
    
    /**
     * Error state with detailed information
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null,
        val errorType: ErrorType = ErrorType.UNKNOWN,
        val isRetryable: Boolean = true
    ) : UiState<Nothing>()
}

/**
 * Types of errors that can occur
 */
enum class ErrorType {
    /**
     * Network connectivity issues
     */
    NETWORK,
    
    /**
     * Authentication/authorization failures
     */
    AUTHENTICATION,
    
    /**
     * Server errors (5xx)
     */
    SERVER_ERROR,
    
    /**
     * Resource not found (404)
     */
    NOT_FOUND,
    
    /**
     * Request validation errors (4xx)
     */
    VALIDATION,
    
    /**
     * Server version incompatibility
     */
    VERSION_MISMATCH,
    
    /**
     * Unknown or unclassified errors
     */
    UNKNOWN
}

/**
 * Extension function to get user-friendly error message
 */
fun ErrorType.toUserMessage(): String {
    return when (this) {
        ErrorType.NETWORK -> "No internet connection. Please check your network and try again."
        ErrorType.AUTHENTICATION -> "Your session has expired. Please log in again."
        ErrorType.SERVER_ERROR -> "Server error occurred. Please try again later."
        ErrorType.NOT_FOUND -> "The requested content could not be found."
        ErrorType.VALIDATION -> "Invalid request. Please check your input."
        ErrorType.VERSION_MISMATCH -> "Server version is incompatible. Please update the app or contact support."
        ErrorType.UNKNOWN -> "An unexpected error occurred. Please try again."
    }
}

/**
 * Extension function to map exceptions to ErrorType
 */
fun Throwable.toErrorType(): ErrorType {
    return when (this) {
        is java.net.UnknownHostException,
        is java.net.SocketTimeoutException,
        is java.io.IOException -> ErrorType.NETWORK
        
        is retrofit2.HttpException -> when (this.code()) {
            401, 403 -> ErrorType.AUTHENTICATION
            404 -> ErrorType.NOT_FOUND
            in 400..499 -> ErrorType.VALIDATION
            in 500..599 -> ErrorType.SERVER_ERROR
            else -> ErrorType.UNKNOWN
        }
        
        is IllegalStateException -> {
            if (this.message?.contains("version", ignoreCase = true) == true) {
                ErrorType.VERSION_MISMATCH
            } else {
                ErrorType.UNKNOWN
            }
        }
        
        else -> ErrorType.UNKNOWN
    }
}
