package com.nuvio.tv.core.ambient

import com.nuvio.tv.core.player.ExternalPlaybackTracker
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.data.local.AmbientModeSettingsDataStore
import com.nuvio.tv.domain.model.AmbientModeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmbientModeManager @Inject constructor(
    private val settingsDataStore: AmbientModeSettingsDataStore,
    private val trailerPlayerPool: TrailerPlayerPool,
    private val externalPlaybackTracker: ExternalPlaybackTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isAmbientActive = MutableStateFlow(false)
    val isAmbientActive: StateFlow<Boolean> = _isAmbientActive.asStateFlow()

    private val _currentSettings = MutableStateFlow(AmbientModeSettings())
    val currentSettings: StateFlow<AmbientModeSettings> = _currentSettings.asStateFlow()

    @Volatile
    private var isPlayerScreenActive: Boolean = false

    private var idleCountdownJob: Job? = null
    private var lastInteractionTimeMs: Long = System.currentTimeMillis()

    init {
        scope.launch {
            settingsDataStore.settings.collectLatest { settings ->
                _currentSettings.value = settings
                if (!settings.enabled && _isAmbientActive.value) {
                    dismiss()
                }
                restartIdleTimer()
            }
        }
    }

    fun setPlayerScreenActive(active: Boolean) {
        isPlayerScreenActive = active
        if (active) {
            if (_isAmbientActive.value) {
                dismiss()
            }
            cancelIdleTimer()
        } else {
            restartIdleTimer()
        }
    }

    fun isMediaOrTrailerPlaying(): Boolean {
        if (isPlayerScreenActive) return true
        if (externalPlaybackTracker.isTracking) return true
        return runCatching {
            trailerPlayerPool.acquire()?.isPlaying == true
        }.getOrDefault(false)
    }

    fun onUserInteraction() {
        lastInteractionTimeMs = System.currentTimeMillis()
        if (_isAmbientActive.value) {
            dismiss()
            return
        }
        restartIdleTimer()
    }

    fun triggerPreview() {
        _isAmbientActive.value = true
        cancelIdleTimer()
    }

    fun dismiss() {
        _isAmbientActive.value = false
        lastInteractionTimeMs = System.currentTimeMillis()
        restartIdleTimer()
    }

    private fun restartIdleTimer() {
        idleCountdownJob?.cancel()
        val settings = _currentSettings.value
        if (!settings.enabled || isMediaOrTrailerPlaying()) {
            return
        }

        val timeoutMs = settings.idleTimeoutMinutes * 60_000L
        idleCountdownJob = scope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - lastInteractionTimeMs
                val remaining = timeoutMs - elapsed
                if (remaining <= 0) {
                    if (!isMediaOrTrailerPlaying() && _currentSettings.value.enabled) {
                        _isAmbientActive.value = true
                    }
                    break
                }
                delay(remaining.coerceAtLeast(1000L))
            }
        }
    }

    private fun cancelIdleTimer() {
        idleCountdownJob?.cancel()
        idleCountdownJob = null
    }
}
