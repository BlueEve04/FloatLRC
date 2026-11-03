package com.blueeve.floatlrc.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗的显示设置。位置也放在这里，由服务在拖动结束后写入。
 */
data class OverlaySettings(
    val showTranslation: Boolean = true,
    val karaokeEnabled: Boolean = true,
    val lockPosition: Boolean = false,
    val fontScale: Float = 1f,
    val backgroundOpacity: Float = 0.9f,
    val themeMode: Int = THEME_SYSTEM,
    val offsetX: Int = POSITION_UNSET,
    val offsetY: Int = POSITION_UNSET,
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val POSITION_UNSET = -1
    }
}

/**
 * 轻量设置仓库。只有一个写入方（本应用），因此 SharedPreferences + StateFlow 足够。
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()

    fun update(transform: (OverlaySettings) -> OverlaySettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_SHOW_TRANSLATION, next.showTranslation)
            .putBoolean(KEY_KARAOKE, next.karaokeEnabled)
            .putBoolean(KEY_LOCK_POSITION, next.lockPosition)
            .putFloat(KEY_FONT_SCALE, next.fontScale)
            .putFloat(KEY_BACKGROUND_OPACITY, next.backgroundOpacity)
            .putInt(KEY_THEME_MODE, next.themeMode)
            .putInt(KEY_OFFSET_X, next.offsetX)
            .putInt(KEY_OFFSET_Y, next.offsetY)
            .apply()
    }

    fun saveOverlayPosition(x: Int, y: Int) {
        if (_settings.value.offsetX == x && _settings.value.offsetY == y) return
        // 拖动结束后才调用，不需要每帧写盘。
        _settings.value = _settings.value.copy(offsetX = x, offsetY = y)
        prefs.edit().putInt(KEY_OFFSET_X, x).putInt(KEY_OFFSET_Y, y).apply()
    }

    fun clearOverlayPosition() {
        _settings.value = _settings.value.copy(
            offsetX = OverlaySettings.POSITION_UNSET,
            offsetY = OverlaySettings.POSITION_UNSET,
        )
        prefs.edit()
            .remove(KEY_OFFSET_X)
            .remove(KEY_OFFSET_Y)
            .apply()
    }

    private fun read(): OverlaySettings = OverlaySettings(
        showTranslation = prefs.getBoolean(KEY_SHOW_TRANSLATION, true),
        karaokeEnabled = prefs.getBoolean(KEY_KARAOKE, true),
        lockPosition = prefs.getBoolean(KEY_LOCK_POSITION, false),
        fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f),
        backgroundOpacity = prefs.getFloat(KEY_BACKGROUND_OPACITY, 0.9f),
        themeMode = prefs.getInt(KEY_THEME_MODE, OverlaySettings.THEME_SYSTEM),
        offsetX = prefs.getInt(KEY_OFFSET_X, OverlaySettings.POSITION_UNSET),
        offsetY = prefs.getInt(KEY_OFFSET_Y, OverlaySettings.POSITION_UNSET),
    )

    private companion object {
        const val PREFS_NAME = "floatlrc_settings"
        const val KEY_SHOW_TRANSLATION = "show_translation"
        const val KEY_KARAOKE = "karaoke"
        const val KEY_LOCK_POSITION = "lock_position"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_BACKGROUND_OPACITY = "background_opacity"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_OFFSET_X = "offset_x"
        const val KEY_OFFSET_Y = "offset_y"
    }
}
