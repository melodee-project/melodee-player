package com.melodee.autoplayer.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI scroll performance benchmarks for list-heavy screens
 * 
 * Tests scrolling performance in key user flows:
 * - Playlist browsing (main screen)
 * - Song search results (search screen)
 * - Artist/album browsing
 */
@RunWith(AndroidJUnit4::class)
class ScrollPerformanceBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun playlistScrollPerformance() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            
            // Navigate to main screen with playlists
            // Wait for content to load
            device.wait(Until.hasObject(By.textContains("songs")), 5000)
        }
    ) {
        scrollPlaylistList()
    }

    @Test
    fun searchResultsScrollPerformance() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            
            // Perform search to populate results
            val searchField = device.wait(Until.findObject(By.text("Search songs...")), 3000)
            searchField?.text = "test"
            
            // Wait for search results
            device.wait(Until.hasObject(By.textContains("results")), 5000)
        }
    ) {
        scrollSearchResults()
    }

    @Test
    fun infiniteScrollPerformance() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            
            // Trigger search to enable infinite scroll
            val searchField = device.wait(Until.findObject(By.text("Search songs...")), 3000)
            searchField?.text = "music"
            
            // Wait for initial results
            device.wait(Until.hasObject(By.textContains("results")), 5000)
        }
    ) {
        // Perform extended scroll to test infinite loading
        repeat(10) {
            scrollSearchResults()
            // Brief pause to allow loading
            device.waitForIdle(1000)
        }
    }

    @Test
    fun artistBrowseScrollPerformance() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            startActivityAndWait()
            
            // Access artist filter dropdown
            val artistFilter = device.wait(Until.findObject(By.text("Search artists or select 'Everyone'")), 3000)
            artistFilter?.text = "a"
            
            // Wait for artist results
            device.waitForIdle(2000)
        }
    ) {
        scrollArtistDropdown()
    }

    private fun MacrobenchmarkScope.scrollPlaylistList() {
        val playlistList = device.wait(Until.findObject(By.scrollable(true)), 3000)
        
        // Scroll down through playlists
        repeat(5) {
            playlistList?.scroll(Direction.DOWN, 1.0f)
            device.waitForIdle(100)
        }
        
        // Scroll back up
        repeat(5) {
            playlistList?.scroll(Direction.UP, 1.0f)
            device.waitForIdle(100)
        }
    }

    private fun MacrobenchmarkScope.scrollSearchResults() {
        val resultsList = device.wait(Until.findObject(By.scrollable(true)), 3000)
        
        // Fast scroll through search results
        repeat(8) {
            resultsList?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle(50)
        }
        
        // Scroll back to top
        repeat(8) {
            resultsList?.scroll(Direction.UP, 0.8f)
            device.waitForIdle(50)
        }
    }

    private fun MacrobenchmarkScope.scrollArtistDropdown() {
        // Look for scrollable dropdown content
        val dropdown = device.wait(Until.findObject(By.scrollable(true)), 2000)
        
        // Scroll through artist suggestions
        repeat(3) {
            dropdown?.scroll(Direction.DOWN, 0.5f)
            device.waitForIdle(100)
        }
    }
}