package com.haofan.xiaoxinxin.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

// ---------------- 液态玻璃交互 ----------------
//
// 之前的可点元素只有 Material 默认水波纹（ripple），跟「液态玻璃」没关系：
// ripple 是从触点扩散的实心圆，看起来像纸上的墨点，不是玻璃。
//
// 这里做的是真正的玻璃按压：
//   ① 按下时整块「被压进去」—— 微缩 + 弹簧回弹（松手会轻微过冲，像液体回弹）；
//   ② 触点处出现一团折射光晕，并且**跟着手指滑动**，滑到哪亮到哪，这是「液态」的核心；
//   ③ 顶部一道弧光 + 边缘一圈内高光，交代玻璃的厚度与被挤压时的折射；
//   ④ 光晕颜色取主题强调色，不是纯白，换主题后按压反馈跟着变色。
//
// 实现上两个关键点，改的时候别踩：
//   · 按压状态用 clickable 自己的 interactionSource 收集，不自己消费手势，
//     否则设置页列表会被按钮「吃掉」滚动；
//   · 触点位置用 PointerEventPass.Initial 只读不消费，同样是为了不抢滚动。

/** 按压反馈的两种量级。 */
enum class PressWeight {
    /** 实体按钮（主按钮 / chip / 色卡 / 打卡键）：整块缩放 + 强折射。 */
    Solid,

    /** 整行可点（设置行 / 列表项 / 折叠头）：不缩放，只做微弱内高光，避免整行跳动。 */
    Row,
}

/**
 * 液态玻璃按压反馈，直接挂在可点元素上替代 [clickable]。
 *
 * @param shape 元素形状（决定裁剪与边缘高光的轮廓）
 * @param weight 反馈量级，见 [PressWeight]
 * @param scaleDown 按下时缩到的比例，仅 [PressWeight.Solid] 生效
 * @param tint 折射光晕的色调，默认跟主题强调色
 */
@Composable
fun Modifier.liquidPress(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(999.dp),
    weight: PressWeight = PressWeight.Solid,
    scaleDown: Float = 0.955f,
    tint: Color = XC.gold,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // 按下快、回弹慢且带一点过冲 —— 这一下「弹」就是液态感的来源。
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else 0.42f,
            stiffness = if (pressed) Spring.StiffnessHigh else Spring.StiffnessMediumLow,
        ),
        label = "liquid-press",
    )

    // 触点位置：手指在元素上滑动时会持续更新，光晕跟着走。
    var touch by remember { mutableStateOf(Offset.Unspecified) }

    val solid = weight == PressWeight.Solid
    val gain = if (solid) 1f else 0.42f

    return this
        .graphicsLayer {
            if (solid) {
                val s = 1f - (1f - scaleDown) * press
                scaleX = s
                scaleY = s
            }
        }
        .clip(shape)
        // 只读触点，不消费事件：父级滚动、clickable 全都照常工作。
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: continue
                    touch = if (c.pressed) c.position else Offset.Unspecified
                }
            }
        }
        // 光晕画在背景之上、文字之下，字始终清晰。
        .drawBehind {
            val p = press.coerceIn(0f, 1.15f) * gain
            if (p <= 0.01f) return@drawBehind
            val c = touch.takeIf { it.isSpecified } ?: Offset(size.width / 2f, size.height / 2f)
            val far = size.maxDimension * 1.15f

            // ① 触点折射：白色光晕，随手指滑动
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.44f * p),
                    0.42f to Color.White.copy(alpha = 0.16f * p),
                    1f to Color.Transparent,
                    center = c,
                    radius = far,
                ),
                radius = far,
                center = c,
            )
            // ② 主题色折射：让反馈带自己的色调，不是纯白糊成一片
            drawCircle(
                brush = Brush.radialGradient(
                    0f to tint.copy(alpha = 0.20f * p),
                    1f to Color.Transparent,
                    center = c,
                    radius = far * 0.62f,
                ),
                radius = far * 0.62f,
                center = c,
            )
            // ③ 顶部弧光：玻璃厚度，按下时更明显
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.30f * p),
                    1f to Color.Transparent,
                    endY = size.height * 0.72f,
                ),
            )
        }
        // 边缘内高光：玻璃被挤压时边缘折射变亮，这一圈让形状「立」起来。
        .drawWithContent {
            drawContent()
            val p = press.coerceIn(0f, 1.15f) * (if (solid) 1f else 0.7f)
            if (p <= 0.01f) return@drawWithContent
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                color = Color.White.copy(alpha = 0.55f * p),
                style = Stroke(width = 1.6.dp.toPx()),
            )
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * 液态玻璃滑块：拖动时 thumb 是一颗玻璃球（顶部高光 + 主题色描边 + 光晕），
 * 已填充的液面带一道白高光，像液面反光。
 *
 * 手势说明：只做「拖动」和「点轨道跳转」，两者都不消费 down，
 * 所以从滑块上竖着划过时页面照常滚动，不会把列表卡住。
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val frac = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    // 拖动时球体微微胀大 + 光晕亮起，松手弹性收回
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "slider-lift",
    )

    fun seek(x: Float) {
        val f = (x / widthPx).coerceIn(0f, 1f)
        onValueChange(valueRange.start + f * span)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    seek(change.position.x)
                    change.consume()
                }
            }
            .pointerInput(valueRange) {
                detectTapGestures { o -> seek(o.x) }
            },
    ) {
        val cy = size.height / 2f
        val trackH = 6.dp.toPx()
        val rad = trackH / 2f
        val fillW = (size.width * frac).coerceIn(trackH, size.width)
        val thumbR = 10.dp.toPx() * (1f + 0.16f * lift)
        val center = Offset(fillW, cy)

        // 轨道底
        drawRoundRect(
            color = XC.trackFill,
            topLeft = Offset(0f, cy - rad),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(rad),
        )
        // 液面（已填充段）
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(XC.goldHi, XC.gold, XC.goldDim),
                endX = fillW,
            ),
            topLeft = Offset(0f, cy - rad),
            size = Size(fillW, trackH),
            cornerRadius = CornerRadius(rad),
        )
        // 液面反光：上半条淡淡的白
        drawRoundRect(
            color = Color.White.copy(alpha = 0.30f),
            topLeft = Offset(0f, cy - rad),
            size = Size(fillW, trackH * 0.44f),
            cornerRadius = CornerRadius(rad),
        )
        // 拖动光晕
        if (lift > 0.02f) {
            val glowR = thumbR * 3.2f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to XC.gold.copy(alpha = 0.30f * lift),
                    1f to Color.Transparent,
                    center = center,
                    radius = glowR,
                ),
                radius = glowR,
                center = center,
            )
        }
        // 玻璃球：主体 + 顶部高光 + 主题色描边
        drawCircle(
            brush = Brush.verticalGradient(
                listOf(Color.White, XC.goldHi, XC.gold),
                startY = cy - thumbR,
                endY = cy + thumbR,
            ),
            radius = thumbR,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.62f),
            radius = thumbR * 0.52f,
            center = Offset(center.x, center.y - thumbR * 0.34f),
        )
        drawCircle(
            color = XC.gold.copy(alpha = 0.9f),
            radius = thumbR,
            center = center,
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }
}
