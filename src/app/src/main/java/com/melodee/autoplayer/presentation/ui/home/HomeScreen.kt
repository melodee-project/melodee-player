package com.melodee.autoplayer.presentation.ui.home

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import com.melodee.autoplayer.R
import com.melodee.autoplayer.domain.model.Playlist
import com.melodee.autoplayer.domain.model.Song
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.media.MediaMetadata
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import com.melodee.autoplayer.util.rememberPermissionState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.text.style.TextOverflow
import com.melodee.autoplayer.presentation.ui.components.FullImageViewer


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlaylistClick: (String) -> Unit
) {
    val context = LocalContext.current
    
    var musicService by remember { mutableStateOf<MediaSessionManager?>(null) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    // Set up media session monitoring
    LaunchedEffect(Unit) {
        viewModel.setContext(context)
        try {
            musicService = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val controller = musicService?.getActiveSessions(null)?.firstOrNull()
            mediaController = controller?.let { MediaController(context, it.sessionToken) }
        } catch (e: SecurityException) {
            Log.e("HomeScreen", "Failed to get media session: ${e.message}")
        }
    }

    // Monitor media controller changes
    DisposableEffect(mediaController) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                super.onPlaybackStateChanged(state)
                // Do not automatically toggle playback on state changes
                // This prevents unwanted playback resume during search operations
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                super.onMetadataChanged(metadata)
                // Metadata changes are now handled by the ViewModel
            }
        }

        mediaController?.registerCallback(callback)

        onDispose {
            mediaController?.unregisterCallback(callback)
        }
    }

    val permissionState = rememberPermissionState(
        context = context,
        onPermissionGranted = {
            try {
                musicService = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val controller = musicService?.getActiveSessions(null)?.firstOrNull()
                mediaController = controller?.let { MediaController(context, it.sessionToken) }
            } catch (e: SecurityException) {
                Log.e("HomeScreen", "Failed to get media session: ${e.message}")
                musicService = null
                mediaController = null
            }
        }
    )

    // Request permissions on first launch
    LaunchedEffect(Unit) {
        if (!permissionState.hasMediaPermission) {
            permissionState.requestPermission()
        }
    }

    val user by viewModel.user.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val totalSearchResults by viewModel.totalSearchResults.collectAsStateWithLifecycle()
    val currentPageStart by viewModel.currentPageStart.collectAsStateWithLifecycle()
    val currentPageEnd by viewModel.currentPageEnd.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Infinite scroll
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index
        }
        .map { index -> index != null && index >= listState.layoutInfo.totalItemsCount - 5 }
        .distinctUntilChanged()
        .collect { shouldLoadMore ->
            if (shouldLoadMore && searchQuery.isNotEmpty()) {
                viewModel.loadMoreSearchResults()
            }
        }
    }

    // Get current song from search results if available
    val currentSong = remember(currentSongIndex, songs) {
        if (currentSongIndex >= 0 && currentSongIndex < songs.size) {
            songs[currentSongIndex]
        } else {
            null
        }
    }

    // Update playback state when media controller changes
    LaunchedEffect(mediaController) {
        try {
            mediaController?.registerCallback(object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    super.onPlaybackStateChanged(state)
                    // Do not automatically toggle playback on state changes
                    // This prevents unwanted playback resume during search operations
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    super.onMetadataChanged(metadata)
                    // Metadata changes are handled by the ViewModel's media controller callback
                }
            })
        } catch (e: SecurityException) {
            Log.e("HomeScreen", "Failed to register media controller callback: ${e.message}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.clearQueue() }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear Queue",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // Mobile layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // User header
                user?.let { currentUser ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentUser.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "User avatar",
                            modifier = Modifier.size(48.dp),
                            error = painterResource(id = R.drawable.ic_launcher_foreground),
                            onError = {
                                Log.e("HomeScreen", "Failed to load user avatar: ${currentUser.thumbnailUrl}")
                            },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentUser.username,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        viewModel.searchSongs(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search songs...") },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.clearSearchAndStopPlayback()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isLoading && songs.isEmpty() && playlists.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (searchQuery.isNotEmpty() && songs.isNotEmpty()) {
                                Text(
                                    text = "Displaying $currentPageStart to $currentPageEnd of $totalSearchResults results",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (searchQuery.isNotEmpty()) {
                                    items(songs) { song ->
                                        SongItem(
                                            song = song,
                                            onClick = { 
                                                if (permissionState.hasMediaPermission) {
                                                    viewModel.playSong(song)
                                                } else {
                                                    permissionState.requestPermission()
                                                }
                                            },
                                            isCurrentlyPlaying = song == currentSong,
                                            onFavoriteClick = { songToFavorite, newStarredValue ->
                                                viewModel.favoriteSong(songToFavorite, newStarredValue)
                                            }
                                        )
                                    }
                                } else {
                                    items(playlists) { playlist ->
                                        PlaylistItem(
                                            playlist = playlist,
                                            onClick = { onPlaylistClick(playlist.id.toString()) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Enhanced logging for playlist thumbnail
    LaunchedEffect(playlist.thumbnailUrl) {
        Log.d("HomeScreen", "Playlist: ${playlist.name}")
        Log.d("HomeScreen", "Thumbnail URL: ${playlist.thumbnailUrl}")
        Log.d("HomeScreen", "URL valid: ${playlist.thumbnailUrl.isNotBlank()}")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(playlist.thumbnailUrl)
                    .crossfade(true)
                    .listener(
                        onStart = {
                            Log.d("HomeScreen", "Starting to load playlist image: ${playlist.thumbnailUrl}")
                        },
                        onSuccess = { _, result ->
                            Log.d("HomeScreen", "Successfully loaded playlist image: ${playlist.thumbnailUrl}")
                            Log.d("HomeScreen", "Image size: ${result.drawable.intrinsicWidth}x${result.drawable.intrinsicHeight}")
                        },
                        onError = { _, result ->
                            Log.e("HomeScreen", "Failed to load playlist image: ${playlist.thumbnailUrl}")
                            Log.e("HomeScreen", "Error: ${result.throwable}")
                            result.throwable.printStackTrace()
                        }
                    )
                    .build(),
                contentDescription = "Playlist thumbnail",
                modifier = Modifier.size(64.dp),
                error = painterResource(id = R.drawable.ic_launcher_foreground),
                onError = {
                    Log.e("HomeScreen", "Failed to load playlist image: ${playlist.thumbnailUrl}")
                },
                onSuccess = {
                    Log.d("HomeScreen", "Successfully loaded playlist image: ${playlist.thumbnailUrl}")
                },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${playlist.songCount} songs • ${playlist.durationFormatted}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SongItem(
    song: Song,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    onFavoriteClick: (Song, Boolean) -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }
    var isStarred by remember { mutableStateOf(song.userStarred) }

    // Update isStarred when song.userStarred changes
    LaunchedEffect(song.userStarred) {
        isStarred = song.userStarred
    }

    // Update playing state when song changes or media controller updates
    LaunchedEffect(song, isCurrentlyPlaying) {
        isPlaying = isCurrentlyPlaying
    }

    val itemHeight = 72.dp
    val thumbnailSize = 56.dp
    val horizontalPadding = 16.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Song thumbnail",
            modifier = Modifier
                .size(thumbnailSize)
                .clip(RoundedCornerShape(8.dp))
                .clickable { 
                    song.imageUrl.let { 
                        showFullImage = true 
                    }
                },
            error = painterResource(id = R.drawable.ic_launcher_foreground),
            onError = {
                Log.e("HomeScreen", "Failed to load song image: ${song.thumbnailUrl}")
            },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Song info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.durationFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Favorite star icon
        IconButton(
            onClick = {
                val newStarredValue = !isStarred
                isStarred = newStarredValue
                onFavoriteClick(song, newStarredValue)
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isStarred) "Remove from favorites" else "Add to favorites",
                tint = if (isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }

        // Play/Pause icon
        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Pause",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
    
    // Show full image viewer when requested
    if (showFullImage) {
        FullImageViewer(
            imageUrl = song.imageUrl,
            contentDescription = "Song Image - ${song.title}",
            onDismiss = { showFullImage = false }
        )
    }
} 