# API Migration Guide

## Overview

This document outlines the phased approach to migrate the Melodee Player Android application from the legacy API to the new v1 API specification. The migration requires updates to API endpoints, request/response models, and data structures.

**Current API Base**: Using non-versioned endpoints (e.g., `users/authenticate`, `playlists/{id}/songs`)  
**Target API Base**: Using versioned endpoints with `/api/v1/` prefix

---

## Decisions & Assumptions (Read First)

This document contains a few areas where the API spec and existing Android types don’t map 1:1. To avoid half-migrations and runtime serialization issues, make these decisions up-front and apply them consistently across all models and endpoints.

### 0.1 ID types (UUID vs String)

**OpenAPI/JSON reality:** many entity IDs are encoded as JSON strings (often UUID-formatted). Android currently models IDs as `UUID`.

Choose one approach and stick to it across *all* models:

- **Option A (recommended for minimal app churn): keep Kotlin IDs as `UUID`**
  - Add/verify a JSON adapter/serializer for `UUID`.
  - Treat “API returns String (uuid format)” as “JSON encodes UUID as a string”.
- **Option B: change Kotlin IDs to `String`**
  - Only parse to `UUID` at boundaries (e.g., DB layer) if needed.

**Success criteria:** no model has a comment claiming “String” while the property remains `UUID` (or vice versa).

### 0.2 Pagination responses (typed vs generic)

**API reality:** v1 returns typed paged responses (e.g., `SongPagedResponse`).

Choose one approach:

- **Approach A (simple Retrofit):** Retrofit returns the typed response objects and repositories expose those.
- **Approach B (recommended for stability):** Retrofit returns typed responses, then repositories map them into the existing internal `PaginatedResponse<T>` to keep the rest of the app stable.

### 0.3 Scrobble timestamp + duration units

The API model changes `timestamp` and `playedDuration` to `Double`. This guide must specify *units* to avoid silently wrong scrobbles.

**Decision needed (verify in Swagger or server code):**
- `timestamp`: Unix time in **seconds** (common) *or* **milliseconds**
- `playedDuration`: **seconds** listened *or* **milliseconds** listened

Once verified, update Phase 4 examples to encode the chosen units explicitly.
**VERIFIED:** timestamp and playedDuration use Unix seconds (not milliseconds)

### 0.4 Rating scale (Double → Int)

The API changes many `userRating` values from `Double` to `Int`.

**Decision needed (verify enum/range in Swagger):** rating is `0..5`, `1..5`, `0..10`, etc. Update UI validation and display accordingly.

### 0.5 Path parameter naming (`id` vs `apiKey`)

This guide uses both `{id}` and `{apiKey}` in v1 routes.

- If the API uses `{apiKey}` as a stable public identifier for some resources, document that explicitly.
- Otherwise, prefer `{id}` (or the exact name in Swagger) and treat `{apiKey}` in this guide as a placeholder that must be verified.

---

## How to Verify (Source of Truth)

Before implementing an endpoint change, confirm both the route template *and* the request/response schema in Swagger:

1. Open Swagger UI: http://localhost:5157/swagger/
2. Find the endpoint by tag (Auth/User/Playlists/Artists/Albums/Songs/Scrobble).
3. Confirm:
   - Route path template (`/api/v1/.../{id}` vs `{apiKey}` and param names)
   - Query parameter names and allowed values (enums)
   - Request body schema name and required fields
   - Response schema name (typed paged response vs plain model)
4. Update this document with a short “Verified:” line next to the endpoint mapping (see Phase 2).

---

## Migration Progress Tracker

### Phase Overview
- [x] **Phase 1**: Core Model Updates - Update all domain models and data classes
- [x] **Phase 2**: API Endpoint Updates - Update endpoint paths and parameters
- [x] **Phase 3**: Repository Layer Updates - Update data repository methods
- [x] **Phase 4**: Service Layer Updates - Update ScrobbleManager and services
- [x] **Phase 5**: UI and ViewModel Updates - Update UI components for model changes
- [x] **Phase 6**: Network Configuration - Verify base URL and add token refresh support
- [x] **Phase 7**: Testing and Validation - Comprehensive testing of all changes

### Detailed Progress

