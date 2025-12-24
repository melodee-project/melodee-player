package com.melodee.autoplayer.service

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.melodee.autoplayer.data.api.ScrobbleRequest
import com.melodee.autoplayer.data.api.ScrobbleRequestType
import org.junit.Test

class ScrobbleRequestTest {
    private val gson = Gson()

    @Test
    fun `scrobble request converts timestamp from milliseconds to seconds`() {
        val timestampMs = 1703462400000L
        val expectedSeconds = 1703462400.0

        val request = ScrobbleRequest(
            songId = "test-song-id",
            playerName = "MelodeePlayer",
            timestamp = (timestampMs / 1000.0),
            playedDuration = 0.0,
            scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING
        )

        assertThat(request.timestamp).isEqualTo(expectedSeconds)

        val json = gson.toJson(request)
        val jsonObject = JsonParser.parseString(json).asJsonObject

        assertThat(jsonObject.get("timestamp").asDouble).isEqualTo(expectedSeconds)
        assertThat(jsonObject.get("timestamp").asDouble).isLessThan(2000000000.0)
    }

    @Test
    fun `scrobble request converts playedDuration from milliseconds to seconds`() {
        val durationMs = 180000L
        val expectedSeconds = 180.0

        val request = ScrobbleRequest(
            songId = "test-song-id",
            playerName = "MelodeePlayer",
            timestamp = (System.currentTimeMillis() / 1000.0),
            playedDuration = (durationMs / 1000.0),
            scrobbleTypeValue = ScrobbleRequestType.PLAYED
        )

        assertThat(request.playedDuration).isEqualTo(expectedSeconds)

        val json = gson.toJson(request)
        val jsonObject = JsonParser.parseString(json).asJsonObject

        assertThat(jsonObject.get("playedDuration").asDouble).isEqualTo(expectedSeconds)
    }

    @Test
    fun `timestamp is in reasonable range for current dates`() {
        val currentTimeMs = System.currentTimeMillis()
        val timestampSeconds = currentTimeMs / 1000.0

        val year2020Seconds = 1577836800.0
        val year2030Seconds = 1893456000.0

        assertThat(timestampSeconds).isGreaterThan(year2020Seconds)
        assertThat(timestampSeconds).isLessThan(year2030Seconds)
    }
}
