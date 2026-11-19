package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.AmbientModeSettings
import com.nuvio.tv.domain.model.AmbientSourceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AmbientModeSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "ambient_mode_settings"
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val enabledKey = booleanPreferencesKey("ambient_enabled")
    private val idleTimeoutMinutesKey = intPreferencesKey("ambient_idle_timeout_min")
    private val slideshowIntervalSecondsKey = intPreferencesKey("ambient_slideshow_interval_sec")
    private val sourceModeKey = stringPreferencesKey("ambient_source_mode")
    private val showClockAndMetadataKey = booleanPreferencesKey("ambient_show_clock_meta")

    val settings: Flow<AmbientModeSettings> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            val sourceModeStr = prefs[sourceModeKey] ?: AmbientSourceMode.TRENDING.name
            val sourceMode = runCatching { AmbientSourceMode.valueOf(sourceModeStr) }
                .getOrDefault(AmbientSourceMode.TRENDING)

            AmbientModeSettings(
                enabled = prefs[enabledKey] ?: true,
                idleTimeoutMinutes = prefs[idleTimeoutMinutesKey] ?: 5,
                slideshowIntervalSeconds = prefs[slideshowIntervalSecondsKey] ?: 15,
                sourceMode = sourceMode,
                showClockAndMetadata = prefs[showClockAndMetadataKey] ?: true
            )
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        store().edit { it[enabledKey] = enabled }
    }

    suspend fun setIdleTimeoutMinutes(minutes: Int) {
        store().edit { it[idleTimeoutMinutesKey] = minutes }
    }

    suspend fun setSlideshowIntervalSeconds(seconds: Int) {
        store().edit { it[slideshowIntervalSecondsKey] = seconds }
    }

    suspend fun setSourceMode(mode: AmbientSourceMode) {
        store().edit { it[sourceModeKey] = mode.name }
    }

    suspend fun setShowClockAndMetadata(show: Boolean) {
        store().edit { it[showClockAndMetadataKey] = show }
    }
}
