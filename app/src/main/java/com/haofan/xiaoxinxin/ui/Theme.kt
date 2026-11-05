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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------- 主题 ----------------

/** 一套完整的配色。所有界面颜色都必须从这里取，不允许在界面里写死色值。 */
data class Palette(
    val key: String,
    val label: String,
    val isDark: Boolean,

    val bg: Color,
    val card: Color,
    val cardHi: Color,

    /** 主强调色：金额、标题、开关、主按钮。 */
    val gold: Color,
    val goldDim: Color,
    val goldHi: Color,
    /** 金底上的文字 / 圆钮颜色。 */
    val onGold: Color,

    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val orange: Color,
    val teal: Color,
    val danger: Color,
    val line: Color,
    val lineStrong: Color,

    // —— 玻璃材质 ——
    /** 主卡片 / 普通卡片的垂直渐变填充（自上而下）。 */
    val glassStrong: List<Color>,
    val glassSoft: List<Color>,
    /** 卡片外圈高光边。 */
    val edge: Brush,
    val edgeInner: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,

    // —— 控件 ——
    /** 输入框 / 小胶囊的浅填充。 */
    val fill: Color,
    /** 进度条轨道。 */
    val trackFill: Color,
    val trackEdge: Color,
    /** 玻璃按钮的常态填充与描边。 */
    val ghostA: Color,
    val ghostB: Color,
    val ghostEdge: Color,
    /** 开关圆钮。 */
    val knobOn: Color,
    val knobOff: Color,
    val knobOffDim: Color,
    /** 按钮顶部的白色高光 / 进度条的液体反光。 */
    val sheen: Color,

    val blobs: List<Blob>,
)

/** 背景柔光斑：坐标与半径都是屏幕宽高的比例。 */
data class Blob(val x: Float, val y: Float, val r: Float, val color: Color)

private fun blob(x: Float, y: Float, r: Float, argb: Long) = Blob(x, y, r, Color(argb))

/** 米白：暖白纸感 + 淡金晕染，默认主题。 */
val CreamPalette = Palette(
    key = "cream",
    label = "米白",
    isDark = false,
    bg = Color(0xFFF7F3EA),
    card = Color(0xFFFFFFFF),
    cardHi = Color(0xFFFFFCF5),
    gold = Color(0xFFA8701A),
    goldDim = Color(0xFF7C5010),
    goldHi = Color(0xFFC99239),
    onGold = Color(0xFFFFFBF2),
    text = Color(0xFF241E16),
    textDim = Color(0xB3241E16),
    textFaint = Color(0x8A241E16),
    orange = Color(0xFFC2600F),
    teal = Color(0xFF1C7F68),
    danger = Color(0xFFC93A38),
    line = Color(0x0F241E16),
    lineStrong = Color(0x1F241E16),
    glassStrong = listOf(Color(0xFFFFFFFF), Color(0xFAFFFFFF), Color(0xF2FFFFFF)),
    glassSoft = listOf(Color(0xF7FFFFFF), Color(0xECFFFFFF)),
    edge = Brush.verticalGradient(
        listOf(Color(0xFFFFFFFF), Color(0x1F8A6B2A), Color(0x0D241E16))
    ),
    edgeInner = Color(0x14FFFFFF),
    shadowAmbient = Color(0x1F9A7B44),
    shadowSpot = Color(0x2E8A6B2A),
    fill = Color(0x0D241E16),
    trackFill = Color(0x14241E16),
    trackEdge = Color(0x0A241E16),
    ghostA = Color(0x14241E16),
    ghostB = Color(0x08241E16),
    ghostEdge = Color(0x1F241E16),
    knobOn = Color(0xFFFFFFFF),
    knobOff = Color(0xFFFFFFFF),
    knobOffDim = Color(0x66FFFFFF),
    sheen = Color(0x8CFFFFFF),
    blobs = listOf(
        blob(0.90f, -0.04f, 1.00f, 0x1CE8C878L),
        blob(0.02f, 0.18f, 0.80f, 0x14F0B87AL),
        blob(-0.06f, 0.60f, 0.95f, 0x187FD3B4L),
        blob(0.94f, 0.88f, 0.88f, 0x148FB6E8L),
        blob(0.50f, 0.42f, 1.10f, 0x14FFFFFFL),
    ),
)

