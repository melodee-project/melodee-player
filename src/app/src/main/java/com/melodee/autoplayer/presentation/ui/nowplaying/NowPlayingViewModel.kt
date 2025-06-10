package com.melodee.autoplayer.presentation.ui.nowplaying

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.service.MusicService
import com.melodee.autoplayer.service.QueueManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

class NowPlayingViewModel : ViewModel() {
    private var context: Context? = null
    private var musicService: MusicService? = null
    private var bound = false
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
            Log.d("NowPlayingViewModel", "Service connected")
            
            // Start observing service state
            observeServiceState()
            
            if (isPlaying.value) {
                startProgressUpdates()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            musicService = null
            bound = false
            stopProgressUpdates()
            Log.d("NowPlayingViewModel", "Service disconnected")
        }
    }
    
    // Playback state
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()
    
    private val _currentDuration = MutableStateFlow(0L)
    val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    // Queue state
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    // Shuffle and repeat state
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(QueueManager.RepeatMode.NONE)
    val repeatMode: StateFlow<QueueManager.RepeatMode> = _repeatMode.asStateFlow()
    
    private var progressUpdateJob: Job? = null
    
    fun setContext(context: Context) {
        this.context = context
        // Bind to MusicService
        Intent(context, MusicService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }
    
    private fun observeServiceState() {
        viewModelScope.launch {
            musicService?.let { service ->
                // Observe playlist manager state
                service.getPlaylistManager().currentSong.collect { song ->
                    _currentSong.value = song
                    Log.d("NowPlayingViewModel", "Current song updated: ${song?.title}")
                }
            }
        }
        
        viewModelScope.launch {
            musicService?.let { service ->
                // Observe queue manager state
                service.getQueueManager().currentQueue.collect { songs ->
                    _queue.value = songs
                    Log.d("NowPlayingViewModel", "Queue updated: ${songs.size} songs")
                }
            }
        }
        
        viewModelScope.launch {
            musicService?.let { service ->
                service.getQueueManager().currentIndex.collect { index ->
                    _currentIndex.value = index
                    Log.d("NowPlayingViewModel", "Current index updated: $index")
                }
            }
        }
        
        viewModelScope.launch {
            musicService?.let { service ->
                service.getQueueManager().isShuffleEnabled.collect { enabled ->
                    _isShuffleEnabled.value = enabled
                    Log.d("NowPlayingViewModel", "Shuffle updated: $enabled")
                }
            }
        }
        
        viewModelScope.launch {
            musicService?.let { service ->
                service.getQueueManager().repeatMode.collect { mode ->
                    _repeatMode.value = mode
                    Log.d("NowPlayingViewModel", "Repeat mode updated: $mode")
                }
            }
        }
    }
    
    fun togglePlayPause() {
        Log.d("NowPlayingViewModel", "Toggle play/pause")
        _isPlaying.value = !_isPlaying.value
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = if (_isPlaying.value) {
                    MusicService.ACTION_RESUME
                } else {
                    MusicService.ACTION_PAUSE
                }
            }
            ctx.startService(intent)
        }
        
        if (_isPlaying.value) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }
    
    fun skipToNext() {
        Log.d("NowPlayingViewModel", "Skip to next")
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            ctx.startService(intent)
        }
    }
    
    fun skipToPrevious() {
        Log.d("NowPlayingViewModel", "Skip to previous")
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            ctx.startService(intent)
        }
    }
    
    fun seekTo(position: Long) {
        Log.d("NowPlayingViewModel", "Seek to: $position")
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_SEEK_TO
                putExtra(MusicService.EXTRA_POSITION, position)
            }
            ctx.startService(intent)
        }
    }
    
    fun toggleShuffle() {
        Log.d("NowPlayingViewModel", "Toggle shuffle")
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_TOGGLE_SHUFFLE
            }
            ctx.startService(intent)
        }
    }
    
    fun toggleRepeat() {
        Log.d("NowPlayingViewModel", "Toggle repeat")
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_TOGGLE_REPEAT
            }
            ctx.startService(intent)
        }
    }
    
    fun playSong(song: Song) {
        Log.d("NowPlayingViewModel", "Play song: ${song.title}")
        _currentSong.value = song
        _isPlaying.value = true
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY_SONG
                putExtra(MusicService.EXTRA_SONG, song)
            }
            ctx.startService(intent)
        }
        startProgressUpdates()
    }
    
    fun removeFromQueue(index: Int) {
        Log.d("NowPlayingViewModel", "Remove from queue at index: $index")
        musicService?.getQueueManager()?.removeFromQueue(index)
    }
    
    fun addToQueue(song: Song) {
        Log.d("NowPlayingViewModel", "Add to queue: ${song.title}")
        musicService?.getQueueManager()?.addToQueue(song)
    }
    
    fun clearQueue() {
        Log.d("NowPlayingViewModel", "Clear queue")
        musicService?.getQueueManager()?.clearQueue()
    }
    
    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        Log.d("NowPlayingViewModel", "Set queue with ${songs.size} songs")
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_SET_PLAYLIST
                putParcelableArrayListExtra(MusicService.EXTRA_PLAYLIST, ArrayList(songs))
                putExtra("START_INDEX", startIndex)
            }
            ctx.startService(intent)
        }
    }
    
    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        Log.d("NowPlayingViewModel", "Starting progress updates")
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                musicService?.let { service ->
                    val duration = service.getDuration()
                    val position = service.getCurrentPosition()
                    val playing = service.isPlaying()
                    
                    if (duration > 0) {
                        _currentDuration.value = duration
                        _currentPosition.value = position
                        _playbackProgress.value = position.toFloat() / duration.toFloat()
                    }
                    
                    _isPlaying.value = playing
                    
                    Log.v("NowPlayingViewModel", "Progress update: ${position}ms / ${duration}ms (${(_playbackProgress.value * 100).toInt()}%)")
                } ?: run {
                    Log.w("NowPlayingViewModel", "MusicService is null during progress update")
                }
                delay(1000) // Update every second
            }
        }
    }
    
    private fun stopProgressUpdates() {
        Log.d("NowPlayingViewModel", "Stopping progress updates")
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
    
    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        if (bound) {
            context?.unbindService(connection)
            bound = false
        }
        Log.d("NowPlayingViewModel", "ViewModel cleared")
    }

    fun logout() {
        // Stop any playing music
        if (_isPlaying.value) {
            context?.let { ctx ->
                val intent = Intent(ctx, MusicService::class.java).apply {
                    action = MusicService.ACTION_STOP
                }
                ctx.startService(intent)
            }
        }
        
        // Clear all data
        _currentSong.value = null
        _isPlaying.value = false
        _playbackProgress.value = 0f
        _currentDuration.value = 0L
        _currentPosition.value = 0L
        _queue.value = emptyList()
        _currentIndex.value = -1
        _isShuffleEnabled.value = false
        _repeatMode.value = QueueManager.RepeatMode.NONE
        
        // Stop progress updates
        stopProgressUpdates()
        
        // Clear queue in service
        musicService?.getQueueManager()?.clearQueue()
        
        Log.d("NowPlayingViewModel", "Logout completed - all data cleared")
    }
} 