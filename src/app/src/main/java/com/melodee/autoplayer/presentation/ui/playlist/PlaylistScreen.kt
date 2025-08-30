package com.melodee.autoplayer.presentation.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.melodee.autoplayer.domain.model.Song
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.melodee.autoplayer.presentation.ui.components.PlaylistSongItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.media.session.MediaSessionManager
import android.media.session.MediaController
import android.media.MediaMetadata
import android.content.Context
import java.lang.SecurityException
import com.melodee.autoplayer.util.rememberPermissionState
import com.melodee.autoplayer.presentation.ui.components.FullImageViewer
import com.melodee.autoplayer.util.hasNotificationListenerAccess


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onBackClick: () -> Unit,
    globalCurrentSong: Song? = null
) {
    val context = LocalContext.current
    
    var musicService by remember { mutableStateOf<MediaSessionManager?>(null) }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    val permissionState = rememberPermissionState(
        context = context,
        onPermissionGranted = {
            if (hasNotificationListenerAccess(context)) {
                try {
                    musicService = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                    val controller = musicService?.getActiveSessions(null)?.firstOrNull()
                    mediaController = controller?.let { MediaController(context, it.sessionToken) }
                } catch (_: SecurityException) {
                    musicService = null
                    mediaController = null
                }
            }
        }
    )

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val shouldScrollToTop by viewModel.shouldScrollToTop.collectAsStateWithLifecycle()
    val totalSongs by viewModel.totalSongs.collectAsStateWithLifecycle()
    val currentSongsStart by viewModel.currentSongsStart.collectAsStateWithLifecycle()
    val currentSongsEnd by viewModel.currentSongsEnd.collectAsStateWithLifecycle()

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
                                    text = "${currentPlaylist.songCount} songs • ${currentPlaylist.durationFormatted}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    
                    // Song position indicator (above the list)
                    if (songs.isNotEmpty() && totalSongs > 0) {
                        com.melodee.autoplayer.presentation.ui.components.DisplayProgress(
                            start = currentSongsStart,
                            end = currentSongsEnd,
                            total = totalSongs,
                            label = "songs"
                        )
                    }

                    // Songs list
                                        LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(songs, key = { it.id }) { song ->
                            PlaylistSongItem(
                                song = song,
                                isCurrentlyPlaying = song.id == (globalCurrentSong?.id ?: currentSong?.id),
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
