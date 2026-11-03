package com.blueeve.floatlrc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blueeve.floatlrc.BuildConfig
import com.blueeve.floatlrc.R
import com.blueeve.floatlrc.data.NowPlayingUi
import com.blueeve.floatlrc.data.OverlaySettings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

data class MainCallbacks(
    val hasOverlayPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val onToggleOverlay: (Boolean) -> Unit,
    val onRequestOverlayPermission: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onOpenSuperLyric: () -> Unit,
    val onRefresh: () -> Unit,
    val onShowTranslationChange: (Boolean) -> Unit,
    val onKaraokeChange: (Boolean) -> Unit,
    val onLockChange: (Boolean) -> Unit,
    val onFontScaleChange: (Float) -> Unit,
    val onOpacityChange: (Float) -> Unit,
    val onThemeCycle: () -> Unit,
    val onResetPosition: () -> Unit,
    val onInjectDemo: () -> Unit,
)

@Composable
fun MainScreen(
    state: MainUiState,
    callbacks: MainCallbacks,
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                subtitle = topBarSubtitle(state),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = callbacks.onRefresh) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .overScrollVertical()
                .scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NowPlayingCard(state)
            }

            item { SectionTitle(R.string.section_overlay) }
            item {
                OverlaySettingsCard(state, callbacks)
            }

            item { SectionTitle(R.string.section_permissions) }
            item {
                PermissionCard(callbacks)
            }

            item { SectionTitle(R.string.section_connection) }
            item {
                ConnectionCard(state, callbacks)
            }

            item { SectionTitle(R.string.section_about) }
            item {
                AboutCard()
            }
        }
    }
}

@Composable
private fun topBarSubtitle(state: MainUiState): String = when {
    state.connection.registered -> stringResource(R.string.status_connected)
    state.connection.serviceAvailable -> stringResource(R.string.status_service_ready)
    else -> stringResource(R.string.status_service_missing)
}

