package com.blueeve.floatlrc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.blueeve.floatlrc.data.OverlaySettings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun FloatLrcTheme(
    themeMode: Int = OverlaySettings.THEME_SYSTEM,
    content: @Composable () -> Unit,
) {
    val mode = when (themeMode) {
        OverlaySettings.THEME_LIGHT -> ColorSchemeMode.Light
        OverlaySettings.THEME_DARK -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val controller = remember(themeMode) { ThemeController(mode) }
    MiuixTheme(controller = controller, content = content)
}
