package com.nuvio.tv.data.trailer

import android.content.Context
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TrailerCacheManager"

/**
 * Strict 3-Hour TTL (10,800,000 ms) as specified in STAGE 2E.
 */
val TRAILER_CACHE_TTL: Duration = Duration.ofHours(3)
private const val MEMORY_CACHE_MAX_ENTRIES = 50

data class CachedTrailerEntry(
    val key: String,
    val source: TrailerPlaybackSource,
    val cachedAt: Instant,
    val expiresAt: Instant
) {
    fun isExpired(now: Instant): Boolean = now.isAfter(expiresAt)
}

/**
 * High-performance two-tier (in-memory LRU + persistent disk) cache for resolved YouTube trailer sources.
 * - Strict 3-hour TTL (10,800,000 ms) or URL /expire/EPOCH/ timestamp, whichever is earlier.
 * - Non-blocking lookup on Dispatchers.IO.
 * - Auto-purges expired entries on startup and cache operations.
 * - Immediate invalidation support when playback encounters HTTP 403 / 410 expired signature errors.
 */
@Singleton
class TrailerCacheManager(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC()
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Clock.systemUTC())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskCacheDir: File by lazy {
        File(context.cacheDir, "trailer_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private val memoryCache = object : LinkedHashMap<String, CachedTrailerEntry>(MEMORY_CACHE_MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTrailerEntry>?) = size > MEMORY_CACHE_MAX_ENTRIES
    }

    init {
        scope.launch {
            purgeExpired()
        }
    }

    /**
     * Look up a cached trailer source by key.
     * Checks in-memory LRU first, then falls back to persistent disk cache.
     * Returns null if missing or expired.
     */
    suspend fun get(key: String): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        val now = Instant.now(clock)

        // 1. In-memory check
        synchronized(memoryCache) {
            memoryCache[key]?.let { entry ->
                if (!entry.isExpired(now)) {
                    Log.d(TAG, "TrailerCacheManager in-memory hit for key: $key")
                    return@withContext entry.source
                } else {
                    Log.d(TAG, "TrailerCacheManager in-memory entry expired for key: $key")
                    memoryCache.remove(key)
                }
            }
        }

        // 2. Persistent disk check
        val diskEntry = readFromDisk(key, now)
        if (diskEntry != null) {
            synchronized(memoryCache) {
                memoryCache[key] = diskEntry
            }
            Log.d(TAG, "TrailerCacheManager disk hit for key: $key")
            return@withContext diskEntry.source
        }

        null
    }

    /**
     * Store a resolved trailer source with a strict 3-hour TTL (or URL expiration if earlier).
     */
    suspend fun put(
        key: String,
        source: TrailerPlaybackSource,
        urlExpireInstant: Instant? = null
    ) = withContext(Dispatchers.IO) {
        val now = Instant.now(clock)
        val defaultTtlExpiry = now.plus(TRAILER_CACHE_TTL)
        val expiresAt = if (urlExpireInstant != null && urlExpireInstant.isBefore(defaultTtlExpiry)) {
            urlExpireInstant
        } else {
            defaultTtlExpiry
        }

        val entry = CachedTrailerEntry(
            key = key,
            source = source,
            cachedAt = now,
            expiresAt = expiresAt
        )

        synchronized(memoryCache) {
            memoryCache[key] = entry
        }

        writeToDisk(entry)
    }

    /**
     * Immediately invalidate an entry across both in-memory and disk caches (e.g., on HTTP 403/410).
     */
    suspend fun invalidate(key: String) = withContext(Dispatchers.IO) {
        synchronized(memoryCache) {
            memoryCache.remove(key)
            memoryCache.keys.removeIf { it.startsWith(key) || it.contains(key) }
        }
        deleteFromDisk(key)
        Log.d(TAG, "Invalidated trailer cache for: $key")
    }

    /**
     * Purges all expired disk entries. Run on startup and eviction.
     */
    suspend fun purgeExpired() = withContext(Dispatchers.IO) {
        val now = Instant.now(clock)
        try {
            val files = diskCacheDir.listFiles() ?: return@withContext
            var purgedCount = 0
            for (file in files) {
                try {
                    val content = file.readText()
                    val json = JSONObject(content)
                    val expiresEpoch = json.optLong("expiresAtEpoch", 0L)
                    if (expiresEpoch > 0 && now.toEpochMilli() >= expiresEpoch) {
                        if (file.delete()) purgedCount++
                    }
                } catch (_: Exception) {
                    file.delete()
                }
            }
            if (purgedCount > 0) {
                Log.d(TAG, "Purged $purgedCount expired trailer cache files from disk")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error purging expired trailer cache: ${e.message}")
        }
    }

    /**
     * Clear all cached trailer sources (both memory and disk).
     */
    fun clear() {
        synchronized(memoryCache) {
            memoryCache.clear()
        }
        scope.launch {
            val files = diskCacheDir.listFiles() ?: return@launch
            for (file in files) {
                file.delete()
            }
        }
    }

    private fun readFromDisk(key: String, now: Instant): CachedTrailerEntry? {
        val file = getDiskFile(key)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val expiresEpoch = json.getLong("expiresAtEpoch")
            val expiresAt = Instant.ofEpochMilli(expiresEpoch)
            if (now.isAfter(expiresAt)) {
                file.delete()
                return null
            }
            val cachedEpoch = json.optLong("cachedAtEpoch", now.toEpochMilli())
            val videoUrl = json.getString("videoUrl")
            val audioUrl = json.optString("audioUrl").takeIf { it.isNotBlank() }
            CachedTrailerEntry(
                key = key,
                source = TrailerPlaybackSource(videoUrl = videoUrl, audioUrl = audioUrl),
                cachedAt = Instant.ofEpochMilli(cachedEpoch),
                expiresAt = expiresAt
            )
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    private fun writeToDisk(entry: CachedTrailerEntry) {
        try {
            val file = getDiskFile(entry.key)
            val json = JSONObject().apply {
                put("key", entry.key)
                put("videoUrl", entry.source.videoUrl)
                put("audioUrl", entry.source.audioUrl ?: "")
                put("cachedAtEpoch", entry.cachedAt.toEpochMilli())
                put("expiresAtEpoch", entry.expiresAt.toEpochMilli())
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write trailer cache to disk for ${entry.key}: ${e.message}")
        }
    }

    private fun deleteFromDisk(key: String) {
        val file = getDiskFile(key)
        if (file.exists()) {
            file.delete()
        }
        // Also scan disk directory for any compound files that match
        try {
            val files = diskCacheDir.listFiles() ?: return
            for (f in files) {
                try {
                    val content = f.readText()
                    val json = JSONObject(content)
                    val storedKey = json.optString("key")
                    if (storedKey.startsWith(key) || storedKey.contains(key)) {
                        f.delete()
                    }
                } catch (_: Exception) {
                    f.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun getDiskFile(key: String): File {
        val hash = hashKey(key)
        return File(diskCacheDir, "$hash.json")
    }

    private fun hashKey(key: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        }
    }
}
