# Android Auto Enhancement Implementation Checklist

This document outlines the phased implementation plan for enhancing the Melodee Android Auto integration with high-priority features.

## 📋 Overview

**Goal**: Transform Android Auto integration from functional to exceptional by adding:
1. Queue Management Callbacks
2. Custom Favorite Action
3. Enhanced Voice Command Parsing

**Estimated Total Time**: 12-17 hours
**Implementation Phases**: 3 phases over 2-3 sprints

---

## 🎯 Phase 1: Custom Favorite Action (PRIORITY: HIGH)
**Estimated Time**: 2-3 hours
**Complexity**: Low
**Dependencies**: None

### ✅ Tasks

#### Core Implementation
- [ ] **1.1** Add custom action constants to MusicService
  ```kotlin
  companion object {
      const val ACTION_ADD_FAVORITE = "ACTION_ADD_FAVORITE"
      const val ACTION_REMOVE_FAVORITE = "ACTION_REMOVE_FAVORITE"
  }
  ```
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/MusicService.kt`
  - **Location**: Add to existing companion object

- [ ] **1.2** Create favorite action drawable resources
  - **Files**: 
    - `app/src/main/res/drawable/ic_favorite_filled.xml`
    - `app/src/main/res/drawable/ic_favorite_border.xml`
  - **Status**: ✅ Already exist in project

- [ ] **1.3** Modify `updateMediaSessionPlaybackState()` to include favorite action
  ```kotlin
  val favoriteAction = if (currentSong?.userStarred == true) {
      PlaybackStateCompat.CustomAction.Builder(
          ACTION_REMOVE_FAVORITE, "Remove Favorite", R.drawable.ic_favorite_filled
      ).build()
  } else {
      PlaybackStateCompat.CustomAction.Builder(
          ACTION_ADD_FAVORITE, "Add Favorite", R.drawable.ic_favorite_border
      ).build()
  }
  ```
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/MusicService.kt`
  - **Function**: `updateMediaSessionPlaybackState()`

- [ ] **1.4** Add `onCustomAction()` callback to MediaSession setup
  ```kotlin
  override fun onCustomAction(action: String?, extras: Bundle?) {
      when (action) {
          ACTION_ADD_FAVORITE -> favoriteSongFromAndroidAuto(currentSong, true)
          ACTION_REMOVE_FAVORITE -> favoriteSongFromAndroidAuto(currentSong, false)
      }
  }
  ```
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/MusicService.kt`
  - **Location**: Inside `setupMediaSession()` MediaSession callback

- [ ] **1.5** Implement `favoriteSongFromAndroidAuto()` function
  ```kotlin
  private fun favoriteSongFromAndroidAuto(song: Song?, favorite: Boolean) {
      song?.let { /* API call + state update */ }
  }
  ```
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/MusicService.kt`
  - **Location**: Add as private function

#### Testing & Validation
- [ ] **1.6** Test favorite button appears in Android Auto controls
- [ ] **1.7** Test button state changes based on song's favorite status
- [ ] **1.8** Test API call is made when button is pressed
- [ ] **1.9** Test button updates immediately after state change

#### Documentation
- [ ] **1.10** Update API integration docs with favorite endpoint usage
- [ ] **1.11** Add voice command examples for favoriting

---

## ⚡ Phase 2: Enhanced Voice Command Parsing (PRIORITY: HIGH)
**Estimated Time**: 6-8 hours
**Complexity**: High
**Dependencies**: Phase 1 complete

### ✅ Tasks

#### Core Parser Implementation
- [ ] **2.1** Create `VoiceCommandParser` class
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/VoiceCommandParser.kt` (new file)
  - **Content**: Command patterns, parsing logic, and sealed classes

- [ ] **2.2** Define command pattern constants
  ```kotlin
  private val ARTIST_PATTERNS = listOf("play (.+) by (.+)", "play artist (.+)")
  private val ALBUM_PATTERNS = listOf("play album (.+)", "play the album (.+)")
  private val CONTROL_PATTERNS = listOf("next song" to "skip_next")
  ```

- [ ] **2.3** Create `VoiceCommand` sealed classes
  ```kotlin
  sealed class VoiceCommand {
      data class Song(val query: String) : VoiceCommand()
      data class Artist(val name: String) : VoiceCommand()
      // ... other command types
  }
  ```

#### Voice Command Handlers
- [ ] **2.4** Replace existing `onPlayFromSearch()` implementation
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/MusicService.kt`
  - **Function**: `onPlayFromSearch(query: String?, extras: Bundle?)`

