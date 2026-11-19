package com.nuvio.tv.core.player.badges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCodecBadgeParserTest {

    @Test
    fun parse_4kDolbyVisionAtmos_fromMpvProperties() {
        val badges = PlayerCodecBadgeParser.parse(
            videoWidth = 3840,
            videoHeight = 2160,
            gamma = "pq",
            pixelFormat = "yuv420p10le",
            videoCodec = "hevc",
            videoDecoderDesc = "hevc (dovi)",
            colorTransfer = null,
            audioCodec = "eac3_atmos",
            audioChannels = 8,
            audioTrackTitle = "English (Dolby Atmos)",
            streamTitle = "Sample.4K.DV.Atmos.mkv",
            streamDescription = null,
            filename = "Sample.4K.DV.Atmos.mkv"
        )

        assertNotNull(badges.resolution)
        assertEquals("4k", badges.resolution?.key)
        assertEquals("4K UHD", badges.resolution?.label)
        assertEquals("4K", badges.resolution?.shortLabel)
        assertTrue(badges.resolution?.isPremium == true)

        assertNotNull(badges.dynamicRange)
        assertEquals("dolby_vision", badges.dynamicRange?.key)
        assertEquals("Dolby Vision", badges.dynamicRange?.label)
        assertEquals("VISION", badges.dynamicRange?.shortLabel)
        assertTrue(badges.dynamicRange?.isPremium == true)

        assertNotNull(badges.audioCodec)
        assertEquals("atmos", badges.audioCodec?.key)
        assertEquals("Dolby Atmos", badges.audioCodec?.label)
        assertEquals("ATMOS", badges.audioCodec?.shortLabel)
        assertTrue(badges.audioCodec?.isPremium == true)

        assertNotNull(badges.audioChannels)
        assertEquals("7.1", badges.audioChannels?.key)
        assertEquals("7.1", badges.audioChannels?.label)
    }

    @Test
    fun parse_1080pHdr10TrueHd_fromDimensionsAndMime() {
        val badges = PlayerCodecBadgeParser.parse(
            videoWidth = 1920,
            videoHeight = 1080,
            gamma = "pq",
            pixelFormat = null,
            videoCodec = "hevc",
            videoDecoderDesc = null,
            colorTransfer = 16, // C.COLOR_TRANSFER_ST2084
            audioCodec = "truehd",
            audioChannels = 6,
            audioTrackTitle = "Surround 5.1",
            streamTitle = "Movie.1080p.HDR.TrueHD.mkv",
            streamDescription = null,
            filename = null
        )

        assertEquals("1080p", badges.resolution?.key)
        assertEquals("1080p", badges.resolution?.shortLabel)

        assertEquals("hdr10", badges.dynamicRange?.key)
        assertEquals("HDR10", badges.dynamicRange?.shortLabel)

        assertEquals("truehd", badges.audioCodec?.key)
        assertEquals("TRUEHD", badges.audioCodec?.shortLabel)

        assertEquals("5.1", badges.audioChannels?.key)
    }

    @Test
    fun parse_dtsHdMaAndHdr10Plus_fromTextTags() {
        val badges = PlayerCodecBadgeParser.parse(
            videoWidth = 0,
            videoHeight = 0,
            gamma = null,
            pixelFormat = null,
            videoCodec = null,
            videoDecoderDesc = null,
            colorTransfer = null,
            audioCodec = null,
            audioChannels = null,
            audioTrackTitle = null,
            streamTitle = "Awesome.Film.2160p.HDR10Plus.DTS-HD.MA.5.1.mkv",
            streamDescription = "2160p UHD REMUX with HDR10+ and DTS-HD MA",
            filename = "Awesome.Film.2160p.HDR10Plus.DTS-HD.MA.5.1.mkv"
        )

        assertEquals("4k", badges.resolution?.key)
        assertEquals("hdr10plus", badges.dynamicRange?.key)
        assertEquals("dts_hd_ma", badges.audioCodec?.key)
        assertEquals("DTS-HD", badges.audioCodec?.shortLabel)
        assertEquals("5.1", badges.audioChannels?.key)
    }

    @Test
    fun parse_dtsX_detectedProperly() {
        val badges = PlayerCodecBadgeParser.parse(
            videoWidth = 3840,
            videoHeight = 1600,
            gamma = "pq",
            pixelFormat = null,
            videoCodec = "hevc",
            videoDecoderDesc = null,
            colorTransfer = null,
            audioCodec = "dts:x",
            audioChannels = 8,
            audioTrackTitle = "English DTS:X",
            streamTitle = "Movie.4K.HDR.DTS-X.mkv",
            streamDescription = null,
            filename = null
        )

        assertEquals("4k", badges.resolution?.key)
        assertEquals("dts_x", badges.audioCodec?.key)
        assertEquals("DTS:X", badges.audioCodec?.shortLabel)
        assertTrue(badges.audioCodec?.isPremium == true)
        assertEquals("7.1", badges.audioChannels?.key)
    }

    @Test
    fun parse_eac3AndAc3AndAac_detectedProperly() {
        val eac3Badges = PlayerCodecBadgeParser.parse(
            videoWidth = 1280,
            videoHeight = 720,
            gamma = null,
            pixelFormat = null,
            videoCodec = "h264",
            videoDecoderDesc = null,
            colorTransfer = null,
            audioCodec = "eac3",
            audioChannels = 6,
            audioTrackTitle = null,
            streamTitle = "Show.S01E01.720p.WEB-DL.DDP5.1.mkv",
            streamDescription = null,
            filename = null
        )

        assertEquals("720p", eac3Badges.resolution?.key)
        assertEquals("eac3", eac3Badges.audioCodec?.key)
        assertEquals("DDP", eac3Badges.audioCodec?.shortLabel)
        assertEquals("5.1", eac3Badges.audioChannels?.key)

        val ac3Badges = PlayerCodecBadgeParser.parse(
            videoWidth = 720,
            videoHeight = 480,
            gamma = null,
            pixelFormat = null,
            videoCodec = "mpeg2",
            videoDecoderDesc = null,
            colorTransfer = null,
            audioCodec = "ac3",
            audioChannels = 2,
            audioTrackTitle = null,
            streamTitle = "OldMovie.DVDRip.AC3.2.0.avi",
            streamDescription = null,
            filename = null
        )

        assertEquals("sd", ac3Badges.resolution?.key)
        assertEquals("ac3", ac3Badges.audioCodec?.key)
        assertEquals("DD", ac3Badges.audioCodec?.shortLabel)
        assertEquals("2.0", ac3Badges.audioChannels?.key)
    }

    @Test
    fun parse_hlg_detectedProperly() {
        val badges = PlayerCodecBadgeParser.parse(
            videoWidth = 3840,
            videoHeight = 2160,
            gamma = "hlg",
            pixelFormat = null,
            videoCodec = "hevc",
            videoDecoderDesc = null,
            colorTransfer = 18,
            audioCodec = "aac",
            audioChannels = 2,
            audioTrackTitle = null,
            streamTitle = "Live.Broadcast.4K.HLG.mkv",
            streamDescription = null,
            filename = null
        )

        assertEquals("4k", badges.resolution?.key)
        assertEquals("hlg", badges.dynamicRange?.key)
        assertEquals("HLG", badges.dynamicRange?.shortLabel)
        assertEquals("aac", badges.audioCodec?.key)
        assertEquals("2.0", badges.audioChannels?.key)
    }

    @Test
    fun parse_handlesNullAndEmptyGracefully() {
        val badges = PlayerCodecBadgeParser.parse(
            videoWidth = null,
            videoHeight = null,
            gamma = null,
            pixelFormat = null,
            videoCodec = null,
            videoDecoderDesc = null,
            colorTransfer = null,
            audioCodec = null,
            audioChannels = null,
            audioTrackTitle = null,
            streamTitle = null,
            streamDescription = null,
            filename = null
        )

        assertNull(badges.resolution)
        assertNull(badges.dynamicRange)
        assertNull(badges.audioCodec)
        assertNull(badges.audioChannels)
        assertTrue(badges.items.isEmpty())
    }
}
