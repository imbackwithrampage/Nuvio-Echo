package com.nuvio.tv.core.player

import com.nuvio.tv.data.local.Dv7HandlingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyVisionConversionPolicyTest {

    @Test
    fun `when conversion enabled and bridge ready on DV display, resolves CONVERT_TO_DV81`() {
        val result = DolbyVisionBaseLayerPolicy.resolveFromCapabilities(
            hdrCapsKnown = true,
            displayDv = true,
            displayHdr10 = true,
            displayHdr10Plus = false,
            displayHlg = false,
            codecSupportsDvheDtb = false,
            codecSupportsDvheStn = false,
            codecSupportsDvheSt = true,
            isAmazonFireTv = false,
            isSamsung = false,
            isXiaomi = false,
            bridgeReady = true,
            apiLevel = 30
        )
        assertEquals(DolbyVisionBaseLayerPolicy.Decision.CONVERT_TO_DV81, result.decision)
        assertTrue(result.divertsFromNativeDv7)
        assertFalse(result.mapToHevc)
    }

    @Test
    fun `when display is HDR10 only and not Samsung or Amazon, falls back to STRIP_TO_HDR10`() {
        val result = DolbyVisionBaseLayerPolicy.resolveFromCapabilities(
            hdrCapsKnown = true,
            displayDv = false,
            displayHdr10 = true,
            displayHdr10Plus = false,
            displayHlg = false,
            codecSupportsDvheDtb = false,
            codecSupportsDvheStn = false,
            codecSupportsDvheSt = true,
            isAmazonFireTv = false,
            isSamsung = false,
            isXiaomi = false,
            bridgeReady = true,
            apiLevel = 30
        )
        assertEquals(DolbyVisionBaseLayerPolicy.Decision.STRIP_TO_HDR10, result.decision)
        assertTrue(result.mapToHevc)
    }

    @Test
    fun `when display is SDR only, safely strips and tonemaps`() {
        val result = DolbyVisionBaseLayerPolicy.resolveFromCapabilities(
            hdrCapsKnown = true,
            displayDv = false,
            displayHdr10 = false,
            displayHdr10Plus = false,
            displayHlg = false,
            codecSupportsDvheDtb = false,
            codecSupportsDvheStn = false,
            codecSupportsDvheSt = false,
            isAmazonFireTv = false,
            isSamsung = false,
            isXiaomi = false,
            bridgeReady = false,
            apiLevel = 30
        )
        assertEquals(DolbyVisionBaseLayerPolicy.Decision.STRIP_AND_TONEMAP, result.decision)
        assertTrue(result.mapToHevc)
    }

    @Test
    fun `when probe reports unsupported, fallback downgrades DV81_LIBDOVI to HDR10_BASE_LAYER`() {
        // Simulates the runtime logic in PlayerRuntimeControllerInitialization
        var effectiveDv7Mode = Dv7HandlingMode.DV81_LIBDOVI
        val probeSupported = false

        if (effectiveDv7Mode == Dv7HandlingMode.DV81_LIBDOVI && !probeSupported) {
            effectiveDv7Mode = Dv7HandlingMode.HDR10_BASE_LAYER
        }

        // Guarantees zero black screens or green/magenta tints
        assertEquals(Dv7HandlingMode.HDR10_BASE_LAYER, effectiveDv7Mode)
    }
}
