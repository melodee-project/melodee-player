# Prompt: Fix API Migration Critical Issues

## Context

You are working on a Kotlin/Android project at `/home/steven/source/melodee-player`. An API migration from legacy endpoints to v1 API was implemented and reviewed. The review found **4 BLOCKER issues** that must be fixed before merging to production.

**Review Document:** `docs/API-CHANGES-REVIEW.md` (read this first for full context)  
**Specification:** `docs/API-CHANGES.md` (the original migration spec)

## Your Task

Fix the 4 BLOCKER issues identified in the code review. All other issues (MAJOR/MINOR) can be deferred to future sprints. Focus on making the code production-ready.

---

## BLOCKER #1: Fix Scrobble Timestamp Units (CRITICAL)

### Problem
`ScrobbleManager.kt` is sending timestamps in **milliseconds** but the server likely expects **seconds** (1000× magnitude error). This will corrupt all scrobble data.

### Required Actions

1. **Verify server expectation:**
   - Check Swagger at http://localhost:5157/swagger/ 
   - Find `/api/v1/scrobble` endpoint
   - Look for `timestamp` and `playedDuration` field descriptions
   - Confirm units (should say "Unix timestamp in seconds")

2. **Fix ScrobbleManager.kt:**
   - File: `src/app/src/main/java/com/melodee/autoplayer/service/ScrobbleManager.kt`
   - Line 121: Change `timestamp = tracker.startTime.toDouble()` to `timestamp = (tracker.startTime / 1000.0)`
   - Line 167: Change `timestamp = System.currentTimeMillis().toDouble()` to `timestamp = (System.currentTimeMillis() / 1000.0)`
   - Line 168: Change `playedDuration = playedDuration.toDouble()` to `playedDuration = (playedDuration / 1000.0)`

3. **Add unit test:**
   - Create file: `src/app/src/test/java/com/melodee/autoplayer/service/ScrobbleRequestTest.kt`
   - Copy the complete test implementation from review doc section "Priority 1: Scrobble Timestamp Unit Test"
   - Run test: `./gradlew app:testDebugUnitTest --tests "ScrobbleRequestTest"`
   - Verify all tests pass

4. **Update documentation:**
   - Update `docs/API-CHANGES.md` section 0.3 with confirmed units
   - Add note: "VERIFIED: timestamp and playedDuration use Unix seconds (not milliseconds)"

### Expected Result
```kotlin
// Before (WRONG):
timestamp = tracker.startTime.toDouble(),  // 1703462400000

// After (CORRECT):
timestamp = (tracker.startTime / 1000.0),  // 1703462400.0
```

---

## BLOCKER #2: Fix Path Parameter Naming Inconsistency

### Problem
`MusicApi.kt` uses `{apiKey}` in two endpoints but `{id}` in others. Mixed usage suggests placeholders were never verified against actual server API. If server expects `{id}`, these calls will 404.

### Required Actions

1. **Verify server paths:**
   - Check Swagger at http://localhost:5157/swagger/
   - Find `GET /api/v1/playlists/{???}/songs` - note the exact parameter name
   - Find `POST /api/v1/songs/starred/{???}/{???}` - note both parameter names
   - Document actual names (likely `{id}` to match other endpoints)

2. **Fix MusicApi.kt (if server uses {id}):**
   - File: `src/app/src/main/java/com/melodee/autoplayer/data/api/MusicApi.kt`
   
   **Line 22-27:**
   ```kotlin
   // Change from:
   @GET("api/v1/playlists/{apiKey}/songs")
   suspend fun getPlaylistSongs(
       @Path("apiKey") playlistId: String,
   
   // To:
   @GET("api/v1/playlists/{id}/songs")
   suspend fun getPlaylistSongs(
       @Path("id") playlistId: String,
   ```
   
   **Line 67-71:**
   ```kotlin
   // Change from:
   @POST("api/v1/songs/starred/{apiKey}/{isStarred}")
   suspend fun favoriteSong(
       @Path("apiKey") songId: String,
   
   // To:
   @POST("api/v1/songs/starred/{id}/{isStarred}")
   suspend fun favoriteSong(
       @Path("id") songId: String,
   ```

