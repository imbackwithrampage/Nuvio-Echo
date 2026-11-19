package com.nuvio.tv.domain.model

enum class AmbientSourceMode {
    TRENDING,
    LIBRARY
}

data class AmbientModeSettings(
    val enabled: Boolean = true,
    val idleTimeoutMinutes: Int = 5,
    val slideshowIntervalSeconds: Int = 15,
    val sourceMode: AmbientSourceMode = AmbientSourceMode.TRENDING,
    val showClockAndMetadata: Boolean = true
) {
    companion object {
        val TIMEOUT_OPTIONS_MINUTES = listOf(1, 2, 5, 10, 15, 30)
        val INTERVAL_OPTIONS_SECONDS = listOf(10, 15, 20, 30, 60)
    }
}
