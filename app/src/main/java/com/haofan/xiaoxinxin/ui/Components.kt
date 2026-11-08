package com.haofan.xiaoxinxin.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 2.0 共享控件。原则：颜色一律走 XC / Glass，尺寸集中在文件顶部，
// 三个二级页 + 首页 + 设置页复用同一套，不再各写一份。

object Dimen {
    val PageH = 16.dp
    val CardRadius = 22
    val RowRadius = 12
    val Gap = 12.dp
    val SmallGap = 8.dp
}

// ---------------- 文本 / 标题 ----------------

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = XC.textFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
        if (!trailing.isNullOrEmpty()) {
            Spacer(Modifier.weight(1f))
            Text(trailing, color = XC.textFaint, fontSize = 10.sp, maxLines = 1)
        }
    }
}

/** 二级页顶栏：返回 + 标题 + 可选右侧操作 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(XC.ghostA, XC.ghostB)))
                .border(0.8.dp, XC.ghostEdge, CircleShape)
                .liquidPress(onClick = onBack, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = XC.textDim,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = XC.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = -0.2.sp)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = XC.textFaint, fontSize = 10.5.sp, maxLines = 1)
            }
        }
        trailing?.let {
            Row(verticalAlignment = Alignment.CenterVertically, content = it)
        }
    }
}

/** 圆形玻璃小按钮 */
@Composable
fun GlassRoundButton(
    active: Boolean = false,
    size: Int = 32,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (active) Brush.linearGradient(
                    listOf(XC.gold.copy(alpha = 0.20f), XC.gold.copy(alpha = 0.08f))
                ) else Brush.verticalGradient(listOf(XC.ghostA, XC.ghostB))
            )
            .border(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(if (active) XC.gold.copy(alpha = 0.65f) else XC.ghostEdge, Color.Transparent)
                ),
                CircleShape,
            )
            .liquidPress(onClick = onClick, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------- 设置控件 ----------------

/** 可折叠设置分组（2.0 的 19 个分组都用它） */
@Composable
fun SettingGroup(
    title: String,
    summary: String? = null,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), radius = Dimen.CardRadius) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidPress(
                        onClick = onToggle,
                        shape = RoundedCornerShape(Dimen.RowRadius.dp),
                        weight = PressWeight.Row,
                    )
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = XC.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    if (!summary.isNullOrEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(summary, color = XC.textFaint, fontSize = 10.5.sp, maxLines = 1)
                    }
                }
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (open) "折叠" else "展开",
                    tint = XC.textFaint,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (open) {
                Column(Modifier.padding(start = 15.dp, end = 15.dp, bottom = 15.dp)) { content() }
            }
        }
    }
}

/** 输入框行：上标签、下输入 */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String = "",
    kb: KeyboardType = KeyboardType.Text,
    placeholder: String = "—",
    enabled: Boolean = true,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimen.RowRadius.dp))
            .background(XC.fill)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, color = XC.textFaint, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = { if (enabled) onChange(it) },
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(
                    color = if (enabled) XC.text else XC.textFaint,
                    fontSize = 14.sp,
                    fontFamily = if (kb == KeyboardType.Number) NumFont else null,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(XC.gold),
                keyboardOptions = KeyboardOptions(keyboardType = kb),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = XC.textFaint, fontSize = 14.sp)
                    }
                    inner()
                },
            )
            if (suffix.isNotEmpty()) {
                Text(suffix, color = XC.textFaint, fontSize = 10.sp)
            }
        }
    }
}

/** 开关行 */
@Composable
fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimen.RowRadius.dp))
            .liquidPress(
                onClick = { onChange(!checked) },
                enabled = enabled,
                shape = RoundedCornerShape(Dimen.RowRadius.dp),
                weight = PressWeight.Row,
            )
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (enabled) title else title, color = if (enabled) XC.text else XC.textFaint, fontSize = 13.sp)
            if (desc.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(desc, color = XC.textFaint, fontSize = 10.5.sp, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        SwitchDot(checked, enabled)
    }
}

@Composable
fun SwitchDot(checked: Boolean, enabled: Boolean = true) {
    Box(
        Modifier
            .width(46.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> Brush.linearGradient(listOf(XC.fill, XC.fill))
                    checked -> Brush.linearGradient(listOf(XC.gold, XC.goldHi))
                    else -> Brush.linearGradient(listOf(XC.ghostA, XC.ghostB))
                }
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> XC.knobOffDim
                        checked -> XC.knobOn
                        else -> XC.knobOff
                    }
                ),
        )
    }
}

@Composable
fun Hint(text: String, color: Color = XC.textFaint) {
    Text(text, color = color, fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
fun SmallChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) XC.gold.copy(alpha = 0.16f) else XC.fill)
            .liquidPress(onClick = onClick, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            color = if (selected) XC.gold else XC.textDim,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 主按钮 */
@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    height: Int = 46,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim))
                else Brush.linearGradient(listOf(XC.fill, XC.fill))
            )
            .liquidPress(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) XC.onGold else XC.textFaint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}

