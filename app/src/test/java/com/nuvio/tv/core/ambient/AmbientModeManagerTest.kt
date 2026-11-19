package com.nuvio.tv.core.ambient

import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.core.player.ExternalPlaybackTracker
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.data.local.AmbientModeSettingsDataStore
import com.nuvio.tv.domain.model.AmbientModeSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmbientModeManagerTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settingsFlow = MutableStateFlow(AmbientModeSettings(enabled = true, idleTimeoutMinutes = 5))
    private val settingsDataStore = mockk<AmbientModeSettingsDataStore> {
        every { settings } returns settingsFlow
    }
    private val trailerPlayerPool = mockk<TrailerPlayerPool> {
        every { acquire() } returns null
    }
    private val externalPlaybackTracker = mockk<ExternalPlaybackTracker> {
        every { isTracking } returns false
    }

    private lateinit var manager: AmbientModeManager

    @Before
    fun setup() {
        manager = AmbientModeManager(
            settingsDataStore = settingsDataStore,
            trailerPlayerPool = trailerPlayerPool,
            externalPlaybackTracker = externalPlaybackTracker
        )
    }

    @Test
    fun `triggerPreview activates ambient mode immediately`() = runTest {
        assertFalse(manager.isAmbientActive.value)
        manager.triggerPreview()
        assertTrue(manager.isAmbientActive.value)
    }

    @Test
    fun `dismiss deactivates ambient mode`() = runTest {
        manager.triggerPreview()
        assertTrue(manager.isAmbientActive.value)

        manager.dismiss()
        assertFalse(manager.isAmbientActive.value)
    }

    @Test
    fun `user interaction dismisses active ambient mode`() = runTest {
        manager.triggerPreview()
        assertTrue(manager.isAmbientActive.value)

        manager.onUserInteraction()
        assertFalse(manager.isAmbientActive.value)
    }

    @Test
    fun `setting player active dismisses ambient mode and pauses idle`() = runTest {
        manager.triggerPreview()
        assertTrue(manager.isAmbientActive.value)

        manager.setPlayerScreenActive(true)
        assertFalse(manager.isAmbientActive.value)
        assertTrue(manager.isMediaOrTrailerPlaying())
    }

    @Test
    fun `when disabled in settings ambient mode dismisses if active`() = runTest {
        manager.triggerPreview()
        assertTrue(manager.isAmbientActive.value)

        settingsFlow.value = AmbientModeSettings(enabled = false)
        runCurrent()
        assertFalse(manager.isAmbientActive.value)
    }
}
