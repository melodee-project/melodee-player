package com.melodee.autoplayer.service

import android.util.Log
import com.melodee.autoplayer.data.api.ScrobbleApi
import com.melodee.autoplayer.data.api.ScrobbleRequest
import com.melodee.autoplayer.data.api.ScrobbleResult
import com.melodee.autoplayer.data.api.ScrobbleRequestType
import com.melodee.autoplayer.data.api.toScrobbleResult
import com.melodee.autoplayer.domain.model.Song
import kotlinx.coroutines.*
import kotlin.math.min
import java.util.concurrent.ConcurrentHashMap

class ScrobbleManager(
    private val scrobbleApi: ScrobbleApi
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeScrobbles = ConcurrentHashMap<String, ScrobbleTracker>()
    private val defaultPlayerName = "MelodeePlayer"
    
    companion object {
        private const val TAG = "ScrobbleManager"
        private const val NOW_PLAYING_THRESHOLD_MS = 10_000L // 10 seconds
        private const val PLAYED_THRESHOLD_PERCENT = 0.5f // 50%
        private const val MIN_SONG_DURATION_MS = 30_000L // 30 seconds minimum to scrobble
    }
    
    data class ScrobbleTracker(
        val song: Song,
        val startTime: Long,
        var nowPlayingScrobbled: Boolean = false,
        var playedScrobbled: Boolean = false,
        var job: Job? = null
    )
    
    fun startTracking(song: Song, duration: Long) {
        Log.d(TAG, "Starting scrobble tracking for song: ${song.title} (${song.id})")
        
        // Don't track songs that are too short
        if (duration < MIN_SONG_DURATION_MS) {
            Log.d(TAG, "Song too short to scrobble: ${duration}ms")
            return
        }
        
        // Cancel any existing tracking for this song
        stopTracking(song.id.toString())
        
        val tracker = ScrobbleTracker(
            song = song,
            startTime = System.currentTimeMillis()
        )
        
        // Start tracking job
        tracker.job = scope.launch {
            try {
                // Wait for "now playing" threshold
                delay(NOW_PLAYING_THRESHOLD_MS)
                
                if (isActive && !tracker.nowPlayingScrobbled) {
                    scrobbleNowPlaying(tracker)
                }
                
                // Calculate when to scrobble as "played" (50% of song)
                val playedThresholdMs = (duration * PLAYED_THRESHOLD_PERCENT).toLong()
                val remainingDelay = playedThresholdMs - NOW_PLAYING_THRESHOLD_MS
                
                if (remainingDelay > 0) {
                    delay(remainingDelay)
                    
                    if (isActive && !tracker.playedScrobbled) {
                        scrobblePlayed(tracker, duration)
                    }
                }
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Scrobble tracking cancelled for song: ${song.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in scrobble tracking for song: ${song.title}", e)
            }
        }
        
        activeScrobbles[song.id.toString()] = tracker
    }
    
    fun stopTracking(songId: String) {
        activeScrobbles[songId]?.let { tracker ->
            Log.d(TAG, "Stopping scrobble tracking for song: ${tracker.song.title}")
            tracker.job?.cancel()
            activeScrobbles.remove(songId)
        }
    }
    
    fun stopAllTracking() {
        Log.d(TAG, "Stopping all scrobble tracking")
        activeScrobbles.values.forEach { tracker ->
            tracker.job?.cancel()
        }
        activeScrobbles.clear()
    }
    
    fun updatePlaybackPosition(songId: String, position: Long, duration: Long) {
        activeScrobbles[songId]?.let { tracker ->
            // Check if we should scrobble "played" based on current position
            if (!tracker.playedScrobbled && position >= duration * PLAYED_THRESHOLD_PERCENT) {
                scope.launch {
                    scrobblePlayed(tracker, duration)
                }
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private suspend fun scrobbleNowPlaying(tracker: ScrobbleTracker) {
        if (tracker.nowPlayingScrobbled) return
        
        try {
            Log.d(TAG, "Scrobbling 'nowPlaying' for song: ${tracker.song.title}")
            
            val request = ScrobbleRequest(
                songId = tracker.song.id.toString(),
                playerName = defaultPlayerName,
                scrobbleType = "nowPlaying",
                timestamp = (tracker.startTime / 1000.0),
                playedDuration = 0.0,
                scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING
            )
            
            // Log the scrobble request values
            Log.i(TAG, "Scrobble Request (nowPlaying): songId=${request.songId}, scrobbleType=${request.scrobbleType}, scrobbleTypeValue=${request.scrobbleTypeValue}, timestamp=${request.timestamp}, playerName=${request.playerName}, playedDuration=${request.playedDuration}")
            
            val response = scrobbleApi.scrobble(request)
            val result = response.toScrobbleResult()
            
            when (result) {
                is ScrobbleResult.Success -> {
                    tracker.nowPlayingScrobbled = true
                    Log.d(TAG, "Successfully scrobbled 'nowPlaying' for song: ${tracker.song.title}")
                    result.response.message?.let { message ->
                        Log.d(TAG, "Server message: $message")
                    }
                }
                is ScrobbleResult.Error -> {
                    Log.e(TAG, "Failed to scrobble 'nowPlaying' for song: ${tracker.song.title}. " +
                            "Status: ${result.httpStatus}, Title: ${result.errorResponse.title}, " +
                            "Type: ${result.errorResponse.type}, TraceId: ${result.errorResponse.traceId}")
                }
                is ScrobbleResult.NetworkError -> {
                    Log.e(TAG, "Network error scrobbling 'nowPlaying' for song: ${tracker.song.title}", result.exception)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scrobbling 'nowPlaying' for song: ${tracker.song.title}", e)
        }
    }
    
    @Suppress("DEPRECATION")
    private suspend fun scrobblePlayed(tracker: ScrobbleTracker, duration: Long) {
        if (tracker.playedScrobbled) return
        
        try {
            Log.d(TAG, "Scrobbling 'played' for song: ${tracker.song.title}")
            
            val playedDuration = min(System.currentTimeMillis() - tracker.startTime, duration)
            
            val request = ScrobbleRequest(
                songId = tracker.song.id.toString(),
                playerName = defaultPlayerName,
                scrobbleType = "played",
                timestamp = (System.currentTimeMillis() / 1000.0),
                playedDuration = (playedDuration / 1000.0),
                scrobbleTypeValue = ScrobbleRequestType.PLAYED
            )
            
            // Log the scrobble request values
            Log.i(TAG, "Scrobble Request (played): songId=${request.songId}, scrobbleType=${request.scrobbleType}, scrobbleTypeValue=${request.scrobbleTypeValue}, timestamp=${request.timestamp}, playerName=${request.playerName}, playedDuration=${request.playedDuration}")
            
            val response = scrobbleApi.scrobble(request)
            val result = response.toScrobbleResult()
            
            when (result) {
                is ScrobbleResult.Success -> {
                    tracker.playedScrobbled = true
                    Log.d(TAG, "Successfully scrobbled 'played' for song: ${tracker.song.title}")
                    result.response.message?.let { message ->
                        Log.d(TAG, "Server message: $message")
                    }
                }
                is ScrobbleResult.Error -> {
                    Log.e(TAG, "Failed to scrobble 'played' for song: ${tracker.song.title}. " +
                            "Status: ${result.httpStatus}, Title: ${result.errorResponse.title}, " +
                            "Type: ${result.errorResponse.type}, TraceId: ${result.errorResponse.traceId}")
                }
                is ScrobbleResult.NetworkError -> {
                    Log.e(TAG, "Network error scrobbling 'played' for song: ${tracker.song.title}", result.exception)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scrobbling 'played' for song: ${tracker.song.title}", e)
        }
    }
    
    fun destroy() {
        Log.d(TAG, "Destroying ScrobbleManager")
        stopAllTracking()
        scope.cancel()
    }
} 