3. **Add integration test:**
   - Create file: `src/app/src/test/java/com/melodee/autoplayer/api/RetrofitPathTest.kt`
   - Copy complete test from review doc section "Priority 2: Retrofit Path Generation Test"
   - Add dependency to `src/app/build.gradle.kts`:
     ```kotlin
     testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
     ```
   - Run test: `./gradlew app:testDebugUnitTest --tests "RetrofitPathTest"`

4. **Update documentation:**
   - Add to `docs/API-CHANGES.md` Phase 2.3 (Playlist Endpoints):
     ```
     **Verified:** ✅ Server uses {id} not {apiKey}
     ```
   - Add to Phase 2.7 (Song Endpoints):
     ```
     **Verified:** ✅ Server uses {id} not {apiKey}
     ```

### Expected Result
All endpoints use `{id}` consistently. Path generation test verifies correct substitution.

---

## BLOCKER #3: Add UUID Serialization Error Handling

### Problem
NetworkModule uses default Gson which can handle UUID but has no error handling for malformed/null UUIDs. Any server schema drift will cause unclear crashes.

### Required Actions

1. **Add explicit UUID TypeAdapter:**
   - File: `src/app/src/main/java/com/melodee/autoplayer/data/api/NetworkModule.kt`
   - Modify `createRetrofitInstance()` function starting at line 102
   
   **Replace this section (around line 178):**
   ```kotlin
   retrofit = Retrofit.Builder()
       .baseUrl(baseUrl)
       .client(okHttpClient)
       .addConverterFactory(GsonConverterFactory.create())
       .build()
   ```
   
   **With this:**
   ```kotlin
   // Create Gson with explicit UUID adapter for safe error handling
   val gson = GsonBuilder()
       .registerTypeAdapter(UUID::class.java, object : TypeAdapter<UUID>() {
           override fun write(out: JsonWriter, value: UUID?) {
               out.value(value?.toString())
           }
           
           override fun read(reader: JsonReader): UUID? {
               return try {
                   val str = reader.nextString()
                   if (str.isNullOrBlank()) {
                       Log.w("NetworkModule", "Received null/blank UUID, using empty UUID")
                       UUID(0, 0)  // Empty UUID for null/blank
                   } else {
                       UUID.fromString(str)
                   }
               } catch (e: IllegalArgumentException) {
                   Log.e("NetworkModule", "Invalid UUID format: ${e.message}, using empty UUID")
                   UUID(0, 0)  // Empty UUID for invalid format
               }
           }
       })
       .create()

   retrofit = Retrofit.Builder()
       .baseUrl(baseUrl)
       .client(okHttpClient)
       .addConverterFactory(GsonConverterFactory.create(gson))  // Use custom Gson
       .build()
   ```

2. **Add required imports at top of file:**
   ```kotlin
   import com.google.gson.GsonBuilder
   import com.google.gson.TypeAdapter
   import com.google.gson.stream.JsonReader
   import com.google.gson.stream.JsonWriter
   import java.util.UUID
   ```

3. **Add null UUID tests:**
   - File: `src/app/src/test/java/com/melodee/autoplayer/domain/ModelSerializationTest.kt`
   - Add the three test methods from review doc section "Priority 3: UUID Null Handling Test"
   - Run tests: `./gradlew app:testDebugUnitTest --tests "ModelSerializationTest"`

### Expected Result
- Gson gracefully handles null UUIDs (returns UUID(0,0))
- Gson gracefully handles invalid UUID formats (returns UUID(0,0) with log warning)
- Tests verify behavior

---

## BLOCKER #4: Add Parcelable Size Monitoring

### Problem
Song → Album → Artist parcelable graph could exceed Android's 1MB transaction limit with large playlists (500+ songs), causing TransactionTooLargeException.

### Required Actions

