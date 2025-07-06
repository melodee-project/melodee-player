package com.melodee.autoplayer.data.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private var baseUrl: String = ""
    private var authToken: String = ""
    private var retrofit: Retrofit? = null
    private var musicApi: MusicApi? = null
    private var scrobbleApi: ScrobbleApi? = null
    
    // Callback for handling authentication failures
    private var onAuthenticationFailure: (() -> Unit)? = null

    fun setBaseUrl(url: String) {
        if (baseUrl != url) {
            baseUrl = url
            createRetrofitInstance()
        }
    }

    fun setAuthToken(token: String) {
        authToken = token
        // Recreate the Retrofit instance to update the auth token
        createRetrofitInstance()
    }

    fun getAuthToken(): String? {
        return if (authToken.isNotEmpty()) authToken else null
    }
    
    // Set callback for authentication failures
    fun setAuthenticationFailureCallback(callback: () -> Unit) {
        onAuthenticationFailure = callback
    }
    
    // Clear authentication
    fun clearAuthentication() {
        authToken = ""
        createRetrofitInstance()
    }
    
    // Check if authenticated
    fun isAuthenticated(): Boolean {
        return authToken.isNotEmpty() && baseUrl.isNotEmpty()
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
                if (response.code == 401) {
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