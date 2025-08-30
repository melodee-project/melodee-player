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
import com.melodee.autoplayer.domain.model.Artist
import com.melodee.autoplayer.domain.model.Album
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
import androidx.compose.ui.focus.onFocusChanged
import com.melodee.autoplayer.util.rememberPermissionState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.text.style.TextOverflow
import com.melodee.autoplayer.presentation.ui.components.FullImageViewer
import com.melodee.autoplayer.presentation.ui.components.SongItem
import com.melodee.autoplayer.presentation.ui.components.AlbumItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlaylistClick: (String) -> Unit,
    globalCurrentSong: Song? = null
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
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val selectedArtist by viewModel.selectedArtist.collectAsStateWithLifecycle()
    val isArtistLoading by viewModel.isArtistLoading.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val totalSearchResults by viewModel.totalSearchResults.collectAsStateWithLifecycle()
    val currentPageStart by viewModel.currentPageStart.collectAsStateWithLifecycle()
    val currentPageEnd by viewModel.currentPageEnd.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val isAlbumsLoading by viewModel.isAlbumsLoading.collectAsStateWithLifecycle()
    val showAlbums by viewModel.showAlbums.collectAsStateWithLifecycle()
    val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
    val showAlbumSongs by viewModel.showAlbumSongs.collectAsStateWithLifecycle()
    val totalAlbums by viewModel.totalAlbums.collectAsStateWithLifecycle()
    val currentAlbumsStart by viewModel.currentAlbumsStart.collectAsStateWithLifecycle()
    val currentAlbumsEnd by viewModel.currentAlbumsEnd.collectAsStateWithLifecycle()
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
    ) { _ ->
        // Mobile layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
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

                // Artist autocomplete
                ArtistAutocomplete(
                    selectedArtist = selectedArtist,
                    artists = artists,
                    isLoading = isArtistLoading,
                    onArtistSearchQueryChanged = { query ->
                        viewModel.searchArtists(query)
                    },
                    onArtistSelected = { artist ->
                        viewModel.selectArtist(artist)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Browse buttons (shown when specific artist is selected)
                if (selectedArtist != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                viewModel.browseArtistAlbums()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse Albums")
                        }
                        
                        OutlinedButton(
                            onClick = { 
                                viewModel.browseArtistSongs()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse Songs")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                            
                            // Album songs position indicator (above the list)
                            if (showAlbumSongs && songs.isNotEmpty() && totalSearchResults > 0) {
                                Text(
                                    text = "Displaying $currentPageStart to $currentPageEnd of $totalSearchResults songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            
                            // Album position indicator (above the list)
                            if (showAlbums && albums.isNotEmpty() && totalAlbums > 0) {
                                Text(
                                    text = "Displaying $currentAlbumsStart to $currentAlbumsEnd of $totalAlbums albums",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                when {
                                    showAlbumSongs -> {
                                        // Show songs for selected album
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Songs in ${selectedAlbum?.name}",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(
                                                    onClick = { viewModel.hideAlbumSongs() }
                                                ) {
                                                    Text("Back to Albums")
                                                }
                                            }
                                        }
                                        
                                        items(songs, key = { it.id }) { song ->
                                            SongItem(
                                                song = song,
                                                onClick = { 
                                                    if (permissionState.hasMediaPermission) {
                                                        viewModel.playSong(song)
                                                    } else {
                                                        permissionState.requestPermission()
                                                    }
                                                },
                                                isCurrentlyPlaying = song.id == (globalCurrentSong?.id ?: currentSong?.id),
                                                onFavoriteClick = { songToFavorite, newStarredValue ->
                                                    viewModel.favoriteSong(songToFavorite, newStarredValue)
                                                }
                                            )
                                        }
                                    }
                                    showAlbums -> {
                                        // Show albums for selected artist
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Albums by ${selectedArtist?.name}",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(
                                                    onClick = { viewModel.hideAlbums() }
                                                ) {
                                                    Text("Back")
                                                }
                                            }
                                        }
                                        
                                        if (isAlbumsLoading) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(32.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator()
                                                }
                                            }
                                        } else {
                                            items(albums, key = { it.id }) { album ->
                                                AlbumItem(
                                                    album = album,
                                                    onClick = { 
                                                        viewModel.browseAlbumSongs(album)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    searchQuery.isNotEmpty() || songs.isNotEmpty() -> {
                                        // Show songs (either from search or artist browse)
                                        items(songs, key = { it.id }) { song ->
                                            SongItem(
                                                song = song,
                                                onClick = { 
                                                    if (permissionState.hasMediaPermission) {
                                                        viewModel.playSong(song)
                                                    } else {
                                                        permissionState.requestPermission()
                                                    }
                                                },
                                                isCurrentlyPlaying = song.id == (globalCurrentSong?.id ?: currentSong?.id),
                                                onFavoriteClick = { songToFavorite, newStarredValue ->
                                                    viewModel.favoriteSong(songToFavorite, newStarredValue)
                                                }
                                            )
                                        }
                                    }
                                    else -> {
                                        // Show playlists (default view)
                                        items(playlists, key = { it.id }) { playlist ->
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    

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
                        onError = { _, result ->
                            Log.e("HomeScreen", "Failed to load playlist image: ${result.throwable.message}")
                        }
                    )
                    .build(),
                contentDescription = "Playlist thumbnail",
                modifier = Modifier.size(64.dp),
                error = painterResource(id = R.drawable.ic_launcher_foreground),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistAutocomplete(
    selectedArtist: Artist?,
    artists: List<Artist>,
    isLoading: Boolean,
    onArtistSearchQueryChanged: (String) -> Unit,
    onArtistSelected: (Artist?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    
    // Update search query when selectedArtist changes - but don't trigger during typing
    LaunchedEffect(selectedArtist) {
        if (!isFocused) {  // Only update when not actively typing
            searchQuery = selectedArtist?.name ?: ""
        }
    }
    
    // Stable derived state for showing dropdown
    val shouldShowDropdown by remember {
        derivedStateOf {
            isExpanded && (searchQuery.isNotEmpty() || selectedArtist == null)
        }
    }
    
    // Debounced search - only call ViewModel after user stops typing
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            delay(300) // Shorter delay for better UX
            onArtistSearchQueryChanged(searchQuery)
        } else if (searchQuery.isEmpty()) {
            onArtistSearchQueryChanged("")
        }
    }
    
    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                // Show dropdown when typing - debounced search happens in LaunchedEffect
                isExpanded = true
            },
            label = { Text("Filter by Artist") },
            placeholder = { Text("Search artists or select 'Everyone'") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        // Show dropdown if there's text to search
                        isExpanded = searchQuery.isNotEmpty()
                    } else {
                        isExpanded = false
                    }
                },
            singleLine = true,
            trailingIcon = {
                Row {
                    if (isLoading && searchQuery.length >= 2) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    if (selectedArtist != null) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                isExpanded = false
                                isFocused = false
                                // First clear the artist selection
                                onArtistSelected(null)
                                // Then clear the search query to ensure fresh state
                                onArtistSearchQueryChanged("")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear selection"
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { 
                            isExpanded = !isExpanded
                            if (isExpanded) isFocused = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Show artists"
                        )
                    }
                }
            }
        )
        
        // Custom dropdown overlay that doesn't capture focus
        if (shouldShowDropdown) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Loading indicator
                    if (isLoading && searchQuery.length >= 2) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Searching artists...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Show "Everyone" option when not loading or when no specific search
                    if (!isLoading && (searchQuery.isEmpty() || "everyone".contains(searchQuery.lowercase()))) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = ""
                                        isExpanded = false
                                        // First clear the artist selection
                                        onArtistSelected(null)
                                        // Then clear the search query to ensure fresh state
                                        onArtistSearchQueryChanged("")
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Everyone")
                            }
                        }
                    }
                    
                    // Artist options - only show when not loading
                    if (!isLoading) {
                        items(artists) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = artist.name
                                        isExpanded = false
                                        onArtistSelected(artist)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Show artist thumbnail if available, otherwise show artist icon
                                if (artist.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(artist.thumbnailUrl)
                                            .crossfade(true)
                                            .listener(
                                                onError = { _, result ->
                                                    Log.e("HomeScreen", "Failed to load artist thumbnail for ${artist.name}: ${result.throwable.message}")
                                                }
                                            )
                                            .build(),
                                        contentDescription = "${artist.name} thumbnail",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        error = painterResource(id = R.drawable.ic_launcher_foreground),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = artist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    // No results message
                    if (!isLoading && searchQuery.length >= 2 && artists.isEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "No artists found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
