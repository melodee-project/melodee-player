package com.melodee.autoplayer.service

import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.DataSpec
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CancellationException

import java.io.File

@UnstableApi
object MediaCache {
    private const val TAG = "MediaCache"
    private const val CACHE_FOLDER = "media_cache"
    private const val MAX_CACHE_SIZE_BYTES = 200L * 1024L * 1024L // 200 MB

    @Volatile private var cache: Cache? = null
    @Volatile private var databaseProvider: DatabaseProvider? = null

    private fun ensureCache(context: Context): Cache {
        val existing = cache
        if (existing != null) return existing

        synchronized(this) {
            val again = cache
            if (again != null) return again
            val cacheDir = File(context.cacheDir, CACHE_FOLDER)
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val dbProvider = StandaloneDatabaseProvider(context)
            databaseProvider = dbProvider
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val simpleCache = SimpleCache(cacheDir, evictor, dbProvider)
            cache = simpleCache
            Log.d(TAG, "Initialized SimpleCache at ${cacheDir.absolutePath}")
            return simpleCache
        }
    }

    fun mediaSourceFactory(context: Context): MediaSource.Factory =
        DefaultMediaSourceFactory(dataSourceFactory(context))

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
        return CacheDataSource.Factory()
            .setCache(ensureCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun clearCache(context: Context) {
        try {
            cache?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing cache", e)
        } finally {
            cache = null
            databaseProvider = null
        }
        try {
            val dir = File(context.cacheDir, CACHE_FOLDER)
            if (dir.exists()) {
                dir.deleteRecursively()
                Log.d(TAG, "Cleared media cache at ${dir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting cache directory", e)
        }
    }

    fun prefetchUrl(context: Context, url: String) {
        try {
            val dataSpec = DataSpec(Uri.parse(url))
            val upstreamDs = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(30_000)
                .createDataSource()
            val cacheDataSource = CacheDataSource(ensureCache(context), upstreamDs)
            val writer = CacheWriter(cacheDataSource, dataSpec, ByteArray(8 * 1024), /* progressListener = */ null)
            writer.cache()
            Log.d(TAG, "Prefetched: $url")
        } catch (ce: CancellationException) {
            Log.d(TAG, "Prefetch cancelled for $url")
        } catch (e: Exception) {
            Log.w(TAG, "Prefetch failed for $url: ${e.message}")
        }
    }
}
