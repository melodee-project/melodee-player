package com.melodee.autoplayer.presentation.ui.nowplaying

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.service.QueueManager
import com.melodee.autoplayer.presentation.ui.components.FullImageViewer
import com.melodee.autoplayer.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBackClick: () -> Unit,
    // Global state parameters for sync with mini player
    globalCurrentSong: Song? = null,
    globalIsPlaying: Boolean = false,
    globalProgress: Float = 0f,
    globalCurrentPosition: Long = 0L,
    globalCurrentDuration: Long = 0L,
    onGlobalPlayPauseClick: () -> Unit = {},
    onGlobalPreviousClick: () -> Unit = {},
    onGlobalNextClick: () -> Unit = {},
    onGlobalSeekTo: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Use global state if provided, otherwise fall back to ViewModel state
    val currentSong = globalCurrentSong ?: viewModel.currentSong.collectAsStateWithLifecycle().value
    val isPlaying = if (globalCurrentSong != null) globalIsPlaying else viewModel.isPlaying.collectAsStateWithLifecycle().value
    val playbackProgress = if (globalCurrentSong != null) globalProgress else viewModel.playbackProgress.collectAsStateWithLifecycle().value
    val currentPosition = if (globalCurrentSong != null) globalCurrentPosition else viewModel.currentPosition.collectAsStateWithLifecycle().value
    val currentDuration = if (globalCurrentSong != null) globalCurrentDuration else viewModel.currentDuration.collectAsStateWithLifecycle().value
    
    // These are still from ViewModel as they're not part of the sync issue
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    
    var showQueue by remember { mutableStateOf(false) }
    
    // Set context in ViewModel only if not using global state
    LaunchedEffect(Unit) {
        if (globalCurrentSong == null) {
            viewModel.setContext(context)
        }
    }
    
    // Mobile layout
    MobileNowPlayingLayout(
        currentSong = currentSong,
        isPlaying = isPlaying,
        playbackProgress = playbackProgress,
        currentPosition = currentPosition,
        currentDuration = currentDuration,
        isShuffleEnabled = isShuffleEnabled,
        repeatMode = repeatMode,
        queue = queue,
        currentIndex = currentIndex,
        showQueue = showQueue,
        onBackClick = onBackClick,
        onPlayPauseClick = if (globalCurrentSong != null) onGlobalPlayPauseClick else ({ viewModel.togglePlayPause() }),
        onPreviousClick = if (globalCurrentSong != null) onGlobalPreviousClick else ({ viewModel.skipToPrevious() }),
        onNextClick = if (globalCurrentSong != null) onGlobalNextClick else ({ viewModel.skipToNext() }),
        onShuffleClick = { viewModel.toggleShuffle() },
        onRepeatClick = { viewModel.toggleRepeat() },
        onSeekTo = if (globalCurrentSong != null) onGlobalSeekTo else { progress -> viewModel.seekTo((progress * currentDuration).toLong()) },
        onQueueToggle = { showQueue = !showQueue },
        onQueueItemClick = { song -> viewModel.playSong(song) },
        onQueueItemRemove = { index -> viewModel.removeFromQueue(index) },
        onClearQueue = { viewModel.clearQueue() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileNowPlayingLayout(
    currentSong: Song?,
    isPlaying: Boolean,
    playbackProgress: Float,
    currentPosition: Long,
    currentDuration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: QueueManager.RepeatMode,
    queue: List<Song>,
    currentIndex: Int,
    showQueue: Boolean,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onQueueToggle: () -> Unit,
    onQueueItemClick: (Song) -> Unit,
    onQueueItemRemove: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onQueueToggle) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic, 
                            contentDescription = "Queue",
                            tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showQueue) {
            QueueView(
                queue = queue,
                currentIndex = currentIndex,
                onItemClick = onQueueItemClick,
                onItemRemove = onQueueItemRemove,
                onClearQueue = onClearQueue,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // Album Art
                AlbumArtSection(
                    song = currentSong,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .size(320.dp)
                        .weight(1f, false)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Song Info
                SongInfoSection(
                    song = currentSong,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Progress Bar
                ProgressSection(
                    progress = playbackProgress,
                    currentPosition = currentPosition,
                    duration = currentDuration,
                    onSeekTo = onSeekTo,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Control Buttons
                ControlButtonsSection(
                    isPlaying = isPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AlbumArtSection(
    song: Song?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var showFullImage by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )
        
        // Album art
        AsyncImage(
            model = song?.imageUrl ?: song?.thumbnailUrl,
            contentDescription = "Album Art",
            modifier = Modifier
                .fillMaxSize(0.9f)
                .clip(CircleShape)
                .rotate(if (isPlaying) rotation else 0f)
                .clickable { 
                    song?.imageUrl?.let { 
                        showFullImage = true 
                    }
                },
            contentScale = ContentScale.Crop
        )
        
        // Play/pause overlay
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = "Paused",
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
            }
        }
    }
    
    // Show full image viewer when requested
    if (showFullImage && song?.imageUrl != null) {
        FullImageViewer(
            imageUrl = song.imageUrl,
            contentDescription = "Album Art - ${song.title}",
            onDismiss = { showFullImage = false }
        )
    }
}

@Composable
private fun SongInfoSection(
    song: Song?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = song?.title ?: "No song playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = song?.artist?.name ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = song?.album?.name ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressSection(
    progress: Float,
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Slider(
            value = progress,
            onValueChange = onSeekTo,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ControlButtonsSection(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: QueueManager.RepeatMode,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonSize = 56.dp
    val iconSize = 28.dp
    val playButtonSize = 72.dp
    val playIconSize = 36.dp
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle button
        IconButton(
            onClick = onShuffleClick,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                modifier = Modifier.size(iconSize),
                tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Previous button
        IconButton(
            onClick = onPreviousClick,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(iconSize)
            )
        }
        
        // Play/Pause button
        FloatingActionButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(playButtonSize),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(playIconSize),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        // Next button
        IconButton(
            onClick = onNextClick,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(iconSize)
            )
        }
        
        // Repeat button
        IconButton(
            onClick = onRepeatClick,
            modifier = Modifier.size(buttonSize)
        ) {
            val (icon, tint) = when (repeatMode) {
                QueueManager.RepeatMode.NONE -> Icons.Filled.Repeat to MaterialTheme.colorScheme.onSurfaceVariant
                QueueManager.RepeatMode.ALL -> Icons.Filled.Repeat to MaterialTheme.colorScheme.primary
                QueueManager.RepeatMode.ONE -> Icons.Filled.RepeatOne to MaterialTheme.colorScheme.primary
            }
            Icon(
                icon,
                contentDescription = "Repeat",
                modifier = Modifier.size(iconSize),
                tint = tint
            )
        }
    }
}

@Composable
private fun QueueView(
    queue: List<Song>,
    currentIndex: Int,
    onItemClick: (Song) -> Unit,
    onItemRemove: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to current song
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < queue.size) {
            listState.animateScrollToItem(currentIndex)
        }
    }
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Queue (${queue.size} songs)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (queue.isNotEmpty()) {
                TextButton(onClick = onClearQueue) {
                    Text(
                        text = "Clear All",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(queue.size) { index ->
                QueueItem(
                    song = queue[index],
                    isCurrentSong = index == currentIndex,
                    onClick = { onItemClick(queue[index]) },
                    onRemove = { onItemRemove(index) }
                )
            }
        }
    }
}

@Composable
private fun QueueItem(
    song: Song,
    isCurrentSong: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val itemHeight = 64.dp
    val thumbnailSize = 48.dp
    var showFullImage by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentSong) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
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
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Duration
            Text(
                text = song.durationFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from queue",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // Show full image viewer when requested
    if (showFullImage) {
        FullImageViewer(
            imageUrl = song.imageUrl,
            contentDescription = "Queue Song Image - ${song.title}",
            onDismiss = { showFullImage = false }
        )
    }
} 