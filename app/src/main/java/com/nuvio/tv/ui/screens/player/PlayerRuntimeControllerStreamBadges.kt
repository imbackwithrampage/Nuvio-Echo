package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.player.badges.PlayerBadgePackManager
import com.nuvio.tv.core.player.badges.PlayerCodecBadgeParser
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Periodically or reactively inspects active playback properties and updates
 * [_uiState.streamBadges] with resolved codec badges.
 */
internal fun PlayerRuntimeController.refreshStreamBadges() {
    val state = _uiState.value
    if (!state.showStreamCodecBadges) {
        if (state.streamBadges.items.isNotEmpty()) {
            _uiState.update { it.copy(streamBadges = com.nuvio.tv.core.player.badges.PlayerStreamBadges()) }
        }
        return
    }

    val mpv = mpvView?.mpv
    val isMpv = isUsingMpvEngine()

    val width: Int?
    val height: Int?
    val gamma: String?
    val pixelFormat: String?
    val videoCodec: String?
    val videoDecoderDesc: String?
    val colorTransfer: Int?
    val audioCodec: String?
    val audioChannels: Int?
    val audioTrackTitle: String?

    val selectedAudio = state.audioTracks.getOrNull(state.selectedAudioTrackIndex)
        ?: state.audioTracks.firstOrNull { it.isSelected }

    if (isMpv && mpv != null) {
        width = runCatching { mpv.getPropertyInt("width") }.getOrNull()?.takeIf { it > 0 }
            ?: runCatching { mpv.getPropertyInt("video-params/w") }.getOrNull()?.takeIf { it > 0 }
            ?: currentVideoWidth
        height = runCatching { mpv.getPropertyInt("height") }.getOrNull()?.takeIf { it > 0 }
            ?: runCatching { mpv.getPropertyInt("video-params/h") }.getOrNull()?.takeIf { it > 0 }
            ?: currentVideoHeight
        gamma = runCatching { mpv.getPropertyString("video-params/gamma") }.getOrNull()
        pixelFormat = runCatching { mpv.getPropertyString("video-params/pixelformat") }.getOrNull()
        videoCodec = runCatching { mpv.getPropertyString("video-codec") }.getOrNull()
            ?: runCatching { mpv.getPropertyString("video-format") }.getOrNull()
            ?: currentVideoCodec
        videoDecoderDesc = runCatching { mpv.getPropertyString("current-tracks/video/decoder-desc") }.getOrNull()
        colorTransfer = null
        audioCodec = runCatching { mpv.getPropertyString("audio-codec") }.getOrNull()
            ?: runCatching { mpv.getPropertyString("current-tracks/audio/codec") }.getOrNull()
            ?: selectedAudio?.codec
        audioChannels = runCatching { mpv.getPropertyInt("audio-params/channel-count") }.getOrNull()
            ?: runCatching { mpv.getPropertyInt("audio-params/channels") }.getOrNull()
            ?: selectedAudio?.channelCount
        audioTrackTitle = selectedAudio?.name
    } else {
        val activeVideoFormat = _exoPlayer?.videoFormat
        width = activeVideoFormat?.width?.takeIf { it > 0 } ?: currentVideoWidth
        height = activeVideoFormat?.height?.takeIf { it > 0 } ?: currentVideoHeight
        gamma = null
        pixelFormat = null
        videoCodec = activeVideoFormat?.let { format ->
            CustomDefaultTrackNameProvider.formatNameFromMime(format.sampleMimeType)
                ?: CustomDefaultTrackNameProvider.formatNameFromMime(format.codecs)
        } ?: currentVideoCodec
        videoDecoderDesc = null
        colorTransfer = activeVideoFormat?.colorInfo?.colorTransfer
        val audioFormat = _exoPlayer?.audioFormat
        audioCodec = selectedAudio?.codec ?: audioFormat?.sampleMimeType
        audioChannels = selectedAudio?.channelCount ?: audioFormat?.channelCount
        audioTrackTitle = selectedAudio?.name
    }

    val streamTitle = state.currentStreamName ?: streamName
    val streamDesc = currentStreamDescription
    val filename = currentFilename

    val activePackDir = state.customBadgePackPath?.let { File(it) }?.takeIf { it.exists() && it.isDirectory }

    val badges = PlayerCodecBadgeParser.parse(
        videoWidth = width,
        videoHeight = height,
        gamma = gamma,
        pixelFormat = pixelFormat,
        videoCodec = videoCodec,
        videoDecoderDesc = videoDecoderDesc,
        colorTransfer = colorTransfer,
        audioCodec = audioCodec,
        audioChannels = audioChannels,
        audioTrackTitle = audioTrackTitle,
        streamTitle = streamTitle,
        streamDescription = streamDesc,
        filename = filename,
        badgePackManager = PlayerBadgePackManager,
        activePackDir = activePackDir
    )

    if (_uiState.value.streamBadges != badges) {
        _uiState.update { it.copy(streamBadges = badges) }
    }
}
