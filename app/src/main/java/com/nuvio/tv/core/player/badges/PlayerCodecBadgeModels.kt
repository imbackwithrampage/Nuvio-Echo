package com.nuvio.tv.core.player.badges

import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Category of a codec badge displayed on the Player HUD overlay.
 */
enum class BadgeCategory {
    RESOLUTION,
    DYNAMIC_RANGE,
    AUDIO_CODEC,
    AUDIO_CHANNELS
}

/**
 * A single codec badge item to be rendered on the Player HUD overlay.
 *
 * @param key Canonical lookup key for asset packs (e.g. "4k", "1080p", "dolby_vision", "hdr10", "atmos", "truehd", "dts_hd_ma").
 * @param label Full descriptive label (e.g. "4K UHD", "Dolby Vision", "Dolby Atmos").
 * @param shortLabel Compact display label for glassmorphic chips when custom assets are not loaded (e.g. "4K", "VISION", "ATMOS").
 * @param category Category of this badge.
 * @param isPremium True if this badge represents a flagship feature (4K, Dolby Vision, Dolby Atmos, DTS:X) to give it a warm glass accent.
 * @param customAssetFile Optional custom image file (.svg, .png, .webp) resolved from an imported badge pack.
 */
@Immutable
data class StreamBadgeItem(
    val key: String,
    val label: String,
    val shortLabel: String,
    val category: BadgeCategory,
    val isPremium: Boolean = false,
    val customAssetFile: File? = null
)

/**
 * Encapsulates all parsed active stream badges for the player.
 */
@Immutable
data class PlayerStreamBadges(
    val resolution: StreamBadgeItem? = null,
    val dynamicRange: StreamBadgeItem? = null,
    val audioCodec: StreamBadgeItem? = null,
    val audioChannels: StreamBadgeItem? = null
) {
    val items: List<StreamBadgeItem> = listOfNotNull(resolution, dynamicRange, audioCodec, audioChannels)
}

/**
 * Metadata for an installed custom badge pack.
 */
@Immutable
data class BadgePackInfo(
    val id: String,
    val name: String,
    val directoryPath: String,
    val assetCount: Int = 0
)