/** 彩虹：粉白打底，五色柔光叠成彩虹晕，边框走扫掠渐变。 */
val RainbowPalette = Palette(
    key = "rainbow",
    label = "彩虹",
    isDark = false,
    bg = Color(0xFFFDF7FA),
    card = Color(0xFFFFFFFF),
    cardHi = Color(0xFFFFF5FB),
    gold = Color(0xFFD81B60),
    goldDim = Color(0xFF9C1246),
    goldHi = Color(0xFFF0629A),
    onGold = Color(0xFFFFFFFF),
    text = Color(0xFF2A1730),
    textDim = Color(0xB32A1730),
    textFaint = Color(0x8A2A1730),
    orange = Color(0xFFEF6C10),
    teal = Color(0xFF0E9F8E),
    danger = Color(0xFFD32F2F),
    line = Color(0x142A1730),
    lineStrong = Color(0x242A1730),
    glassStrong = listOf(Color(0xFFFFFFFF), Color(0xFAFFFFFF), Color(0xF0FFF2FA)),
    glassSoft = listOf(Color(0xF7FFFFFF), Color(0xEAFFFAFD)),
    edge = Brush.sweepGradient(
        listOf(
            Color(0x99FF4FA3), Color(0x99FF9A3C), Color(0x99FFD84A),
            Color(0x9934C7B4), Color(0x997B5CFF), Color(0x99FF4FA3),
        )
    ),
    edgeInner = Color(0x1AFFFFFF),
    shadowAmbient = Color(0x24C2559A),
    shadowSpot = Color(0x2E8A4BD0),
    fill = Color(0x0D2A1730),
    trackFill = Color(0x142A1730),
    trackEdge = Color(0x0A2A1730),
    ghostA = Color(0x142A1730),
    ghostB = Color(0x082A1730),
    ghostEdge = Color(0x242A1730),
    knobOn = Color(0xFFFFFFFF),
    knobOff = Color(0xFFFFFFFF),
    knobOffDim = Color(0x66FFFFFF),
    sheen = Color(0x8CFFFFFF),
    blobs = listOf(
        blob(0.92f, 0.02f, 1.10f, 0x33FF4FA3L),
        blob(0.02f, 0.14f, 0.90f, 0x30FF9A3CL),
        blob(0.10f, 0.52f, 1.00f, 0x2AFFD84AL),
        blob(0.90f, 0.62f, 0.95f, 0x2E34C7B4L),
        blob(0.42f, 0.96f, 1.05f, 0x337B5CFFL),
        blob(0.55f, 0.34f, 0.80f, 0x1AFFFFFFL),
    ),
)

