package com.melodee.autoplayer.presentation.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.melodee.autoplayer.domain.model.Song
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.pm.PackageManager
import android.widget.Toast
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.media.MediaMetadata
import android.content.Context
import java.lang.SecurityException
import com.melodee.autoplayer.util.rememberPermissionState
import android.content.Intent
import com.melodee.autoplayer.service.MusicService
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import com.melodee.autoplayer.presentation.ui.components.FullImageViewer
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    var musicService by remember { mutableStateOf<MediaSessionManager?>(null) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    val permissionState = rememberPermissionState(
        context = context,
        onPermissionGranted = {
            try {
                musicService = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val controller = musicService?.getActiveSessions(null)?.firstOrNull()
                mediaController = controller?.let { MediaController(context, it.sessionToken) }
            } catch (e: SecurityException) {
                musicService = null
                mediaController = null
            }
        }
    )

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val shouldScrollToTop by viewModel.shouldScrollToTop.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    
    // Set context in ViewModel
    LaunchedEffect(Unit) {
        viewModel.setContext(context)
    }
    
    // Handle scroll to top when songs are loaded after refresh
    LaunchedEffect(songs, shouldScrollToTop) {
        if (shouldScrollToTop && songs.isNotEmpty()) {
            listState.animateScrollToItem(0)
            viewModel.onScrollToTopHandled()
        }
    }

    // Infinite scrolling
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
        .map { index -> index != null && index >= listState.layoutInfo.totalItemsCount - 5 }
        .distinctUntilChanged()
        .collect { shouldLoadMore ->
            if (shouldLoadMore) {
                viewModel.loadMoreSongs()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshSongs() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Mobile layout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && songs.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Playlist info
                    playlist?.let { currentPlaylist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = currentPlaylist.name,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                currentPlaylist.description?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                Text(
                                    text = "${currentPlaylist.songsCount} songs • ${currentPlaylist.durationFormatted}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    // Songs list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(songs) { song ->
                            SongItem(
                                song = song,
                                isPlaying = song.id == currentSong?.id,
                                onClick = { 
                                    if (permissionState.hasMediaPermission) {
                                        viewModel.playSong(song)
                                    } else {
                                        permissionState.requestPermission()
                                    }
                                },
                                onFavoriteClick = { songToFavorite, newStarredValue ->
                                    viewModel.favoriteSong(songToFavorite, newStarredValue)
                                }
                            )
                        }
                        
                        if (isLoading && songs.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: (Song, Boolean) -> Unit
) {
    val context = LocalContext.current
    var musicService by remember { mutableStateOf<MediaSessionManager?>(null) }
    var isCurrentlyPlaying by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }
    var isStarred by remember { mutableStateOf(song.userStarred) }

    // Update isStarred when song.userStarred changes
    LaunchedEffect(song.userStarred) {
        isStarred = song.userStarred
    }

    LaunchedEffect(song) {
        try {
            musicService = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val controller = musicService?.getActiveSessions(null)?.firstOrNull()
            val mediaController = controller?.let { MediaController(context, it.sessionToken) }
            val currentSongId = mediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            isCurrentlyPlaying = currentSongId == song.id.toString()
        } catch (e: SecurityException) {
            isCurrentlyPlaying = false
        }
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
                if (isPlaying || isCurrentlyPlaying) {
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
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(thumbnailSize)
                .clip(RoundedCornerShape(8.dp))
                .clickable { 
                    song.imageUrl.let { 
                        showFullImage = true 
                    }
                },
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Song info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying || isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

        // Favorite heart icon
        IconButton(
            onClick = {
                val newStarredValue = !isStarred
                isStarred = newStarredValue
                onFavoriteClick(song, newStarredValue)
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isStarred) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isStarred) "Remove from favorites" else "Add to favorites",
                tint = if (isStarred) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }

        // Play/Pause icon
        if (isPlaying || isCurrentlyPlaying) {
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
            contentDescription = "Song Image - ${song.name}",
            onDismiss = { showFullImage = false }
        )
    }
}

// Removed PlaybackControls and formatDuration functions - now handled by global mini player in MainActivity 