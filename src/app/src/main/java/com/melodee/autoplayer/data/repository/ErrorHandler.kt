package com.melodee.autoplayer.data.repository

import android.content.Context
import android.util.Log
import com.melodee.autoplayer.R
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized error handling utility for consistent error mapping and logging
 */
object ErrorHandler {
    
    /**
     * Maps various exceptions to user-friendly IOException with localized messages
     * and ensures consistent logging
     */
    fun handleRepositoryError(
        context: Context,
        exception: Exception,
        operationName: String,
        tag: String = "Repository"
    ): IOException {
        val errorMessage = when (exception) {
            is HttpException -> {
                Log.w(tag, "$operationName failed with HTTP ${exception.code()}: ${exception.message()}")
                when (exception.code()) {
                    400 -> context.getString(R.string.invalid_credentials)
                    401 -> context.getString(R.string.unauthorized_access)
                    403 -> context.getString(R.string.forbidden_access)
                    404 -> context.getString(R.string.account_not_found)
                    429 -> context.getString(R.string.too_many_requests)
                    in 500..599 -> context.getString(R.string.server_error)
                    else -> context.getString(R.string.network_error, "HTTP ${exception.code()}")
                }
            }
            is UnknownHostException -> {
                Log.w(tag, "$operationName failed: Server unreachable", exception)
                context.getString(R.string.server_unreachable)
            }
            is ConnectException -> {
                Log.w(tag, "$operationName failed: Connection failed", exception)
                context.getString(R.string.server_unreachable)
            }
            is SocketTimeoutException -> {
                Log.w(tag, "$operationName failed: Request timeout", exception)
                context.getString(R.string.server_unreachable)
            }
            is IOException -> {
                Log.w(tag, "$operationName failed: IO error", exception)
                context.getString(R.string.network_error, exception.message ?: "Unknown network error")
            }
            else -> {
                Log.e(tag, "$operationName failed: Unexpected error", exception)
                context.getString(R.string.network_error, exception.message ?: "Unknown error")
            }
        }
        
        return IOException(errorMessage)
    }
    
    /**
     * Wraps repository operations with consistent error handling
     */
    inline fun <T> handleOperation(
        context: Context,
        operationName: String,
        tag: String = "Repository",
        operation: () -> T
    ): T {
        return try {
            Log.d(tag, "Starting $operationName")
            val result = operation()
            Log.d(tag, "$operationName completed successfully")
            result
        } catch (e: Exception) {
            throw handleRepositoryError(context, e, operationName, tag)
        }
    }
}