package com.melodee.autoplayer.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.melodee.autoplayer.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerManager(private val context: Context) {
    
    private var player: ExoPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _currentDuration = MutableStateFlow(0L)
    val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    
    fun initializePlayer(): ExoPlayer {
        return ExoPlayer.Builder(context).build().also { exoPlayer ->
            player = exoPlayer
            exoPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    // Handle player errors
                }
            })
        }
    }
    
    fun playSong(song: Song) {
        player?.let { exoPlayer ->
            val mediaItem = MediaItem.fromUri(song.streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            _currentDuration.value = song.durationMs.toLong()
        }
    }
    
    fun play() {
        player?.play()
    }
    
    fun pause() {
        player?.pause()
    }
    
    fun stop() {
        player?.stop()
    }
    
    fun seekTo(position: Long) {
        player?.seekTo(position)
    }
    
    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }
    
    fun getDuration(): Long {
        return player?.duration ?: 0L
    }
    
    fun isCurrentlyPlaying(): Boolean {
        return player?.isPlaying ?: false
    }
    
    fun updatePosition() {
        _currentPosition.value = getCurrentPosition()
    }
    
    fun release() {
        player?.release()
        player = null
    }
} 