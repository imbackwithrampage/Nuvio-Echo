@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AmbientModeSettings
import com.nuvio.tv.domain.model.AmbientSourceMode
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun AmbientModeSettingsSection(
    uiState: LayoutSettingsUiState,
    onEvent: (LayoutSettingsEvent) -> Unit
) {
    val ambient = uiState.ambientSettings

    SettingsToggleRow(
        title = stringResource(R.string.ambient_mode_enable_title),
        subtitle = stringResource(R.string.ambient_mode_enable_subtitle),
        checked = ambient.enabled,
        onToggle = { onEvent(LayoutSettingsEvent.SetAmbientEnabled(!ambient.enabled)) }
    )

    if (ambient.enabled) {
        SettingsSectionLabel(text = stringResource(R.string.ambient_mode_timeout_title))
        Text(
            text = stringResource(R.string.ambient_mode_timeout_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextTertiary
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            items(items = AmbientModeSettings.TIMEOUT_OPTIONS_MINUTES) { minutes ->
                SettingsChoiceChip(
                    label = stringResource(R.string.ambient_mode_timeout_format, minutes),
                    selected = ambient.idleTimeoutMinutes == minutes,
                    onClick = { onEvent(LayoutSettingsEvent.SetAmbientIdleTimeout(minutes)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))

        SettingsSectionLabel(text = stringResource(R.string.ambient_mode_interval_title))
        Text(
            text = stringResource(R.string.ambient_mode_interval_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextTertiary
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            items(items = AmbientModeSettings.INTERVAL_OPTIONS_SECONDS) { seconds ->
                SettingsChoiceChip(
                    label = stringResource(R.string.ambient_mode_interval_format, seconds),
                    selected = ambient.slideshowIntervalSeconds == seconds,
                    onClick = { onEvent(LayoutSettingsEvent.SetAmbientSlideshowInterval(seconds)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))

        SettingsSectionLabel(text = stringResource(R.string.ambient_mode_source_title))
        Text(
            text = stringResource(R.string.ambient_mode_source_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextTertiary
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            item {
                SettingsChoiceChip(
                    label = stringResource(R.string.ambient_mode_source_trending),
                    selected = ambient.sourceMode == AmbientSourceMode.TRENDING,
                    onClick = { onEvent(LayoutSettingsEvent.SetAmbientSourceMode(AmbientSourceMode.TRENDING)) }
                )
            }
            item {
                SettingsChoiceChip(
                    label = stringResource(R.string.ambient_mode_source_library),
                    selected = ambient.sourceMode == AmbientSourceMode.LIBRARY,
                    onClick = { onEvent(LayoutSettingsEvent.SetAmbientSourceMode(AmbientSourceMode.LIBRARY)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))

        SettingsToggleRow(
            title = stringResource(R.string.ambient_mode_clock_title),
            subtitle = stringResource(R.string.ambient_mode_clock_subtitle),
            checked = ambient.showClockAndMetadata,
            onToggle = { onEvent(LayoutSettingsEvent.SetAmbientShowClockAndMetadata(!ambient.showClockAndMetadata)) }
        )

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))

        Button(
            onClick = { onEvent(LayoutSettingsEvent.PreviewAmbientMode) },
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.SurfaceVariant,
                focusedContainerColor = NuvioTheme.colors.FocusRing
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.ambient_mode_preview_button),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
