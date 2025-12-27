package com.melodee.autoplayer.service

import androidx.media3.common.util.UnstableApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.PlaybackException
import com.melodee.autoplayer.R
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.domain.model.Playlist
import com.melodee.autoplayer.presentation.ui.MainActivity
import com.melodee.autoplayer.data.SettingsManager
import com.melodee.autoplayer.data.AuthenticationManager
import com.melodee.autoplayer.data.api.NetworkModule
import com.melodee.autoplayer.MelodeeApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map

class MusicService : MediaBrowserServiceCompat() {
    private var player: ExoPlayer? = null
    private var currentSong: Song? = null
    private val binder = MusicBinder()
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var playbackManager: MusicPlaybackManager
    private var scrobbleManager: ScrobbleManager? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var authenticationManager: AuthenticationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null
    private var prefetchJob: Job? = null
    private var isFetchingNextPlaylistPage: Boolean = false
    private var remotePlaylistId: String? = null
    private var nextPlaylistPage: Int = 1
    private var hasMorePlaylistPages: Boolean = false
    private val paginationPrefetchThreshold = 3 // when within 3 songs of end, prefetch next page
    
    // Audio focus management
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // Add playback context tracking (moved to MusicPlaybackManager)
    enum class PlaybackContext {
        PLAYLIST,    // Playing from a playlist
        SEARCH,      // Playing from search results
        SINGLE_SONG  // Playing a single song
    }
    private var currentBrowsingPlaylistId: String? = null  // Track which playlist is currently being browsed
    private var currentBrowsingPlaylistSongs: List<Song> = emptyList()  // Cache songs from the browsed playlist
    private var searchResultsCache: List<Song> = emptyList()  // Cache search results for Android Auto playback
    private var searchResultsCacheTime: Long = 0  // Track when search results were cached
    
    // Helper to access playback context from consolidated manager

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_playback_channel"
        private const val CHANNEL_NAME = "Music Playback"
        
        // Media Browser IDs
        private const val MEDIA_ROOT_ID = "root"
        private const val MEDIA_PLAYLISTS_ID = "playlists"
        private const val MEDIA_QUEUE_ID = "queue"
        private const val MEDIA_RECENT_ID = "recent"
        private const val MEDIA_FAVORITES_ID = "favorites"
        
