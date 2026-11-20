package com.blueeve.floatlrc.overlay

import android.icu.text.BreakIterator
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blueeve.floatlrc.data.LyricLineUi
import com.blueeve.floatlrc.data.NowPlayingUi
import com.blueeve.floatlrc.data.OverlaySettings
import com.blueeve.floatlrc.data.PlaybackActivityMonitor
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
    val state = nowPlaying.playbackState
    val explicitlyPaused = state != null && state != PlaybackState.PLAYING
    // 3.x 服务端拿不到播放状态，用系统音乐输出是否活跃来推断暂停；
    // 4.0 服务端自带 state，直接用 state。
    val useAudioSignal = state == null

    var positionMs by remember(nowPlaying) {
        val pausedNow = explicitlyPaused ||
            (useAudioSignal && !PlaybackActivityMonitor.isMusicActive)
        mutableLongStateOf(
            if (pausedNow) nowPlaying.position else nowPlaying.estimatedPosition()
        )
    }
    // 本地插值的基准点：暂停时以冻结帧为新基准，避免把暂停时长算进去。
    var basePositionMs by remember(nowPlaying) { mutableLongStateOf(nowPlaying.position) }
    var baseElapsedMs by remember(nowPlaying) {
        mutableLongStateOf(nowPlaying.capturedAtElapsedMs)
    }

    LaunchedEffect(nowPlaying) {
        if (explicitlyPaused) {
            positionMs = nowPlaying.position
            return@LaunchedEffect
        }

        val speed = nowPlaying.effectiveSpeed
        var wasActive = !useAudioSignal || PlaybackActivityMonitor.isMusicActive
        while (true) {
            withFrameNanos {
                val now = SystemClock.elapsedRealtime()
                val active = !useAudioSignal || PlaybackActivityMonitor.isMusicActive
                if (active) {
                    if (!wasActive) {
                        // 暂停结束：从冻结的这一帧重新起算。
                        basePositionMs = positionMs
                        baseElapsedMs = now
                    }
                    val elapsed = (now - baseElapsedMs).coerceAtLeast(0L)
                    val raw = basePositionMs + (elapsed * speed).toLong()
                    positionMs = if (nowPlaying.duration > 0L) {
                        raw.coerceIn(0L, nowPlaying.duration)
                    } else {
                        raw.coerceAtLeast(0L)
                    }
                } else if (wasActive) {
                    // 刚刚暂停：把当前帧固定为基准，之后保持不动，
                    // 直到恢复播放或收到新快照（新快照会重置基准）。
                    basePositionMs = positionMs
                    baseElapsedMs = now
                }
                wasActive = active
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

    val timedLine = remember(line) { buildTimedLine(line) }
    if (timedLine.text.isBlank()) {
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

    // 同一段文本叠两层：底层是未唱颜色，上层是已唱颜色并按逐字进度裁剪。
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = timedLine.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.SemiBold,
            color = idleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        KaraokeFill(
            text = timedLine.text,
            glyphs = timedLine.glyphs,
            positionMs = positionMs,
            color = playedColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )
    }
}

@Composable
private fun KaraokeFill(
    text: String,
    glyphs: List<TimedGlyph>,
    positionMs: Long,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
        onTextLayout = { layoutResult = it },
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                val layout = layoutResult ?: return@drawWithContent
                val path = progressPath(layout, glyphs, positionMs)
                clipPath(path) {
                    this@drawWithContent.drawContent()
                }
            },
    )
}

/**
 * 把已唱到的字（以及当前字的左侧部分）拼成裁剪路径。
 *
 * 用 [TextLayoutResult.getBoundingBox] 拿真实排版坐标，因此自动适配居中、
 * 自动换行与字间距；不再用整行水平裁剪，避免"不管逐字时间、整行匀速扫过"。
 */
private fun progressPath(
    layout: TextLayoutResult,
    glyphs: List<TimedGlyph>,
    positionMs: Long,
): Path {
    val path = Path()
    val textLength = layout.layoutInput.text.length
    var offset = 0
    for (glyph in glyphs) {
        if (offset !in 0 until textLength) break
        val progress = glyph.progressAt(positionMs)
        if (progress > 0f) {
            val box = layout.getBoundingBox(offset)
            if (progress >= 1f) {
                path.addRect(box)
            } else {
                path.addRect(
                    Rect(
                        left = box.left,
                        top = box.top,
                        right = box.left + box.width * progress,
                        bottom = box.bottom,
                    )
                )
            }
        }
        offset += glyph.text.length
    }
    return path
}

/** 一行歌词拆分后的逐字时间轴。 */
private data class TimedLine(
    val text: String,
    val glyphs: List<TimedGlyph>,
)

/** 填色的最小单位：一个字（字素簇）及其时间区间。 */
private data class TimedGlyph(
    val text: String,
    val start: Long,
    val end: Long,
) {
    fun progressAt(positionMs: Long): Float = when {
        positionMs >= end -> 1f
        positionMs <= start -> 0f
        else -> (positionMs - start).toFloat() / (end - start)
    }
}

/**
 * 构造逐字时间轴。
 *
 * SuperLyric 的 words 可能是单字，也可能是「夜色」这样的多字片段。
 * 对多字片段按字数均分它的起止时间，让填色仍在字与字之间依次推进，
 * 而不是整段一起变色。完全没有逐字数据时（3.x 服务端常见），
 * 把整行时长按字数均分，至少做到“按字”推进。
 */
private fun buildTimedLine(line: LyricLineUi): TimedLine {
    val words = line.words
    if (words.isNotEmpty()) {
        val text = StringBuilder()
        val glyphs = ArrayList<TimedGlyph>()
        for (word in words) {
            val parts = splitGraphemes(word.text)
            if (parts.isEmpty()) continue
            val duration = word.durationMs
            val count = parts.size
            parts.forEachIndexed { index, part ->
                val start = word.start + duration * index / count
                val end = word.start + duration * (index + 1) / count
                glyphs += TimedGlyph(part, start, end)
                text.append(part)
            }
        }
        if (text.isNotBlank()) {
            return TimedLine(text.toString(), glyphs)
        }
    }
    return splitLineEvenly(line)
}

/** 无逐字数据时，把整行时长按字均分；空白字符不占用时长。 */
private fun splitLineEvenly(line: LyricLineUi): TimedLine {
    val parts = splitGraphemes(line.text)
    if (parts.isEmpty()) return TimedLine(line.text, emptyList())

    val weights = parts.map { if (it.isBlank()) 0 else 1 }
    val totalWeight = weights.sum()
    val glyphs = ArrayList<TimedGlyph>(parts.size)
    if (totalWeight == 0) {
        parts.forEach { glyphs += TimedGlyph(it, line.start, line.end) }
        return TimedLine(line.text, glyphs)
    }

    var consumed = 0
    parts.forEachIndexed { index, part ->
        val before = line.start + line.durationMs * consumed / totalWeight
        consumed += weights[index]
        val after = line.start + line.durationMs * consumed / totalWeight
        glyphs += TimedGlyph(part, before, after)
    }
    return TimedLine(line.text, glyphs)
}

/** 按用户可见的字符（字素簇）拆分，避免把 emoji 等代理对拆坏。 */
private fun splitGraphemes(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val result = ArrayList<String>(text.length)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result += text.substring(start, end)
        start = end
        end = iterator.next()
    }
    return result
}