1. **Add size logging to Song.writeToParcel():**
   - File: `src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt`
   - Modify `Song.writeToParcel()` method starting at line 116
   
   **Replace:**
   ```kotlin
   override fun writeToParcel(parcel: Parcel, flags: Int) {
       parcel.writeString(id.toString())
       parcel.writeString(streamUrl)
       // ... rest of writes
   ```
   
   **With:**
   ```kotlin
   override fun writeToParcel(parcel: Parcel, flags: Int) {
       val startSize = parcel.dataSize()
       
       parcel.writeString(id.toString())
       parcel.writeString(streamUrl)
       parcel.writeString(title)
       parcel.writeParcelable(artist, flags)
       parcel.writeParcelable(album, flags)
       parcel.writeString(thumbnailUrl)
       parcel.writeString(imageUrl)
       parcel.writeDouble(durationMs)
       parcel.writeString(durationFormatted)
       parcel.writeByte(if (userStarred) 1 else 0)
       parcel.writeInt(userRating)
       parcel.writeInt(songNumber)
       parcel.writeInt(bitrate)
       parcel.writeInt(playCount)
       parcel.writeString(createdAt)
       parcel.writeString(updatedAt)
       parcel.writeString(genre)
       
       val endSize = parcel.dataSize()
       val size = endSize - startSize
       
       // Log warning if parcel is getting large
       if (size > 5000) {  // 5KB per song is concerning
           android.util.Log.w("Song", "Large parcel detected: $size bytes for song $id")
       }
   }
   ```

2. **Add required import:**
   ```kotlin
   import android.util.Log
   ```

3. **Add documentation comment:**
   - Above `data class Song` (line 77), add:
   ```kotlin
   /**
    * Song model with parcelable support.
    * 
    * WARNING: This class has a deep parcelable graph (Song → Album → Artist → List<String>).
    * When passing List<Song> via Intent, monitor parcel size to avoid TransactionTooLargeException.
    * Android's Binder has a 1MB transaction limit.
    * 
    * For large collections (>100 songs), consider passing only List<UUID> and fetching from ViewModel.
    */
   ```

### Expected Result
- Parcel size logged in debug builds
- Warning emitted for unusually large song parcels (>5KB)
- Documentation warns future developers

---

## Verification Steps

After fixing all 4 blockers, perform these verification steps:

### 1. Run All Tests
```bash
cd /home/steven/source/melodee-player/src
./gradlew app:testDebugUnitTest
```
**Expected:** All tests pass (should be 49+ tests now with new additions)

### 2. Check Test Output
Verify these specific tests pass:
- ✅ `ScrobbleRequestTest.scrobble_request_converts_timestamp_from_milliseconds_to_seconds`
- ✅ `ScrobbleRequestTest.scrobble_request_converts_playedDuration_from_milliseconds_to_seconds`
- ✅ `RetrofitPathTest.playlist_songs_endpoint_uses_correct_path_parameter`
- ✅ `RetrofitPathTest.favorite_song_endpoint_uses_correct_path_parameters`
- ✅ `ModelSerializationTest.user_deserializes_with_null_id_returns_empty_uuid`

### 3. Manual Testing Checklist

If you have access to a running API server:

```bash
# Start API server (if not already running)
cd /home/steven/source/melodee-player/api-server
dotnet run
```

Then test in the Android app:

- [ ] Login successfully
- [ ] View playlists (verify no 404)
- [ ] Open a playlist and view songs (verify no 404)
- [ ] Favorite/unfavorite a song (verify no 404)
- [ ] Play a song for >10 seconds (check logs for scrobble request)
- [ ] Verify scrobble timestamp in logs looks like `1703462400.0` not `1703462400000.0`

### 4. Check Logs

Run app with verbose logging and look for:

```
ScrobbleManager: Scrobble Request (nowPlaying): timestamp=1703462400.0, playedDuration=0.0
```

