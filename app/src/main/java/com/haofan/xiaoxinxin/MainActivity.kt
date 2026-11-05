package com.haofan.xiaoxinxin

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.floatx.FloatService
import com.haofan.xiaoxinxin.ui.HomeScreen
import com.haofan.xiaoxinxin.ui.SettingsScreen
import com.haofan.xiaoxinxin.ui.ThemeMode
import com.haofan.xiaoxinxin.ui.XC
import com.haofan.xiaoxinxin.ui.XiaoxinxinTheme
import com.haofan.xiaoxinxin.ui.resolvePalette
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var pendingFloatEnable = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EarnRepo.init(this)
        // 内容延伸到系统栏下方由 Compose 的 systemBarsPadding 负责，
        // 这样背景光斑能一直铺到状态栏后面，和系统岛同色更协调。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 先把配色定下来，避免首帧用错主题闪一下
        val night0 = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        XC.use(resolvePalette(ThemeMode.of(EarnRepo.state.value.themeMode), night0))
        askNotificationPermission()

        setContent {
            val state by EarnRepo.state.collectAsState()
            val night = isSystemInDarkTheme()
            val palette = resolvePalette(ThemeMode.of(state.themeMode), night)
            val view = LocalView.current

            // 配色写回全局单例：各处 XC.xxx 读的是 state，会自动重组。
            // 必须放在 SideEffect 里，组合期间改 state 会触发重组冲突。
            SideEffect { XC.use(palette) }
            // 浅色背景要深色状态栏图标，否则时间和信号看不见
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !palette.isDark
                    isAppearanceLightNavigationBars = !palette.isDark
                }
            }

            XiaoxinxinTheme {
                var page by remember { mutableStateOf("home") }

                LaunchedEffect(Unit) {
                    while (true) {
                        EarnRepo.tick()
                        delay(1000)
                    }
                }
                LaunchedEffect(state.floatEnabled, state.islandEnabled) {
                    FloatService.needed(this@MainActivity)
                }

                Box(Modifier.fillMaxSize().background(XC.bg)) {
                    when (page) {
                        "home" -> HomeScreen(
                            onOpenSettings = { page = "settings" },
                            onToggleFloat = { enableFloat(!state.floatEnabled) },
                        )
                        else -> SettingsScreen(onBack = { page = "home" })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FloatService.setAppForeground(this, true)
        if (pendingFloatEnable && canDrawOverlays()) {
            pendingFloatEnable = false
            EarnRepo.setFloatEnabled(true)
            FloatService.needed(this)
        }
    }

    override fun onPause() {
        FloatService.setAppForeground(this, false)
        super.onPause()
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun enableFloat(on: Boolean) {
        if (!on) {
            EarnRepo.setFloatEnabled(false)
            FloatService.needed(this)
            return
        }
        if (canDrawOverlays()) {
            EarnRepo.setFloatEnabled(true)
            FloatService.needed(this)
        } else {
            // 需要用户去系统设置里手动开启「显示在其他应用上层」
            pendingFloatEnable = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
