package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.data.local.AutoSkipSegmentType
import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.data.local.PlayerPreference
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.SkipIntroMode
import com.nuvio.tv.data.local.SkipTimeFileCheck

@Composable
internal fun PlaybackPlayerSection(
    settings: PlayerSettings,
    onUpdate: PlaybackSettingsUpdate,
    onOpenDialog: (PlaybackDialog) -> Unit
) {
    val usesExternalPlayer = settings.playerPreference == PlayerPreference.EXTERNAL

    SettingsActionRow(
        title = stringResource(R.string.playback_default_player),
        subtitle = null,
        value = playerPreferenceLabel(settings.playerPreference),
        onClick = { onOpenDialog(PlaybackDialog.PLAYER_PREFERENCE) }
    )
    SettingsActionRow(
        title = stringResource(R.string.playback_internal_player_engine),
        subtitle = null,
        value = internalEngineLabel(settings.internalPlayerEngine),
        enabled = !usesExternalPlayer,
        onClick = { onOpenDialog(PlaybackDialog.INTERNAL_ENGINE) }
    )
    SettingsToggleRow(
        title = stringResource(R.string.playback_auto_switch_internal_player_on_error),
        subtitle = stringResource(R.string.playback_auto_switch_internal_player_on_error_sub),
        checked = settings.autoSwitchInternalPlayerOnError,
        onToggle = { onUpdate { setAutoSwitchInternalPlayerOnError(!settings.autoSwitchInternalPlayerOnError) } },
        enabled = !usesExternalPlayer
    )

    if (settings.playerPreference != PlayerPreference.INTERNAL) {
        SettingsSectionLabel(text = stringResource(R.string.playback_external_player_label))
        SettingsToggleRow(
            title = stringResource(R.string.playback_external_forward_subtitles),
            subtitle = stringResource(R.string.playback_external_forward_subtitles_sub),
            checked = settings.externalPlayerForwardSubtitles,
            onToggle = { onUpdate { setExternalPlayerForwardSubtitles(!settings.externalPlayerForwardSubtitles) } }
        )
        SettingsToggleRow(
            title = stringResource(R.string.playback_external_send_skip_segments),
            subtitle = stringResource(R.string.playback_external_send_skip_segments_sub),
            checked = settings.externalPlayerSendSkipSegments,
            onToggle = { onUpdate { setExternalPlayerSendSkipSegments(!settings.externalPlayerSendSkipSegments) } }
        )
    }
}

