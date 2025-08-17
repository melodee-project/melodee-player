package com.melodee.autoplayer.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Request deduplication utility to prevent redundant API calls
 * Ensures that identical requests are not made simultaneously
 */
class RequestDeduplicator {
    private val activeRequests = ConcurrentHashMap<String, Flow<*>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    
    /**
     * Deduplicates requests based on a unique key
     * If a request with the same key is already in progress, returns the existing Flow
     * Otherwise, executes the new request and caches it
     */
    suspend fun <T> deduplicate(
        key: String,
        operation: suspend () -> Flow<T>
    ): Flow<T> {
        mutex.withLock {
            @Suppress("UNCHECKED_CAST")
            val existingRequest = activeRequests[key] as? Flow<T>
            
            if (existingRequest != null) {
                Log.d("RequestDeduplicator", "Reusing existing request for key: $key")
                return existingRequest
            }
            
            Log.d("RequestDeduplicator", "Creating new request for key: $key")
            val newRequest = operation()
                .shareIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), 1) // Share the Flow so multiple collectors get the same result
                .onEach { 
                    // Remove from cache when completed
                    activeRequests.remove(key)
                    Log.d("RequestDeduplicator", "Request completed and removed from cache: $key")
                }
            
            activeRequests[key] = newRequest
            return newRequest
        }
    }
    
    /**
     * Generates a cache key for repository operations
     */
    fun generateKey(operation: String, vararg params: Any?): String {
        val paramString = params.joinToString("|") { it?.toString() ?: "null" }
        return "$operation:$paramString"
    }
    
    /**
     * Clears all cached requests (useful for logout or force refresh)
     */
    suspend fun clearCache() {
        mutex.withLock {
            Log.d("RequestDeduplicator", "Clearing request cache (${activeRequests.size} items)")
            activeRequests.clear()
        }
    }
    
    /**
     * Gets the number of active requests (for monitoring)
     */
    fun getActiveRequestCount(): Int = activeRequests.size
    
    companion object {
        @Volatile
        private var INSTANCE: RequestDeduplicator? = null
        
        fun getInstance(): RequestDeduplicator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RequestDeduplicator().also { INSTANCE = it }
            }
        }
    }
}