package com.nuvio.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBackdropAnalyzerTest {

    private fun colorRgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun `uniform image produces center bias`() {
        val width = 128
        val height = 72
        val darkGray = colorRgb(60, 60, 60)
        val pixels = IntArray(width * height) { darkGray }

        val alignment = SmartBackdropAnalyzer.analyzePixels(pixels, width, height, "uniform_test")
        assertEquals(0f, alignment.horizontalBias, 0.1f)
        assertEquals(0f, alignment.verticalBias, 0.1f)
    }

    @Test
    fun `subject on right shifts biasX to positive`() {
        val width = 128
        val height = 72
        val black = colorRgb(0, 0, 0)
        val skinColor = colorRgb(220, 160, 130)
        val pixels = IntArray(width * height) { black }

        // Paint skin-toned face / subject on the far right (x from 90 to 120)
        for (y in 20 until 50) {
            val rowOffset = y * width
            for (x in 90 until 120) {
                pixels[rowOffset + x] = skinColor
            }
        }

        val alignment = SmartBackdropAnalyzer.analyzePixels(pixels, width, height, "right_subject_test")
        assertTrue("Expected positive biasX for subject on right, got ${alignment.horizontalBias}", alignment.horizontalBias > 0.1f)
    }

    @Test
    fun `subject on left shifts biasX to negative`() {
        val width = 128
        val height = 72
        val black = colorRgb(0, 0, 0)
        val skinColor = colorRgb(220, 160, 130)
        val pixels = IntArray(width * height) { black }

        // Paint skin-toned face / subject on the far left (x from 10 to 40)
        for (y in 20 until 50) {
            val rowOffset = y * width
            for (x in 10 until 40) {
                pixels[rowOffset + x] = skinColor
            }
        }

        val alignment = SmartBackdropAnalyzer.analyzePixels(pixels, width, height, "left_subject_test")
        assertTrue("Expected negative biasX for subject on left, got ${alignment.horizontalBias}", alignment.horizontalBias < -0.1f)
    }

    @Test
    fun `results are cached and retrievable`() {
        val width = 128
        val height = 72
        val gray = colorRgb(128, 128, 128)
        val pixels = IntArray(width * height) { gray }

        val computed = SmartBackdropAnalyzer.analyzePixels(pixels, width, height, "cached_key_1")
        val cached = SmartBackdropAnalyzer.getCached("cached_key_1")

        assertNotNull(cached)
        assertEquals(computed.horizontalBias, cached?.horizontalBias ?: 100f, 0.001f)
        assertEquals(computed.verticalBias, cached?.verticalBias ?: 100f, 0.001f)
    }

    @Test
    fun `bias values stay strictly within aesthetic bounds`() {
        val width = 128
        val height = 72
        val black = colorRgb(0, 0, 0)
        val white = colorRgb(255, 255, 255)
        val pixels = IntArray(width * height) { black }

        // Extremely intense bright pixel at the very corner
        pixels[(height - 1) * width + (width - 1)] = white

        val alignment = SmartBackdropAnalyzer.analyzePixels(pixels, width, height, "corner_test")
        assertTrue(alignment.horizontalBias >= -0.65f && alignment.horizontalBias <= 0.65f)
        assertTrue(alignment.verticalBias >= -0.4f && alignment.verticalBias <= 0.4f)
    }
}
