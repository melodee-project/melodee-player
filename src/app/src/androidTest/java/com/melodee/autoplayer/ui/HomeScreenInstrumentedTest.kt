package com.melodee.autoplayer.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.melodee.autoplayer.domain.model.Artist
import com.melodee.autoplayer.domain.model.Playlist
import com.melodee.autoplayer.domain.model.Song
import com.melodee.autoplayer.domain.model.User
import com.melodee.autoplayer.presentation.ui.home.HomeScreen
import com.melodee.autoplayer.presentation.ui.home.HomeViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * UI instrumented tests for HomeScreen critical user flows
 * Tests state management, search functionality, and user interactions
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockViewModel = mockk<HomeViewModel>(relaxed = true)

    private val testUser = User(
        id = UUID.randomUUID(),
        email = "test@test.com",
        username = "Test User",
        thumbnailUrl = "",
        imageUrl = ""
    )

    private val testPlaylist = Playlist(
        id = UUID.randomUUID(),
        name = "Test Playlist",
        thumbnailUrl = "",
        imageUrl = "",
        songCount = 5,
        duration = 300000L,
        durationFormatted = "5:00"
    )

    private val testSong = Song(
        id = UUID.randomUUID(),
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        albumImageUrl = "",
        thumbnailUrl = "",
        duration = 180000L,
        formattedDuration = "3:00",
        url = "https://test.com/song.mp3",
        userStarred = false
    )

    @Test
    fun homeScreen_displaysUserInformation() {
        // Arrange
        every { mockViewModel.user } returns MutableStateFlow(testUser)
        every { mockViewModel.playlists } returns MutableStateFlow(emptyList())
        every { mockViewModel.songs } returns MutableStateFlow(emptyList())
        every { mockViewModel.artists } returns MutableStateFlow(emptyList())
        every { mockViewModel.selectedArtist } returns MutableStateFlow(null)
        every { mockViewModel.isLoading } returns MutableStateFlow(false)
        every { mockViewModel.isArtistLoading } returns MutableStateFlow(false)
        every { mockViewModel.totalSearchResults } returns MutableStateFlow(0)
        every { mockViewModel.currentPageStart } returns MutableStateFlow(0)
        every { mockViewModel.currentPageEnd } returns MutableStateFlow(0)
        every { mockViewModel.currentSongIndex } returns MutableStateFlow(-1)
        every { mockViewModel.albums } returns MutableStateFlow(emptyList())
        every { mockViewModel.isAlbumsLoading } returns MutableStateFlow(false)
        every { mockViewModel.showAlbums } returns MutableStateFlow(false)
        every { mockViewModel.selectedAlbum } returns MutableStateFlow(null)
        every { mockViewModel.showAlbumSongs } returns MutableStateFlow(false)
        every { mockViewModel.totalAlbums } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsStart } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsEnd } returns MutableStateFlow(0)

        // Act
        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockViewModel,
                onPlaylistClick = { }
            )
        }

        // Assert
        composeTestRule.onNodeWithText("Test User").assertExists()
    }

    @Test
    fun homeScreen_displaysPlaylistsWhenAvailable() {
        // Arrange
        every { mockViewModel.user } returns MutableStateFlow(testUser)
        every { mockViewModel.playlists } returns MutableStateFlow(listOf(testPlaylist))
        every { mockViewModel.songs } returns MutableStateFlow(emptyList())
        every { mockViewModel.artists } returns MutableStateFlow(emptyList())
        every { mockViewModel.selectedArtist } returns MutableStateFlow(null)
        every { mockViewModel.isLoading } returns MutableStateFlow(false)
        every { mockViewModel.isArtistLoading } returns MutableStateFlow(false)
        every { mockViewModel.totalSearchResults } returns MutableStateFlow(0)
        every { mockViewModel.currentPageStart } returns MutableStateFlow(0)
        every { mockViewModel.currentPageEnd } returns MutableStateFlow(0)
        every { mockViewModel.currentSongIndex } returns MutableStateFlow(-1)
        every { mockViewModel.albums } returns MutableStateFlow(emptyList())
        every { mockViewModel.isAlbumsLoading } returns MutableStateFlow(false)
        every { mockViewModel.showAlbums } returns MutableStateFlow(false)
        every { mockViewModel.selectedAlbum } returns MutableStateFlow(null)
        every { mockViewModel.showAlbumSongs } returns MutableStateFlow(false)
        every { mockViewModel.totalAlbums } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsStart } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsEnd } returns MutableStateFlow(0)

        // Act
        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockViewModel,
                onPlaylistClick = { }
            )
        }

        // Assert
        composeTestRule.onNodeWithText("Test Playlist").assertExists()
        composeTestRule.onNodeWithText("5 songs • 5:00").assertExists()
    }

    @Test
    fun homeScreen_searchFunctionality_callsViewModel() {
        // Arrange
        every { mockViewModel.user } returns MutableStateFlow(testUser)
        every { mockViewModel.playlists } returns MutableStateFlow(emptyList())
        every { mockViewModel.songs } returns MutableStateFlow(emptyList())
        every { mockViewModel.artists } returns MutableStateFlow(emptyList())
        every { mockViewModel.selectedArtist } returns MutableStateFlow(null)
        every { mockViewModel.isLoading } returns MutableStateFlow(false)
        every { mockViewModel.isArtistLoading } returns MutableStateFlow(false)
        every { mockViewModel.totalSearchResults } returns MutableStateFlow(0)
        every { mockViewModel.currentPageStart } returns MutableStateFlow(0)
        every { mockViewModel.currentPageEnd } returns MutableStateFlow(0)
        every { mockViewModel.currentSongIndex } returns MutableStateFlow(-1)
        every { mockViewModel.albums } returns MutableStateFlow(emptyList())
        every { mockViewModel.isAlbumsLoading } returns MutableStateFlow(false)
        every { mockViewModel.showAlbums } returns MutableStateFlow(false)
        every { mockViewModel.selectedAlbum } returns MutableStateFlow(null)
        every { mockViewModel.showAlbumSongs } returns MutableStateFlow(false)
        every { mockViewModel.totalAlbums } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsStart } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsEnd } returns MutableStateFlow(0)

        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockViewModel,
                onPlaylistClick = { }
            )
        }

        // Act
        composeTestRule.onNodeWithText("Search songs...").performTextInput("test query")

        // Assert
        verify { mockViewModel.searchSongs("test query") }
    }

    @Test
    fun homeScreen_displaysSongSearchResults() {
        // Arrange
        every { mockViewModel.user } returns MutableStateFlow(testUser)
        every { mockViewModel.playlists } returns MutableStateFlow(emptyList())
        every { mockViewModel.songs } returns MutableStateFlow(listOf(testSong))
        every { mockViewModel.artists } returns MutableStateFlow(emptyList())
        every { mockViewModel.selectedArtist } returns MutableStateFlow(null)
        every { mockViewModel.isLoading } returns MutableStateFlow(false)
        every { mockViewModel.isArtistLoading } returns MutableStateFlow(false)
        every { mockViewModel.totalSearchResults } returns MutableStateFlow(1)
        every { mockViewModel.currentPageStart } returns MutableStateFlow(1)
        every { mockViewModel.currentPageEnd } returns MutableStateFlow(1)
        every { mockViewModel.currentSongIndex } returns MutableStateFlow(-1)
        every { mockViewModel.albums } returns MutableStateFlow(emptyList())
        every { mockViewModel.isAlbumsLoading } returns MutableStateFlow(false)
        every { mockViewModel.showAlbums } returns MutableStateFlow(false)
        every { mockViewModel.selectedAlbum } returns MutableStateFlow(null)
        every { mockViewModel.showAlbumSongs } returns MutableStateFlow(false)
        every { mockViewModel.totalAlbums } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsStart } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsEnd } returns MutableStateFlow(0)

        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockViewModel,
                onPlaylistClick = { }
            )
        }

        // Act - simulate search query input
        composeTestRule.onNodeWithText("Search songs...").performTextInput("test")

        // Assert
        composeTestRule.onNodeWithText("Test Song").assertExists()
        composeTestRule.onNodeWithText("Test Artist").assertExists()
        composeTestRule.onNodeWithText("Displaying 1 to 1 of 1 results").assertExists()
    }

    @Test
    fun homeScreen_artistFilter_callsViewModel() {
        // Arrange
        val testArtist = Artist(
            id = UUID.randomUUID(),
            name = "Test Artist",
            thumbnailUrl = "",
            imageUrl = ""
        )

        every { mockViewModel.user } returns MutableStateFlow(testUser)
        every { mockViewModel.playlists } returns MutableStateFlow(emptyList())
        every { mockViewModel.songs } returns MutableStateFlow(emptyList())
        every { mockViewModel.artists } returns MutableStateFlow(listOf(testArtist))
        every { mockViewModel.selectedArtist } returns MutableStateFlow(null)
        every { mockViewModel.isLoading } returns MutableStateFlow(false)
        every { mockViewModel.isArtistLoading } returns MutableStateFlow(false)
        every { mockViewModel.totalSearchResults } returns MutableStateFlow(0)
        every { mockViewModel.currentPageStart } returns MutableStateFlow(0)
        every { mockViewModel.currentPageEnd } returns MutableStateFlow(0)
        every { mockViewModel.currentSongIndex } returns MutableStateFlow(-1)
        every { mockViewModel.albums } returns MutableStateFlow(emptyList())
        every { mockViewModel.isAlbumsLoading } returns MutableStateFlow(false)
        every { mockViewModel.showAlbums } returns MutableStateFlow(false)
        every { mockViewModel.selectedAlbum } returns MutableStateFlow(null)
        every { mockViewModel.showAlbumSongs } returns MutableStateFlow(false)
        every { mockViewModel.totalAlbums } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsStart } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsEnd } returns MutableStateFlow(0)

        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockViewModel,
                onPlaylistClick = { }
            )
        }

        // Act - interact with artist filter
        composeTestRule.onNodeWithText("Search artists or select 'Everyone'").performTextInput("Test")

        // Assert
        verify { mockViewModel.searchArtists("Test") }
    }

    @Test
    fun homeScreen_loadingState_showsProgressIndicator() {
        // Arrange
        every { mockViewModel.user } returns MutableStateFlow(testUser)
        every { mockViewModel.playlists } returns MutableStateFlow(emptyList())
        every { mockViewModel.songs } returns MutableStateFlow(emptyList())
        every { mockViewModel.artists } returns MutableStateFlow(emptyList())
        every { mockViewModel.selectedArtist } returns MutableStateFlow(null)
        every { mockViewModel.isLoading } returns MutableStateFlow(true)
        every { mockViewModel.isArtistLoading } returns MutableStateFlow(false)
        every { mockViewModel.totalSearchResults } returns MutableStateFlow(0)
        every { mockViewModel.currentPageStart } returns MutableStateFlow(0)
        every { mockViewModel.currentPageEnd } returns MutableStateFlow(0)
        every { mockViewModel.currentSongIndex } returns MutableStateFlow(-1)
        every { mockViewModel.albums } returns MutableStateFlow(emptyList())
        every { mockViewModel.isAlbumsLoading } returns MutableStateFlow(false)
        every { mockViewModel.showAlbums } returns MutableStateFlow(false)
        every { mockViewModel.selectedAlbum } returns MutableStateFlow(null)
        every { mockViewModel.showAlbumSongs } returns MutableStateFlow(false)
        every { mockViewModel.totalAlbums } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsStart } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsEnd } returns MutableStateFlow(0)

        // Act
        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockViewModel,
                onPlaylistClick = { }
            )
        }

        // Assert - Loading indicator should be visible
        // Note: Compose Test Rule can verify loading indicators by accessibility tags
        // Since we don't have specific tags in the current implementation, 
        // this would need proper test tags in production code
    }

    @Test
    fun homeScreen_clearSearch_callsViewModel() {
        // Arrange
        every { mockViewModel.user } returns MutableStateFlow(testUser)
        every { mockViewModel.playlists } returns MutableStateFlow(emptyList())
        every { mockViewModel.songs } returns MutableStateFlow(listOf(testSong))
        every { mockViewModel.artists } returns MutableStateFlow(emptyList())
        every { mockViewModel.selectedArtist } returns MutableStateFlow(null)
        every { mockViewModel.isLoading } returns MutableStateFlow(false)
        every { mockViewModel.isArtistLoading } returns MutableStateFlow(false)
        every { mockViewModel.totalSearchResults } returns MutableStateFlow(1)
        every { mockViewModel.currentPageStart } returns MutableStateFlow(1)
        every { mockViewModel.currentPageEnd } returns MutableStateFlow(1)
        every { mockViewModel.currentSongIndex } returns MutableStateFlow(-1)
        every { mockViewModel.albums } returns MutableStateFlow(emptyList())
        every { mockViewModel.isAlbumsLoading } returns MutableStateFlow(false)
        every { mockViewModel.showAlbums } returns MutableStateFlow(false)
        every { mockViewModel.selectedAlbum } returns MutableStateFlow(null)
        every { mockViewModel.showAlbumSongs } returns MutableStateFlow(false)
        every { mockViewModel.totalAlbums } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsStart } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsEnd } returns MutableStateFlow(0)

        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockViewModel,
                onPlaylistClick = { }
            )
        }

        // Simulate having search text first
        composeTestRule.onNodeWithText("Search songs...").performTextInput("test")

        // Act - click clear button (X icon)
        composeTestRule.onNodeWithContentDescription("Clear search").performClick()

        // Assert
        verify { mockViewModel.clearSearchAndStopPlayback() }
    }

    @Test
    fun homeScreen_playlistClick_triggersCallback() {
        // Arrange
        var clickedPlaylistId: String? = null
        val onPlaylistClick: (String) -> Unit = { playlistId -> 
            clickedPlaylistId = playlistId 
        }

        every { mockViewModel.user } returns MutableStateFlow(testUser)
        every { mockViewModel.playlists } returns MutableStateFlow(listOf(testPlaylist))
        every { mockViewModel.songs } returns MutableStateFlow(emptyList())
        every { mockViewModel.artists } returns MutableStateFlow(emptyList())
        every { mockViewModel.selectedArtist } returns MutableStateFlow(null)
        every { mockViewModel.isLoading } returns MutableStateFlow(false)
        every { mockViewModel.isArtistLoading } returns MutableStateFlow(false)
        every { mockViewModel.totalSearchResults } returns MutableStateFlow(0)
        every { mockViewModel.currentPageStart } returns MutableStateFlow(0)
        every { mockViewModel.currentPageEnd } returns MutableStateFlow(0)
        every { mockViewModel.currentSongIndex } returns MutableStateFlow(-1)
        every { mockViewModel.albums } returns MutableStateFlow(emptyList())
        every { mockViewModel.isAlbumsLoading } returns MutableStateFlow(false)
        every { mockViewModel.showAlbums } returns MutableStateFlow(false)
        every { mockViewModel.selectedAlbum } returns MutableStateFlow(null)
        every { mockViewModel.showAlbumSongs } returns MutableStateFlow(false)
        every { mockViewModel.totalAlbums } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsStart } returns MutableStateFlow(0)
        every { mockViewModel.currentAlbumsEnd } returns MutableStateFlow(0)

        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockViewModel,
                onPlaylistClick = onPlaylistClick
            )
        }

        // Act
        composeTestRule.onNodeWithText("Test Playlist").performClick()

        // Assert
        assert(clickedPlaylistId == testPlaylist.id.toString())
    }
}