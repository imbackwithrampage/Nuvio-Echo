package com.nuvio.tv.core.player.badges

import java.io.File
import java.util.Locale

/**
 * Pure parser that inspects active stream properties from libmpv and ExoPlayer
 * and constructs [PlayerStreamBadges] with resolved custom assets or glassmorphic fallbacks.
 */
object PlayerCodecBadgeParser {

    private val REGEX_4K = Regex("\\b(4k|2160p|uhd|ultra[\\s._-]?hd)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_1440P = Regex("\\b(1440p|2k|qhd)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_1080P = Regex("\\b(1080p|fhd|full[\\s._-]?hd)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_720P = Regex("\\b(720p|hd)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_SD = Regex("\\b(480p|576p|sd)\\b", RegexOption.IGNORE_CASE)

    private val REGEX_DOVI = Regex("\\b(dovi|dvh1|dvhe|dolby[\\s._-]?vision|\\bdv\\b)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_HDR10PLUS = Regex("\\b(hdr10\\+|hdr10plus|hdr10_plus|smpte2094-40)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_HDR10 = Regex("\\b(hdr10|hdr)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_HLG = Regex("\\b(hlg)\\b", RegexOption.IGNORE_CASE)

    private val REGEX_ATMOS = Regex("\\b(atmos|eac3_atmos|truehd_atmos|eac3-joc)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_TRUEHD = Regex("\\b(truehd|true-hd|true_hd)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_DTS_X = Regex("\\b(dts:x|dts-x|dtsx)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_DTS_HD_MA = Regex("\\b(dts-hd[\\s._-]?ma|dts[\\s._-]?hd|dtshd)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_DTS = Regex("\\b(dts)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_EAC3 = Regex("\\b(eac3|e-ac3|e_ac3|ddp|dd\\+|dolby[\\s._-]?digital[\\s._-]?plus)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_AC3 = Regex("\\b(ac3|ac-3|\\bdd\\b|dolby[\\s._-]?digital)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_AAC = Regex("\\b(aac)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_FLAC = Regex("\\b(flac)\\b", RegexOption.IGNORE_CASE)
    private val REGEX_OPUS = Regex("\\b(opus)\\b", RegexOption.IGNORE_CASE)

    fun parse(
        videoWidth: Int?,
        videoHeight: Int?,
        gamma: String?,
        pixelFormat: String?,
        videoCodec: String?,
        videoDecoderDesc: String?,
        colorTransfer: Int?,
        audioCodec: String?,
        audioChannels: Int?,
        audioTrackTitle: String?,
        streamTitle: String?,
        streamDescription: String?,
        filename: String?,
        badgePackManager: PlayerBadgePackManager? = null,
        activePackDir: File? = null
    ): PlayerStreamBadges {
        val textContext = listOfNotNull(streamTitle, streamDescription, filename).joinToString(" ")

        val resolution = resolveResolution(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            textContext = textContext,
            badgePackManager = badgePackManager,
            activePackDir = activePackDir
        )

        val dynamicRange = resolveDynamicRange(
            gamma = gamma,
            pixelFormat = pixelFormat,
            videoCodec = videoCodec,
            videoDecoderDesc = videoDecoderDesc,
            colorTransfer = colorTransfer,
            textContext = textContext,
            badgePackManager = badgePackManager,
            activePackDir = activePackDir
        )

        val audioItem = resolveAudioCodec(
            audioCodec = audioCodec,
            audioTrackTitle = audioTrackTitle,
            textContext = textContext,
            badgePackManager = badgePackManager,
            activePackDir = activePackDir
        )

        val channelItem = resolveAudioChannels(
            channels = audioChannels,
            audioTrackTitle = audioTrackTitle,
            textContext = textContext,
            badgePackManager = badgePackManager,
            activePackDir = activePackDir
        )

        return PlayerStreamBadges(
            resolution = resolution,
            dynamicRange = dynamicRange,
            audioCodec = audioItem,
            audioChannels = channelItem
        )
    }

    private fun resolveResolution(
        videoWidth: Int?,
        videoHeight: Int?,
        textContext: String,
        badgePackManager: PlayerBadgePackManager?,
        activePackDir: File?
    ): StreamBadgeItem? {
        val w = videoWidth ?: 0
        val h = videoHeight ?: 0

        val (key, label, shortLabel, isPremium) = when {
            w >= 3800 || h >= 2000 -> Quad("4k", "4K UHD", "4K", true)
            w >= 2500 || h >= 1400 -> Quad("1440p", "1440p QHD", "1440p", false)
            w >= 1900 || h >= 1000 -> Quad("1080p", "1080p FHD", "1080p", false)
            w >= 1200 || h >= 700 -> Quad("720p", "720p HD", "720p", false)
            w > 0 && h > 0 -> Quad("sd", "SD", "SD", false)
            REGEX_4K.containsMatchIn(textContext) -> Quad("4k", "4K UHD", "4K", true)
            REGEX_1440P.containsMatchIn(textContext) -> Quad("1440p", "1440p QHD", "1440p", false)
            REGEX_1080P.containsMatchIn(textContext) -> Quad("1080p", "1080p FHD", "1080p", false)
            REGEX_720P.containsMatchIn(textContext) -> Quad("720p", "720p HD", "720p", false)
            REGEX_SD.containsMatchIn(textContext) -> Quad("sd", "SD", "SD", false)
            else -> return null
        }

        val assetFile = badgePackManager?.resolveBadgeFile(activePackDir, key)
        return StreamBadgeItem(
            key = key,
            label = label,
            shortLabel = shortLabel,
            category = BadgeCategory.RESOLUTION,
            isPremium = isPremium,
            customAssetFile = assetFile
        )
    }

    private fun resolveDynamicRange(
        gamma: String?,
        pixelFormat: String?,
        videoCodec: String?,
        videoDecoderDesc: String?,
        colorTransfer: Int?,
        textContext: String,
        badgePackManager: PlayerBadgePackManager?,
        activePackDir: File?
    ): StreamBadgeItem? {
        val videoContext = listOfNotNull(gamma, pixelFormat, videoCodec, videoDecoderDesc, textContext)
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        val isDolbyVision = REGEX_DOVI.containsMatchIn(videoContext) ||
            videoCodec?.contains("dvh1", ignoreCase = true) == true ||
            videoCodec?.contains("dvhe", ignoreCase = true) == true

        if (isDolbyVision) {
            val key = "dolby_vision"
            return StreamBadgeItem(
                key = key,
                label = "Dolby Vision",
                shortLabel = "VISION",
                category = BadgeCategory.DYNAMIC_RANGE,
                isPremium = true,
                customAssetFile = badgePackManager?.resolveBadgeFile(activePackDir, key)
            )
        }

        if (REGEX_HDR10PLUS.containsMatchIn(videoContext)) {
            val key = "hdr10plus"
            return StreamBadgeItem(
                key = key,
                label = "HDR10+",
                shortLabel = "HDR10+",
                category = BadgeCategory.DYNAMIC_RANGE,
                isPremium = true,
                customAssetFile = badgePackManager?.resolveBadgeFile(activePackDir, key)
            )
        }

        // Color transfer 16 is C.COLOR_TRANSFER_ST2084 (SMPTE ST 2084 / PQ HDR)
        val isHdr10 = gamma?.equals("pq", ignoreCase = true) == true ||
            colorTransfer == 16 ||
            REGEX_HDR10.containsMatchIn(videoContext)

        if (isHdr10) {
            val key = "hdr10"
            return StreamBadgeItem(
                key = key,
                label = "HDR10",
                shortLabel = "HDR10",
                category = BadgeCategory.DYNAMIC_RANGE,
                isPremium = false,
                customAssetFile = badgePackManager?.resolveBadgeFile(activePackDir, key)
            )
        }

        // Color transfer 18 is C.COLOR_TRANSFER_HLG (ARIB STD-B67 / HLG)
        val isHlg = gamma?.equals("hlg", ignoreCase = true) == true ||
            colorTransfer == 18 ||
            REGEX_HLG.containsMatchIn(videoContext)

        if (isHlg) {
            val key = "hlg"
            return StreamBadgeItem(
                key = key,
                label = "HLG",
                shortLabel = "HLG",
                category = BadgeCategory.DYNAMIC_RANGE,
                isPremium = false,
                customAssetFile = badgePackManager?.resolveBadgeFile(activePackDir, key)
            )
        }

        return null
    }

    private fun resolveAudioCodec(
        audioCodec: String?,
        audioTrackTitle: String?,
        textContext: String,
        badgePackManager: PlayerBadgePackManager?,
        activePackDir: File?
    ): StreamBadgeItem? {
        val audioContext = listOfNotNull(audioCodec, audioTrackTitle, textContext)
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        val (key, label, shortLabel, isPremium) = when {
            REGEX_ATMOS.containsMatchIn(audioContext) -> Quad("atmos", "Dolby Atmos", "ATMOS", true)
            REGEX_TRUEHD.containsMatchIn(audioContext) -> Quad("truehd", "Dolby TrueHD", "TRUEHD", false)
            REGEX_DTS_X.containsMatchIn(audioContext) -> Quad("dts_x", "DTS:X", "DTS:X", true)
            REGEX_DTS_HD_MA.containsMatchIn(audioContext) -> Quad("dts_hd_ma", "DTS-HD MA", "DTS-HD", false)
            REGEX_DTS.containsMatchIn(audioContext) -> Quad("dts", "DTS", "DTS", false)
            REGEX_EAC3.containsMatchIn(audioContext) -> Quad("eac3", "Dolby Digital+", "DDP", false)
            REGEX_AC3.containsMatchIn(audioContext) -> Quad("ac3", "Dolby Digital", "DD", false)
            REGEX_FLAC.containsMatchIn(audioContext) -> Quad("flac", "FLAC", "FLAC", false)
            REGEX_AAC.containsMatchIn(audioContext) -> Quad("aac", "AAC", "AAC", false)
            REGEX_OPUS.containsMatchIn(audioContext) -> Quad("opus", "Opus", "OPUS", false)
            else -> return null
        }

        val assetFile = badgePackManager?.resolveBadgeFile(activePackDir, key)
        return StreamBadgeItem(
            key = key,
            label = label,
            shortLabel = shortLabel,
            category = BadgeCategory.AUDIO_CODEC,
            isPremium = isPremium,
            customAssetFile = assetFile
        )
    }

    private fun resolveAudioChannels(
        channels: Int?,
        audioTrackTitle: String?,
        textContext: String,
        badgePackManager: PlayerBadgePackManager?,
        activePackDir: File?
    ): StreamBadgeItem? {
        val combined = listOfNotNull(audioTrackTitle, textContext).joinToString(" ")
        val (key, label) = when {
            channels != null && channels >= 8 -> "7.1" to "7.1"
            channels != null && channels in 6..7 -> "5.1" to "5.1"
            channels != null && channels == 2 -> "2.0" to "2.0"
            channels != null && channels == 1 -> "1.0" to "1.0"
            combined.contains("7.1", ignoreCase = true) || combined.contains("8ch", ignoreCase = true) -> "7.1" to "7.1"
            combined.contains("5.1", ignoreCase = true) || combined.contains("6ch", ignoreCase = true) -> "5.1" to "5.1"
            combined.contains("2.0", ignoreCase = true) || combined.contains("stereo", ignoreCase = true) -> "2.0" to "2.0"
            else -> return null
        }

        val assetFile = badgePackManager?.resolveBadgeFile(activePackDir, key)
        return StreamBadgeItem(
            key = key,
            label = label,
            shortLabel = label,
            category = BadgeCategory.AUDIO_CHANNELS,
            isPremium = false,
            customAssetFile = assetFile
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
