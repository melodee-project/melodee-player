# Android Auto Enhancement Technical Specification

This document provides detailed technical specifications for implementing the Android Auto enhancements.

## 🏗️ Architecture Overview

### Current Android Auto Integration
```
Android Auto App ←→ MediaBrowserService ←→ MusicService
                         ↓
                   MediaSession ←→ QueueManager
                                      ↓
                                 MusicRepository ←→ API
```

### Enhanced Integration Flow
```
Android Auto App ←→ MediaBrowserService ←→ MusicService
                         ↓                      ↓
                   MediaSession ←→ QueueManager + VoiceCommandParser
                         ↓                      ↓
                   CustomActions          Enhanced Queue Ops
                         ↓                      ↓
                   FavoriteAPI ←→ MusicRepository ←→ Enhanced API
```

---

## 📱 Phase 1: Custom Favorite Action

### Technical Implementation

#### 1.1 MediaSession Custom Actions
```kotlin
// Location: MusicService.kt - updateMediaSessionPlaybackState()
val customActions = mutableListOf<PlaybackStateCompat.CustomAction>()

// Favorite action (dynamic based on current song)
val currentSong = queueManager.getCurrentSong()
if (currentSong != null) {
    val favoriteAction = if (currentSong.userStarred) {
        PlaybackStateCompat.CustomAction.Builder(
            ACTION_REMOVE_FAVORITE,
            getString(R.string.remove_favorite),
            R.drawable.ic_favorite_filled
        ).build()
    } else {
        PlaybackStateCompat.CustomAction.Builder(
            ACTION_ADD_FAVORITE,
            getString(R.string.add_favorite),
            R.drawable.ic_favorite_border
        ).build()
    }
    customActions.add(favoriteAction)
}

val stateBuilder = PlaybackStateCompat.Builder()
    .setActions(/* standard actions */)
    .setState(/* current state */)

customActions.forEach { action ->
    stateBuilder.addCustomAction(action)
}

mediaSession?.setPlaybackState(stateBuilder.build())
```

#### 1.2 Custom Action Handler
```kotlin
// Location: MusicService.kt - MediaSession callback
override fun onCustomAction(action: String?, extras: Bundle?) {
    Log.d(TAG, "Custom action received: $action")
    
    when (action) {
        ACTION_ADD_FAVORITE -> {
            queueManager.getCurrentSong()?.let { song ->
                toggleFavoriteStatus(song, true)
            }
        }
        ACTION_REMOVE_FAVORITE -> {
            queueManager.getCurrentSong()?.let { song ->
                toggleFavoriteStatus(song, false)
            }
        }
    }
}

private fun toggleFavoriteStatus(song: Song, favorite: Boolean) {
    serviceScope.launch {
        try {
            val result = NetworkModule.getMusicApi().favoriteSong(
                song.id.toString(),
                favorite
            )
            
            if (result.isSuccessful) {
                // Update local state
                song.userStarred = favorite
                
                // Update MediaSession state
                updateMediaSessionPlaybackState()
                
                // Send confirmation
                sendCustomEvent("FAVORITE_TOGGLED", Bundle().apply {
                    putString("song_title", song.title)
                    putBoolean("is_favorite", favorite)
                })
                
                Log.i(TAG, "Favorite status updated: ${song.title} = $favorite")
            } else {
                Log.e(TAG, "Failed to update favorite status: ${result.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating favorite status", e)
        }
    }
}
```

#### 1.3 Required String Resources
```xml
<!-- res/values/strings.xml -->
<string name="add_favorite">Add to Favorites</string>
<string name="remove_favorite">Remove from Favorites</string>
```

---

## 🎤 Phase 2: Enhanced Voice Command Parsing

### Technical Implementation

