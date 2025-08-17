# Artist Browse Enhancements - Task List

## Overview
This document outlines the tasks needed to implement two new features for the Home screen: "Browse Albums" and "Browse Songs" buttons that appear when a specific artist is selected (not "Everyone").

## Current State Analysis
- **Home Screen Location**: `src/app/src/main/java/com/melodee/autoplayer/presentation/ui/home/HomeScreen.kt:223-234`
- **Artist Selection State**: Managed by `selectedArtist` StateFlow in HomeViewModel
- **UI Pattern**: "Everyone" vs specific artist selection with ArtistAutocomplete component
- **Data Models**: Album, Song, Artist models exist in `domain/model/Models.kt` with required fields
- **API Support**: MusicApi has endpoints for artist songs but needs album endpoints

## Critical Bug Fixes - Search/Filter Logic

### Current Broken Behavior (HomeViewModel.kt:743-763, 408-468)
The current implementation has backwards logic for song filtering when artist selection changes. The problems are:

1. **Line 752-753**: When artist is selected with no search query, it loads all artist songs (incorrect)
2. **Lines 408-426**: Shows all songs for selected artist when query is blank (incorrect)
3. **Missing Logic**: No automatic filtering when artist changes while search text exists

### Expected Workflow (MUST FIX)
1. **No artist selected, user enters text**: Show all songs from all artists matching search text (ilike)
2. **User selects artist**: Songs should immediately filter to show only songs from that artist matching any existing search text. If no search text, show empty list.
3. **Artist selected, no search text, no songs displayed**: User selects artist first, then enters text - songs should show only from selected artist matching search text

### Required Fixes
- [ ] **Fix selectArtist() logic in HomeViewModel.kt:743-763**:
  - When artist is selected with existing search text: Re-filter current results to only show that artist's songs
  - When artist is selected with no search text: Show empty list (no automatic loading)
  - When artist is cleared back to "Everyone": Re-search with current query (if any) across all artists
- [ ] **Fix performSearch() logic in HomeViewModel.kt:408-468**:
  - Remove lines 408-426 that auto-load all artist songs when query is blank
  - Only perform search when query is not blank
  - When artist is selected + search text: use searchSongsWithArtist()
  - When no artist selected + search text: use searchSongs() for all artists

## Implementation Tasks

### 1. Backend API Enhancement
- [x] **1.1** ✅ **API ENDPOINTS ALREADY EXIST** - Server has all required endpoints:
  - ✅ `GET /artists/{id}/albums` - Get albums for artist (ArtistsController.cs:161-211)
  - ✅ `GET /playlists/{apiKey}/songs` - Get songs for playlist (PlaylistsController.cs:95-142)
  - ⚠️ **MISSING**: Dedicated album songs endpoint (use playlist pattern)
- [ ] **1.2** Add missing Android client API endpoints to `MusicApi.kt`:
  - `getArtistAlbums(artistId: String, page: Int)` - Maps to existing server endpoint
  - `getAlbumSongs(albumId: String, page: Int)` - Server needs this endpoint pattern
- [ ] **1.3** Update `MusicRepository.kt` to include album methods:
  - `getArtistAlbums()` method with Flow<PaginatedResponse<Album>>
  - `getAlbumSongs()` method with Flow<PaginatedResponse<Song>>

#### Server API Analysis (✅ READY FOR 100K+ SCALE)
- **Pagination**: Server defaults to 50 items/page (optimal for scale)
- **Search**: Uses normalized fields (`TitleNormalized`) for performance
- **Sorting**: Albums sorted by CreatedAt DESC (ArtistsController.cs:196)
- **Filtering**: Built-in artist filtering support

### 2. UI Components and Navigation
- [ ] **2.1** Create new composable screens:
  - `ArtistAlbumsScreen.kt` - Shows albums for selected artist in grid/list
  - `AlbumSongsScreen.kt` - Shows songs for selected album with play all functionality
- [ ] **2.2** Create ViewModels:
  - `ArtistAlbumsViewModel.kt` - Manages album loading and state
  - `AlbumSongsViewModel.kt` - Manages album songs and queue operations
- [ ] **2.3** Add navigation routes to MainActivity:
  - Route: `"artist/{artistId}/albums"` for ArtistAlbumsScreen
  - Route: `"album/{albumId}/songs"` for AlbumSongsScreen

### 3. Home Screen UI Updates
- [ ] **3.1** Modify `HomeScreen.kt` around line 236 (after ArtistAutocomplete):
  - Add conditional visibility Row with two buttons when `selectedArtist != null`
  - "Browse Albums" button - navigates to artist albums screen
  - "Browse Songs" button - navigates to enhanced song list (existing functionality)
- [ ] **3.2** Update button styling to match existing UI patterns:
  - Use OutlinedButton or similar from Material3
  - Consistent spacing and padding with existing elements
  - Add appropriate icons (album icon, music note icon)

### 4. Enhanced Song Browse Experience
- [ ] **4.1** Create dedicated `ArtistSongsScreen.kt`:
  - Infinite scroll song list for selected artist
  - Star/unstar functionality per song
  - "Play Now" action (replaces queue)
  - "Add to Queue" action (appends to queue)
  - Search/filter within artist songs
