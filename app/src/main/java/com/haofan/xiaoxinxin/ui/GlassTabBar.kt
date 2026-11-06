package com.haofan.xiaoxinxin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** 底部三格。 */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("主页", Icons.Outlined.Home),
    STATS("统计", Icons.Outlined.BarChart),
    SETTINGS("设置", Icons.Outlined.Settings),
}

/**
 * 底栏尺寸集中在这里。
 *
 * ══ 真机返工记（vivo X200 Pro，1.9.6 → 1.9.7）═════════════════
 * 上一版截图上「看不出玻璃、看不清选中」，逐像素量过之后是这两个数错了：
 *
 *  Inset 7dp —— 滑片 7dp 内缩 + 29dp 圆角，等于顶满整格再裁圆角，
 *              渲染出来是一根细长胶囊贴在栏边上，很廉价。改 10dp 后
 *              滑片四周有明确留白，才像个「浮在玻璃上的晶片」。
 *
 *  滑片用 0xF7FFFFFF（97% 白）盖在已经被压白的底栏上，两者只差 3 个色阶，
 *  肉眼等于没有选中态。现在滑片不靠「更白」，靠半透明白 + 金色描边 + 投影
 *  拉开层次 —— 在纯白面上也能看见。
 */
object DockSpec {
    val BarHeight = 78.dp
    val ShapeRadius = 39.dp
    val Inset = 10.dp
    val IconSize = 23.dp
    /** 滑片圆角：比内嵌后的方角更圆，收成胶囊形。 */
    val SliderRadius = 29.dp
}

/**
 * 滑片位置的来源有两套，按当前手势择一：
 *  - 手指拖动页面 → 跟手连续移动（用 currentPage + currentPageOffsetFraction）；
 *  - 点击底栏 → 弹簧动画滑过去，带一次轻微过冲。
 *
 * 只用 pagerState.currentPage 的话拖动时滑片不动；只用动画值的话拖动时会打架。
 */