        const val ACTION_PLAY_SONG = "com.melodee.autoplayer.ACTION_PLAY_SONG"
        const val ACTION_PAUSE = "com.melodee.autoplayer.ACTION_PAUSE"
        const val ACTION_RESUME = "com.melodee.autoplayer.ACTION_RESUME"
        const val ACTION_STOP = "com.melodee.autoplayer.ACTION_STOP"
        const val ACTION_PREVIOUS = "com.melodee.autoplayer.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.melodee.autoplayer.ACTION_NEXT"
        const val ACTION_SEEK_TO = "com.melodee.autoplayer.ACTION_SEEK_TO"
        const val ACTION_SET_PLAYLIST = "com.melodee.autoplayer.ACTION_SET_PLAYLIST"
        const val ACTION_SET_SEARCH_RESULTS = "com.melodee.autoplayer.ACTION_SET_SEARCH_RESULTS"
        const val ACTION_TOGGLE_SHUFFLE = "com.melodee.autoplayer.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_TOGGLE_REPEAT = "com.melodee.autoplayer.ACTION_TOGGLE_REPEAT"
        const val ACTION_TOGGLE_FAVORITE = "com.melodee.autoplayer.ACTION_TOGGLE_FAVORITE"
        const val ACTION_CLEAR_QUEUE = "com.melodee.autoplayer.ACTION_CLEAR_QUEUE"
        const val EXTRA_SONG = "com.melodee.autoplayer.EXTRA_SONG"
        const val EXTRA_POSITION = "com.melodee.autoplayer.EXTRA_POSITION"
        const val EXTRA_PLAYLIST = "com.melodee.autoplayer.EXTRA_PLAYLIST"
        const val EXTRA_SEARCH_RESULTS = "com.melodee.autoplayer.EXTRA_SEARCH_RESULTS"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    
    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "MusicService onCreate called")
        
        settingsManager = SettingsManager(this)
        
        // Get authentication manager from application
        val app = application as MelodeeApplication
        authenticationManager = app.authenticationManager
        
        // Initialize consolidated playback manager
        playbackManager = MusicPlaybackManager(this)
        
        // Authentication is now handled by AuthenticationManager
        // It will automatically restore authentication if available
        
        // Debug current state
        debugAuthenticationState()
        
        // Initialize audio manager
        setupAudioManager()
        
        createNotificationChannel()
        setupPlayer()
        setupMediaSession()
        initializeScrobbleManager()
        
        // Add debugging for Android Auto
        populateTestContentForAndroidAuto()
        
        Log.d("MusicService", "Service initialization complete")
    }

    private fun debugAuthenticationState() {
        try {
            val isAuthenticated = authenticationManager.isAuthenticated.value
            val currentUser = authenticationManager.getCurrentUser()
            val networkHasToken = NetworkModule.isAuthenticated()
            
            Log.i("MusicService", "=== Authentication Debug ===")
            Log.i("MusicService", "AuthenticationManager authenticated: $isAuthenticated")
            Log.i("MusicService", "NetworkModule authenticated: $networkHasToken")
            if (currentUser != null) {
                Log.i("MusicService", "Current user: ${currentUser.username} (${currentUser.email})")
                Log.i("MusicService", "Server URL: ${currentUser.serverUrl}")
            } else {
                Log.i("MusicService", "No current user")
            }
            Log.i("MusicService", "Search should work: ${isAuthenticated && networkHasToken}")
            Log.i("MusicService", "=============================")
        } catch (e: Exception) {
            Log.e("MusicService", "Error debugging authentication state", e)
        }
    }

    private fun initializeScrobbleManager() {
        try {
            if (NetworkModule.isAuthenticated()) {
                val scrobbleApi = NetworkModule.getScrobbleApi()
                scrobbleManager = ScrobbleManager(scrobbleApi)
                Log.d("MusicService", "ScrobbleManager initialized")
            } else {
                Log.w("MusicService", "Not authenticated, scrobbling disabled")
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Failed to initialize ScrobbleManager", e)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Log.d("MusicService", "onGetRoot called for package: $clientPackageName, uid: $clientUid")
        Log.d("MusicService", "Root hints: $rootHints")
        
        // Check if the client is Android Auto
        val isAndroidAuto = rootHints?.getBoolean("android.service.media.extra.RECENT") == true ||
                          clientPackageName == "com.google.android.projection.gearhead" ||
                          clientPackageName == "com.google.android.gms" ||
                          clientPackageName.contains("android.auto") ||
                          clientPackageName.contains("gearhead")
        
        Log.d("MusicService", "Is Android Auto client: $isAndroidAuto (package: $clientPackageName)")
        
        // Allow all clients to browse the media library
        val rootExtras = Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
            
            // Add Android Auto specific extras
            if (isAndroidAuto) {
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                putInt("android.media.extras.CONTENT_STYLE_GROUP_TITLE_HINT", 1)
                // Add more Android Auto specific styling hints
                putInt("android.media.browse.CONTENT_STYLE_LIST_ITEM_HINT_VALUE", 1)
                putInt("android.media.browse.CONTENT_STYLE_GRID_ITEM_HINT_VALUE", 2)
                
                // Add voice recognition metadata
                putString("android.media.extras.MEDIA_TITLE", "Melodee")
                putString("android.media.extras.MEDIA_DESCRIPTION", "Music Player")
                putStringArray("android.media.extras.MEDIA_KEYWORDS", arrayOf("melodee", "music", "player"))
            }
        }
        
        Log.d("MusicService", "Returning BrowserRoot with ID: $MEDIA_ROOT_ID")
        return BrowserRoot(MEDIA_ROOT_ID, rootExtras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d("MusicService", "onLoadChildren called for parentId: $parentId")
        
        when (parentId) {
            MEDIA_ROOT_ID -> {
                // Clear browsing playlist context when returning to root
                currentBrowsingPlaylistId = null
                currentBrowsingPlaylistSongs = emptyList()
                // Keep search results cache as user might still want to play from search
                loadRootItems(result)
            }
            
            MEDIA_PLAYLISTS_ID -> {
                // Clear browsing playlist context when browsing playlists list
                currentBrowsingPlaylistId = null
                currentBrowsingPlaylistSongs = emptyList()
                // Clear search cache when browsing playlists
                searchResultsCache = emptyList()
                loadPlaylists(result)
            }
            
            MEDIA_QUEUE_ID -> {
                // Clear browsing playlist context when browsing queue
                currentBrowsingPlaylistId = null
                currentBrowsingPlaylistSongs = emptyList()
                // Clear search cache when browsing queue
                searchResultsCache = emptyList()
                loadCurrentQueue(result)
            }
            
            "no_content" -> {
                loadHelpInfo(result)
            }
            
            MEDIA_RECENT_ID, MEDIA_FAVORITES_ID -> {
                // For now, return empty list - can be implemented later
                result.sendResult(mutableListOf())
            }
            
            else -> {
                // Check if it's a playlist ID (they start with "playlist_")
                if (parentId.startsWith("playlist_")) {
                    val playlistId = parentId.removePrefix("playlist_")
                    loadPlaylistSongs(playlistId, result)
                } else {
                    Log.d("MusicService", "Unknown parentId: $parentId, returning empty list")
                    result.sendResult(mutableListOf())
                }
            }
        }
    }

    private fun loadRootItems(result: Result<List<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        
        // Add Playlists category
        mediaItems.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(MEDIA_PLAYLISTS_ID)
                    .setTitle("Playlists")
                    .setSubtitle("Browse your playlists")
                    .setIconUri(android.net.Uri.parse("android.resource://$packageName/${R.drawable.ic_playlist_music}"))
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
        
        // Add Current Queue category
        mediaItems.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(MEDIA_QUEUE_ID)
                    .setTitle("Current Queue")
                    .setSubtitle("Currently playing songs")
                    .setIconUri(android.net.Uri.parse("android.resource://$packageName/${R.drawable.ic_library_music}"))
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            )
        )
        
        Log.d("MusicService", "Returning ${mediaItems.size} root items for Android Auto")
        result.sendResult(mediaItems)
    }

    private fun loadPlaylists(result: Result<List<MediaBrowserCompat.MediaItem>>) {
        // Check authentication first
        val authToken = NetworkModule.getAuthToken()
        if (authToken.isNullOrEmpty()) {
            Log.w("MusicService", "No auth token available for playlists")
            val errorItems = listOf(
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("auth_required")
                        .setTitle("Login Required")
                        .setSubtitle("Please login to the app first")
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
            result.sendResult(errorItems)
            return
        }

        // Detach result for async operation
        result.detach()
        
        // Fetch playlists from API
        serviceScope.launch {
            try {
                Log.d("MusicService", "Fetching playlists from API")
                val playlists = fetchUserPlaylists()
                
                val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
                
                if (playlists.isNotEmpty()) {
                    playlists.forEach { playlist ->
                        mediaItems.add(
                            MediaBrowserCompat.MediaItem(
                                MediaDescriptionCompat.Builder()
                                    .setMediaId("playlist_${playlist.id}")
                                    .setTitle(playlist.name)
                                    .setSubtitle("${playlist.songCount} songs")
                                    .setDescription(playlist.description)
                                    .setIconUri(
                                        android.net.Uri.parse(
                                            playlist.imageUrl.takeIf { it.isNotBlank() }
                                                ?: "android.resource://$packageName/${R.drawable.ic_playlist_music}"
                                        )
                                    )
                                    .build(),
                                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                            )
                        )
                    }
                } else {
                    mediaItems.add(
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("no_playlists")
                                .setTitle("No Playlists Found")
                                .setSubtitle("Create playlists in the app to see them here")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
                
                Log.d("MusicService", "Returning ${mediaItems.size} playlists for Android Auto")
                result.sendResult(mediaItems)
                
            } catch (e: Exception) {
                Log.e("MusicService", "Error fetching playlists", e)
                val errorItems = listOf(
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId("playlists_error")
                            .setTitle("Error Loading Playlists")
                            .setSubtitle("Unable to load playlists: ${e.message}")
                            .build(),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
                )
                result.sendResult(errorItems)
            }
        }
    }

    private fun loadCurrentQueue(result: Result<List<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        
        // First try current queue, then playlist manager
        val songs = if (queueManager().currentQueue.value.isNotEmpty()) {
            queueManager().currentQueue.value
        } else {
            playlistManager().currentPlaylist.value
        }
        
        Log.d("MusicService", "Loading ${songs.size} songs for current queue")
        Log.d("MusicService", "Current song: ${currentSong?.title}")
        Log.d("MusicService", "Queue manager songs: ${queueManager().currentQueue.value.size}")
        Log.d("MusicService", "Playlist manager songs: ${playlistManager().currentPlaylist.value.size}")
        
        if (songs.isNotEmpty()) {
            songs.forEach { song ->
                mediaItems.add(createMediaItem(song))
            }
            
            Log.d("MusicService", "Current Queue: Returning ${mediaItems.size} songs")
        } else {
            // Show "no content" message instead of empty list
            mediaItems.add(
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("empty_queue")
                        .setTitle("Queue is Empty")
                        .setSubtitle("Play songs from a playlist to populate your queue")
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
            
            Log.d("MusicService", "Current Queue: No songs available, showing empty message")
        }
        
        result.sendResult(mediaItems)
    }

    private fun loadHelpInfo(result: Result<List<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        
        mediaItems.add(
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("help_info")
                    .setTitle("How to Add Music")
                    .setSubtitle("Open the Melodee app and browse playlists or search for songs")
                    .build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            )
        )
        
        Log.d("MusicService", "Returning help information")
        result.sendResult(mediaItems)
    }

    private fun loadPlaylistSongs(playlistId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        // Check authentication first
        val authToken = NetworkModule.getAuthToken()
        if (authToken.isNullOrEmpty()) {
            Log.w("MusicService", "No auth token available for playlist songs")
            val errorItems = listOf(
                MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("auth_required")
                        .setTitle("Login Required")
                        .setSubtitle("Please login to the app first")
                        .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
            result.sendResult(errorItems)
            return
        }

        // Detach result for async operation
        result.detach()
        
        // Fetch playlist songs from API
        serviceScope.launch {
            try {
                Log.d("MusicService", "Fetching songs for playlist: $playlistId")
                // Set the current browsing playlist ID
                currentBrowsingPlaylistId = playlistId
                val songs = fetchPlaylistSongs(playlistId)
                
                // Cache the songs for later use in onPlayFromMediaId
                currentBrowsingPlaylistSongs = songs
                
                val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
                
                if (songs.isNotEmpty()) {
                    songs.forEach { song ->
                        mediaItems.add(createMediaItem(song, playlistId))
                    }
                } else {
                    mediaItems.add(
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("empty_playlist")
                                .setTitle("Playlist is Empty")
                                .setSubtitle("Add songs to this playlist in the app")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
                
                Log.d("MusicService", "Returning ${mediaItems.size} songs for playlist $playlistId")
                result.sendResult(mediaItems)
                
            } catch (e: Exception) {
                Log.e("MusicService", "Error fetching playlist songs", e)
                val errorItems = listOf(
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId("playlist_error")
                            .setTitle("Error Loading Playlist")
                            .setSubtitle("Unable to load playlist: ${e.message}")
                            .build(),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
                )
                result.sendResult(errorItems)
            }
        }
    }

    // API methods for fetching playlists and songs
    private suspend fun fetchUserPlaylists(): List<Playlist> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MusicService", "Fetching user playlists from API")
                val musicApi = NetworkModule.getMusicApi()
                val playlistsResponse = musicApi.getPlaylists(1, 50) // Get first 50 playlists
                
                Log.d("MusicService", "API returned ${playlistsResponse.data.size} playlists")
                return@withContext playlistsResponse.data
                
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to fetch playlists", e)
                throw e
            }
        }
    }

    private suspend fun fetchPlaylistSongs(playlistId: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MusicService", "Fetching songs for playlist: $playlistId")
                
                // Verify authentication before making API call
                if (!verifyAuthentication()) {
                    Log.e("MusicService", "Cannot fetch playlist songs - not authenticated")
                    throw Exception("Not authenticated")
                }
                
                val musicApi = NetworkModule.getMusicApi()
                val songsResponse = musicApi.getPlaylistSongs(playlistId, 1, 100) // Get first 100 songs
                
                Log.d("MusicService", "API returned ${songsResponse.data.size} songs for playlist $playlistId")
                
                // Log stream URLs for debugging
                songsResponse.data.take(3).forEach { song ->
                    Log.d("MusicService", "Sample song: ${song.title} - Stream URL: ${song.streamUrl}")
                }
                
                return@withContext songsResponse.data
                
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to fetch playlist songs for $playlistId", e)
                throw e
            }
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d("MusicService", "Android Auto onSearch called with query: '$query'")
        Log.d("MusicService", "Extras: $extras")
        
        if (query.isEmpty()) {
            Log.d("MusicService", "Empty query, returning empty results")
            result.sendResult(mutableListOf())
            return
        }

        // Detach the result to perform async API call
        result.detach()
        
        // Perform API search in background
        serviceScope.launch {
            try {
                Log.d("MusicService", "Starting async API search for: '$query'")
                val searchResults = performApiSearch(query)
                
                // Send results back to Android Auto
                result.sendResult(searchResults)
                Log.i("MusicService", "Android Auto search completed: ${searchResults.size} results for '$query'")
                
            } catch (e: Exception) {
                Log.e("MusicService", "Error performing API search for '$query'", e)
                
                // Send error result with detailed info
                val errorResults = mutableListOf<MediaBrowserCompat.MediaItem>()
                errorResults.add(
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId("search_error")
                            .setTitle("Search Error")
                            .setSubtitle("API Error: ${e.message}")
                            .setDescription("Failed to search for '$query'. Check network and authentication.")
                            .build(),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
                )
                result.sendResult(errorResults)
            }
        }
    }

    private suspend fun performApiSearch(query: String): List<MediaBrowserCompat.MediaItem> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MusicService", "Starting API search for: '$query'")
                
                // Check if we have authentication
                val authToken = NetworkModule.getAuthToken()
                Log.d("MusicService", "Auth token available: ${!authToken.isNullOrEmpty()}")
                
                if (authToken.isNullOrEmpty()) {
                    Log.w("MusicService", "No auth token available for search")
                    return@withContext listOf(
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("auth_required")
                                .setTitle("Login Required")
                                .setSubtitle("Please login to the app first to search")
                                .setDescription("Open the Melodee app and login to enable search")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }

                Log.d("MusicService", "Making API search call")
                // Perform API search
                val musicApi = NetworkModule.getMusicApi()
                val searchResponse = musicApi.searchSongs(query, 1, 10) // Search first page, limit 10 results
                
                Log.d("MusicService", "API search response received: ${searchResponse.data.size} songs")
                
                val searchResults = mutableListOf<MediaBrowserCompat.MediaItem>()
                
                if (searchResponse.data.isNotEmpty()) {
                    Log.i("MusicService", "API search found ${searchResponse.data.size} songs for '$query'")
                    
                    // Cache the search results for Android Auto playback
                    searchResultsCache = searchResponse.data
                    searchResultsCacheTime = System.currentTimeMillis()
                    Log.d("MusicService", "Cached ${searchResultsCache.size} search results at ${searchResultsCacheTime}")
                    
                    searchResponse.data.forEach { song ->
                        searchResults.add(createMediaItem(song, fromSearch = true))
                        Log.d("MusicService", "Added song: ${song.title} by ${song.artist.name}")
                    }
                } else {
                    Log.i("MusicService", "API search found no results for: '$query'")
                    searchResults.add(
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("no_results")
                                .setTitle("No Results Found")
                                .setSubtitle("No songs found for \"$query\"")
                                .setDescription("Try a different search term or check your spelling")
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
                
                Log.d("MusicService", "Returning ${searchResults.size} search results")
                return@withContext searchResults
                
            } catch (e: Exception) {
                Log.e("MusicService", "API search failed for '$query'", e)
                Log.e("MusicService", "Exception type: ${e.javaClass.simpleName}")
                Log.e("MusicService", "Exception message: ${e.message}")
                throw e
            }
        }
    }

    private fun createMediaItem(song: Song, playlistId: String? = null, fromSearch: Boolean = false): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
            .setMediaId(song.id.toString())
            .setTitle(song.title)
            .setSubtitle(song.artist.name)
            .setDescription(song.album.name)
            .setIconUri(android.net.Uri.parse(song.thumbnailUrl))
        
        // Add context extras
        val extras = Bundle()
        if (playlistId != null) {
            extras.putString("from_playlist", playlistId)
        }
        if (fromSearch) {
            extras.putBoolean("from_search", true)
        }
        
        if (playlistId != null || fromSearch) {
            builder.setExtras(extras)
        }
        
        return MediaBrowserCompat.MediaItem(
            builder.build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicService", "onStartCommand called with intent: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_PLAY_SONG -> {
                val song = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_SONG, Song::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_SONG)
                }
                Log.d("MusicService", "Received play command for song: ${song?.title}")
                if (song != null) {
                    // Set context to single song if no queue is set
                    if (queueManager().getQueueSize() == 0) {
                        // Context set by playbackManager.setQueue call
                    }
                    queueManager().addToQueue(song)
                    playlistManager().playSong(song)
                    
                    // Notify Android Auto that the queue has changed
                    notifyQueueChanged()
                    
                    playSong(song)
                } else {
                    Log.e("MusicService", "Received null song in intent")
                }
            }
            ACTION_SET_PLAYLIST -> {
                val songs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_PLAYLIST, Song::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(EXTRA_PLAYLIST)
                }
                val startIndex = intent.getIntExtra("START_INDEX", 0)
                // Capture playlist pagination context if provided
                remotePlaylistId = intent.getStringExtra("PLAYLIST_ID")
                nextPlaylistPage = intent.getIntExtra("NEXT_PAGE", 1)
                hasMorePlaylistPages = intent.getBooleanExtra("HAS_MORE", false)
                if (songs != null) {
                    // Context set by playbackManager.setQueue call
                    queueManager().setQueue(songs, startIndex)
                    playlistManager().setPlaylist(songs, startIndex)
                    
                    // Notify Android Auto that the queue has changed
                    notifyQueueChanged()
                    
                    if (songs.isNotEmpty() && startIndex < songs.size) {
                        playSong(songs[startIndex])
                        // Proactively prefetch next page if we're near the end of this page
                        maybePrefetchNextPageIfNeeded()
                    }
                }
            }
            ACTION_SET_SEARCH_RESULTS -> {
                val songs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_SEARCH_RESULTS, Song::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(EXTRA_SEARCH_RESULTS)
                }
                val startIndex = intent.getIntExtra("START_INDEX", 0)
                if (songs != null) {
                    // Context set by playbackManager.setQueue call
                    queueManager().setQueue(songs, startIndex)
                    playlistManager().setPlaylist(songs, startIndex)
                    
                    // Notify Android Auto that the queue has changed
                    notifyQueueChanged()
                    
                    if (songs.isNotEmpty() && startIndex < songs.size) {
                        playSong(songs[startIndex])
                    }
                }
            }
            ACTION_PAUSE -> {
                Log.d("MusicService", "Received pause command")
                pausePlayback()
            }
            ACTION_RESUME -> {
                Log.d("MusicService", "Received resume command")
                resumePlayback()
            }
            ACTION_STOP -> {
                Log.d("MusicService", "Received stop command")
                stopPlayback()
            }
            ACTION_PREVIOUS -> {
                Log.d("MusicService", "Received previous command")
                skipToPrevious()
            }
            ACTION_NEXT -> {
                Log.d("MusicService", "Received next command")
                skipToNext()
            }
            ACTION_SEEK_TO -> {
                val position = intent.getLongExtra(EXTRA_POSITION, 0)
                player?.seekTo(position)
            }
            ACTION_TOGGLE_SHUFFLE -> {
                Log.d("MusicService", "Received toggle shuffle command")
                queueManager().toggleShuffle()
                updateMediaSessionPlaybackState()
            }
            ACTION_TOGGLE_REPEAT -> {
                Log.d("MusicService", "Received toggle repeat command")
                queueManager().toggleRepeat()
                updateMediaSessionPlaybackState()
            }
            ACTION_CLEAR_QUEUE -> {
                Log.d("MusicService", "Received clear queue command")
                clearQueue()
            }
            ACTION_TOGGLE_FAVORITE -> {
                Log.d("MusicService", "Received toggle favorite command")
                toggleCurrentSongFavorite()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return if (intent.action == "android.media.browse.MediaBrowserService") {
            super.onBind(intent)
        } else {
            binder
        }
    }
    
    @UnstableApi
    override fun onDestroy() {
        Log.d("MusicService", "onDestroy called")
        
        // Stop position updates
        stopPositionUpdates()
        
        // Abandon audio focus
        abandonAudioFocus()
        
        // Clean up scrobble manager
        scrobbleManager?.destroy()
        scrobbleManager = null
        
        // Cancel coroutine scope
        serviceScope.cancel()
        
        mediaSession?.release()
        mediaSession = null
        
        // Release playback manager (which handles player cleanup)
        playbackManager.release()
        player = null
        
        // Clear on-disk media cache on service shutdown
        MediaCache.clearCache(applicationContext)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(song: Song? = null): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (player?.isPlaying == true) {
            NotificationCompat.Action(
                R.drawable.ic_pause_auto,
                "Pause",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play_auto,
                "Play",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, MusicService::class.java).apply { action = ACTION_RESUME },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "Melodee")
            .setContentText(song?.artist?.name ?: "Music Player")
            .setSubText(song?.album?.name)
            .setSmallIcon(R.drawable.ic_library_music)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_skip_previous_auto,
                "Previous",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(playPauseAction)
            .addAction(
                R.drawable.ic_skip_next_auto,
                "Next",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun playSong(song: Song) {
        Log.i("MusicService", "=== ATTEMPTING TO PLAY SONG ===")
        Log.i("MusicService", "Song ID: ${song.id}")
        Log.i("MusicService", "Song title: ${song.title}")
        Log.i("MusicService", "Song artist: ${song.artist.name}")
        Log.i("MusicService", "Stream URL: ${song.streamUrl}")
        Log.i("MusicService", "Stream URL length: ${song.streamUrl.length}")
        Log.i("MusicService", "Stream URL valid: ${song.streamUrl.isNotBlank() && song.streamUrl.startsWith("http")}")
        
        // Validate stream URL
        if (song.streamUrl.isBlank() || !song.streamUrl.startsWith("http")) {
            Log.e("MusicService", "Invalid stream URL: '${song.streamUrl}' - cannot play song")
            return
        }
        
        // Request audio focus before starting playback - be aggressive about it
        Log.d("MusicService", "Requesting audio focus...")
        var focusGained = requestAudioFocus()
        
        if (!focusGained) {
            Log.w("MusicService", "Initial audio focus request failed - trying force method for Android Auto")
            focusGained = forceAudioFocusForAndroidAuto()
        }
        
        if (!focusGained) {
            Log.w("MusicService", "Force method failed - trying super aggressive approach")
            focusGained = superAggressiveAudioFocus()
        }
        
        if (!focusGained) {
            Log.e("MusicService", "Could not gain audio focus after all attempts, cannot start playback")
            return
        }
        Log.d("MusicService", "Audio focus granted successfully")
        
        // Stop tracking previous song
        currentSong?.let { prevSong ->
            Log.d("MusicService", "Stopping scrobble tracking for previous song: ${prevSong.title}")
            scrobbleManager?.stopTracking(prevSong.id.toString())
        }
        
        try {
            Log.d("MusicService", "Creating MediaItem from URI: ${song.streamUrl}")
            val mediaItem = MediaItem.fromUri(song.streamUrl)
            Log.d("MusicService", "Created media item successfully")
            
            val player = this.player
            if (player == null) {
                Log.e("MusicService", "Player is null! Cannot set media item")
                return
            }
            
            Log.d("MusicService", "Setting media item in player...")
            player.setMediaItem(mediaItem)
            Log.d("MusicService", "Media item set successfully")
            
            Log.d("MusicService", "Preparing player...")
            player.prepare()
            Log.d("MusicService", "Player prepared")
            
            Log.d("MusicService", "Starting playback...")
            player.play()
            Log.d("MusicService", "Playback started successfully")
            
            // Update current song
            currentSong = song
            Log.d("MusicService", "Updated current song reference")

            // Ensure MediaSession is active and ready
            mediaSession?.isActive = true
            Log.d("MusicService", "MediaSession activated")
            
            // Update MediaSession metadata
            updateMediaSessionMetadata(song)
            Log.d("MusicService", "MediaSession metadata updated")

            // Start as foreground service with notification
            val notification = createNotification(song)
            startForeground(NOTIFICATION_ID, notification)
            Log.d("MusicService", "Started foreground service with notification")
            
            Log.i("MusicService", "=== SONG PLAYBACK SETUP COMPLETE ===")
        } catch (e: Exception) {
            Log.e("MusicService", "=== ERROR PLAYING SONG ===")
            Log.e("MusicService", "Error type: ${e.javaClass.simpleName}")
            Log.e("MusicService", "Error message: ${e.message}")
            Log.e("MusicService", "Full error:", e)
            e.printStackTrace()
            
            // Handle the failure with enhanced error reporting
            handlePlaybackFailure(song, e)
        }
    }

    
    @UnstableApi
    private fun setupPlayer() {
        Log.d("MusicService", "Setting up ExoPlayer via MusicPlaybackManager")
        
        // Initialize player through consolidated manager
        player = playbackManager.initializePlayer().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    Log.d("MusicService", "Playback state changed: $state")
                    when (state) {
                        Player.STATE_READY -> {
                            Log.d("MusicService", "Player ready")
                            updateMediaSessionPlaybackState()
                            updateMediaSessionMetadataWithDuration()
                            
                            // Start scrobble tracking when player is ready
                            currentSong?.let { song ->
                                val duration = this@apply.duration
                                if (duration > 0) {
                                    scrobbleManager?.startTracking(song, duration)
                                    Log.d("MusicService", "Started scrobble tracking for: ${song.title}, duration: $duration")
                                }
                            }

                            // Prefetch upcoming songs to tolerate spotty networks
                            prefetchUpcoming()
                        }
                        Player.STATE_BUFFERING -> {
                            Log.d("MusicService", "Player buffering")
                            updateMediaSessionPlaybackState()
                        }
                        Player.STATE_ENDED -> {
                            Log.d("MusicService", "Player ended - auto-playing next song")
                            updateMediaSessionPlaybackState()
                            
                            // Stop scrobble tracking when song ends
                            currentSong?.let { song ->
                                scrobbleManager?.stopTracking(song.id.toString())
                            }
                            
                            // Auto-play next song when current song ends
                            skipToNext()
                        }
                        Player.STATE_IDLE -> {
                            Log.d("MusicService", "Player idle")
                            updateMediaSessionPlaybackState()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d("MusicService", "Is playing changed: $isPlaying")
                    updateMediaSessionPlaybackState()
                    
                    // Start or stop position updates based on playback state
                    if (isPlaying) {
                        startPositionUpdates()
                        // Prefetch again in case queue position changed
                        prefetchUpcoming()
                    } else {
                        stopPositionUpdates()
                    }
                    
                    // Update notification
                    currentSong?.let { song ->
                        startForeground(NOTIFICATION_ID, createNotification(song))
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("MusicService", "Player error: ${error.message}", error)
                    error.printStackTrace()
                    
                    // Stop scrobble tracking on error
                    currentSong?.let { song ->
                        scrobbleManager?.stopTracking(song.id.toString())
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: androidx.media3.common.Player.PositionInfo,
                    newPosition: androidx.media3.common.Player.PositionInfo,
                    reason: Int
                ) {
                    // Update scrobble manager with current position for seeking
                    currentSong?.let { song ->
                        val currentPos = this@apply.currentPosition
                        val duration = this@apply.duration
                        if (duration > 0) {
                            scrobbleManager?.updatePlaybackPosition(song.id.toString(), currentPos, duration)
                        }
                    }
                }
            })
        }
        Log.d("MusicService", "ExoPlayer setup complete")
    }

    private fun updateMediaSessionPlaybackState() {
        val player = this.player ?: return
        val mediaSession = this.mediaSession ?: return

        val state = when {
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            player.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            player.playbackState == Player.STATE_READY -> PlaybackStateCompat.STATE_PAUSED
            player.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_NONE
        }

        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE

        val shuffleMode = if (queueManager().isShuffleEnabled()) {
            PlaybackStateCompat.SHUFFLE_MODE_ALL
        } else {
            PlaybackStateCompat.SHUFFLE_MODE_NONE
        }

        val repeatMode = when (queueManager().getRepeatMode()) {
            QueueManager.RepeatMode.NONE -> PlaybackStateCompat.REPEAT_MODE_NONE
            QueueManager.RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
            QueueManager.RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
        }

        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setState(state, player.currentPosition, 1.0f)
            .setActions(actions)

        // Add favorite action for current song
        currentSong?.let { song ->
            val favoriteIcon = if (song.userStarred) {
                android.R.drawable.btn_star_big_on
            } else {
                android.R.drawable.btn_star_big_off
            }
            
            val favoriteText = if (song.userStarred) {
                "Remove from Favorites"
            } else {
                "Add to Favorites"
            }
            
            // Create custom action for favorite toggle
            val favoriteAction = PlaybackStateCompat.CustomAction.Builder(
                ACTION_TOGGLE_FAVORITE,
                favoriteText,
                favoriteIcon
            ).build()
            
            playbackStateBuilder.addCustomAction(favoriteAction)
            Log.d("MusicService", "Added favorite custom action - icon: $favoriteIcon, text: $favoriteText, starred: ${song.userStarred}")
        }

        val playbackState = playbackStateBuilder.build()

        mediaSession.setPlaybackState(playbackState)
        mediaSession.setShuffleMode(shuffleMode)
        mediaSession.setRepeatMode(repeatMode)
        
        Log.d("MusicService", "Updated MediaSession playback state: $state, position: ${player.currentPosition}")
    }

    private fun updateMediaSessionMetadata(song: Song) {
        val mediaSession = this.mediaSession ?: return
        val player = this.player ?: return

        val duration = if (player.duration > 0) player.duration else -1L
        
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.id.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artist.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.imageUrl)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, song.thumbnailUrl)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build()

        mediaSession.setMetadata(metadata)
        Log.d("MusicService", "Updated MediaSession metadata for: ${song.title}, duration: $duration")
    }

    private fun updateMediaSessionMetadataWithDuration() {
        currentSong?.let { song ->
            updateMediaSessionMetadata(song)
        }
    }

    private fun pausePlayback() {
        Log.d("MusicService", "Pausing playback")
        player?.pause()
        updateMediaSessionPlaybackState()
    }

    private fun resumePlayback() {
        Log.d("MusicService", "Resuming playback")
        
        // Request audio focus before resuming
        if (!hasAudioFocus && !requestAudioFocus()) {
            Log.w("MusicService", "Could not gain audio focus for resume")
            return
        }
        
        player?.play()
        updateMediaSessionPlaybackState()
    }

    private fun startPlaybackFromCurrentContext() {
        Log.d("MusicService", "Starting playback from current context")
        
        // First, try to play from currently browsed playlist
        if (currentBrowsingPlaylistId != null && currentBrowsingPlaylistSongs.isNotEmpty()) {
            Log.d("MusicService", "Playing from browsed playlist: $currentBrowsingPlaylistId")
            val firstSong = currentBrowsingPlaylistSongs.first()
            
            // Set the playlist context and play the first song
            // Context set by playbackManager.setQueue call
            queueManager().setQueue(currentBrowsingPlaylistSongs, 0)
            playlistManager().setPlaylist(currentBrowsingPlaylistSongs, 0)
            
            // Notify Android Auto that the queue has changed
            notifyQueueChanged()
            
            playSong(firstSong)
            return
        }
        
        // Second, try to play from current queue
        val queueSongs = queueManager().currentQueue.value
        if (queueSongs.isNotEmpty()) {
            Log.d("MusicService", "Playing from current queue")
            val firstSong = queueSongs.first()
            // Context set by playbackManager.setQueue call
            playlistManager().setPlaylist(queueSongs, 0)
            playSong(firstSong)
            return
        }
        
        // Third, try to play from current playlist
        val playlistSongs = playlistManager().currentPlaylist.value
        if (playlistSongs.isNotEmpty()) {
            Log.d("MusicService", "Playing from current playlist")
            val firstSong = playlistSongs.first()
            // Context set by playbackManager.setQueue call
            queueManager().setQueue(playlistSongs, 0)
            
            // Notify Android Auto that the queue has changed
            notifyQueueChanged()
            playSong(firstSong)
            return
        }
        
        // No context available
        Log.w("MusicService", "No playback context available - cannot start playback")
    }

    private fun stopPlayback() {
        Log.d("MusicService", "Stopping playback")
        player?.stop()
        currentSong = null
        
        // Abandon audio focus when stopping playback
        abandonAudioFocus()
        
        updateMediaSessionPlaybackState()
        
        // Stop foreground service and remove notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d("MusicService", "Stopped foreground service and removed notification")
    }

    
    @UnstableApi
    private fun prefetchUpcoming(count: Int = 2) {
        prefetchJob?.cancel()
        prefetchJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val queue = queueManager().currentQueue.value
                val index = queueManager().currentIndex.value
                if (queue.isEmpty() || index < 0) return@launch
                for (i in 1..count) {
                    val nextIdx = index + i
                    if (nextIdx in queue.indices) {
                        val url = queue[nextIdx].streamUrl
                        MediaCache.prefetchUrl(this@MusicService, url)
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicService", "Prefetch job error: ${e.message}")
            }
        }
    }

    private fun skipToNext() {
        Log.d("MusicService", "Skipping to next song, context: ${getCurrentPlaybackContext()}")
        
        when (getCurrentPlaybackContext()) {
            PlaybackContext.PLAYLIST, PlaybackContext.SEARCH -> {
                // Use queue manager's skipToNext which handles index updates properly
                val nextSong = queueManager().skipToNext()
                if (nextSong != null) {
                    Log.d("MusicService", "Playing next song: ${nextSong.title}")
                    // Update playlist manager to keep them synchronized
                    playlistManager().playSong(nextSong)
                    playSong(nextSong)
                    Log.d("MusicService", "Successfully started playing next song: ${nextSong.title}")
                    // Proactively prefetch next page if we're nearing end of current queue page
                    maybePrefetchNextPageIfNeeded()
                } else {
                    Log.d("MusicService", "No next song available in current queue")
                    // Attempt to load the next page of the playlist if available
                    if (getCurrentPlaybackContext() == PlaybackContext.PLAYLIST && hasMorePlaylistPages && remotePlaylistId != null && !isFetchingNextPlaylistPage) {
                        Log.d("MusicService", "Fetching next playlist page: $nextPlaylistPage for $remotePlaylistId")
                        isFetchingNextPlaylistPage = true
                        serviceScope.launch {
                            val appended = fetchAndAppendNextPlaylistPage()
                            isFetchingNextPlaylistPage = false
                            if (appended) {
                                // Try skipping again now that queue grew
                                withContext(Dispatchers.Main) { skipToNext() }
                            } else {
                                Log.d("MusicService", "No more songs to append; stopping playback")
                                withContext(Dispatchers.Main) { stopPlayback() }
                            }
                        }
                    } else {
                        Log.d("MusicService", "No more pages or missing playlist context; stopping playback")
                        stopPlayback()
                    }
                }
            }
            PlaybackContext.SINGLE_SONG -> {
                // For single song context, just stop playback
                Log.d("MusicService", "Single song finished, stopping playback")
                stopPlayback()
            }
        }
    }

    private suspend fun fetchAndAppendNextPlaylistPage(): Boolean {
        val playlistId = remotePlaylistId ?: return false
        if (!hasMorePlaylistPages) return false
        return try {
            val musicApi = NetworkModule.getMusicApi()
            Log.d("MusicService", "Requesting playlist page $nextPlaylistPage for $playlistId")
            val response = withContext(Dispatchers.IO) { musicApi.getPlaylistSongs(playlistId, nextPlaylistPage) }
            val newSongs = response.data
            Log.d("MusicService", "Fetched ${newSongs.size} new songs (hasNext=${response.meta.hasNext})")
            if (newSongs.isNotEmpty()) {
                // Append without altering current playing index
                queueManager().appendToQueue(newSongs)
                // Keep playlist manager in sync (it just mirrors queue for now)
                playlistManager().setPlaylist(queueManager().currentQueue.value, queueManager().currentIndex.value)
                // Update pagination state
                hasMorePlaylistPages = response.meta.hasNext
                nextPlaylistPage = response.meta.currentPage + 1
                // Inform AA clients that queue changed
                notifyQueueChanged()
                true
            } else {
                hasMorePlaylistPages = false
                false
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error fetching next playlist page", e)
            false
        }
    }

    private fun maybePrefetchNextPageIfNeeded() {
        try {
            if (getCurrentPlaybackContext() != PlaybackContext.PLAYLIST) return
            if (!hasMorePlaylistPages || remotePlaylistId == null || isFetchingNextPlaylistPage) return
            val size = queueManager().currentQueue.value.size
            val idx = queueManager().currentIndex.value
            val remaining = size - idx - 1
            if (remaining <= paginationPrefetchThreshold) {
                Log.d("MusicService", "Near end of queue page (remaining=$remaining). Prefetching page $nextPlaylistPage...")
                isFetchingNextPlaylistPage = true
                serviceScope.launch {
                    val appended = fetchAndAppendNextPlaylistPage()
                    isFetchingNextPlaylistPage = false
                    if (appended) {
                        Log.d("MusicService", "Next page appended in advance; queue size now ${queueManager().currentQueue.value.size}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MusicService", "maybePrefetchNextPageIfNeeded error: ${e.message}")
        }
    }

    private fun skipToPrevious() {
        Log.d("MusicService", "Skipping to previous song")
        
        // Use queue manager's skipToPrevious which handles index updates properly
        val previousSong = queueManager().skipToPrevious()
        if (previousSong != null) {
            Log.d("MusicService", "Playing previous song: ${previousSong.title}")
            // Update playlist manager to keep them synchronized
            playlistManager().playSong(previousSong)
            playSong(previousSong)
            Log.d("MusicService", "Successfully started playing previous song: ${previousSong.title}")
        } else {
            Log.d("MusicService", "No previous song available")
        }
    }

    // Public methods for UI binding
    fun pause() {
        Log.d("MusicService", "Pause called")
        player?.pause()
        updateMediaSessionPlaybackState()
    }

    fun resume() {
        Log.d("MusicService", "Resume called")
        player?.play()
        updateMediaSessionPlaybackState()
    }

    fun stop() {
        Log.d("MusicService", "Stop called")
        player?.stop()
        updateMediaSessionPlaybackState()
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0
    }

    fun getDuration(): Long {
        return player?.duration ?: 0
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
        updateMediaSessionPlaybackState()
        
        // Update scrobble manager with new position
        currentSong?.let { song ->
            val duration = player?.duration ?: 0
            if (duration > 0) {
                scrobbleManager?.updatePlaybackPosition(song.id.toString(), position, duration)
            }
        }
    }

    // Removed duplicate getters - using adapters at bottom of file

    fun getCurrentSong(): Song? = currentSong
    
    fun getCurrentPlaybackContext(): PlaybackContext = when (playbackManager.playbackContext.value) {
        MusicPlaybackManager.PlaybackContext.PLAYLIST -> PlaybackContext.PLAYLIST
        MusicPlaybackManager.PlaybackContext.SEARCH -> PlaybackContext.SEARCH
        MusicPlaybackManager.PlaybackContext.SINGLE_SONG -> PlaybackContext.SINGLE_SONG
    }

    private fun clearQueue() {
        Log.d("MusicService", "Clearing queue and stopping playback")
        
        // Stop the player and clear current song
        player?.stop()
        currentSong = null
        
        // Clear both queue and playlist managers
        queueManager().clearQueue()
        playlistManager().clear()
        
        // Clear browsing context
        currentBrowsingPlaylistId = null
        currentBrowsingPlaylistSongs = emptyList()
        // Clear remote playlist pagination context
        remotePlaylistId = null
        nextPlaylistPage = 1
        hasMorePlaylistPages = false
        
        // Clear search cache
        searchResultsCache = emptyList()
        searchResultsCacheTime = 0
        
        // Reset playback context
        // Context set by playbackManager.setQueue call
        
        // Update media session
        updateMediaSessionPlaybackState()
        
        // Clear metadata
        mediaSession?.setMetadata(null)
        notifyQueueChanged()
        
        // Stop foreground service and remove notification
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        Log.d("MusicService", "Queue cleared successfully")
    }

    fun reinitializeScrobbleManager() {
        Log.d("MusicService", "Reinitializing ScrobbleManager")
        scrobbleManager?.destroy()
        scrobbleManager = null
        initializeScrobbleManager()
    }

    private fun setupMediaSession() {
        Log.d("MusicService", "Setting up MediaSession")
        mediaSession = MediaSessionCompat(this, "Melodee")
        Log.d("MusicService", "MediaSession created: ${mediaSession?.sessionToken}")
        
        // Log MediaSession details for debugging
        Log.i("MusicService", "=== MEDIASESSION DEBUG INFO ===")
        Log.i("MusicService", "Session tag: Melodee")
        Log.i("MusicService", "Package name: $packageName")
        Log.i("MusicService", "Service label: Melodee")
        Log.i("MusicService", "App name: Melodee Player")
        
        // Set flags for Android Auto compatibility
        @Suppress("DEPRECATION")
        mediaSession?.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        
        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.d("MusicService", "MediaSession onPlay called")
                Log.d("MusicService", "Current song: ${currentSong?.title}")
                Log.d("MusicService", "Player has media item: ${player?.currentMediaItem != null}")
                Log.d("MusicService", "Current browsing playlist: $currentBrowsingPlaylistId")
                Log.d("MusicService", "Browsing songs count: ${currentBrowsingPlaylistSongs.size}")
                
                // If there's already a song loaded, just resume
                if (currentSong != null && player?.currentMediaItem != null) {
                    Log.d("MusicService", "Resuming existing playback")
                    resumePlayback()
                } else {
                    // No song loaded, try to start playback from current context
                    Log.d("MusicService", "No current song, attempting to start from context")
                    startPlaybackFromCurrentContext()
                }
            }

            override fun onPause() {
                Log.d("MusicService", "MediaSession onPause")
                pausePlayback()
            }

            override fun onSkipToNext() {
                Log.d("MusicService", "MediaSession onSkipToNext")
                skipToNext()
            }

            override fun onSkipToPrevious() {
                Log.d("MusicService", "MediaSession onSkipToPrevious")
                skipToPrevious()
            }

            override fun onSeekTo(pos: Long) {
                Log.d("MusicService", "MediaSession onSeekTo: $pos")
                seekTo(pos)
            }

            override fun onSetShuffleMode(shuffleMode: Int) {
                Log.d("MusicService", "MediaSession onSetShuffleMode: $shuffleMode")
                when (shuffleMode) {
                    PlaybackStateCompat.SHUFFLE_MODE_ALL -> queueManager().setShuffle(true)
                    PlaybackStateCompat.SHUFFLE_MODE_NONE -> queueManager().setShuffle(false)
                }
                updateMediaSessionPlaybackState()
            }

            override fun onSetRepeatMode(repeatMode: Int) {
                Log.d("MusicService", "MediaSession onSetRepeatMode: $repeatMode")
                val mode = when (repeatMode) {
                    PlaybackStateCompat.REPEAT_MODE_ONE -> QueueManager.RepeatMode.ONE
                    PlaybackStateCompat.REPEAT_MODE_ALL -> QueueManager.RepeatMode.ALL
                    else -> QueueManager.RepeatMode.NONE
                }
                queueManager().setRepeatMode(mode)
                updateMediaSessionPlaybackState()
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                Log.i("MusicService", "=== ANDROID AUTO VOICE COMMAND RECEIVED ===")
                Log.i("MusicService", "Voice search query: '$query'")
                Log.i("MusicService", "Extras: $extras")
                Log.i("MusicService", "MediaSession active: ${mediaSession?.isActive}")
                Log.i("MusicService", "App recognized by Android Auto for voice command!")
                
                // Clear browsing playlist context when playing from search
                currentBrowsingPlaylistId = null
                currentBrowsingPlaylistSongs = emptyList()
                // Clear any old search cache
                searchResultsCache = emptyList()
                
                if (query.isNullOrBlank()) {
                    Log.d("MusicService", "Empty search query, not resuming playback")
                    return
                }
                
                // Handle voice commands like "play next song"
                when {
                    query.contains("next", ignoreCase = true) -> {
                        skipToNext()
                    }
                    query.contains("previous", ignoreCase = true) -> {
                        skipToPrevious()
                    }
                    query.contains("shuffle", ignoreCase = true) -> {
                        queueManager().toggleShuffle()
                        updateMediaSessionPlaybackState()
                    }
                    else -> {
                        // Perform actual search and play first result for Android Auto
                        Log.i("MusicService", "Android Auto voice search request: '$query'")
                        serviceScope.launch {
                            try {
                                val searchResults = performApiSearch(query)
                                if (searchResults.isNotEmpty()) {
                                    // Find the first playable song in search results
                                    val firstSong = searchResults.find { item ->
                                        val mediaId = item.mediaId
                                        mediaId != null && !mediaId.startsWith("no_") && !mediaId.startsWith("auth_") && !mediaId.startsWith("search_")
                                    }
                                    firstSong?.let { mediaItem ->
                                        Log.i("MusicService", "Playing first search result: ${mediaItem.description.title}")
                                        // Set context to single song so it stops after playing
                                        // Context will be set when playing single song
                                        // Fetch and play the song
                                        mediaItem.mediaId?.let { mediaId ->
                                            fetchAndPlaySong(mediaId)
                                        }
                                    } ?: run {
                                        Log.w("MusicService", "No playable songs found in search results")
                                    }
                                } else {
                                    Log.w("MusicService", "Search returned no results for: '$query'")
                                }
                            } catch (e: Exception) {
                                Log.e("MusicService", "Failed to search and play for query: '$query'", e)
                            }
                        }
                    }
                }
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                Log.i("MusicService", "=== ANDROID AUTO PLAY REQUEST ===")
                Log.i("MusicService", "MediaID: $mediaId")
                Log.i("MusicService", "Current browsing playlist ID: $currentBrowsingPlaylistId")
                Log.i("MusicService", "Browsing playlist songs count: ${currentBrowsingPlaylistSongs.size}")
                Log.i("MusicService", "Queue songs count: ${queueManager().currentQueue.value.size}")
                Log.i("MusicService", "Playlist songs count: ${playlistManager().currentPlaylist.value.size}")
                Log.i("MusicService", "Extras: $extras")
                
                // Handle playing specific songs by media ID
                mediaId?.let { id ->
                    if (id.startsWith("no_") || id.startsWith("help_") || id.startsWith("empty_") || 
                        id.startsWith("auth_") || id.startsWith("playlists_") || id.startsWith("playlist_")) {
                        // Handle informational content or non-playable items
                        Log.d("MusicService", "User selected non-playable item: $id")
                        return@let
                    }
                    
                    // Handle queue item selection (e.g., "queue_0", "queue_1")
                    if (id.startsWith("queue_")) {
                        val queueIndexStr = id.removePrefix("queue_")
                        val queueIndex = queueIndexStr.toIntOrNull()
                        if (queueIndex != null) {
                            val currentQueueSongs = queueManager().currentQueue.value
                            if (queueIndex >= 0 && queueIndex < currentQueueSongs.size) {
                                val songToPlay = currentQueueSongs[queueIndex]
                                Log.i("MusicService", "Playing song from queue at index $queueIndex: ${songToPlay.title}")
                                
                                // Update queue manager to the selected index
                                queueManager().playAtIndex(queueIndex)
                                playSong(songToPlay)
                                return@let
                            } else {
                                Log.w("MusicService", "Queue index $queueIndex out of bounds (queue size: ${currentQueueSongs.size})")
                                return@let
                            }
                        } else {
                            Log.w("MusicService", "Invalid queue index: $queueIndexStr")
                            return@let
                        }
                    }
                    
                    Log.d("MusicService", "Searching for song with ID: $id")
                    
                    // First try to find the song in current browsing content
                    val queueSongs = queueManager().currentQueue.value
                    val playlistSongs = playlistManager().currentPlaylist.value
                    val browsingPlaylistSongs = currentBrowsingPlaylistSongs
                    val searchSongs = searchResultsCache
                    val allSongs = (queueSongs + playlistSongs + browsingPlaylistSongs + searchSongs).distinctBy { it.id }
                    
                    Log.d("MusicService", "Total searchable songs: ${allSongs.size}")
                    Log.d("MusicService", "Song IDs available: ${allSongs.map { it.id.toString() }}")
                    Log.d("MusicService", "Search cache size: ${searchResultsCache.size}")
                    
                    val song = allSongs.find { it.id.toString() == id }
                    if (song != null) {
                        Log.i("MusicService", "Found song: ${song.title} by ${song.artist.name}")
                        Log.i("MusicService", "Song stream URL: ${song.streamUrl}")
                        
                        // Check if this song is from search results
                        val isFromSearchCache = searchResultsCache.any { it.id.toString() == id }
                        
                        // Check if we're browsing a playlist context
                        if (currentBrowsingPlaylistId != null && !isFromSearchCache) {
                            Log.i("MusicService", "Playing song from playlist context: $currentBrowsingPlaylistId")
                            // Context set by playbackManager.setQueue call
                            // Load the entire playlist if needed
                            loadPlaylistAndPlay(currentBrowsingPlaylistId!!, song)
                        } else {
                            // Check if this song came from a search result
                            val isFromSearch = extras?.getBoolean("from_search", false) == true || isFromSearchCache
                            if (isFromSearch) {
                                Log.i("MusicService", "Playing song from search results as single song")
                                // Context set by playbackManager.setQueue call
                                // Play just this song, don't add to queue
                                playSong(song)
                            } else {
                                Log.i("MusicService", "Playing song as individual track")
                                // Context set by playbackManager.setQueue call
                                queueManager().addToQueue(song)
                                playlistManager().playSong(song)
                                playSong(song)
                            }
                        }
                    } else {
                        Log.w("MusicService", "Song with mediaId $id not found in local content")
                        Log.w("MusicService", "Available song IDs: ${allSongs.map { "${it.id} (${it.title})" }}")
                        // Try to fetch and play the song from API (for search results or new playlists)
                        // When playing from search, set context to single song
                        // Context set by playbackManager.setQueue call
                        fetchAndPlaySong(id)
                    }
                }
            }

            override fun onCustomAction(action: String?, extras: Bundle?) {
                Log.d("MusicService", "MediaSession onCustomAction: $action")
                when (action) {
                    ACTION_TOGGLE_FAVORITE -> {
                        Log.d("MusicService", "Custom action: toggle favorite")
                        toggleCurrentSongFavorite()
                    }
                    else -> {
                        Log.w("MusicService", "Unknown custom action: $action")
                    }
                }
            }

            override fun onStop() {
                Log.d("MusicService", "MediaSession onStop")
                stopPlayback()
            }
        })
        
        val initialPlaybackState = PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            )
            .build()
            
        mediaSession?.setPlaybackState(initialPlaybackState)
        
        // Set initial metadata with app information for better voice recognition
        val appMetadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Melodee")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Music Player")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "Melodee")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Melodee")
            .build()
        mediaSession?.setMetadata(appMetadata)
        
        // Set queue title for Android Auto recognition
        mediaSession?.setQueueTitle("Melodee")
        
        mediaSession?.isActive = true
        
        // Set the session token for MediaBrowserServiceCompat
        sessionToken = mediaSession?.sessionToken
        Log.d("MusicService", "MediaSession setup complete. Token: $sessionToken")
        Log.d("MusicService", "MediaSession active: ${mediaSession?.isActive}")
        Log.d("MusicService", "MediaSession flags: ${mediaSession?.controller?.flags}")
    }

    // Helper methods for playlist handling
    private fun loadPlaylistAndPlay(playlistId: String, songToPlay: Song) {
        Log.i("MusicService", "=== LOADING PLAYLIST AND PLAYING SONG ===")
        Log.i("MusicService", "Playlist ID: $playlistId")
        Log.i("MusicService", "Song to play: ${songToPlay.title}")
        
        serviceScope.launch {
            try {
                Log.d("MusicService", "Fetching playlist songs...")
                val playlistSongs = fetchPlaylistSongs(playlistId)
                
                if (playlistSongs.isNotEmpty()) {
                    Log.i("MusicService", "Fetched ${playlistSongs.size} songs from playlist")
                    val startIndex = playlistSongs.indexOfFirst { it.id == songToPlay.id }
                    Log.i("MusicService", "Start index for song: $startIndex")
                    
                    if (startIndex >= 0) {
                        // Context set by playbackManager.setQueue call
                        queueManager().setQueue(playlistSongs, startIndex)
                        playlistManager().setPlaylist(playlistSongs, startIndex)
                        
                        // Notify Android Auto that the queue has changed
                        notifyQueueChanged()
                        
                        playSong(songToPlay)
                        
                        Log.i("MusicService", "Successfully loaded playlist and started playback")
                    } else {
                        Log.w("MusicService", "Song not found in playlist, playing as standalone")
                        queueManager().addToQueue(songToPlay)
                        playlistManager().playSong(songToPlay)
                        
                        // Notify Android Auto that the queue has changed
                        notifyQueueChanged()
                        
                        playSong(songToPlay)
                    }
                } else {
                    Log.w("MusicService", "Playlist $playlistId is empty, playing song standalone")
                    // Just play the single song
                    queueManager().addToQueue(songToPlay)
                    playlistManager().playSong(songToPlay)
                    
                    // Notify Android Auto that the queue has changed (even for single songs)
                    notifyQueueChanged()
                    
                    playSong(songToPlay)
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to load playlist $playlistId", e)
                Log.e("MusicService", "Error details: ${e.message}")
                e.printStackTrace()
                
                // Fallback to playing just the single song
                Log.i("MusicService", "Falling back to standalone song playback")
                queueManager().addToQueue(songToPlay)
                playlistManager().playSong(songToPlay)
                
                // Notify Android Auto that the queue has changed
                notifyQueueChanged()
                
                playSong(songToPlay)
            }
        }
    }

    private fun fetchAndPlaySong(songId: String) {
        serviceScope.launch {
            try {
                Log.d("MusicService", "Attempting to fetch and play song: $songId")
                
                // First, try to find the song in recent search results
                // This is a common case for Android Auto where search results are cached
                Log.d("MusicService", "Searching for song in all available collections...")
                
                // Check all possible sources again
                val allAvailableSongs = mutableListOf<Song>()
                allAvailableSongs.addAll(queueManager().currentQueue.value)
                allAvailableSongs.addAll(playlistManager().currentPlaylist.value)
                allAvailableSongs.addAll(currentBrowsingPlaylistSongs)
                allAvailableSongs.addAll(searchResultsCache)  // Include cached search results
                
                val song = allAvailableSongs.find { it.id.toString() == songId }
                if (song != null) {
                    Log.i("MusicService", "Found song in available collections: ${song.title}")
                    // Play as single song based on current context
                    if (getCurrentPlaybackContext() == PlaybackContext.SINGLE_SONG) {
                        Log.i("MusicService", "Playing as single song - will stop after completion")
                        playSong(song)
                    } else {
                        queueManager().addToQueue(song)
                        playlistManager().playSong(song)
                        
                        // Notify Android Auto that the queue has changed
                        notifyQueueChanged()
                        
                        playSong(song)
                    }
                } else {
                    Log.w("MusicService", "Cannot play song $songId - not found in any local content")
                    Log.w("MusicService", "Available songs: ${allAvailableSongs.map { "${it.id} (${it.title})" }}")
                    Log.w("MusicService", "Cached search results: ${searchResultsCache.map { "${it.id} (${it.title})" }}")
                    // In a production app, you might fetch the song details from an API here
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to fetch and play song $songId", e)
            }
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive && player?.isPlaying == true) {
                currentSong?.let { song ->
                    val currentPos = player?.currentPosition ?: 0
                    val duration = player?.duration ?: 0
                    if (duration > 0) {
                        scrobbleManager?.updatePlaybackPosition(song.id.toString(), currentPos, duration)
                    }
                }
                delay(5000) // Update every 5 seconds
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun populateTestContentForAndroidAuto() {
        Log.d("MusicService", "Populating test content for Android Auto debugging")
        
        // Check if we have any content
        val hasQueueContent = queueManager().currentQueue.value.isNotEmpty()
        val hasPlaylistContent = playlistManager().currentPlaylist.value.isNotEmpty()
        
        Log.d("MusicService", "Current content status - Queue: $hasQueueContent, Playlist: $hasPlaylistContent")
        
        if (!hasQueueContent && !hasPlaylistContent) {
            Log.d("MusicService", "No content available - Android Auto will show 'No Music Available' message")
        } else {
            Log.d("MusicService", "Content available - Queue: ${queueManager().currentQueue.value.size}, Playlist: ${playlistManager().currentPlaylist.value.size}")
        }
    }

    // Debug method - can be called during development
    fun testSearchFunctionality(query: String = "test") {
        Log.i("MusicService", "=== Testing Search Functionality ===")
        serviceScope.launch {
            try {
                val results = performApiSearch(query)
                Log.i("MusicService", "Test search returned ${results.size} results")
                results.forEach { item ->
                    val desc = item.description
                    Log.i("MusicService", "Result: ${desc.title} - ${desc.subtitle}")
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Test search failed", e)
            }
        }
    }

    private fun setupAudioManager() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d("MusicService", "AudioManager initialized")
    }

    private fun requestAudioFocus(): Boolean {
        Log.i("MusicService", "=== REQUESTING AUDIO FOCUS ===")
        Log.d("MusicService", "Current hasAudioFocus: $hasAudioFocus")
        Log.d("MusicService", "Android version: ${Build.VERSION.SDK_INT}")
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true) // Important for Android Auto
                .setWillPauseWhenDucked(false)   // Don't pause when other apps duck
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED || 
                           result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
            
            Log.i("MusicService", "Audio focus request result: $result")
            Log.i("MusicService", "AUDIOFOCUS_REQUEST_GRANTED = ${AudioManager.AUDIOFOCUS_REQUEST_GRANTED}")
            Log.i("MusicService", "AUDIOFOCUS_REQUEST_FAILED = ${AudioManager.AUDIOFOCUS_REQUEST_FAILED}")
            Log.i("MusicService", "AUDIOFOCUS_REQUEST_DELAYED = ${AudioManager.AUDIOFOCUS_REQUEST_DELAYED}")
            Log.i("MusicService", "Audio focus granted or delayed: $hasAudioFocus")
            
            if (result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                Log.i("MusicService", "Audio focus delayed - will be granted when available")
            }
            
            hasAudioFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            
            Log.i("MusicService", "Audio focus request result (legacy): $result")
            Log.i("MusicService", "Audio focus granted (legacy): $hasAudioFocus")
            
            hasAudioFocus
        }
    }

    private fun abandonAudioFocus() {
        Log.d("MusicService", "Abandoning audio focus")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.i("MusicService", "=== AUDIO FOCUS CHANGED: $focusChange ===")
        Log.i("MusicService", "AUDIOFOCUS_GAIN = ${AudioManager.AUDIOFOCUS_GAIN}")
        Log.i("MusicService", "AUDIOFOCUS_LOSS = ${AudioManager.AUDIOFOCUS_LOSS}")
        Log.i("MusicService", "AUDIOFOCUS_LOSS_TRANSIENT = ${AudioManager.AUDIOFOCUS_LOSS_TRANSIENT}")
        Log.i("MusicService", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = ${AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}")
        
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i("MusicService", "Audio focus gained - checking if we should resume")
                hasAudioFocus = true
                
                // Resume playback if we have a current song and player is not playing
                if (currentSong != null && player?.isPlaying == false) {
                    Log.i("MusicService", "Resuming playback after gaining audio focus")
                    player?.play()
                    updateMediaSessionPlaybackState()
                }
                               // Restore full volume if it was ducked
                player?.volume = 1.0f
                Log.i("MusicService", "Audio focus handling complete - volume restored")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w("MusicService", "Audio focus lost permanently - this may be an Android Auto conflict")
                hasAudioFocus = false
                
                // Check if this is happening immediately after we gained focus (Android Auto bug)
                val wasPlaying = player?.isPlaying == true
                if (wasPlaying && currentSong != null) {
                    Log.w("MusicService", "Detected immediate focus loss while playing - attempting aggressive reclaim")
                    
                    // Try to reclaim focus multiple times with different strategies
                    serviceScope.launch {
                        var attempts = 0
                        val maxAttempts = 5
                        
                        while (attempts < maxAttempts && !hasAudioFocus && currentSong != null) {
                            attempts++
                            val delayMs = when (attempts) {
                                1 -> 100L   // First attempt very quickly
                                2 -> 250L // Second attempt after quarter second
                                3 -> 500L   // Third attempt after half second
                                else -> 1000L // Later attempts after full second
                            }
                            
                            Log.i("MusicService", "Attempt $attempts/$maxAttempts: Waiting ${delayMs}ms before reclaiming focus")
                            delay(delayMs)
                            
                            if (!hasAudioFocus && currentSong != null) {
                                Log.i("MusicService", "Attempt $attempts: Trying to reclaim audio focus...")
                                
                                val focusGained = if (attempts <= 2) {
                                    // Use normal request for first two attempts
                                    requestAudioFocus()
                                } else {
                                    // Use super aggressive method for later attempts
                                    Log.w("MusicService", "Using superAggressiveAudioFocus for attempt $attempts")
                                    superAggressiveAudioFocus()
                                }
                                
                                if (focusGained) {
                                    Log.i("MusicService", "Successfully reclaimed audio focus on attempt $attempts - resuming playback")
                                    player?.play()
                                    updateMediaSessionPlaybackState()
                                    break
                                } else {
                                    Log.w("MusicService", "Failed to reclaim audio focus on attempt $attempts")
                                }
                            } else {
                                Log.i("MusicService", "Focus regained or song cleared during retry loop - stopping attempts")
                                break
                            }
                        }
                        
                        // If all attempts failed, pause playback
                        if (!hasAudioFocus && currentSong != null) {
                            Log.e("MusicService", "Failed to reclaim audio focus after $maxAttempts attempts - pausing playback")
                            pausePlayback()
                        }
                    }
                } else {
                    // Normal focus loss - pause playback
                    pausePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i("MusicService", "Audio focus lost temporarily - pausing")
                hasAudioFocus = false
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i("MusicService", "Audio focus lost temporarily - ducking volume")
                // Lower the volume instead of pausing
                player?.volume = 0.2f
            }
        }
        
        Log.i("MusicService", "Audio focus change handling complete. Current state: hasAudioFocus=$hasAudioFocus, isPlaying=${player?.isPlaying}")
    }

    private fun verifyAuthentication(): Boolean {
        val isAuthenticatedState = authenticationManager.isAuthenticated.value
        val hasNetworkToken = NetworkModule.isAuthenticated()
        val currentUser = authenticationManager.getCurrentUser()
        
        Log.i("MusicService", "=== AUTHENTICATION VERIFICATION ===")
        Log.i("MusicService", "AuthenticationManager state: $isAuthenticatedState")
        Log.i("MusicService", "NetworkModule has token: $hasNetworkToken")
        Log.i("MusicService", "Current user: ${currentUser?.username ?: "null"}")
        
        // If we have network token and user data but AuthenticationManager state is false,
        // try to restore the authentication state
        if (!isAuthenticatedState && hasNetworkToken && currentUser != null) {
            Log.w("MusicService", "Authentication state mismatch detected - attempting to restore")
            
            // Force re-check authentication in AuthenticationManager
            try {
                // Re-initialize the AuthenticationManager state based on stored data
                val settingsManager = SettingsManager(this)
                if (settingsManager.isAuthenticated()) {
                    Log.i("MusicService", "Stored authentication data is valid - restoring state")
                    
                    // Manually trigger authentication restoration
                    NetworkModule.setBaseUrl(settingsManager.serverUrl)
                    NetworkModule.setAuthToken(settingsManager.authToken)
                    
                    // Update AuthenticationManager state through saveAuthentication
                    authenticationManager.saveAuthentication(
                        settingsManager.authToken,
                        settingsManager.userId,
                        settingsManager.userEmail,
                        settingsManager.username,
                        settingsManager.serverUrl
                    )
                    
                    Log.i("MusicService", "Authentication state restored successfully")
                    return true
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to restore authentication state", e)
            }
        }
        
        val isFullyAuthenticated = isAuthenticatedState && hasNetworkToken && currentUser != null
        Log.i("MusicService", "Fully authenticated: $isFullyAuthenticated")
        
        return isFullyAuthenticated
    }

    private fun handlePlaybackFailure(song: Song, error: Exception) {
        Log.e("MusicService", "=== PLAYBACK FAILURE HANDLER ===")
        Log.e("MusicService", "Failed song: ${song.title}")
        Log.e("MusicService", "Stream URL: ${song.streamUrl}")
        Log.e("MusicService", "Error: ${error.message}")
        
        // Check authentication status
        if (!verifyAuthentication()) {
            Log.e("MusicService", "Playback failed due to authentication issues")
        }
        
        // Update MediaSession to show error state
        val errorState = PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_ERROR, 0, 1.0f)
            .setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, "Playback failed: ${error.message}")
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            )
            .build()
        
        mediaSession?.setPlaybackState(errorState)
        
        // Abandon audio focus
        abandonAudioFocus()
    }

    // Special method for Android Auto to aggressively claim audio focus
    private fun forceAudioFocusForAndroidAuto(): Boolean {
        Log.i("MusicService", "=== FORCING AUDIO FOCUS FOR ANDROID AUTO ===")
        
        // First, abandon any existing focus
        abandonAudioFocus()
        
        // Wait a moment
        Thread.sleep(100)
        
        // Request focus with more aggressive settings
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // Force audibility
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .apply {
                    // setForceDucking requires API 28, so add version check
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setForceDucking(false) // Don't allow other apps to duck us
                    }
                }
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED || 
                           result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
            
            Log.i("MusicService", "Forced audio focus result: $result, hasAudioFocus: $hasAudioFocus")
            hasAudioFocus
        } else {
            // For older versions, use legacy method
            requestAudioFocus()
        }
    }

    // Ultra-aggressive method that tries multiple strategies
    private fun superAggressiveAudioFocus(): Boolean {
        Log.i("MusicService", "=== SUPER AGGRESSIVE AUDIO FOCUS ===")
        
        // Strategy 1: Normal request
        if (requestAudioFocus()) {
            Log.i("MusicService", "Audio focus gained with normal request")
            return true
        }
        
        // Strategy 2: Force method
        if (forceAudioFocusForAndroidAuto()) {
            Log.i("MusicService", "Audio focus gained with force method")
            return true
        }
        
        // Strategy 3: Multiple attempts with delays
        repeat(3) { attempt ->
            Log.i("MusicService", "Attempting audio focus with delay strategy: attempt ${attempt + 1}")
            Thread.sleep(200L * (attempt + 1))
            
            if (requestAudioFocus()) {
                Log.i("MusicService", "Audio focus gained with delay strategy on attempt ${attempt + 1}")
                return true
            }
        }
        
        // Strategy 4: Try to become audio focus owner by requesting and abandoning quickly
        Log.i("MusicService", "Trying rapid request/abandon cycle to claim focus")
        repeat(3) {
            requestAudioFocus()
            Thread.sleep(50)
            abandonAudioFocus()
            Thread.sleep(50)
        }
        
        // Final attempt
        val finalResult = requestAudioFocus()
        Log.i("MusicService", "Final aggressive audio focus result: $finalResult")
        return finalResult
    }

    private fun clearExpiredSearchCache() {
        val cacheExpirationMs = 10 * 60 * 1000L // 10 minutes
        val currentTime = System.currentTimeMillis()
        
        if (searchResultsCache.isNotEmpty() && (currentTime - searchResultsCacheTime) > cacheExpirationMs) {
            Log.d("MusicService", "Search cache expired, clearing it")
            searchResultsCache = emptyList()
            searchResultsCacheTime = 0
        }
    }
    
    private fun clearSearchCache() {
        Log.d("MusicService", "Manually clearing search cache")
        searchResultsCache = emptyList()
        searchResultsCacheTime = 0
    }

    private fun notifyQueueChanged() {
        // Notify Android Auto that the Current Queue content has changed
        Log.d("MusicService", "Notifying Android Auto that queue has changed")
        
        // Update MediaSessionCompat queue for Android Auto
        val currentQueueSongs = queueManager().currentQueue.value
        if (currentQueueSongs.isNotEmpty()) {
            val sessionQueue = currentQueueSongs.mapIndexed { index, song ->
                MediaSessionCompat.QueueItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("queue_$index")
                        .setTitle(song.title)
                        .setSubtitle(song.artist.name)
                        .setDescription(song.album.name)
                        .build(),
                    index.toLong()
                )
            }
            
            Log.d("MusicService", "Setting MediaSession queue with ${sessionQueue.size} items")
            mediaSession?.setQueue(sessionQueue)
            
            // Set queue title based on playback context
            val queueTitle = when (getCurrentPlaybackContext()) {
                PlaybackContext.PLAYLIST -> "Playlist Queue"
                PlaybackContext.SEARCH -> "Search Results"
                PlaybackContext.SINGLE_SONG -> "Now Playing"
            }
            mediaSession?.setQueueTitle(queueTitle)
        } else {
            Log.d("MusicService", "Clearing MediaSession queue (empty)")
            mediaSession?.setQueue(emptyList())
            mediaSession?.setQueueTitle(null)
        }
        
        // Also notify media browser that queue children changed
        notifyChildrenChanged(MEDIA_QUEUE_ID)
    }

    private fun toggleCurrentSongFavorite() {
        val song = currentSong
        if (song == null) {
            Log.w("MusicService", "No current song to favorite")
            return
        }
        
        serviceScope.launch {
            try {
                Log.d("MusicService", "Toggling favorite for song: ${song.title}")
                val newFavoriteStatus = !song.userStarred
                
                // Call the API to update favorite status
                val musicApi = NetworkModule.getMusicApi()
                val response = musicApi.favoriteSong(song.id.toString(), newFavoriteStatus)
                
                if (response.isSuccessful) {
                    // Update the current song object with new favorite status
                    currentSong = song.copy(userStarred = newFavoriteStatus)
                    
                    // Update MediaSession metadata and playback state to reflect new favorite status
                    updateMediaSessionMetadata(currentSong!!)
                    updateMediaSessionPlaybackState()
                    
                    val statusText = if (newFavoriteStatus) "favorited" else "unfavorited"
                    Log.i("MusicService", "Successfully ${statusText} song: ${song.title}")
                    
                } else {
                    Log.e("MusicService", "Failed to update favorite status for song: ${song.title}. Response code: ${response.code()}")
                }
                
            } catch (e: Exception) {
                Log.e("MusicService", "Error toggling favorite for song: ${song.title}", e)
            }
        }
    }

    // === ADAPTER METHODS FOR LEGACY COMPATIBILITY ===
    // These methods maintain compatibility with existing code while using the consolidated manager
    
    inner class QueueManagerAdapter {
        val currentQueue get() = playbackManager.currentQueue
        val currentIndex get() = playbackManager.currentIndex
        val currentSong get() = playbackManager.currentSong
        
        fun setQueue(songs: List<Song>, startIndex: Int = 0) {
            playbackManager.setQueue(songs, startIndex, MusicPlaybackManager.PlaybackContext.PLAYLIST)
        }
        
        fun addToQueue(song: Song) {
            val currentList = playbackManager.currentQueue.value.toMutableList()
            currentList.add(song)
            playbackManager.setQueue(currentList, playbackManager.currentIndex.value)
        }
        
        fun appendToQueue(songs: List<Song>) {
            if (songs.isEmpty()) return
            val currentList = playbackManager.currentQueue.value.toMutableList()
            currentList.addAll(songs)
            // Preserve current index
            playbackManager.setQueue(currentList, playbackManager.currentIndex.value)
        }
        
        fun clearQueue() = playbackManager.clearQueue()
        
        fun getQueueSize() = playbackManager.currentQueue.value.size
        
        fun skipToNext() = if (playbackManager.playNext()) playbackManager.currentSong.value else null
        
        fun skipToPrevious() = if (playbackManager.playPrevious()) playbackManager.currentSong.value else null
        
        fun toggleShuffle() = playbackManager.toggleShuffle()
        
        fun toggleRepeat() = playbackManager.toggleRepeat()
        
        val isShuffleEnabled get() = playbackManager.isShuffleEnabled
        
        val repeatMode get() = playbackManager.repeatMode.map { mode ->
            when (mode) {
                MusicPlaybackManager.RepeatMode.NONE -> QueueManager.RepeatMode.NONE
                MusicPlaybackManager.RepeatMode.ONE -> QueueManager.RepeatMode.ONE
                MusicPlaybackManager.RepeatMode.ALL -> QueueManager.RepeatMode.ALL
            }
        }
        
        fun getRepeatMode() = when (playbackManager.repeatMode.value) {
            MusicPlaybackManager.RepeatMode.NONE -> QueueManager.RepeatMode.NONE
            MusicPlaybackManager.RepeatMode.ONE -> QueueManager.RepeatMode.ONE
            MusicPlaybackManager.RepeatMode.ALL -> QueueManager.RepeatMode.ALL
        }
        
        fun isShuffleEnabled() = playbackManager.isShuffleEnabled.value
        
        fun setShuffle(enabled: Boolean) {
            if (enabled != playbackManager.isShuffleEnabled.value) {
                playbackManager.toggleShuffle()
            }
        }
        
        fun setRepeatMode(mode: QueueManager.RepeatMode) {
            val targetMode = when (mode) {
                QueueManager.RepeatMode.NONE -> MusicPlaybackManager.RepeatMode.NONE
                QueueManager.RepeatMode.ONE -> MusicPlaybackManager.RepeatMode.ONE
                QueueManager.RepeatMode.ALL -> MusicPlaybackManager.RepeatMode.ALL
            }
            
            while (playbackManager.repeatMode.value != targetMode) {
                playbackManager.toggleRepeat()
            }
        }
        
        fun playAtIndex(index: Int): Boolean {
            val songs = playbackManager.currentQueue.value
            return if (index in songs.indices) {
                playbackManager.playSong(songs[index])
            } else false
        }
        
        fun removeFromQueue(song: Song) {
            val currentList = playbackManager.currentQueue.value.toMutableList()
            currentList.remove(song)
            val currentIndex = playbackManager.currentIndex.value
            playbackManager.setQueue(currentList, if (currentIndex >= currentList.size) 0 else currentIndex)
        }
        
        fun removeFromQueue(index: Int) {
            val currentList = playbackManager.currentQueue.value.toMutableList()
            if (index in currentList.indices) {
                currentList.removeAt(index)
                val currentIndex = playbackManager.currentIndex.value
                playbackManager.setQueue(currentList, if (currentIndex >= currentList.size) 0 else currentIndex)
            }
        }
    }
    
    inner class PlaylistManagerAdapter {
        val currentPlaylist get() = playbackManager.currentQueue
        val currentIndex get() = playbackManager.currentIndex
        val currentSong get() = playbackManager.currentSong
        
        fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
            playbackManager.setQueue(songs, startIndex, MusicPlaybackManager.PlaybackContext.PLAYLIST)
        }
        
        fun playSong(song: Song): Boolean = playbackManager.playSong(song)
        
        fun clear() = playbackManager.clearQueue()
    }
    
    // Legacy manager adapters (replace the old manager declarations)
    private val _queueManager by lazy { QueueManagerAdapter() }
    private val _playlistManager by lazy { PlaylistManagerAdapter() }
    
    // Public getters for external access
    fun getPlaylistManager() = _playlistManager
    fun getQueueManager() = _queueManager
    
    // Direct access for internal code
    private fun queueManager() = _queueManager
    private fun playlistManager() = _playlistManager
}
