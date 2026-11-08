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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------- 主题：与 macOS 2.0 完全对齐的四套预设 ----------------
//
// 色值直接取自 macOS AppTheme.preset（RGB 0...1 换算成 0-255），
// 所以两端同一套主题看起来是同一个颜色，不是「差不多」。
//
//  Android 这边多做了一层：由「底色 / 文本色 / 重点色」自动派生卡片、描边、
//  阴影、玻璃分层色块。这样加一套主题只要写三个色值，且色卡无极调节也能复用。

/** 一套完整的配色。所有界面颜色都必须从这里取，不允许在界面里写死色值。 */
data class Palette(
    val key: String,
    val label: String,
    val isDark: Boolean,
    /** 主题指纹：id + 自定义重点色 + 透明度。用来判断「配色是否真的变了」——
     *  不能拿整个 Palette 做相等比较，里面的 Brush 每次都是新实例，会永远不相等。 */
    val sig: String,

    val bg: Color,
    val card: Color,
    val cardHi: Color,

    /** 主强调色：金额、标题、开关、主按钮。 */
    val gold: Color,
    val goldDim: Color,
    val goldHi: Color,
    /** 强调底上的文字 / 圆钮颜色。 */
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
    val glassStrong: List<Color>,
    val glassSoft: List<Color>,
    val edge: Brush,
    val edgeInner: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,

    // —— 控件 ——
    val fill: Color,
    val trackFill: Color,
    val trackEdge: Color,
    val ghostA: Color,
    val ghostB: Color,
    val ghostEdge: Color,
    val knobOn: Color,
    val knobOff: Color,
    val knobOffDim: Color,
    val sheen: Color,

    /** Sony 30th 的四色按键点缀，其他主题为空 */
    val highlights: List<Color>,
    val blobs: List<Blob>,
    /** 背景不透明度（只有 Liquid Glass 会小于 1） */
    val alpha: Double,
)

/** 背景柔光斑：坐标与半径都是屏幕宽高的比例。 */
data class Blob(val x: Float, val y: Float, val r: Float, val color: Color)