/** 深夜蓝：夜间 / 暗色模式的经典配色，也是 1.9.4 之前唯一的一套。 */
val NightPalette = Palette(
    key = "night",
    label = "暗夜",
    isDark = true,
    bg = Color(0xFF070E1B),
    card = Color(0xFF14213A),
    cardHi = Color(0xFF1D2C4A),
    gold = Color(0xFFFFD257),
    goldDim = Color(0xFFB08A34),
    goldHi = Color(0xFFFFE9A8),
    onGold = Color(0xFF241A00),
    text = Color(0xFFF4F7FC),
    textDim = Color(0xB3F4F7FC),
    textFaint = Color(0x7AF4F7FC),
    orange = Color(0xFFFF9F27),
    teal = Color(0xFF5DCAA5),
    danger = Color(0xFFE24B4A),
    line = Color(0x1FFFFFFF),
    lineStrong = Color(0x33FFFFFF),
    glassStrong = listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF), Color(0x0FFFFFFF)),
    glassSoft = listOf(Color(0x26FFFFFF), Color(0x12FFFFFF)),
    edge = Brush.verticalGradient(
        listOf(Color(0x8CFFFFFF), Color(0x1FFFFFFF), Color(0x0AFFFFFF))
    ),
    edgeInner = Color(0x33FFFFFF),
    shadowAmbient = Color(0x66000000),
    shadowSpot = Color(0x88000000),
    fill = Color(0x14FFFFFF),
    trackFill = Color(0x1FFFFFFF),
    trackEdge = Color(0x14FFFFFF),
    ghostA = Color(0x1AFFFFFF),
    ghostB = Color(0x0AFFFFFF),
    ghostEdge = Color(0x4DFFFFFF),
    knobOn = Color(0xFF241A00),
    knobOff = Color(0xE6F4F7FA),
    knobOffDim = Color(0x66F4F7FA),
    sheen = Color(0x66FFFFFF),
    blobs = listOf(
        blob(0.92f, 0.02f, 0.82f, 0x33FFD257L),
        blob(0.18f, 0.24f, 0.68f, 0x1FFF9F27L),
        blob(0.00f, 0.64f, 0.88f, 0x225DCAA5L),
        blob(0.97f, 0.86f, 0.85f, 0x3D2E6BD6L),
        blob(0.50f, 0.46f, 1.15f, 0x4D1B3560L),
    ),
)

/** 设置里的三个选项。SYSTEM 是策略，不是配色：亮色跟米白，暗色跟暗夜。 */
enum class ThemeMode(val key: String, val label: String, val desc: String) {
    CREAM("cream", "米白", "暖白纸感，白天看着最舒服"),
    RAINBOW("rainbow", "彩虹", "粉白底 + 五色晕染，边框走彩虹"),
    SYSTEM("system", "跟随系统", "白天米白、夜间暗夜，跟着系统开关切"),
    ;

    companion object {
        fun of(key: String): ThemeMode = entries.firstOrNull { it.key == key } ?: CREAM
    }
}

fun resolvePalette(mode: ThemeMode, systemDark: Boolean): Palette = when (mode) {
    ThemeMode.CREAM -> CreamPalette
    ThemeMode.RAINBOW -> RainbowPalette
    ThemeMode.SYSTEM -> if (systemDark) NightPalette else CreamPalette
}

/**
 * 全局配色入口。
 *
 * 每个属性都是 getter，内部读的是 Compose 的 state，所以任何一处 `XC.gold`
 * 都会被自动登记成依赖 —— 换主题时整个界面自动重组，不需要改调用点。
 */
object XC {
    private val cur = mutableStateOf(CreamPalette)

    val palette: Palette get() = cur.value
    val isDark: Boolean get() = cur.value.isDark

    fun use(p: Palette) {
        if (cur.value.key != p.key) cur.value = p
    }

    val bg: Color get() = cur.value.bg
    val card: Color get() = cur.value.card
    val cardHi: Color get() = cur.value.cardHi
    val gold: Color get() = cur.value.gold
    val goldDim: Color get() = cur.value.goldDim
    val goldHi: Color get() = cur.value.goldHi
    val onGold: Color get() = cur.value.onGold
    val text: Color get() = cur.value.text
    val textDim: Color get() = cur.value.textDim
    val textFaint: Color get() = cur.value.textFaint
    val orange: Color get() = cur.value.orange
    val teal: Color get() = cur.value.teal
    val danger: Color get() = cur.value.danger
    val line: Color get() = cur.value.line
    val lineStrong: Color get() = cur.value.lineStrong
    val glassStrong: List<Color> get() = cur.value.glassStrong
    val glassSoft: List<Color> get() = cur.value.glassSoft
    val edge: Brush get() = cur.value.edge
    val edgeInner: Color get() = cur.value.edgeInner
    val shadowAmbient: Color get() = cur.value.shadowAmbient
    val shadowSpot: Color get() = cur.value.shadowSpot
    val fill: Color get() = cur.value.fill
    val trackFill: Color get() = cur.value.trackFill
    val trackEdge: Color get() = cur.value.trackEdge
    val ghostA: Color get() = cur.value.ghostA
    val ghostB: Color get() = cur.value.ghostB
    val ghostEdge: Color get() = cur.value.ghostEdge
    val knobOn: Color get() = cur.value.knobOn
    val knobOff: Color get() = cur.value.knobOff
    val knobOffDim: Color get() = cur.value.knobOffDim
    val sheen: Color get() = cur.value.sheen
}

