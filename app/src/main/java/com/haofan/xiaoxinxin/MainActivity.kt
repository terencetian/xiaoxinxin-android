package com.haofan.xiaoxinxin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.floatx.FloatService
import com.haofan.xiaoxinxin.ui.HomeScreen
import com.haofan.xiaoxinxin.ui.SettingsScreen
import com.haofan.xiaoxinxin.ui.XC
import com.haofan.xiaoxinxin.ui.XiaoxinxinTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var pendingFloatEnable = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EarnRepo.init(this)
        askNotificationPermission()

        setContent {
            XiaoxinxinTheme {
                var page by remember { mutableStateOf("home") }
                val state by EarnRepo.state.collectAsState()

                LaunchedEffect(Unit) {
                    while (true) {
                        EarnRepo.tick()
                        delay(1000)
                    }
                }
                LaunchedEffect(state.floatEnabled) {
                    FloatService.syncVisibility(this@MainActivity, state.floatEnabled)
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
            FloatService.syncVisibility(this, true)
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
            FloatService.syncVisibility(this, false)
            return
        }
        if (canDrawOverlays()) {
            EarnRepo.setFloatEnabled(true)
            FloatService.syncVisibility(this, true)
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
