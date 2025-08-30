package com.melodee.autoplayer.data.api

import android.util.Log
import com.melodee.autoplayer.util.Logger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object NetworkModule {
    // Optional application context for features that need it (HTTP cache)
    @Volatile
    private var appContext: android.content.Context? = null
    private var baseUrl: String = ""
    private var authToken: String = ""
    private var retrofit: Retrofit? = null
    private var musicApi: MusicApi? = null
    private var scrobbleApi: ScrobbleApi? = null
    
    // Thread-safe locks for configuration changes
    private val configLock = ReentrantReadWriteLock()
    
    // Callback for handling authentication failures
    @Volatile
    private var onAuthenticationFailure: (() -> Unit)? = null
    
    // Thread-safe flag to prevent multiple 401 callbacks
    private val handlingAuthFailure = AtomicBoolean(false)

    fun init(context: android.content.Context) {
        // Keep application context only
        appContext = context.applicationContext
        // If we've already configured a base URL, recreate Retrofit to include cache, etc.
        if (baseUrl.isNotEmpty()) {
            createRetrofitInstance()
        }
    }

    fun setBaseUrl(url: String) {
        configLock.write {
            Logger.i("NetworkModule", "Setting base URL from '$baseUrl' to '$url'")
            
            if (baseUrl != url) {
                baseUrl = url
                Logger.d("NetworkModule", "Base URL changed, recreating Retrofit instance")
                createRetrofitInstance()
            } else {
                Logger.d("NetworkModule", "Base URL unchanged, skipping Retrofit recreation")
            }
            
            Logger.i("NetworkModule", "Base URL configured: $baseUrl")
        }
    }

    fun setAuthToken(token: String) {
        configLock.write {
            Logger.logAuth("NetworkModule", "Setting auth token (present: ${token.isNotEmpty()})")
            
            authToken = token
            // Reset auth failure flag when setting new token
            handlingAuthFailure.set(false)
            // Recreate the Retrofit instance to update the auth token
            Logger.d("NetworkModule", "Recreating Retrofit instance with new token")
            createRetrofitInstance()
            
            Logger.logAuth("NetworkModule", "Auth token configured")
        }
    }

    fun getAuthToken(): String? {
        return configLock.read {
            if (authToken.isNotEmpty()) authToken else null
        }
    }
    
    // Set callback for authentication failures
    fun setAuthenticationFailureCallback(callback: () -> Unit) {
        onAuthenticationFailure = callback
    }
    
    // Clear authentication
    fun clearAuthentication() {
        configLock.write {
            authToken = ""
            handlingAuthFailure.set(false)
            createRetrofitInstance()
        }
    }
    
    // Check if authenticated
    fun isAuthenticated(): Boolean {
        return configLock.read {
            authToken.isNotEmpty() && baseUrl.isNotEmpty()
        }
    }

    private fun createRetrofitInstance() {
        val ctx = appContext

        // Configure dispatcher and connection pool for better control
        val dispatcher = okhttp3.Dispatcher().apply {
            // Allow more overall concurrency but limit per host to prevent overload
            maxRequests = 32
            maxRequestsPerHost = 8
        }

        val connectionPool = okhttp3.ConnectionPool(
            10, // idle connections
            5,  // keep-alive duration
            java.util.concurrent.TimeUnit.MINUTES
        )

        val okHttpBuilder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $authToken")
                    // Pass through request priority if set by callers via header
                    // e.g., request.header("X-Request-Priority")
                    .method(original.method, original.body)
                    .build()
                
                val response = chain.proceed(request)
                
                // Handle 401 Unauthorized responses
                if (response.code == 401 && handlingAuthFailure.compareAndSet(false, true)) {
                    Log.w("NetworkModule", "Received 401 Unauthorized - token expired")
                    // Clear authentication and notify callback
                    clearAuthentication()
                    onAuthenticationFailure?.invoke()
                }
                
                response
            }
            // Retry with simple exponential backoff for idempotent requests and 429/5xx
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            // Conservative, but responsive timeouts
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        // Enable on-disk HTTP cache if we have a context
        if (ctx != null) {
            val cacheDir = java.io.File(ctx.cacheDir, "http_cache")
            val cacheSizeBytes = 10L * 1024L * 1024L // 10 MB
            okHttpBuilder.cache(okhttp3.Cache(cacheDir, cacheSizeBytes))

            // Add a network interceptor to honor cache headers and provide sane defaults for GET
            okHttpBuilder.addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                // If server did not specify caching and request is GET, set a short-lived cache
                if (request.method.equals("GET", ignoreCase = true) &&
                    response.header("Cache-Control").isNullOrBlank()) {
                    return@addNetworkInterceptor response.newBuilder()
                        .header("Cache-Control", "public, max-age=60") // 1 minute default
                        .build()
                }
                response
            }
        }

        val okHttpClient = okHttpBuilder.build()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        musicApi = retrofit?.create(MusicApi::class.java)
        scrobbleApi = retrofit?.create(ScrobbleApi::class.java)
    }

    fun getMusicApi(): MusicApi {
        if (musicApi == null) {
            throw IllegalStateException("MusicApi not initialized. Call setBaseUrl first.")
        }
        return musicApi!!
    }

    fun getScrobbleApi(): ScrobbleApi {
        if (scrobbleApi == null) {
            throw IllegalStateException("ScrobbleApi not initialized. Call setBaseUrl first.")
        }
        return scrobbleApi!!
    }
}
