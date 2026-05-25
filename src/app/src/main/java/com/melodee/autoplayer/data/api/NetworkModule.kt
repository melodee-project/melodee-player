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
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
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
    private var refreshApi: MusicApi? = null
    
    // Thread-safe locks for configuration changes
    private val configLock = ReentrantReadWriteLock()
    
    // Callback for handling authentication failures
    @Volatile
    private var onAuthenticationFailure: (() -> Unit)? = null
    @Volatile
    private var onTokenUpdated: ((String, String, String) -> Unit)? = null
    
    // Thread-safe flag to prevent multiple 401 callbacks
    private val handlingAuthFailure = AtomicBoolean(false)
    private val refreshLock = Any()

    private enum class TokenRefreshResult {
        SUCCESS,
        INVALID_CREDENTIALS,
        TRANSIENT_FAILURE
    }

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

    fun setTokenUpdateCallback(callback: (newAccessToken: String, newRefreshToken: String, refreshTokenExpiresAt: String) -> Unit) {
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
        val gson = createGson()
        refreshApi = createRefreshApi(gson)

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
                val currentAuthToken = configLock.read { authToken }

                if (!skipAuth && currentAuthToken.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $currentAuthToken")
                }

                chain.proceed(requestBuilder.build())
            }
            .authenticator(TokenAuthenticator())
            // Retry with simple exponential backoff for idempotent requests and 429/5xx
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .addInterceptor(createLoggingInterceptor())
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
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        musicApi = retrofit?.create(MusicApi::class.java)
        scrobbleApi = retrofit?.create(ScrobbleApi::class.java)
    }

    private fun createRefreshApi(gson: com.google.gson.Gson): MusicApi? {
        if (baseUrl.isEmpty()) return null

        val refreshClient = OkHttpClient.Builder()
            .addInterceptor(createLoggingInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(refreshClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MusicApi::class.java)
    }

    private fun createGson(): com.google.gson.Gson {
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

        return gson
    }

    private fun attemptTokenRefresh(): TokenRefreshResult {
        synchronized(refreshLock) {
            val currentRefreshToken = configLock.read { refreshToken }
            val api = refreshApi
            if (currentRefreshToken.isEmpty() || api == null) return TokenRefreshResult.INVALID_CREDENTIALS
            return try {
                val refreshResponse = runBlocking {
                    api.refresh(RefreshRequest(currentRefreshToken), skipAuthHeader = true)
                }
                val newRefreshToken = refreshResponse.refreshToken.ifBlank { currentRefreshToken }
                setTokens(refreshResponse.token, newRefreshToken)
                onTokenUpdated?.invoke(
                    refreshResponse.token,
                    newRefreshToken,
                    refreshResponse.refreshTokenExpiresAt
                )
                TokenRefreshResult.SUCCESS
            } catch (e: HttpException) {
                if (e.code() == 400 || e.code() == 401 || e.code() == 403) {
                    Log.e("NetworkModule", "Token refresh rejected by server with HTTP ${e.code()}")
                    TokenRefreshResult.INVALID_CREDENTIALS
                } else {
                    Log.e("NetworkModule", "Token refresh failed with recoverable HTTP ${e.code()}", e)
                    TokenRefreshResult.TRANSIENT_FAILURE
                }
            } catch (e: IOException) {
                Log.e("NetworkModule", "Token refresh failed due to network error; credentials retained", e)
                TokenRefreshResult.TRANSIENT_FAILURE
            } catch (e: Exception) {
                Log.e("NetworkModule", "Token refresh failed unexpectedly; credentials retained", e)
                TokenRefreshResult.TRANSIENT_FAILURE
            }
        }
    }

    private class TokenAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.header("X-Refresh-Request") == "true") return null
            if (responseCount(response) > 1) return null

            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentToken = configLock.read { authToken }
            if (currentToken.isNotEmpty() && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            return when (attemptTokenRefresh()) {
                TokenRefreshResult.SUCCESS -> {
                    val refreshedToken = configLock.read { authToken }
                    if (refreshedToken.isEmpty()) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header("Authorization", "Bearer $refreshedToken")
                            .build()
                    }
                }
                TokenRefreshResult.INVALID_CREDENTIALS -> {
                    if (handlingAuthFailure.compareAndSet(false, true)) {
                        Log.w("NetworkModule", "Received 401 Unauthorized - refresh token is invalid or expired")
                        clearAuthentication()
                        onAuthenticationFailure?.invoke()
                    }
                    null
                }
                TokenRefreshResult.TRANSIENT_FAILURE -> {
                    Log.w("NetworkModule", "Received 401 Unauthorized, but token refresh failed transiently; keeping stored authentication for retry")
                    null
                }
            }
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }
    }

    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (isDebuggable()) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private fun isDebuggable(): Boolean {
        val flags = appContext?.applicationInfo?.flags ?: return false
        return flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
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
