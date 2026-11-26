package com.example.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "MediaPlaybackCache"
private const val CACHE_MAX_BYTES = 180L * 1024L * 1024L
private const val PREWARM_BYTES = 2_500_000L

@OptIn(UnstableApi::class)
object MediaPlaybackCache {

    @Volatile
    private var simpleCache: SimpleCache? = null
    private val cacheLock = Any()
    private val activePrewarms = ConcurrentHashMap.newKeySet<String>()

    fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        return synchronized(cacheLock) {
            simpleCache ?: run {
                val appContext = context.applicationContext
                val cacheDir = File(appContext.cacheDir, "media_playback_cache").apply {
                    if (!exists()) mkdirs()
                }
                val evictor = LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES)
                val dbProvider = StandaloneDatabaseProvider(appContext)
                SimpleCache(cacheDir, evictor, dbProvider).also {
                    simpleCache = it
                }
            }
        }
    }

    fun buildCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val appContext = context.applicationContext
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(10_000)
            .setUserAgent("AldiAIClipper/1.0")

        val upstreamFactory = DefaultDataSource.Factory(appContext, httpFactory)
        return CacheDataSource.Factory()
            .setCache(getCache(appContext))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun buildFastExoPlayer(context: Context): ExoPlayer {
        val appContext = context.applicationContext
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12_000, // minBufferMs
                36_000, // maxBufferMs
                350,    // bufferForPlaybackMs — instant start (<0.4s)
                850     // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(appContext)
            .forceEnableMediaCodecAsynchronousQueueing()

        val mediaSourceFactory = DefaultMediaSourceFactory(buildCacheDataSourceFactory(appContext))

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
    }

    suspend fun prewarmStream(
        context: Context,
        uriString: String,
        maxBytes: Long = PREWARM_BYTES
    ) = withContext(Dispatchers.IO) {
        val scheme = Uri.parse(uriString).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return@withContext
        if (!activePrewarms.add(uriString)) return@withContext

        try {
            val cache = getCache(context)
            val alreadyCached = runCatching {
                cache.getCachedBytes(uriString, 0L, maxBytes)
            }.getOrDefault(0L)
            if (alreadyCached >= 512_000L) return@withContext

            val dataSource = buildCacheDataSourceFactory(context).createDataSource()
            val dataSpec = DataSpec.Builder()
                .setUri(uriString)
                .setPosition(0L)
                .setLength(maxBytes)
                .build()
            val writer = CacheWriter(dataSource, dataSpec, null, null)
            writer.cache()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Stream prewarm skipped for $uriString: ${e.message}")
        } finally {
            activePrewarms.remove(uriString)
        }
    }

    fun isStreamCached(context: Context, uriString: String): Boolean {
        val scheme = Uri.parse(uriString).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return true
        return runCatching {
            getCache(context).getCachedBytes(uriString, 0L, 512_000L) >= 128_000L
        }.getOrDefault(false)
    }
}