/** 2.0 四套预设，顺序与 macOS 设置页一致。 */
enum class AppTheme(
    val id: String,
    val label: String,
    val detail: String,
    val bg: Color,
    val text: Color,
    val accent: Color,
    val secondary: Color,
    val alpha: Double,
) {
    LIQUID_GLASS(
        "liquidGlass", "Liquid Glass",
        "初始透明玻璃：跟随背景光影，背景透明度可单独调",
        Color(0xFFFFFFFF), Color(0xFF1F2126), Color(0xFFD60014), Color(0xFF3C6E9E), 0.58,
    ),
    MINT(
        "mint", "白 + 薄荷绿",
        "纯白底 + 薄荷绿强调色，清爽不刺眼",
        Color(0xFFFFFFFF), Color(0xFF14332B), Color(0xFF1AAD80), Color(0xFF2A7BD4), 1.0,
    ),
    SKY(
        "sky", "白 + 天蓝",
        "纯白底 + 天蓝强调色，冷静通透",
        Color(0xFFFFFFFF), Color(0xFF122945), Color(0xFF2985E6), Color(0xFF12A8A0), 1.0,
    ),
    SONY_30(
        "sony30", "Sony 30th",
        "初代 PlayStation 30 周年：浅灰机身 + 深灰字 + 四色点缀",
        Color(0xFFD1D4D6), Color(0xFF26292E), Color(0xFF474F5C), Color(0xFF3373D9), 1.0,
    ),
    ;

    val isGlass: Boolean get() = this == LIQUID_GLASS

    /** Sony 30th 用初代四色按键做点缀 */
    val highlights: List<Color>
        get() = when (this) {
            SONY_30 -> listOf(Color(0xFF33B359), Color(0xFFE0333D), Color(0xFF3373D9), Color(0xFFD973BF))
            else -> emptyList()
        }

    val swatches: List<Color>
        get() = when (this) {
            LIQUID_GLASS -> listOf(Color(0xFFF2F2F2), Color(0xFFB8B8B8), Color(0xFFD60014))
            MINT -> listOf(Color.White, Color(0xFF1AAD80), Color(0xFF9FEBD1))
            SKY -> listOf(Color.White, Color(0xFF2985E6), Color(0xFFA8D6FA))
            SONY_30 -> highlights
        }

    companion object {
        fun of(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: SKY
    }
}

// —— 色彩工具 ——

private fun luminance(c: Color): Double =
    0.299 * c.red + 0.587 * c.green + 0.114 * c.blue

private fun lighten(c: Color, f: Float): Color = lerp(c, Color.White, f)
private fun darken(c: Color, f: Float): Color = lerp(c, Color.Black, f)

/** 白底混入一点文本色，得到「同一色系的深一档」表面色。 */
private fun surfaceOf(bg: Color, text: Color, f: Float): Color = lerp(bg, text, f.coerceIn(0f, 0.5f))

private fun withAlpha(c: Color, a: Float): Color = c.copy(alpha = (c.alpha * a).coerceIn(0f, 1f))

/** 由预设的底色 / 文本 / 重点色派生出完整 Palette。 */
fun buildPalette(
    theme: AppTheme,
    accentRgb: String = "",
    alphaOverride: Double? = null,
    bgRgb: String = "",
    textRgb: String = "",
): Palette {
    val bg = parseRgb(bgRgb) ?: theme.bg
    val text = parseRgb(textRgb) ?: theme.text
    val accent = parseRgb(accentRgb) ?: theme.accent
    val dark = luminance(bg) < 0.5
    val alpha = (alphaOverride ?: theme.alpha).coerceIn(0.15, 1.0)

    val cardBase = if (dark) lighten(bg, 0.10f) else lerp(bg, Color.White, 0.55f)
    val onAccent = if (luminance(accent) > 0.62) Color(0xFF1F2126) else Color.White

    val blobs = when (theme) {
        AppTheme.LIQUID_GLASS -> listOf(
            Blob(0.88f, -0.02f, 1.0f, Color(0x2E9ECFFF)),
            Blob(0.04f, 0.20f, 0.85f, Color(0x1FD60014)),
            Blob(-0.04f, 0.62f, 0.95f, Color(0x1F7FC4FF)),
            Blob(0.94f, 0.88f, 0.9f, Color(0x1FA8B8D0)),
            Blob(0.5f, 0.42f, 1.1f, Color(0x26FFFFFF)),
        )
        AppTheme.MINT -> listOf(
            Blob(0.9f, -0.04f, 1.0f, Color(0x1A1AAD80)),
            Blob(0.02f, 0.18f, 0.8f, Color(0x1434E0A8)),
            Blob(-0.06f, 0.6f, 0.95f, Color(0x12B2F0D4)),
            Blob(0.94f, 0.88f, 0.88f, Color(0x122A7BD4)),
            Blob(0.5f, 0.42f, 1.1f, Color(0x14FFFFFF)),
        )
        AppTheme.SKY -> listOf(
            Blob(0.9f, -0.04f, 1.0f, Color(0x262985E6)),
            Blob(0.02f, 0.18f, 0.82f, Color(0x1A5FB4F0)),
            Blob(-0.06f, 0.6f, 0.95f, Color(0x1412A8A0)),
            Blob(0.94f, 0.88f, 0.88f, Color(0x1490C4F4)),
            Blob(0.5f, 0.42f, 1.1f, Color(0x1AFFFFFF)),
        )
        AppTheme.SONY_30 -> listOf(
            Blob(0.9f, -0.02f, 0.9f, Color(0x223373D9)),
            Blob(0.04f, 0.22f, 0.8f, Color(0x1AE0333D)),
            Blob(-0.04f, 0.66f, 0.9f, Color(0x1A33B359)),
            Blob(0.92f, 0.9f, 0.85f, Color(0x1AD973BF)),
            Blob(0.5f, 0.46f, 1.1f, Color(0x26FFFFFF)),
        )
    }

    return Palette(
        key = theme.id,
        label = theme.label,
        isDark = dark,
        sig = "${theme.id}|$accentRgb|$bgRgb|$textRgb|${"%.3f".format(alpha)}",
        bg = bg,
        card = cardBase,
        cardHi = if (dark) lighten(bg, 0.16f) else lerp(bg, Color.White, 0.75f),
        gold = accent,
        goldDim = darken(accent, 0.72f),
        goldHi = lighten(accent, if (luminance(accent) > 0.6) 0.16f else 0.30f),
        onGold = onAccent,
        text = text,
        textDim = withAlpha(text, 0.70f),
        textFaint = withAlpha(text, 0.54f),
        orange = if (theme == AppTheme.SONY_30) Color(0xFFE0333D) else Color(0xFFC2600F),
        teal = theme.secondary,
        danger = Color(0xFFC93A38),
        line = withAlpha(text, 0.08f),
        lineStrong = withAlpha(text, 0.16f),
        glassStrong = if (dark) {
            listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF), Color(0x0FFFFFFF))
        } else {
            listOf(Color(0xFFFFFFFF), Color(0xFAFFFFFF), Color(0xF4FFFFFF))
        },
        glassSoft = if (dark) {
            listOf(Color(0x26FFFFFF), Color(0x12FFFFFF))
        } else {
            listOf(Color(0xF7FFFFFF), Color(0xEDFFFFFF))
        },
        edge = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), withAlpha(accent, 0.35f), withAlpha(text, 0.10f))),
        edgeInner = if (dark) Color(0x33FFFFFF) else Color(0x14FFFFFF),
        shadowAmbient = withAlpha(text, 0.14f),
        shadowSpot = withAlpha(accent, 0.22f),
        fill = withAlpha(text, 0.055f),
        trackFill = withAlpha(text, 0.09f),
        trackEdge = withAlpha(text, 0.04f),
        ghostA = withAlpha(text, 0.08f),
        ghostB = withAlpha(text, 0.03f),
        ghostEdge = withAlpha(text, 0.13f),
        knobOn = Color.White,
        knobOff = Color.White,
        knobOffDim = Color(0x66FFFFFF),
        sheen = Color(0x8CFFFFFF),
        highlights = theme.highlights,
        blobs = blobs,
        alpha = alpha,
    )
}

