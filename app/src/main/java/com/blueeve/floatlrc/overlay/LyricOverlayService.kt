package com.blueeve.floatlrc.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.blueeve.floatlrc.FloatLrcApp
import com.blueeve.floatlrc.MainActivity
import com.blueeve.floatlrc.R
import com.blueeve.floatlrc.data.AppPreferences
import com.blueeve.floatlrc.data.LyricRepository
import com.blueeve.floatlrc.data.NowPlayingUi
import com.blueeve.floatlrc.data.OverlaySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * 用 TYPE_APPLICATION_OVERLAY 承载一个 ComposeView。
 *
 * 前台服务保证悬浮窗在切到音乐应用后仍存活；通知提供停止入口。
 */
class LyricOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var preferences: AppPreferences
    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var composeView: ComposeView
    private lateinit var overlayOwner: OverlayLifecycleOwner

    private var attached = false
    private var repositoryAcquired = false

    override fun onCreate() {
        super.onCreate()
        preferences = (application as FloatLrcApp).preferences
        notificationManager = getSystemService(NotificationManager::class.java)

        createNotificationChannel()
        startForegroundNotification(null)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        LyricRepository.acquire()
        repositoryAcquired = true
        windowManager = getSystemService(WindowManager::class.java)
        runCatching { createOverlay() }
            .onFailure {
                Log.e(TAG, "Unable to attach the lyric overlay", it)
                stopSelf()
                return
            }
        observeSettings()
        observeNowPlayingForNotification()

        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_RESET_POSITION -> {
                preferences.clearOverlayPosition()
                centerWindow()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!attached) return
        val screen = screenSize()
        params.width = overlayWidth(screen)
        clampToScreen(screen)
        updateLayout()
    }

    override fun onDestroy() {
        scope.cancel()
        if (attached) {
            runCatching { windowManager.removeView(composeView) }
            attached = false
        }
        if (::overlayOwner.isInitialized) overlayOwner.destroy()
        if (repositoryAcquired) {
            LyricRepository.release()
            repositoryAcquired = false
        }
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createOverlay() {
        val screen = screenSize()
        params = WindowManager.LayoutParams(
            overlayWidth(screen),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseWindowFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 不让系统栏裁剪悬浮窗，歌词可以移动到屏幕任意位置。
                fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        overlayOwner = OverlayLifecycleOwner().apply { create() }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setContent {
                val nowPlaying by LyricRepository.nowPlaying.collectAsStateWithLifecycle()
                val settings by preferences.settings.collectAsStateWithLifecycle()
                OverlayLyricView(
                    nowPlaying = nowPlaying,
                    settings = settings,
                    onDrag = ::moveWindow,
                    onDragEnd = { preferences.saveOverlayPosition(params.x, params.y) },
                    onTap = ::openMainActivity,
                )
            }
        }

        windowManager.addView(composeView, params)
        attached = true
        overlayOwner.start()
        overlayOwner.resume()
        applyTouchable(!preferences.settings.value.lockPosition)

        composeView.post {
            restoreOrCenterPosition(screenSize())
        }
    }

    private fun observeSettings() {
        scope.launch {
            preferences.settings
                .map { it.lockPosition }
                .distinctUntilChanged()
                .collect { locked -> applyTouchable(!locked) }
        }
    }

    private fun observeNowPlayingForNotification() {
        scope.launch {
            LyricRepository.nowPlaying
                // 3.x 服务端会高频推送进度帧；只有切歌/换行才需要重发通知。
                .distinctUntilChangedBy { it?.let { np -> np.lyricId to np.line?.text } }
                .collect { nowPlaying ->
                    updateNotification(nowPlaying)
                }
        }
    }

    private fun moveWindow(dx: Float, dy: Float) {
        if (!attached) return
        val screen = screenSize()
        val viewHeight = composeView.height.takeIf { it > 0 } ?: dp(80)
        val maxX = (screen.x - params.width).coerceAtLeast(0)
        val maxY = (screen.y - viewHeight).coerceAtLeast(0)
        params.x = (params.x + dx.toInt()).coerceIn(0, maxX)
        params.y = (params.y + dy.toInt()).coerceIn(0, maxY)
        updateLayout()
    }

    private fun restoreOrCenterPosition(screen: Point) {
        val savedX = preferences.settings.value.offsetX
        val savedY = preferences.settings.value.offsetY
        if (savedX >= 0 && savedY >= 0) {
            params.x = savedX
            params.y = savedY
        } else {
            params.x = ((screen.x - params.width) / 2).coerceAtLeast(0)
            params.y = dp(110)
        }
        clampToScreen(screen)
        updateLayout()
    }

    private fun centerWindow() {
        if (!attached) return
        val screen = screenSize()
        params.x = ((screen.x - params.width) / 2).coerceAtLeast(0)
        params.y = dp(110)
        clampToScreen(screen)
        updateLayout()
        preferences.saveOverlayPosition(params.x, params.y)
    }

    private fun clampToScreen(screen: Point) {
        val viewHeight = composeView.height.takeIf { it > 0 } ?: dp(80)
        params.x = params.x.coerceIn(0, (screen.x - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screen.y - viewHeight).coerceAtLeast(0))
    }

    private fun applyTouchable(touchable: Boolean) {
        if (!attached) return
        val newFlags = if (touchable) {
            baseWindowFlags()
        } else {
            baseWindowFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (params.flags == newFlags) return
        params.flags = newFlags
        updateLayout()
    }

    private fun updateLayout() {
        if (!attached) return
        runCatching { windowManager.updateViewLayout(composeView, params) }
    }

    private fun overlayWidth(screen: Point): Int =
        min(dp(440), screen.x - dp(24)).coerceAtLeast(dp(180))

    private fun baseWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundNotification(nowPlaying: NowPlayingUi?) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(nowPlaying),
            type,
        )
    }

    private fun updateNotification(nowPlaying: NowPlayingUi?) {
        runCatching {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(nowPlaying))
        }
    }

    private fun buildNotification(nowPlaying: NowPlayingUi?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LyricOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = nowPlaying?.title?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val text = nowPlaying?.line?.text?.takeIf { it.isNotBlank() }
            ?: nowPlaying?.artist?.takeIf { it.isNotBlank() }
            ?: getString(R.string.notification_waiting)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Point {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        val display = windowManager.defaultDisplay
        return Point().also { display.getRealSize(it) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "FloatLrcOverlay"

        const val ACTION_STOP = "com.blueeve.floatlrc.action.STOP_OVERLAY"
        const val ACTION_RESET_POSITION = "com.blueeve.floatlrc.action.RESET_POSITION"

        private const val CHANNEL_ID = "floatlrc_overlay"
        private const val NOTIFICATION_ID = 1001

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LyricOverlayService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LyricOverlayService::class.java))
        }

        fun resetPosition(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, LyricOverlayService::class.java)
                        .setAction(ACTION_RESET_POSITION),
                )
            }
        }
    }
}