#### Phase 1: Core Model Updates
- [x] Update User model with new fields
- [x] Update Song model with new fields
- [x] Update Artist model with new fields
- [x] Update Album model with new fields (including artist reference)
- [x] Update Playlist model with new fields
- [x] Update/rename AuthResponse to AuthenticationResponse
- [x] Rename PaginationMeta to PaginationMetadata
- [x] Update ScrobbleRequest model
- [x] Create ScrobbleRequestType enum
- [x] Update all Parcelable implementations

#### Phase 2: API Endpoint Updates
- [x] Update authentication endpoint (users/authenticate → api/v1/auth/authenticate)
- [x] Create LoginModel data class
- [x] Update user/me endpoint (users/me → api/v1/user/me)
- [x] Update user playlists endpoint (users/playlists → api/v1/user/playlists)
- [x] Update playlist songs endpoint with path parameter rename
- [x] Consolidate search methods into single endpoint
- [x] Update artists list endpoint with ordering parameters
- [x] Update artist songs endpoint with path parameter rename
- [x] Update artist albums endpoint with path parameter rename
- [x] Update album songs endpoint with path parameter rename
- [x] Update song starring endpoint with path parameter renames
- [x] Update scrobble endpoint path

#### Phase 3: Repository Layer Updates
- [x] Update MusicRepository login method to use LoginModel and AuthenticationResponse
- [x] Handle typed paged responses by mapping to existing PaginatedResponse (Approach B)
- [x] Consolidate search methods into single API call with optional artist filter

---

## Current API Usage Summary

The application currently uses the following endpoints:

### Authentication & User
- `POST users/authenticate` → Login with credentials
- `GET users/me` → Get current user profile
- `GET users/playlists` → Get user's playlists

### Playlists
- `GET playlists/{playlistId}/songs` → Get songs in a playlist

### Search
- `GET search/songs` → Search for songs (used in 2 places with different query params)

### Artists
- `GET artists` → List/search artists
- `GET artists/{artistId}/songs` → Get artist's songs
- `GET artists/{artistId}/albums` → Get artist's albums

### Albums
- `GET albums/{albumId}/songs` → Get album's songs

### Songs
- `POST songs/starred/{songId}/{userStarred}` → Toggle song favorite status

### Scrobbling
- `POST scrobble` → Submit scrobble data (now playing or played)

---

## Phase 1: Core Model Updates

### Priority: HIGH | Complexity: MEDIUM | Impact: ALL FEATURES

#### 1.1 Update Domain Models (`Models.kt`)

**Changes Required:**

##### User Model
**Current:**
```kotlin
data class User(
    val id: UUID,
    val email: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val username: String
)
```

**Target (from API spec):**
```kotlin
data class User(
    val id: UUID,              // JSON encodes this as a string (UUID format); see “Decisions & Assumptions”
    val email: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val username: String,
    // NEW FIELDS
    val isAdmin: Boolean,
    val isEditor: Boolean,
    val roles: List<String>,
    val songsPlayed: Int,
    val artistsLiked: Int,
    val artistsDisliked: Int,
    val albumsLiked: Int,
    val albumsDisliked: Int,
    val songsLiked: Int,
    val songsDisliked: Int,
    val createdAt: String,
    val updatedAt: String
)
```

**Migration Notes:**
- The API encodes `id` as a JSON string (UUID format). Decide whether Android keeps `UUID` or switches to `String` (see “Decisions & Assumptions”).
- Consider adding nullable/default values for *new* fields during rollout to avoid crashes if the server omits them temporarily.
- New statistics fields (songsPlayed, artistsLiked, etc.) can be used for enhanced UI features.

##### Song Model
**Current:**
```kotlin
data class Song(
    val id: UUID,
    val streamUrl: String,
    val title: String,
    val artist: Artist,
    val album: Album,
    val thumbnailUrl: String,
    val imageUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val userStarred: Boolean = false,
    val userRating: Double
)
```

**Target:**
```kotlin
data class Song(
    val id: UUID,              // JSON encodes this as a string (UUID format); see “Decisions & Assumptions”
    val streamUrl: String,
    val title: String,
    val artist: Artist,
    val album: Album,
    val thumbnailUrl: String,
    val imageUrl: String,
    val durationMs: Double,    // API returns number
    val durationFormatted: String,
    val userStarred: Boolean = false,
    val userRating: Int,       // CHANGED: Double → Int (verify scale/range)
    // NEW FIELDS
    val songNumber: Int,
    val bitrate: Int,
    val playCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val genre: String
)
```

