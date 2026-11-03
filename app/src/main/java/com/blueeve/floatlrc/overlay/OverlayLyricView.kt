package com.blueeve.floatlrc.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blueeve.floatlrc.data.LyricLineUi
import com.blueeve.floatlrc.data.LyricWordUi
import com.blueeve.floatlrc.data.NowPlayingUi
import com.blueeve.floatlrc.data.OverlaySettings
import com.blueeve.floatlrc.ui.FloatLrcTheme
import com.hchen.superlyricapi.model.PlaybackState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.hypot

/**
 * 悬浮窗根内容。没有可显示的歌词时渲染 0 尺寸，窗口随之收起。
 */
@Composable
fun OverlayLyricView(
    nowPlaying: NowPlayingUi?,
    settings: OverlaySettings,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
) {
    FloatLrcTheme(settings.themeMode) {
        if (nowPlaying == null || nowPlaying.line == null) {
            Box(Modifier.size(0.dp))
            return@FloatLrcTheme
        }

        val colors = MiuixTheme.colorScheme
        val interactive = !settings.lockPosition

        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (interactive) {
                            Modifier.pointerInput(Unit) {
                                val touchSlop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var dragged = false
                                    var accumulatedX = 0f
                                    var accumulatedY = 0f

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes
                                            .firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break

                                        val delta = change.positionChange()
                                        if (!dragged) {
                                            accumulatedX += delta.x
                                            accumulatedY += delta.y
                                            if (hypot(accumulatedX, accumulatedY) > touchSlop) {
                                                dragged = true
                                            }
                                        }
                                        if (dragged) {
                                            change.consume()
                                            onDrag(delta.x, delta.y)
                                        }
                                    }

                                    if (dragged) onDragEnd() else onTap()
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
                shape = RoundedCornerShape(22.dp),
                color = colors.surface.copy(alpha = settings.backgroundOpacity.coerceIn(0.25f, 1f)),
                contentColor = colors.onSurface,
                border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.16f)),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedLyricLine(
                        nowPlaying = nowPlaying,
                        line = nowPlaying.line,
                        settings = settings,
                    )

                    val translation = nowPlaying.line.translation
                    if (settings.showTranslation && !translation.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = translation,
                            fontSize = (13f * settings.fontScale).sp,
                            lineHeight = (18f * settings.fontScale).sp,
                            color = colors.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedLyricLine(
    nowPlaying: NowPlayingUi,
    line: LyricLineUi,
    settings: OverlaySettings,
) {
    var positionMs by remember(nowPlaying) {
        mutableLongStateOf(nowPlaying.estimatedPosition())
    }

    LaunchedEffect(nowPlaying) {
        val state = nowPlaying.playbackState
        // state == null 是 3.x 服务端的正常情况（LegacyV37 不提供播放状态），
        // 必须按播放中处理；否则这里直接返回，填色会永远停在快照位置。
        if (state != null && state != PlaybackState.PLAYING) {
            positionMs = nowPlaying.position
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos {
                positionMs = nowPlaying.estimatedPosition()
            }
        }
    }

    KaraokeLine(
        line = line,
        positionMs = positionMs,
        karaoke = settings.karaokeEnabled,
        fontScale = settings.fontScale,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KaraokeLine(
    line: LyricLineUi,
    positionMs: Long,
    karaoke: Boolean,
    fontScale: Float,
) {
    val colors = MiuixTheme.colorScheme
    val idleColor = colors.onSurface.copy(alpha = 0.42f)
    val playedColor = colors.primary
    val fontSize = (20f * fontScale).sp
    val lineHeight = (27f * fontScale).sp

    if (!karaoke) {
        Text(
            text = line.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        return
    }

    if (line.words.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center,
        ) {
            line.words.forEach { word ->
                WordSpan(
                    word = word,
                    positionMs = positionMs,
                    idleColor = idleColor,
                    playedColor = playedColor,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                )
            }
        }
    } else {
        val fraction = ((positionMs - line.start).toFloat() / line.durationMs)
            .coerceIn(0f, 1f)
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = line.text,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = FontWeight.SemiBold,
                color = idleColor,
                textAlign = TextAlign.Center,
            )
            Text(
                text = line.text,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = FontWeight.SemiBold,
                color = playedColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.drawWithContent {
                    clipRect(right = size.width * fraction) {
                        this@drawWithContent.drawContent()
                    }
                },
            )
        }
    }
}

@Composable
private fun WordSpan(
    word: LyricWordUi,
    positionMs: Long,
    idleColor: Color,
    playedColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
) {
    val fraction = ((positionMs - word.start).toFloat() / word.durationMs)
        .coerceIn(0f, 1f)

    Box {
        Text(
            text = word.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.SemiBold,
            color = idleColor,
            softWrap = false,
            maxLines = 1,
        )
        if (fraction > 0f) {
            Text(
                text = word.text,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = FontWeight.SemiBold,
                color = playedColor,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.drawWithContent {
                    clipRect(right = size.width * fraction) {
                        this@drawWithContent.drawContent()
                    }
                },
            )
        }
    }
}