@Composable
private fun rememberTabPosition(pager: PagerState): Float {
    val target = pager.currentPage.toFloat()
    val live = pager.currentPage + pager.currentPageOffsetFraction
    val anim = remember { Animatable(target) }
    var settledTarget by remember { mutableStateOf(target) }

    LaunchedEffect(target, pager.isScrollInProgress) {
        if (target != settledTarget) {
            settledTarget = target
            anim.animateTo(
                target,
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    // 拖动中直接跟手；静止时用动画值（保证点击后有过冲）
    return if (pager.isScrollInProgress) live else anim.value
}

/**
 * Liquid Glass 底栏。
 *
 * 「玻璃感」= 真模糊 + 高光 + 双层折边，缺一不可：
 *  - 真模糊由 [AppShell] 的背板层提供（身后的页面真的被高斯模糊了）；
 *  - 高光是自上而下衰减的白色 + 底部一道反光，模拟光线穿过玻璃的路径；
 *  - 折边是外圈亮线 + 内圈暗线，对应玻璃边缘两次折射。
 *
 * ══ alpha 预算（关键，别再加白）══════════════════════════════
 * 一屏米白背景本身已经是 247 亮度，玻璃上每一层白都会往上推：
 *   模糊层 tint 0x14FFFFFF → 玻璃面 0x2E→0x12 → 顶部 gloss 0.34
 * 三层叠起来落在 251~253，正好比页面亮一点、又还看得见背景色，
 * 这就是「玻璃」和「白板」的分界线。再加白就是白板了。
 */
@Composable
fun GlassTabBar(
    pager: PagerState,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DockSpec.ShapeRadius)
    val pos = rememberTabPosition(pager)

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF)))
            )
            .border(1.dp, XC.edge, shape)
            .padding(1.dp)
            .border(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (XC.isDark) 0.22f else 0.55f),
                        Color.Transparent,
                    )
                ),
                shape,
            ),
    ) {
        // 高光：顶亮底透，做出玻璃的厚度
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .drawWithCache {
                    val gloss = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (XC.isDark) 0.20f else 0.34f),
                        0.30f to Color.White.copy(alpha = if (XC.isDark) 0.06f else 0.10f),
                        0.52f to Color.Transparent,
                        0.86f to Color.Transparent,
                        1f to Color.White.copy(alpha = if (XC.isDark) 0.10f else 0.26f),
                    )
                    onDrawWithContent { drawRect(gloss) }
                },
        )

        // 选中滑片
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemW = (maxWidth - DockSpec.Inset * 2) / Tab.entries.size
            val sliderShape = RoundedCornerShape(DockSpec.SliderRadius)
            Box(
                Modifier
                    .offset(x = DockSpec.Inset + itemW * pos, y = DockSpec.Inset)
                    .width(itemW)
                    .height(DockSpec.BarHeight - DockSpec.Inset * 2)
                    .shadow(
                        elevation = 6.dp,
                        shape = sliderShape,
                        ambientColor = XC.gold.copy(alpha = 0.30f),
                        spotColor = XC.gold.copy(alpha = 0.45f),
                    )
                    .clip(sliderShape)
                    // 半透明白：不遮住背后的模糊，只把这一格「提亮 + 折射」
                    .background(
                        Brush.verticalGradient(
                            if (XC.isDark) {
                                listOf(Color(0x38FFFFFF), Color(0x0DFFFFFF))
                            } else {
                                listOf(Color(0x8AFFFFFF), Color(0x40FFFFFF))
                            }
                        )
                    )
                    // 内圈白色亮线：玻璃切边的第一道折射
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (XC.isDark) 0.30f else 0.85f),
                                Color.White.copy(alpha = 0.10f),
                            )
                        ),
                        sliderShape,
                    )
                    .padding(1.dp)
                    // 外圈金色描边：选中态真正「看得见」靠的是这一笔
                    .border(
                        1.4.dp,
                        Brush.verticalGradient(
                            listOf(
                                XC.gold.copy(alpha = if (XC.isDark) 0.85f else 0.72f),
                                XC.goldDim.copy(alpha = if (XC.isDark) 0.55f else 0.38f),
                            )
                        ),
                        sliderShape,
                    ),
            ) {
                // 滑片自带一道顶部高光，做出晶片的弧面
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(topStart = DockSpec.SliderRadius, topEnd = DockSpec.SliderRadius))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = if (XC.isDark) 0.16f else 0.70f), Color.Transparent)
                            )
                        ),
                )
            }
        }

        // 三格按钮
        Row(Modifier.fillMaxSize().padding(DockSpec.Inset)) {
            Tab.entries.forEachIndexed { i, tab ->
                TabButton(
                    tab = tab,
                    near = (1f - abs(pos - i)).coerceIn(0f, 1f),
                    onClick = { onSelect(tab) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * 单格按钮。三种动效叠在一起：
 *  1. 按下整体缩到 0.88 —— 快进（stiffness 大、无回弹），像手指压进玻璃；
 *  2. 松手弹回 1.0 —— 低阻尼弹簧，允许一次轻微过冲；
 *  3. 图标随选中程度放大 0.94→1.08 并上浮，文字颜色由灰渐金。
 */
@Composable
private fun TabButton(
    tab: Tab,
    near: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else Spring.DampingRatioLowBouncy,
            stiffness = if (pressed) Spring.StiffnessHigh else Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )

    Column(
        modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(DockSpec.ShapeRadius - DockSpec.Inset))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            // 上下各 6dp 呼吸位，76 - 7*2 - 6*2 = 50dp 给图标 + 文字
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = lerp(XC.textFaint, XC.gold, near),
            modifier = Modifier
                .size(DockSpec.IconSize)
                .graphicsLayer {
                    val s = 0.94f + near * 0.14f
                    scaleX = s
                    scaleY = s
                    translationY = -near * 1.2f
                },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tab.label,
            color = lerp(XC.textFaint, XC.gold, near),
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            fontWeight = if (near > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
    }
}

/** 极简柱状图：不为几张图引一个 1MB 的依赖。 */
@Composable
fun MiniBarChart(
    values: List<Double?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
    color: Color = XC.gold,
) {
    val maxV = (values.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            if (values.isEmpty()) return@Canvas
            val n = values.size
            val gap = size.width / n * 0.28f
            val bw = (size.width - gap * (n - 1)) / n
            for (i in values.indices) {
                val x = i * (bw + gap)
                val v = values[i]
                drawRoundRect(
                    color = XC.trackFill.copy(alpha = 0.5f),
                    topLeft = Offset(x, 0f),
                    size = Size(bw, size.height),
                    cornerRadius = CornerRadius(bw / 3f),
                )
                if (v != null && v > 0) {
                    val h = (size.height * (v / maxV)).toFloat().coerceAtLeast(bw / 3f)
                    drawRoundRect(
                        brush = if (i == highlight) {
                            Brush.verticalGradient(listOf(XC.goldHi, color))
                        } else {
                            Brush.verticalGradient(listOf(color.copy(alpha = 0.72f), color.copy(alpha = 0.42f)))
                        },
                        topLeft = Offset(x, size.height - h),
                        size = Size(bw, h),
                        cornerRadius = CornerRadius(bw / 3f),
                    )
                }
            }
            drawLine(
                color = XC.lineStrong,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, s ->
                Text(
                    s,
                    color = if (i == highlight) color else XC.textFaint,
                    fontSize = 9.5.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 环形进度：月度目标完成度。 */
@Composable
fun RingProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
    stroke: Float = 11f,
    content: @Composable () -> Unit = {},
) {
    val f = fraction.coerceIn(0f, 1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(f) {
        anim.animateTo(f, spring(stiffness = Spring.StiffnessVeryLow, dampingRatio = 0.9f))
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
            drawArc(
                color = XC.trackFill,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = s,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke),
            )
            if (anim.value > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(XC.goldHi, XC.gold, XC.goldDim, XC.goldHi)),
                    startAngle = -90f,
                    sweepAngle = 360f * anim.value,
                    useCenter = false,
                    style = s,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                )
            }
        }
        content()
    }
}