**Verify timestamp is in reasonable range:**
- ✅ Should be ~1700000000 to 1900000000 (year 2023-2030)
- ❌ Should NOT be ~1700000000000 (that's milliseconds)

### 5. Review Changes

Use git to review what changed:
```bash
git diff src/app/src/main/java/com/melodee/autoplayer/service/ScrobbleManager.kt
git diff src/app/src/main/java/com/melodee/autoplayer/data/api/MusicApi.kt
git diff src/app/src/main/java/com/melodee/autoplayer/data/api/NetworkModule.kt
git diff src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt
```

Ensure only the specific lines mentioned were changed (surgical fixes).

---

## Files to Modify

### Must Edit:
1. `src/app/src/main/java/com/melodee/autoplayer/service/ScrobbleManager.kt` (3 lines)
2. `src/app/src/main/java/com/melodee/autoplayer/data/api/MusicApi.kt` (4 lines)
3. `src/app/src/main/java/com/melodee/autoplayer/data/api/NetworkModule.kt` (add UUID adapter)
4. `src/app/src/main/java/com/melodee/autoplayer/domain/model/Models.kt` (add size logging)

### Must Create:
5. `src/app/src/test/java/com/melodee/autoplayer/service/ScrobbleRequestTest.kt` (new file)
6. `src/app/src/test/java/com/melodee/autoplayer/api/RetrofitPathTest.kt` (new file)

### Must Update (add tests):
7. `src/app/src/test/java/com/melodee/autoplayer/domain/ModelSerializationTest.kt` (add 3 tests)

### Must Update (documentation):
8. `docs/API-CHANGES.md` (add verification notes)

### May Need to Edit:
9. `src/app/build.gradle.kts` (add mockwebserver dependency if not present)

---

## Expected Outcomes

After completing this work:

### Code Changes
- ✅ All scrobble timestamps converted from ms → seconds
- ✅ All API endpoints use consistent path parameter names
- ✅ UUID deserialization has explicit error handling
- ✅ Parcelable size is monitored and documented

### Test Coverage
- ✅ 3+ new test files added
- ✅ 10+ new test cases added
- ✅ All tests passing
- ✅ Critical paths verified

### Documentation
- ✅ Swagger verification documented in spec
- ✅ Confirmed units documented
- ✅ Parcelable risks documented in code comments

### Production Readiness
- ✅ No blocker issues remaining
- ✅ Migration is safe to merge
- ✅ Scrobble data will be correct
- ✅ All endpoints will work (no 404s)

---

## Success Criteria

You are done when:

1. ✅ All 4 BLOCKER issues are fixed
2. ✅ All unit tests pass (including new tests)
3. ✅ Test count increased by at least 10 tests
4. ✅ Manual testing shows scrobbles work with correct timestamps
5. ✅ Manual testing shows playlists and favoriting work (no 404s)
6. ✅ Git diff shows only surgical changes (no unrelated modifications)
7. ✅ Documentation updated with verification notes

---

## Notes and Tips

### Swagger Access
If API server is not running locally, you may need to:
```bash
cd /home/steven/source/melodee-player/api-server
dotnet restore
dotnet run
```
Then open: http://localhost:5157/swagger/

### If Swagger Verification Shows Different Parameter Names
If Swagger shows the server actually uses `{apiKey}` (not `{id}`), then:
- Update docs/API-CHANGES.md to document this is intentional
- Add comment in MusicApi.kt explaining the difference
- Keep the current code but verify it matches Swagger exactly

### If Tests Fail
- Check imports are correct
- Verify Gson configuration is valid
- Run with `--stacktrace` flag: `./gradlew app:testDebugUnitTest --stacktrace`
- Check that new test files are in correct package structure

### Time Estimate
- BLOCKER #1 (timestamps): 30 minutes
- BLOCKER #2 (paths): 30 minutes
- BLOCKER #3 (UUID): 45 minutes
- BLOCKER #4 (parcel): 30 minutes
- Testing & verification: 60 minutes
- **Total: 3-4 hours**

---

## Optional: MAJOR Issues (Can Defer)

If time permits, consider fixing these MAJOR issues as well:

### MAJOR #5: Remove deprecated scrobbleType field
- Check if server needs the string field
- If not needed, remove from ScrobbleRequest and ScrobbleManager

### MAJOR #6: Add pagination boundary tests
- Copy PaginationTest.kt from review doc
- Test empty, first, last, middle pages

### MAJOR #7: Add rating validation
- Create RatingConstants.kt with MIN/MAX values
- Add init block validation to Song/Artist/Album

**But these are NOT blocking merge.** Focus on the 4 blockers first.

---

## Questions?

If anything is unclear:
1. Read the full review doc: `docs/API-CHANGES-REVIEW.md`
2. Read the original spec: `docs/API-CHANGES.md`
3. Check existing tests for patterns: `src/app/src/test/java/com/melodee/autoplayer/domain/ModelSerializationTest.kt`

---

## Summary

**Priority:** HIGH (blocking production deployment)  
**Effort:** 3-4 hours  
**Risk:** LOW (surgical fixes with test coverage)  
**Impact:** HIGH (prevents data corruption and 404 errors)

Focus on correctness over speed. Each blocker could cause production incidents if not fixed properly. Follow the verification steps carefully.

Good luck! 🚀
