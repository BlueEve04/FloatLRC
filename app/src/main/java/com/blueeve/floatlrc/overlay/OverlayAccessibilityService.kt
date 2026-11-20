package com.blueeve.floatlrc.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 只为悬浮窗提供高于锁屏的窗口层级，不读取、不操作任何屏幕内容。
 *
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] 的层级低于锁屏，
 * 系统在锁屏显示时会把它整体隐藏（AOSP DisplayPolicy 的 keyguard 规则）。
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] 层级高于锁屏，
 * 因此这里借无障碍服务的窗口令牌来承载同一个 Compose 悬浮窗。
 *
 * 服务连接后由 [LyricOverlayService] 通过 [overlayWindowManager] 添加视图；
 * 用户在无障碍设置里关闭本服务时，[connected] 变化会让悬浮窗回退到普通类型。
 */
class OverlayAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        if (instance === this) instance = null
        _connected.value = false
    }

    /**
     * 带无障碍窗口令牌的 WindowManager。
     *
     * [AccessibilityService.getSystemService] 会用连接令牌设置 defaultToken，
     * 这是 TYPE_ACCESSIBILITY_OVERLAY 能成功 addView 的前提。
     */
    val overlayWindowManager: WindowManager
        get() = getSystemService(WINDOW_SERVICE) as WindowManager

    companion object {
        @Volatile
        var instance: OverlayAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        /** 打开系统无障碍设置，让用户开启/关闭「浮言锁屏歌词」。 */
        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
