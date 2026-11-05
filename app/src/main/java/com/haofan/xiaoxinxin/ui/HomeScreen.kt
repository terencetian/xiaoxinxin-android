package com.haofan.xiaoxinxin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.fmt
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onToggleFloat: () -> Unit,
) {
    val s by EarnRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }

    val earned = s.displayEarned(now)
    val progress = s.progress(now)
    val working = s.working
    val overtime = s.overtimeNow(now)

    LiquidBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            TopBar(onOpenSettings, onToggleFloat, s.floatEnabled)
            Spacer(Modifier.height(16.dp))

            // —— 主卡：今日到账 ——
            GlassCard(Modifier.fillMaxWidth(), radius = 28, strong = true) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "今日已到账",
                        color = XC.textFaint,
                        fontSize = 11.5.sp,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (s.salary > 0) {
                            Text(
                                "¥",
                                color = XC.goldDim,
                                fontSize = 22.sp,
                                fontWeight = W500,
                                modifier = Modifier.padding(bottom = 9.dp, end = 2.dp),
                            )
                            Text(
                                fmt(earned),
                                style = TextStyle(
                                    color = XC.gold,
                                    fontSize = MoneyBig,
                                    fontWeight = W600,
                                    fontFamily = NumFont,
                                    shadow = Shadow(
                                        color = XC.gold.copy(alpha = if (XC.isDark) 0.38f else 0.16f),
                                        offset = Offset.Zero,
                                        blurRadius = 14f,
                                    ),
                                ),
                            )
                        } else {
                            Text(
                                "设置月薪后开始计费",
                                color = XC.textDim,
                                fontSize = 16.sp,
                                fontWeight = W500,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }

                    if (overtime) {
                        Spacer(Modifier.height(10.dp))
                        Pill("加班中", XC.orange)
                    }

                    Spacer(Modifier.height(18.dp))
                    GlassProgress(if (s.salary > 0) progress.toFloat() else 0f, s.barStyle)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        if (s.salary > 0)
                            "今日进度 ${fmt(progress * 100, 1)}% · 距离全天还差 ¥${fmt((s.ratePerDay - earned).coerceAtLeast(0.0))}"
                        else "还没有月薪，算不出你每秒值多少钱",
                        color = XC.textFaint,
                        fontSize = 11.5.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 三档费率 ——
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                RateTile("日薪", if (s.salary > 0) "¥${fmt(s.ratePerDay)}" else "—", Modifier.weight(1f))
                RateTile("时薪", if (s.salary > 0) "¥${fmt(s.ratePerHour)}" else "—", Modifier.weight(1f))
                RateTile("每秒", if (s.salary > 0) "¥${fmt(s.ratePerSec, 4)}" else "—", Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            // —— 打卡 ——
            PunchButton(
                working = working,
                label = when {
                    s.salary <= 0 -> "先设置月薪"
                    working -> "打卡下班"
                    else -> "打卡上班"
                },
                workedText = if (working) {
                    val m = s.workedMinutes(now)
                    "已工作 ${m / 60} 小时 ${m % 60} 分"
                } else null,
                onClick = {
                    when {
                        s.salary <= 0 -> onOpenSettings()
                        working -> EarnRepo.clockOut()
                        else -> EarnRepo.clockIn()
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MonthTile("本月进账", "¥${fmt(s.monthEarned(now))}", Modifier.weight(1f))
                MonthTile(
                    "本月加班",
                    run {
                        val m = (s.monthOTSeconds(now) / 60).toInt()
                        if (m <= 0) "0 分" else "${m / 60} 时 ${m % 60} 分"
                    },
                    Modifier.weight(1f),
                )
            }

            if (s.logText.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    s.logText,
                    color = XC.textFaint,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            if (s.salary <= 0) {
                Spacer(Modifier.height(20.dp))
                SetupCard(onOpen = onOpenSettings)
            } else if (!s.floatEnabled) {
                Spacer(Modifier.height(20.dp))
                FloatHintCard(onEnable = onToggleFloat)
            } else {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(XC.teal),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("顶部悬浮岛已开启 · 单击展开、双击迷你、长按隐藏", color = XC.textFaint, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

/** 首次使用引导：还没填月薪时置顶显示。 */
@Composable
private fun SetupCard(onOpen: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), radius = 20, strong = true) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 15.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(XC.goldHi, XC.gold))),
                contentAlignment = Alignment.Center,
            ) {
                Text("1", color = XC.onGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("先填一下月薪", color = XC.text, fontSize = 13.sp, fontWeight = W500)
                Spacer(Modifier.height(3.dp))
                Text(
                    "填完才知道你每一秒值多少钱，30 秒搞定",
                    color = XC.textFaint,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("去设置 ›", color = XC.gold, fontSize = 11.5.sp, fontWeight = W500)
        }
    }
}

/** 未开启悬浮窗时的引导卡。 */
@Composable
private fun FloatHintCard(onEnable: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), radius = 20) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEnable)
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(XC.gold.copy(alpha = 0.20f), XC.gold.copy(alpha = 0.06f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("浮", color = XC.gold, fontSize = 14.sp, fontWeight = W500)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("开启屏幕悬浮窗", color = XC.text, fontSize = 13.sp, fontWeight = W500)
                Spacer(Modifier.height(3.dp))
                Text(
                    "切到别的应用、刷视频打游戏时，进账数字依然挂在屏幕上",
                    color = XC.textFaint,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", color = XC.textDim, fontSize = 20.sp)
        }
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit, onToggleFloat: () -> Unit, floatOn: Boolean) {
    GlassCard(Modifier.fillMaxWidth(), radius = 40) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(XC.goldHi, XC.gold, XC.goldDim))),
                contentAlignment = Alignment.Center,
            ) {
                Text("薪", color = XC.onGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(9.dp))
            Text("小薪薪", color = XC.text, fontSize = 14.5.sp, fontWeight = W500)
            Spacer(Modifier.width(6.dp))
            Text("v1.9.5", color = XC.textFaint, fontSize = 10.5.sp)
            Spacer(Modifier.weight(1f))
            RoundGlassButton(active = floatOn, onClick = onToggleFloat) {
                Text("浮", color = if (floatOn) XC.gold else XC.textDim, fontSize = 13.sp, fontWeight = W500)
            }
            Spacer(Modifier.width(7.dp))
            RoundGlassButton(active = false, onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = XC.textDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundGlassButton(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(31.dp)
            .clip(shape)
            .background(
                if (active) Brush.linearGradient(
                    listOf(XC.gold.copy(alpha = 0.20f), XC.gold.copy(alpha = 0.10f))
                )
                else Brush.linearGradient(listOf(XC.ghostA, XC.ghostB))
            )
            .border(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(
                        if (active) XC.gold.copy(alpha = 0.6f) else XC.ghostEdge,
                        Color.Transparent,
                    )
                ),
                shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 玻璃槽 + 液面 + 顶部反光。
 *
 * 液面渐变按整条轨道宽度铺，再用 clipRect 裁出左侧进度段 —— 这样彩虹条是
 * 「随着进度一点点露出粉橙黄青紫」，而不是一小段被压扁的彩虹。
 */
@Composable
private fun GlassProgress(p: Float, style: String = "gold") {
    val v = p.coerceIn(0f, 1f)
    val brush = barBrush(BarStyle.of(style))
    Box(
        Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(XC.trackFill)
            .border(0.8.dp, XC.trackEdge, RoundedCornerShape(5.dp)),
    ) {
        if (v > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * v) { this@drawWithContent.drawContent() }
                    },
            ) {
                Box(Modifier.fillMaxSize().background(brush))
                // 顶部一道细高光，做出液体的厚度
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Brush.verticalGradient(listOf(XC.sheen, Color.Transparent))),
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .border(0.8.dp, color.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = W500)
    }
}

@Composable
private fun RateTile(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier, radius = 17) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = XC.text, fontSize = 13.5.sp, fontWeight = W500, fontFamily = NumFont)
            Spacer(Modifier.height(4.dp))
            Text(label, color = XC.textFaint, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MonthTile(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier, radius = 17) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 13.dp)) {
            Text(label, color = XC.textFaint, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            Text(value, color = XC.teal, fontSize = 15.sp, fontWeight = W500, fontFamily = NumFont)
        }
    }
}

@Composable
private fun PunchButton(working: Boolean, label: String, workedText: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .clip(shape)
                .background(
                    if (working) Brush.linearGradient(listOf(XC.ghostA, XC.ghostB))
                    else Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim))
                )
                .border(
                    if (working) 0.9.dp else 0.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (working) XC.gold.copy(alpha = 0.65f) else Color.Transparent,
                            Color.Transparent,
                        )
                    ),
                    shape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (!working) {
                // 玻璃高光：顶部一条更亮的横向渐变
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(
                            Brush.verticalGradient(listOf(XC.sheen, Color.Transparent))
                        ),
                )
            }
            Text(
                label,
                color = if (working) XC.gold else XC.onGold,
                fontSize = 15.5.sp,
                fontWeight = W600,
                letterSpacing = 1.2.sp,
            )
        }
        if (workedText != null) {
            Spacer(Modifier.height(9.dp))
            Text(
                workedText,
                color = XC.textFaint,
                fontSize = 11.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