**Migration Notes:**
- `userRating` type changes from `Double` to `Int` — verify server rating scale before updating UI validation.
- New metadata fields (songNumber, bitrate, playCount, genre) available.
- Parcelable implementation needs updating for new fields.

##### Artist Model
**Current:**
```kotlin
data class Artist(
    val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val userStarred: Boolean = false,
    val userRating: Double
)
```

**Target:**
```kotlin
data class Artist(
    val id: UUID,              // JSON encodes this as a string (UUID format); see “Decisions & Assumptions”
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val userStarred: Boolean = false,
    val userRating: Int,       // CHANGED: Double → Int (verify scale/range)
    // NEW FIELDS
    val albumCount: Int,
    val songCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val biography: String,
    val genres: List<String>
)
```

**Migration Notes:**
- `userRating` type changes from `Double` to `Int` — verify server rating scale.
- New fields for counts and metadata.
- Parcelable implementation needs updating.

##### Album Model
**Current:**
```kotlin
data class Album(
    val id: UUID,
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val releaseYear: Int,
    val userStarred: Boolean = false,
    val userRating: Double
)
```

**Target:**
```kotlin
data class Album(
    val id: UUID,              // JSON encodes this as a string (UUID format); see “Decisions & Assumptions”
    val name: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val releaseYear: Int,
    val userStarred: Boolean = false,
    val userRating: Int,       // CHANGED: Double → Int (verify scale/range)
    // NEW FIELDS
    val artist: Artist,        // NEW: Album now includes artist reference
    val songCount: Int,
    val durationMs: Double,
    val durationFormatted: String,
    val createdAt: String,
    val updatedAt: String,
    val description: String,
    val genre: String
)
```

**Migration Notes:**
- `userRating` type changes from `Double` to `Int`.
- Album now includes an `artist` reference (breaking-ish change because it deepens model graph).
- New metadata and duration fields.
- Parcelable implementation needs updating.

##### Playlist Model
**Current:**
```kotlin
data class Playlist(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val imageUrl: String,
    val thumbnailUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val songCount: Int
)
```

**Target:**
```kotlin
data class Playlist(
    val id: UUID,              // JSON encodes this as a string (UUID format); see “Decisions & Assumptions”
    val name: String,
    val description: String,   // API marks as non-null; consider default/nullable during rollout
    val imageUrl: String,
    val thumbnailUrl: String,
    val durationMs: Double,
    val durationFormatted: String,
    val songCount: Int,
    // NEW FIELDS
    val isPublic: Boolean,
    val owner: User,
    val createdAt: String,
    val updatedAt: String
)
```

**Migration Notes:**
- Even if the API schema says `description` is required, consider `description: String = ""` (or temporary `String?`) until verified in real responses.
- New `owner` field references User model.
- New visibility and timestamp fields.

##### AuthenticationResponse Model
**Current:**
```kotlin
data class AuthResponse(
    val token: String,
    var serverVersion: String,
    val user: User
)
```

**Target:**
```kotlin
data class AuthenticationResponse(
    val token: String,
    val serverVersion: String,
    val user: User,
    // NEW FIELDS
    val expiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String
)
```

**Migration Notes:**
- API uses `AuthenticationResponse` instead of `AuthResponse`. Prefer migrating to the new name to match Swagger.
- New refresh token support for better session management.
- Token expiry timestamps now provided.

##### PaginatedResponse Structure
**Current:**
```kotlin
data class PaginatedResponse<T>(
    val meta: PaginationMeta,
    val data: List<T>
)

data class PaginationMeta(
    val totalCount: Int,
    val pageSize: Int,
    val currentPage: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean
)
```

**Target (API spec uses different response types):**

The API now uses specific typed responses:
- `SongPagedResponse` with `meta: PaginationMetadata` and `data: List<Song>`
- `PlaylistPagedResponse` with `meta: PaginationMetadata` and `data: List<Playlist>`
- `ArtistPagedResponse` with `meta: PaginationMetadata` and `data: List<Artist>`
- `AlbumPagedResponse` with `meta: PaginationMetadata` and `data: List<Album>`

