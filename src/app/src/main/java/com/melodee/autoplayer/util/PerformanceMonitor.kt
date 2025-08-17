package com.melodee.autoplayer.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Performance monitoring utility for tracking memory usage and app performance
 */
class PerformanceMonitor(private val context: Context) {
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var monitoringJob: Job? = null
    
    // Performance metrics
    private val maxMemoryUsed = AtomicLong(0)
    private val peakHeapSize = AtomicLong(0)
    private var startTime = System.currentTimeMillis()
    
    data class MemoryStats(
        val usedMemoryMB: Double,
        val freeMemoryMB: Double,
        val totalMemoryMB: Double,
        val heapUsedMB: Double,
        val heapMaxMB: Double,
        val nativeUsedMB: Double
    )
    
    data class PerformanceReport(
        val currentMemory: MemoryStats,
        val peakMemoryMB: Double,
        val peakHeapMB: Double,
        val uptimeMs: Long,
        val activeRequestCount: Int
    )
    
    /**
     * Start continuous memory monitoring
     */
    fun startMonitoring(intervalMs: Long = 30000) {
        stopMonitoring()
        
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val stats = getCurrentMemoryStats()
                    
                    // Track peak usage
                    val currentUsed = (stats.usedMemoryMB * 1024 * 1024).toLong()
                    maxMemoryUsed.updateAndGet { current -> maxOf(current, currentUsed) }
                    
                    val currentHeap = (stats.heapUsedMB * 1024 * 1024).toLong()
                    peakHeapSize.updateAndGet { current -> maxOf(current, currentHeap) }
                    
                    // Log memory stats
                    Logger.logPerformance("PerformanceMonitor", "Memory", "%.1f MB".format(stats.usedMemoryMB))
                    Logger.logPerformance("PerformanceMonitor", "Heap", "%.1f/%.1f MB".format(stats.heapUsedMB, stats.heapMaxMB))
                    
                    // Check for memory pressure
                    if (stats.usedMemoryMB / stats.totalMemoryMB > 0.8) {
                        Logger.w("PerformanceMonitor", "High memory usage: %.1f MB (%.1f%%)".format(
                            stats.usedMemoryMB, 
                            (stats.usedMemoryMB / stats.totalMemoryMB) * 100
                        ))
                    }
                    
                    delay(intervalMs)
                } catch (e: Exception) {
                    Logger.e("PerformanceMonitor", "Error in memory monitoring", e)
                    delay(intervalMs)
                }
            }
        }
        
        Logger.d("PerformanceMonitor", "Performance monitoring started (interval: ${intervalMs}ms)")
    }
    
    /**
     * Stop memory monitoring
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        Logger.d("PerformanceMonitor", "Performance monitoring stopped")
    }
    
    /**
     * Get current memory statistics
     */
    fun getCurrentMemoryStats(): MemoryStats {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        // Get heap information
        val runtime = Runtime.getRuntime()
        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val heapMax = runtime.maxMemory()
        
        // Get native memory (approximation)
        val nativeUsed = Debug.getNativeHeapAllocatedSize()
        
        return MemoryStats(
            usedMemoryMB = (memInfo.totalMem - memInfo.availMem) / (1024.0 * 1024.0),
            freeMemoryMB = memInfo.availMem / (1024.0 * 1024.0),
            totalMemoryMB = memInfo.totalMem / (1024.0 * 1024.0),
            heapUsedMB = heapUsed / (1024.0 * 1024.0),
            heapMaxMB = heapMax / (1024.0 * 1024.0),
            nativeUsedMB = nativeUsed / (1024.0 * 1024.0)
        )
    }
    
    /**
     * Generate comprehensive performance report
     */
    fun generateReport(): PerformanceReport {
        val currentStats = getCurrentMemoryStats()
        val uptime = System.currentTimeMillis() - startTime
        val activeRequests = 0 // TODO: Re-enable when RequestDeduplicator is fixed
        
        return PerformanceReport(
            currentMemory = currentStats,
            peakMemoryMB = maxMemoryUsed.get() / (1024.0 * 1024.0),
            peakHeapMB = peakHeapSize.get() / (1024.0 * 1024.0),
            uptimeMs = uptime,
            activeRequestCount = activeRequests
        )
    }
    
    /**
     * Log detailed performance report
     */
    fun logPerformanceReport() {
        val report = generateReport()
        
        Logger.i("PerformanceMonitor", "=== PERFORMANCE REPORT ===")
        Logger.i("PerformanceMonitor", "Uptime: ${report.uptimeMs / 1000}s")
        Logger.i("PerformanceMonitor", "Current Memory: %.1f MB / %.1f MB (%.1f%%)".format(
            report.currentMemory.usedMemoryMB,
            report.currentMemory.totalMemoryMB,
            (report.currentMemory.usedMemoryMB / report.currentMemory.totalMemoryMB) * 100
        ))
        Logger.i("PerformanceMonitor", "Peak Memory: %.1f MB".format(report.peakMemoryMB))
        Logger.i("PerformanceMonitor", "Heap Usage: %.1f MB / %.1f MB".format(
            report.currentMemory.heapUsedMB,
            report.currentMemory.heapMaxMB
        ))
        Logger.i("PerformanceMonitor", "Peak Heap: %.1f MB".format(report.peakHeapMB))
        Logger.i("PerformanceMonitor", "Native Memory: %.1f MB".format(report.currentMemory.nativeUsedMB))
        Logger.i("PerformanceMonitor", "Active Requests: ${report.activeRequestCount}")
        Logger.i("PerformanceMonitor", "========================")
    }
    
    /**
     * Check if app is under memory pressure
     */
    fun isMemoryUnderPressure(): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.lowMemory
    }
    
    /**
     * Force garbage collection and log memory before/after
     */
    fun forceGarbageCollection() {
        val beforeStats = getCurrentMemoryStats()
        Logger.d("PerformanceMonitor", "Memory before GC: %.1f MB".format(beforeStats.heapUsedMB))
        
        System.gc()
        
        // Wait a bit for GC to complete
        Thread.sleep(100)
        
        val afterStats = getCurrentMemoryStats()
        val freed = beforeStats.heapUsedMB - afterStats.heapUsedMB
        Logger.d("PerformanceMonitor", "Memory after GC: %.1f MB (freed: %.1f MB)".format(
            afterStats.heapUsedMB, freed))
    }
    
    /**
     * Reset performance tracking
     */
    fun reset() {
        maxMemoryUsed.set(0)
        peakHeapSize.set(0)
        startTime = System.currentTimeMillis()
        Logger.d("PerformanceMonitor", "Performance tracking reset")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: PerformanceMonitor? = null
        
        fun getInstance(context: Context): PerformanceMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}