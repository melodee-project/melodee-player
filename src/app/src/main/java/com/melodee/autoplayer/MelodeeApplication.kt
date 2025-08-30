package com.melodee.autoplayer

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import com.melodee.autoplayer.data.AuthenticationManager
import com.melodee.autoplayer.data.api.NetworkModule
import java.util.concurrent.TimeUnit

class MelodeeApplication : Application(), ImageLoaderFactory {
    
    // Global authentication manager
    lateinit var authenticationManager: AuthenticationManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        Log.i("MelodeeApplication", "=== APPLICATION STARTUP ===")
        Log.i("MelodeeApplication", "Application onCreate")
        
        // Initialize authentication manager early
        Log.i("MelodeeApplication", "Initializing AuthenticationManager...")
        authenticationManager = AuthenticationManager(this)

        // Initialize NetworkModule to enable HTTP cache and auth failure handling
        NetworkModule.init(this)
        NetworkModule.setAuthenticationFailureCallback {
            Log.w("MelodeeApplication", "Authentication failure detected by NetworkModule")
            // Potential hook: route to login screen via a global event bus
        }
        
        Log.i("MelodeeApplication", "Authentication manager initialized")
        Log.i("MelodeeApplication", "=== APPLICATION STARTUP COMPLETE ===")
    }
    override fun newImageLoader(): ImageLoader {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val response = chain.proceed(request)
                        Log.d("OkHttp", "Response headers for ${request.url}: ${response.headers}")
                        response
                    }
                    .addInterceptor(loggingInterceptor)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
} 
