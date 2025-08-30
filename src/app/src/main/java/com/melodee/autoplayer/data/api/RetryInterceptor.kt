package com.melodee.autoplayer.data.api

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * OkHttp interceptor that retries requests with exponential backoff and jitter.
 *
 * - Retries idempotent requests (GET/HEAD/OPTIONS) on network errors and 5xx/429 responses.
 * - Retries non-idempotent requests only on 429 (Too Many Requests).
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 300,
    private val maxDelayMs: Long = 3_000
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var tryCount = 0
        val request = chain.request()
        val method = request.method.uppercase()

        // Helper to decide if we should retry for this method/status
        fun shouldRetry(responseCode: Int?, error: IOException?): Boolean {
            val idempotent = method == "GET" || method == "HEAD" || method == "OPTIONS"

            // Retry on 429 (rate limiting) for all methods
            if (responseCode == 429) return true

            // Retry on 5xx for idempotent requests
            if (responseCode != null && responseCode in 500..599 && idempotent) return true

            // Retry on certain IO/network issues for idempotent requests
            if (error != null && idempotent) return true

            return false
        }

        while (true) {
            try {
                val response = chain.proceed(request)

                if (tryCount >= maxRetries || !shouldRetry(response.code, null)) {
                    return response
                }

                response.close()
            } catch (e: IOException) {
                if (tryCount >= maxRetries || !shouldRetry(null, e)) {
                    throw e
                }
            }

            // Exponential backoff with jitter
            tryCount += 1
            val exponential = baseDelayMs * (1L shl (tryCount - 1))
            val capped = min(exponential, maxDelayMs)
            val jitter = Random.nextLong(0, capped / 2 + 1)
            val delay = capped / 2 + jitter
            try {
                TimeUnit.MILLISECONDS.sleep(delay)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Retry interrupted", ie)
            }
        }
    }
}

