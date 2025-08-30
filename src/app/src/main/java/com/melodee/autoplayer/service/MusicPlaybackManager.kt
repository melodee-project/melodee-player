package com.melodee.autoplayer.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

/**
 * Consolidated music playback manager that combines queue, playlist, and player management
 * Replaces separate QueueManager, PlaylistManager, and PlayerManager classes
 */
class MusicPlaybackManager(private val context: Context) {
    
    enum class RepeatMode {
        NONE,    // No repeat
        ONE,     // Repeat current song
        ALL      // Repeat entire queue
    }
    
    enum class PlaybackContext {
        PLAYLIST,    // Playing from a playlist
        SEARCH,      // Playing from search results
        SINGLE_SONG  // Playing a single song
    }
    
    // Player state
    private var exoPlayer: ExoPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _currentDuration = MutableStateFlow(0L)
    val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    
    // Queue and playlist state
    private val _originalQueue = MutableStateFlow<List<Song>>(emptyList())
    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    
    private val _playHistory = MutableStateFlow<List<Song>>(emptyList())
    val playHistory: StateFlow<List<Song>> = _playHistory.asStateFlow()
    
    private val _playbackContext = MutableStateFlow(PlaybackContext.SINGLE_SONG)
    val playbackContext: StateFlow<PlaybackContext> = _playbackContext.asStateFlow()
    
    // Shuffle state
    private var shuffledIndices: List<Int> = emptyList()
    private var currentShuffleIndex = -1
    
    fun initializePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val mediaSourceFactory = MediaCache.mediaSourceFactory(context)
            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().also { player ->
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        Logger.logPerformance("MusicPlaybackManager", "PlaybackState", if (isPlaying) "Playing" else "Paused")
                    }
                    
