# Implementation Guide - Critical Fixes

This document provides detailed implementation steps for the critical issues identified in the architecture review.

## Priority 1: Fix ViewModel Memory Leaks

### Problem
ViewModels store Activity Context, causing memory leaks on configuration changes.

### Files to Modify
1. `PlaylistViewModel.kt`
2. `HomeViewModel.kt`

### Changes Required

#### Step 1: Remove Context field
```kotlin
// BEFORE
private var context: Context? = null

// AFTER  
// Remove this field entirely
```

#### Step 2: Update setContext()
```kotlin
// BEFORE
fun setContext(context: Context) {
    this.context = context
    Intent(context, MusicService::class.java).also { intent ->
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
}

// AFTER
fun setContext(context: Context) {
    val appContext = context.applicationContext
    Intent(appContext, MusicService::class.java).also { intent ->
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
}
```

#### Step 3: Replace all `context?.let`
Replace all occurrences with `getApplication<Application>().let`:

```kotlin
// BEFORE
context?.let { ctx ->
    val intent = Intent(ctx, MusicService::class.java)
    ctx.startService(intent)
}

// AFTER
getApplication<Application>().let { ctx ->
    val intent = Intent(ctx, MusicService::class.java)
    ctx.startService(intent)
}
```

**Affected methods in PlaylistViewModel:**
- Line 313, 324, 349, 407, 415, 422, 465, 493, 532
- `refreshSongs()`, `favoriteSong()`, `playSong()`, `togglePlayPause()`, `logout()`

**Affected methods in HomeViewModel:**
- Line 339, 469, 658, 669, 779
- `setContext()`, `clearSearchAndStopPlayback()`, `seekTo()`, `logout()`, `clearQueue()`

#### Step 4: Update onCleared()
```kotlin
// BEFORE
override fun onCleared() {
    super.onCleared()
    stopProgressUpdates()
    if (bound) {
        context?.unbindService(connection)
        bound = false
    }
}

// AFTER
override fun onCleared() {
    super.onCleared()
    stopProgressUpdates()
    if (bound) {
        getApplication<Application>().unbindService(connection)
        bound = false
    }
}
```

---

## Priority 2: Add Room Database

### Add Dependencies

Update `app/build.gradle.kts`:

```kotlin
dependencies {
    val roomVersion = "2.6.1"
    
    // Room
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Paging
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1")
    implementation("androidx.room:room-paging:$roomVersion")
}
```

Also add KSP plugin to `plugins` block:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
    id("jacoco")
}
```

### Create Database Entities

Create `data/local/entity/` package and add entities:

```kotlin
// PlaylistEntity.kt
package com.melodee.autoplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val description: String,
    val imageUrl: String,
    val thumbnailUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val songCount: Int,
    val isPublic: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncedAt: Long = System.currentTimeMillis()
)

// SongEntity.kt
@Entity(
    tableName = "songs",
    indices = [Index(value = ["albumId"]), Index(value = ["artistId"])]
)
data class SongEntity(
    @PrimaryKey val id: UUID,
    val streamUrl: String,
    val title: String,
    val artistId: UUID,
    val albumId: UUID,
    val thumbnailUrl: String,
    val imageUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val userStarred: Boolean,
    val userRating: Int,
    val songNumber: Int,
    val bitrate: Int,
    val playCount: Int,
    val genre: String?,
    val syncedAt: Long = System.currentTimeMillis()
)

// ArtistEntity.kt  
@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val userStarred: Boolean,
    val userRating: Int,
    val albumCount: Int,
    val songCount: Int,
    val syncedAt: Long = System.currentTimeMillis()
)