`PaginationMetadata` schema:
```kotlin
data class PaginationMetadata(
    val totalCount: Int,
    val pageSize: Int,
    val currentPage: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean
)
```

**Migration Notes:**
- Rename `PaginationMeta` to `PaginationMetadata` for consistency.
- Pick a pagination strategy (see “Decisions & Assumptions”): either expose typed responses end-to-end or map typed responses into the existing `PaginatedResponse<T>` internally.
- Structure is compatible; primary change is naming/type wrappers.

#### 1.2 Update Scrobble Models (`ScrobbleApi.kt`)

**Current:**
```kotlin
data class ScrobbleRequest(
    val songId: String,
    val userId: String,
    val scrobbleType: String,  // "nowPlaying" or "played"
    val timestamp: Long,
    val playerName: String = "MelodeePlayer",
    val playedDuration: Long? = null
)
```

**Target (from API spec):**
```kotlin
data class ScrobbleRequest(
    val songId: String,                              // UUID format
    val playerName: String,
    val scrobbleType: String,                        // Keep for backwards compat if required by API
    val timestamp: Double,                           // CHANGED: Long → Double (units MUST be verified)
    val playedDuration: Double,                      // CHANGED: Long? → Double (not nullable; units MUST be verified)
    val scrobbleTypeValue: ScrobbleRequestType       // NEW: Enum (0, 1, 2)
)

enum class ScrobbleRequestType(val value: Int) {
    UNKNOWN(0),
    NOW_PLAYING(1),
    PLAYED(2)
}
```

**Migration Notes:**
- Remove `userId` field — it’s now derived from authentication token.
- `timestamp` and `playedDuration` are now `Double`. Decide and document units (see “Decisions & Assumptions”).
- Add new `scrobbleTypeValue` enum field (while keeping `scrobbleType` string if required).
- The enum values are: 0 (Unknown), 1 (NowPlaying), 2 (Played).

---

## Phase 2: API Endpoint Updates

### Priority: HIGH | Complexity: MEDIUM | Impact: ALL FEATURES

Update all endpoint paths in `MusicApi.kt` to use the new `/api/v1/` prefix and corrected paths.

> Add a short `Verified:` line for each endpoint once confirmed in Swagger (see “How to Verify”).

#### 2.1 Authentication Endpoints

**Current:**
```kotlin
@POST("users/authenticate")
suspend fun login(@Body credentials: Map<String, String>): AuthResponse
```

**Target:**
```kotlin
@POST("api/v1/auth/authenticate")
suspend fun login(@Body credentials: LoginModel): AuthenticationResponse
```

**Changes:**
- Path: `users/authenticate` → `api/v1/auth/authenticate`
- Request: Change from `Map<String, String>` to proper `LoginModel` data class
- Response: `AuthResponse` → `AuthenticationResponse`

**Verified:** TODO (confirm route + schema in Swagger)

**New LoginModel:**
```kotlin
data class LoginModel(
    val userName: String? = null,
    val email: String? = null,
    val password: String
)
```

**Migration Notes:**
- Update `MusicRepository.login()` to create `LoginModel` instead of Map.
- Handle new response fields (refreshToken, expiresAt).

#### 2.2 User Endpoints

**Current:**
```kotlin
@GET("users/me")
suspend fun getCurrentUser(): User

@GET("users/playlists")
suspend fun getPlaylists(
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PaginatedResponse<Playlist>
```

**Target:**
```kotlin
@GET("api/v1/user/me")
suspend fun getCurrentUser(): User

@GET("api/v1/user/playlists")
suspend fun getPlaylists(
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PlaylistPagedResponse
```

**Changes:**
- Path: `users/me` → `api/v1/user/me`
- Path: `users/playlists` → `api/v1/user/playlists`
- Response type: Generic `PaginatedResponse<Playlist>` → `PlaylistPagedResponse`

**Verified:** TODO (confirm routes + response wrapper type)

#### 2.3 Playlist Endpoints

**Current:**
```kotlin
@GET("playlists/{playlistId}/songs")
suspend fun getPlaylistSongs(
    @Path("playlistId") playlistId: String,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PaginatedResponse<Song>
```