- [ ] **4.2** Update navigation for "Browse Songs" button:
  - Navigate to dedicated artist songs screen instead of using search

### 5. Album Browse Implementation
- [ ] **5.1** Implement `ArtistAlbumsScreen.kt`:
  - Grid or list layout showing album covers
  - Album title, year, and song count
  - Sort by year descending (most recent first)
  - Tap album to navigate to album songs screen
- [ ] **5.2** Implement `AlbumSongsScreen.kt`:
  - List of all songs in album
  - "Play All" button at top - clears queue and adds all album songs
  - Individual song actions (star, play now, add to queue)
  - Album header with cover, title, year, total duration

### 6. Queue Management Integration
- [ ] **6.1** Update `MusicService.kt` queue operations:
  - Add `ACTION_PLAY_ALBUM` for "Play All" functionality
  - Add `ACTION_ADD_ALBUM_TO_QUEUE` for queue append operations
  - Ensure proper playback context tracking for albums
- [ ] **6.2** Update ViewModels to handle album playback:
  - Integrate with existing MusicService patterns
  - Proper state management for album vs song vs playlist playback

### 7. State Management and Data Flow
- [ ] **7.1** Update `HomeViewModel.kt`:
  - Add navigation actions for Browse Albums/Songs buttons
  - Ensure selected artist state is properly shared with new screens
- [ ] **7.2** Implement proper state sharing:
  - Pass selectedArtist between screens via navigation arguments
  - Maintain consistent state across Home -> Albums -> Songs flow

### 8. UI Polish and Consistency
- [ ] **8.1** Ensure UI consistency across all new screens:
  - Match existing loading states, error handling
  - Use same AsyncImage patterns for album covers
  - Consistent typography and color scheme
  - Proper back navigation handling
- [ ] **8.2** Add proper loading and error states:
  - Loading indicators for album/song fetching
  - Empty state when artist has no albums/songs
  - Error handling for network failures

### 9. Testing and Integration
- [ ] **9.1** Test complete user flow:
  - Home -> Select Artist -> Browse Albums -> Select Album -> Play All
  - Home -> Select Artist -> Browse Songs -> Play/Queue individual songs
- [ ] **9.2** Verify edge cases:
  - Artist with no albums
  - Album with no songs
  - Network error handling
  - Back navigation behavior

### 10. Performance Optimization
- [ ] **10.1** Implement efficient data loading:
  - Pagination for artist albums and album songs
  - Image loading optimization for album grids
  - Proper coroutine scope management in new ViewModels
- [ ] **10.2** Memory management:
  - Proper cleanup in ViewModel onCleared()
  - Efficient list recycling for large album/song collections

## Dependencies and Considerations
- **Material3 Icons**: May need additional icons for albums/songs buttons
- **Navigation**: Uses Jetpack Compose Navigation with existing patterns
- **State Management**: Follows existing StateFlow patterns in ViewModels
- **Service Integration**: Leverages existing MusicService architecture
- **✅ API Server**: All major endpoints exist - only client-side implementation needed

## Critical Performance Updates (From Server Analysis)

### Android Client Page Size Mismatch
- **Current**: Client requests 20 items/page (MusicApi.kt defaults)
- **Server optimized**: 50 items/page (SearchController.cs:56,123)
- **Impact**: Inefficient API usage for large datasets
- **Fix**: Update all `pageSize: Int = 20` to `pageSize: Int = 50` in MusicApi.kt

### Server API Endpoints Ready for Scale
```csharp
// ✅ AVAILABLE: Artist albums endpoint
GET /artists/{id}/albums?page={page}&pageSize={pageSize}
// Returns: PaginatedResponse<Album> sorted by year DESC

// ✅ AVAILABLE: Artist songs with search
GET /artists/{id}/songs?q={query}&page={page}&pageSize={pageSize}
// Returns: PaginatedResponse<Song> with optional title search

// ⚠️ NEEDED: Direct album songs endpoint
// Current workaround: Use playlist pattern for albums
```

### Search Performance (Server Optimized)
- **TitleNormalized indexing**: Efficient search on 1.8M songs
- **Artist filtering**: Built-in `filterByArtistApiKey` parameter  
- **Pagination**: Robust metadata with hasNext/hasPrevious
- **Caching**: Server implements ETag caching (ETagFilter.cs)

## Implementation Priority

### Phase 1: Critical Fixes
1. **CRITICAL**: Fix search/filter logic bugs in HomeViewModel.kt
2. **CRITICAL**: Update page sizes from 20→50 in MusicApi.kt
3. **CRITICAL**: Add LazyColumn item keys

### Phase 2: API Integration
1. **HIGH**: Add artist albums endpoint to Android client (server ready)
2. **HIGH**: Implement Browse Albums/Songs UI buttons
3. **HIGH**: Create ArtistAlbumsScreen.kt and navigation

### Phase 3: Features & Polish
1. **MEDIUM**: Implement AlbumSongsScreen.kt with Play All
2. **MEDIUM**: Queue management integration  
3. **LOW**: Performance optimization and virtual scrolling

## Server Status: ✅ READY FOR SCALE
- All pagination optimized for 100K+ artists
- Search performance ready for 1.8M songs
- Normalized indexing implemented
- ETag caching for efficiency
- **No server changes needed** - client optimization focus