/** 进度条的三档样式。金色是默认，纯色跟随主题主色，彩虹走五色渐变。 */
enum class BarStyle(val key: String, val label: String) {
    GOLD("gold", "金色"),
    SOLID("solid", "纯色"),
    RAINBOW("rainbow", "彩虹"),
    ;

    companion object {
        fun of(key: String): BarStyle = entries.firstOrNull { it.key == key } ?: GOLD
    }
}

/** 黄金三色：不跟主题走，任何配色下都是同一条真正的金色进度条。 */
private val GoldLiquid = listOf(Color(0xFFDFA02A), Color(0xFFFFD257), Color(0xFFFFE9A8))

/** 彩虹液面：粉 → 橙 → 黄 → 青 → 紫。 */
private val RainbowLiquid = listOf(
    Color(0xFFFF4FA3), Color(0xFFFF9A3C), Color(0xFFFFD84A),
    Color(0xFF34C7B4), Color(0xFF7B5CFF),
)

@Composable
fun barBrush(style: BarStyle): Brush = when (style) {
    BarStyle.GOLD -> Brush.horizontalGradient(GoldLiquid)
    BarStyle.SOLID -> SolidColor(XC.gold)
    BarStyle.RAINBOW -> Brush.horizontalGradient(RainbowLiquid)
}

val NumFont: FontFamily = FontFamily.Monospace

val MoneyBig = 48.sp
val LabelSm = 11.sp
val W600 = FontWeight.SemiBold
val W500 = FontWeight.Medium

@Composable
fun XiaoxinxinTheme(content: @Composable () -> Unit) {
    val p = XC.palette
    val scheme = if (p.isDark) {
        darkColorScheme(
            primary = p.gold,
            onPrimary = p.onGold,
            background = p.bg,
            onBackground = p.text,
            surface = p.card,
            onSurface = p.text,
            surfaceVariant = p.cardHi,
            onSurfaceVariant = p.textDim,
        )
    } else {
        lightColorScheme(
            primary = p.gold,
            onPrimary = p.onGold,
            background = p.bg,
            onBackground = p.text,
            surface = p.card,
            onSurface = p.text,
            surfaceVariant = p.cardHi,
            onSurfaceVariant = p.textDim,
        )
    }
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
                elevation = if (strong) 16.dp else 8.dp,
                shape = shape,
                ambientColor = XC.shadowAmbient,
                spotColor = XC.shadowSpot,
            )
            .clip(shape)
            .background(Brush.verticalGradient(if (strong) XC.glassStrong else XC.glassSoft))
            // 外圈高光边
            .border(1.dp, XC.edge, shape)
            // 内侧一层更细的亮边，做出玻璃折射的双层感
            .padding(0.6.dp)
            .border(
                0.6.dp,
                Brush.verticalGradient(listOf(XC.edgeInner, Color.Transparent)),
                shape,
            ),
        content = content,
    )
}

/** 背景：底色 + 若干柔光斑，让上层玻璃"透"出颜色。三套主题各有自己的光斑布局。 */
@Composable
fun LiquidBackground(content: @Composable BoxScope.() -> Unit) {
    val blobs = XC.palette.blobs
    Box(Modifier.fillMaxSize().background(XC.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (b in blobs) {
                val c = Offset(w * b.x, h * b.y)
                val r = w * b.r
                if (r <= 0f) continue
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(b.color, b.color.copy(alpha = b.color.alpha * 0.35f), Color.Transparent),
                        center = c,
                        radius = r,
                    ),
                    radius = r,
                    center = c,
                )
            }
        }
        content()
    }
}
