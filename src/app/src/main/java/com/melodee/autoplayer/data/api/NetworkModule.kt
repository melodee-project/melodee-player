package com.melodee.autoplayer.data.api

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.melodee.autoplayer.util.Logger
import com.melodee.autoplayer.domain.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
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
    private var refreshToken: String = ""
    private var retrofit: Retrofit? = null
    private var musicApi: MusicApi? = null
    private var scrobbleApi: ScrobbleApi? = null
    
    // Thread-safe locks for configuration changes
    private val configLock = ReentrantReadWriteLock()
    
    // Callback for handling authentication failures
    @Volatile
    private var onAuthenticationFailure: (() -> Unit)? = null
    @Volatile
    private var onTokenUpdated: ((String, String) -> Unit)? = null
    
    // Thread-safe flag to prevent multiple 401 callbacks
    private val handlingAuthFailure = AtomicBoolean(false)
    private val refreshLock = Any()

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

    fun setTokens(token: String, refresh: String) {
        configLock.write {
            Logger.logAuth("NetworkModule", "Setting auth token (present: ${token.isNotEmpty()}); refresh present: ${refresh.isNotEmpty()}")
            authToken = token
            refreshToken = refresh
            handlingAuthFailure.set(false)
            Logger.d("NetworkModule", "Recreating Retrofit instance with updated tokens")
            createRetrofitInstance()
            Logger.logAuth("NetworkModule", "Tokens configured")
        }
    }

    fun setAuthToken(token: String) {
        setTokens(token, refreshToken)
    }

    fun setRefreshToken(token: String) {
        setTokens(authToken, token)
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

    fun setTokenUpdateCallback(callback: (newAccessToken: String, newRefreshToken: String) -> Unit) {
        onTokenUpdated = callback
    }
    
    // Clear authentication
    fun clearAuthentication() {
        configLock.write {
            authToken = ""
            refreshToken = ""
            handlingAuthFailure.set(false)
            createRetrofitInstance()
        }
    }
    
    // Check if authenticated
    fun isAuthenticated(): Boolean {
        return configLock.read {
            (authToken.isNotEmpty() || refreshToken.isNotEmpty()) && baseUrl.isNotEmpty()
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
                val skipAuth = original.header("X-Refresh-Request") == "true"
                val requestBuilder = original.newBuilder()
                    .method(original.method, original.body)

                if (!skipAuth && authToken.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $authToken")
                }

                var response = chain.proceed(requestBuilder.build())

                // Handle 401 Unauthorized responses with a single refresh attempt
                if (!skipAuth && response.code == 401) {
                    val refreshed = attemptTokenRefresh()
                    if (refreshed) {
                        response.close()
                        val retry = original.newBuilder()
                            .method(original.method, original.body)
                            .header("Authorization", "Bearer $authToken")
                            .build()
                        response = chain.proceed(retry)
                    } else if (handlingAuthFailure.compareAndSet(false, true)) {
                        Log.w("NetworkModule", "Received 401 Unauthorized - token expired and refresh failed")
                        clearAuthentication()
                        onAuthenticationFailure?.invoke()
                    }
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

        // Create Gson with explicit UUID adapter for safe error handling
        val gson = GsonBuilder()
            .registerTypeAdapter(UUID::class.java, object : TypeAdapter<UUID>() {
                override fun write(out: JsonWriter, value: UUID?) {
                    out.value(value?.toString())
                }

                override fun read(reader: JsonReader): UUID? {
                    return try {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                            Log.w("NetworkModule", "Received null/blank UUID, using empty UUID")
                            return UUID(0, 0)
                        }
                        val str = reader.nextString()
                        if (str.isNullOrBlank()) {
                            Log.w("NetworkModule", "Received null/blank UUID, using empty UUID")
                            UUID(0, 0)
                        } else {
                            UUID.fromString(str)
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e("NetworkModule", "Invalid UUID format: ${e.message}, using empty UUID")
                        UUID(0, 0)
                    } catch (e: IllegalStateException) {
                        Log.e("NetworkModule", "Invalid UUID token: ${e.message}, using empty UUID")
                        UUID(0, 0)
                    }
                }
            })
            .create()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        musicApi = retrofit?.create(MusicApi::class.java)
        scrobbleApi = retrofit?.create(ScrobbleApi::class.java)
    }

    private fun attemptTokenRefresh(): Boolean {
        synchronized(refreshLock) {
            if (refreshToken.isEmpty() || retrofit == null) return false
            return try {
                val api = retrofit!!.create(MusicApi::class.java)
                val refreshResponse = runBlocking {
                    api.refresh(RefreshRequest(refreshToken), skipAuthHeader = true)
                }
                setTokens(refreshResponse.token, refreshResponse.refreshToken)
                onTokenUpdated?.invoke(refreshResponse.token, refreshResponse.refreshToken)
                true
            } catch (e: Exception) {
                Log.e("NetworkModule", "Token refresh failed", e)
                false
            }
        }
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
