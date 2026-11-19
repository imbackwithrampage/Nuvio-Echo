package com.nuvio.tv.ui.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.ambient.AmbientModeRepository
import com.nuvio.tv.core.ambient.AmbientSlideItem
import com.nuvio.tv.data.local.AmbientModeSettingsDataStore
import com.nuvio.tv.domain.model.AmbientModeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AmbientModeViewModel @Inject constructor(
    private val settingsDataStore: AmbientModeSettingsDataStore,
    private val repository: AmbientModeRepository
) : ViewModel() {

    val settings: StateFlow<AmbientModeSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AmbientModeSettings())

    private val _slides = MutableStateFlow<List<AmbientSlideItem>>(emptyList())
    val slides: StateFlow<List<AmbientSlideItem>> = _slides.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collectLatest { s ->
                val loaded = repository.getSlides(s.sourceMode)
                _slides.value = loaded
            }
        }
    }
}
