package com.blueeve.floatlrc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blueeve.floatlrc.data.LyricRepository
import com.blueeve.floatlrc.ui.FloatLrcTheme
import com.blueeve.floatlrc.ui.MainCallbacks
import com.blueeve.floatlrc.ui.MainScreen
import com.blueeve.floatlrc.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val context = LocalContext.current
            var hasOverlayPermission by remember {
                mutableStateOf(Settings.canDrawOverlays(context))
            }
            var hasNotificationPermission by remember {
                mutableStateOf(notificationPermissionGranted(context))
            }

            val overlayPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                hasNotificationPermission = notificationPermissionGranted(context)
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasOverlayPermission = Settings.canDrawOverlays(context)
                        hasNotificationPermission = notificationPermissionGranted(context)
                        LyricRepository.refreshNow()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val state by viewModel.uiState.collectAsStateWithLifecycle()

            FloatLrcTheme(state.settings.themeMode) {
                MainScreen(
                    state = state,
                    callbacks = MainCallbacks(
                        hasOverlayPermission = hasOverlayPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onToggleOverlay = { enabled ->
                            if (enabled && !hasOverlayPermission) {
                                requestOverlayPermission(context, overlayPermissionLauncher)
                            } else {
                                viewModel.setOverlayEnabled(enabled)
                            }
                        },
                        onRequestOverlayPermission = {
                            requestOverlayPermission(context, overlayPermissionLauncher)
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenSuperLyric = {
                            openUrl(context, "https://github.com/HChenX/SuperLyric")
                        },
                        onRefresh = viewModel::refresh,
                        onShowTranslationChange = viewModel::setShowTranslation,
                        onKaraokeChange = viewModel::setKaraokeEnabled,
                        onLockChange = viewModel::setLockPosition,
                        onFontScaleChange = viewModel::setFontScale,
                        onOpacityChange = viewModel::setBackgroundOpacity,
                        onThemeCycle = viewModel::cycleTheme,
                        onResetPosition = viewModel::resetOverlayPosition,
                        onInjectDemo = viewModel::injectDemoLyric,
                    ),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LyricRepository.acquire()
    }

    override fun onStop() {
        LyricRepository.release()
        super.onStop()
    }

    private fun notificationPermissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}

private fun requestOverlayPermission(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val appSpecific = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
    runCatching { launcher.launch(appSpecific) }
        .onFailure {
            runCatching {
                launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
