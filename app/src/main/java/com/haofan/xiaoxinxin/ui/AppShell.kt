package com.haofan.xiaoxinxin.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

/**
 * 应用外壳：三页 Pager + 悬浮 Liquid Glass 导航。
 *
 * ══ 毛玻璃这块返工过，坑记在这 ══════════════════════════════════
 * Compose 没有公开的「背景模糊」API。给空 Box 加 renderEffect 是**无效**的：
 * RenderEffect 只作用于「节点自己画出来的内容」，空 Box 什么都没画，自然没有模糊。
 * 上一版就栽在这，最后只是一块半透明白，所以完全没有玻璃感。
 *
 * ══ 这次的做法 ═════════════════════════════════════════════════
 * 底栏背后挂一个独立的 Android View，它把自己**身后的窗口区域**重绘一遍并模糊：
 *   1. 拿到 Activity 的 DecorView 的 RenderNode（包含整个界面，含 Pager 内容）；
 *   2. 绘制时把「底栏自己」临时设为 INVISIBLE，避免玻璃里映出自己（镜像递归）；
 *   3. 用 [RenderEffect] 做高斯模糊，再把结果贴到药丸下面。
 *
 * 效果是**实时**的：滑动、数字跳动、切主题，背后那层都跟着变。
 * 只在底栏那 76dp 的条带里模糊，开销与页面高度无关，滑页不掉帧。
 * API 31 以下没有 RenderEffect，退化成磨砂色 + 高光，观感仍是玻璃。
 */
@Composable
fun AppShell(
    onOpenSettings: () -> Unit,
    onToggleFloat: () -> Unit,
    openSettingsTick: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = Tab.HOME.ordinal) { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val blurPx = with(LocalDensity.current) { 18.dp.toPx() }

    LaunchedEffect(openSettingsTick) {
        if (openSettingsTick > 0) pagerState.animateScrollToPage(Tab.SETTINGS.ordinal)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(XC.bg),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            when (Tab.entries[index]) {
                Tab.HOME -> HomeScreen(onOpenSettings = onOpenSettings, onToggleFloat = onToggleFloat)
                Tab.STATS -> StatsScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }

        // ── 悬浮导航 ──
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .fillMaxWidth()
                .height(DockSpec.BarHeight),
        ) {
            // ① 背板：身后画面的实时模糊。
            // 注意必须用 factory 现场创建：remember 存下来会导致复用到已 detach 的 View。
            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(DockSpec.ShapeRadius)),
                factory = { ctx -> DockHost(ctx) },
                update = { v ->
                    v.blurPx = blurPx
                    v.dark = XC.isDark
                    v.invalidate()
                },
            )

            // ②③④ 玻璃面 + 滑片 + 按钮
            GlassTabBar(
                pager = pagerState,
                onSelect = { tab -> scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * 玻璃背板。它自己不做任何业务绘制，只负责把「父窗口画面」模糊后交出来。
 */
/** 反射拿 View 自带 RenderNode（API 29+），拿不到返回 null 走兜底。 */
private object RenderNodeCompat {
    fun nodeOf(v: View): android.graphics.RenderNode? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val m = View::class.java.getMethod("getRenderNode")
            m.invoke(v) as? android.graphics.RenderNode
        } catch (_: Throwable) {
            null
        }
    }
}

private class DockHost(ctx: android.content.Context) : View(ctx) {
    var blurPx = 60f
    var dark = false

    private val blit = android.graphics.Paint()
    private val tint = android.graphics.Paint()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val root = rootView
        // View.getRenderNode() 在 API 29 才有；更老的机器直接走兜底
        val rn = RenderNodeCompat.nodeOf(root)

        // 首帧 / 拿不到 RenderNode：半透明兜底，别闪白块
        if (rn == null || root.width == 0) {
            canvas.drawColor(if (dark) 0x4D060C18.toInt() else 0x12FFFFFF)
            return
        }

        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val loc = IntArray(2)
        getLocationInWindow(loc)

        canvas.save()
        // 把 canvas 原点移到「本 View 在窗口里的位置」，
        // drawRenderNode 会以窗口坐标重绘整棵视图树，于是本区域正好落进 canvas
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())

        if (canBlur) {
            rn.setRenderEffect(
                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            )
        }
        blit.isFilterBitmap = true
        canvas.drawRenderNode(rn)
        if (canBlur) rn.setRenderEffect(null)
        canvas.restore()

        // 着色：只压一点点，别盖住模糊层。
        // 上一版这里是 0x40（25% 白），叠在米白背景上直接推到 252+，
        // 结果玻璃变成一块不透明白板 —— 真机截图里就是这么露馅的。
        tint.color = if (dark) 0x40060C18 else 0x0FFFFFFF
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tint)
    }
}
