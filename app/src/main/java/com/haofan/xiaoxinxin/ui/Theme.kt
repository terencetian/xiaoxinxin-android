package com.haofan.xiaoxinxin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 与 macOS 版落地页同一套配色：深夜蓝 + 金色。 */
object XC {
    val bg = Color(0xFF070E1B)
    val bgSoft = Color(0xFF0C1728)
    val card = Color(0xFF14213A)
    val cardHi = Color(0xFF1D2C4A)
    val gold = Color(0xFFFFD257)
    val goldDim = Color(0xFFB08A34)
    val text = Color(0xFFF4F7FC)
    val textDim = Color(0xB3F4F7FC)
    val textFaint = Color(0x7AF4F7FC)
    val orange = Color(0xFFFF9F27)
    val teal = Color(0xFF5DCAA5)
    val line = Color(0x1FFFFFFF)
    val lineStrong = Color(0x33FFFFFF)

    // —— Liquid Glass 材质 ——
    val glassFill = Color(0x14FFFFFF)      // 8% 白，玻璃体
    val glassFillDeep = Color(0x0AFFFFFF)  // 4% 白，次级玻璃
    val glassEdgeTop = Color(0x59FFFFFF)   // 顶部高光边
    val glassEdgeBot = Color(0x0DFFFFFF)   // 底部收边
}

val NumFont: FontFamily = FontFamily.Monospace

val MoneyBig = 48.sp
val LabelSm = 11.sp
val W600 = FontWeight.SemiBold
val W500 = FontWeight.Medium

private val scheme = darkColorScheme(
    primary = XC.gold,
    onPrimary = Color(0xFF1A1200),
    background = XC.bg,
    onBackground = XC.text,
    surface = XC.card,
    onSurface = XC.text,
    surfaceVariant = XC.cardHi,
    onSurfaceVariant = XC.textDim,
)

@Composable
fun XiaoxinxinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 玻璃卡片：半透明填充 + 上下不等的高光边 + 柔和投影，模拟玻璃的厚度与悬浮。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Int = 24,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier
            .shadow(
                elevation = if (strong) 18.dp else 10.dp,
                shape = shape,
                ambientColor = Color(0x66000000),
                spotColor = Color(0x88000000),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (strong) listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF), Color(0x0FFFFFFF))
                    else listOf(Color(0x26FFFFFF), Color(0x12FFFFFF))
                )
            )
            // 外圈高光边
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color(0x8CFFFFFF), Color(0x1FFFFFFF), Color(0x0AFFFFFF))),
                shape,
            )
            // 内侧一层更细的亮边，做出玻璃折射的双层感
            .padding(0.6.dp)
            .border(
                0.6.dp,
                Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color.Transparent)),
                shape,
            ),
        content = content,
    )
}

/** 背景：深蓝底 + 两团柔光斑，让上层玻璃"透"出颜色。 */
@Composable
fun LiquidBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(XC.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 右上金色主光斑
            val goldCenter = Offset(w * 0.86f, h * 0.10f)
            val goldRadius = w * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(XC.gold.copy(alpha = 0.28f), XC.gold.copy(alpha = 0.08f), Color.Transparent),
                    center = goldCenter,
                    radius = goldRadius,
                ),
                radius = goldRadius,
                center = goldCenter,
            )

            // 主卡背后的暖色，让玻璃"透"出颜色
            val cardGlow = Offset(w * 0.22f, h * 0.20f)
            val cardGlowR = w * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFF9F27).copy(alpha = 0.16f), Color.Transparent),
                    center = cardGlow,
                    radius = cardGlowR,
                ),
                radius = cardGlowR,
                center = cardGlow,
            )

            // 左下青绿光斑
            val tealCenter = Offset(w * 0.02f, h * 0.62f)
            val tealRadius = w * 0.90f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(XC.teal.copy(alpha = 0.16f), Color.Transparent),
                    center = tealCenter,
                    radius = tealRadius,
                ),
                radius = tealRadius,
                center = tealCenter,
            )

            // 右下冷蓝，压住整体亮度并补层次
            val blueCenter = Offset(w * 0.95f, h * 0.85f)
            val blueRadius = w * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF2E6BD6).copy(alpha = 0.26f), Color.Transparent),
                    center = blueCenter,
                    radius = blueRadius,
                ),
                radius = blueRadius,
                center = blueCenter,
            )

            // 中部底色
            val midCenter = Offset(w * 0.5f, h * 0.45f)
            val midRadius = w * 1.15f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF1B3560).copy(alpha = 0.35f), Color.Transparent),
                    center = midCenter,
                    radius = midRadius,
                ),
                radius = midRadius,
                center = midCenter,
            )
        }
        content()
    }
}