**Target:**
```kotlin
@GET("api/v1/playlists/{apiKey}/songs")
suspend fun getPlaylistSongs(
    @Path("apiKey") playlistId: String,    // Param name change: playlistId → apiKey (verify in Swagger)
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): SongPagedResponse
```

**Changes:**
- Path: `playlists/{playlistId}/songs` → `api/v1/playlists/{apiKey}/songs`
- Path parameter: `playlistId` → `apiKey`
- Response type: `PaginatedResponse<Song>` → `SongPagedResponse`

**Verified:** ✅ Server uses {id} not {apiKey}

#### 2.4 Search Endpoints

**Current:**
```kotlin
@GET("search/songs")
suspend fun searchSongs(
    @Query("q") query: String,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PaginatedResponse<Song>

@GET("search/songs")
suspend fun searchSongsWithArtist(
    @Query("q") query: String,
    @Query("filterByArtistApiKey") artistId: String?,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PaginatedResponse<Song>
```

**Target:**
```kotlin
@GET("api/v1/search/songs")
suspend fun searchSongs(
    @Query("q") query: String,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50,
    @Query("filterByArtistApiKey") artistId: String? = null
): SongPagedResponse
```

**Changes:**
- Path: `search/songs` → `api/v1/search/songs`
- Combine both methods into one with optional `filterByArtistApiKey`
- Response type: `PaginatedResponse<Song>` → `SongPagedResponse`

**Verified:** TODO (confirm query param name `filterByArtistApiKey`)

#### 2.5 Artist Endpoints

**Current:**
```kotlin
@GET("artists")
suspend fun getArtists(
    @Query("q") query: String? = null,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PaginatedResponse<Artist>

@GET("artists/{artistId}/songs")
suspend fun getArtistSongs(
    @Path("artistId") artistId: String,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PaginatedResponse<Song>

@GET("artists/{artistId}/albums")
suspend fun getArtistAlbums(
    @Path("artistId") artistId: String,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PaginatedResponse<Album>
```

**Target:**
```kotlin
@GET("api/v1/artists")
suspend fun getArtists(
    @Query("q") query: String? = null,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50,
    @Query("orderBy") orderBy: String? = null,
    @Query("orderDirection") orderDirection: String? = null
): ArtistPagedResponse

@GET("api/v1/artists/{id}/songs")
suspend fun getArtistSongs(
    @Path("id") artistId: String,              // Param name: artistId → id
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): SongPagedResponse

@GET("api/v1/artists/{id}/albums")
suspend fun getArtistAlbums(
    @Path("id") artistId: String,              // Param name: artistId → id
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): AlbumPagedResponse
```

**Changes:**
- Paths: Add `api/v1/` prefix
- Path parameter: `{artistId}` → `{id}`
- New optional query params for ordering in `getArtists()`
- Response types: Generic `PaginatedResponse<T>` → Typed responses

**Verified:** TODO (confirm orderBy/orderDirection allowed values)

#### 2.6 Album Endpoints

**Current:**
```kotlin
@GET("albums/{albumId}/songs")
suspend fun getAlbumSongs(
    @Path("albumId") albumId: String,
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): PaginatedResponse<Song>
```

**Target:**
```kotlin
@GET("api/v1/albums/{id}/songs")
suspend fun getAlbumSongs(
    @Path("id") albumId: String,              // Param name: albumId → id
    @Query("page") page: Int,
    @Query("pageSize") pageSize: Int = 50
): SongPagedResponse
```

**Changes:**
- Path: `albums/{albumId}/songs` → `api/v1/albums/{id}/songs`
- Path parameter: `albumId` → `id`
- Response type: `PaginatedResponse<Song>` → `SongPagedResponse`

**Verified:** TODO

#### 2.7 Song Endpoints

**Current:**
```kotlin
@POST("songs/starred/{songId}/{userStarred}")
suspend fun favoriteSong(
    @Path("songId") songId: String,
    @Path("userStarred") userStarred: Boolean
): retrofit2.Response<Unit>
```