// AlbumEntity.kt
@Entity(
    tableName = "albums",
    indices = [Index(value = ["artistId"])]
)
data class AlbumEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val releaseYear: Int,
    val userStarred: Boolean,
    val userRating: Int,
    val artistId: UUID?,
    val songCount: Int,
    val durationMs: Double,
    val durationFormatted: String,
    val genre: String?,
    val syncedAt: Long = System.currentTimeMillis()
)
```

### Create DAOs

```kotlin
// PlaylistDao.kt
package com.melodee.autoplayer.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.melodee.autoplayer.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getPlaylistsPaged(): PagingSource<Int, PlaylistEntity>
    
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: UUID): PlaylistEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    
    @Query("DELETE FROM playlists")
    suspend fun deleteAll()
    
    @Query("DELETE FROM playlists WHERE syncedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}

// Similar DAOs for Song, Artist, Album
```

### Create Database

```kotlin
// AppDatabase.kt
package com.melodee.autoplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.melodee.autoplayer.data.local.dao.*
import com.melodee.autoplayer.data.local.entity.*

@Database(
    entities = [
        PlaylistEntity::class,
        SongEntity::class,
        ArtistEntity::class,
        AlbumEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
}

// Converters.kt
class Converters {
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()
    
    @TypeConverter
    fun toUUID(value: String?): UUID? = value?.let { UUID.fromString(it) }
}
```

### Create Database Module

```kotlin
// DatabaseModule.kt
package com.melodee.autoplayer.data.local

import android.content.Context
import androidx.room.Room

object DatabaseModule {
    @Volatile
    private var database: AppDatabase? = null
    
    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "melodee_database"
            )
            .fallbackToDestructiveMigration()
            .build()
            .also { database = it }
        }
    }
}
```

### Update MelodeeApplication

```kotlin
// MelodeeApplication.kt
class MelodeeApplication : Application() {
    val database: AppDatabase by lazy {
        DatabaseModule.getDatabase(this)
    }
    
    // ... rest of the code
}
```

---

## Priority 3: Implement Offline-First Repository

### Create Mappers

```kotlin
// Mappers.kt
package com.melodee.autoplayer.data.mapper

import com.melodee.autoplayer.data.local.entity.*
import com.melodee.autoplayer.domain.model.*

fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    durationFormatted = durationFormatted,
    songCount = songCount,
    isPublic = isPublic,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    durationFormatted = durationFormatted,
    songCount = songCount,
    isPublic = isPublic,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// Similar for Song, Artist, Album
```

### Update MusicRepository

```kotlin
class MusicRepository(
    private val baseUrl: String,
    private val context: Context,
    private val database: AppDatabase
) {
    private val api: MusicApi get() = NetworkModule.getMusicApi()
    private val playlistDao = database.playlistDao()
    
    // Offline-first: try database first, then network
    fun getPlaylists(page: Int): Flow<PaginatedResponse<Playlist>> = flow {
        // Try database first for immediate response
        val cached = playlistDao.getPlaylists().first()
        if (cached.isNotEmpty()) {
            emit(PaginatedResponse(
                meta = PaginationMetadata(
                    totalCount = cached.size,
                    pageSize = cached.size,
                    currentPage = 1,
                    totalPages = 1,
                    hasPrevious = false,
                    hasNext = false
                ),
                data = cached.map { it.toDomain() }
            ))
        }
        
        // Then fetch from network and update database
        try {
            val response = ErrorHandler.handleOperation(context, "getPlaylists", "MusicRepository") {
                api.getPlaylists(page)
            }
            
            // Save to database
            playlistDao.insertPlaylists(response.data.map { it.toEntity() })
            
            // Emit network response
            emit(PaginatedResponse(meta = response.meta, data = response.data))
        } catch (e: Exception) {
            // If network fails and we have cache, we already emitted it
            if (cached.isEmpty()) {
                throw e
            }
        }
    }
}
```

---

## Priority 4: Apply RequestDeduplicator Globally

Update `MusicRepository.kt` to wrap ALL methods:

```kotlin
suspend fun getPlaylists(page: Int): Flow<PaginatedResponse<Playlist>> {
    val key = deduplicator.generateKey("getPlaylists", page)
    return deduplicator.deduplicate(key) {
        flow {
            // ... implementation
        }
    }
}

