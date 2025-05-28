package com.melodee.autoplayer.presentation.ui.playlist

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodee.autoplayer.data.repository.MusicRepository
import com.melodee.autoplayer.domain.model.Playlist
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.service.MusicService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.media.session.MediaSessionManager
import kotlinx.coroutines.Job
import android.media.session.MediaController
import android.media.MediaMetadata
import java.util.*
import android.util.Log

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private var repository: MusicRepository? = null
    private var context: Context? = null
    private var musicService: MusicService? = null
    private var bound = false
    private var onPlaylistsNeedRefresh: (() -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
            
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
        }
    }

    fun setBaseUrl(baseUrl: String) {
        repository = MusicRepository(baseUrl, getApplication())
    }

    fun setContext(context: Context) {
        this.context = context
        // Bind to MusicService
        Intent(context, MusicService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }
    
    fun setOnPlaylistsNeedRefresh(callback: () -> Unit) {
        onPlaylistsNeedRefresh = callback
    }

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentDuration = MutableStateFlow(0L)
    val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _shouldScrollToTop = MutableStateFlow(false)
    val shouldScrollToTop: StateFlow<Boolean> = _shouldScrollToTop.asStateFlow()

    private var progressUpdateJob: Job? = null

    private var currentPage = 1
    private var hasMoreSongs = true
    private var currentPlaylistId: String? = null
    private var isRefreshing = false

    init {
        // Start progress updates when playing
        viewModelScope.launch {
            _isPlaying.collect { playing ->
                if (playing && bound) {
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                }
            }
        }
    }

    private fun hasMediaControlPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context ?: return false,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for older Android versions
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                musicService?.let { service ->
                    val duration = service.getDuration()
                    val position = service.getCurrentPosition()
                    if (duration > 0) {
                        _currentDuration.value = duration
                        _currentPosition.value = position
                        _playbackProgress.value = position.toFloat() / duration.toFloat()
                    }
                }
                delay(1000) // Update every second
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            musicService?.let { service ->
                // Observe playlist manager state to track current song changes
                service.getPlaylistManager().currentSong.collect { song ->
                    Log.d("PlaylistViewModel", "Service current song updated: ${song?.name}")
                    
                    // Check if the current playback context is PLAYLIST
                    val playbackContext = service.getCurrentPlaybackContext()
                    Log.d("PlaylistViewModel", "Current playback context: $playbackContext")
                    
                    if (song != null && playbackContext == MusicService.PlaybackContext.PLAYLIST && _songs.value.contains(song)) {
                        // Only update if the song is in our current playlist and context is PLAYLIST
                        Log.d("PlaylistViewModel", "Setting current song: ${song.name} (PLAYLIST context)")
                        _currentSong.value = song
                        _isPlaying.value = service.isPlaying()
                    } else if (playbackContext != MusicService.PlaybackContext.PLAYLIST) {
                        Log.d("PlaylistViewModel", "Playback context is not PLAYLIST ($playbackContext), resetting playlist state")
                        // If playback context is not PLAYLIST, reset our state
                        _currentSong.value = null
                        _isPlaying.value = false
                    }
                }
            }
        }
        
        viewModelScope.launch {
            musicService?.let { service ->
                // Observe playing state from the service
                while (bound && musicService != null) {
                    val isServicePlaying = service.isPlaying()
                    val playbackContext = service.getCurrentPlaybackContext()
                    
                    // Only update if we're in PLAYLIST context and there's a meaningful change
                    if (playbackContext == MusicService.PlaybackContext.PLAYLIST && 
                        _currentSong.value != null && 
                        _isPlaying.value != isServicePlaying) {
                        Log.d("PlaylistViewModel", "Service playing state changed: $isServicePlaying (was: ${_isPlaying.value}) in PLAYLIST context")
                        _isPlaying.value = isServicePlaying
                    } else if (playbackContext != MusicService.PlaybackContext.PLAYLIST && _isPlaying.value) {
                        Log.d("PlaylistViewModel", "Playback context changed from PLAYLIST to $playbackContext, stopping playlist playback state")
                        _isPlaying.value = false
                        _currentSong.value = null
                    }
                    delay(1000) // Check every second
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        if (bound) {
            context?.unbindService(connection)
            bound = false
        }
    }

    val playlistName: String
        get() = _playlist.value?.name ?: ""

    fun loadPlaylist(playlistId: String) {
        // Check if this is a different playlist than what's currently playing
        val isNewPlaylist = playlistId != currentPlaylistId
        
        // If it's the same playlist and we have no more songs to load, 
        // still trigger auto-play but don't reload the songs
        if (playlistId == currentPlaylistId && !hasMoreSongs && _songs.value.isNotEmpty()) {
            // Stop current playback if any
            context?.let { ctx ->
                val stopIntent = Intent(ctx, MusicService::class.java).apply {
                    action = MusicService.ACTION_STOP
                }
                ctx.startService(stopIntent)
            }
            
            // Start playing the first song with playlist context
            val firstSong = _songs.value.first()
            _currentSong.value = firstSong
            _isPlaying.value = true
            context?.let { ctx ->
                val playIntent = Intent(ctx, MusicService::class.java).apply {
                    action = MusicService.ACTION_SET_PLAYLIST
                    putParcelableArrayListExtra(MusicService.EXTRA_PLAYLIST, ArrayList(_songs.value))
                    putExtra("START_INDEX", 0)
                }
                ctx.startService(playIntent)
            }
            return
        }
        
        // Reset pagination state for new playlist
        if (isNewPlaylist) {
            currentPage = 1
            hasMoreSongs = true
            _songs.value = emptyList()
            isRefreshing = false // Reset refresh state for new playlist
        }
        
        currentPlaylistId = playlistId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // First, get the playlist details from the repository
                repository?.getPlaylists(1)
                    ?.catch { _ ->
                        // Handle error
                    }
                    ?.collect { response ->
                        // Find the playlist in the response
                        val playlistDetails = response.data.find { it.id.toString() == playlistId }
                        if (playlistDetails != null) {
                            _playlist.value = playlistDetails
                        }
                    }

                // Then load the songs
                repository?.getPlaylistSongs(playlistId, currentPage)
                    ?.catch { _ ->
                        // Handle error
                    }
                    ?.collect { response ->
                        _songs.value = if (currentPage == 1) {
                            response.data
                        } else {
                            _songs.value + response.data
                        }
                        hasMoreSongs = response.meta.hasNext
                        currentPage = response.meta.currentPage + 1
                        
                        // If this was a refresh and we have songs, trigger scroll to top
                        if (isRefreshing && response.data.isNotEmpty() && currentPage == 2) {
                            _shouldScrollToTop.value = true
                            isRefreshing = false
                        }
                        
                        // Always auto-play the first song when loading a playlist
                        if (response.data.isNotEmpty()) {
                            // Stop current playback if any
                            context?.let { ctx ->
                                val stopIntent = Intent(ctx, MusicService::class.java).apply {
                                    action = MusicService.ACTION_STOP
                                }
                                ctx.startService(stopIntent)
                            }
                            
                            // Start playing the first song with playlist context
                            val firstSong = response.data.first()
                            _currentSong.value = firstSong
                            _isPlaying.value = true
                            context?.let { ctx ->
                                val playIntent = Intent(ctx, MusicService::class.java).apply {
                                    action = MusicService.ACTION_SET_PLAYLIST
                                    putParcelableArrayListExtra(MusicService.EXTRA_PLAYLIST, ArrayList(_songs.value))
                                    putExtra("START_INDEX", 0)
                                }
                                ctx.startService(playIntent)
                            }
                        }
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshSongs() {
        currentPage = 1
        hasMoreSongs = true
        _songs.value = emptyList()
        isRefreshing = true
        currentPlaylistId?.let { loadPlaylist(it) }
    }

    fun onScrollToTopHandled() {
        _shouldScrollToTop.value = false
    }

    fun favoriteSong(song: Song, newStarredValue: Boolean) {
        viewModelScope.launch {
            try {
                val success = repository?.favoriteSong(song.id.toString(), newStarredValue) ?: false
                
                if (success) {
                    // Update the song in the current list
                    val updatedSongs = _songs.value.map { currentSong ->
                        if (currentSong.id == song.id) {
                            currentSong.copy(userStarred = newStarredValue)
                        } else {
                            currentSong
                        }
                    }
                    _songs.value = updatedSongs
                    
                    // Refresh the current playlist since favorite status changes may affect playlist details
                    currentPlaylistId?.let { playlistId ->
                        refreshPlaylistDetails(playlistId)
                    }
                    
                    // Notify home page that playlists need to be refreshed
                    onPlaylistsNeedRefresh?.invoke()
                    
                    // Show success toast
                    context?.let { ctx ->
                        val message = if (newStarredValue) "Favorited Song" else "Un-favorited Song"
                        android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Show error toast
                    context?.let { ctx ->
                        android.widget.Toast.makeText(ctx, "Doh! Unable to change songs favorite status", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Show error toast
                context?.let { ctx ->
                    android.widget.Toast.makeText(ctx, "Doh! Unable to change songs favorite status", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshPlaylistDetails(playlistId: String) {
        viewModelScope.launch {
            try {
                // Refresh the playlist details from the repository
                repository?.getPlaylists(1)
                    ?.catch { _ ->
                        // Handle error silently
                    }
                    ?.collect { response ->
                        // Find the updated playlist in the response
                        val updatedPlaylistDetails = response.data.find { it.id.toString() == playlistId }
                        if (updatedPlaylistDetails != null) {
                            _playlist.value = updatedPlaylistDetails
                        }
                    }
            } catch (e: Exception) {
                // Handle error silently - playlist details refresh is not critical
                Log.w("PlaylistViewModel", "Failed to refresh playlist details after favorite change", e)
            }
        }
    }

    fun loadMoreSongs() {
        if (!hasMoreSongs || _isLoading.value) return
        isRefreshing = false // Ensure we don't trigger scroll during pagination
        currentPlaylistId?.let { loadPlaylist(it) }
    }

    fun playSong(song: Song) {
        if (song == currentSong.value) {
            // If the same song is clicked, toggle play/pause
            togglePlayPause()
        } else {
            // Play the new song from playlist context
            _currentSong.value = song
            _isPlaying.value = true
            context?.let { ctx ->
                val songIndex = _songs.value.indexOf(song)
                if (songIndex >= 0) {
                    // Set the entire playlist as the queue with playlist context
                    val intent = Intent(ctx, MusicService::class.java).apply {
                        action = MusicService.ACTION_SET_PLAYLIST
                        putParcelableArrayListExtra(MusicService.EXTRA_PLAYLIST, ArrayList(_songs.value))
                        putExtra("START_INDEX", songIndex)
                    }
                    ctx.startService(intent)
                } else {
                    // Fallback to single song if not found in playlist
                    val intent = Intent(ctx, MusicService::class.java).apply {
                        action = MusicService.ACTION_PLAY_SONG
                        putExtra(MusicService.EXTRA_SONG, song)
                    }
                    ctx.startService(intent)
                }
            }
        }
    }

    fun togglePlayPause() {
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
    }

    fun skipToNext() {
        val currentIndex = _songs.value.indexOf(_currentSong.value)
        if (currentIndex >= 0 && currentIndex < _songs.value.size - 1) {
            playSong(_songs.value[currentIndex + 1])
        } else if (currentIndex == _songs.value.size - 1) {
            // If we're at the last song, play the first one
            _songs.value.firstOrNull()?.let { playSong(it) }
        }
    }

    fun skipToPrevious() {
        val currentIndex = _songs.value.indexOf(_currentSong.value)
        if (currentIndex > 0) {
            playSong(_songs.value[currentIndex - 1])
        } else if (currentIndex == 0) {
            // If we're at the first song, play the last one
            _songs.value.lastOrNull()?.let { playSong(it) }
        }
    }

    fun updatePlaybackProgress(progress: Float) {
        _playbackProgress.value = progress
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
        _playlist.value = null
        _songs.value = emptyList()
        _currentSong.value = null
        _playbackProgress.value = 0f
        _currentDuration.value = 0L
        _currentPosition.value = 0L
        _isLoading.value = false
        _isPlaying.value = false
        
        // Reset pagination
        currentPage = 1
        hasMoreSongs = true
        currentPlaylistId = null
        
        // Stop progress updates
        stopProgressUpdates()
        
        // Clear repository
        repository = null
    }
} 