package com.nuvio.tv.data.trailer

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TrailerCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        cacheDir = tempFolder.newFolder("app_cache")
        mockContext = mockk<Context>()
        every { mockContext.cacheDir } returns cacheDir
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private class TestClock(var currentInstant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = currentInstant
        fun advance(duration: Duration) {
            currentInstant = currentInstant.plus(duration)
        }
    }

    @Test
    fun `returns cached trailer source within 3 hour TTL`() = runTest {
        val testClock = TestClock(Instant.parse("2026-01-01T12:00:00Z"))
        val manager = TrailerCacheManager(mockContext, testClock)

        val sampleSource = TrailerPlaybackSource(
            videoUrl = "https://youtube.com/video123",
            audioUrl = "https://youtube.com/audio123"
        )

        manager.put("video123", sampleSource)

        // Advance 2 hours (within 3h TTL)
        testClock.advance(Duration.ofHours(2))

        val cached = manager.get("video123")
        assertNotNull(cached)
        assertEquals("https://youtube.com/video123", cached?.videoUrl)
        assertEquals("https://youtube.com/audio123", cached?.audioUrl)
    }

    @Test
    fun `expires and evicts trailer source after 3 hours TTL`() = runTest {
        val testClock = TestClock(Instant.parse("2026-01-01T12:00:00Z"))
        val manager = TrailerCacheManager(mockContext, testClock)

        val sampleSource = TrailerPlaybackSource(
            videoUrl = "https://youtube.com/video123",
            audioUrl = "https://youtube.com/audio123"
        )

        manager.put("video123", sampleSource)

        // Advance 3 hours and 1 minute (exceeding strict 3h TTL)
        testClock.advance(Duration.ofHours(3).plusMinutes(1))

        val cached = manager.get("video123")
        assertNull(cached)
    }

    @Test
    fun `invalidate removes entry from both memory and disk immediately`() = runTest {
        val testClock = TestClock(Instant.parse("2026-01-01T12:00:00Z"))
        val manager = TrailerCacheManager(mockContext, testClock)

        val sampleSource = TrailerPlaybackSource(videoUrl = "https://youtube.com/video456")
        manager.put("video456", sampleSource)

        assertNotNull(manager.get("video456"))

        // HTTP 403/410 invalidation
        manager.invalidate("video456")

        assertNull(manager.get("video456"))
    }

    @Test
    fun `loads persisted entries from disk on cold cache instance`() = runTest {
        val testClock = TestClock(Instant.parse("2026-01-01T12:00:00Z"))
        val manager1 = TrailerCacheManager(mockContext, testClock)

        val sampleSource = TrailerPlaybackSource(
            videoUrl = "https://youtube.com/persisted",
            audioUrl = "https://youtube.com/audio_p"
        )
        manager1.put("persisted_key", sampleSource)

        // Cold second instance with clean memory cache
        val manager2 = TrailerCacheManager(mockContext, testClock)
        val loaded = manager2.get("persisted_key")

        assertNotNull(loaded)
        assertEquals("https://youtube.com/persisted", loaded?.videoUrl)
        assertEquals("https://youtube.com/audio_p", loaded?.audioUrl)
    }

    @Test
    fun `purgeExpired removes expired files from disk`() = runTest {
        val testClock = TestClock(Instant.parse("2026-01-01T12:00:00Z"))
        val manager = TrailerCacheManager(mockContext, testClock)

        manager.put("entry1", TrailerPlaybackSource("https://v1"))
        manager.put("entry2", TrailerPlaybackSource("https://v2"))

        // Advance 3.5 hours
        testClock.advance(Duration.ofMinutes(210))

        manager.purgeExpired()

        val trailerCacheFolder = File(cacheDir, "trailer_cache")
        val remainingFiles = trailerCacheFolder.listFiles()?.filter { it.isFile } ?: emptyList()
        assertEquals(0, remainingFiles.size)
    }
}