- [ ] **2.5** Implement `handleControlCommand(action: String)`
  - **Commands**: skip_next, skip_previous, shuffle_enable/disable, repeat_enable/disable

- [ ] **2.6** Implement `handleContextualCommand(type: String)`
  - **Commands**: favorites, recent, shuffle_all

- [ ] **2.7** Implement `handleArtistCommand(artistName: String)`
  - **Logic**: Search for artist → Get artist songs → Play results

- [ ] **2.8** Implement `handleAlbumCommand(albumName: String)`
  - **Logic**: Search for album songs → Play results

- [ ] **2.9** Implement `handleSongSearch(query: String)` (refactor existing)
  - **Logic**: Direct song search (fallback for unrecognized commands)

#### API Integration Enhancements
- [ ] **2.10** Add support for special search queries
  - **API Calls**: `starred:true`, `recent:true`, `random:true`
  - **File**: Update search endpoints in `MusicRepository.kt`

- [ ] **2.11** Implement `playSearchResults()` helper function
  - **Logic**: Cache results → Set queue → Start playback

#### Voice UI Updates  
- [ ] **2.12** Update `searchable.xml` with enhanced voice examples
  ```xml
  <voice-search-examples>
      <example text="Play my favorites" />
      <example text="Play Beatles" />
      <example text="Next song" />
  </voice-search-examples>
  ```
  - **File**: `app/src/main/res/xml/searchable.xml`

#### Testing & Validation
- [ ] **2.13** Test basic voice commands ("play song name")
- [ ] **2.14** Test artist commands ("play Beatles")
- [ ] **2.15** Test album commands ("play Abbey Road album")
- [ ] **2.16** Test control commands ("next song", "shuffle on")
- [ ] **2.17** Test contextual commands ("play my favorites")
- [ ] **2.18** Test fallback behavior for unrecognized commands

---

## 🎵 Phase 3: Queue Management Callbacks (PRIORITY: HIGH)  
**Estimated Time**: 4-6 hours
**Complexity**: Medium
**Dependencies**: Phase 2 complete

### ✅ Tasks

#### MediaSession Queue Callbacks
- [ ] **3.1** Add `onAddQueueItem(description: MediaDescriptionCompat?)` callback
  ```kotlin
  override fun onAddQueueItem(description: MediaDescriptionCompat?) {
      description?.mediaId?.let { mediaId ->
          val song = findSongByMediaId(mediaId)
          song?.let { queueManager.addToQueue(it) }
      }
  }
  ```
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/MusicService.kt`
  - **Location**: Inside MediaSession callback

- [ ] **3.2** Add `onAddQueueItem(description, index)` overload for position-specific adds
- [ ] **3.3** Add `onRemoveQueueItem(description: MediaDescriptionCompat?)` callback  
- [ ] **3.4** Add `onSkipToQueueItem(id: Long)` callback

#### QueueManager Enhancements
- [ ] **3.5** Add `addToQueue(song: Song, index: Int = -1)` function
  ```kotlin
  fun addToQueue(song: Song, index: Int = -1) {
      val currentQueue = _currentQueue.value.toMutableList()
      if (index >= 0) currentQueue.add(index, song) else currentQueue.add(song)
      _currentQueue.value = currentQueue
  }
  ```
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/QueueManager.kt`

- [ ] **3.6** Add `removeFromQueue(mediaId: String)` function
- [ ] **3.7** Add `skipToIndex(index: Int)` function  
- [ ] **3.8** Update existing functions to handle queue modifications

