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
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $authToken")
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
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

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