package com.haofan.xiaoxinxin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.haofan.xiaoxinxin.core.DetailMode
import kotlinx.coroutines.launch

/**
 * 应用外壳：三页 Pager + 悬浮 Liquid Glass 导航；点首页卡片进二级页时整屏切换。
 *
 * 2.0 起首页是「进度卡墙」，三个二级页各自独立（薪酬 / 倒计时 / 纪念日），
 * 进二级页后底栏收起、右上角出返回 —— 和 macOS 的页面层级一致。
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
    var detail by remember { mutableStateOf<DetailMode?>(null) }

    LaunchedEffect(openSettingsTick) {
        if (openSettingsTick > 0) {
            detail = null
            pagerState.animateScrollToPage(Tab.SETTINGS.ordinal)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(XC.bg),
    ) {
        AnimatedContent(
            targetState = detail,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200))) togetherWith
                        fadeOut(tween(140))
                } else {
                    fadeIn(tween(200)) togetherWith
                        (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(160)))
                }
            },
            label = "page",
        ) { mode ->
            if (mode != null) {
                LiquidBackground {
                    DetailHost(mode = mode, onBack = { detail = null })
                }
            } else {
                // 真实底部栏布局：内容区占满除 dock 外的空间（weight(1f)），
                // dock 作为第二个子项固定在底部 —— pager 物理上不可能盖到 dock 上，
                // 从根上消除之前 overlay 方案下「最后一张卡被 dock 压住」的遮挡。
                Column(Modifier.fillMaxSize().background(XC.bg)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { index ->
                            when (Tab.entries[index]) {
                                Tab.HOME -> HomeScreen(
                                    onOpenDetail = { detail = it },
                                    onOpenSettings = onOpenSettings,
                                    onToggleFloat = onToggleFloat,
                                )
                                Tab.STATS -> StatsScreen()
                                Tab.SETTINGS -> SettingsScreen()
                            }
                        }
                    }

                    // ── 底部导航 ──
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
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
        }
    }
}

/** 三个二级页的分发；统一带背景与返回手势。 */
@Composable
private fun DetailHost(mode: DetailMode, onBack: () -> Unit) {
    when (mode) {
        DetailMode.SALARY -> SalaryScreen(onBack = onBack, onOpenSettings = {})
        DetailMode.COUNTDOWN -> CountdownScreen(onBack = onBack)
        DetailMode.RECORD_DAY -> RecordDayScreen(onBack = onBack)
    }
}

/**
 * 玻璃背板。它自己不做任何业务绘制，只负责把「父窗口画面」模糊后交出来。
 */
/** 反射拿 View 自带 RenderNode（API 29+），拿不到返回 null 走兜底。 */
private object RenderNodeCompat {
    fun nodeOf(v: android.view.View): android.graphics.RenderNode? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null
        return try {
            val m = android.view.View::class.java.getMethod("getRenderNode")
            m.invoke(v) as? android.graphics.RenderNode
        } catch (_: Throwable) {
            null
        }
    }
}

private class DockHost(ctx: android.content.Context) : android.view.View(ctx) {
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

        val canBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        val loc = IntArray(2)
        getLocationInWindow(loc)

        canvas.save()
        // 把 canvas 原点移到「本 View 在窗口里的位置」，
        // drawRenderNode 会以窗口坐标重绘整棵视图树，于是本区域正好落进 canvas
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())

        if (canBlur) {
            rn.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP
                )
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
