package com.melodee.autoplayer.data.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class ScrobbleRequest(
    val songId: String,
    val playerName: String = "MelodeePlayer",
    @Deprecated("Use scrobbleTypeValue")
    val scrobbleType: String? = null,
    val timestamp: Double,
    val playedDuration: Double,
    val scrobbleTypeValue: ScrobbleRequestType
)

@JsonAdapter(ScrobbleRequestTypeAdapter::class)
enum class ScrobbleRequestType(val value: Int) {
    UNKNOWN(0),
    NOW_PLAYING(1),
    PLAYED(2)
}

class ScrobbleRequestTypeAdapter : TypeAdapter<ScrobbleRequestType>() {
    override fun write(out: JsonWriter, value: ScrobbleRequestType) {
        out.value(value.value)
    }

    override fun read(reader: JsonReader): ScrobbleRequestType {
        val intValue = reader.nextInt()
        return ScrobbleRequestType.values().firstOrNull { it.value == intValue } ?: ScrobbleRequestType.UNKNOWN
    }
}

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
    @POST("api/v1/scrobble")
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
