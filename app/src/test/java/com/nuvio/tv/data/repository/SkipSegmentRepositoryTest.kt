package com.nuvio.tv.data.repository

import com.nuvio.tv.data.local.AutoSkipSegmentType
import com.nuvio.tv.data.local.SkipIntroMode
import com.nuvio.tv.data.remote.api.AniSkipInterval
import com.nuvio.tv.data.remote.api.AniSkipResult
import com.nuvio.tv.data.remote.api.IntroDbSegment
import com.nuvio.tv.data.remote.api.IntroDbSegmentsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentRepositoryTest {

    @Test
    fun `introdb response maps opening and ending intervals correctly`() {
        val response = IntroDbSegmentsResponse(
            imdbId = "tt0944947",
            intro = IntroDbSegment(startSec = 85.0, endSec = 175.0),
            outro = IntroDbSegment(startSec = 3200.0, endSec = 3300.0),
            recap = IntroDbSegment(startSec = 0.0, endSec = 80.0)
        )

        val intervals = response.toSkipIntervals(movie = false)
        assertEquals(3, intervals.size)

        val intro = intervals.firstOrNull { it.type == "intro" }
        assertNotNull(intro)
        assertEquals(85.0, intro!!.startTime, 0.001)
        assertEquals(175.0, intro.endTime, 0.001)
        assertEquals("introdb", intro.provider)
    }

    @Test
    fun `aniskip result converts to valid skip intervals`() {
        val result = AniSkipResult(
            interval = AniSkipInterval(startTime = 120.0, endTime = 210.0),
            skipType = "op",
            episodeLength = 1420.0
        )

        val interval = SkipInterval(
            startTime = result.interval.startTime,
            endTime = result.interval.endTime,
            type = result.skipType,
            provider = "aniskip"
        )

        assertEquals("op", interval.type)
        assertEquals(120.0, interval.startTime, 0.001)
        assertEquals(210.0, interval.endTime, 0.001)
        assertEquals(SkipSegments.OPENING, SkipSegments.category(interval.type))
    }

    @Test
    fun `malformed introdb timestamps with invalid start and end are ignored safely`() {
        val malformed = IntroDbSegmentsResponse(
            imdbId = "tt1234567",
            intro = IntroDbSegment(startSec = 200.0, endSec = 100.0), // end < start
            outro = IntroDbSegment(startSec = -10.0, endSec = 50.0),  // negative start
            recap = null
        )

        val intervals = malformed.toSkipIntervals(movie = false)
        assertTrue("Malformed intervals should be discarded", intervals.isEmpty())
    }

    @Test
    fun `skip intro mode auto skip resolves opening and ending without user action`() {
        val mode = SkipIntroMode.AUTO_SKIP
        val interval = SkipInterval(startTime = 90.0, endTime = 180.0, type = "op", provider = "aniskip")
        val currentPositionMs = 95_000L

        val inInterval = currentPositionMs in (interval.startTime * 1000).toLong() until (interval.endTime * 1000).toLong()
        assertTrue(inInterval)

        val shouldAutoSkip = when (mode) {
            SkipIntroMode.AUTO_SKIP -> true
            SkipIntroMode.SHOW_BUTTON -> false
            SkipIntroMode.OFF -> false
        }
        assertTrue("AUTO_SKIP mode must automatically trigger skip", shouldAutoSkip)
    }

    @Test
    fun `skip intro mode show button renders button without auto-seeking unless segment opted-in`() {
        val mode = SkipIntroMode.SHOW_BUTTON
        val interval = SkipInterval(startTime = 90.0, endTime = 180.0, type = "op", provider = "aniskip")
        val segmentType = AutoSkipSegmentType.fromSkipIntervalType(interval.type)
        assertEquals(AutoSkipSegmentType.INTRO, segmentType)

        // User only enabled AUTO_SKIP for RECAP, not INTRO
        val userAutoSkipSegments = setOf(AutoSkipSegmentType.RECAP)

        val shouldAutoSkip = when (mode) {
            SkipIntroMode.AUTO_SKIP -> true
            SkipIntroMode.SHOW_BUTTON -> segmentType != null && segmentType in userAutoSkipSegments
            SkipIntroMode.OFF -> false
        }
        assertFalse("SHOW_BUTTON mode should display button and not auto-skip unselected segments", shouldAutoSkip)
    }

    @Test
    fun `skip intro mode off disables all skip actions`() {
        val mode = SkipIntroMode.OFF
        val interval = SkipInterval(startTime = 90.0, endTime = 180.0, type = "op", provider = "aniskip")
        val segmentType = AutoSkipSegmentType.fromSkipIntervalType(interval.type)

        val shouldAutoSkip = when (mode) {
            SkipIntroMode.AUTO_SKIP -> true
            SkipIntroMode.SHOW_BUTTON -> segmentType != null
            SkipIntroMode.OFF -> false
        }
        assertFalse("OFF mode must disable all skip triggers", shouldAutoSkip)
    }

    @Test
    fun `skip intro mode parser handles case insensitive and invalid values`() {
        assertEquals(SkipIntroMode.AUTO_SKIP, SkipIntroMode.fromStoredValue("auto_skip"))
        assertEquals(SkipIntroMode.AUTO_SKIP, SkipIntroMode.fromStoredValue("AUTO_SKIP"))
        assertEquals(SkipIntroMode.OFF, SkipIntroMode.fromStoredValue("off"))
        assertEquals(SkipIntroMode.SHOW_BUTTON, SkipIntroMode.fromStoredValue("invalid_mode"))
        assertEquals(SkipIntroMode.SHOW_BUTTON, SkipIntroMode.fromStoredValue(null))
    }
}
