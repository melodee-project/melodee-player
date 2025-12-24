package com.melodee.autoplayer.presentation.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.melodee.autoplayer.MelodeeApplication
import com.melodee.autoplayer.domain.model.AuthResponse
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.presentation.ui.about.AboutScreen
import com.melodee.autoplayer.presentation.ui.home.HomeScreen
import com.melodee.autoplayer.presentation.ui.home.HomeViewModel
import com.melodee.autoplayer.presentation.ui.login.LoginScreen
import com.melodee.autoplayer.presentation.ui.login.LoginViewModel
import com.melodee.autoplayer.presentation.ui.nowplaying.NowPlayingScreen
import com.melodee.autoplayer.presentation.ui.nowplaying.NowPlayingViewModel
import com.melodee.autoplayer.presentation.ui.playlist.PlaylistScreen
import com.melodee.autoplayer.presentation.ui.playlist.PlaylistViewModel
import com.melodee.autoplayer.presentation.ui.settings.ThemeSettingsScreen
import com.melodee.autoplayer.ui.theme.*
import com.melodee.autoplayer.service.MusicService
import android.support.v4.media.MediaBrowserCompat
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val playlistViewModel: PlaylistViewModel by viewModels()
    private val nowPlayingViewModel: NowPlayingViewModel by viewModels()
    private var musicService: MusicService? = null
    private var bound = false
    private var mediaBrowser: MediaBrowserCompat? = null
    
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
            Log.d("MainActivity", "MusicService bound successfully")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            musicService = null
            Log.d("MainActivity", "MusicService unbound")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Log.w("MainActivity", "Some permissions were not granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewModels with context
        homeViewModel.setContext(this)
        playlistViewModel.setContext(this)
        nowPlayingViewModel.setContext(this)
        
        // Set up callback for playlist refresh when favorites change
        playlistViewModel.setOnPlaylistsNeedRefresh {
            homeViewModel.refreshPlaylists()
        }
        
        // Bind to the MusicService (this will start it if needed)
        Log.d("MainActivity", "Binding to MusicService for Android Auto compatibility")
        val serviceIntent = Intent(this, MusicService::class.java)
        
        // Only bind - don't start the service explicitly to avoid background service restrictions
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        // Initialize MediaBrowser for testing
        initializeMediaBrowser()
        
        // Request permissions
        requestRequiredPermissions()

        setContent {
            val context = LocalContext.current
            val themeManager = rememberThemeManager(context)
            val themeState = rememberThemeState(themeManager)
            
            MelodeeTheme(
                darkTheme = themeState.isDarkTheme,
                palette = themeState.palette,
                dynamicColor = themeState.isDynamicColorEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        loginViewModel = loginViewModel,
                        homeViewModel = homeViewModel,
                        playlistViewModel = playlistViewModel,
                        nowPlayingViewModel = nowPlayingViewModel,
                        themeManager = themeManager,
                        onLoginSuccess = { _ ->
                            // Reinitialize scrobble manager with user information
                            if (bound && musicService != null) {
                                musicService?.reinitializeScrobbleManager()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissions = mutableListOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.MEDIA_CONTENT_CONTROL
            )
            
            val permissionsToRequest = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsToRequest.isNotEmpty()) {
                requestPermissionLauncher.launch(permissionsToRequest)
            }
        }
    }

    private fun initializeMediaBrowser() {
        Log.d("MainActivity", "Initializing MediaBrowser for testing")
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MusicService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    Log.d("MainActivity", "MediaBrowser connected successfully!")
                    Log.d("MainActivity", "Session token: ${mediaBrowser?.sessionToken}")
                    
                    // Test loading children
                    mediaBrowser?.subscribe("root", object : MediaBrowserCompat.SubscriptionCallback() {
                        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                            Log.d("MainActivity", "Children loaded for $parentId: ${children.size} items")
                            children.forEach { item ->
                                Log.d("MainActivity", "Item: ${item.description.title}")
                            }
                        }
                        
                        override fun onError(parentId: String) {
                            Log.e("MainActivity", "Error loading children for $parentId")
                        }
                    })
                }
                
                override fun onConnectionFailed() {
                    Log.e("MainActivity", "MediaBrowser connection failed!")
                }
                
                override fun onConnectionSuspended() {
                    Log.w("MainActivity", "MediaBrowser connection suspended")
                }
            },
            null
        )
        
        mediaBrowser?.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaBrowser?.disconnect()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    loginViewModel: LoginViewModel,
    homeViewModel: HomeViewModel,
    playlistViewModel: PlaylistViewModel,
    nowPlayingViewModel: NowPlayingViewModel,
    themeManager: ThemeManager,
    onLoginSuccess: (AuthResponse) -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val themeState = rememberThemeState(themeManager)
    
    // Get authentication manager and state
    val authenticationManager = remember {
        (context.applicationContext as MelodeeApplication).authenticationManager
    }
    val isAuthenticated by authenticationManager.isAuthenticated.collectAsStateWithLifecycle()
    
    // Defensive auth restoration if persisted credentials exist
    LaunchedEffect(isAuthenticated) {
        if (!isAuthenticated) {
            val settings = com.melodee.autoplayer.data.SettingsManager(context)
            if (settings.isAuthenticated()) {
                Log.w("MainActivity", "Auth state false but credentials present; restoring from storage")
                authenticationManager.restoreAuthenticationFromStorage()
            }
        }
    }

    // Handle navigation based on authentication state changes
    LaunchedEffect(isAuthenticated) {
        val currentRoute = navController.currentDestination?.route
        
        if (isAuthenticated && currentRoute == "login") {
            Log.d("MainActivity", "User became authenticated, navigating to home")
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
            }
        } else if (!isAuthenticated && currentRoute != "login") {
            Log.d("MainActivity", "User became unauthenticated, navigating to login")
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }
    
    // Determine start destination based on authentication state
    val startDestination = if (isAuthenticated) {
        Log.d("MainActivity", "User is authenticated, starting with home screen")
        "home"
    } else {
        Log.d("MainActivity", "User not authenticated, starting with login screen") 
        "login"
    }
    
    // Set up ViewModels when user is authenticated
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            val currentUser = authenticationManager.getCurrentUser()
            if (currentUser != null) {
                Log.d("MainActivity", "Setting up ViewModels for authenticated user: ${currentUser.username}")
                
                // Set base URL for both view models
                homeViewModel.setBaseUrl(currentUser.serverUrl)
                playlistViewModel.setBaseUrl(currentUser.serverUrl)
                
                // Create User object for HomeViewModel
                val user = com.melodee.autoplayer.domain.model.User(
                    id = java.util.UUID.fromString(currentUser.userId.ifEmpty { "00000000-0000-0000-0000-000000000000" }),
                    username = currentUser.username,
                    email = currentUser.email,
                    thumbnailUrl = currentUser.thumbnailUrl,
                    imageUrl = currentUser.imageUrl
                )
                
                // Set user data (this will automatically load playlists)
                homeViewModel.setUser(user)
                
                Log.d("MainActivity", "ViewModels configured for authenticated user")
            }
        }
    }
    
    // Get current route to conditionally show icons
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showIcons = currentRoute != "login"

    // Collect playback state from multiple ViewModels to determine global playback state
    val homeCurrentSongs by homeViewModel.songs.collectAsStateWithLifecycle()
    val homeCurrentIndex by homeViewModel.currentSongIndex.collectAsStateWithLifecycle()
    val homeIsPlaying by homeViewModel.isPlaying.collectAsStateWithLifecycle()
    val homeProgress by homeViewModel.playbackProgress.collectAsStateWithLifecycle()
    val homeCurrentPlayingSong by homeViewModel.currentPlayingSong.collectAsStateWithLifecycle()
    
    val playlistCurrentSong by playlistViewModel.currentSong.collectAsStateWithLifecycle()
    val playlistIsPlaying by playlistViewModel.isPlaying.collectAsStateWithLifecycle()
    val playlistProgress by playlistViewModel.playbackProgress.collectAsStateWithLifecycle()
    
    // Debug state changes
    LaunchedEffect(homeCurrentSongs, homeCurrentIndex, homeIsPlaying) {
        Log.d("MainActivity", "HOME STATE CHANGED - songs: ${homeCurrentSongs.size}, index: $homeCurrentIndex, playing: $homeIsPlaying")
    }
    
    LaunchedEffect(playlistCurrentSong, playlistIsPlaying) {
        Log.d("MainActivity", "PLAYLIST STATE CHANGED - song: ${playlistCurrentSong?.title}, playing: $playlistIsPlaying")
    }
    
    // Determine global playback state - now that ViewModels are context-aware, this is simpler
    val globalCurrentSong = remember(homeCurrentSongs, homeCurrentIndex, homeIsPlaying, homeCurrentPlayingSong, playlistCurrentSong, playlistIsPlaying) {
        val result = when {
            // Home playing from service - this works even when browsing albums
            homeCurrentPlayingSong != null && homeIsPlaying -> {
                val song = homeCurrentPlayingSong
                Log.d("MainActivity", "Using home current playing song: ${song?.title}")
                song
            }
            // Home search results if playing and valid (fallback for older state)
            homeIsPlaying && homeCurrentIndex >= 0 && homeCurrentIndex < homeCurrentSongs.size -> {
                val song = homeCurrentSongs[homeCurrentIndex]
                Log.d("MainActivity", "Using home search song: ${song.title}")
                song
            }
            // Playlist if playing
            playlistCurrentSong != null && playlistIsPlaying -> {
                val song = playlistCurrentSong
                Log.d("MainActivity", "Using playlist song: ${song?.title}")
                song
            }
            // Paused states - show last known song
            homeCurrentPlayingSong != null -> {
                val song = homeCurrentPlayingSong
                Log.d("MainActivity", "Using home current playing song (paused): ${song?.title}")
                song
            }
            // Fallback: show home song from search results (paused state)
            homeCurrentIndex >= 0 && homeCurrentIndex < homeCurrentSongs.size -> {
                val song = homeCurrentSongs[homeCurrentIndex]
                Log.d("MainActivity", "Using home search song (paused): ${song.title}")
                song
            }
            // Fallback: show playlist song even if not playing (paused state)
            playlistCurrentSong != null -> {
                val song = playlistCurrentSong
                Log.d("MainActivity", "Using playlist song (paused): ${song?.title}")
                song
            }
            else -> {
                Log.d("MainActivity", "No current song found")
                null
            }
        }
        
        Log.d("MainActivity", "=== GLOBAL STATE DEBUG ===")
        Log.d("MainActivity", "homeIsPlaying: $homeIsPlaying")
        Log.d("MainActivity", "homeCurrentIndex: $homeCurrentIndex") 
        Log.d("MainActivity", "homeCurrentSongs.size: ${homeCurrentSongs.size}")
        Log.d("MainActivity", "playlistCurrentSong: ${playlistCurrentSong?.title}")
        Log.d("MainActivity", "playlistIsPlaying: $playlistIsPlaying")
        Log.d("MainActivity", "Final result: ${result?.title}")
        Log.d("MainActivity", "========================")
        result
    }
    
    val globalIsPlaying = homeIsPlaying || playlistIsPlaying
    val globalProgress = when {
        playlistCurrentSong != null && playlistIsPlaying -> playlistProgress
        homeIsPlaying -> homeProgress
        playlistCurrentSong != null -> playlistProgress
        else -> homeProgress
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Melodee Player") },
                actions = {
                    if (showIcons) {
                        IconButton(onClick = { themeManager.toggleDarkTheme() }) {
                            Icon(
                                imageVector = if (themeState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (themeState.isDarkTheme) "Switch to Light Theme" else "Switch to Dark Theme"
                            )
                        }
                        IconButton(onClick = { navController.navigate("theme_settings") }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Theme Settings"
                            )
                        }
                        IconButton(onClick = { navController.navigate("about") }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About"
                            )
                        }
                        IconButton(onClick = { 
                            // Perform logout
                            loginViewModel.logout()
                            homeViewModel.logout()
                            playlistViewModel.logout()
                            nowPlayingViewModel.logout()
                            
                            // Navigate to login and clear back stack
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Logout"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Global Mini Player (hide when on now playing screen)
            if (globalCurrentSong != null && showIcons && currentRoute != "now_playing") {
                com.melodee.autoplayer.presentation.ui.components.MiniPlayer(
                    currentSong = globalCurrentSong,
                    isPlaying = globalIsPlaying,
                    progress = globalProgress,
                    onPlayPauseClick = {
                        // Delegate to the appropriate ViewModel based on current song source
                        when {
                            playlistCurrentSong != null && playlistIsPlaying -> playlistViewModel.togglePlayPause()
                            homeIsPlaying -> homeViewModel.togglePlayPause()
                            playlistCurrentSong != null -> playlistViewModel.togglePlayPause()
                            else -> homeViewModel.togglePlayPause()
                        }
                    },
                    onPreviousClick = {
                        // Delegate to the appropriate ViewModel based on current song source
                        when {
                            playlistCurrentSong != null && playlistIsPlaying -> playlistViewModel.skipToPrevious()
                            homeIsPlaying -> {
                                val currentIndex = homeViewModel.currentSongIndex.value
                                if (currentIndex > 0) {
                                    homeViewModel.playSong(homeViewModel.songs.value[currentIndex - 1])
                                }
                            }
                            playlistCurrentSong != null -> playlistViewModel.skipToPrevious()
                            else -> {
                                val currentIndex = homeViewModel.currentSongIndex.value
                                if (currentIndex > 0) {
                                    homeViewModel.playSong(homeViewModel.songs.value[currentIndex - 1])
                                }
                            }
                        }
                    },
                    onNextClick = {
                        // Delegate to the appropriate ViewModel based on current song source
                        when {
                            playlistCurrentSong != null && playlistIsPlaying -> playlistViewModel.skipToNext()
                            homeIsPlaying -> {
                                val currentIndex = homeViewModel.currentSongIndex.value
                                val songs = homeViewModel.songs.value
                                if (currentIndex >= 0 && currentIndex < songs.size - 1) {
                                    homeViewModel.playSong(songs[currentIndex + 1])
                                }
                            }
                            playlistCurrentSong != null -> playlistViewModel.skipToNext()
                            else -> {
                                val currentIndex = homeViewModel.currentSongIndex.value
                                val songs = homeViewModel.songs.value
                                if (currentIndex >= 0 && currentIndex < songs.size - 1) {
                                    homeViewModel.playSong(songs[currentIndex + 1])
                                }
                            }
                        }
                    },
                    onMiniPlayerClick = {
                        navController.navigate("now_playing")
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = { authResponse ->
                        // Ensure any existing playback is stopped and queue cleared on login
                        try {
                            val ctx = context
                            val stopIntent = android.content.Intent(ctx, com.melodee.autoplayer.service.MusicService::class.java).apply {
                                action = com.melodee.autoplayer.service.MusicService.ACTION_STOP
                            }
                            ctx.startService(stopIntent)
                            val clearIntent = android.content.Intent(ctx, com.melodee.autoplayer.service.MusicService::class.java).apply {
                                action = com.melodee.autoplayer.service.MusicService.ACTION_CLEAR_QUEUE
                            }
                            ctx.startService(clearIntent)
                        } catch (_: Exception) {}
                        
                        // Set base URL for both view models
                        homeViewModel.setBaseUrl(loginViewModel.serverUrl)
                        playlistViewModel.setBaseUrl(loginViewModel.serverUrl)
                        
                        // Set user data (this will automatically load playlists)
                        homeViewModel.setUser(authResponse.user)
                        
                        // Reinitialize scrobble manager with user information
                        onLoginSuccess(authResponse)
                        
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("home") {
                HomeScreen(
                    viewModel = homeViewModel,
                    onPlaylistClick = { playlistId ->
                        Log.d("MainActivity", "Navigating to playlist: $playlistId")
                        navController.navigate("playlist/$playlistId")
                    },
                    globalCurrentSong = globalCurrentSong
                )
            }
            composable("playlist/{playlistId}") { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
                // Load the playlist before showing the screen
                LaunchedEffect(playlistId) {
                    playlistViewModel.loadPlaylist(playlistId)
                }
                PlaylistScreen(
                    viewModel = playlistViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    globalCurrentSong = globalCurrentSong
                )
            }
            composable("theme_settings") {
                ThemeSettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("about") {
                AboutScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("now_playing") {
                val globalDuration = globalCurrentSong?.durationMs?.toLong() ?: 0L
                val globalCurrentPosition = (globalProgress * globalDuration).toLong()
                
                NowPlayingScreen(
                    viewModel = nowPlayingViewModel,
                    onBackClick = { navController.popBackStack() },
                    globalCurrentSong = globalCurrentSong,
                    globalIsPlaying = globalIsPlaying,
                    globalProgress = globalProgress,
                    globalCurrentPosition = globalCurrentPosition,
                    globalCurrentDuration = globalDuration,
                    onGlobalPlayPauseClick = {
                        // Use the same logic as the mini player
                        when {
                            playlistCurrentSong != null && playlistIsPlaying -> playlistViewModel.togglePlayPause()
                            homeIsPlaying -> homeViewModel.togglePlayPause()
                            playlistCurrentSong != null -> playlistViewModel.togglePlayPause()
                            else -> homeViewModel.togglePlayPause()
                        }
                    },
                    onGlobalPreviousClick = {
                        // Use the same logic as the mini player
                        when {
                            playlistCurrentSong != null && playlistIsPlaying -> playlistViewModel.skipToPrevious()
                            homeIsPlaying -> {
                                val currentIndex = homeViewModel.currentSongIndex.value
                                if (currentIndex > 0) {
                                    homeViewModel.playSong(homeViewModel.songs.value[currentIndex - 1])
                                }
                            }
                            playlistCurrentSong != null -> playlistViewModel.skipToPrevious()
                            else -> {
                                val currentIndex = homeViewModel.currentSongIndex.value
                                if (currentIndex > 0) {
                                    homeViewModel.playSong(homeViewModel.songs.value[currentIndex - 1])
                                }
                            }
                        }
                    },
                    onGlobalNextClick = {
                        // Use the same logic as the mini player
                        when {
                            playlistCurrentSong != null && playlistIsPlaying -> playlistViewModel.skipToNext()
                            homeIsPlaying -> {
                                val currentIndex = homeViewModel.currentSongIndex.value
                                val songs = homeViewModel.songs.value
                                if (currentIndex >= 0 && currentIndex < songs.size - 1) {
                                    homeViewModel.playSong(songs[currentIndex + 1])
                                }
                            }
                            playlistCurrentSong != null -> playlistViewModel.skipToNext()
                            else -> {
                                val currentIndex = homeViewModel.currentSongIndex.value
                                val songs = homeViewModel.songs.value
                                if (currentIndex >= 0 && currentIndex < songs.size - 1) {
                                    homeViewModel.playSong(songs[currentIndex + 1])
                                }
                            }
                        }
                    },
                    onGlobalSeekTo = { progress ->
                        // Delegate seeking to the appropriate ViewModel
                        when {
                            homeIsPlaying -> {
                                val position = (progress * globalDuration).toLong() 
                                homeViewModel.seekTo(position)
                            }
                            else -> {
                                val position = (progress * globalDuration).toLong()
                                homeViewModel.seekTo(position)
                            }
                        }
                    }
                )
            }
        }
    }
}
