package com.nuvio.tv.core.ratings

import android.content.Context
import androidx.collection.LruCache
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.mdblist.MdbListRatingsDiskCache
import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.Meta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiProviderRatingsAggregator @Inject constructor(
    private val mdbListRepository: MDBListRepository,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val STRICT_TIMEOUT_MS = 2500L
        private const val MEMORY_CACHE_SIZE = 128
    }

    private val memoryCache = LruCache<String, MDBListRatings>(MEMORY_CACHE_SIZE)
    private val diskCache by lazy { MdbListRatingsDiskCache(context) }

    fun getCached(itemId: String, itemType: String): MDBListRatings? {
        val key = "$itemType:$itemId"
        return memoryCache.get(key)
    }

    suspend fun getRatings(
        itemId: String,
        itemType: String,
        imdbId: String? = null,
        fallbackImdbRating: Double? = null,
        fallbackTmdbRating: Double? = null,
        meta: Meta? = null
    ): MDBListRatings? = withContext(Dispatchers.IO) {
        val cacheKey = "$itemType:$itemId"

        // 1. Check in-memory LRU cache
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // 2. Check disk cache
        runCatching {
            diskCache.get(cacheKey)?.let { diskCached ->
                memoryCache.put(cacheKey, diskCached)
                return@withContext diskCached
            }
        }

        // 3. Parallel asynchronous fetch with strict timeout
        val aggregated = runCatching {
            coroutineScope {
                val mdbDeferred = async {
                    withTimeoutOrNull(STRICT_TIMEOUT_MS) {
                        runCatching {
                            if (meta != null) {
                                mdbListRepository.getRatingsForMeta(meta, itemId, itemType)?.ratings
                            } else {
                                mdbListRepository.getRatingsForId(itemId, itemType, imdbId)?.ratings
                            }
                        }.getOrNull()
                    }
                }

                val tmdbDeferred = async {
                    if (fallbackTmdbRating != null) {
                        fallbackTmdbRating
                    } else {
                        withTimeoutOrNull(STRICT_TIMEOUT_MS) {
                            runCatching {
                                val isTv = itemType.equals("series", true) || itemType.equals("tv", true) || itemType.equals("show", true)
                                val tmdbId = tmdbService.ensureTmdbId(itemId, if (isTv) "tv" else "movie", imdbId)
                                if (tmdbId != null) {
                                    val enrichment = tmdbMetadataService.fetchEnrichment(
                                        tmdbId,
                                        if (isTv) ContentType.SERIES else ContentType.MOVIE,
                                        "en"
                                    )
                                    enrichment?.rating
                                } else null
                            }.getOrNull()
                        }
                    }
                }

                val mdbRatings = mdbDeferred.await()
                val tmdbRating = tmdbDeferred.await()

                val resolvedImdb = mdbRatings?.imdb ?: fallbackImdbRating
                val resolvedTmdb = mdbRatings?.tmdb ?: tmdbRating

                if (mdbRatings != null) {
                    mdbRatings.copy(
                        imdb = resolvedImdb,
                        tmdb = resolvedTmdb
                    )
                } else if (resolvedImdb != null || resolvedTmdb != null) {
                    MDBListRatings(
                        imdb = resolvedImdb,
                        tmdb = resolvedTmdb
                    )
                } else {
                    null
                }
            }
        }.getOrNull()

        if (aggregated != null && !aggregated.isEmpty()) {
            memoryCache.put(cacheKey, aggregated)
            runCatching { diskCache.put(cacheKey, aggregated) }
        }

        aggregated
    }
}