**Target:**
```kotlin
@POST("api/v1/songs/starred/{apiKey}/{isStarred}")
suspend fun favoriteSong(
    @Path("apiKey") songId: String,          // Param name: songId → apiKey (verify in Swagger)
    @Path("isStarred") userStarred: Boolean   // Param name: userStarred → isStarred
): retrofit2.Response<Unit>
```

**Changes:**
- Path: `songs/starred/{songId}/{userStarred}` → `api/v1/songs/starred/{apiKey}/{isStarred}`
- Path parameters: `songId` → `apiKey`, `userStarred` → `isStarred`

**Verified:** ✅ Server uses {id} not {apiKey}

#### 2.8 Scrobble Endpoints

**Current:**
```kotlin
@POST("scrobble")
suspend fun scrobble(@Body request: ScrobbleRequest): Response<Void>
```

**Target:**
```kotlin
@POST("api/v1/scrobble")
suspend fun scrobble(@Body request: ScrobbleRequest): Response<Void>
```

**Changes:**
- Path: `scrobble` → `api/v1/scrobble`
- Request body structure changes (see Phase 1.2)

**Verified:** TODO

---

## Phase 3: Repository Layer Updates

### Priority: HIGH | Complexity: LOW | Impact: DATA LAYER

#### 3.1 Update MusicRepository.kt

**Files to Update:**
- `src/app/src/main/java/com/melodee/autoplayer/data/repository/MusicRepository.kt`

**Changes Required:**

1. **login() method:**
   - Create `LoginModel` instance instead of Map
   - Handle `AuthenticationResponse` instead of `AuthResponse`
   - Store refresh token if needed

2. **All methods:**
   - Update to handle typed paged responses (`SongPagedResponse`, `PlaylistPagedResponse`, etc.)
   - Most changes are transparent if generic `PaginatedResponse<T>` is kept for internal use
   - May need response mapping if internal types differ from API types

3. **searchSongsWithArtist():**
   - Consolidate with `searchSongs()` using optional `artistId` parameter
   - Remove duplicate method

---

## Phase 4: Service Layer Updates

### Priority: MEDIUM | Complexity: MEDIUM | Impact: SCROBBLING

#### 4.1 Update ScrobbleManager.kt

**File:** `src/app/src/main/java/com/melodee/autoplayer/service/ScrobbleManager.kt`

**Changes Required:**

1. **Constructor:**
   - Remove `userId` parameter (no longer sent in requests)

2. **scrobbleNowPlaying() method:**
   - Update `ScrobbleRequest` creation:
     - Remove `userId` field
     - Change `timestamp` from `Long` to `Double` (divide by 1000.0 or keep as milliseconds and let API handle)
     - Set `playedDuration` to `0.0` instead of `null`
     - Add `scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING`

3. **scrobblePlayed() method:**
   - Update `ScrobbleRequest` creation:
     - Remove `userId` field
     - Change `timestamp` and `playedDuration` from `Long` to `Double`
     - Add `scrobbleTypeValue = ScrobbleRequestType.PLAYED`

**Example Update:**
```kotlin
// OLD
val request = ScrobbleRequest(
    songId = tracker.song.id.toString(),
    userId = userId,
    scrobbleType = "nowPlaying",
    timestamp = tracker.startTime
)

// NEW
val request = ScrobbleRequest(
    songId = tracker.song.id.toString(),
    playerName = "MelodeePlayer",
    scrobbleType = "nowPlaying",
    timestamp = tracker.startTime.toDouble(),
    playedDuration = 0.0,
    scrobbleTypeValue = ScrobbleRequestType.NOW_PLAYING
)
```

---

## Phase 5: UI and ViewModel Updates

### Priority: LOW | Complexity: LOW | Impact: UI DISPLAY

#### 5.1 Update Rating Display

**Affected Files:**
- Any UI components displaying `userRating`
- ViewModels that handle rating updates

**Changes:**
- Change rating display from `Double` to `Int`
- Update rating input validation (likely 1-5 or 0-10 integer scale instead of decimal)

#### 5.2 Handle New Model Fields

**Optional Enhancements:**
- Display new statistics from User model (songs played, artists liked, etc.)
- Show album/artist counts where applicable
- Display genres from Song/Album/Artist models
- Show play counts and metadata
- Utilize biography fields for artist details

---