@Composable
internal fun PlaybackSkipSegmentsSection(
    settings: PlayerSettings,
    onUpdate: PlaybackSettingsUpdate
) {
    val internalPlayer = settings.playerPreference != PlayerPreference.EXTERNAL
    val autoSkipEnabled = internalPlayer && settings.skipIntroEnabled && settings.skipIntroMode != SkipIntroMode.OFF
    var showSkipModeDialog by remember { mutableStateOf(false) }

    SettingsActionRow(
        title = stringResource(R.string.skip_intro_mode_title),
        subtitle = null,
        value = when (settings.skipIntroMode) {
            SkipIntroMode.AUTO_SKIP -> stringResource(R.string.skip_intro_mode_auto)
            SkipIntroMode.SHOW_BUTTON -> stringResource(R.string.skip_intro_mode_button)
            SkipIntroMode.OFF -> stringResource(R.string.skip_intro_mode_off)
        },
        enabled = internalPlayer,
        onClick = { showSkipModeDialog = true }
    )

    if (showSkipModeDialog) {
        val options = listOf(
            SettingsPickerOption(
                SkipIntroMode.SHOW_BUTTON,
                stringResource(R.string.skip_intro_mode_button),
                stringResource(R.string.skip_intro_mode_button_sub)
            ),
            SettingsPickerOption(
                SkipIntroMode.AUTO_SKIP,
                stringResource(R.string.skip_intro_mode_auto),
                stringResource(R.string.skip_intro_mode_auto_sub)
            ),
            SettingsPickerOption(
                SkipIntroMode.OFF,
                stringResource(R.string.skip_intro_mode_off),
                stringResource(R.string.skip_intro_mode_off_sub)
            )
        )
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.skip_intro_mode_title),
            options = options,
            selectedValue = settings.skipIntroMode,
            onOptionSelected = { mode ->
                onUpdate { setSkipIntroMode(mode) }
                showSkipModeDialog = false
            },
            onDismiss = { showSkipModeDialog = false }
        )
    }

    SettingsToggleRow(
        title = stringResource(R.string.playback_skip_intro),
        subtitle = stringResource(R.string.playback_skip_intro_sub),
        checked = settings.skipIntroEnabled,
        onToggle = { onUpdate { setSkipIntroEnabled(!settings.skipIntroEnabled) } },
        enabled = internalPlayer
    )
    if (internalPlayer && settings.skipIntroEnabled) {
        SettingsSectionLabel(
            text = stringResource(R.string.skip_file_checks_title),
            description = stringResource(R.string.skip_file_checks_subtitle)
        )
        SettingsChipRow(
            items = SkipTimeFileCheck.entries.map { check ->
                check.storedValue to stringResource(
                    when (check) {
                        SkipTimeFileCheck.CHAPTERS -> R.string.skip_file_check_chapters
                        SkipTimeFileCheck.FIT -> R.string.skip_file_check_fit
                        SkipTimeFileCheck.CREDITS -> R.string.skip_file_check_credits
                    }
                )
            },
            selected = settings.skipTimeFileChecks.map { it.storedValue }.toSet(),
            onToggle = { key ->
                val check = SkipTimeFileCheck.fromStoredValue(key) ?: return@SettingsChipRow
                val current = settings.skipTimeFileChecks
                val next = if (check in current) current - check else current + check
                onUpdate { setSkipTimeFileChecks(next) }
            }
        )
    }

    SettingsSectionLabel(
        text = stringResource(R.string.playback_auto_skip_segments),
        description = stringResource(R.string.playback_auto_skip_segments_sub)
    )
    AutoSkipToggle(
        segment = AutoSkipSegmentType.INTRO,
        title = stringResource(R.string.auto_skip_intro),
        subtitle = stringResource(R.string.auto_skip_intro_sub),
        settings = settings,
        enabled = autoSkipEnabled,
        onUpdate = onUpdate
    )
    AutoSkipToggle(
        segment = AutoSkipSegmentType.RECAP,
        title = stringResource(R.string.auto_skip_recap),
        subtitle = stringResource(R.string.auto_skip_recap_sub),
        settings = settings,
        enabled = autoSkipEnabled,
        onUpdate = onUpdate
    )
    AutoSkipToggle(
        segment = AutoSkipSegmentType.OUTRO,
        title = stringResource(R.string.auto_skip_outro),
        subtitle = stringResource(R.string.auto_skip_outro_sub),
        settings = settings,
        enabled = autoSkipEnabled,
        onUpdate = onUpdate
    )
    AutoSkipToggle(
        segment = AutoSkipSegmentType.MOVIE_CREDITS,
        title = stringResource(R.string.auto_skip_movie_credits),
        subtitle = stringResource(R.string.auto_skip_movie_credits_sub),
        settings = settings,
        enabled = autoSkipEnabled,
        onUpdate = onUpdate
    )
}

@Composable
private fun AutoSkipToggle(
    segment: AutoSkipSegmentType,
    title: String,
    subtitle: String,
    settings: PlayerSettings,
    enabled: Boolean,
    onUpdate: PlaybackSettingsUpdate
) {
    val checked = segment in settings.autoSkipSegmentTypes
    SettingsToggleRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onToggle = { onUpdate { setAutoSkipSegmentTypeEnabled(segment, !checked) } },
        enabled = enabled
    )
}

