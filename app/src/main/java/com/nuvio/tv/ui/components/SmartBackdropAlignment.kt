package com.nuvio.tv.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TAG = "SmartBackdropAlignment"
private const val THUMB_WIDTH = 128
private const val THUMB_HEIGHT = 72
private const val ALIGNMENT_CACHE_SIZE = 200

/**
 * Lightweight, zero-allocation cache and analyzer for focal-point horizontal/vertical bias in wide backdrops.
 */
object SmartBackdropAnalyzer {
    private val alignmentCache = object : LinkedHashMap<String, BiasAlignment>(ALIGNMENT_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BiasAlignment>?) = size > ALIGNMENT_CACHE_SIZE
    }

    fun getCached(url: String?): BiasAlignment? {
        if (url.isNullOrBlank()) return null
        return synchronized(alignmentCache) {
            alignmentCache[url]
        }
    }

    fun putCached(url: String, alignment: BiasAlignment) {
        synchronized(alignmentCache) {
            alignmentCache[url] = alignment
        }
    }

    /**
     * Analyzes downsampled pixel array on Dispatchers.Default.
     * Computes focal centroid (combining skin-tone face detection and local high-contrast edges)
     * and maps it to a Compose BiasAlignment(biasX, biasY).
     */
    fun analyzePixels(pixels: IntArray, width: Int, height: Int, url: String? = null): BiasAlignment {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return BiasAlignment(0f, 0f)

        var totalWeight = 0.0
        var weightedX = 0.0
        var weightedY = 0.0

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val pixel = pixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Luminance calculation
                val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

                // Skin-tone detection (faces/characters in movie backdrops)
                val isSkinTone = r > 95 && g > 40 && b > 20 &&
                    (maxOf(r, g, b) - minOf(r, g, b) > 15) &&
                    abs(r - g) > 15 && r > g && r > b

                // Local contrast detection (edge energy)
                val leftLum = if (x > 0) {
                    val p = pixels[rowOffset + x - 1]
                    (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
                } else lum

                val topLum = if (y > 0) {
                    val p = pixels[(y - 1) * width + x]
                    (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
                } else lum

                val edgeEnergy = abs(lum - leftLum) + abs(lum - topLum)

                // Base weight + contrast energy + skin-tone bonus
                val weight = 0.2f + (edgeEnergy * 2.5f) + (if (isSkinTone) 3.5f else 0.0f)

                totalWeight += weight
                weightedX += weight * x
                weightedY += weight * y
            }
        }

        if (totalWeight <= 0.0) {
            val fallback = BiasAlignment(0f, 0f)
            url?.let { putCached(it, fallback) }
            return fallback
        }

        val focalNormX = ((weightedX / totalWeight) / width.toDouble()).toFloat()
        val focalNormY = ((weightedY / totalWeight) / height.toDouble()).toFloat()

        // Convert 0..1 normalized coordinate to Compose -1..1 bias range
        // Clamped to avoid clipping too far past aesthetic bounds
        val biasX = ((focalNormX - 0.5f) * 2f).coerceIn(-0.65f, 0.65f)
        val biasY = ((focalNormY - 0.5f) * 2f).coerceIn(-0.4f, 0.4f)

        val alignment = BiasAlignment(biasX, biasY)
        url?.let { putCached(it, alignment) }
        return alignment
    }

    /**
     * Analyzes a downsampled 128x72 bitmap on Dispatchers.Default.
     */
    suspend fun analyzeBitmap(bitmap: Bitmap, url: String?): BiasAlignment = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return@withContext BiasAlignment(0f, 0f)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        analyzePixels(pixels, width, height, url)
    }

    /**
     * Resolves smart alignment for an image URL asynchronously using Coil's memory/disk cache.
     */
    suspend fun resolveAlignment(context: Context, url: String): BiasAlignment = withContext(Dispatchers.IO) {
        getCached(url)?.let { return@withContext it }

        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(Size(THUMB_WIDTH, THUMB_HEIGHT))
                .scale(Scale.FIT)
                .allowHardware(false) // Required for getPixels()
                .build()

            val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.image as? BitmapImage)?.bitmap

            if (bitmap != null) {
                analyzeBitmap(bitmap, url)
            } else {
                val fallback = BiasAlignment(0f, 0f)
                putCached(url, fallback)
                fallback
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to analyze backdrop framing for $url: ${e.message}")
            val fallback = BiasAlignment(0f, 0f)
            putCached(url, fallback)
            fallback
        }
    }
}

/**
 * Composable helper returning a Coil-compatible SmartBackdropAlignment.
 * Dynamically shifts horizontal/vertical Alignment(biasX, biasY) when cropping into
 * Nuvio-Echo's Hero Banner and Highlighted/Grand cards so faces and key artwork are centered.
 */
@Composable
fun rememberSmartBackdropAlignment(
    imageUrl: String?,
    defaultAlignment: Alignment = Alignment.Center
): Alignment {
    if (imageUrl.isNullOrBlank()) return defaultAlignment

    val context = LocalContext.current
    var alignment by remember(imageUrl) {
        mutableStateOf<Alignment>(
            SmartBackdropAnalyzer.getCached(imageUrl) ?: defaultAlignment
        )
    }

    LaunchedEffect(imageUrl) {
        if (SmartBackdropAnalyzer.getCached(imageUrl) == null) {
            val smartBias = SmartBackdropAnalyzer.resolveAlignment(context, imageUrl)
            alignment = smartBias
        }
    }

    return alignment
}
