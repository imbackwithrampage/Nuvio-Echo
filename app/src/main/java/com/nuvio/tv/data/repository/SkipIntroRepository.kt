package com.nuvio.tv.data.repository

import com.nuvio.tv.data.remote.api.IntroDbSegment
import com.nuvio.tv.data.remote.api.IntroDbSegmentsResponse
import com.nuvio.tv.data.remote.api.TheIntroDbMediaResponse
import com.nuvio.tv.data.remote.api.TheIntroDbSegment
import javax.inject.Inject
import javax.inject.Singleton

data class SkipInterval(
    val startTime: Double, // seconds
    val endTime: Double,   // seconds
    val type: String,      // intro/op, recap, outro/ed, movie-credits, post-credits
    val provider: String   // "theintrodb", "introdb", "aniskip", "animeskip"; in the player also "chapters", "picture"
)

/** What the skip-time databases have for one episode or movie. */
data class SkipTimes(
    /** The best of each segment: each from the highest-priority provider that has it. */
    val intervals: List<SkipInterval>,
    /** Every provider's plausible segments, highest priority first, for fitting to the file that plays. */
    val candidates: List<SkipInterval> = intervals,
    /** Segment kinds ([SkipSegments.category]) a database confirmed the episode doesn't have. */
    val confirmedAbsent: Set<String> = emptySet()
) {
    companion object {
        val NONE = SkipTimes(emptyList())
    }
}

/** Sorts the providers' segment types into kinds, so one of each kind is kept. */
object SkipSegments {
    const val OPENING = "opening"
    const val RECAP = "recap"
    const val ENDING = "ending"
    const val MOVIE_CREDITS = "movie-credits"

    fun category(type: String): String? = when (type.lowercase()) {
        "intro", "op", "mixed-op" -> OPENING
        "outro", "ed", "mixed-ed", "credits", "ending" -> ENDING
        "recap" -> RECAP
        "movie-credits" -> MOVIE_CREDITS
        else -> null
    }

    /**
     * Whether [interval] can belong to a file [durationMs] long: it starts inside it, an opening or recap in its
     * first half, an ending or the credits in its second. This drops times submitted under the wrong label or for
     * a very different release. Everything passes while the length is unknown.
     */
    fun plausible(interval: SkipInterval, durationMs: Long?): Boolean {
        val length = durationMs?.takeIf { it > 0 }?.div(1000.0) ?: return true
        if (interval.startTime >= length) return false
        return when (category(interval.type)) {
            OPENING, RECAP -> interval.startTime < length * 0.5
            ENDING, MOVIE_CREDITS -> interval.startTime >= length * 0.5
            else -> true
        }
    }
}

internal fun IntroDbSegmentsResponse.toSkipIntervals(movie: Boolean): List<SkipInterval> {
    if (!movie) return listOfNotNull(
        intro.toSkipIntervalOrNull("intro"),
        recap.toSkipIntervalOrNull("recap"),
        outro.toSkipIntervalOrNull("outro")
    )
    val credits = outro.toSkipIntervalOrNull("movie-credits")
    val scene = postCredits.toSkipIntervalOrNull("post-credits")
    // Skipping credits must not also skip the post-credits scene.
    val safeCredits = if (credits != null && scene != null &&
        scene.startTime < credits.endTime && scene.endTime > credits.startTime
    ) {
        credits.copy(endTime = scene.startTime).takeIf { it.endTime > it.startTime }
    } else credits
    return listOfNotNull(safeCredits, scene)
}

private fun IntroDbSegment?.toSkipIntervalOrNull(type: String): SkipInterval? {
    if (this == null) return null
    val start = startSec ?: startMs?.let { it / 1000.0 } ?: return null
    val end = endSec ?: endMs?.let { it / 1000.0 } ?: return null
    if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start) return null
    return SkipInterval(startTime = start, endTime = end, type = type, provider = "introdb")
}

/** The first of each segment; credits without an end run to the end of a file [durationMs] long. */
internal fun TheIntroDbMediaResponse.toSkipIntervals(movie: Boolean, durationMs: Long?): List<SkipInterval> {
    val fileEnd = durationMs?.takeIf { it > 0 }?.div(1000.0) ?: Double.MAX_VALUE
    fun List<TheIntroDbSegment>?.first(type: String, openEnded: Boolean): SkipInterval? =
        orEmpty().firstNotNullOfOrNull { segment ->
            // An intro without a start begins the file.
            val start = segment.startMs?.div(1000.0) ?: if (type == "intro") 0.0 else return@firstNotNullOfOrNull null
            val end = segment.endMs?.div(1000.0) ?: if (openEnded) fileEnd else return@firstNotNullOfOrNull null
            if (!start.isFinite() || start < 0 || end <= start) null
            else SkipInterval(startTime = start, endTime = end, type = type, provider = "theintrodb")
        }
    return listOfNotNull(
        intro.first("intro", openEnded = false),
        recap.first("recap", openEnded = false),
        credits.first(if (movie) "movie-credits" else "outro", openEnded = true)
    )
}

/** Whether a segment starts at or after [lengthMs]: these times were taken on a longer release. */
internal fun TheIntroDbMediaResponse.startsPast(lengthMs: Long): Boolean =
    listOfNotNull(intro, recap, credits, preview).flatten().any { (it.startMs ?: 0L) >= lengthMs }

/** Skipping the credits must not also skip a post-credits scene: they stop where it starts. */
internal fun List<SkipInterval>.creditsBeforeScene(scene: SkipInterval?): List<SkipInterval> {
    if (scene == null) return this
    return mapNotNull { interval ->
        if (interval.type != "movie-credits" || scene.startTime >= interval.endTime || scene.endTime <= interval.startTime) {
            interval
        } else {
            interval.copy(endTime = scene.startTime).takeIf { it.endTime > it.startTime }
        }
    }
}

@Singleton
class SkipIntroRepository @Inject constructor(
    private val skipSegmentRepository: SkipSegmentRepository
) {
    suspend fun getMovieSkipIntervals(contentId: String?, videoId: String? = null, durationMs: Long? = null): SkipTimes =
        skipSegmentRepository.getMovieSkipIntervals(contentId, videoId, durationMs)

    suspend fun getSkipIntervals(imdbId: String?, season: Int, episode: Int, durationMs: Long? = null): SkipTimes =
        skipSegmentRepository.getSkipIntervals(imdbId, season, episode, durationMs)

    suspend fun getSkipIntervalsForMal(
        malId: String,
        episode: Int,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null,
        durationMs: Long? = null
    ): SkipTimes =
        skipSegmentRepository.getSkipIntervalsForMal(malId, episode, imdbId, imdbSeason, imdbEpisode, durationMs)

    suspend fun getSkipIntervalsForKitsu(
        kitsuId: String,
        episode: Int,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null,
        durationMs: Long? = null
    ): SkipTimes =
        skipSegmentRepository.getSkipIntervalsForKitsu(kitsuId, episode, imdbId, imdbSeason, imdbEpisode, durationMs)
}