/** 次要按钮（描边玻璃） */
@Composable
fun GhostButton(text: String, height: Int = 42, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(listOf(XC.ghostA, XC.ghostB)))
            .border(0.8.dp, XC.ghostEdge, RoundedCornerShape(13.dp))
            .liquidPress(onClick = onClick, shape = RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = XC.textDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 两档 / 多档选择器 */
@Composable
fun <T> SegmentedPick(
    options: List<T>,
    current: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val sel = o == current
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (sel) XC.gold.copy(alpha = 0.12f) else XC.fill)
                    .border(
                        if (sel) 1.3.dp else 0.8.dp,
                        if (sel) XC.gold.copy(alpha = 0.75f) else XC.lineStrong,
                        RoundedCornerShape(11.dp),
                    )
                    .liquidPress(onClick = { onPick(o) }, shape = RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o),
                    color = if (sel) XC.gold else XC.textDim,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 信息模块显隐行（带标题 + 说明 + 开关） */
@Composable
fun ChipToggleRow(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .liquidPress(
                onClick = { onChange(!on) },
                shape = RoundedCornerShape(10.dp),
                weight = PressWeight.Row,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = XC.text, fontSize = 12.5.sp)
            Spacer(Modifier.height(1.dp))
            Text(detail, color = XC.textFaint, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        SwitchDot(on)
    }
}

// ---------------- 数据展示 ----------------

/** 信息模块小卡：左上标题，右下数值 */
@Composable
fun InfoTile(
    title: String,
    value: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    GlassCard(modifier, radius = 16) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(title, color = XC.textFaint, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                color = if (accent) XC.gold else XC.text,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = NumFont,
                maxLines = 1,
            )
            if (sub != null) {
                Spacer(Modifier.height(3.dp))
                Text(sub, color = XC.textFaint, fontSize = 9.5.sp, maxLines = 1)
            }
        }
    }
}

/** 一行标签 + 值的键值行（详情页信息区） */
@Composable
fun KeyValueRow(k: String, v: String, valueColor: Color = XC.text) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(k, color = XC.textFaint, fontSize = 11.5.sp)
        Spacer(Modifier.weight(1f))
        Text(v, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = NumFont)
    }
}

/**
 * 玻璃进度条：液面按整条轨道铺渐变，再裁出进度段。
 *
 * 超出 100% 的部分单独画一段「溢出条」—— 加班 4h 时第一段满格、第二段半格，
 * 而不是把整条压回 100% 假装没有加班。
 */
@Composable
fun ProgressMeter(
    ratio: Double,
    height: Int = 10,
    showOverflow: Boolean = true,
) {
    val fill = ratio.coerceIn(0.0, 1.0).toFloat()
    val overflow = if (showOverflow) (ratio - 1.0).coerceIn(0.0, 1.0).toFloat() else 0f
    val anim by animateFloatAsState(fill, tween(420), label = "fill")
    val animOT by animateFloatAsState(overflow, tween(420), label = "ot")
    val shape = RoundedCornerShape((height / 2 + 1).dp)
    // Sony 30th 用初代四色按键做进度条渐变；其余主题走主色双色渐变（对齐 macOS main.swift:473）
    val fillBrush = if (XC.highlights.isNotEmpty()) {
        Brush.horizontalGradient(XC.highlights)
    } else {
        Brush.horizontalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(XC.trackFill)
            .border(0.8.dp, XC.trackEdge, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(h / 2f, h / 2f)
            if (anim > 0f) {
                clipRect(0f, 0f, w * anim, h) {
                    drawRoundRect(
                        brush = fillBrush,
                        topLeft = Offset.Zero,
                        size = Size(w, h),
                        cornerRadius = r,
                    )
                    // 顶部一道细高光，做出液体的厚度
                    drawRect(
                        brush = Brush.verticalGradient(listOf(XC.sheen, Color.Transparent)),
                        topLeft = Offset.Zero,
                        size = Size(w, h * 0.36f),
                    )
                }
            }
            if (animOT > 0f) {
                val start = w * anim
                val end = (start + w * animOT).coerceAtMost(w)
                clipRect(start, 0f, end, h) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(XC.orange, XC.orange.copy(alpha = 0.72f))),
                        topLeft = Offset.Zero,
                        size = Size(w, h),
                        cornerRadius = r,
                    )
                }
            }
        }
    }
}

/** 进度条上方的两行说明 */
@Composable
fun ProgressCaption(info: com.haofan.xiaoxinxin.core.ProgressInfo) {
    Column {
        Text(
            info.caption,
            color = XC.textDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
        )
        if (info.footer.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(info.footer, color = XC.textFaint, fontSize = 10.5.sp, lineHeight = 15.sp)
        }
    }
}

/** 分段标题 + 右侧说明，用于设置页分区 */
@Composable
fun RangeCaption(text: String) {
    Text(
        text,
        color = XC.textFaint,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** 隐私模式的小锁徽标 */
@Composable
fun PrivacyBadge(active: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) XC.orange.copy(alpha = 0.14f) else XC.fill)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (active) "🔒" else "👁", fontSize = 9.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            if (active) "隐私模式" else "临时可见",
            color = if (active) XC.orange else XC.textFaint,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