#### 2.1 Voice Command Parser Architecture
```kotlin
// New file: service/VoiceCommandParser.kt
class VoiceCommandParser {
    
    companion object {
        private val TAG = "VoiceCommandParser"
        
        // Regex patterns for different command types
        private val ARTIST_PATTERNS = arrayOf(
            "play\\s+(.+?)\\s+by\\s+(.+)",           // "play song by artist"  
            "play\\s+artist\\s+(.+)",                // "play artist name"
            "play\\s+(.+?)\\s+artist",               // "play name artist"
            "play\\s+some\\s+(.+)"                   // "play some artist"
        )
        
        private val ALBUM_PATTERNS = arrayOf(
            "play\\s+album\\s+(.+)",                 // "play album name"
            "play\\s+the\\s+album\\s+(.+)",          // "play the album name"  
            "play\\s+(.+?)\\s+album"                 // "play name album"
        )
        
        private val CONTROL_COMMANDS = mapOf(
            "next song" to ControlAction.SKIP_NEXT,
            "previous song" to ControlAction.SKIP_PREVIOUS,
            "skip this" to ControlAction.SKIP_NEXT,
            "go back" to ControlAction.SKIP_PREVIOUS,
            "shuffle on" to ControlAction.SHUFFLE_ENABLE,
            "shuffle off" to ControlAction.SHUFFLE_DISABLE,
            "repeat on" to ControlAction.REPEAT_ENABLE,
            "repeat off" to ControlAction.REPEAT_DISABLE
        )
        
        private val CONTEXTUAL_COMMANDS = mapOf(
            "play my favorites" to ContextualAction.FAVORITES,
            "play favorite songs" to ContextualAction.FAVORITES,
            "play recent music" to ContextualAction.RECENT,
            "play recently played" to ContextualAction.RECENT,
            "shuffle everything" to ContextualAction.SHUFFLE_ALL,
            "play random music" to ContextualAction.SHUFFLE_ALL
        )
    }
    
    fun parseVoiceCommand(query: String): VoiceCommand {
        val cleanQuery = query.lowercase().trim()
        
        // Check control commands first (highest priority)
        CONTROL_COMMANDS.entries.forEach { (phrase, action) ->
            if (cleanQuery.contains(phrase)) {
                return VoiceCommand.Control(action)
            }
        }
        
        // Check contextual commands  
        CONTEXTUAL_COMMANDS.entries.forEach { (phrase, action) ->
            if (cleanQuery.contains(phrase)) {
                return VoiceCommand.Contextual(action)
            }
        }
        
        // Check artist patterns
        ARTIST_PATTERNS.forEach { pattern ->
            val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(cleanQuery)
            if (match != null && match.groupValues.size > 1) {
                val artistName = match.groupValues[1].trim()
                if (artistName.isNotBlank()) {
                    return VoiceCommand.Artist(artistName)
                }
            }
        }
        
        // Check album patterns
        ALBUM_PATTERNS.forEach { pattern ->
            val regex = pattern.toRegex(RegexOption.IGNORE_CASE)  
            val match = regex.find(cleanQuery)
            if (match != null && match.groupValues.size > 1) {
                val albumName = match.groupValues[1].trim()
                if (albumName.isNotBlank()) {
                    return VoiceCommand.Album(albumName)
                }
            }
        }
        
        // Default to song search
        return VoiceCommand.Song(cleanQuery)
    }
}

// Command data classes
sealed class VoiceCommand {
    data class Song(val query: String) : VoiceCommand()
    data class Artist(val name: String) : VoiceCommand()
    data class Album(val name: String) : VoiceCommand()
    data class Control(val action: ControlAction) : VoiceCommand()
    data class Contextual(val action: ContextualAction) : VoiceCommand()
}

enum class ControlAction {
    SKIP_NEXT, SKIP_PREVIOUS, SHUFFLE_ENABLE, SHUFFLE_DISABLE, 
    REPEAT_ENABLE, REPEAT_DISABLE
}

enum class ContextualAction {
    FAVORITES, RECENT, SHUFFLE_ALL
}
```

#### 2.2 Enhanced onPlayFromSearch Implementation
```kotlin
// Location: MusicService.kt - MediaSession callback
override fun onPlayFromSearch(query: String?, extras: Bundle?) {
    Log.i(TAG, "Voice command received: '$query'")
    Log.d(TAG, "Extras: $extras")
    
    if (query.isNullOrBlank()) {
        handleEmptyVoiceQuery()
        return
    }
    
    val command = VoiceCommandParser().parseVoiceCommand(query)
    Log.d(TAG, "Parsed command: $command")
    
    when (command) {
        is VoiceCommand.Control -> handleControlCommand(command.action)
        is VoiceCommand.Contextual -> handleContextualCommand(command.action)
        is VoiceCommand.Artist -> handleArtistQuery(command.name)
        is VoiceCommand.Album -> handleAlbumQuery(command.name)
        is VoiceCommand.Song -> handleSongQuery(command.query)
    }
}
```

