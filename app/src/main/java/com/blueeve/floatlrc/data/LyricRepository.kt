package com.blueeve.floatlrc.data

import android.os.SystemClock
import com.hchen.superlyricapi.LyricListener
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.Receiver
import com.hchen.superlyricapi.SuperLyric
import com.hchen.superlyricapi.model.PlaybackState
import com.hchen.superlyricapi.model.Stop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SuperLyric 接收端与歌词状态的唯一入口。
 *
 * 主界面和悬浮窗服务都通过 [acquire] / [release] 做引用计数：
 * 任一界面存活时保持注册，最后一个消费者退出后才断开 Binder。
 */
object LyricRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _nowPlaying = MutableStateFlow<NowPlayingUi?>(null)
    val nowPlaying: StateFlow<NowPlayingUi?> = _nowPlaying.asStateFlow()

    private val _connection = MutableStateFlow(LyricConnection())
    val connection: StateFlow<LyricConnection> = _connection.asStateFlow()

    private val lock = Any()
    private var refCount = 0

    @Volatile
    private var registered = false

    @Volatile
    private var acceptingEvents = false

    private var watchJob: Job? = null

    private val listener = object : LyricListener {
        override fun onLyric(view: LyricView) {
            if (!acceptingEvents) return
            _nowPlaying.value = view.toNowPlayingUi()
            _connection.update {
                it.copy(
                    serviceAvailable = true,
                    registered = true,
                    error = null,
                    lastStopReason = null,
                )
            }
        }

        override fun onStop(stop: Stop) {
            if (!acceptingEvents) return
            _nowPlaying.value = null
            _connection.update {
                it.copy(
                    registered = true,
                    lastStopReason = stop.reason,
                )
            }
        }
    }

    fun acquire() {
        synchronized(lock) {
            refCount++
            acceptingEvents = true
            if (refCount == 1) startWatching()
        }
    }

    fun release() {
        synchronized(lock) {
            if (refCount == 0) return
            refCount--
            if (refCount > 0) return
            acceptingEvents = false
            watchJob?.cancel()
            watchJob = null
            scope.launch {
                // release 之后可能立刻又 acquire（例如旋转屏幕），此时不要拆掉新会话。
                if (refCount > 0) return@launch
                unregisterInternal()
                if (refCount == 0) _nowPlaying.value = null
            }
        }
    }

    /** 主界面回到前台、用户手动刷新时调用。 */
    fun refreshNow() {
        if (refCount <= 0) return
        scope.launch {
            ensureRegistered()
            pullLatest()
        }
    }

    private fun startWatching() {
        watchJob = scope.launch {
            while (isActive) {
                ensureRegistered()
                delay(if (registered) 10_000L else 3_000L)
            }
        }
    }

    private suspend fun ensureRegistered() {
        val available = runCatching { SuperLyric.isAvailable() }.getOrDefault(false)
        val apiVersion = if (available) {
            runCatching { SuperLyric.serverApiVersion() }.getOrDefault(0)
        } else {
            0
        }

        _connection.update {
            it.copy(serviceAvailable = available, serverApiVersion = apiVersion)
        }

        if (!available) {
            if (registered) {
                registered = false
                _connection.update { it.copy(registered = false) }
            }
            return
        }
        if (registered) return

        val result = runCatching { SuperLyric.receiver().register(listener) }
            .getOrElse { error ->
                _connection.update {
                    it.copy(
                        registered = false,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
                return
            }

        when (result) {
            Receiver.RegisterResult.OK -> {
                registered = true
                _connection.update { it.copy(registered = true, error = null) }
                pullLatest()
            }

            Receiver.RegisterResult.SERVICE_UNAVAILABLE -> {
                _connection.update {
                    it.copy(registered = false, error = "SuperLyric 服务不可用")
                }
            }

            Receiver.RegisterResult.REJECTED_QUOTA -> {
                _connection.update {
                    it.copy(registered = false, error = "接收器配额已满")
                }
            }

            Receiver.RegisterResult.REJECTED -> {
                _connection.update {
                    it.copy(registered = false, error = "SuperLyric 拒绝了注册请求")
                }
            }
        }
    }

    /** 冷启动或重新注册后补拉一次当前会话。 */
    private fun pullLatest() {
        val latest = runCatching { SuperLyric.receiver().latest() }.getOrNull() ?: return
        if (acceptingEvents) listener.onLyric(latest)
    }

    private fun unregisterInternal() {
        if (!registered) return
        registered = false
        runCatching { SuperLyric.receiver().unregister(listener) }
        _connection.update { it.copy(registered = false) }
    }

    /**
     * 调试用：不依赖 SuperLyric 服务，直接注入一份带逐字时间的歌词。
     * 用于在模拟器或未激活模块的手机上预览悬浮窗。
     */
    fun injectDemoLyric() {
        val words = listOf(
            word("夜色", 0, 600),
            word("沿着", 600, 1_100),
            word("窗台", 1_100, 1_700),
            word("漫", 1_700, 2_100),
            word("进来", 2_100, 2_900),
        )
        val line = LyricLineUi(
            text = "夜色沿着窗台漫进来",
            start = 0,
            end = 3_000,
            words = words,
            translation = "Night creeps in along the windowsill",
            phonetic = null,
        )
        val nextLine = LyricLineUi(
            text = "把心事轻轻翻到下一页",
            start = 3_200,
            end = 6_500,
            words = listOf(
                word("把", 3_200, 3_500),
                word("心事", 3_500, 4_100),
                word("轻轻", 4_100, 4_800),
                word("翻到", 4_800, 5_300),
                word("下一页", 5_300, 6_200),
            ),
            translation = "Turn the worries to the next page",
            phonetic = null,
        )
        _nowPlaying.value = NowPlayingUi(
            lyricId = "demo-floatlrc",
            title = "浮言 · 演示",
            artist = "FloatLRC",
            album = "Demo",
            duration = 180_000,
            lineIndex = 0,
            lineCount = 8,
            line = line,
            nextLine = nextLine,
            position = 0,
            speed = 1f,
            playbackState = PlaybackState.PLAYING,
            publisher = "demo",
            capturedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun word(text: String, start: Long, end: Long) = LyricWordUi(text, start, end)
}
