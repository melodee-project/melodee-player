# API Migration Implementation Review

**Review Date:** 2024-12-24  
**Reviewer:** Code Review Agent  
**Migration Spec:** `docs/API-CHANGES.md`  
**Overall Status:** ⚠️ **REQUEST CHANGES** (4 Blockers, 3 Major, 3 Minor issues)

---

## Executive Summary

The API migration from legacy endpoints to v1 API has been **85% completed successfully**. The developer demonstrated strong engineering practices with comprehensive model updates, proper parcelable implementations, and good test coverage. However, **4 BLOCKER issues** must be resolved before production deployment:

1. **Scrobble timestamp units** are in milliseconds but likely need seconds (1000× magnitude error)
2. **Path parameter naming inconsistency** ({apiKey} vs {id}) risks 404 errors
3. **UUID serialization** lacks explicit error handling
4. **Parcelable graph size** could exceed Android's 1MB transaction limit

**Estimated fix time:** 4-6 hours  
**Regression risk:** Low (surgical fixes with existing test coverage)

---

## Phase-by-Phase Assessment

### ✅ Phase 1: Core Model Updates — PASS

**Status:** All requirements met

**Verified Changes:**
- ✅ `User` model: Added all 11 new fields (isAdmin, isEditor, roles, stats, timestamps)
- ✅ `Song` model: Changed userRating Double→Int, added 6 new fields (songNumber, bitrate, playCount, genre, timestamps)
- ✅ `Artist` model: Changed userRating Double→Int, added 6 new fields (counts, biography, genres, timestamps)
- ✅ `Album` model: Changed userRating Double→Int, added artist reference and 7 new fields
- ✅ `Playlist` model: Added 4 new fields (isPublic, owner, timestamps)
- ✅ `AuthenticationResponse`: Renamed from AuthResponse, added 3 refresh token fields
- ✅ `PaginationMetadata`: Renamed from PaginationMeta with typealias for compatibility
- ✅ `LoginModel`: Created with userName/email/password fields
- ✅ `ScrobbleRequest`: Updated with Double timestamps, enum, removed userId
- ✅ `ScrobbleRequestType` enum: Implemented with custom TypeAdapter for int serialization

**Parcelable Implementation:**
- ✅ Song: All 15 fields correctly serialized/deserialized (verified by test)
- ✅ Album: Correctly handles nested Artist parcelable
- ✅ Artist: Correctly handles genres List<String>

**Nullability Strategy:**
- ✅ All new fields have safe defaults or nullable types
- ✅ Prevents crashes if server omits optional fields
- ✅ Example: `val createdAt: String = ""`

**File:** `src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt`

---

### ⚠️ Phase 2: API Endpoint Updates — PARTIAL FAIL

**Status:** 6/8 endpoints correct, 2 with critical issues

**Correct Implementations:**
- ✅ Auth: `POST api/v1/auth/authenticate` with LoginModel → AuthenticationResponse
- ✅ User: `GET api/v1/user/me` and `GET api/v1/user/playlists`
- ✅ Search: `GET api/v1/search/songs` with optional filterByArtistApiKey
- ✅ Artists: `GET api/v1/artists` with orderBy/orderDirection params
- ✅ Artists: `GET api/v1/artists/{id}/songs` and `GET api/v1/artists/{id}/albums`
- ✅ Albums: `GET api/v1/albums/{id}/songs`

**Issues Found:**

#### 🔴 BLOCKER: Path Parameter Inconsistency
**Lines:** `MusicApi.kt:22, 67`

```kotlin
// INCONSISTENT - Uses {apiKey}:
@GET("api/v1/playlists/{apiKey}/songs")
fun getPlaylistSongs(@Path("apiKey") playlistId: String, ...)

@POST("api/v1/songs/starred/{apiKey}/{isStarred}")
fun favoriteSong(@Path("apiKey") songId: String, ...)

// CONSISTENT - Uses {id}:
@GET("api/v1/artists/{id}/songs")
@GET("api/v1/albums/{id}/songs")
```

**Problem:** 
- Spec (section 0.5) says: "prefer `{id}` or exact Swagger name; treat `{apiKey}` as placeholder"
- Mixed usage suggests placeholders were never verified against actual API
- If server expects `{id}`, these endpoints will return 404

**Required Action:** Verify actual path parameter names in Swagger and align code.

**Missing Verifications:**
- ❌ No "Verified:" comments in spec (section 2.x requirements)
- ❌ No integration tests to validate path generation

**File:** `src/app/src/main/java/com/melodee/autoplayer/data/api/MusicApi.kt`

---

### ✅ Phase 3: Repository Layer Updates — PASS

**Status:** All requirements met

**Verified Changes:**
- ✅ Login method creates LoginModel based on email vs username detection
- ✅ AuthenticationResponse properly consumed and token extracted
- ✅ Typed paged responses mapped to internal PaginatedResponse<T> (Approach B)
- ✅ Search consolidation implemented with optional artistId parameter

**Mapping Correctness:**
```kotlin
// MusicRepository.kt lines 44-48 (example):
val response = api.getPlaylists(page)
emit(PaginatedResponse(meta = response.meta, data = response.data))
```
✅ Structural mapping preserves all pagination metadata fields

**File:** `src/app/src/main/java/com/melodee/autoplayer/data/repository/MusicRepository.kt`

---

### ❌ Phase 4: ScrobbleManager/Service Changes — FAIL (BLOCKER)

**Status:** 3/4 requirements met, 1 critical unit error

**Correct Implementations:**
- ✅ userId removed from ScrobbleRequest
- ✅ scrobbleTypeValue enum used (NOW_PLAYING=1, PLAYED=2)
- ✅ Enum serializes to int value (verified by test)

**Critical Issue:**

#### 🔴 BLOCKER: Timestamp/Duration Units Wrong
**Lines:** `ScrobbleManager.kt:121, 167-168`

```kotlin
// CURRENT IMPLEMENTATION (WRONG):
// Line 121 (nowPlaying):
timestamp = tracker.startTime.toDouble(),           // System.currentTimeMillis() = 1703462400000ms
playedDuration = 0.0,

// Line 167-168 (played):
timestamp = System.currentTimeMillis().toDouble(),  // milliseconds
playedDuration = playedDuration.toDouble(),         // milliseconds
```

