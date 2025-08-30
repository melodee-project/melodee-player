package com.melodee.autoplayer.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.melodee.autoplayer.domain.model.Song

/**
 * Custom song item component for playlist screens that removes click-to-play from title
 * and adds a play icon overlay on the album image
 */
@Composable
fun PlaylistSongItem(
    song: Song,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    onFavoriteClick: ((Song, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showFullImage by remember { mutableStateOf(false) }
    var isStarred by remember { mutableStateOf(song.userStarred) }

    // Update isStarred when song.userStarred changes
    LaunchedEffect(song.userStarred) {
        isStarred = song.userStarred
    }

    val itemHeight = 72.dp
    val thumbnailSize = 56.dp
    val horizontalPadding = 16.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .background(
                if (isCurrentlyPlaying) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with play icon overlay
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.thumbnailUrl)
                    .crossfade(true)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = "Song thumbnail",
                modifier = Modifier
                    .size(thumbnailSize)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            // Play icon overlay
            Box(
                modifier = Modifier
                    .size(thumbnailSize)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClick() }
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play song",
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Song details (no click functionality on text)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrentlyPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Text(
                text = "${song.artist.name} • ${song.album.name}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        Text(
            text = song.durationFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Favorite button (if callback provided)
        onFavoriteClick?.let { callback ->
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = {
                    val newStarredValue = !isStarred
                    isStarred = newStarredValue
                    callback(song, newStarredValue)
                }
            ) {
                Icon(
                    imageVector = if (isStarred) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isStarred) "Remove from favorites" else "Add to favorites",
                    tint = if (isStarred) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }

    // Full image viewer
    if (showFullImage) {
        FullImageViewer(
            imageUrl = song.imageUrl,
            onDismiss = { showFullImage = false }
        )
    }
}