                    // Position updates handled elsewhere
                    
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        Logger.d("MusicPlaybackManager", "PlayWhenReady changed: $playWhenReady, reason: $reason")
                    }
                })
            }
        }
        return exoPlayer!!
    }
    
    fun getPlayer(): ExoPlayer? = exoPlayer
    
    fun setQueue(songs: List<Song>, startIndex: Int = 0, context: PlaybackContext = PlaybackContext.PLAYLIST) {
        Logger.d("MusicPlaybackManager", "Setting queue with ${songs.size} songs, startIndex: $startIndex, context: $context")
        
        _originalQueue.value = songs
        _currentQueue.value = songs
        _playbackContext.value = context
        
        if (songs.isNotEmpty()) {
            // Ensure startIndex is valid - if invalid, default to 0
            val validStartIndex = when {
                startIndex < 0 -> 0
                startIndex >= songs.size -> 0
                else -> startIndex
            }
            
            _currentIndex.value = validStartIndex
            _currentSong.value = songs[validStartIndex]
            Logger.d("MusicPlaybackManager", "Set current song to index $validStartIndex: ${songs[validStartIndex].title}")
            
            // If shuffle is enabled, create new shuffle order starting with the selected song
            if (_isShuffleEnabled.value) {
                createShuffleOrder(validStartIndex)
            }
        } else {
            _currentIndex.value = -1
            _currentSong.value = null
            Logger.d("MusicPlaybackManager", "Empty queue - no current song set")
        }
        
        Logger.logPerformance("MusicPlaybackManager", "QueueSize", songs.size)
    }
    
    fun playSong(song: Song): Boolean {
        val index = _currentQueue.value.indexOf(song)
        return if (index >= 0) {
            _currentIndex.value = index
            _currentSong.value = song
            addToHistory(song)
            
            exoPlayer?.let { player ->
                val mediaItem = MediaItem.fromUri(song.streamUrl)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                _currentDuration.value = song.durationMs.toLong()
            }
            
            Logger.d("MusicPlaybackManager", "Playing song: ${song.title} at index $index")
            Logger.logPerformance("MusicPlaybackManager", "SongPlayed", song.title)
            true
        } else {
            Logger.w("MusicPlaybackManager", "Song not found in current queue: ${song.title}")
            false
        }
    }
    
    fun playNext(): Boolean {
        val nextSong = getNextSong()
        return if (nextSong != null) {
            playSong(nextSong)
        } else {
            Logger.d("MusicPlaybackManager", "No next song available")
            false
        }
    }
    
    fun playPrevious(): Boolean {
        val previousSong = getPreviousSong()
        return if (previousSong != null) {
            playSong(previousSong)
        } else {
            Logger.d("MusicPlaybackManager", "No previous song available")
            false
        }
    }
    
    private fun getNextSong(): Song? {
        val queue = _currentQueue.value
        val currentIdx = _currentIndex.value
        
        if (queue.isEmpty() || currentIdx < 0) return null
        
        return when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Repeat current song
                queue[currentIdx]
            }
            RepeatMode.ALL -> {
                if (_isShuffleEnabled.value) {
                    getNextShuffledSong()
                } else {
                    val nextIndex = (currentIdx + 1) % queue.size
                    _currentIndex.value = nextIndex
                    queue[nextIndex]
                }
            }
            RepeatMode.NONE -> {
                if (_isShuffleEnabled.value) {
                    getNextShuffledSong()
                } else {
                    val nextIndex = currentIdx + 1
                    if (nextIndex < queue.size) {
                        _currentIndex.value = nextIndex
                        queue[nextIndex]
                    } else null
                }
            }
        }
    }
    
    private fun getPreviousSong(): Song? {
        val queue = _currentQueue.value
        val currentIdx = _currentIndex.value
        
        if (queue.isEmpty() || currentIdx < 0) return null
        
        return if (_isShuffleEnabled.value) {
            getPreviousShuffledSong()
        } else {
            val prevIndex = if (currentIdx > 0) currentIdx - 1 else {
                if (_repeatMode.value == RepeatMode.ALL) queue.size - 1 else -1
            }
            if (prevIndex >= 0) {
                _currentIndex.value = prevIndex
                queue[prevIndex]
            } else null
        }
    }
    
    fun toggleShuffle() {
        val newShuffleState = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffleState
        
        if (newShuffleState) {
            createShuffleOrder(_currentIndex.value)
        } else {
            shuffledIndices = emptyList()
            currentShuffleIndex = -1
        }
        
        Logger.d("MusicPlaybackManager", "Shuffle toggled: $newShuffleState")
    }
    
    fun toggleRepeat() {
        val newRepeatMode = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        _repeatMode.value = newRepeatMode
        Logger.d("MusicPlaybackManager", "Repeat mode changed: $newRepeatMode")
    }
    
    private fun createShuffleOrder(startIndex: Int) {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return
        
        val indices = queue.indices.toMutableList()
        
        // Remove current song from shuffle and put it first
        if (startIndex in indices) {
            indices.removeAt(startIndex)
            indices.shuffle()
            shuffledIndices = listOf(startIndex) + indices
            currentShuffleIndex = 0
        }
    }
    
    private fun getNextShuffledSong(): Song? {
        if (shuffledIndices.isEmpty()) return null
        
        val nextShuffleIndex = when (_repeatMode.value) {
            RepeatMode.ONE -> currentShuffleIndex
            RepeatMode.ALL -> (currentShuffleIndex + 1) % shuffledIndices.size
            RepeatMode.NONE -> {
                val next = currentShuffleIndex + 1
                if (next < shuffledIndices.size) next else -1
            }
        }
        
        return if (nextShuffleIndex >= 0) {
            currentShuffleIndex = nextShuffleIndex
            val queueIndex = shuffledIndices[nextShuffleIndex]
            _currentIndex.value = queueIndex
            _currentQueue.value[queueIndex]
        } else null
    }
    
    private fun getPreviousShuffledSong(): Song? {
        if (shuffledIndices.isEmpty()) return null
        
        val prevShuffleIndex = if (currentShuffleIndex > 0) {
            currentShuffleIndex - 1
        } else {
            if (_repeatMode.value == RepeatMode.ALL) shuffledIndices.size - 1 else -1
        }
        
        return if (prevShuffleIndex >= 0) {
            currentShuffleIndex = prevShuffleIndex
            val queueIndex = shuffledIndices[prevShuffleIndex]
            _currentIndex.value = queueIndex
            _currentQueue.value[queueIndex]
        } else null
    }
    
    private fun addToHistory(song: Song) {
        val currentHistory = _playHistory.value.toMutableList()
        
        // Remove song if already in history to avoid duplicates
        currentHistory.removeAll { it.id == song.id }
        
        // Add to front
        currentHistory.add(0, song)
        
        // Keep only last 50 songs
        if (currentHistory.size > 50) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _playHistory.value = currentHistory
    }
    
    fun play() {
        exoPlayer?.play()
    }
    
    fun pause() {
        exoPlayer?.pause()
    }
    
    fun stop() {
        exoPlayer?.stop()
        _isPlaying.value = false
        _currentPosition.value = 0L
    }
    
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _currentPosition.value = position
    }
    
    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    
    fun getDuration(): Long = exoPlayer?.duration ?: 0L
    
    fun clearQueue() {
        _originalQueue.value = emptyList()
        _currentQueue.value = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
        _playbackContext.value = PlaybackContext.SINGLE_SONG
        shuffledIndices = emptyList()
        currentShuffleIndex = -1
        
        exoPlayer?.stop()
        _isPlaying.value = false
        _currentPosition.value = 0L
        _currentDuration.value = 0L
        
        Logger.d("MusicPlaybackManager", "Queue cleared")
    }
    
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        Logger.d("MusicPlaybackManager", "Player released")
    }
}
