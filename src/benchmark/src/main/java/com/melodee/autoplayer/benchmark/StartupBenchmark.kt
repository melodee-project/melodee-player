package com.melodee.autoplayer.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup performance benchmarks for Melodee Player
 * 
 * Measures app launch performance under different conditions:
 * - Cold start: App process not in memory
 * - Warm start: App process in memory but activity destroyed
 * - Hot start: App and activity in memory
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None()
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.None()
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun hotStartup() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.HOT,
        compilationMode = CompilationMode.None()
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial()
    ) {
        pressHome()
        startActivityAndWait()
    }

    /**
     * Navigate to login screen and measure first frame render time
     * This tests the critical path for new users
     */
    @Test
    fun firstTimeUserStartup() = benchmarkRule.measureRepeated(
        packageName = "com.melodee.autoplayer",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
        setupBlock = {
            // Clear app data to simulate first-time user
            device.executeShellCommand("pm clear $packageName")
        }
    ) {
        pressHome()
        startActivityAndWait()
        
        // Wait for login screen to fully render
        device.waitForIdle()
    }
}