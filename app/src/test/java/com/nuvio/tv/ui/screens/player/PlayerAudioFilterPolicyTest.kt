package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAudioFilterPolicyTest {

    @Test
    fun isLfeDownmixAvailable_onlyActiveForSurroundTracks() {
        assertFalse("Mono audio has no LFE downmix", PlayerAudioFilterPolicy.isLfeDownmixAvailable(1))
        assertFalse("Stereo audio has no LFE downmix", PlayerAudioFilterPolicy.isLfeDownmixAvailable(2))
        assertTrue("5.1 surround audio has LFE downmix", PlayerAudioFilterPolicy.isLfeDownmixAvailable(6))
        assertTrue("7.1 surround audio has LFE downmix", PlayerAudioFilterPolicy.isLfeDownmixAvailable(8))
    }

    @Test
    fun buildNightModeFilter_togglesFilterString() {
        assertNull("Night mode disabled should yield null", PlayerAudioFilterPolicy.buildNightModeFilter(false))
        val filter = PlayerAudioFilterPolicy.buildNightModeFilter(true)
        assertEquals(PlayerAudioFilterPolicy.NIGHT_MODE_ACOMPRESSOR_FILTER, filter)
        assertTrue(filter?.contains("acompressor") == true)
        assertTrue(filter?.contains("threshold=0.089") == true)
    }

    @Test
    fun buildDownmixPanFilter_returnsNullForMonoAndStereo() {
        assertNull(PlayerAudioFilterPolicy.buildDownmixPanFilter(channelCount = 1, lfeMixLevel = 1.0f))
        assertNull(PlayerAudioFilterPolicy.buildDownmixPanFilter(channelCount = 2, lfeMixLevel = 1.0f))
    }

    @Test
    fun buildDownmixPanFilter_generates51DownmixWithLfe() {
        val filter = PlayerAudioFilterPolicy.buildDownmixPanFilter(
            channelCount = 6,
            lfeMixLevel = 1.0f,
            centerMixDb = 0
        )
        // 5.1 layout contains stereo pan with center and LFE coefficients
        assertTrue("Expected 5.1 pan matrix, got: $filter", filter != null && filter.startsWith("pan=stereo|c0="))
        assertTrue("LFE coeff must be present", filter?.contains("*c3") == true)
    }

    @Test
    fun buildDownmixPanFilter_generates71DownmixWithLfe() {
        val filter = PlayerAudioFilterPolicy.buildDownmixPanFilter(
            channelCount = 8,
            lfeMixLevel = 1.5f,
            centerMixDb = 2
        )
        assertTrue("Expected 7.1 pan matrix, got: $filter", filter != null && filter.contains("0.5*c6"))
        assertTrue("LFE coeff must be present", filter?.contains("*c3") == true)
    }

    @Test
    fun buildDownmixPanFilter_clampsLfeMixLevel() {
        val filterNegative = PlayerAudioFilterPolicy.buildDownmixPanFilter(channelCount = 6, lfeMixLevel = -1.0f)
        val filterZero = PlayerAudioFilterPolicy.buildDownmixPanFilter(channelCount = 6, lfeMixLevel = 0.0f)
        assertEquals("Negative LFE should clamp to zero", filterZero, filterNegative)

        val filterMax = PlayerAudioFilterPolicy.buildDownmixPanFilter(channelCount = 6, lfeMixLevel = 5.0f)
        val filterTwo = PlayerAudioFilterPolicy.buildDownmixPanFilter(channelCount = 6, lfeMixLevel = 2.0f)
        assertEquals("Exceeded LFE should clamp to 2.0", filterTwo, filterMax)
    }

    @Test
    fun buildAudioFilterChain_handlesCombinationsAndEdgeCases() {
        // Case 1: Stereo track, Night Mode off, LFE default -> Empty string (clean af reset)
        val empty = PlayerAudioFilterPolicy.buildAudioFilterChain(
            nightMode = false,
            lfeMixLevel = 0.0f,
            centerMixDb = 0,
            channelCount = 2
        )
        assertEquals("", empty)

        // Case 2: Stereo track, Night Mode ON -> Only acompressor filter, no invalid pan syntax
        val stereoNight = PlayerAudioFilterPolicy.buildAudioFilterChain(
            nightMode = true,
            lfeMixLevel = 1.0f,
            centerMixDb = 0,
            channelCount = 2
        )
        assertEquals(PlayerAudioFilterPolicy.NIGHT_MODE_ACOMPRESSOR_FILTER, stereoNight)

        // Case 3: Surround 5.1, Night Mode OFF, LFE 1.0 -> Only downmix pan filter
        val surroundDownmixOnly = PlayerAudioFilterPolicy.buildAudioFilterChain(
            nightMode = false,
            lfeMixLevel = 1.0f,
            centerMixDb = 0,
            channelCount = 6
        )
        assertTrue(surroundDownmixOnly.startsWith("pan=stereo|"))
        assertFalse(surroundDownmixOnly.contains("acompressor"))

        // Case 4: Surround 5.1, Night Mode ON, LFE 1.0 -> Combined with comma
        val combined = PlayerAudioFilterPolicy.buildAudioFilterChain(
            nightMode = true,
            lfeMixLevel = 1.0f,
            centerMixDb = 0,
            channelCount = 6
        )
        assertTrue(combined.contains("pan=stereo|"))
        assertTrue(combined.contains(PlayerAudioFilterPolicy.NIGHT_MODE_ACOMPRESSOR_FILTER))
        assertTrue("Filters must be separated by comma", combined.contains(","))
    }

    @Test
    fun formatLfeMixLevel_formatsCorrectly() {
        assertEquals("Off (0.0)", PlayerAudioFilterPolicy.formatLfeMixLevel(0.0f))
        assertEquals("Off (0.0)", PlayerAudioFilterPolicy.formatLfeMixLevel(-0.5f))
        assertEquals("1.0", PlayerAudioFilterPolicy.formatLfeMixLevel(1.0f))
        assertEquals("1.5", PlayerAudioFilterPolicy.formatLfeMixLevel(1.5f))
        assertEquals("2.0", PlayerAudioFilterPolicy.formatLfeMixLevel(2.0f))
        assertEquals("2.0", PlayerAudioFilterPolicy.formatLfeMixLevel(3.5f))
    }
}
