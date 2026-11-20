package com.blueeve.floatlrc.data

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 3.x 服务端不提供播放状态，暂停后也不再推送进度帧；
 * 悬浮窗只能靠本地插值推进，于是暂停后歌词会继续滚动。
 *
 * 这里用 [AudioManager.isMusicActive] 作为补充信号：
 * 系统里没有活跃的音乐输出时，认为播放已暂停，悬浮窗冻结插值。
 * 4.0 服务端自带 playbackState 时不依赖这个信号。
 */
object PlaybackActivityMonitor {

    private const val POLL_INTERVAL_MS = 150L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    @Volatile
    var isMusicActive: Boolean = true
        private set

    fun start(context: Context) {
        if (pollJob != null) return
        val audioManager = context.applicationContext
            .getSystemService(AudioManager::class.java) ?: return

        // 先同步取一次，避免悬浮窗刚出现时多走几帧。
        isMusicActive = readMusicActive(audioManager)
        pollJob = scope.launch {
            while (isActive) {
                isMusicActive = readMusicActive(audioManager)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        isMusicActive = true
    }

    private fun readMusicActive(audioManager: AudioManager): Boolean =
        runCatching { audioManager.isMusicActive }.getOrDefault(true)
}
