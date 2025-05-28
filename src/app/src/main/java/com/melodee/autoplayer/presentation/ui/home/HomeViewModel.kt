package com.melodee.autoplayer.presentation.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.melodee.autoplayer.data.repository.MusicRepository
import com.melodee.autoplayer.domain.model.Playlist
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.domain.model.User
import com.melodee.autoplayer.service.MusicService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.util.UUID
import java.util.ArrayList

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private var repository: MusicRepository? = null
    private var context: Context? = null
    private var musicService: MusicService? = null
    private var bound = false
    
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _totalSearchResults = MutableStateFlow(0)
    val totalSearchResults: StateFlow<Int> = _totalSearchResults.asStateFlow()

    private val _currentPageStart = MutableStateFlow(0)
    val currentPageStart: StateFlow<Int> = _currentPageStart.asStateFlow()

    private val _currentPageEnd = MutableStateFlow(0)
    val currentPageEnd: StateFlow<Int> = _currentPageEnd.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentDuration = MutableStateFlow(0L)
    val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()

    private var currentPage = 1
    private var hasMorePlaylists = true
    private var hasMoreSongs = true
    private var currentSearchPage = 1
    private var progressUpdateJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
            Log.d("HomeViewModel", "Service connected, isPlaying: ${_isPlaying.value}")
            
            // Start observing service state
            observeServiceState()
            
            // Ensure progress updates start if we should be playing
            ensureProgressUpdatesStarted()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            musicService = null
            bound = false
            Log.d("HomeViewModel", "Service disconnected")
            stopProgressUpdates()
        }
    }

    private fun updateProgress() {
        val duration = _currentDuration.value
        val position = _currentPosition.value
        _playbackProgress.value = if (duration > 0) position.toFloat() / duration else 0f
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            musicService?.let { service ->
                // Observe playlist manager state to track current song changes
                service.getPlaylistManager().currentSong.collect { song ->
                    Log.d("HomeViewModel", "Service current song updated: ${song?.name}")
                    
                    // Check if the current playback context is SEARCH
                    val playbackContext = service.getCurrentPlaybackContext()
                    Log.d("HomeViewModel", "Current playback context: $playbackContext")
                    
                    if (song != null && playbackContext == MusicService.PlaybackContext.SEARCH) {
                        // Find the song in our current search results and update the index
                        val index = _songs.value.indexOf(song)
                        if (index >= 0) {
                            Log.d("HomeViewModel", "Found song in search results at index: $index (SEARCH context)")
                            // Update both index and playing state to ensure mini player shows
                            _currentSongIndex.value = index
                            _isPlaying.value = service.isPlaying()
                            Log.d("HomeViewModel", "Updated currentSongIndex to: $index, isPlaying: ${_isPlaying.value}")
                        } else {
                            Log.d("HomeViewModel", "Song not found in current search results")
                        }
                    } else if (playbackContext != MusicService.PlaybackContext.SEARCH) {
                        Log.d("HomeViewModel", "Playback context is not SEARCH ($playbackContext), resetting home state")
                        // If playback context is not SEARCH, reset our state
                        _currentSongIndex.value = -1
                        _isPlaying.value = false
                    } else {
                        Log.d("HomeViewModel", "Service current song is null")
                        // Only reset if we're not currently playing from search
                        if (_searchQuery.value.isBlank()) {
                            _currentSongIndex.value = -1
                            _isPlaying.value = false
                        }
                    }
                }
            }
        }
        
        viewModelScope.launch {
            musicService?.let { service ->
                // Observe playing state from the service more frequently for better responsiveness
                while (bound && musicService != null) {
                    val isServicePlaying = service.isPlaying()
                    val playbackContext = service.getCurrentPlaybackContext()
                    
                    // Only update if we're in SEARCH context and there's a meaningful change
                    if (playbackContext == MusicService.PlaybackContext.SEARCH && 
                        _currentSongIndex.value >= 0 && 
                        _isPlaying.value != isServicePlaying) {
                        Log.d("HomeViewModel", "Service playing state changed: $isServicePlaying (was: ${_isPlaying.value}) in SEARCH context")
                        _isPlaying.value = isServicePlaying
                        Log.d("HomeViewModel", "Updated isPlaying from service: $isServicePlaying")
                    } else if (playbackContext != MusicService.PlaybackContext.SEARCH && _isPlaying.value) {
                        Log.d("HomeViewModel", "Playback context changed from SEARCH to $playbackContext, stopping home playback state")
                        _isPlaying.value = false
                        _currentSongIndex.value = -1
                    }
                    delay(500) // Check more frequently for better responsiveness
                }
            }
        }
    }

    init {
        // Set up debounced search
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _searchQuery
                .debounce(1000) // 1 second delay
                .collect { query ->
                    if (query.isBlank()) {
                        _songs.value = emptyList()
                        currentSearchPage = 1
                        hasMoreSongs = true
                        // Reset current song index when search is cleared
                        _currentSongIndex.value = -1
                    } else {
                        performSearch(query)
                    }
                }
        }

        // Log playlist state changes
        viewModelScope.launch {
            _playlists.collect { playlists ->
                Log.d("HomeViewModel", "Playlists updated: ${playlists.size} items")
            }
        }

        // Start progress updates when playing
        viewModelScope.launch {
            _isPlaying.collect { playing ->
                Log.d("HomeViewModel", "isPlaying changed to: $playing, bound: $bound")
                if (playing && bound) {
                    ensureProgressUpdatesStarted()
                } else {
                    stopProgressUpdates()
                }
            }
        }

        // Periodic check to ensure progress updates are running when they should be
        viewModelScope.launch {
            while (true) {
                delay(5000) // Check every 5 seconds
                if (_isPlaying.value && bound && musicService != null && progressUpdateJob == null) {
                    Log.w("HomeViewModel", "Progress updates should be running but aren't - restarting")
                    ensureProgressUpdatesStarted()
                }
            }
        }

        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // TODO: Load user from repository
                _user.value = User(
                    id = UUID.randomUUID(),
                    email = "test@example.com",
                    avatarThumbnailUrl = "https://example.com/avatar.jpg",
                    avatarUrl = "https://example.com/avatar.jpg",
                    userName = "Test User"
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading user", e)
            }
        }
    }

    fun setContext(context: Context) {
        this.context = context
        // Bind to MusicService
        Intent(context, MusicService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun ensureProgressUpdatesStarted() {
        if (_isPlaying.value && bound && musicService != null && progressUpdateJob == null) {
            Log.d("HomeViewModel", "Ensuring progress updates are started")
            startProgressUpdates()
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        Log.d("HomeViewModel", "Starting progress updates, bound: $bound, musicService: ${musicService != null}")
        progressUpdateJob = viewModelScope.launch {
            while (true) {
                musicService?.let { service ->
                    val duration = service.getDuration()
                    val position = service.getCurrentPosition()
                    if (duration > 0) {
                        _currentDuration.value = duration
                        _currentPosition.value = position
                        _playbackProgress.value = position.toFloat() / duration.toFloat()
                        Log.v("HomeViewModel", "Progress update: ${position}ms / ${duration}ms (${(_playbackProgress.value * 100).toInt()}%)")
                    }
                } ?: run {
                    Log.w("HomeViewModel", "MusicService is null during progress update")
                }
                delay(1000) // Update every second
            }
        }
    }

    private fun stopProgressUpdates() {
        Log.d("HomeViewModel", "Stopping progress updates")
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
    }

    fun setBaseUrl(baseUrl: String) {
        Log.d("HomeViewModel", "Setting base URL: $baseUrl")
        repository = MusicRepository(baseUrl, getApplication())
        // Reset state when setting new base URL
        currentPage = 1
        hasMorePlaylists = true
        _playlists.value = emptyList()
        
        // Note: Playlists will be loaded when setUser() is called after authentication
    }

    fun loadPlaylists() {
        Log.d("HomeViewModel", "Loading playlists, page: $currentPage, hasMore: $hasMorePlaylists")
        if (!hasMorePlaylists || repository == null) {
            Log.d("HomeViewModel", "Skipping playlist load: hasMore=$hasMorePlaylists, repository=${repository != null}")
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d("HomeViewModel", "Making API call for playlists")
                repository?.getPlaylists(currentPage)
                    ?.catch { e ->
                        Log.e("HomeViewModel", "Error loading playlists", e)
                        _isLoading.value = false
                    }
                    ?.collect { response ->
                        Log.d("HomeViewModel", "Received playlists response: ${response.data.size} items")
                        response.data.forEach { playlist ->
                            Log.d("HomeViewModel", "Playlist: ${playlist.name}, Song count: ${playlist.songsCount}")
                        }
                        _playlists.value = if (currentPage == 1) {
                            response.data
                        } else {
                            _playlists.value + response.data
                        }
                        hasMorePlaylists = response.meta.hasNext
                        currentPage = response.meta.currentPage + 1
                        _isLoading.value = false
                        Log.d("HomeViewModel", "Updated playlists state: ${_playlists.value.size} items")
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Exception loading playlists", e)
                _isLoading.value = false
            }
        }
    }

    fun searchSongs(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchAndStopPlayback() {
        // Clear search
        _searchQuery.value = ""
        
        // Stop playback if playing
        if (_isPlaying.value) {
            context?.let { ctx ->
                val intent = Intent(ctx, MusicService::class.java).apply {
                    action = MusicService.ACTION_STOP
                }
                ctx.startService(intent)
            }
            _isPlaying.value = false
        }
        
        // Reset playback state
        _currentSongIndex.value = -1
        _playbackProgress.value = 0f
        _currentPosition.value = 0L
        _currentDuration.value = 0L
        
        // Stop progress updates
        stopProgressUpdates()
        
        Log.d("HomeViewModel", "Cleared search and stopped playback")
    }

    private fun performSearch(query: String) {
        if (!hasMoreSongs || repository == null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository?.searchSongs(query, currentSearchPage)
                    ?.catch { _ ->
                        _isLoading.value = false
                    }
                    ?.collect { response ->
                        _songs.value = if (currentSearchPage == 1) {
                            response.data
                        } else {
                            _songs.value + response.data
                        }
                        _totalSearchResults.value = response.meta.totalCount
                        _currentPageStart.value = if (currentSearchPage == 1) 1 else _currentPageEnd.value + 1
                        _currentPageEnd.value = _currentPageStart.value + response.data.size - 1
                        hasMoreSongs = response.meta.hasNext
                        currentSearchPage = response.meta.currentPage + 1
                        _isLoading.value = false

                        // Note: Removed auto-play logic - users must manually click on songs to play them
                    }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreSearchResults() {
        if (!hasMoreSongs || _isLoading.value) return
        _searchQuery.value.let { query ->
            if (query.isNotBlank()) {
                performSearch(query)
            }
        }
    }

    fun setUser(user: User) {
        Log.d("HomeViewModel", "Setting user: ${user.userName}")
        _user.value = user
        
        // Load playlists when user is set (after authentication)
        if (repository != null) {
            loadPlaylists()
        }
    }

    fun playSong(song: Song) {
        val index = songs.value.indexOf(song)
        Log.d("HomeViewModel", "=== PLAY SONG DEBUG ===")
        Log.d("HomeViewModel", "playSong called - song: ${song.name}")
        Log.d("HomeViewModel", "Song index in search results: $index")
        Log.d("HomeViewModel", "Total songs in search results: ${songs.value.size}")
        Log.d("HomeViewModel", "Current search query: '${_searchQuery.value}'")
        Log.d("HomeViewModel", "Current bound state: $bound")
        
        if (index != -1) {
            Log.d("HomeViewModel", "Setting state - currentSongIndex: $index, isPlaying: true")
            _currentSongIndex.value = index
            _isPlaying.value = true
            
            Log.d("HomeViewModel", "State after setting - currentSongIndex: ${_currentSongIndex.value}, isPlaying: ${_isPlaying.value}")
            
            context?.let { ctx ->
                // If we have search results, set them as the queue with search context
                if (songs.value.isNotEmpty() && _searchQuery.value.isNotBlank()) {
                    Log.d("HomeViewModel", "Sending ACTION_SET_SEARCH_RESULTS to service")
                    val intent = Intent(ctx, MusicService::class.java).apply {
                        action = MusicService.ACTION_SET_SEARCH_RESULTS
                        putParcelableArrayListExtra(MusicService.EXTRA_SEARCH_RESULTS, ArrayList(songs.value))
                        putExtra("START_INDEX", index)
                    }
                    ctx.startService(intent)
                } else {
                    Log.d("HomeViewModel", "Sending ACTION_PLAY_SONG to service (single song)")
                    // Single song playback
                    val intent = Intent(ctx, MusicService::class.java).apply {
                        action = MusicService.ACTION_PLAY_SONG
                        putExtra(MusicService.EXTRA_SONG, song)
                    }
                    ctx.startService(intent)
                }
                
                // Ensure progress updates start (will work immediately if bound, or later when service connects)
                ensureProgressUpdatesStarted()
                
                // Add a verification mechanism to ensure state is correct after a short delay
                viewModelScope.launch {
                    delay(1000) // Wait 1 second for service to process
                    
                    // Verify that our state is still correct
                    if (_currentSongIndex.value == index && _isPlaying.value) {
                        Log.d("HomeViewModel", "State verification: OK - index: $index, playing: true")
                    } else {
                        Log.w("HomeViewModel", "State verification: MISMATCH - expected index: $index, actual: ${_currentSongIndex.value}, expected playing: true, actual: ${_isPlaying.value}")
                        // Force correct state
                        _currentSongIndex.value = index
                        _isPlaying.value = true
                        Log.d("HomeViewModel", "Forced state correction")
                    }
                    
                    // Also verify service state if bound
                    if (bound && musicService != null) {
                        val serviceIsPlaying = musicService!!.isPlaying()
                        Log.d("HomeViewModel", "Service verification: isPlaying = $serviceIsPlaying")
                        if (!serviceIsPlaying) {
                            Log.w("HomeViewModel", "Service is not playing, but we expect it to be")
                        }
                    }
                }
                
                // If service is not bound yet, retry after a delay
                if (!bound) {
                    Log.d("HomeViewModel", "Service not bound yet, will retry progress updates")
                    viewModelScope.launch {
                        delay(500)
                        ensureProgressUpdatesStarted()
                    }
                }
            } ?: run {
                Log.e("HomeViewModel", "Context is null, cannot start service")
            }
        } else {
            Log.e("HomeViewModel", "Song not found in search results!")
        }
        Log.d("HomeViewModel", "=====================")
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

    fun seekTo(position: Long) {
        context?.let { ctx ->
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_SEEK_TO
                putExtra(MusicService.EXTRA_POSITION, position)
            }
            ctx.startService(intent)
        }
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
        _user.value = null
        _playlists.value = emptyList()
        _songs.value = emptyList()
        _searchQuery.value = ""
        _totalSearchResults.value = 0
        _currentPageStart.value = 0
        _currentPageEnd.value = 0
        _currentSongIndex.value = -1
        _isPlaying.value = false
        _playbackProgress.value = 0f
        _currentPosition.value = 0L
        _currentDuration.value = 0L
        _isLoading.value = false
        
        // Reset pagination
        currentPage = 1
        hasMorePlaylists = true
        hasMoreSongs = true
        currentSearchPage = 1
        
        // Stop progress updates
        stopProgressUpdates()
        
        // Clear repository
        repository = null
        
        Log.d("HomeViewModel", "Logout completed - all data cleared")
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _songs.value = emptyList()
        _totalSearchResults.value = 0
        _currentPageStart.value = 0
        _currentPageEnd.value = 0
        currentSearchPage = 1
        hasMoreSongs = true
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
                    
                    // Refresh playlists since favorite status changes may affect playlist details
                    refreshPlaylists()
                    
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

    fun refreshPlaylists() {
        // Reset pagination and reload playlists from the beginning
        currentPage = 1
        hasMorePlaylists = true
        _playlists.value = emptyList()
        loadPlaylists()
    }
} 