#### 2.3 Command Handler Implementations
```kotlin
private fun handleControlCommand(action: ControlAction) {
    when (action) {
        ControlAction.SKIP_NEXT -> {
            queueManager.getNextSong()?.let { song ->
                playSongInternal(song)
            }
        }
        ControlAction.SKIP_PREVIOUS -> {
            queueManager.getPreviousSong()?.let { song ->
                playSongInternal(song)
            }
        }
        ControlAction.SHUFFLE_ENABLE -> {
            queueManager.setShuffle(true)
            updateMediaSessionPlaybackState()
        }
        ControlAction.SHUFFLE_DISABLE -> {
            queueManager.setShuffle(false)
            updateMediaSessionPlaybackState()
        }
        ControlAction.REPEAT_ENABLE -> {
            queueManager.setRepeatMode(QueueManager.RepeatMode.ALL)
            updateMediaSessionPlaybackState()
        }
        ControlAction.REPEAT_DISABLE -> {
            queueManager.setRepeatMode(QueueManager.RepeatMode.NONE)
            updateMediaSessionPlaybackState()
        }
    }
}

private fun handleContextualCommand(action: ContextualAction) {
    serviceScope.launch {
        try {
            when (action) {
                ContextualAction.FAVORITES -> {
                    val response = NetworkModule.getMusicApi().searchSongs(
                        "starred:true", 1, 50
                    )
                    playSearchResults(response.data, "Playing your favorites")
                }
                ContextualAction.RECENT -> {
                    val response = NetworkModule.getMusicApi().searchSongs(
                        "recent:true", 1, 50  
                    )
                    playSearchResults(response.data, "Playing recent music")
                }
                ContextualAction.SHUFFLE_ALL -> {
                    val response = NetworkModule.getMusicApi().searchSongs(
                        "random:true", 1, 100
                    )
                    queueManager.setShuffle(true)
                    playSearchResults(response.data, "Shuffling all music")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling contextual command", e)
            fallbackToSongSearch(action.toString())
        }
    }
}
```

---

## 🎵 Phase 3: Queue Management Callbacks

### Technical Implementation

#### 3.1 MediaSession Queue Callbacks
```kotlin
// Location: MusicService.kt - MediaSession callback
override fun onAddQueueItem(description: MediaDescriptionCompat?) {
    Log.d(TAG, "Add to queue: ${description?.title}")
    
    description?.mediaId?.let { mediaId ->
        val song = findSongByMediaId(mediaId)
        song?.let { 
            queueManager.addToQueue(it)
            updateMediaSessionQueue()
            
            sendCustomEvent("QUEUE_ITEM_ADDED", Bundle().apply {
                putString("song_title", it.title)
                putInt("queue_size", queueManager.getQueueSize())
            })
        }
    }
}

override fun onAddQueueItem(description: MediaDescriptionCompat?, index: Int) {
    Log.d(TAG, "Add to queue at index $index: ${description?.title}")
    
    description?.mediaId?.let { mediaId ->
        val song = findSongByMediaId(mediaId)
        song?.let {
            queueManager.addToQueue(it, index) 
            updateMediaSessionQueue()
        }
    }
}

override fun onRemoveQueueItem(description: MediaDescriptionCompat?) {
    Log.d(TAG, "Remove from queue: ${description?.title}")
    
    description?.mediaId?.let { mediaId ->
        queueManager.removeFromQueue(mediaId)
        updateMediaSessionQueue()
        
        sendCustomEvent("QUEUE_ITEM_REMOVED", Bundle().apply {
            putString("media_id", mediaId)
            putInt("queue_size", queueManager.getQueueSize())
        })
    }
}

override fun onSkipToQueueItem(id: Long) {
    Log.d(TAG, "Skip to queue item: $id")
    
    val index = id.toInt()
    if (queueManager.skipToIndex(index)) {
        queueManager.getCurrentSong()?.let { song ->
            playSongInternal(song)
        }
    }
}
```

