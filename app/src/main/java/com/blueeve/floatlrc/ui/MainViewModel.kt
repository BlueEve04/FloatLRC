package com.blueeve.floatlrc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blueeve.floatlrc.FloatLrcApp
import com.blueeve.floatlrc.data.AppPreferences
import com.blueeve.floatlrc.data.LyricConnection
import com.blueeve.floatlrc.data.LyricRepository
import com.blueeve.floatlrc.data.NowPlayingUi
import com.blueeve.floatlrc.data.OverlaySettings
import com.blueeve.floatlrc.overlay.LyricOverlayService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MainUiState(
    val nowPlaying: NowPlayingUi? = null,
    val connection: LyricConnection = LyricConnection(),
    val settings: OverlaySettings = OverlaySettings(),
    val overlayRunning: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences: AppPreferences = (application as FloatLrcApp).preferences

    val uiState: StateFlow<MainUiState> = combine(
        LyricRepository.nowPlaying,
        LyricRepository.connection,
        preferences.settings,
        LyricOverlayService.running,
    ) { nowPlaying, connection, settings, running ->
        MainUiState(
            nowPlaying = nowPlaying,
            connection = connection,
            settings = settings,
            overlayRunning = running,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun setOverlayEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            LyricOverlayService.start(context)
        } else {
            LyricOverlayService.stop(context)
        }
    }

    fun setShowTranslation(enabled: Boolean) {
        preferences.update { it.copy(showTranslation = enabled) }
    }

    fun setKaraokeEnabled(enabled: Boolean) {
        preferences.update { it.copy(karaokeEnabled = enabled) }
    }

    fun setLockPosition(locked: Boolean) {
        preferences.update { it.copy(lockPosition = locked) }
    }

    fun setFontScale(scale: Float) {
        preferences.update { it.copy(fontScale = scale.coerceIn(0.8f, 1.6f)) }
    }

    fun setBackgroundOpacity(opacity: Float) {
        preferences.update { it.copy(backgroundOpacity = opacity.coerceIn(0.3f, 1f)) }
    }

    fun cycleTheme() {
        preferences.update {
            it.copy(themeMode = (it.themeMode + 1) % 3)
        }
    }

    fun resetOverlayPosition() {
        val context = getApplication<Application>()
        if (uiState.value.overlayRunning) {
            LyricOverlayService.resetPosition(context)
        } else {
            preferences.clearOverlayPosition()
        }
    }

    fun refresh() {
        LyricRepository.refreshNow()
    }

    fun injectDemoLyric() {
        LyricRepository.injectDemoLyric()
    }
}
