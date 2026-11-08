package com.haofan.xiaoxinxin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.floatx.FloatService
import com.haofan.xiaoxinxin.ui.AppShell
import com.haofan.xiaoxinxin.ui.AppTheme
import com.haofan.xiaoxinxin.ui.OnboardingScreen
import com.haofan.xiaoxinxin.ui.XC
import com.haofan.xiaoxinxin.ui.XiaoxinxinTheme
import com.haofan.xiaoxinxin.ui.buildPalette
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var pendingFloatEnable = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EarnRepo.init(this)
        // App 启动也重排一次自动上班闹钟：用户改了上班时间但忘了重启 App 的情况也能对上
        com.haofan.xiaoxinxin.core.AutoPunch.reschedule(this)
        // 内容延伸到系统栏下方由 Compose 的 systemBarsPadding 负责，
        // 这样背景光斑能一直铺到状态栏后面，和系统岛同色更协调。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 底部导航要「透出」身后的内容，系统栏必须完全透明（默认的 scrim 会在玻璃上糊一条灰雾）
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 先把配色定下来，避免首帧用错主题闪一下
        val st0 = EarnRepo.state.value
        XC.use(buildPalette(AppTheme.of(st0.themeId), st0.themeAccent, st0.themeAlpha, st0.themeBg, st0.themeText))
        askNotificationPermission()

        setContent {
            val state by EarnRepo.state.collectAsState()
            val palette = buildPalette(AppTheme.of(state.themeId), state.themeAccent, state.themeAlpha, state.themeBg, state.themeText)
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
                if (!state.onboardingDone) {
                    OnboardingScreen(onDone = { EarnRepo.completeOnboarding() })
                } else {
                // 主页「去设置」用自增计数通知外壳翻页：计数变了就代表用户又点了一次
                var openSettingsTick by remember { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    while (true) {
                        EarnRepo.tick()
                        delay(1000)
                    }
                }
                LaunchedEffect(state.floatEnabled, state.islandEnabled) {
                    FloatService.needed(this@MainActivity)
                }

                // 外壳需要一个「切到设置页」的信号；用计数自增表达，重复点击也能响应
                AppShell(
                    openSettingsTick = openSettingsTick,
                    onOpenSettings = { openSettingsTick++ },
                    onToggleFloat = { enableFloat(!state.floatEnabled) },
                )
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
