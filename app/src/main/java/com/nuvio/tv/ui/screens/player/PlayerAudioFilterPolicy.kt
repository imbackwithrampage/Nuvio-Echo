package com.nuvio.tv.ui.screens.player

import java.util.Locale
import kotlin.math.pow

/**
 * Audio filter policy for libmpv and FFmpeg audio processing.
 *
 * Provides:
 * 1. Night Mode (Dynamic Range Compression):
 *    FFmpeg `acompressor` filter that raises quiet dialogues and compresses loud peaks,
 *    preventing volume spikes without clipping.
 * 2. Multi-channel LFE and Center channel downmix matrix:
 *    Generates custom FFmpeg/mpv `pan` filter for 5.1 and 7.1 surround tracks
 *    downmixed to stereo with configurable LFE subwoofer level (0.0 to 2.0).
 *    Inactive for mono/stereo audio (channelCount <= 2).
 */
object PlayerAudioFilterPolicy {
    const val LFE_MIX_LEVEL_MIN = 0.0f
    const val LFE_MIX_LEVEL_MAX = 2.0f
    const val LFE_MIX_LEVEL_STEP = 0.1f
    const val LFE_MIX_LEVEL_DEFAULT = 0.0f

    // acompressor parameters for TV night mode:
    // threshold: 0.089 (approx -21 dB)
    // ratio: 4.0 (4:1 compression on sound above threshold)
    // attack: 50 ms (fast enough to catch explosions without clicking)
    // release: 500 ms (natural vocal decay)
    // makeup: 2.0 (+6 dB dialogue boost)
    const val NIGHT_MODE_ACOMPRESSOR_FILTER =
        "lavfi=[acompressor=threshold=0.089:ratio=4:attack=50:release=500:makeup=2]"

    /**
     * LFE channel control is only available when downmixing multi-channel surround sound (> 2 channels).
     * For mono and stereo tracks, LFE downmixing is inactive.
     */
    fun isLfeDownmixAvailable(channelCount: Int): Boolean = channelCount > 2

    /**
     * Returns the night mode audio filter string if enabled, or null if disabled.
     */
    fun buildNightModeFilter(enabled: Boolean): String? {
        return if (enabled) NIGHT_MODE_ACOMPRESSOR_FILTER else null
    }

    /**
     * Builds downmix pan filter for multi-channel audio (5.1 or 7.1) into stereo.
     * If channelCount <= 2, returns null (inactive for stereo/mono).
     */
    fun buildDownmixPanFilter(
        channelCount: Int,
        lfeMixLevel: Float,
        centerMixDb: Int = 0
    ): String? {
        if (!isLfeDownmixAvailable(channelCount)) return null

        val clampedLfe = lfeMixLevel.coerceIn(LFE_MIX_LEVEL_MIN, LFE_MIX_LEVEL_MAX)
        // Center channel coefficient: default is 0.7071 (-3 dB) adjusted by centerMixDb (-10 to +30 dB)
        val centerCoeff = (0.7071 * 10.0.pow(centerMixDb.coerceIn(-10, 30) / 20.0)).coerceIn(0.1, 4.0)
        val lfeCoeff = (clampedLfe * 0.7071).coerceIn(0.0, 2.0)

        val centerStr = String.format(Locale.US, "%.3f", centerCoeff)
        val lfeStr = String.format(Locale.US, "%.3f", lfeCoeff)

        return when {
            channelCount >= 8 -> {
                // 7.1 layout: c0=FL, c1=FR, c2=FC, c3=LFE, c4=BL, c5=BR, c6=SL, c7=SR
                "pan=stereo|c0=c0+${centerStr}*c2+0.5*c4+0.5*c6+${lfeStr}*c3|c1=c1+${centerStr}*c2+0.5*c5+0.5*c7+${lfeStr}*c3"
            }
            channelCount in 3..7 -> {
                // 5.1 layout: c0=FL, c1=FR, c2=FC, c3=LFE, c4=BL, c5=BR
                "pan=stereo|c0=c0+${centerStr}*c2+0.707*c4+${lfeStr}*c3|c1=c1+${centerStr}*c2+0.707*c5+${lfeStr}*c3"
            }
            else -> null
        }
    }

    /**
     * Builds the complete mpv audio filter string ("af").
     * Combines downmix filter (if multi-channel) and Night Mode DRC filter.
     * Returns empty string if no filters are active (clearing mpv af).
     */
    fun buildAudioFilterChain(
        nightMode: Boolean,
        lfeMixLevel: Float,
        centerMixDb: Int = 0,
        channelCount: Int = 2
    ): String {
        val downmix = buildDownmixPanFilter(channelCount, lfeMixLevel, centerMixDb)
        val night = buildNightModeFilter(nightMode)
        return listOfNotNull(downmix, night).joinToString(",")
    }

    /**
     * User-facing label for LFE level display.
     */
    fun formatLfeMixLevel(level: Float): String {
        val clamped = level.coerceIn(LFE_MIX_LEVEL_MIN, LFE_MIX_LEVEL_MAX)
        return if (clamped <= 0.001f) {
            "Off (0.0)"
        } else {
            String.format(Locale.US, "%.1f", clamped)
        }
    }
}