private fun parseRgb(raw: String): Color? {
    if (raw.isBlank()) return null
    val p = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
    if (p.size != 3) return null
    return Color(
        red = (p[0] / 255f).coerceIn(0f, 1f),
        green = (p[1] / 255f).coerceIn(0f, 1f),
        blue = (p[2] / 255f).coerceIn(0f, 1f),
    )
}

fun rgbString(c: Color): String =
    "${(c.red * 255).toInt()},${(c.green * 255).toInt()},${(c.blue * 255).toInt()}"

/**
 * 全局配色入口。
 *
 * 每个属性都是 getter，内部读的是 Compose 的 state，所以任何一处 `XC.gold`
 * 都会被自动登记成依赖 —— 换主题时整个界面自动重组，不需要改调用点。
 */
object XC {
    private val cur = mutableStateOf(buildPalette(AppTheme.SKY))

    val palette: Palette get() = cur.value
    val isDark: Boolean get() = cur.value.isDark
    val theme: AppTheme get() = AppTheme.of(cur.value.key)
    val highlights: List<Color> get() = cur.value.highlights

    fun use(p: Palette) {
        if (cur.value.sig != p.sig) cur.value = p
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

/**
 * 玻璃分层（对应 macOS Glass.level）：
 *  1 分区容器（最浅）/ 2 分组卡 / 3 分区标题（最深）/ 4 组内行。
 * 实色主题走「底色混文本色」，玻璃主题走半透明白雾。
 */
object Glass {
    fun level(n: Int): Color {
        val p = XC.palette
        return if (p.key == AppTheme.LIQUID_GLASS.id) {
            val a = listOf(0f, 0.05f, 0.10f, 0.18f, 0.05f)[n.coerceIn(1, 4)]
            if (p.isDark) Color.White.copy(alpha = 0.05f + a) else Color.White.copy(alpha = 0.28f + a)
        } else {
            val f = listOf(0f, 0.035f, 0.075f, 0.17f, 0.055f)[n.coerceIn(1, 4)]
            lerp(p.bg, p.text, f.coerceIn(0f, 0.5f))
        }
    }

    fun fill(a: Double): Color =
        if (XC.palette.key == AppTheme.LIQUID_GLASS.id) {
            Color.White.copy(alpha = (0.10 + a * 0.45).toFloat().coerceAtMost(0.9f))
        } else {
            lerp(XC.bg, XC.text, (0.035 + a * 0.30).toFloat().coerceAtMost(0.34f))
        }

    fun line(a: Double): Color =
        if (XC.palette.key == AppTheme.LIQUID_GLASS.id) {
            Color.White.copy(alpha = (0.10 + a * 0.25).toFloat().coerceAtMost(0.5f))
        } else {
            XC.text.copy(alpha = (0.06 + a * 0.24).toFloat().coerceAtMost(0.42f))
        }
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
                elevation = if (strong) 14.dp else 7.dp,
                shape = shape,
                ambientColor = XC.shadowAmbient,
                spotColor = XC.shadowSpot,
            )
            .clip(shape)
            .background(Brush.verticalGradient(if (strong) XC.glassStrong else XC.glassSoft))
            .border(1.dp, XC.edge, shape)
            .padding(0.6.dp)
            .border(
                0.6.dp,
                Brush.verticalGradient(listOf(XC.edgeInner, Color.Transparent)),
                shape,
            ),
        content = content,
    )
}

/** 背景：底色 + 若干柔光斑，让上层玻璃"透"出颜色。四套主题各有自己的光斑布局。 */
@Composable
fun LiquidBackground(content: @Composable BoxScope.() -> Unit) {
    val p = XC.palette
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (b in p.blobs) {
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

/** 主色渐变：进度条、主按钮统一用它，保证换主题一致。 */
@Composable
fun accentBrush(): Brush = Brush.horizontalGradient(
    listOf(XC.goldHi, XC.gold, XC.goldDim)
)

/** Sony 主题的四色点缀；其他主题回落到主色。 */
fun accentOrHighlights(index: Int): Color =
    XC.highlights.getOrNull(index % (XC.highlights.size.coerceAtLeast(1))) ?: XC.gold

/** 主题指纹：给设置页做「是否已改动」提示用 */
val currentThemeSig: String get() = XC.palette.sig
