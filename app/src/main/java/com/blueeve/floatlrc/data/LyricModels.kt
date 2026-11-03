package com.blueeve.floatlrc.data

import android.os.SystemClock
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.PlaybackState
import com.hchen.superlyricapi.model.StopReason
import com.hchen.superlyricapi.model.Word

/**
 * 界面直接消费的不可变歌词模型。
 *
 * SuperLyric 的回调在 Binder 线程执行；一律在回调里转换成这份数据，界面层不再引用库对象。
 */
data class NowPlayingUi(
    val lyricId: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val lineIndex: Int,
    val lineCount: Int,
    val line: LyricLineUi?,
    val nextLine: LyricLineUi?,
    val position: Long,
    val speed: Float,
    val playbackState: PlaybackState?,
    val publisher: String?,
    val capturedAtElapsedMs: Long,
) {
    val hasLyrics: Boolean
        get() = line != null && line.text.isNotBlank()

    /**
     * 根据快照时刻推算当前播放进度。
     *
     * SuperLyric 的 position 是推送时刻的快照，播放中只有状态/行变化才会再推送，
     * 因此悬浮窗需要结合 elapsedRealtime 自己插值。
     */
    fun estimatedPosition(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        val elapsed = (nowElapsedMs - capturedAtElapsedMs).coerceAtLeast(0L)
        // 3.x 服务端经 LegacyV37 转换后 state 为 null、speed 为 1f。
        // 此时必须按“正在播放”处理，否则进度会永远停在最后一帧的位置，
        // 悬浮窗的逐字填色就会卡住不动。
        val effectiveSpeed = when (playbackState) {
            PlaybackState.PAUSED,
            PlaybackState.STOPPED,
            PlaybackState.ERROR,
            PlaybackState.BUFFERING,
            -> 0f

            PlaybackState.PLAYING,
            null,
            -> if (speed > 0f) speed else 1f
        }
        val estimated = position + (elapsed * effectiveSpeed).toLong()
        return if (duration > 0L) estimated.coerceIn(0L, duration) else estimated.coerceAtLeast(0L)
    }
}

data class LyricLineUi(
    val text: String,
    val start: Long,
    val end: Long,
    val words: List<LyricWordUi>,
    val translation: String?,
    val phonetic: String?,
) {
    val durationMs: Long
        get() = (end - start).coerceAtLeast(1L)
}

data class LyricWordUi(
    val text: String,
    val start: Long,
    val end: Long,
) {
    val durationMs: Long
        get() = (end - start).coerceAtLeast(1L)
}

data class LyricConnection(
    val serviceAvailable: Boolean = false,
    val serverApiVersion: Int = 0,
    val registered: Boolean = false,
    val error: String? = null,
    val lastStopReason: StopReason? = null,
)

fun LyricView.toNowPlayingUi(): NowPlayingUi = NowPlayingUi(
    lyricId = lyric.id,
    title = lyric.title,
    artist = lyric.artist,
    album = lyric.album,
    duration = lyric.duration,
    lineIndex = index,
    lineCount = lyric.lineCount,
    line = line?.toUi(),
    nextLine = upcoming.firstOrNull()?.toUi(),
    position = position,
    speed = speed,
    playbackState = state,
    publisher = publisher,
    capturedAtElapsedMs = SystemClock.elapsedRealtime(),
)

private fun Line.toUi(): LyricLineUi = LyricLineUi(
    text = text,
    start = start,
    end = end,
    words = words.orEmpty().map { it.toUi() },
    translation = translation,
    phonetic = phonetic,
)

private fun Word.toUi(): LyricWordUi = LyricWordUi(
    text = text,
    start = start,
    end = end,
)
