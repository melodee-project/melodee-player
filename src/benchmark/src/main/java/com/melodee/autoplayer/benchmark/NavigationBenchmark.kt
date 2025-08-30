package com.melodee.autoplayer.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation performance benchmarks for key user journeys
 * 
 * Tests transition performance between screens:
 * - Login → Home
 * - Home → Playlist Details
 * - Search → Song Playback
 * - Background → Foreground with service reconnection
 */
@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun loginToHomeNavigation() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            // Clear app data to force login screen
            device.executeShellCommand("pm clear $packageName")
            startActivityAndWait()
        }
    ) {
        performLoginFlow()
    }

    @Test
    fun homeToPlaylistNavigation() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            // Ensure we're authenticated and have playlists loaded
            device.wait(Until.hasObject(By.textContains("songs")), 5000)
        }
    ) {
        navigateToPlaylist()
    }

    @Test
    fun searchToPlaybackNavigation() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        performSearchAndPlay()
    }

    @Test
    fun backgroundToForegroundTransition() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.HOT,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            // Start some background activity (music service)
            performSearchAndPlay()
            device.waitForIdle(1000)
        }
    ) {
        // Send app to background
        pressHome()
        device.waitForIdle(2000)
        
        // Bring back to foreground - tests service reconnection
        startActivityAndWait()
        device.waitForIdle()
    }

    @Test
    fun artistBrowseNavigation() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            device.waitForIdle()
        }
    ) {
        navigateArtistBrowseFlow()
    }

    @Test
    fun memoryPressureNavigation() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            // Generate memory pressure with large search results
            repeat(3) {
                performLargeSearch()
                device.waitForIdle(500)
            }
        }
    ) {
        // Test navigation under memory pressure
        navigateToPlaylist()
        device.pressBack()
        performSearchAndPlay()
    }


    private fun MacrobenchmarkScope.performLoginFlow() {
        // Wait for login screen elements
        val serverField = device.wait(Until.findObject(By.text("Server URL")), 3000)
        val usernameField = device.wait(Until.findObject(By.text("Username")), 3000)
        val passwordField = device.wait(Until.findObject(By.text("Password")), 3000)
        
        // Fill login form (mock data)
        serverField?.text = "https://demo.melodee.com"
        usernameField?.text = "testuser"
        passwordField?.text = "testpass"
        
        // Submit login
        val loginButton = device.findObject(By.text("Login"))
        loginButton?.click()
        
        // Wait for home screen to load
        device.wait(Until.hasObject(By.textContains("Search songs")), 10000)
    }

    private fun MacrobenchmarkScope.navigateToPlaylist() {
        // Find and click on first playlist
        val playlist = device.wait(Until.findObject(By.textContains("songs")), 3000)
        playlist?.click()
        
        // Wait for playlist details to load
        device.wait(Until.hasObject(By.textContains("Play All")), 5000)
    }

    private fun MacrobenchmarkScope.performSearchAndPlay() {
        // Perform search
        val searchField = device.wait(Until.findObject(By.text("Search songs...")), 3000)
        searchField?.text = "test song"
        
        // Wait for results and click first song
        device.wait(Until.hasObject(By.textContains("results")), 5000)
        val firstSong = device.findObjects(By.clickable(true)).firstOrNull { 
            it.text?.contains("Test") == true 
        }
        firstSong?.click()
        
        // Wait for playback to start
        device.waitForIdle(1000)
    }

    private fun MacrobenchmarkScope.navigateArtistBrowseFlow() {
        // Open artist filter
        val artistFilter = device.wait(Until.findObject(By.text("Search artists or select 'Everyone'")), 3000)
        artistFilter?.text = "artist"
        
        // Select an artist
        device.waitForIdle(1000)
        val firstArtist = device.findObjects(By.clickable(true)).firstOrNull()
        firstArtist?.click()
        
        // Browse albums
        val browseAlbumsButton = device.wait(Until.findObject(By.text("Browse Albums")), 3000)
        browseAlbumsButton?.click()
        
        // Wait for albums to load
        device.wait(Until.hasObject(By.textContains("albums")), 5000)
        
        // Return to main view
        val backButton = device.findObject(By.text("Back"))
        backButton?.click()
    }

    private fun MacrobenchmarkScope.performLargeSearch() {
        val searchField = device.wait(Until.findObject(By.text("Search songs...")), 2000)
        searchField?.text = "music"
        
        // Wait for results and scroll to load more
        device.wait(Until.hasObject(By.textContains("results")), 3000)
        val resultsList = device.findObject(By.scrollable(true))
        repeat(5) {
            resultsList?.scroll(androidx.test.uiautomator.Direction.DOWN, 1.0f)
            device.waitForIdle(200)
        }
    }
}