fun getPlaylistSongs(playlistId: String, page: Int): Flow<PaginatedResponse<Song>> {
    val key = deduplicator.generateKey("getPlaylistSongs", playlistId, page)
    return deduplicator.deduplicate(key) {
        flow {
            // ... implementation
        }
    }
}

// Apply to ALL 10+ methods in MusicRepository
```

---

## Priority 5: Implement Paging 3

### Update ViewModel to use Paging

```kotlin
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    val playlists: Flow<PagingData<Playlist>> = repository?.getPlaylistsPaging()
        ?.cachedIn(viewModelScope)
        ?: flow { emit(PagingData.empty()) }
}
```

### Update Repository

```kotlin
fun getPlaylistsPaging(): Flow<PagingData<Playlist>> {
    return Pager(
        config = PagingConfig(
            pageSize = 30,
            enablePlaceholders = false,
            prefetchDistance = 10
        ),
        pagingSourceFactory = { playlistDao.getPlaylistsPaged() }
    ).flow.map { pagingData ->
        pagingData.map { it.toDomain() }
    }
}
```

### Update UI

```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val playlists = viewModel.playlists.collectAsLazyPagingItems()
    
    LazyColumn {
        items(playlists.itemCount) { index ->
            playlists[index]?.let { playlist ->
                PlaylistItem(playlist)
            }
        }
        
        item {
            if (playlists.loadState.append is LoadState.Loading) {
                CircularProgressIndicator()
            }
        }
    }
}
```

---

## Testing the Changes

### Unit Tests

```kotlin
class PlaylistViewModelTest {
    @Test
    fun `setContext does not leak Activity context`() {
        val viewModel = PlaylistViewModel(application)
        val activity = mockk<Activity>(relaxed = true)
        
        viewModel.setContext(activity)
        
        // Verify Application context is used, not Activity
        verify(exactly = 0) { activity.bindService(any(), any(), any()) }
    }
}
```

### Integration Tests

```kotlin
@Test
fun `offline mode returns cached playlists`() = runTest {
    // Insert cached playlists
    database.playlistDao().insertPlaylists(testPlaylists)
    
    // Disconnect network
    NetworkModule.setBaseUrl("http://invalid.local")
    
    // Should still get cached playlists
    repository.getPlaylists(1).test {
        val result = awaitItem()
        assertThat(result.data).isNotEmpty()
        cancelAndIgnoreRemainingEvents()
    }
}
```

---

## Rollout Plan

### Phase 1 (Week 1)
- [ ] Fix ViewModel memory leaks
- [ ] Add Room dependencies
- [ ] Create database entities and DAOs
- [ ] Test database operations

### Phase 2 (Week 2)
- [ ] Implement offline-first repository
- [ ] Apply RequestDeduplicator globally
- [ ] Add mappers between entities and domain models
- [ ] Test offline functionality

### Phase 3 (Week 3)
- [ ] Implement Paging 3 for all lists
- [ ] Update ViewModels to use Paging
- [ ] Update UI components
- [ ] Performance testing

### Phase 4 (Week 4)
- [ ] Increase media cache size
- [ ] Add cache configuration settings
- [ ] Implement encrypted SharedPreferences
- [ ] Final integration testing

---

## Metrics to Track

Before and after implementation:

| Metric | Before | Target | Actual |
|--------|--------|--------|--------|
| Memory usage (avg) | 180MB | 120MB | |
| Cold start time | 2.8s | 2.0s | |
| Network requests (session) | 150+ | <75 | |
| Offline functionality | 0% | 80% | |
| Crash rate | 0.5% | <0.1% | |

---

## Additional Resources

- [Room Database Guide](https://developer.android.com/training/data-storage/room)
- [Paging 3 Library](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)
- [Offline-First Architecture](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [ViewModel Best Practices](https://developer.android.com/topic/libraries/architecture/viewmodel)
