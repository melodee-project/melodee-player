package com.melodee.autoplayer.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

data class ScrobbleRequest(
    val songId: String,
    val userId: String,
    val scrobbleType: String, // "nowPlaying" or "played"
    val timestamp: Long,
    val playerName: String = "MelodeePlayer",
    val playedDuration: Long? = null // Duration played in milliseconds
)

data class ScrobbleResponse(
    val message: String? = null
)

data class ScrobbleErrorResponse(
    val type: String,
    val title: String,
    val status: Int,
    val traceId: String
)

// Sealed class to represent either success or error response
sealed class ScrobbleResult {
    data class Success(val response: ScrobbleResponse) : ScrobbleResult()
    data class Error(val errorResponse: ScrobbleErrorResponse, val httpStatus: Int) : ScrobbleResult()
    data class NetworkError(val exception: Throwable) : ScrobbleResult()
}

interface ScrobbleApi {
    @POST("scrobble")
    suspend fun scrobble(@Body request: ScrobbleRequest): Response<Void>
}

// Extension function to handle response parsing
fun Response<Void>.toScrobbleResult(): ScrobbleResult {
    return try {
        if (isSuccessful) {
            // For successful responses, create an empty ScrobbleResponse since body is empty
            ScrobbleResult.Success(ScrobbleResponse())
        } else {
            val errorBody = errorBody()?.string()
            if (errorBody != null) {
                try {
                    val gson = Gson()
                    val errorResponse = gson.fromJson(errorBody, ScrobbleErrorResponse::class.java)
                    ScrobbleResult.Error(errorResponse, code())
                } catch (e: JsonSyntaxException) {
                    // If we can't parse the error response, create a generic one
                    val genericError = ScrobbleErrorResponse(
                        type = "unknown",
                        title = "HTTP Error ${code()}",
                        status = code(),
                        traceId = "unknown"
                    )
                    ScrobbleResult.Error(genericError, code())
                }
            } else {
                // No error body, create a generic error
                val genericError = ScrobbleErrorResponse(
                    type = "unknown",
                    title = "HTTP Error ${code()}",
                    status = code(),
                    traceId = "unknown"
                )
                ScrobbleResult.Error(genericError, code())
            }
        }
    } catch (e: Exception) {
        ScrobbleResult.NetworkError(e)
    }
} 