@Composable
internal fun PlaybackPlayerInterfaceSection(
    settings: PlayerSettings,
    onUpdate: PlaybackSettingsUpdate
) {
    val internalPlayer = settings.playerPreference != PlayerPreference.EXTERNAL
    var showBadgePackDialog by rememberSaveable { mutableStateOf(false) }

    SettingsToggleRow(
        title = stringResource(R.string.playback_loading_overlay),
        subtitle = stringResource(R.string.playback_loading_overlay_sub),
        checked = settings.loadingOverlayEnabled,
        onToggle = { onUpdate { setLoadingOverlayEnabled(!settings.loadingOverlayEnabled) } },
        enabled = internalPlayer
    )
    SettingsToggleRow(
        title = stringResource(R.string.playback_show_loading_status),
        subtitle = stringResource(R.string.playback_show_loading_status_sub),
        checked = settings.showPlayerLoadingStatus,
        onToggle = { onUpdate { setShowPlayerLoadingStatus(!settings.showPlayerLoadingStatus) } }
    )
    SettingsToggleRow(
        title = stringResource(R.string.playback_pause_overlay),
        subtitle = stringResource(R.string.playback_pause_overlay_sub),
        checked = settings.pauseOverlayEnabled,
        onToggle = { onUpdate { setPauseOverlayEnabled(!settings.pauseOverlayEnabled) } },
        enabled = internalPlayer
    )
    SettingsToggleRow(
        title = stringResource(R.string.playback_osd_clock),
        subtitle = stringResource(R.string.playback_show_clock_sub),
        checked = settings.osdClockEnabled,
        onToggle = { onUpdate { setOsdClockEnabled(!settings.osdClockEnabled) } },
        enabled = internalPlayer
    )
    SettingsToggleRow(
        title = stringResource(R.string.playback_show_codec_badges),
        subtitle = stringResource(R.string.playback_show_codec_badges_sub),
        checked = settings.showStreamCodecBadges,
        onToggle = { onUpdate { setShowStreamCodecBadges(!settings.showStreamCodecBadges) } },
        enabled = internalPlayer
    )
    if (settings.showStreamCodecBadges) {
        val currentPackName = settings.customBadgePackPath?.let {
            java.io.File(it).name.replace("_", " ").replaceFirstChar { c -> c.titlecase() }
        } ?: stringResource(R.string.playback_badge_pack_default)
        SettingsActionRow(
            title = stringResource(R.string.playback_custom_badge_pack),
            subtitle = stringResource(R.string.playback_custom_badge_pack_sub),
            value = currentPackName,
            onClick = { showBadgePackDialog = true },
            enabled = internalPlayer
        )
    }
    PlayerExtrasRows()

    if (showBadgePackDialog) {
        PlayerBadgePackDialog(
            currentPackPath = settings.customBadgePackPath,
            onPackSelected = { path ->
                onUpdate { setCustomBadgePackPath(path) }
            },
            onDismiss = { showBadgePackDialog = false }
        )
    }
}

@Composable
private fun playerPreferenceLabel(preference: PlayerPreference): String = when (preference) {
    PlayerPreference.INTERNAL -> stringResource(R.string.playback_player_internal)
    PlayerPreference.EXTERNAL -> stringResource(R.string.playback_player_external)
    PlayerPreference.ASK_EVERY_TIME -> stringResource(R.string.playback_player_ask)
}

@Composable
private fun internalEngineLabel(engine: InternalPlayerEngine): String = when (engine) {
    InternalPlayerEngine.EXOPLAYER -> stringResource(R.string.playback_engine_exoplayer)
    InternalPlayerEngine.MVP_PLAYER -> stringResource(R.string.playback_engine_mvplayer)
    InternalPlayerEngine.AUTO -> stringResource(R.string.playback_player_auto)
}

@Composable
internal fun PlayerPreferenceDialog(
    currentPreference: PlayerPreference,
    onPreferenceSelected: (PlayerPreference) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        SettingsPickerOption(PlayerPreference.INTERNAL, stringResource(R.string.playback_player_internal), stringResource(R.string.playback_player_internal_desc)),
        SettingsPickerOption(PlayerPreference.EXTERNAL, stringResource(R.string.playback_player_external), stringResource(R.string.playback_player_external_desc)),
        SettingsPickerOption(PlayerPreference.ASK_EVERY_TIME, stringResource(R.string.playback_player_ask), stringResource(R.string.playback_player_ask_desc))
    )

    SettingsSingleChoiceDialog(
        title = stringResource(R.string.playback_default_player),
        options = options,
        selectedValue = currentPreference,
        onOptionSelected = onPreferenceSelected,
        onDismiss = onDismiss,
        width = 420.dp,
        maxHeight = 320.dp
    )
}

@Composable
internal fun InternalPlayerEngineDialog(
    currentEngine: InternalPlayerEngine,
    onEngineSelected: (InternalPlayerEngine) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        SettingsPickerOption(
            InternalPlayerEngine.EXOPLAYER,
            stringResource(R.string.playback_engine_exoplayer),
            stringResource(R.string.playback_engine_exoplayer_desc)
        ),
        SettingsPickerOption(
            InternalPlayerEngine.MVP_PLAYER,
            stringResource(R.string.playback_engine_mvplayer),
            stringResource(R.string.playback_engine_mvplayer_desc)
        ),
        SettingsPickerOption(
            InternalPlayerEngine.AUTO,
            stringResource(R.string.playback_player_auto),
            stringResource(R.string.playback_player_auto_desc)
        )
    )

    SettingsSingleChoiceDialog(
        title = stringResource(R.string.playback_internal_player_engine),
        options = options,
        selectedValue = currentEngine,
        onOptionSelected = onEngineSelected,
        onDismiss = onDismiss,
        width = 420.dp,
        maxHeight = 320.dp
    )
}