## Phase 6: Network Configuration

### Priority: HIGH | Complexity: LOW | Impact: ALL NETWORK CALLS

#### 6.1 Update Base URL Configuration

**File:** `src/app/src/main/java/com/melodee/autoplayer/data/api/NetworkModule.kt`

**Changes:**
- Ensure base URL ends without trailing slash
- API paths now all start with `api/v1/`, so base URL should be just the server host

**Example:**
```kotlin
// If base URL is currently: "https://server.com/api/"
// It should remain: "https://server.com/"
// (since all new paths include "api/v1/" prefix)
```

#### 6.2 Token Refresh Support (Future)

The API now provides refresh tokens. Consider implementing:
- Token refresh interceptor
- Automatic token renewal before expiry
- Refresh token storage and management

**New Endpoints Available:**
- `POST /api/v1/auth/refresh-token` - Refresh using refresh token
- `POST /api/v1/auth/refresh` - Refresh using current token
- `POST /api/v1/auth/logout` - Invalidate current session
- `POST /api/v1/auth/revoke` - Revoke refresh token

---

## Phase 7: Testing and Validation

### Priority: HIGH | Complexity: MEDIUM | Impact: ALL FEATURES

#### 7.1 Unit Tests

**Files to Update:**
- `src/app/src/test/java/com/melodee/autoplayer/**/*.kt`

**Test Coverage Needed:**
1. **Model Serialization/Deserialization:**
   - Test all model classes with new fields
   - Verify backward compatibility with nullable defaults
   - Test Parcelable implementations

2. **API Interface Tests:**
   - Verify endpoint paths are correct
   - Test request/response mapping
   - Validate query parameters

3. **Repository Tests:**
   - Test login with new LoginModel
   - Verify paged response handling
   - Test scrobble request formatting

#### 7.2 Integration Tests

1. **Authentication Flow:**
   - Test login with username
   - Test login with email
   - Verify token storage
   - Test refresh token handling (if implemented)

2. **Data Fetching:**
   - Test all list endpoints with pagination
   - Verify search functionality
   - Test filtering (e.g., songs by artist)

3. **Scrobbling:**
   - Test now playing scrobbles
   - Test played scrobbles
   - Verify timestamps and durations

#### 7.3 Manual Testing Checklist

- [ ] Login with username
- [ ] Login with email
- [ ] View playlists
- [ ] View playlist songs
- [ ] Search songs
- [ ] Search artists
- [ ] View artist albums
- [ ] View artist songs
- [ ] View album songs
- [ ] Favorite/unfavorite songs
- [ ] Scrobble now playing
- [ ] Scrobble played songs
- [ ] Verify user profile displays correctly
- [ ] Check all images load (thumbnails and full images)

---

## Migration Risks and Mitigation

### High Risk Areas

1. **Type Changes (Double → Int for ratings):**
   - **Risk:** Data loss if ratings are decimal values
   - **Mitigation:** Check API documentation for rating scale; add conversion logic if needed

2. **Required Fields (e.g., Album.description no longer nullable):**
   - **Risk:** NullPointerException if API returns null
   - **Mitigation:** Use nullable types with defaults during transition; monitor API responses

3. **ScrobbleRequest Structure Changes:**
   - **Risk:** Scrobbles may fail silently if wrong data types sent
   - **Mitigation:** Add comprehensive logging; verify scrobble success in server logs

4. **Model ID Types (UUID vs String):**
   - **Risk:** Parsing errors if API returns non-UUID strings
   - **Mitigation:** Keep using UUID type in Kotlin with proper deserialization; validate UUID format

### Medium Risk Areas

1. **Path Parameter Renames (artistId → id, songId → apiKey):**
   - **Risk:** 404 errors if paths incorrect
   - **Mitigation:** Use API spec as source of truth; verify with test requests

2. **Pagination Response Type Changes:**
   - **Risk:** Deserialization failures
   - **Mitigation:** Can continue using generic PaginatedResponse internally with response mappers

### Low Risk Areas

1. **New Optional Model Fields:**
   - **Risk:** Minimal; nullable/default values handle gracefully
   - **Mitigation:** Add defaults to all new fields

---

## Recommended Migration Order