#### 3.2 Enhanced QueueManager Functions
```kotlin
// Location: QueueManager.kt - Add these functions

fun addToQueue(song: Song, index: Int = -1): Boolean {
    val currentQueue = _currentQueue.value.toMutableList()
    
    return try {
        if (index >= 0 && index <= currentQueue.size) {
            currentQueue.add(index, song)
        } else {
            currentQueue.add(song)
        }
        
        _currentQueue.value = currentQueue
        
        // Update shuffle order if needed
        if (_isShuffleEnabled.value) {
            createShuffleOrder(_currentIndex.value)
        }
        
        Log.d(TAG, "Added '${song.title}' to queue at position ${if (index >= 0) index else currentQueue.size - 1}")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error adding song to queue", e)
        false
    }
}

fun removeFromQueue(mediaId: String): Boolean {
    val currentQueue = _currentQueue.value.toMutableList()
    val initialSize = currentQueue.size
    
    return try {
        val removedSongs = currentQueue.removeAll { it.id.toString() == mediaId }
        
        if (removedSongs) {
            _currentQueue.value = currentQueue
            
            // Adjust current index if needed
            val newIndex = when {
                currentQueue.isEmpty() -> -1
                _currentIndex.value >= currentQueue.size -> currentQueue.size - 1
                else -> _currentIndex.value
            }
            
            if (newIndex != _currentIndex.value) {
                _currentIndex.value = newIndex
                _currentSong.value = if (newIndex >= 0) currentQueue[newIndex] else null
            }
            
            // Update shuffle order if needed  
            if (_isShuffleEnabled.value && currentQueue.isNotEmpty()) {
                createShuffleOrder(_currentIndex.value)
            }
            
            Log.d(TAG, "Removed song(s) from queue. Size: $initialSize -> ${currentQueue.size}")
        }
        
        removedSongs
    } catch (e: Exception) {
        Log.e(TAG, "Error removing song from queue", e) 
        false
    }
}

fun skipToIndex(index: Int): Boolean {
    return try {
        val queue = _currentQueue.value
        if (index >= 0 && index < queue.size) {
            _currentIndex.value = index
            _currentSong.value = queue[index]
            
            // Update shuffle position if needed
            if (_isShuffleEnabled.value && shuffledIndices.isNotEmpty()) {
                currentShuffleIndex = shuffledIndices.indexOf(index)
            }
            
            addToHistory(queue[index])
            Log.d(TAG, "Skipped to queue index $index: ${queue[index].title}")
            true
        } else {
            Log.w(TAG, "Invalid queue index: $index (queue size: ${queue.size})")
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error skipping to queue index", e)
        false
    }
}
```

#### 3.3 MediaSession Queue Synchronization
```kotlin
// Location: MusicService.kt
private fun updateMediaSessionQueue() {
    try {
        val queueItems = queueManager.currentQueue.value.mapIndexed { index, song ->
            MediaSessionCompat.QueueItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(song.id.toString())
                    .setTitle(song.title)
                    .setSubtitle(song.artist.name)
                    .setDescription(song.album.name)
                    .setIconUri(Uri.parse(song.thumbnailUrl))
                    .setExtras(Bundle().apply {
                        putString("artist", song.artist.name)
                        putString("album", song.album.name)
                        putLong("duration", song.duration)
                        putBoolean("favorite", song.userStarred)
                    })
                    .build(),
                index.toLong()
            )
        }
        
        mediaSession?.setQueue(queueItems)
        Log.d(TAG, "Updated MediaSession queue: ${queueItems.size} items")
    } catch (e: Exception) {
        Log.e(TAG, "Error updating MediaSession queue", e)
    }
}

private fun findSongByMediaId(mediaId: String): Song? {
    return try {
        // First check current queue
        queueManager.currentQueue.value.find { it.id.toString() == mediaId }
            ?: run {
                // Check browsing playlist cache  
                currentBrowsingPlaylistSongs.find { it.id.toString() == mediaId }
            } ?: run {
                // Check search results cache
                searchResultsCache.find { it.id.toString() == mediaId }
            }
    } catch (e: Exception) {
        Log.e(TAG, "Error finding song by media ID: $mediaId", e)
        null
    }
}
```