#### Queue Display & Sync
- [ ] **3.9** Implement `updateMediaSessionQueue()` function
  ```kotlin
  private fun updateMediaSessionQueue() {
      val queue = queueManager.currentQueue.value.mapIndexed { index, song ->
          MediaSessionCompat.QueueItem(/* MediaDescription */, index.toLong())
      }
      mediaSession?.setQueue(queue)
  }
  ```
  - **File**: `app/src/main/java/com/melodee/autoplayer/service/MusicService.kt`

- [ ] **3.10** Add `findSongByMediaId(mediaId: String)` helper function
- [ ] **3.11** Call `updateMediaSessionQueue()` after queue modifications
- [ ] **3.12** Ensure queue state persistence across app restarts

#### Testing & Validation
- [ ] **3.13** Test "Add to Queue" option appears on songs in Android Auto
- [ ] **3.14** Test songs are added to end of queue correctly
- [ ] **3.15** Test songs can be added at specific positions
- [ ] **3.16** Test songs can be removed from queue
- [ ] **3.17** Test queue order updates correctly in Android Auto
- [ ] **3.18** Test skip to queue item functionality
- [ ] **3.19** Test queue state survives app restart

---

## 🔧 Integration & Polish Phase
**Estimated Time**: 2-3 hours
**Complexity**: Low
**Dependencies**: All phases complete

### ✅ Tasks

#### Cross-Feature Integration
- [ ] **4.1** Ensure queue management works with voice commands
- [ ] **4.2** Ensure favorites work within queue context
- [ ] **4.3** Test all features work together seamlessly

#### Error Handling & Edge Cases
- [ ] **4.4** Add error handling for failed API calls
- [ ] **4.5** Add error handling for invalid voice commands
- [ ] **4.6** Add error handling for queue operations on empty queue
- [ ] **4.7** Add graceful degradation when offline

#### Performance Optimization
- [ ] **4.8** Optimize voice command parsing performance
- [ ] **4.9** Add caching for frequent voice command results
- [ ] **4.10** Optimize queue update operations

#### Documentation & Cleanup
- [ ] **4.11** Update code documentation and comments
- [ ] **4.12** Update user-facing documentation
- [ ] **4.13** Clean up debug logging for production
- [ ] **4.14** Update version numbers and changelog

---

## 📊 Progress Tracking

### Phase Completion Status
- [ ] **Phase 1**: Custom Favorite Action (0/10 tasks complete)
- [ ] **Phase 2**: Enhanced Voice Commands (0/18 tasks complete)  
- [ ] **Phase 3**: Queue Management (0/19 tasks complete)
- [ ] **Phase 4**: Integration & Polish (0/14 tasks complete)

### Overall Progress: 0/61 tasks complete (0%)

---

## 🚦 Testing Strategy

### Manual Testing Checklist
- [ ] Test on physical Android Auto head unit
- [ ] Test with Android Auto simulator 
- [ ] Test voice commands in noisy environment
- [ ] Test with various music libraries (small/large)
- [ ] Test network connectivity issues

### Automated Testing
- [ ] Unit tests for VoiceCommandParser
- [ ] Unit tests for QueueManager enhancements
- [ ] Integration tests for MediaSession callbacks
- [ ] API integration tests

### User Acceptance Criteria
- [ ] Voice commands work 95% of the time for clear speech
- [ ] Queue operations complete within 2 seconds
- [ ] Favorite toggle provides immediate visual feedback
- [ ] All features work without looking at screen (safety)

---

## 📝 Notes

### Known Limitations
- Voice command parsing may need fine-tuning based on real-world usage
- Queue management is limited by Android Auto UI constraints
- Custom actions are limited to 3-4 buttons in Android Auto interface

### Future Enhancements (Post-Implementation)
- Smart playlist creation from voice commands
- Cross-device queue synchronization
- Advanced recommendation voice commands
- Multiple language support for voice commands

### Dependencies
- Requires Android Auto-enabled vehicle or simulator for testing
- Requires Google Play Services for voice recognition
- Requires network connectivity for API calls

---

**Last Updated**: 2025-08-10
**Document Version**: 1.0
**Implementation Target**: Sprint 2025.1-2025.2