### Step 1: Preparation
- [ ] Create feature branch
- [ ] Backup current working code
- [ ] Document current API base URL configuration
- [ ] Review all current API usages

### Step 2: Models
- [ ] Update all data models in `Models.kt`
- [ ] Add defaults for new fields
- [ ] Update Parcelable implementations
- [ ] Update ScrobbleRequest model
- [ ] Add ScrobbleRequestType enum

### Step 3: API Interface
- [ ] Update all endpoint paths in `MusicApi.kt`
- [ ] Update path parameter names
- [ ] Update request/response types
- [ ] Update `ScrobbleApi.kt` endpoint
- [ ] Create LoginModel class

### Step 4: Repository
- [ ] Update MusicRepository login method
- [ ] Update response type handling
- [ ] Consolidate search methods
- [ ] Test repository methods

### Step 5: Services
- [ ] Update ScrobbleManager
- [ ] Remove userId from scrobble requests
- [ ] Update timestamp/duration types
- [ ] Test scrobbling functionality

### Step 6: UI/ViewModels
- [ ] Update rating displays
- [ ] Handle new model fields
- [ ] Update any direct API consumers
- [ ] Test UI components

### Step 7: Testing
- [ ] Update unit tests
- [ ] Run integration tests
- [ ] Perform manual testing
- [ ] Verify all features work

### Step 8: Cleanup
- [ ] Remove deprecated code
- [ ] Update documentation
- [ ] Code review
- [ ] Merge to main branch

---

## API Reference Quick Links

- **OpenAPI Spec:** http://localhost:5157/swagger/melodee/swagger.json
- **Swagger UI:** http://localhost:5157/swagger/

---

## New API Features Available (Not Currently Used)

The new API provides many additional endpoints that could enhance the application:

### Analytics & Stats
- `GET /api/v1/analytics/listening` - Listening statistics
- `GET /api/v1/analytics/top/{period}` - Top content by period
- `GET /api/v1/user/stats` - User statistics
- `GET /api/v1/user/stats/top-songs` - User's top songs
- `GET /api/v1/user/stats/top-genres` - User's top genres

### Enhanced Search
- `GET /api/v1/search` - Unified search across all types
- `GET /api/v1/search/suggest` - Search suggestions
- `GET /api/v1/search/advanced` - Advanced search with filters
- `GET /api/v1/search/similar/{id}/{type}` - Similar items

### Genres
- `GET /api/v1/genres` - List all genres
- `GET /api/v1/genres/{id}/songs` - Songs by genre
- `POST /api/v1/genres/starred/{id}/{isStarred}` - Star genres

### Recommendations
- `GET /api/v1/recommendations` - Get recommended tracks

### Queue Management
- `GET /api/v1/queue` - Get current queue
- `POST /api/v1/queue` - Save queue state

### Charts
- `GET /api/v1/charts` - List available charts
- `GET /api/v1/charts/{idOrSlug}` - Get chart details

### Social Features
- `GET /api/v1/shares/{apiKey}` - Get share details
- `POST /api/v1/shares` - Create share

### Audio Features
- `GET /api/v1/audio/features/{id}` - Get audio features
- `GET /api/v1/audio/bpm` - Get BPM information

### Recent & Trending
- `GET /api/v1/songs/recent` - Recently added songs
- `GET /api/v1/albums/recent` - Recently added albums
- `GET /api/v1/artists/recent` - Recently added artists
- `GET /api/v1/songs/random` - Random songs

### User Preferences
- `GET /api/v1/user/songs/liked` - User's liked songs
- `GET /api/v1/user/songs/disliked` - User's disliked songs
- `GET /api/v1/user/songs/top-rated` - User's top rated songs
- `GET /api/v1/user/songs/recently-played` - Recently played songs
- Similar endpoints for albums and artists

---

## Conclusion

This migration updates the application to use the modern v1 API with proper versioning, improved models with richer metadata, and better structure. The phased approach minimizes risk while ensuring all features continue to work correctly.

The migration primarily involves:
1. Adding new fields to existing models
2. Updating endpoint paths with `/api/v1/` prefix
3. Adjusting path parameter names
4. Updating scrobble request structure
5. Handling typed paged responses

Most changes are additive (new fields) or structural (path changes), with few breaking changes to the core data flow.
