package com.nuvio.tv.core.ratings

import android.content.Context
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.MDBListRatingsResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MultiProviderRatingsAggregatorTest {

    private val mdbListRepository = mockk<MDBListRepository>()
    private val tmdbService = mockk<TmdbService>()
    private val tmdbMetadataService = mockk<TmdbMetadataService>()
    private val context = mockk<Context>()

    private lateinit var aggregator: MultiProviderRatingsAggregator

    @Before
    fun setup() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "nuvio_test_cache_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        every { context.filesDir } returns tempDir

        aggregator = MultiProviderRatingsAggregator(
            mdbListRepository = mdbListRepository,
            tmdbService = tmdbService,
            tmdbMetadataService = tmdbMetadataService,
            context = context
        )
    }

    @Test
    fun `returns aggregated ratings when MDBList succeeds`() = runTest {
        val mockRatings = MDBListRatings(
            imdb = 8.5,
            tmdb = 82.0,
            tomatoes = 94.0,
            audience = 88.0,
            metacritic = 76.0
        )
        coEvery {
            mdbListRepository.getRatingsForId("tt1234567", "movie", any())
        } returns MDBListRatingsResult(mockRatings, hasImdbRating = true)

        val result = aggregator.getRatings(
            itemId = "tt1234567",
            itemType = "movie"
        )

        assertNotNull(result)
        assertEquals(8.5, result?.imdb ?: 0.0, 0.01)
        assertEquals(82.0, result?.tmdb ?: 0.0, 0.01)
        assertEquals(94.0, result?.tomatoes ?: 0.0, 0.01)
        assertEquals(88.0, result?.audience ?: 0.0, 0.01)
        assertEquals(76.0, result?.metacritic ?: 0.0, 0.01)
    }

    @Test
    fun `falls back to fallback IMDb and TMDB ratings when MDBList returns null`() = runTest {
        coEvery {
            mdbListRepository.getRatingsForId("tt9999999", "movie", any())
        } returns null

        val result = aggregator.getRatings(
            itemId = "tt9999999",
            itemType = "movie",
            fallbackImdbRating = 7.8,
            fallbackTmdbRating = 7.5
        )

        assertNotNull(result)
        assertEquals(7.8, result?.imdb ?: 0.0, 0.01)
        assertEquals(7.5, result?.tmdb ?: 0.0, 0.01)
    }

    @Test
    fun `cached result returns immediately without hitting repository again`() = runTest {
        val mockRatings = MDBListRatings(imdb = 9.1, tmdb = 85.0)
        coEvery {
            mdbListRepository.getRatingsForId("tt1111111", "movie", any())
        } returns MDBListRatingsResult(mockRatings, hasImdbRating = true)

        val first = aggregator.getRatings("tt1111111", "movie")
        val cached = aggregator.getCached("tt1111111", "movie")

        assertNotNull(first)
        assertNotNull(cached)
        assertEquals(9.1, cached?.imdb ?: 0.0, 0.01)
    }

    @Test
    fun `network exception returns null without crashing or delaying`() = runTest {
        coEvery {
            mdbListRepository.getRatingsForId("ttcrash", "movie", any())
        } throws RuntimeException("Simulated network failure")

        coEvery {
            tmdbService.ensureTmdbId(any(), any(), any())
        } throws RuntimeException("Simulated tmdb failure")

        val result = aggregator.getRatings("ttcrash", "movie")
        assertNull(result)
    }
}