---

## 🧪 Testing Strategy

### Unit Testing
```kotlin
// VoiceCommandParserTest.kt
class VoiceCommandParserTest {
    
    private lateinit var parser: VoiceCommandParser
    
    @Before
    fun setup() {
        parser = VoiceCommandParser()
    }
    
    @Test
    fun `parseVoiceCommand should handle artist queries`() {
        val command = parser.parseVoiceCommand("play Beatles")
        assertThat(command).isInstanceOf(VoiceCommand.Artist::class.java)
        assertThat((command as VoiceCommand.Artist).name).isEqualTo("Beatles")
    }
    
    @Test  
    fun `parseVoiceCommand should handle control commands`() {
        val command = parser.parseVoiceCommand("next song")
        assertThat(command).isInstanceOf(VoiceCommand.Control::class.java)
        assertThat((command as VoiceCommand.Control).action).isEqualTo(ControlAction.SKIP_NEXT)
    }
}

// QueueManagerTest.kt  
class QueueManagerTest {
    
    @Test
    fun `addToQueue should add song at specified index`() {
        val queueManager = QueueManager()
        val testSongs = createTestSongs(3)
        val newSong = createTestSong("New Song")
        
        queueManager.setQueue(testSongs)
        queueManager.addToQueue(newSong, 1)
        
        val queue = queueManager.currentQueue.value
        assertThat(queue.size).isEqualTo(4)
        assertThat(queue[1]).isEqualTo(newSong)
    }
}
```

### Integration Testing
```kotlin
// MediaSessionIntegrationTest.kt
@RunWith(AndroidJUnit4::class)
class MediaSessionIntegrationTest {
    
    @Test
    fun `custom favorite action should toggle song favorite status`() {
        // Setup service with test song
        val service = createMusicService()
        val testSong = createTestSong("Test Song", favorite = false)
        
        // Trigger favorite action
        service.onCustomAction(MusicService.ACTION_ADD_FAVORITE, Bundle())
        
        // Verify API call and state update
        verify(mockApi).favoriteSong(testSong.id.toString(), true)
        assertThat(testSong.userStarred).isTrue()
    }
}
```

---

## 📚 API Requirements

### Enhanced Search Endpoints
The implementation requires these API enhancements:

```kotlin
// MusicApi.kt additions
@GET("search/songs")
suspend fun searchSongs(
    @Query("q") query: String,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 20,
    @Query("filter") filter: String? = null  // starred:true, recent:true, random:true
): PaginatedResponse<Song>

@GET("artists/{artistId}/songs")  
suspend fun getArtistSongs(
    @Path("artistId") artistId: String,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 20
): PaginatedResponse<Song>
```

### Required Filter Support
- `starred:true` - User's favorited songs
- `recent:true` - Recently played songs  
- `random:true` - Random song selection
- `artist:[name]` - Songs by specific artist
- `album:[name]` - Songs from specific album

---

## 🔧 Configuration Files

### Updated searchable.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<searchable xmlns:android="http://schemas.android.com/apk/res/android"
    android:label="@string/app_name"
    android:hint="@string/search_hint"
    android:voiceSearchMode="showVoiceSearchButton|launchRecognizer"
    android:voiceLanguageModel="free_form"
    android:voicePromptText="@string/voice_prompt"
    android:includeInGlobalSearch="true">
    
    <voice-search-examples>
        <example text="Play my favorites" />
        <example text="Play Beatles" />
        <example text="Play Abbey Road album" />
        <example text="Next song" />
        <example text="Shuffle on" />
        <example text="Play recent music" />
    </voice-search-examples>
</searchable>
```

### Required String Resources
```xml  
<!-- res/values/strings.xml additions -->
<string name="search_hint">Search for songs, artists, or albums</string>
<string name="voice_prompt">Say play, next, shuffle, or artist name</string>
<string name="add_favorite">Add to Favorites</string>
<string name="remove_favorite">Remove from Favorites</string>
<string name="queue_item_added">Added to queue</string>
<string name="queue_item_removed">Removed from queue</string>
```

---

**Document Version**: 1.0  
**Last Updated**: 2025-08-10
**Target Android API**: 33+
**Target Android Auto**: 6.0+