package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.AnimeSkipSettingsDataStore
import com.nuvio.tv.data.remote.api.AniSkipApi
import com.nuvio.tv.data.remote.api.AnimeSkipApi
import com.nuvio.tv.data.remote.api.AnimeSkipRequest
import com.nuvio.tv.data.remote.api.IntroDbApi
import com.nuvio.tv.data.remote.api.IntroDbSegment
import com.nuvio.tv.data.remote.api.IntroDbSegmentsResponse
import com.nuvio.tv.data.remote.api.TheIntroDbApi
import com.nuvio.tv.data.remote.api.TheIntroDbMediaResponse
import com.nuvio.tv.data.remote.api.TheIntroDbSegment
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Unified, parallel skip segment repository that queries IntroDB, AniSkip, and Anime-Skip
 * concurrently on [Dispatchers.IO] with strict 3-second network timeouts and thread-safe caching.
 */
@Singleton
class SkipSegmentRepository @Inject constructor(
    private val introDbApi: IntroDbApi,
    private val theIntroDbApi: TheIntroDbApi,
    private val aniSkipApi: AniSkipApi,
    private val animeSkipApi: AnimeSkipApi,
    private val simklResolver: SimklIdResolver,
    private val animeSkipSettingsDataStore: AnimeSkipSettingsDataStore,
    private val tmdbService: TmdbService
) {
    private val cache = ConcurrentHashMap<String, SkipTimes>()
    private val idMappingCache = ConcurrentHashMap<String, ResolvedAnimeIds>()
    private val animeSkipShowIdCache = ConcurrentHashMap<String, String>()
    private val introDbConfigured = BuildConfig.INTRODB_API_URL.isNotEmpty()

    private data class ResolvedAnimeIds(
        val imdb: String?,
        val mal: String?,
        val anilist: String?,
        val simklId: Long?,
        val type: String?
    )

    private class IntroDbResult(val intervals: List<SkipInterval>, val absent: Set<String>) {
        companion object {
            val NONE = IntroDbResult(emptyList(), emptySet())
        }
    }

    suspend fun getMovieSkipIntervals(
        contentId: String?,
        videoId: String? = null,
        durationMs: Long? = null
    ): SkipTimes = withContext(Dispatchers.IO) {
        val ids = listOfNotNull(contentId, videoId).distinct()
        val imdbId = ids.firstNotNullOfOrNull { id ->
            id.substringBefore(':').takeIf { it.matches(Regex("tt[0-9]+")) }
        } ?: ids.firstNotNullOfOrNull { id ->
            val parts = id.split(':')
            val value = parts.getOrNull(1) ?: return@firstNotNullOfOrNull null
            when (parts[0]) {
                "tmdb" -> value.toIntOrNull()?.let { tmdbId ->
                    runCatching {
                        withTimeoutOrNull(QUERY_TIMEOUT_MS) { tmdbService.tmdbToImdb(tmdbId, "movie") }
                    }.getOrNull()
                }
                "mal", "kitsu" -> runCatching {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) { simklResolver.resolveIds(parts[0], value)?.imdb }
                }.getOrNull()
                else -> null
            }
        } ?: return@withContext SkipTimes.NONE

        val key = "movie:$imdbId:${durationKey(durationMs)}"
        cache[key]?.let { return@withContext it }

        coroutineScope {
            val introDbDeferred = async {
                withTimeoutOrNull(QUERY_TIMEOUT_MS) { fetchFromIntroDb(imdbId, isMovie = true) }
                    ?: IntroDbResult.NONE
            }
            val theIntroDbDeferred = async {
                withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                    fetchFromTheIntroDb(imdbId, null, null, durationMs, movie = true)
                } ?: emptyList()
            }

            val fromIntroDb = introDbDeferred.await()
            val fromTheIntroDb = theIntroDbDeferred.await()

            combine(
                durationMs = durationMs,
                movie = true,
                providerResults = listOf(fromTheIntroDb, fromIntroDb.intervals),
                absent = fromIntroDb.absent
            ).also { cache[key] = it }
        }
    }

    suspend fun getSkipIntervals(
        imdbId: String?,
        season: Int,
        episode: Int,
        durationMs: Long? = null
    ): SkipTimes = withContext(Dispatchers.IO) {
        if (imdbId == null) return@withContext SkipTimes.NONE
        val cacheKey = "$imdbId:$season:$episode:${durationKey(durationMs)}"
        cache[cacheKey]?.let { return@withContext it }

        coroutineScope {
            val introDbDeferred = async {
                withTimeoutOrNull(QUERY_TIMEOUT_MS) { fetchFromIntroDb(imdbId, season, episode) }
                    ?: IntroDbResult.NONE
            }
            val theIntroDbDeferred = async {
                withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                    fetchFromTheIntroDb(imdbId, season, episode, durationMs, movie = false)
                } ?: emptyList()
            }
            val simklIdsDeferred = async {
                resolveIdsWithTimeout(imdbId, season, episode)
            }

            val simklIds = simklIdsDeferred.await()
            val malId = simklIds?.mal
            val anilistId = simklIds?.anilist

            val animeEpisode = if (simklIds?.simklId != null && simklIds.type != null) {
                runCatching {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                        val mapping = simklResolver.getEpisodeMapping(simklIds.simklId, simklIds.type)
                        mapping.firstOrNull { it.tvdbSeason == season && it.tvdbEpisode == episode }
                            ?.animeEpisode
                    }
                }.getOrNull() ?: episode
            } else {
                episode
            }

            val aniSkipDeferred = async {
                if (malId != null) {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) { fetchFromAniSkip(malId, animeEpisode, durationMs) }
                        ?: emptyList()
                } else emptyList()
            }
            val animeSkipDeferred = async {
                if (anilistId != null) {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) { fetchFromAnimeSkip(anilistId, animeEpisode, season = null) }
                        ?: emptyList()
                } else emptyList()
            }

            val introDb = introDbDeferred.await()
            val theIntroDb = theIntroDbDeferred.await()
            val animeSkip = animeSkipDeferred.await()
            val aniSkip = aniSkipDeferred.await()

            combine(
                durationMs = durationMs,
                movie = false,
                providerResults = listOf(theIntroDb, introDb.intervals, animeSkip, aniSkip),
                absent = introDb.absent
            ).also { cache[cacheKey] = it }
        }
    }

    suspend fun getSkipIntervalsForMal(
        malId: String,
        episode: Int,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null,
        durationMs: Long? = null
    ): SkipTimes = withContext(Dispatchers.IO) {
        val cacheKey = "mal:$malId:$episode:${durationKey(durationMs)}"
        cache[cacheKey]?.let { return@withContext it }

        coroutineScope {
            val aniSkipDeferred = async {
                withTimeoutOrNull(QUERY_TIMEOUT_MS) { fetchFromAniSkip(malId, episode, durationMs) }
                    ?: emptyList()
            }
            val simklIdsDeferred = async {
                withTimeoutOrNull(QUERY_TIMEOUT_MS) { simklResolver.resolveIds("mal", malId) }
            }

            val simklIds = simklIdsDeferred.await()
            val resolvedImdbId = imdbId ?: simklIds?.imdb

            val tvdbDeferred = async {
                if (resolvedImdbId != null && imdbSeason == null && simklIds != null) {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                        simklResolver.resolveEpisodeTvdb("mal", malId, episode)
                    }
                } else null
            }

            fromImdbAndAnime(
                resolvedImdbId = resolvedImdbId,
                imdbSeason = imdbSeason,
                imdbEpisode = imdbEpisode,
                episode = episode,
                anilistId = simklIds?.anilist,
                durationMs = durationMs,
                tvdb = { tvdbDeferred.await() },
                aniSkip = { aniSkipDeferred.await() }
            ).also { cache[cacheKey] = it }
        }
    }

    suspend fun getSkipIntervalsForKitsu(
        kitsuId: String,
        episode: Int,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null,
        durationMs: Long? = null
    ): SkipTimes = withContext(Dispatchers.IO) {
        val cacheKey = "kitsu:$kitsuId:$episode:${durationKey(durationMs)}"
        cache[cacheKey]?.let { return@withContext it }

        coroutineScope {
            val simklIdsDeferred = async {
                withTimeoutOrNull(QUERY_TIMEOUT_MS) { simklResolver.resolveIds("kitsu", kitsuId) }
            }
            val simklIds = simklIdsDeferred.await()
            val malIdStr = simklIds?.mal
            val resolvedImdbId = imdbId ?: simklIds?.imdb

            val aniSkipDeferred = async {
                if (malIdStr != null) {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) { fetchFromAniSkip(malIdStr, episode, durationMs) }
                        ?: emptyList()
                } else emptyList()
            }

            val tvdbDeferred = async {
                if (resolvedImdbId != null && imdbSeason == null && simklIds != null) {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                        simklResolver.resolveEpisodeTvdb("kitsu", kitsuId, episode)
                    }
                } else null
            }

            fromImdbAndAnime(
                resolvedImdbId = resolvedImdbId,
                imdbSeason = imdbSeason,
                imdbEpisode = imdbEpisode,
                episode = episode,
                anilistId = simklIds?.anilist,
                durationMs = durationMs,
                tvdb = { tvdbDeferred.await() },
                aniSkip = { aniSkipDeferred.await() }
            ).also { cache[cacheKey] = it }
        }
    }

    private suspend fun fromImdbAndAnime(
        resolvedImdbId: String?,
        imdbSeason: Int?,
        imdbEpisode: Int?,
        episode: Int,
        anilistId: String?,
        durationMs: Long?,
        tvdb: suspend () -> Pair<Int, Int>?,
        aniSkip: suspend () -> List<SkipInterval>
    ): SkipTimes = coroutineScope {
        var introDb = IntroDbResult.NONE
        var theIntroDb = emptyList<SkipInterval>()
        var animeSkip = emptyList<SkipInterval>()

        if (resolvedImdbId != null) {
            val mapped = if (imdbSeason == null) tvdb() else null
            val season = imdbSeason ?: mapped?.first
            if (season != null) {
                val imdbEp = imdbEpisode ?: mapped?.second ?: episode
                val introDbDeferred = async {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) { fetchFromIntroDb(resolvedImdbId, season, imdbEp) }
                        ?: IntroDbResult.NONE
                }
                val theIntroDbDeferred = async {
                    withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                        fetchFromTheIntroDb(resolvedImdbId, season, imdbEp, durationMs, movie = false)
                    } ?: emptyList()
                }
                val animeSkipDeferred = async {
                    if (anilistId != null) {
                        withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                            fetchFromAnimeSkip(anilistId, episode, season = null)
                        } ?: emptyList()
                    } else emptyList()
                }
                introDb = introDbDeferred.await()
                theIntroDb = theIntroDbDeferred.await()
                animeSkip = animeSkipDeferred.await()
            }
        } else if (anilistId != null) {
            animeSkip = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                fetchFromAnimeSkip(anilistId, episode, season = null)
            } ?: emptyList()
        }

        combine(
            durationMs = durationMs,
            movie = false,
            providerResults = listOf(theIntroDb, introDb.intervals, animeSkip, aniSkip()),
            absent = introDb.absent
        )
    }

    private suspend fun resolveIdsWithTimeout(imdbId: String, season: Int, episode: Int): ResolvedAnimeIds? {
        val mappingKey = "$imdbId:$season:$episode"
        idMappingCache[mappingKey]?.let { return it }

        val resolved = runCatching {
            withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                simklResolver.resolveIdsForImdbEpisode(imdbId, season, episode)
            }
        }.getOrNull()

        val result = if (resolved != null) {
            ResolvedAnimeIds(
                imdb = resolved.imdb,
                mal = resolved.mal,
                anilist = resolved.anilist,
                simklId = resolved.simklId,
                type = resolved.type
            )
        } else null

        if (result != null) {
            idMappingCache[mappingKey] = result
        }
        return result
    }

    private fun combine(
        durationMs: Long?,
        movie: Boolean,
        providerResults: List<List<SkipInterval>>,
        absent: Set<String>
    ): SkipTimes {
        val plausible = providerResults.map { result -> result.filter { SkipSegments.plausible(it, durationMs) } }
        var merged = mergeByPriority(plausible)
        if (movie) {
            val scene = plausible.flatten().filter { it.type == "post-credits" }.minByOrNull { it.startTime }
            merged = merged.creditsBeforeScene(scene) + listOfNotNull(scene)
        }
        return SkipTimes(merged, plausible.flatten(), absent)
    }

    private fun mergeByPriority(providerResults: List<List<SkipInterval>>): List<SkipInterval> {
        val chosen = LinkedHashMap<String, SkipInterval>()
        for (result in providerResults) {
            for (interval in result) {
                val category = SkipSegments.category(interval.type) ?: continue
                chosen.putIfAbsent(category, interval)
            }
        }
        return chosen.values.toList()
    }

    private fun durationKey(durationMs: Long?): Long = durationMs?.takeIf { it > 0 }?.div(1000) ?: 0L

    private suspend fun fetchFromIntroDb(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null,
        isMovie: Boolean = false
    ): IntroDbResult {
        if (!introDbConfigured) return IntroDbResult.NONE
        return try {
            val response = introDbApi.getSegments(imdbId, season, episode, true.takeIf { isMovie })
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val absent = buildSet {
                    if (body.noIntro == true) add(SkipSegments.OPENING)
                    if (body.noRecap == true) add(SkipSegments.RECAP)
                    if (body.noOutro == true) add(if (isMovie) SkipSegments.MOVIE_CREDITS else SkipSegments.ENDING)
                }
                IntroDbResult(body.toSkipIntervals(isMovie), absent)
            } else IntroDbResult.NONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "IntroDB: no data for $imdbId S${season}E${episode}: ${e.message}")
            IntroDbResult.NONE
        }
    }

    private suspend fun fetchFromTheIntroDb(
        imdbId: String,
        season: Int?,
        episode: Int?,
        durationMs: Long?,
        movie: Boolean
    ): List<SkipInterval> {
        val lengthMs = durationMs?.takeIf { it > 0 }
        var media = fetchTheIntroDbMedia(imdbId, season, episode, lengthMs, movie) ?: return emptyList()
        if (lengthMs != null && media.startsPast(lengthMs)) {
            Log.i(TAG, "TheIntroDB: times run past length; trying general times")
            fetchTheIntroDbMedia(imdbId, season, episode, null, movie)?.let { media = it }
        }
        return media.toSkipIntervals(movie, durationMs)
    }

    private suspend fun fetchTheIntroDbMedia(
        imdbId: String,
        season: Int?,
        episode: Int?,
        askForMs: Long?,
        movie: Boolean
    ): TheIntroDbMediaResponse? {
        return try {
            val response = theIntroDbApi.getMedia(
                imdbId = imdbId,
                season = season.takeUnless { movie },
                episode = episode.takeUnless { movie },
                durationMs = askForMs
            )
            response.body()?.takeIf { response.isSuccessful }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "TheIntroDB: no data for $imdbId S${season}E${episode}: ${e.message}")
            null
        }
    }

    private suspend fun fetchFromAniSkip(malId: String, episode: Int, durationMs: Long?): List<SkipInterval> {
        val length = durationMs?.takeIf { it > 0 }?.div(1000)?.toInt() ?: 0
        val matched = fetchFromAniSkip(malId, episode, length)
        return if (matched.isEmpty() && length > 0) fetchFromAniSkip(malId, episode, 0) else matched
    }

    private suspend fun fetchFromAniSkip(malId: String, episode: Int, episodeLength: Int): List<SkipInterval> {
        return try {
            val types = listOf("op", "ed", "recap", "mixed-op", "mixed-ed")
            val response = aniSkipApi.getSkipTimes(malId, episode, types, episodeLength)
            if (response.isSuccessful && response.body()?.found == true) {
                response.body()!!.results?.map { result ->
                    SkipInterval(
                        startTime = result.interval.startTime,
                        endTime = result.interval.endTime,
                        type = result.skipType,
                        provider = "aniskip"
                    )
                } ?: emptyList()
            } else emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "AniSkip: no data for MAL $malId ep $episode: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchFromAnimeSkip(anilistId: String, episode: Int, season: Int?): List<SkipInterval> {
        val clientId = animeSkipSettingsDataStore.clientId.firstOrNull()?.trim()
        if (clientId.isNullOrBlank()) return emptyList()
        val enabled = animeSkipSettingsDataStore.enabled.firstOrNull() ?: false
        if (!enabled) return emptyList()
        return try {
            val showIds = resolveAnimeSkipShowIds(anilistId, clientId)
            if (showIds.isEmpty()) return emptyList()

            for (showId in showIds) {
                val episodesResponse = animeSkipApi.query(
                    clientId = clientId,
                    body = AnimeSkipRequest(
                        query = "{ findEpisodesByShowId(showId: \"$showId\") { season number timestamps { at type { name } } } }"
                    )
                )
                if (!episodesResponse.isSuccessful) continue

                val episodes = episodesResponse.body()?.data?.findEpisodesByShowId ?: continue
                val targetEpisode = episodes.firstOrNull { ep ->
                    ep.number?.toIntOrNull() == episode &&
                        (season == null || ep.season?.toIntOrNull() == season)
                } ?: continue

                val sorted = (targetEpisode.timestamps ?: continue).sortedBy { it.at }
                val result = sorted.mapIndexedNotNull { i, ts ->
                    val endTime = sorted.getOrNull(i + 1)?.at ?: Double.MAX_VALUE
                    val type = when (ts.type.name.lowercase()) {
                        "intro", "new intro" -> "op"
                        "credits", "new credits" -> "ed"
                        "mixed intro" -> "mixed-op"
                        "mixed credits" -> "mixed-ed"
                        "recap" -> "recap"
                        else -> return@mapIndexedNotNull null
                    }
                    SkipInterval(startTime = ts.at, endTime = endTime, type = type, provider = "animeskip")
                }
                if (result.isNotEmpty()) return result
            }
            emptyList()
        } catch (e: Exception) {
            Log.d(TAG, "AnimeSkip: error for anilist $anilistId ep $episode: ${e.message}")
            emptyList()
        }
    }

    private suspend fun resolveAnimeSkipShowIds(anilistId: String, clientId: String): List<String> {
        animeSkipShowIdCache[anilistId]?.let { cached ->
            return if (cached == NO_ID) emptyList() else listOf(cached)
        }
        val showIds = try {
            animeSkipApi.query(
                clientId = clientId,
                body = AnimeSkipRequest(
                    query = "{ findShowsByExternalId(service: ANILIST, serviceId: \"$anilistId\") { id } }"
                )
            ).body()?.data?.findShowsByExternalId?.map { it.id } ?: emptyList()
        } catch (e: Exception) { emptyList() }
        if (showIds.size == 1) animeSkipShowIdCache[anilistId] = showIds[0]
        else if (showIds.isEmpty()) animeSkipShowIdCache[anilistId] = NO_ID
        return showIds
    }

    fun clearCache() {
        cache.clear()
        idMappingCache.clear()
        animeSkipShowIdCache.clear()
    }

    companion object {
        private const val TAG = "SkipSegmentRepository"
        private const val NO_ID = "__none__"
        private const val QUERY_TIMEOUT_MS = 3000L
    }
}
