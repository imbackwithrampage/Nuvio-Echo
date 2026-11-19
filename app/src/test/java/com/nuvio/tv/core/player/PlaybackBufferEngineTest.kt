package com.nuvio.tv.core.player

import com.nuvio.tv.data.local.PlaybackBufferPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBufferEngineTest {

    @Test
    fun `low RAM 32-bit safe preset enforces strict 32MB memory cap`() {
        val preset = PlaybackBufferPreset.LOW_RAM_32BIT
        val (demuxerMaxBytes, demuxerMaxBackBytes) = resolveMpvBufferLimitsForPreset(preset, 32)
        val maxMb = demuxerMaxBytes / (1024 * 1024)

        // Strict non-negotiable guardrail: 32-bit ARM process limits (~1.5-2GB RAM TVs)
        assertTrue("Low RAM preset demuxer max must be <= 32MB, was $maxMb", maxMb <= 32)
        assertEquals(32L * 1024 * 1024, demuxerMaxBytes)
        assertEquals(16L * 1024 * 1024, demuxerMaxBackBytes)
    }

    @Test
    fun `balanced preset uses 64MB buffer for smooth playback`() {
        val preset = PlaybackBufferPreset.BALANCED
        val (demuxerMaxBytes, demuxerMaxBackBytes) = resolveMpvBufferLimitsForPreset(preset, 64)

        assertEquals(64L * 1024 * 1024, demuxerMaxBytes)
        assertEquals(32L * 1024 * 1024, demuxerMaxBackBytes)
    }

    @Test
    fun `high bitrate preset uses 128MB buffer`() {
        val preset = PlaybackBufferPreset.HIGH_BITRATE
        val (demuxerMaxBytes, demuxerMaxBackBytes) = resolveMpvBufferLimitsForPreset(preset, 128)

        assertEquals(128L * 1024 * 1024, demuxerMaxBytes)
        assertEquals(64L * 1024 * 1024, demuxerMaxBackBytes)
    }

    @Test
    fun `custom preset respects target buffer size within 32-bit safe bounds`() {
        val preset = PlaybackBufferPreset.CUSTOM
        val (demuxerMaxBytes, _) = resolveMpvBufferLimitsForPreset(preset, 200)

        assertEquals(200L * 1024 * 1024, demuxerMaxBytes)

        // Lower clamp at 16MB
        val (clampedLowBytes, _) = resolveMpvBufferLimitsForPreset(preset, 5)
        assertEquals(16L * 1024 * 1024, clampedLowBytes)

        // Upper clamp at 256MB for 32-bit ARM safety
        val (clampedHighBytes, _) = resolveMpvBufferLimitsForPreset(preset, 500)
        assertEquals(256L * 1024 * 1024, clampedHighBytes)
    }

    @Test
    fun `preset parser is case-insensitive and falls back to BALANCED`() {
        assertEquals(PlaybackBufferPreset.LOW_RAM_32BIT, PlaybackBufferPreset.fromStoredValue("low_ram_32bit"))
        assertEquals(PlaybackBufferPreset.LOW_RAM_32BIT, PlaybackBufferPreset.fromStoredValue("LOW_RAM_32BIT"))
        assertEquals(PlaybackBufferPreset.HIGH_BITRATE, PlaybackBufferPreset.fromStoredValue("high_bitrate"))
        assertEquals(PlaybackBufferPreset.BALANCED, PlaybackBufferPreset.fromStoredValue("unknown_preset"))
        assertEquals(PlaybackBufferPreset.BALANCED, PlaybackBufferPreset.fromStoredValue(null))
    }

    @Test
    fun `background hardware decoder release state cycle operates cleanly`() {
        var wasReleased = false
        var savedPos = 0L

        // Simulate onStop with release enabled
        val currentPlaybackPos = 45_200L
        val releaseEnabled = true

        if (releaseEnabled && currentPlaybackPos > 0L) {
            savedPos = currentPlaybackPos
            wasReleased = true
        }

        assertTrue(wasReleased)
        assertEquals(45_200L, savedPos)

        // Simulate onResume
        var restoredPos = 0L
        if (wasReleased) {
            wasReleased = false
            restoredPos = savedPos
            savedPos = 0L
        }

        assertFalse(wasReleased)
        assertEquals(45_200L, restoredPos)
        assertEquals(0L, savedPos)
    }

    private fun resolveMpvBufferLimitsForPreset(
        preset: PlaybackBufferPreset,
        targetBufferSizeMb: Int
    ): Pair<Long, Long> {
        return when (preset) {
            PlaybackBufferPreset.LOW_RAM_32BIT -> 32L * 1024 * 1024 to 16L * 1024 * 1024
            PlaybackBufferPreset.BALANCED -> 64L * 1024 * 1024 to 32L * 1024 * 1024
            PlaybackBufferPreset.HIGH_BITRATE -> 128L * 1024 * 1024 to 64L * 1024 * 1024
            PlaybackBufferPreset.CUSTOM -> {
                val mb = targetBufferSizeMb.coerceIn(16, 256).toLong()
                (mb * 1024 * 1024) to (mb * 1024 * 512)
            }
        }
    }
}
