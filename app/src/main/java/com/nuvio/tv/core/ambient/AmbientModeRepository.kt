package com.nuvio.tv.core.ambient

import com.nuvio.tv.core.home.BuiltInRowsRepository
import com.nuvio.tv.domain.model.AmbientSourceMode
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AmbientSlideItem(
    val id: String,
    val title: String,
    val backdropUrl: String,
    val logoUrl: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val multiRatings: MDBListRatings? = null
)

@Singleton
class AmbientModeRepository @Inject constructor(
    private val builtInRowsRepository: BuiltInRowsRepository,
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository
) {
    suspend fun getSlides(sourceMode: AmbientSourceMode): List<AmbientSlideItem> = withContext(Dispatchers.IO) {
        val slides = when (sourceMode) {
            AmbientSourceMode.LIBRARY -> loadLibrarySlides().ifEmpty { loadTrendingSlides() }
            AmbientSourceMode.TRENDING -> loadTrendingSlides()
        }
        if (slides.isNotEmpty()) slides.shuffled() else loadFallbackSlides()
    }

    private suspend fun loadTrendingSlides(): List<AmbientSlideItem> {
        val rows = builtInRowsRepository.rows.value
        val items = (rows.trendingWeek + rows.topMoviesToday + rows.topShowsToday + rows.forYou)
            .distinctBy { it.id }

        return items.filter { !it.backdropUrl.isNullOrBlank() }.map { preview ->
            AmbientSlideItem(
                id = preview.id,
                title = preview.name,
                backdropUrl = preview.backdropUrl.orEmpty(),
                logoUrl = preview.logo,
                year = preview.releaseInfo?.take(4),
                genre = preview.genres.firstOrNull(),
                overview = preview.description,
                rating = preview.imdbRating?.toDouble(),
                multiRatings = preview.imdbRating?.let {
                    MDBListRatings(imdb = it.toDouble())
                }
            )
        }
    }

    private suspend fun loadLibrarySlides(): List<AmbientSlideItem> {
        val libraryEntries = runCatching { libraryRepository.libraryItems.firstOrNull() }.getOrNull().orEmpty()
        val cwEntries = runCatching { watchProgressRepository.continueWatching.firstOrNull() }.getOrNull().orEmpty()

        val fromLibrary = libraryEntries
            .filter { !it.background.isNullOrBlank() }
            .map { entry ->
                AmbientSlideItem(
                    id = entry.id,
                    title = entry.name,
                    backdropUrl = entry.background.orEmpty(),
                    logoUrl = entry.logo,
                    year = entry.releaseInfo?.take(4),
                    genre = entry.genres.firstOrNull(),
                    overview = entry.description,
                    rating = entry.imdbRating?.toDouble(),
                    multiRatings = entry.imdbRating?.let {
                        MDBListRatings(imdb = it.toDouble())
                    }
                )
            }

        val fromCw = cwEntries
            .filter { !it.backdrop.isNullOrBlank() }
            .map { cw ->
                AmbientSlideItem(
                    id = cw.contentId,
                    title = cw.name,
                    backdropUrl = cw.backdrop.orEmpty(),
                    logoUrl = cw.logo,
                    year = null,
                    genre = null,
                    overview = null,
                    rating = null,
                    multiRatings = null
                )
            }

        return (fromLibrary + fromCw).distinctBy { it.id }
    }

    private fun loadFallbackSlides(): List<AmbientSlideItem> = listOf(
        AmbientSlideItem(
            id = "ambient_fallback_1",
            title = "Nuvio",
            backdropUrl = "",
            logoUrl = null,
            year = null,
            genre = null,
            overview = null,
            rating = null,
            multiRatings = null
        )
    )
}