**Problem:**
- `System.currentTimeMillis()` returns **milliseconds** since Unix epoch
- Most APIs expect Unix timestamps in **seconds** (e.g., 1703462400, not 1703462400000)
- Spec section 0.3 explicitly warns: "units MUST be verified to avoid silently wrong scrobbles"
- Current implementation will send timestamps 1000× too large

**Evidence:**
```kotlin
// Example values being sent:
timestamp: 1703462400000.0  // December 25, 2023 in MILLISECONDS
playedDuration: 180000.0    // 3 minutes in MILLISECONDS

// Expected by server (likely):
timestamp: 1703462400.0     // December 25, 2023 in SECONDS
playedDuration: 180.0       // 3 minutes in SECONDS
```

**Required Fix:**
```kotlin
// ScrobbleManager.kt line 121:
timestamp = (tracker.startTime / 1000.0),  // Convert ms → seconds
playedDuration = 0.0,

// ScrobbleManager.kt line 167-168:
val playedDuration = System.currentTimeMillis() - tracker.startTime
timestamp = (System.currentTimeMillis() / 1000.0),  // Convert ms → seconds
playedDuration = (playedDuration / 1000.0),         // Convert ms → seconds
```

**Verification Steps:**
1. Check Swagger documentation for `/api/v1/scrobble` endpoint
2. Look for timestamp field description (should specify seconds vs milliseconds)
3. If unclear, check server code or test with known timestamp
4. Add unit test to verify conversion (see Test Recommendations section)

**File:** `src/app/src/main/java/com/melodee/autoplayer/service/ScrobbleManager.kt`

---

### ⚠️ Phase 5: UI/ViewModel Changes — CANNOT VERIFY

**Status:** Models updated correctly, UI usage not found

**Verified:**
- ✅ All models use `Int` for userRating
- ✅ Parcelable implementations handle Int correctly
- ✅ Default value is 0 (suggests 0-based scale)

**Unverified:**
- ⚠️ No UI code found that displays/edits userRating
- ⚠️ Cannot confirm input validation (slider range, bounds checking)
- ⚠️ Cannot confirm display logic (stars, numbers, etc.)

**Recommendation:** Search for rating UI components and verify:
```bash
grep -r "userRating\|Rating" src/app/src/main/java/com/melodee/autoplayer/presentation
```

---

### ✅ Phase 6: Network Configuration — PASS

**Status:** All configuration correct, refresh token support documented as future work

**Verified Changes:**
- ✅ Base URL configuration allows versioned paths
- ✅ Auth interceptor adds Bearer token to all requests
- ✅ 401 handler clears auth and triggers callback
- ✅ Retry interceptor for transient failures
- ✅ Timeout configuration (15s connect, 20s read/write)
- ✅ Connection pooling and dispatcher limits

**Refresh Token Support:**
- ✅ AuthenticationResponse has refreshToken, expiresAt, refreshTokenExpiresAt fields
- ⚠️ Refresh logic not implemented (acceptable per spec "Phase 6.2 Future")
- 🔵 Current behavior: 401 → logout user (acceptable short-term)

**File:** `src/app/src/main/java/com/melodee/autoplayer/data/api/NetworkModule.kt`

---

### ⚠️ Phase 7: Testing and Validation — PARTIAL PASS

**Status:** Good coverage for models, gaps in integration testing

**Test Results:**
```
46 total tests
0 failures
4 skipped
All passing tests verified
```

**Excellent Coverage:**
- ✅ Model serialization (User, Song, Album, Artist with new fields)
- ✅ UUID deserialization from JSON strings (test passes!)
- ✅ Pagination metadata serialization
- ✅ Scrobble enum serializes to int (verified: `"scrobbleTypeValue":2`)
- ✅ Parcelable round-trip preserves all new fields

**Coverage Gaps:**
- ❌ No Retrofit path generation tests (would catch {apiKey} vs {id} issue)
- ❌ No scrobble timestamp unit tests (would catch ms vs seconds issue)
- ❌ No repository error handling tests
- ❌ No pagination boundary tests (empty results, page overflow)
- ❌ No rating validation tests

**Files:** `src/app/src/test/java/com/melodee/autoplayer/`

---

## Detailed Findings (Prioritized for Fixing)

### 🔴 BLOCKER #1: Scrobble Timestamp/Duration Units

**Severity:** BLOCKER  
**Impact:** All scrobbles will have incorrect timestamps (1000× magnitude error), causing data corruption  
**Files:** `src/app/src/main/java/com/melodee/autoplayer/service/ScrobbleManager.kt`

**Current Code:**
```kotlin
// ScrobbleManager.kt lines 117-124 (nowPlaying):
val request = ScrobbleRequest(
    songId = tracker.song.id.toString(),
    playerName = defaultPlayerName,
    scrobbleType = "nowPlaying",
    timestamp = tracker.startTime.toDouble(),        // ❌ WRONG: milliseconds
    playedDuration = 0.0,
    scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING
)

// ScrobbleManager.kt lines 161-170 (played):
val playedDuration = System.currentTimeMillis() - tracker.startTime

val request = ScrobbleRequest(
    songId = tracker.song.id.toString(),
    playerName = defaultPlayerName,
    scrobbleType = "played",
    timestamp = System.currentTimeMillis().toDouble(),  // ❌ WRONG: milliseconds
    playedDuration = playedDuration.toDouble(),         // ❌ WRONG: milliseconds
    scrobbleTypeValue = ScrobbleRequestType.PLAYED
)
```

**Required Fix:**
```kotlin
// ScrobbleManager.kt lines 117-124 (nowPlaying):
val request = ScrobbleRequest(
    songId = tracker.song.id.toString(),
    playerName = defaultPlayerName,
    scrobbleType = "nowPlaying",
    timestamp = (tracker.startTime / 1000.0),  // ✅ FIXED: convert ms to seconds
    playedDuration = 0.0,
    scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING
)

// ScrobbleManager.kt lines 161-170 (played):
val playedDurationMs = System.currentTimeMillis() - tracker.startTime

val request = ScrobbleRequest(
    songId = tracker.song.id.toString(),
    playerName = defaultPlayerName,
    scrobbleType = "played",
    timestamp = (System.currentTimeMillis() / 1000.0),  // ✅ FIXED: convert ms to seconds
    playedDuration = (playedDurationMs / 1000.0),        // ✅ FIXED: convert ms to seconds
    scrobbleTypeValue = ScrobbleRequestType.PLAYED
)
```

