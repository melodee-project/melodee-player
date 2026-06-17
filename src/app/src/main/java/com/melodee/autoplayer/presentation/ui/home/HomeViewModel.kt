package com.melodee.autoplayer.presentation.ui.home

import android.annotation.SuppressLint
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
import com.melodee.autoplayer.domain.model.Artist
import com.melodee.autoplayer.domain.model.Album
import com.melodee.autoplayer.service.MusicService
import com.melodee.autoplayer.util.PerformanceMonitor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.util.UUID

@SuppressLint("StaticFieldLeak")
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private var repository: MusicRepository? = null
    private var musicService: MusicService? = null
    private var bound = false
    private var searchJob: Job? = null
    private var performanceMonitor: PerformanceMonitor? = null
    private var didAttemptUserRefresh = false

    companion object {
        private const val MAX_SONGS_IN_MEMORY = 150
        private const val KEEP_SONGS_ON_CLEANUP = 75
    }

    private fun updateSongsWithVirtualScrolling(newSongs: List<Song>, isFirstPage: Boolean): List<Song> {
        return if (isFirstPage) {
            newSongs
        } else {
            val currentSongs = _songs.value
            val combinedSongs = currentSongs + newSongs

            // If we exceed memory limit, keep only the most recent songs
            if (combinedSongs.size > MAX_SONGS_IN_MEMORY) {
                combinedSongs.takeLast(KEEP_SONGS_ON_CLEANUP)
            } else {
                combinedSongs
            }
        }
    }

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    val selectedArtist: StateFlow<Artist?> = _selectedArtist.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _artistSearchQuery = MutableStateFlow("")

    private val _isArtistLoading = MutableStateFlow(false)
    val isArtistLoading: StateFlow<Boolean> = _isArtistLoading.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isAlbumsLoading = MutableStateFlow(false)
    val isAlbumsLoading: StateFlow<Boolean> = _isAlbumsLoading.asStateFlow()

    private val _totalAlbums = MutableStateFlow(0)
    val totalAlbums: StateFlow<Int> = _totalAlbums.asStateFlow()

    private val _currentAlbumsStart = MutableStateFlow(0)
    val currentAlbumsStart: StateFlow<Int> = _currentAlbumsStart.asStateFlow()

    private val _currentAlbumsEnd = MutableStateFlow(0)
    val currentAlbumsEnd: StateFlow<Int> = _currentAlbumsEnd.asStateFlow()

    private val _showAlbums = MutableStateFlow(false)
    val showAlbums: StateFlow<Boolean> = _showAlbums.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()

    private val _showAlbumSongs = MutableStateFlow(false)
    val showAlbumSongs: StateFlow<Boolean> = _showAlbumSongs.asStateFlow()

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

    // Keep track of the currently playing song, independent of UI state
    private val _currentPlayingSong = MutableStateFlow<Song?>(null)
    val currentPlayingSong: StateFlow<Song?> = _currentPlayingSong.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private var currentPage = 1
    private var hasMorePlaylists = true
    private var hasMoreSongs = true
    private var currentSearchPage = 1
    private var currentArtistPage = 1
    private var hasMoreArtists = true

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
            Log.d("HomeViewModel", "Service connected, isPlaying: ${_isPlaying.value}")

            // Start observing service state
            observeServiceState()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            musicService = null
            bound = false
            Log.d("HomeViewModel", "Service disconnected")
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            musicService?.let { service ->
                launch {
                    service.currentSongFlow().collect { song ->
                        Log.d("HomeViewModel", "Service current song updated: ${song?.title}")
                        val playbackContext = service.getCurrentPlaybackContext()
                        Log.d("HomeViewModel", "Current playback context: $playbackContext")

                        if (song != null) {
                            _currentPlayingSong.value = song

                            val index = _songs.value.indexOf(song)
                            if (index >= 0) {
                                Log.d("HomeViewModel", "Found song in HomeViewModel songs at index: $index (context: $playbackContext)")
                                _currentSongIndex.value = index
                            } else if (playbackContext != MusicService.PlaybackContext.SEARCH &&
                                playbackContext != MusicService.PlaybackContext.PLAYLIST) {
                                _currentSongIndex.value = -1
                                _isPlaying.value = false
                                Log.d("HomeViewModel", "Different playback context ($playbackContext), resetting home state")
                            }
                        } else if (playbackContext != MusicService.PlaybackContext.SEARCH &&
                                  playbackContext != MusicService.PlaybackContext.PLAYLIST) {
                            Log.d("HomeViewModel", "Playback context is not SEARCH/PLAYLIST ($playbackContext), resetting home state")
                            _currentSongIndex.value = -1
                            _isPlaying.value = false
                        } else {
                            Log.d("HomeViewModel", "Service current song is null")
                            if (_searchQuery.value.isBlank()) {
                                _currentSongIndex.value = -1
                                _isPlaying.value = false
                            }
                        }
                    }
                }

                launch {
                    combine(
                        service.isPlayingFlow(),
                        service.currentPlaybackContextFlow()
                    ) { isPlaying, playbackContext ->
                        Pair(isPlaying, playbackContext)
                    }.collect { (isPlaying, playbackContext) ->
                        if (playbackContext == MusicService.PlaybackContext.SEARCH ||
                            playbackContext == MusicService.PlaybackContext.PLAYLIST) {
                            if (_currentSongIndex.value >= 0) {
                                _isPlaying.value = isPlaying
                            }
                        } else if (_isPlaying.value) {
                            Log.d("HomeViewModel", "Playback context changed to $playbackContext, stopping home playback state")
                            _isPlaying.value = false
                            _currentSongIndex.value = -1
                        }
                    }
                }

                launch {
                    combine(
                        service.currentDurationFlow(),
                        service.currentPositionFlow()
                    ) { duration, position ->
                        Pair(duration, position)
                    }.collect { (duration, position) ->
                        if (duration > 0) {
                            _playbackProgress.value = position.toFloat() / duration.toFloat()
                            Log.v("HomeViewModel", "Progress update: ${position}ms / ${duration}ms (${(_playbackProgress.value * 100).toInt()}%)")
                        }
                    }
                }
            }
        }
    }

    init {
        // Set up debounced song search
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

        // Set up debounced artist search
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _artistSearchQuery
                .debounce(500) // 500ms debounce as requested
                .collect { query ->
                    if (query.length >= 2) { // Minimum 2 characters
                        performArtistSearch(query)
                    } else {
                        _artists.value = emptyList()
                        _isArtistLoading.value = false
                    }
                }
        }

        // Log playlist state changes
        viewModelScope.launch {
            _playlists.collect { playlists ->
                Log.d("HomeViewModel", "Playlists updated: ${playlists.size} items")
            }
        }

        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // Load user from stored preferences or repository
                // For now using default user until proper user management is implemented
                _user.value = User(
                    id = UUID.randomUUID(),
                    email = "user@melodee.com",
                    thumbnailUrl = "",
                    imageUrl = "",
                    username = "Melodee User"
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading user", e)
            }
        }
    }

    fun setContext(context: Context) {
        val appContext = context.applicationContext
        // Initialize performance monitoring
        performanceMonitor = PerformanceMonitor.getInstance(appContext)
        performanceMonitor?.startMonitoring()
        // Bind to MusicService
        Intent(appContext, MusicService::class.java).also { intent ->
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    // Progress state is now driven directly by MusicService flow state.

    override fun onCleared() {
        super.onCleared()
        performanceMonitor?.stopMonitoring()
        if (bound) {
            getApplication<Application>().unbindService(connection)
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

    fun refreshUserFromApiIfNeeded() {
        if (repository == null || didAttemptUserRefresh) return
        didAttemptUserRefresh = true
        viewModelScope.launch {
            try {
                repository?.getCurrentUser()
                    ?.catch { e -> Log.w("HomeViewModel", "Failed to fetch current user: ${e.message}") }
                    ?.collect { user ->
                        _user.value = user
                        // Optionally trigger playlists reload or other dependent UI updates
                    }
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Exception fetching current user", e)
            }
        }
    }

    fun loadPlaylists() {
        Log.d("HomeViewModel", "Loading playlists, page: $currentPage, hasMore: $hasMorePlaylists")
        if (!hasMorePlaylists || repository == null || _isLoading.value) {
            Log.d("HomeViewModel", "Skipping playlist load: hasMore=$hasMorePlaylists, repository=${repository != null}, isLoading=${_isLoading.value}")
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
                            Log.d("HomeViewModel", "Playlist: ${playlist.name}, Song count: ${playlist.songCount}")
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
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            }
            ctx.startService(intent)
            _isPlaying.value = false
        }

        // Reset playback state
        _currentSongIndex.value = -1
        _playbackProgress.value = 0f

        Log.d("HomeViewModel", "Cleared search and stopped playback")
    }

    private fun performSearch(query: String) {
        if (!hasMoreSongs || repository == null) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val selectedArtistId = _selectedArtist.value?.id?.toString()

                // Only perform search when query is not blank
                if (query.isNotBlank()) {
                    // Use appropriate search method based on artist filter
                    if (selectedArtistId != null) {
                        // Search songs with artist filter
                        repository?.searchSongsWithArtist(query, selectedArtistId, currentSearchPage)
                            ?.catch { _ ->
                                _isLoading.value = false
                            }
                            ?.collect { response ->
                                _songs.value = updateSongsWithVirtualScrolling(response.data, currentSearchPage == 1)
                                _totalSearchResults.value = response.meta.totalCount
                                _currentPageStart.value = if (currentSearchPage == 1) 1 else _currentPageEnd.value + 1
                                _currentPageEnd.value = _currentPageStart.value + response.data.size - 1
                                hasMoreSongs = response.meta.hasNext
                                currentSearchPage = response.meta.currentPage + 1
                                _isLoading.value = false
                            }
                    } else {
                        // Search all songs (Everyone) - use original searchSongs endpoint
                        repository?.searchSongs(query, currentSearchPage)
                            ?.catch { _ ->
                                _isLoading.value = false
                            }
                            ?.collect { response ->
                                _songs.value = updateSongsWithVirtualScrolling(response.data, currentSearchPage == 1)
                                _totalSearchResults.value = response.meta.totalCount
                                _currentPageStart.value = if (currentSearchPage == 1) 1 else _currentPageEnd.value + 1
                                _currentPageEnd.value = _currentPageStart.value + response.data.size - 1
                                hasMoreSongs = response.meta.hasNext
                                currentSearchPage = response.meta.currentPage + 1
                                _isLoading.value = false
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error searching songs", e)
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
        Log.d("HomeViewModel", "Setting user: ${user.username}")
        _user.value = user

        // Load playlists when user is set (after authentication)
        // Artists will be loaded on-demand when user searches
        if (repository != null) {
            loadPlaylists()
        }
    }

    fun playSong(song: Song) {
        val index = songs.value.indexOf(song)
        Log.d("HomeViewModel", "=== PLAY SONG DEBUG ===")
        Log.d("HomeViewModel", "playSong called - song: ${song.title}")
        Log.d("HomeViewModel", "Song index in search results: $index")
        Log.d("HomeViewModel", "Total songs in search results: ${songs.value.size}")
        Log.d("HomeViewModel", "Current search query: '${_searchQuery.value}'")
        Log.d("HomeViewModel", "Current bound state: $bound")

        if (index != -1) {
            Log.d("HomeViewModel", "Setting state - currentSongIndex: $index, isPlaying: true")
            _currentSongIndex.value = index
            _isPlaying.value = true
            _currentPlayingSong.value = song  // Store the currently playing song

            Log.d("HomeViewModel", "State after setting - currentSongIndex: ${_currentSongIndex.value}, isPlaying: ${_isPlaying.value}")

            val ctx = getApplication<Application>()
            // If we have search results, set them as the queue with search context
            if (songs.value.isNotEmpty() && _searchQuery.value.isNotBlank()) {
                Log.d("HomeViewModel", "Playing service-side search queue")
                val service = musicService
                if (service != null) {
                    service.playSearchQueue(songs.value, index)
                } else {
                    val intent = Intent(ctx, MusicService::class.java).apply {
                        action = MusicService.ACTION_PLAY_SONG
                        putExtra(MusicService.EXTRA_SONG, song)
                    }
                    ctx.startService(intent)
                }
            } else {
                Log.d("HomeViewModel", "Sending ACTION_PLAY_SONG to service (single song)")
                // Single song playback
                val intent = Intent(ctx, MusicService::class.java).apply {
                    action = MusicService.ACTION_PLAY_SONG
                    putExtra(MusicService.EXTRA_SONG, song)
                }
                ctx.startService(intent)
            }

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

        } else {
            Log.e("HomeViewModel", "Song not found in search results!")
        }
        Log.d("HomeViewModel", "=====================")
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MusicService::class.java).apply {
            action = if (_isPlaying.value) {
                MusicService.ACTION_RESUME
            } else {
                MusicService.ACTION_PAUSE
            }
        }
        ctx.startService(intent)
    }

    fun seekTo(position: Long) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MusicService::class.java).apply {
            action = MusicService.ACTION_SEEK_TO
            putExtra(MusicService.EXTRA_POSITION, position)
        }
        ctx.startService(intent)
    }

    fun logout() {
        // Stop any playing music
        if (_isPlaying.value) {
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            }
            ctx.startService(intent)
        }

        // Clear all data
        _user.value = null
        _playlists.value = emptyList()
        _songs.value = emptyList()
        _searchQuery.value = ""
        _selectedArtist.value = null
        _artists.value = emptyList()
        _artistSearchQuery.value = ""
        _isArtistLoading.value = false
        _totalSearchResults.value = 0
        _currentPageStart.value = 0
        _currentPageEnd.value = 0
        _currentSongIndex.value = -1
        _isPlaying.value = false
        _playbackProgress.value = 0f
        _isLoading.value = false

        // Reset album/artist view states to return to normal playlist view
        _albums.value = emptyList()
        _isAlbumsLoading.value = false
        _showAlbums.value = false
        _selectedAlbum.value = null
        _showAlbumSongs.value = false

        // Reset pagination
        currentPage = 1
        hasMorePlaylists = true
        hasMoreSongs = true
        currentSearchPage = 1
        currentArtistPage = 1
        hasMoreArtists = true

        // Clear repository
        repository = null

        Log.d("HomeViewModel", "Logout completed - all data cleared")
    }

    fun favoriteSong(song: Song, newStarredValue: Boolean) {
        viewModelScope.launch {
            try {
                Log.d("HomeViewModel", "favoriteSong called: songId=${song.id}, newStarredValue=$newStarredValue")
                val success = repository?.favoriteSong(song.id.toString(), newStarredValue) ?: false
                Log.d("HomeViewModel", "favoriteSong API result: success=$success")

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
                    Log.d("HomeViewModel", "Updated song list, new size: ${updatedSongs.size}")

                    // Refresh playlists since favorite status changes may affect playlist details
                    refreshPlaylists()

                    // Show success toast
                    val ctx = getApplication<Application>()
                    val message = if (newStarredValue) "Favorited Song" else "Un-favorited Song"
                    Log.d("HomeViewModel", "Showing success toast: $message")
                    android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("HomeViewModel", "favoriteSong failed: success=$success")
                    // Show error toast
                    val ctx = getApplication<Application>()
                    android.widget.Toast.makeText(ctx, "Doh! Unable to change songs favorite status", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "favoriteSong exception: ${e.message}", e)
                // Show error toast
                val ctx = getApplication<Application>()
                android.widget.Toast.makeText(ctx, "Doh! Unable to change songs favorite status", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performArtistSearch(query: String) {
        if (repository == null) return

        viewModelScope.launch {
            _isArtistLoading.value = true
            try {
                repository?.searchArtists(query, 1) // Always search from page 1 for autocomplete
                    ?.catch { e ->
                        Log.e("HomeViewModel", "Error searching artists: ${e.message}")
                        _isArtistLoading.value = false
                    }
                    ?.collect { response ->
                        _artists.value = response.data.take(10) // Limit to 10 results for autocomplete
                        _isArtistLoading.value = false
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Exception searching artists: ${e.message}")
                _isArtistLoading.value = false
            }
        }
    }

    fun searchArtists(query: String) {
        _artistSearchQuery.value = query
    }

    fun selectArtist(artist: Artist?) {
        _selectedArtist.value = artist

        // Clear current songs and reset pagination
        _songs.value = emptyList()
        currentSearchPage = 1
        hasMoreSongs = true

        // Clear album view states when artist changes
        _albums.value = emptyList()
        _isAlbumsLoading.value = false
        _showAlbums.value = false
        _selectedAlbum.value = null
        _showAlbumSongs.value = false

        // If artist is being cleared (set to null), clear the artist results but keep search capability
        if (artist == null) {
            _artists.value = emptyList()
            _isArtistLoading.value = false
        }

        // When artist is selected with existing search text: Re-filter with artist constraint
        if (artist != null && _searchQuery.value.isNotBlank()) {
            performSearch(_searchQuery.value)
        } else if (artist == null && _searchQuery.value.isNotBlank()) {
            // Reset to "Everyone" - re-search across all artists
            performSearch(_searchQuery.value)
        }
        // When artist is selected with no search text: Show empty list (no automatic loading)
        // When artist is cleared with no search text: Show empty list
    }

    fun refreshPlaylists() {
        // Reset pagination and reload playlists from the beginning
        currentPage = 1
        hasMorePlaylists = true
        _playlists.value = emptyList()
        loadPlaylists()
    }

    fun browseArtistAlbums() {
        val artist = _selectedArtist.value ?: return

        _showAlbums.value = true
        _albums.value = emptyList()
        _isAlbumsLoading.value = true

        viewModelScope.launch {
            try {
                repository?.getArtistAlbums(artist.id.toString(), 1)
                    ?.catch { _ ->
                        _isAlbumsLoading.value = false
                    }
                    ?.collect { response ->
                        // Sort albums by release year in descending order (newest first)
                        val sortedAlbums = response.data.sortedByDescending { it.releaseYear }
                        _albums.value = sortedAlbums
                        _totalAlbums.value = response.meta.totalCount
                        _currentAlbumsStart.value = if (sortedAlbums.isNotEmpty()) 1 else 0
                        _currentAlbumsEnd.value = sortedAlbums.size
                        _isAlbumsLoading.value = false
                    }
            } catch (e: Exception) {
                _isAlbumsLoading.value = false
                Log.e("HomeViewModel", "Error loading artist albums", e)
            }
        }
    }

    fun hideAlbums() {
        _showAlbums.value = false
        _albums.value = emptyList()
        _selectedAlbum.value = null
        _showAlbumSongs.value = false
    }

    fun browseAlbumSongs(album: Album) {
        _selectedAlbum.value = album
        _showAlbumSongs.value = true
        _showAlbums.value = false
        _songs.value = emptyList()

        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository?.getAlbumSongs(album.id.toString(), 1)
                    ?.catch { _ ->
                        _isLoading.value = false
                    }
                    ?.collect { response ->
                        _songs.value = response.data
                        _totalSearchResults.value = response.meta.totalCount
                        _currentPageStart.value = 1
                        _currentPageEnd.value = response.data.size
                        hasMoreSongs = response.meta.hasNext
                        currentSearchPage = response.meta.currentPage + 1
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _isLoading.value = false
                Log.e("HomeViewModel", "Error loading album songs", e)
            }
        }
    }

    fun hideAlbumSongs() {
        _showAlbumSongs.value = false
        _selectedAlbum.value = null
        _songs.value = emptyList()
        // Return to albums view
        _showAlbums.value = true
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            try {
                repository?.getAlbumSongs(album.id.toString(), 1)
                    ?.collect { response ->
                        val albumSongs = response.data
                        if (albumSongs.isNotEmpty()) {
                            _currentSongIndex.value = 0
                            _isPlaying.value = true
                            _currentPlayingSong.value = albumSongs.first()

                            val ctx = getApplication<Application>()
                            Log.d("HomeViewModel", "Playing album: ${album.name} with ${albumSongs.size} songs")
                            val service = musicService
                            if (service != null) {
                                service.playSearchQueue(albumSongs, 0)
                            } else {
                                val intent = Intent(ctx, MusicService::class.java).apply {
                                    action = MusicService.ACTION_PLAY_SONG
                                    putExtra(MusicService.EXTRA_SONG, albumSongs.first())
                                }
                                ctx.startService(intent)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error playing album", e)
            }
        }
    }

    fun browseArtistSongs() {
        val artist = _selectedArtist.value ?: return

        // Clear current songs and search
        _songs.value = emptyList()
        _searchQuery.value = ""
        currentSearchPage = 1
        hasMoreSongs = true

        // Hide albums view to show songs
        _showAlbums.value = false
        _showAlbumSongs.value = false

        // Load all songs for this artist
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository?.getArtistSongs(artist.id.toString(), 1)
                    ?.catch { _ ->
                        _isLoading.value = false
                    }
                    ?.collect { response ->
                        _songs.value = response.data
                        _totalSearchResults.value = response.meta.totalCount
                        _currentPageStart.value = 1
                        _currentPageEnd.value = response.data.size
                        hasMoreSongs = response.meta.hasNext
                        currentSearchPage = response.meta.currentPage + 1
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _isLoading.value = false
                Log.e("HomeViewModel", "Error loading artist songs", e)
            }
        }
    }
}