@Composable
private fun SectionTitle(textRes: Int) {
    SmallTitle(
        text = stringResource(textRes),
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun NowPlayingCard(state: MainUiState) {
    val colors = MiuixTheme.colorScheme
    val nowPlaying = state.nowPlaying
    val connected = state.connection.registered
    val statusColor = when {
        connected -> Color(0xFF36A853)
        state.connection.serviceAvailable -> colors.primary
        else -> colors.error
    }
    val statusText = when {
        connected -> stringResource(R.string.status_connected)
        state.connection.serviceAvailable -> stringResource(R.string.status_service_ready)
        else -> stringResource(R.string.status_service_missing)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.weight(1f))
            if (nowPlaying != null && nowPlaying.lineIndex >= 0) {
                Text(
                    text = "${nowPlaying.lineIndex + 1} / ${nowPlaying.lineCount}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = nowPlaying?.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.now_playing_empty_title),
            style = MiuixTheme.textStyles.title1,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = nowPlaying?.let(::artistSummary)?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.now_playing_empty_summary),
            style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                val line = nowPlaying?.line
                Text(
                    text = line?.text?.takeIf { it.isNotBlank() }
                        ?: stringResource(
                            if (nowPlaying == null) R.string.lyrics_placeholder
                            else R.string.waiting_for_lyric
                        ),
                    style = MiuixTheme.textStyles.title3,
                    color = if (nowPlaying?.hasLyrics == true) colors.primary
                    else colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val translation = line?.translation
                if (nowPlaying?.hasLyrics == true && !translation.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = translation,
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun artistSummary(nowPlaying: NowPlayingUi): String {
    val parts = listOf(nowPlaying.artist, nowPlaying.album)
        .filter { it.isNotBlank() }
    return parts.joinToString(" · ")
}

@Composable
private fun OverlaySettingsCard(
    state: MainUiState,
    callbacks: MainCallbacks,
) {
    val settings = state.settings
    val colors = MiuixTheme.colorScheme

    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            checked = state.overlayRunning,
            onCheckedChange = callbacks.onToggleOverlay,
            title = stringResource(R.string.overlay_switch_title),
            summary = stringResource(
                if (state.overlayRunning) R.string.overlay_switch_summary_on
                else R.string.overlay_switch_summary_off
            ),
        )
        SettingsDivider()
        SwitchPreference(
            checked = settings.showTranslation,
            onCheckedChange = callbacks.onShowTranslationChange,
            title = stringResource(R.string.overlay_translation_title),
            summary = stringResource(R.string.overlay_translation_summary),
        )
        SettingsDivider()
        SwitchPreference(
            checked = settings.karaokeEnabled,
            onCheckedChange = callbacks.onKaraokeChange,
            title = stringResource(R.string.overlay_karaoke_title),
            summary = stringResource(R.string.overlay_karaoke_summary),
        )
        SettingsDivider()
        SwitchPreference(
            checked = settings.lockPosition,
            onCheckedChange = callbacks.onLockChange,
            title = stringResource(R.string.overlay_lock_title),
            summary = stringResource(R.string.overlay_lock_summary),
            enabled = state.overlayRunning,
        )
        SettingsDivider()
        SliderRow(
            title = stringResource(R.string.overlay_font_size),
            valueText = stringResource(
                R.string.font_size_value,
                (20f * settings.fontScale).roundToInt(),
            ),
            value = settings.fontScale,
            valueRange = 0.8f..1.6f,
            steps = 7,
            onValueChange = callbacks.onFontScaleChange,
        )
        SettingsDivider()
        SliderRow(
            title = stringResource(R.string.overlay_opacity),
            valueText = stringResource(
                R.string.opacity_value,
                (settings.backgroundOpacity * 100).roundToInt(),
            ),
            value = settings.backgroundOpacity,
            valueRange = 0.3f..1f,
            steps = 13,
            onValueChange = callbacks.onOpacityChange,
        )
        SettingsDivider()
        ArrowPreference(
            title = stringResource(R.string.overlay_theme),
            summary = stringResource(
                R.string.overlay_theme_summary,
                stringResource(themeLabelRes(settings.themeMode)),
            ),
            onClick = callbacks.onThemeCycle,
        )
        SettingsDivider()
        ArrowPreference(
            title = stringResource(R.string.overlay_reset_position),
            summary = stringResource(R.string.overlay_reset_position_summary),
            onClick = callbacks.onResetPosition,
        )
    }

    // 未授予权限时给出明确提示，避免用户误以为开关失效。
    if (!callbacks.hasOverlayPermission) {
        Text(
            text = stringResource(R.string.permission_overlay_denied),
            style = MiuixTheme.textStyles.footnote1,
            color = colors.error,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun themeLabelRes(themeMode: Int): Int = when (themeMode) {
    OverlaySettings.THEME_LIGHT -> R.string.theme_light
    OverlaySettings.THEME_DARK -> R.string.theme_dark
    else -> R.string.theme_system
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceVariantSummary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun PermissionCard(callbacks: MainCallbacks) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = stringResource(R.string.permission_overlay),
            summary = stringResource(
                if (callbacks.hasOverlayPermission) R.string.permission_granted
                else R.string.permission_overlay_denied
            ),
            onClick = callbacks.onRequestOverlayPermission,
        )
        SettingsDivider()
        ArrowPreference(
            title = stringResource(R.string.permission_notification),
            summary = stringResource(
                if (callbacks.hasNotificationPermission) R.string.permission_granted
                else R.string.permission_notification_denied
            ),
            onClick = callbacks.onRequestNotificationPermission,
        )
    }
}

@Composable
private fun ConnectionCard(
    state: MainUiState,
    callbacks: MainCallbacks,
) {
    val colors = MiuixTheme.colorScheme
    val connection = state.connection
    val serviceSummary = when {
        !connection.serviceAvailable -> stringResource(R.string.connection_service_unavailable)
        connection.serverApiVersion > 0 -> stringResource(
            R.string.connection_service_available,
            connection.serverApiVersion,
        )
        else -> stringResource(R.string.connection_service_connected)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = stringResource(R.string.connection_service),
            summary = serviceSummary,
            onClick = callbacks.onRefresh,
        )
        SettingsDivider()
        ArrowPreference(
            title = stringResource(R.string.connection_registered),
            summary = stringResource(
                if (connection.registered) R.string.connection_registered_yes
                else R.string.connection_registered_no
            ),
            onClick = callbacks.onRefresh,
        )
        SettingsDivider()
        ArrowPreference(
            title = stringResource(R.string.connection_publisher),
            summary = state.nowPlaying?.publisher?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.unknown),
        )
        SettingsDivider()
        ArrowPreference(
            title = stringResource(R.string.connection_get_superlyric),
            summary = stringResource(R.string.connection_get_superlyric_summary),
            onClick = callbacks.onOpenSuperLyric,
        )

        if (!connection.serviceAvailable) {
            SettingsDivider()
            Text(
                text = stringResource(R.string.connection_hint),
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        val error = connection.error
        if (!error.isNullOrBlank()) {
            SettingsDivider()
            Text(
                text = stringResource(R.string.connection_error, error),
                style = MiuixTheme.textStyles.body2,
                color = colors.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (BuildConfig.DEBUG) {
            SettingsDivider()
            ArrowPreference(
                title = stringResource(R.string.demo_title),
                summary = stringResource(R.string.demo_summary),
                onClick = callbacks.onInjectDemo,
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Music,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = colors.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MiuixTheme.textStyles.title3,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.about_desc),
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_tech),
            style = MiuixTheme.textStyles.footnote2,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