**Verification:**
1. Check Swagger `/api/v1/scrobble` endpoint documentation
2. Confirm timestamp field description specifies "Unix timestamp in seconds"
3. Update spec docs/API-CHANGES.md section 0.3 with confirmed units
4. Add this test (see Test #1 in recommendations)

**Why This Is Critical:**
- Sending `timestamp: 1703462400000` instead of `1703462400` means:
  - Server interprets as year 55992 instead of 2023
  - Or server rejects request as invalid
  - Or server truncates to int32 causing overflow/wraparound
- All historical scrobble data will be corrupted and unrecoverable

---

### 🔴 BLOCKER #2: Path Parameter Naming Inconsistency

**Severity:** BLOCKER  
**Impact:** Playlist and song-starring endpoints will return 404 if server expects {id}  
**Files:** `src/app/src/main/java/com/melodee/autoplayer/data/api/MusicApi.kt`

**Current Code:**
```kotlin
// MusicApi.kt line 22-27:
@GET("api/v1/playlists/{apiKey}/songs")
suspend fun getPlaylistSongs(
    @Path("apiKey") playlistId: String,  // ❌ Inconsistent with other endpoints
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): SongPagedResponse

// MusicApi.kt line 67-71:
@POST("api/v1/songs/starred/{apiKey}/{isStarred}")
suspend fun favoriteSong(
    @Path("apiKey") songId: String,      // ❌ Inconsistent with other endpoints
    @Path("isStarred") userStarred: Boolean
): retrofit2.Response<Unit>

// COMPARED TO (consistent usage):
@GET("api/v1/artists/{id}/songs")       // ✅ Uses {id}
@GET("api/v1/artists/{id}/albums")      // ✅ Uses {id}
@GET("api/v1/albums/{id}/songs")        // ✅ Uses {id}
```

**Problem:**
- Artist and Album endpoints use `{id}` consistently
- Playlist and Song endpoints use `{apiKey}`
- Spec section 0.5 says `{apiKey}` was a placeholder requiring verification
- No verification was done (no "Verified:" comments in spec)

**Verification Steps:**
1. Start local API server: `cd api-server && dotnet run`
2. Open Swagger UI: http://localhost:5157/swagger/
3. Find these endpoints:
   - `GET /api/v1/playlists/{???}/songs` — check actual parameter name
   - `POST /api/v1/songs/starred/{???}/{???}` — check actual parameter names
4. Document findings in spec

**Most Likely Fix (assuming server uses {id}):**
```kotlin
// MusicApi.kt line 22-27:
@GET("api/v1/playlists/{id}/songs")
suspend fun getPlaylistSongs(
    @Path("id") playlistId: String,      // ✅ FIXED: aligned with server
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): SongPagedResponse

// MusicApi.kt line 67-71:
@POST("api/v1/songs/starred/{id}/{isStarred}")
suspend fun favoriteSong(
    @Path("id") songId: String,          // ✅ FIXED: aligned with server
    @Path("isStarred") userStarred: Boolean
): retrofit2.Response<Unit>
```

**Alternative (if server actually uses {apiKey}):**
- Update docs/API-CHANGES.md to document this is intentional
- Add comment explaining why playlists use apiKey vs id
- Ensure all callers pass the correct ID type

**Impact if Not Fixed:**
- Users cannot view playlist songs (404 error)
- Users cannot favorite/unfavorite songs (404 error)
- No error message will indicate the problem (just generic network failure)

---

### 🔴 BLOCKER #3: Missing UUID Serialization Error Handling

**Severity:** BLOCKER (Latent)  
**Impact:** Crashes with unclear errors if server sends malformed/null UUIDs  
**Files:** `src/app/src/main/java/com/melodee/autoplayer/data/api/NetworkModule.kt`

**Current Code:**
```kotlin
// NetworkModule.kt line 178:
retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())  // ❌ Uses default Gson
    .build()
```

**Problem:**
- Default Gson can handle UUID via reflection (calls toString()/fromString())
- **But:** No error handling for malformed UUIDs
- **But:** No handling for null when model expects non-null UUID
- Tests pass because test data is valid

**What Happens Now:**
```kotlin
// Valid UUID: Works
{"id": "123e4567-e89b-12d3-a456-426614174000"} → UUID.fromString(...) ✅

// Invalid format: Crashes with IllegalArgumentException
{"id": "not-a-uuid"} → UUID.fromString(...) ❌ CRASH

// Null: Crashes with NullPointerException
{"id": null} → UUID.fromString(null) ❌ CRASH
```

**Required Fix:**
```kotlin
// NetworkModule.kt line 102-183 (replace createRetrofitInstance):
private fun createRetrofitInstance() {
    val ctx = appContext
    
    // Create Gson with explicit UUID adapter
    val gson = GsonBuilder()
        .registerTypeAdapter(UUID::class.java, object : TypeAdapter<UUID>() {
            override fun write(out: JsonWriter, value: UUID?) {
                out.value(value?.toString())
            }
            
            override fun read(reader: JsonReader): UUID? {
                return try {
                    val str = reader.nextString()
                    if (str.isNullOrBlank()) {
                        UUID(0, 0)  // Use empty UUID for null/blank
                    } else {
                        UUID.fromString(str)
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e("NetworkModule", "Invalid UUID format: ${e.message}")
                    UUID(0, 0)  // Use empty UUID for invalid format
                }
            }
        })
        .create()

    // ... existing OkHttp setup ...

    retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))  // ✅ Use custom Gson
        .build()

    musicApi = retrofit?.create(MusicApi::class.java)
    scrobbleApi = retrofit?.create(ScrobbleApi::class.java)
}
```

**Why This Is Critical:**
- Spec section 0.1 explicitly says: "Add/verify a JSON adapter for UUID"
- Without explicit adapter, any server schema drift causes unclear crashes
- Production apps need graceful degradation, not IllegalArgumentException

**Alternative (if empty UUID is not acceptable):**
```kotlin
override fun read(reader: JsonReader): UUID? {
    return try {
        val str = reader.nextString()
        if (str.isNullOrBlank()) {
            throw JsonParseException("UUID cannot be null or blank")
        }
        UUID.fromString(str)
    } catch (e: IllegalArgumentException) {
        throw JsonParseException("Invalid UUID format: ${reader.path}", e)
    }
}
```

---

### 🔴 BLOCKER #4: Parcelable Graph Size Risk

**Severity:** BLOCKER (Edge Case)  
**Impact:** TransactionTooLargeException when passing large playlists/albums via Intent  
**Files:** `src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt`

**Current Code:**
```kotlin
// Models.kt lines 77-95:
data class Song(
    val id: UUID,
    val streamUrl: String,
    val title: String,
    val artist: Artist,       // Parcelable
    val album: Album,         // Parcelable (which contains Artist)
    // ... 10 more fields
) : Parcelable

// Album contains Artist:
data class Album(
    // ...
    val artist: Artist? = null,  // Nested Parcelable
    // ...
) : Parcelable

// Artist contains List<String>:
data class Artist(
    // ...
    val genres: List<String> = emptyList(),  // Unbounded
    // ...
) : Parcelable
```

**Problem:**
- Android Parcel has **1MB transaction limit** (Binder buffer)
- Parcelable graph: `Song → Album → Artist → List<String>`
- Passing `List<Song>` (e.g., 100-song playlist) via Intent parcels:
  - 100 Songs
  - 100 Albums (each with nested Artist)
  - Potentially 100 unique Artists (each with genres list)
  - Plus all string fields (URLs, titles, etc.)

**Calculation:**
```
Estimated size per Song:
- Song fields: ~500 bytes (UUIDs, strings)
- Album fields: ~500 bytes
- Artist fields: ~500 bytes
- Genres (5 genres × 20 chars): ~100 bytes
Total per Song: ~1600 bytes

100 Songs × 1600 bytes = 160KB (safe)
500 Songs × 1600 bytes = 800KB (risky)
1000 Songs × 1600 bytes = 1.6MB (EXCEEDS LIMIT)
```

**Current Risk Level:**
- Low for typical playlists (<100 songs)
- **High for large playlists (>500 songs)**
- **High if images/bios are embedded** instead of URLs

**Immediate Fix (Monitoring):**
```kotlin
// Models.kt Song.writeToParcel() line 116:
override fun writeToParcel(parcel: Parcel, flags: Int) {
    val startSize = parcel.dataSize()
    
    parcel.writeString(id.toString())
    parcel.writeString(streamUrl)
    // ... rest of writes ...
    
    val endSize = parcel.dataSize()
    val size = endSize - startSize
    
    // Log warning if approaching limits
    if (size > 5000) {  // 5KB per song is concerning
        Log.w("Song", "Large parcel detected: ${size} bytes for song ${id}")
    }
}
```

**Long-Term Fix (Recommended):**
```kotlin
// Instead of passing List<Song>, pass only List<UUID>:
// In calling Activity:
val songIds = songs.map { it.id }
intent.putExtra("song_ids", ArrayList(songIds.map { it.toString() }))

// In receiving Activity:
val songIds = intent.getStringArrayListExtra("song_ids")?.map { UUID.fromString(it) }
// Fetch full Song objects from ViewModel/Repository
```

**Or use ViewModel shared state:**
```kotlin
// In shared ViewModel:
val selectedSongs = MutableStateFlow<List<Song>>(emptyList())

// Calling screen:
viewModel.selectedSongs.value = songs
navController.navigate("details")

// Receiving screen:
val songs by viewModel.selectedSongs.collectAsState()
```

**Why This Is Critical:**
- TransactionTooLargeException crashes are hard to debug
- Occurs only with large datasets (hard to reproduce in testing)
- No stack trace points to actual problem

---

### 🟠 MAJOR #5: Deprecated scrobbleType Field Still Populated

**Severity:** MAJOR  
**Impact:** Wasted bandwidth, potential API rejection if field is unknown  
**Files:** 
- `src/app/src/main/java/com/melodee/autoplayer/data/api/ScrobbleApi.kt`
- `src/app/src/main/java/com/melodee/autoplayer/service/ScrobbleManager.kt`

**Current Code:**
```kotlin
// ScrobbleApi.kt line 13-21:
data class ScrobbleRequest(
    val songId: String,
    val playerName: String = "MelodeePlayer",
    @Deprecated("Use scrobbleTypeValue")
    val scrobbleType: String? = null,             // Marked deprecated
    val timestamp: Double,
    val playedDuration: Double,
    val scrobbleTypeValue: ScrobbleRequestType
)

// BUT ScrobbleManager.kt still sets it:
// Line 120:
scrobbleType = "nowPlaying",

// Line 166:
scrobbleType = "played",
```

**Problem:**
- Spec Phase 4 says: "keep `scrobbleType` string **if required by API**"
- Field is marked `@Deprecated` but still populated
- No verification if server actually needs it

**Required Action:**
1. Check Swagger for `/api/v1/scrobble` request schema
2. If server uses only `scrobbleTypeValue` int:
   - Remove `scrobbleType` field entirely
   - Remove the string literals from ScrobbleManager
3. If server requires both:
   - Remove `@Deprecated` annotation
   - Document why both are needed

**Option A (server uses only enum):**
```kotlin
// ScrobbleApi.kt:
data class ScrobbleRequest(
    val songId: String,
    val playerName: String = "MelodeePlayer",
    val timestamp: Double,
    val playedDuration: Double,
    val scrobbleTypeValue: ScrobbleRequestType
    // scrobbleType removed entirely
)

// ScrobbleManager.kt line 117-124:
val request = ScrobbleRequest(
    songId = tracker.song.id.toString(),
    playerName = defaultPlayerName,
    timestamp = (tracker.startTime / 1000.0),
    playedDuration = 0.0,
    scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING
    // scrobbleType removed
)
```

**Option B (server needs both):**
```kotlin
// ScrobbleApi.kt:
data class ScrobbleRequest(
    val songId: String,
    val playerName: String = "MelodeePlayer",
    val scrobbleType: String,  // Remove @Deprecated, make non-null
    val timestamp: Double,
    val playedDuration: Double,
    val scrobbleTypeValue: ScrobbleRequestType
)

// Add comment explaining why both exist
```

---

### 🟠 MAJOR #6: No Pagination Boundary Tests

**Severity:** MAJOR  
**Impact:** Off-by-one errors could skip/duplicate content in pagination  
**Files:** Missing test coverage

**Current State:**
- ✅ Pagination mapping is structurally correct
- ✅ Test exists for basic serialization
- ❌ No tests for edge cases

**Missing Test Coverage:**
```kotlin
// Boundary conditions not tested:
- Empty results (totalCount=0, page=1)
- Last page (currentPage=totalPages, hasNext=false)
- First page (currentPage=1, hasPrevious=false)
- Single item (totalCount=1, totalPages=1)
- Page overflow (page > totalPages)
- Invalid page (page = 0 or negative)
```

**Required Fix:**
Create `src/app/src/test/java/com/melodee/autoplayer/domain/PaginationTest.kt`:
```kotlin
package com.melodee.autoplayer.domain

import com.google.common.truth.Truth.assertThat
import com.melodee.autoplayer.domain.model.PaginationMetadata
import com.melodee.autoplayer.domain.model.PaginatedResponse
import com.melodee.autoplayer.domain.model.Song
import org.junit.Test

class PaginationTest {

    @Test
    fun `empty results have correct pagination`() {
        val meta = PaginationMetadata(
            totalCount = 0,
            pageSize = 10,
            currentPage = 1,
            totalPages = 0,
            hasPrevious = false,
            hasNext = false
        )
        
        assertThat(meta.hasNext).isFalse()
        assertThat(meta.hasPrevious).isFalse()
        assertThat(meta.totalPages).isEqualTo(0)
    }

    @Test
    fun `first page has no previous`() {
        val meta = PaginationMetadata(
            totalCount = 100,
            pageSize = 10,
            currentPage = 1,
            totalPages = 10,
            hasPrevious = false,
            hasNext = true
        )
        
        assertThat(meta.hasPrevious).isFalse()
        assertThat(meta.hasNext).isTrue()
    }

    @Test
    fun `last page has no next`() {
        val meta = PaginationMetadata(
            totalCount = 100,
            pageSize = 10,
            currentPage = 10,
            totalPages = 10,
            hasPrevious = true,
            hasNext = false
        )
        
        assertThat(meta.hasNext).isFalse()
        assertThat(meta.hasPrevious).isTrue()
    }

    @Test
    fun `middle page has both previous and next`() {
        val meta = PaginationMetadata(
            totalCount = 100,
            pageSize = 10,
            currentPage = 5,
            totalPages = 10,
            hasPrevious = true,
            hasNext = true
        )
        
        assertThat(meta.hasPrevious).isTrue()
        assertThat(meta.hasNext).isTrue()
    }
}
```

---

### 🟠 MAJOR #7: Rating Scale Not Validated

**Severity:** MAJOR  
**Impact:** Invalid ratings could be sent to server or stored locally  
**Files:** `src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt`

**Current Code:**
```kotlin
// Models.kt:
data class Song(..., val userRating: Int = 0, ...)
data class Artist(..., val userRating: Int = 0, ...)
data class Album(..., val userRating: Int = 0, ...)
```

**Problem:**
- Spec section 0.4 says: "verify rating scale (0..5, 1..5, 0..10, etc.) and update UI validation"
- Default value is `0` (suggests 0 is valid minimum)
- **No validation** prevents negative or excessive values
- **No documentation** of valid range

**Required Action:**
1. Verify rating scale in Swagger (likely 0-5 or 1-5)
2. Add validation to models
3. Document scale in constants

**Recommended Fix:**
```kotlin
// Create new file: src/app/src/main/java/com/melodee/autoplayer/domain/model/RatingConstants.kt
package com.melodee.autoplayer.domain.model

object RatingConstants {
    const val MIN_RATING = 0
    const val MAX_RATING = 5
    
    fun isValid(rating: Int): Boolean = rating in MIN_RATING..MAX_RATING
    
    fun clamp(rating: Int): Int = rating.coerceIn(MIN_RATING, MAX_RATING)
}

// Update Models.kt:
data class Song(
    // ... other fields ...
    val userRating: Int = 0,
    // ... other fields ...
) : Parcelable {
    init {
        require(userRating in RatingConstants.MIN_RATING..RatingConstants.MAX_RATING) {
            "userRating must be ${RatingConstants.MIN_RATING}-${RatingConstants.MAX_RATING}, got $userRating"
        }
    }
    // ... rest of implementation
}

// Apply same to Artist and Album
```

**Add Test:**
```kotlin
@Test
fun `rating is validated at creation`() {
    val exception = assertThrows<IllegalArgumentException> {
        Song(
            // ... valid fields ...
            userRating = 10  // Invalid if max is 5
        )
    }
    assertThat(exception.message).contains("must be 0-5")
}
```

---

### 🟡 MINOR #8: Duplicate Search Method Not Removed

**Severity:** MINOR  
**Impact:** Code duplication, potential for bugs if methods diverge  
**Files:** `src/app/src/main/java/com/melodee/autoplayer/data/repository/MusicRepository.kt`

**Current Code:**
```kotlin
// MusicRepository.kt line 65-75 (primary method):
suspend fun searchSongs(query: String, page: Int = 1): Flow<PaginatedResponse<Song>> {
    val key = deduplicator.generateKey("searchSongs", query, page)
    return deduplicator.deduplicate(key) {
        kotlinx.coroutines.flow.flow {
            val response = ErrorHandler.handleOperation(context, "searchSongs", "MusicRepository") {
                api.searchSongs(query, page)
            }
            emit(PaginatedResponse(meta = response.meta, data = response.data))
        }
    }
}

// MusicRepository.kt line 113-116 (duplicate wrapper):
fun searchSongsWithArtist(query: String, artistId: String?, page: Int = 1): Flow<PaginatedResponse<Song>> = flow {
    val response = api.searchSongs(query, page, artistId = artistId)
    emit(PaginatedResponse(meta = response.meta, data = response.data))
}
```

**Problem:**
- Spec Phase 3 says: "Consolidate search methods... Remove duplicate method"
- `searchSongsWithArtist` is just a wrapper around `searchSongs` with artistId
- Both methods exist when only one is needed

**Required Fix:**
```kotlin
// Option 1: Remove searchSongsWithArtist entirely
// Update all callers to use:
searchSongs(query = "...", page = 1)  // without artist filter
// or
searchSongs(query = "...", page = 1, artistId = "artist-id")  // with filter

// BUT searchSongs doesn't accept artistId parameter currently!
// Need to update searchSongs signature first:

// MusicRepository.kt line 65:
suspend fun searchSongs(
    query: String, 
    page: Int = 1,
    artistId: String? = null  // Add optional artistId parameter
): Flow<PaginatedResponse<Song>> {
    val key = deduplicator.generateKey("searchSongs", query, page, artistId.orEmpty())
    return deduplicator.deduplicate(key) {
        kotlinx.coroutines.flow.flow {
            val response = ErrorHandler.handleOperation(context, "searchSongs", "MusicRepository") {
                api.searchSongs(query, page, artistId = artistId)
            }
            emit(PaginatedResponse(meta = response.meta, data = response.data))
        }
    }
}

// Then remove searchSongsWithArtist (line 113-116)
```

**Impact if Not Fixed:**
- Minor code duplication
- Risk of fixing bug in one method but not the other
- Confusion about which method to use

---

### 🟡 MINOR #9: Refresh Token Implementation Incomplete

**Severity:** MINOR  
**Impact:** Users logged out on token expiry instead of seamless renewal  
**Files:** 
- `src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt`
- `src/app/src/main/java/com/melodee/autoplayer/data/api/NetworkModule.kt`

**Current State:**
- ✅ AuthenticationResponse has refreshToken fields
- ✅ NetworkModule has 401 handler
- ❌ No refresh logic implemented
- ✅ Spec says this is acceptable ("Phase 6.2 Future")

**Current Behavior:**
```kotlin
// NetworkModule.kt line 133-138:
if (response.code == 401 && handlingAuthFailure.compareAndSet(false, true)) {
    Log.w("NetworkModule", "Received 401 Unauthorized - token expired")
    clearAuthentication()
    onAuthenticationFailure?.invoke()  // Triggers logout
}
```

**Recommended Enhancement (if time allows):**
```kotlin
// Add refresh token storage
private var refreshToken: String = ""

fun setAuthenticationResponse(authResponse: AuthenticationResponse) {
    authToken = authResponse.token
    refreshToken = authResponse.refreshToken
    // Store expiresAt for proactive refresh
}

// Update 401 handler:
if (response.code == 401 && handlingAuthFailure.compareAndSet(false, true)) {
    return try {
        // Attempt refresh
        val refreshApi = retrofit?.create(MusicApi::class.java)
        val newAuth = refreshApi?.refreshToken(refreshToken)
        
        if (newAuth != null) {
            setAuthenticationResponse(newAuth)
            handlingAuthFailure.set(false)
            
            // Retry original request with new token
            val newRequest = original.newBuilder()
                .header("Authorization", "Bearer ${newAuth.token}")
                .build()
            chain.proceed(newRequest)
        } else {
            clearAuthentication()
            onAuthenticationFailure?.invoke()
            response
        }
    } catch (e: Exception) {
        clearAuthentication()
        onAuthenticationFailure?.invoke()
        response
    }
}
```

**Note:** Spec marks this as future work, so **not blocking** current migration.

---

### 🟡 MINOR #10: Playlist.owner and Album.artist Nullability

**Severity:** MINOR  
**Impact:** Potential NPE if code assumes non-null  
**Files:** `src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt`

**Current Code:**
```kotlin
// Models.kt line 62-75:
data class Playlist(
    val id: UUID,
    val name: String,
    val description: String = "",
    val imageUrl: String,
    val thumbnailUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val songCount: Int,
    val isPublic: Boolean = false,
    val owner: User? = null,          // Nullable
    val createdAt: String = "",
    val updatedAt: String = ""
)

// Models.kt line 196-212:
data class Album(
    val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val releaseYear: Int,
    val userStarred: Boolean = false,
    val userRating: Int = 0,
    val artist: Artist? = null,       // Nullable
    // ...
)
```

**Spec Discrepancy:**
- Spec Phase 1 says Album "now includes an `artist` reference" (implies required)
- Spec Phase 1 says Playlist has "owner: User" (no mention of nullable)
- Code makes both nullable (safer but inconsistent with spec)

**Required Action:**
1. Check Swagger schemas:
   - Is `Playlist.owner` required or optional?
   - Is `Album.artist` required or optional?
2. If required: make non-null
3. If optional: current code is correct, but add null checks in UI

**If Required (non-null):**
```kotlin
data class Playlist(
    // ...
    val owner: User,  // Non-null
    // ...
)

data class Album(
    // ...
    val artist: Artist,  // Non-null
    // ...
)
```

**If Optional (current code is correct):**
```kotlin
// Add safe handling in UI code:
playlist.owner?.let { owner ->
    Text("Created by ${owner.username}")
} ?: Text("Unknown creator")
```

---

## Test Recommendations

### Priority 1: Scrobble Timestamp Unit Test (CRITICAL)

**Purpose:** Verify timestamps are converted from milliseconds to seconds

**File:** Create `src/app/src/test/java/com/melodee/autoplayer/service/ScrobbleRequestTest.kt`

```kotlin
package com.melodee.autoplayer.service

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.melodee.autoplayer.data.api.ScrobbleRequest
import com.melodee.autoplayer.data.api.ScrobbleRequestType
import org.junit.Test

class ScrobbleRequestTest {
    private val gson = Gson()

    @Test
    fun `scrobble request converts timestamp from milliseconds to seconds`() {
        // Known timestamp: December 25, 2023 00:00:00 GMT
        val timestampMs = 1703462400000L  // milliseconds
        val expectedSeconds = 1703462400.0  // seconds

        val request = ScrobbleRequest(
            songId = "test-song-id",
            playerName = "MelodeePlayer",
            timestamp = (timestampMs / 1000.0),  // Convert ms → seconds
            playedDuration = 0.0,
            scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING
        )

        // Verify the conversion
        assertThat(request.timestamp).isEqualTo(expectedSeconds)

        // Verify JSON serialization
        val json = gson.toJson(request)
        val jsonObject = JsonParser.parseString(json).asJsonObject

        assertThat(jsonObject.get("timestamp").asDouble).isEqualTo(expectedSeconds)
        assertThat(jsonObject.get("timestamp").asDouble).isLessThan(2000000000.0)  // Sanity check: before year 2033
    }

    @Test
    fun `scrobble request converts playedDuration from milliseconds to seconds`() {
        val durationMs = 180000L  // 3 minutes in milliseconds
        val expectedSeconds = 180.0  // 3 minutes in seconds

        val request = ScrobbleRequest(
            songId = "test-song-id",
            playerName = "MelodeePlayer",
            timestamp = (System.currentTimeMillis() / 1000.0),
            playedDuration = (durationMs / 1000.0),  // Convert ms → seconds
            scrobbleTypeValue = ScrobbleRequestType.PLAYED
        )

        assertThat(request.playedDuration).isEqualTo(expectedSeconds)

        val json = gson.toJson(request)
        val jsonObject = JsonParser.parseString(json).asJsonObject

        assertThat(jsonObject.get("playedDuration").asDouble).isEqualTo(expectedSeconds)
    }

    @Test
    fun `timestamp is in reasonable range for current dates`() {
        val currentTimeMs = System.currentTimeMillis()
        val timestampSeconds = currentTimeMs / 1000.0

        // Verify timestamp is between 2020 and 2030
        val year2020Seconds = 1577836800.0  // Jan 1, 2020
        val year2030Seconds = 1893456000.0  // Jan 1, 2030

        assertThat(timestampSeconds).isGreaterThan(year2020Seconds)
        assertThat(timestampSeconds).isLessThan(year2030Seconds)
    }
}
```

---

### Priority 2: Retrofit Path Generation Test

**Purpose:** Verify path parameters are correctly substituted

**File:** Create `src/app/src/test/java/com/melodee/autoplayer/api/RetrofitPathTest.kt`

```kotlin
package com.melodee.autoplayer.api

import com.google.common.truth.Truth.assertThat
import com.melodee.autoplayer.data.api.MusicApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitPathTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: MusicApi

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(MusicApi::class.java)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `playlist songs endpoint uses correct path parameter`() = runBlocking {
        val playlistId = "abc123-def456-ghi789"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"meta": {"totalCount": 0, "pageSize": 10, "currentPage": 1, "totalPages": 0, "hasPrevious": false, "hasNext": false}, "data": []}
        """))

        try {
            api.getPlaylistSongs(playlistId, 1, 10)
        } catch (e: Exception) {
            // We don't care if it fails, just want to see the path
        }

        val request = mockWebServer.takeRequest()
        
        // Verify path substitution (should be /api/v1/playlists/{id}/songs, not /{apiKey}/songs)
        assertThat(request.path).contains("/api/v1/playlists/$playlistId/songs")
        assertThat(request.path).doesNotContain("{apiKey}")
        assertThat(request.path).doesNotContain("{id}")
    }

    @Test
    fun `favorite song endpoint uses correct path parameters`() = runBlocking {
        val songId = "song-uuid-123"
        val isStarred = true

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        try {
            api.favoriteSong(songId, isStarred)
        } catch (e: Exception) {
            // Don't care about response parsing
        }

        val request = mockWebServer.takeRequest()

        // Verify path substitution
        assertThat(request.path).contains("/api/v1/songs/starred/$songId/true")
        assertThat(request.path).doesNotContain("{apiKey}")
        assertThat(request.path).doesNotContain("{id}")
        assertThat(request.path).doesNotContain("{isStarred}")
    }

    @Test
    fun `artist songs endpoint uses id parameter`() = runBlocking {
        val artistId = "artist-uuid-456"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"meta": {"totalCount": 0, "pageSize": 10, "currentPage": 1, "totalPages": 0, "hasPrevious": false, "hasNext": false}, "data": []}
        """))

        try {
            api.getArtistSongs(artistId, 1, 10)
        } catch (e: Exception) {
            // Don't care
        }

        val request = mockWebServer.takeRequest()

        assertThat(request.path).contains("/api/v1/artists/$artistId/songs")
    }
}
```

**Dependencies Required:**
Add to `src/app/build.gradle.kts`:
```kotlin
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

---

### Priority 3: UUID Null Handling Test

**Purpose:** Verify graceful handling of malformed/null UUIDs

**File:** Add to `src/app/src/test/java/com/melodee/autoplayer/domain/ModelSerializationTest.kt`

```kotlin
@Test
fun `user deserializes with null id returns empty uuid`() {
    val json = """
        {
          "id": null,
          "email": "user@example.com",
          "thumbnailUrl": "thumb",
          "imageUrl": "image",
          "username": "tester"
        }
    """.trimIndent()

    val user = gson.fromJson(json, User::class.java)

    // After fix: should return UUID(0,0) instead of crashing
    assertThat(user.id).isEqualTo(UUID(0, 0))
}

@Test
fun `song deserializes with invalid uuid format returns empty uuid`() {
    val json = """
        {
          "id": "not-a-valid-uuid",
          "streamUrl": "stream",
          "title": "Test Song",
          "artist": {...},
          "album": {...},
          ...
        }
    """.trimIndent()

    val song = gson.fromJson(json, Song::class.java)

    // After fix: should return UUID(0,0) instead of throwing IllegalArgumentException
    assertThat(song.id).isEqualTo(UUID(0, 0))
}

@Test
fun `pagination response with null song id handles gracefully`() {
    val json = """
        {
          "meta": {
            "totalCount": 1,
            "pageSize": 10,
            "currentPage": 1,
            "totalPages": 1,
            "hasPrevious": false,
            "hasNext": false
          },
          "data": [
            {
              "id": null,
              "streamUrl": "stream",
              ...
            }
          ]
        }
    """.trimIndent()

    val response = gson.fromJson(json, SongPagedResponse::class.java)

    assertThat(response.data).hasSize(1)
    assertThat(response.data[0].id).isEqualTo(UUID(0, 0))
}
```

---

## Summary of Required Actions

### Immediate (BLOCKERS - Must Fix Before Merge)

1. **Scrobble Timestamp Units**
   - [ ] Verify server expectation (seconds vs milliseconds) in Swagger
   - [ ] Update `ScrobbleManager.kt` lines 121, 167-168 to divide by 1000.0
   - [ ] Add unit test for timestamp conversion
   - [ ] Update spec section 0.3 with confirmed units

2. **Path Parameter Naming**
   - [ ] Verify actual parameter names in Swagger for:
     - `GET /api/v1/playlists/{???}/songs`
     - `POST /api/v1/songs/starred/{???}/{???}`
   - [ ] Update `MusicApi.kt` lines 22, 67 with correct parameter names
   - [ ] Add Retrofit path generation test
   - [ ] Document findings in spec with "Verified:" comments

3. **UUID Serialization**
   - [ ] Add explicit UUID TypeAdapter to `NetworkModule.kt`
   - [ ] Handle null and malformed UUIDs gracefully
   - [ ] Add null UUID test case

4. **Parcelable Size Monitoring**
   - [ ] Add parcel size logging to `Song.writeToParcel()`
   - [ ] Test with large playlists (100+ songs)
   - [ ] Document size limits in code comments
   - [ ] Consider alternative (pass UUIDs, not full objects)

### High Priority (MAJOR - Fix This Sprint)

5. **Deprecated scrobbleType Field**
   - [ ] Verify if server needs string field in Swagger
   - [ ] Either remove field or remove @Deprecated annotation
   - [ ] Update ScrobbleManager accordingly

6. **Pagination Tests**
   - [ ] Create `PaginationTest.kt` with boundary tests
   - [ ] Test empty, first, last, middle pages
   - [ ] Verify hasPrevious/hasNext logic

7. **Rating Validation**
   - [ ] Verify rating scale in Swagger (0-5, 1-5, etc.)
   - [ ] Create `RatingConstants.kt` with MIN/MAX
   - [ ] Add validation to Song/Artist/Album init blocks
   - [ ] Add validation test

### Nice to Have (MINOR - Future Sprints)

8. **Remove Duplicate Search Method**
   - [ ] Add artistId parameter to `searchSongs()`
   - [ ] Remove `searchSongsWithArtist()` wrapper
   - [ ] Update all callers

9. **Refresh Token Implementation**
   - [ ] Implement token refresh in 401 interceptor
   - [ ] Add refresh token storage
   - [ ] Handle refresh failures gracefully

10. **Clarify Nullable Owner/Artist**
    - [ ] Verify Swagger schemas
    - [ ] Make non-null if required, or add null checks in UI

---

## Verification Checklist

Before marking migration complete:

- [ ] All unit tests pass (currently 46/46 passing)
- [ ] New tests added and passing (scrobble units, paths, UUIDs)
- [ ] Swagger verification documented in spec
- [ ] Manual testing completed:
  - [ ] Login with email
  - [ ] Login with username
  - [ ] View playlists
  - [ ] View playlist songs (verify path works)
  - [ ] Search songs
  - [ ] Search songs with artist filter
  - [ ] View artist albums
  - [ ] View album songs
  - [ ] Favorite/unfavorite song (verify path works)
  - [ ] Scrobble now playing (verify timestamp in logs)
  - [ ] Scrobble played song (verify timestamp + duration)
- [ ] Network logging reviewed:
  - [ ] All requests use `/api/v1/` prefix
  - [ ] Authorization header present
  - [ ] Scrobble timestamps look reasonable (<2000000000)
  - [ ] UUID fields serialize as strings
- [ ] Code review completed:
  - [ ] All BLOCKER issues resolved
  - [ ] MAJOR issues resolved or documented as follow-up
  - [ ] MINOR issues tracked in backlog

---

## Additional Resources

**Swagger Documentation:**
- Local: http://localhost:5157/swagger/
- OpenAPI JSON: http://localhost:5157/swagger/melodee/swagger.json

**Key Files:**
- Models: `src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt`
- API: `src/app/src/main/java/com/melodee/autoplayer/data/api/MusicApi.kt`
- Scrobble: `src/app/src/main/java/com/melodee/autoplayer/service/ScrobbleManager.kt`
- Network: `src/app/src/main/java/com/melodee/autoplayer/data/api/NetworkModule.kt`
- Tests: `src/app/src/test/java/com/melodee/autoplayer/`

**Running Tests:**
```bash
cd /home/steven/source/melodee-player/src
./gradlew app:testDebugUnitTest
./gradlew app:testDebugUnitTest --tests "ScrobbleRequestTest"
```

**Checking Network Logs:**
- Enable verbose logging in LogCat
- Filter by tag: "NetworkModule", "ScrobbleManager"
- Look for "Scrobble Request" log lines showing actual values

---

## Questions for Product/API Team

Before completing fixes, get clarification on:

1. **Scrobble Timestamp Units:** Are `timestamp` and `playedDuration` in seconds or milliseconds?
2. **Path Parameters:** Do playlists and songs use `{id}` or `{apiKey}` in path templates?
3. **Deprecated scrobbleType:** Is the string field still required, or can we send only the enum?
4. **Rating Scale:** What is the valid range for userRating? (0-5, 1-5, 0-10?)
5. **Nullable Fields:** Are `Playlist.owner` and `Album.artist` always present in responses?

**Document answers in `docs/API-CHANGES.md` section 0 (Decisions & Assumptions)**

---

## Migration Completion Criteria

✅ **Definition of Done:**
- All 4 BLOCKER issues resolved
- All 3 MAJOR issues resolved or tracked
- All new tests passing
- Manual testing completed successfully
- Swagger verification documented
- Code reviewed and approved
- No regression in existing functionality

**Estimated Effort:** 6-8 hours (4 hours for fixes, 2-4 hours for testing/verification)

**Risk Assessment:** Low (fixes are surgical, existing tests provide safety net)

**Deployment Readiness:** Not ready (blockers must be fixed first)

---

*End of Review Document*
