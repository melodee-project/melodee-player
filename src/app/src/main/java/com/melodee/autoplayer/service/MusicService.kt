package com.melodee.autoplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.melodee.autoplayer.presentation.ui.MainActivity
import com.melodee.autoplayer.data.SettingsManager
import com.melodee.autoplayer.data.api.NetworkModule
import kotlinx.coroutines.*

class MusicService : MediaBrowserServiceCompat() {
    private var player: ExoPlayer? = null
    private var currentSong: Song? = null
    private val binder = MusicBinder()
    private var mediaSession: MediaSessionCompat? = null
    private val playlistManager = PlaylistManager()
    private val queueManager = QueueManager()
    private var scrobbleManager: ScrobbleManager? = null
    private lateinit var settingsManager: SettingsManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

    // Add playback context tracking
    enum class PlaybackContext {
        PLAYLIST,    // Playing from a playlist
        SEARCH,      // Playing from search results
        SINGLE_SONG  // Playing a single song
    }
    
    private var currentPlaybackContext = PlaybackContext.SINGLE_SONG

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_playback_channel"
        private const val CHANNEL_NAME = "Music Playback"
        
        // Media Browser IDs
        private const val MEDIA_ROOT_ID = "root"
        private const val MEDIA_PLAYLISTS_ID = "playlists"
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
        const val EXTRA_SONG = "com.melodee.autoplayer.EXTRA_SONG"
        const val EXTRA_POSITION = "com.melodee.autoplayer.EXTRA_POSITION"
        const val EXTRA_PLAYLIST = "com.melodee.autoplayer.EXTRA_PLAYLIST"
        const val EXTRA_SEARCH_RESULTS = "com.melodee.autoplayer.EXTRA_SEARCH_RESULTS"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "onCreate called")
        Log.d("MusicService", "Service package: ${packageName}")
        Log.d("MusicService", "Service class: ${this.javaClass.name}")
        
        settingsManager = SettingsManager(this)
        initializeScrobbleManager()
        createNotificationChannel()
        setupMediaSession()
        setupPlayer()
        Log.d("MusicService", "Service initialization complete")
    }

    private fun initializeScrobbleManager() {
        try {
            val userId = settingsManager.userId
            if (userId.isNotEmpty()) {
                val scrobbleApi = NetworkModule.getScrobbleApi()
                scrobbleManager = ScrobbleManager(scrobbleApi, userId)
                Log.d("MusicService", "ScrobbleManager initialized for user: $userId")
            } else {
                Log.w("MusicService", "No user ID found, scrobbling disabled")
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
                          clientPackageName == "com.google.android.gms"
        
        Log.d("MusicService", "Is Android Auto client: $isAndroidAuto")
        
        // Allow all clients to browse the media library
        val rootExtras = Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
            // Add Android Auto specific extras
            if (isAndroidAuto) {
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                putInt("android.media.extras.CONTENT_STYLE_GROUP_TITLE_HINT", 1)
            }
        }
        
        return BrowserRoot(MEDIA_ROOT_ID, rootExtras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d("MusicService", "onLoadChildren called for parentId: $parentId")
        
        when (parentId) {
            MEDIA_ROOT_ID -> {
                val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
                
                // Add browsable categories with Android Auto optimized icons
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
                
                mediaItems.add(
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId(MEDIA_RECENT_ID)
                            .setTitle("Recently Played")
                            .setSubtitle("Your recent music")
                            .setIconUri(android.net.Uri.parse("android.resource://$packageName/${R.drawable.ic_history}"))
                            .build(),
                        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    )
                )
                
                mediaItems.add(
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId(MEDIA_FAVORITES_ID)
                            .setTitle("Favorites")
                            .setSubtitle("Your starred music")
                            .setIconUri(android.net.Uri.parse("android.resource://$packageName/${R.drawable.ic_star}"))
                            .build(),
                        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    )
                )
                
                result.sendResult(mediaItems)
            }
            
            MEDIA_PLAYLISTS_ID -> {
                // Return current playlist songs as playable items
                val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
                playlistManager.currentPlaylist.value.forEach { song ->
                    mediaItems.add(createMediaItem(song))
                }
                result.sendResult(mediaItems)
            }
            
            MEDIA_RECENT_ID, MEDIA_FAVORITES_ID -> {
                // For now, return empty list - can be implemented later
                result.sendResult(mutableListOf())
            }
            
            else -> {
                result.sendResult(mutableListOf())
            }
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d("MusicService", "onSearch called with query: $query")
        
        // For now, return a subset of current playlist that matches the query
        // In a real implementation, you'd search your music library
        val searchResults = mutableListOf<MediaBrowserCompat.MediaItem>()
        
        if (query.isNotEmpty()) {
            playlistManager.currentPlaylist.value
                .filter { song ->
                    song.title.contains(query, ignoreCase = true) ||
                    song.artist.name.contains(query, ignoreCase = true) ||
                    song.album.name.contains(query, ignoreCase = true)
                }
                .take(10) // Limit results for Android Auto
                .forEach { song ->
                    searchResults.add(createMediaItem(song))
                }
        }
        
        Log.d("MusicService", "Search returned ${searchResults.size} results")
        result.sendResult(searchResults)
    }

    private fun createMediaItem(song: Song): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(song.id.toString())
                .setTitle(song.title)
                .setSubtitle(song.artist.name)
                .setDescription(song.album.name)
                .setIconUri(android.net.Uri.parse(song.thumbnailUrl))
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicService", "onStartCommand called with intent: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_PLAY_SONG -> {
                val song = intent.getParcelableExtra<Song>(EXTRA_SONG)
                Log.d("MusicService", "Received play command for song: ${song?.title}")
                if (song != null) {
                    // Set context to single song if no queue is set
                    if (queueManager.getQueueSize() == 0) {
                        currentPlaybackContext = PlaybackContext.SINGLE_SONG
                    }
                    queueManager.addToQueue(song)
                    playlistManager.playSong(song)
                    playSong(song)
                } else {
                    Log.e("MusicService", "Received null song in intent")
                }
            }
            ACTION_SET_PLAYLIST -> {
                val songs = intent.getParcelableArrayListExtra<Song>(EXTRA_PLAYLIST)
                val startIndex = intent.getIntExtra("START_INDEX", 0)
                if (songs != null) {
                    currentPlaybackContext = PlaybackContext.PLAYLIST
                    queueManager.setQueue(songs, startIndex)
                    playlistManager.setPlaylist(songs, startIndex)
                    if (songs.isNotEmpty() && startIndex < songs.size) {
                        playSong(songs[startIndex])
                    }
                }
            }
            ACTION_SET_SEARCH_RESULTS -> {
                val songs = intent.getParcelableArrayListExtra<Song>(EXTRA_SEARCH_RESULTS)
                val startIndex = intent.getIntExtra("START_INDEX", 0)
                if (songs != null) {
                    currentPlaybackContext = PlaybackContext.SEARCH
                    queueManager.setQueue(songs, startIndex)
                    playlistManager.setPlaylist(songs, startIndex)
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
                queueManager.toggleShuffle()
                updateMediaSessionPlaybackState()
            }
            ACTION_TOGGLE_REPEAT -> {
                Log.d("MusicService", "Received toggle repeat command")
                queueManager.toggleRepeat()
                updateMediaSessionPlaybackState()
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

    override fun onDestroy() {
        Log.d("MusicService", "onDestroy called")
        
        // Stop position updates
        stopPositionUpdates()
        
        // Clean up scrobble manager
        scrobbleManager?.destroy()
        scrobbleManager = null
        
        // Cancel coroutine scope
        serviceScope.cancel()
        
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
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
        Log.d("MusicService", "Playing song: ${song.title}")
        Log.d("MusicService", "Stream URL: ${song.streamUrl}")
        
        // Stop tracking previous song
        currentSong?.let { prevSong ->
            scrobbleManager?.stopTracking(prevSong.id.toString())
        }
        
        try {
            val mediaItem = MediaItem.fromUri(song.streamUrl)
            Log.d("MusicService", "Created media item for: ${song.streamUrl}")
            
            player?.setMediaItem(mediaItem)
            Log.d("MusicService", "Set media item in player")
            
            player?.prepare()
            Log.d("MusicService", "Prepared player")
            
            player?.play()
            Log.d("MusicService", "Started playback")
            
            currentSong = song
            Log.d("MusicService", "Updated current song")

            // Update MediaSession metadata
            updateMediaSessionMetadata(song)

            // Start as foreground service with notification
            startForeground(NOTIFICATION_ID, createNotification(song))
        } catch (e: Exception) {
            Log.e("MusicService", "Error playing song: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun setupPlayer() {
        Log.d("MusicService", "Setting up ExoPlayer")
        player = ExoPlayer.Builder(this).build().apply {
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

        val shuffleMode = if (queueManager.isShuffleEnabled()) {
            PlaybackStateCompat.SHUFFLE_MODE_ALL
        } else {
            PlaybackStateCompat.SHUFFLE_MODE_NONE
        }

        val repeatMode = when (queueManager.getRepeatMode()) {
            QueueManager.RepeatMode.NONE -> PlaybackStateCompat.REPEAT_MODE_NONE
            QueueManager.RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
            QueueManager.RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, player.currentPosition, 1.0f)
            .setActions(actions)
            .build()

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
        player?.play()
        updateMediaSessionPlaybackState()
    }

    private fun stopPlayback() {
        Log.d("MusicService", "Stopping playback")
        player?.stop()
        currentSong = null
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

    private fun skipToNext() {
        Log.d("MusicService", "Skipping to next song, context: $currentPlaybackContext")
        
        when (currentPlaybackContext) {
            PlaybackContext.PLAYLIST -> {
                // For playlist context, check if there are more songs in the playlist
                val nextSong = queueManager.getNextSong()
                if (nextSong != null) {
                    Log.d("MusicService", "Playing next song from playlist: ${nextSong.title}")
                    // Update both managers to keep them synchronized
                    queueManager.playSong(nextSong)
                    playlistManager.playSong(nextSong)
                    playSong(nextSong)
                    Log.d("MusicService", "Successfully started playing next song: ${nextSong.title}")
                } else {
                    // If repeat mode is NONE and we're at the end, try to get the next song manually
                    if (queueManager.getRepeatMode() == QueueManager.RepeatMode.NONE) {
                        val queue = queueManager.currentQueue.value
                        val currentIndex = queueManager.currentIndex.value
                        val nextIndex = currentIndex + 1
                        
                        if (nextIndex < queue.size) {
                            val manualNextSong = queue[nextIndex]
                            Log.d("MusicService", "Playing next song from playlist (manual): ${manualNextSong.title}")
                            queueManager.playSong(manualNextSong)
                            playlistManager.playSong(manualNextSong)
                            playSong(manualNextSong)
                            Log.d("MusicService", "Successfully started playing next song: ${manualNextSong.title}")
                        } else {
                            Log.d("MusicService", "Reached end of playlist, stopping playback")
                            stopPlayback()
                        }
                    } else {
                        Log.d("MusicService", "No more songs in playlist, stopping playback")
                        stopPlayback()
                    }
                }
            }
            PlaybackContext.SEARCH -> {
                // For search context, check if there are more songs from search results
                val nextSong = queueManager.getNextSong()
                if (nextSong != null) {
                    Log.d("MusicService", "Playing next song from search results: ${nextSong.title}")
                    // Update both managers to keep them synchronized
                    queueManager.playSong(nextSong)
                    playlistManager.playSong(nextSong)
                    playSong(nextSong)
                    Log.d("MusicService", "Successfully started playing next song: ${nextSong.title}")
                } else {
                    // If repeat mode is NONE and we're at the end, try to get the next song manually
                    if (queueManager.getRepeatMode() == QueueManager.RepeatMode.NONE) {
                        val queue = queueManager.currentQueue.value
                        val currentIndex = queueManager.currentIndex.value
                        val nextIndex = currentIndex + 1
                        
                        if (nextIndex < queue.size) {
                            val manualNextSong = queue[nextIndex]
                            Log.d("MusicService", "Playing next song from search results (manual): ${manualNextSong.title}")
                            queueManager.playSong(manualNextSong)
                            playlistManager.playSong(manualNextSong)
                            playSong(manualNextSong)
                            Log.d("MusicService", "Successfully started playing next song: ${manualNextSong.title}")
                        } else {
                            Log.d("MusicService", "Reached end of search results, stopping playback")
                            stopPlayback()
                        }
                    } else {
                        Log.d("MusicService", "No more songs in search results, stopping playback")
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

    private fun skipToPrevious() {
        Log.d("MusicService", "Skipping to previous song")
        val previousSong = queueManager.getPreviousSong()
        if (previousSong != null) {
            // Update both managers to keep them synchronized
            queueManager.playSong(previousSong)
            playlistManager.playSong(previousSong)
            playSong(previousSong)
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

    fun getPlaylistManager(): PlaylistManager = playlistManager
    
    fun getQueueManager(): QueueManager = queueManager

    fun getCurrentSong(): Song? = currentSong
    
    fun getCurrentPlaybackContext(): PlaybackContext = currentPlaybackContext

    fun reinitializeScrobbleManager() {
        Log.d("MusicService", "Reinitializing ScrobbleManager")
        scrobbleManager?.destroy()
        scrobbleManager = null
        initializeScrobbleManager()
    }

    private fun setupMediaSession() {
        Log.d("MusicService", "Setting up MediaSession")
        mediaSession = MediaSessionCompat(this, "MelodeeMediaSession")
        Log.d("MusicService", "MediaSession created: ${mediaSession?.sessionToken}")
        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.d("MusicService", "MediaSession onPlay")
                resumePlayback()
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
                    PlaybackStateCompat.SHUFFLE_MODE_ALL -> queueManager.setShuffle(true)
                    PlaybackStateCompat.SHUFFLE_MODE_NONE -> queueManager.setShuffle(false)
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
                queueManager.setRepeatMode(mode)
                updateMediaSessionPlaybackState()
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                Log.d("MusicService", "MediaSession onPlayFromSearch: $query")
                // Handle voice commands like "play next song"
                when {
                    query?.contains("next", ignoreCase = true) == true -> {
                        skipToNext()
                    }
                    query?.contains("previous", ignoreCase = true) == true -> {
                        skipToPrevious()
                    }
                    query?.contains("shuffle", ignoreCase = true) == true -> {
                        queueManager.toggleShuffle()
                        updateMediaSessionPlaybackState()
                    }
                    else -> {
                        // For now, just resume playback for other voice commands
                        resumePlayback()
                    }
                }
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                Log.d("MusicService", "MediaSession onPlayFromMediaId: $mediaId")
                // Handle playing specific songs by media ID
                mediaId?.let { id ->
                    val song = playlistManager.currentPlaylist.value.find { it.id.toString() == id }
                    if (song != null) {
                        queueManager.addToQueue(song)
                        playlistManager.playSong(song)
                        playSong(song)
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
        mediaSession?.setMetadata(MediaMetadataCompat.Builder().build())
        mediaSession?.isActive = true
        
        // Set the session token for MediaBrowserServiceCompat
        sessionToken = mediaSession?.sessionToken
        Log.d("MusicService", "MediaSession setup complete. Token: $sessionToken")
        Log.d("MusicService", "MediaSession active: ${mediaSession?.isActive}")
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
}