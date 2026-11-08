package com.haofan.xiaoxinxin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.EarnState
import com.haofan.xiaoxinxin.core.StatRange
import com.haofan.xiaoxinxin.core.Stats
import com.haofan.xiaoxinxin.core.countdownProgress
import com.haofan.xiaoxinxin.core.dateKey
import com.haofan.xiaoxinxin.core.fmt
import com.haofan.xiaoxinxin.core.percentText
import com.haofan.xiaoxinxin.core.recordDayProgress
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计页。
 *
 * 三层信息，从粗到细：
 *  1. 区间切换（本周 / 本月 / 上月 / 本年）—— 决定下面所有数字的口径
 *  2. 薪酬 × 加班双视角 —— 同一批数据，换个角度算
 *  3. 图 + 列表 —— 图看趋势，列表看明细，点开某天看当天构成
 */
@Composable
fun StatsScreen() {
    val s by EarnRepo.state.collectAsState()
    var tab by remember { mutableStateOf("money") }     // 2.7：MONEY / DAYS 两大标签
    var range by remember { mutableStateOf(StatRange.MONTH) }
    var otFirst by remember { mutableStateOf(false) }   // false = 薪酬视角，true = 加班视角
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expanded by remember { mutableStateOf<String?>(null) }

    // 统计页没必要 200ms 刷一次，1 秒够用，还能少算几帧
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val today = EarnRepo.nowLocal(now).toLocalDate()
    val todayKey = dateKey(today)
    val todayEarned = s.displayEarned(now)
    val todayOT = s.overtimeSeconds + if (s.overtimeNow(now)) {
        ((now - (s.clockInAt ?: now)).coerceAtLeast(0L)) / 1000.0
    } else 0.0

    val logs = s.dayLog
    val days = Stats.days(range, today, logs, todayEarned, todayOT)
    val sum = Stats.summarize(range.label, days)
    val workDaysInRange = days.count { com.haofan.xiaoxinxin.core.isWorkDate(LocalDate.parse(it.key)) }
    val months = Stats.byMonth(logs, today.year)
    val woW = Stats.weekOverWeek(today, logs, todayEarned)

    LiquidBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(14.dp))

            // —— 标题 ——
            Row(verticalAlignment = Alignment.Bottom) {
                Text("统计", color = XC.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = -0.5.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (tab == "money") range.boundsText(today) else "Your Days",
                    color = XC.textFaint,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // —— 2.7：MONEY / DAYS 两大标签 ——
            ModuleTabs(tab) {
                tab = it
                expanded = null
            }

            // DAYS 标签：时间区间与金额口径都不适用，直接展示事件统计
            if (tab == "days") {
                Spacer(Modifier.height(12.dp))
                DaysStatsSection(s)
                Spacer(Modifier.height(16.dp))
                return@Column
            }

            Spacer(Modifier.height(12.dp))

            // —— 区间切换 ——
            RangePicker(range) {
                range = it
                expanded = null
            }

            Spacer(Modifier.height(12.dp))

            // —— 总览卡 ——
            GlassCard(Modifier.fillMaxWidth(), radius = 28, strong = true) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    // 视角切换：薪酬 / 加班
                    ViewToggle(otFirst) { otFirst = it }
                    Spacer(Modifier.height(14.dp))

                    AnimatedContent(
                        targetState = otFirst,
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInHorizontally { if (it > 0) 40 else -40 })
                                .togetherWith(fadeOut(tween(140)))
                        },
                        label = "stats-headline",
                    ) { ot ->
                        if (!ot) {
                            Column {
                                Text(
                                    "${range.label}累计进账",
                                    color = XC.textFaint,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "¥",
                                        color = XC.goldDim,
                                        fontSize = 18.sp,
                                        fontWeight = W500,
                                        modifier = Modifier.padding(bottom = 7.dp, end = 2.dp),
                                    )
                                    Text(
                                        fmt(sum.earned),
                                        color = XC.gold,
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = NumFont,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "出勤 ${sum.days} 天 · 日均 ¥${fmt(sum.avgEarned)}",
                                        color = XC.textFaint,
                                        fontSize = 11.sp,
                                    )
                                    if (woW != null) {
                                        Spacer(Modifier.width(8.dp))
                                        TrendChip(woW)
                                    }
                                }
                            }
                        } else {
                            Column {
                                Text(
                                    "${range.label}累计加班",
                                    color = XC.textFaint,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    val totalMin = (sum.otSeconds / 60).toInt()
                                    Text(
                                        if (totalMin >= 60) "${totalMin / 60}" else "${totalMin}",
                                        color = XC.orange,
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = NumFont,
                                    )
                                    Text(
                                        if (totalMin >= 60) " 小时" else " 分钟",
                                        color = XC.orange.copy(alpha = 0.7f),
                                        fontSize = 15.sp,
                                        fontWeight = W500,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "折算 ¥${fmt(Stats.otEarned(sum.otSeconds, s.ratePerHour))} · " +
                                        if (sum.days > 0) "人均 ${Stats.otText(sum.otSeconds / sum.days)}" else "还没有加班记录",
                                    color = XC.textFaint,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 三格速览 ——
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatTile(
                    "日均",
                    "¥${fmt(sum.avgEarned)}",
                    "${sum.days} 天有记录",
                    Modifier.weight(1f),
                )
                StatTile(
                    "最高单日",
                    days.filter { it.present }.maxByOrNull { it.earned }?.let { "¥${fmt(it.earned)}" } ?: "—",
                    days.filter { it.present }.maxByOrNull { it.earned }?.key?.takeLast(5) ?: "暂无",
                    Modifier.weight(1f),
                )
                StatTile(
                    "出勤率",
                    if (workDaysInRange > 0) "${sum.days * 100 / workDaysInRange}%" else "—",
                    "应出勤 $workDaysInRange 天",
                    Modifier.weight(1f),
                    accent = XC.teal,
                )
            }

            Spacer(Modifier.height(12.dp))

            // —— 趋势图 ——
            GlassCard(Modifier.fillMaxWidth(), radius = 24) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (otFirst) "每日加班时长" else "每日进账",
                            color = XC.text,
                            fontSize = 13.sp,
                            fontWeight = W500,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(summaryOf(days, otFirst), color = XC.textFaint, fontSize = 10.5.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    val chartDays = when (range) {
                        // 年视图柱子太细，按月聚合更好读
                        StatRange.YEAR -> emptyList()
                        else -> days
                    }
                    if (range == StatRange.YEAR) {
                        YearBars(months, otFirst)
                    } else if (days.none { it.present }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("这一段时间还没有进账记录", color = XC.textFaint, fontSize = 11.5.sp)
                        }
                    } else {
                        MiniBarChart(
                            values = chartDays.map { if (it.present) (if (otFirst) it.otSeconds / 3600.0 else it.earned) else null },
                            labels = chartDays.map {
                                if (range == StatRange.WEEK || range == StatRange.MONTH) Stats.weekdayShort(LocalDate.parse(it.key))
                                else it.key.takeLast(2)
                            },
                            highlight = chartDays.indexOfFirst { it.isToday },
                            color = if (otFirst) XC.orange else XC.gold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // —— 明细列表 ——
            GlassCard(Modifier.fillMaxWidth(), radius = 24) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("明细", color = XC.text, fontSize = 13.sp, fontWeight = W500)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "点开看当天构成",
                            color = XC.textFaint,
                            fontSize = 10.5.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (days.none { it.present }) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "这个区间还没有记录。\n上班打个卡，这里就会长出柱子。",
                            color = XC.textFaint,
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                    } else {
                        // 最近的在前
                        days.reversed().filter { it.present }.forEach { d ->
                            val open = expanded == d.key
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .liquidPress(
                                        onClick = { expanded = if (open) null else d.key },
                                        shape = RoundedCornerShape(12.dp),
                                        weight = PressWeight.Row,
                                    ),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(34.dp, 34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (d.isToday) XC.gold.copy(alpha = 0.14f) else XC.fill
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            if (d.isToday) "今" else Stats.weekdayShort(LocalDate.parse(d.key)),
                                            color = if (d.isToday) XC.gold else XC.textDim,
                                            fontSize = 12.sp,
                                            fontWeight = W500,
                                        )
                                    }
                                    Spacer(Modifier.width(11.dp))
                                    Column {
                                        Text(
                                            "${d.key.take(5)}-${d.key.takeLast(2)} 周${Stats.weekdayShort(LocalDate.parse(d.key))}",
                                            color = XC.text,
                                            fontSize = 12.5.sp,
                                            fontFamily = NumFont,
                                            maxLines = 1,
                                        )
                                        if (d.otSeconds > 0) {
                                            Text(
                                                "加班 ${Stats.otText(d.otSeconds)}",
                                                color = XC.orange,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "¥${fmt(d.earned)}",
                                        color = XC.gold,
                                        fontSize = 14.sp,
                                        fontWeight = W500,
                                        fontFamily = NumFont,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (open) "⌄" else "›",
                                        color = XC.textFaint,
                                        fontSize = 13.sp,
                                    )
                                }
                                if (open) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 45.dp, bottom = 10.dp),
                                    ) {
                                        val otMoney = Stats.otEarned(d.otSeconds, s.ratePerHour)
                                        DetailLine(
                                            "正常工时进账",
                                            "¥${fmt((d.earned - otMoney).coerceAtLeast(0.0))}",
                                        )
                                        DetailLine(
                                            "加班时长",
                                            Stats.otText(d.otSeconds),
                                            if (d.otSeconds > 0) XC.orange else null,
                                        )
                                        DetailLine(
                                            "加班折算（按时薪）",
                                            if (otMoney > 0) "¥${fmt(otMoney)}" else "—",
                                            if (otMoney > 0) XC.orange else null,
                                        )
                                        DetailLine(
                                            "占日薪比例",
                                            if (s.ratePerDay > 0) "${fmt(d.earned / s.ratePerDay * 100, 0)}%" else "—",
                                        )
                                        if (d.isToday) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "今日数据随打卡实时变化，以实际下班结算为准",
                                                color = XC.textFaint,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 45.dp)
                                    .height(0.7.dp)
                                    .background(XC.line),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "加班金额按「当前时薪 × 加班时长」折算，仅作参考；实际加班费以公司规定为准。",
                color = XC.textFaint,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun StatRange.boundsText(today: LocalDate): String {
    val (f, t) = Stats.bounds(this, today)
    return "${f.monthValue}/${f.dayOfMonth} – ${t.monthValue}/${t.dayOfMonth}"
}

/** 区间切换：四格等宽，选中项整块上浮 + 金色描边。 */
@Composable
private fun RangePicker(current: StatRange, onPick: (StatRange) -> Unit) {    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(XC.fill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        StatRange.entries.forEach { r ->
            val sel = r == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (sel) Brush.verticalGradient(listOf(XC.goldHi, XC.gold)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .liquidPress(onClick = { onPick(r) }, shape = RoundedCornerShape(11.dp))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    r.label,
                    color = if (sel) XC.onGold else XC.textDim,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 薪酬 / 加班 双视角切换。 */
@Composable
private fun ViewToggle(otFirst: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(XC.fill)
            .padding(2.dp),
    ) {
        ToggleItem("薪酬", !otFirst) { onChange(false) }
        ToggleItem("加班", otFirst) { onChange(true) }
    }
}

@Composable
private fun ToggleItem(text: String, sel: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (sel) XC.card else Color.Transparent)
            .border(if (sel) 0.8.dp else 0.dp, if (sel) XC.lineStrong else Color.Transparent, RoundedCornerShape(999.dp))
            .liquidPress(onClick = onClick, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            color = if (sel) XC.gold else XC.textFaint,
            fontSize = 11.sp,
            fontWeight = if (sel) W600 else W500,
        )
    }
}

@Composable
private fun TrendChip(pct: Double) {
    val up = pct >= 0
    val c = if (up) XC.teal else XC.orange
    Text(
        (if (up) "↑" else "↓") + "${fmt(kotlin.math.abs(pct) * 100, 0)}% 同比上周",
        color = c,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    accent: Color = XC.text,
) {
    GlassCard(modifier, radius = 18) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(label, color = XC.textFaint, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = NumFont,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(sub, color = XC.textFaint, fontSize = 9.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, color: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = XC.textFaint, fontSize = 10.5.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = color ?: XC.textDim, fontSize = 10.5.sp, fontFamily = NumFont)
    }
}

private fun summaryOf(days: List<com.haofan.xiaoxinxin.core.DayStat>, ot: Boolean): String {
    val present = days.filter { it.present }
    if (present.isEmpty()) return "—"
    return if (!ot) {
        val max = present.maxOf { it.earned }
        "峰值 ¥${fmt(max)}"
    } else {
        val max = present.maxOf { it.otSeconds }
        "最长 ${Stats.otText(max)}"
    }
}

/** 年视图：12 根月柱。 */
@Composable
private fun YearBars(months: List<com.haofan.xiaoxinxin.core.MonthStat>, ot: Boolean) {
    val now = YearMonth.now()
    MiniBarChart(
        values = months.map {
            val empty = it.days == 0
            if (empty) null else if (ot) it.otSeconds / 3600.0 else it.earned
        },
        labels = months.map { "${it.month.takeLast(2)}月" },
        highlight = months.indexOfFirst { it.month == now.toString() },
        color = if (ot) XC.orange else XC.gold,
    )
}

/** 2.7：MONEY / DAYS 两大标签，两格等宽分段，样式与区间切换一脉相承。 */
@Composable
private fun ModuleTabs(current: String, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(XC.fill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf("money" to "MONEY · 薪酬", "days" to "DAYS · 日子").forEach { (key, label) ->
            val sel = key == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (sel) Brush.verticalGradient(listOf(XC.goldHi, XC.gold))
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .liquidPress(
                        onClick = { onPick(key) },
                        shape = RoundedCornerShape(11.dp),
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) XC.onGold else XC.textDim,
                    fontSize = 12.5.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

/** 细分条：轨道 + 单色填充，DAYS 标签里给每个事件画进度。 */
@Composable
private fun MiniTrack(ratio: Double, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(XC.trackFill),
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0.0, 1.0).toFloat().coerceAtLeast(0.02f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
    }
}

/**
 * 2.7：DAYS 标签正文 —— 全部倒计时 + 全部纪念日一览，
 * 每个事件一行：名称、主值（剩余 / 累计天数）、说明与进度条。
 * 当前事件排在最前并带主色高亮。
 */
@Composable
private fun DaysStatsSection(s: EarnState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // —— 倒计时 ——
        GlassCard(Modifier.fillMaxWidth(), radius = 24) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("倒计时", color = XC.text, fontSize = 13.sp, fontWeight = W500)
                    Spacer(Modifier.weight(1f))
                    Text("${s.countdowns.size} 个事件", color = XC.textFaint, fontSize = 10.5.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (s.countdowns.isEmpty()) {
                    EmptyDaysHint("还没有倒计时。进 Your Days · 倒计时 → 事件管理 → 新增。")
                } else {
                    val sorted = s.countdowns.sortedByDescending { it.id == s.activeCountdownId }
                    sorted.forEach { item ->
                        val p = countdownProgress(item)
                        val active = item.id == s.activeCountdownId
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (active) {
                                    Box(
                                        Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                                            .background(XC.gold),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    item.name.ifEmpty { "未命名" },
                                    color = XC.text,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    when {
                                        p == null -> "--"
                                        p.expired -> "已到"
                                        item.mode == "percent" -> percentText(p.ratio, 0)
                                        else -> "${p.daysLeft} 天"
                                    },
                                    color = XC.gold,
                                    fontSize = 14.sp,
                                    fontWeight = W500,
                                    fontFamily = NumFont,
                                )
                            }
                            if (p != null) {
                                Spacer(Modifier.height(6.dp))
                                MiniTrack(
                                    p.ratio,
                                    if (p.expired) XC.orange else XC.gold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (p.expired)
                                        "${item.name} 已到期 · 累计 ${p.elapsedDays} 天"
                                    else
                                        "${p.targetKey} 目标 · 已走 ${percentText(p.ratio, 0)}",
                                    color = XC.textFaint,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (item != sorted.last()) {
                            Box(Modifier.fillMaxWidth().height(0.7.dp).background(XC.line))
                        }
                    }
                }
            }
        }

        // —— 纪念日 ——
        GlassCard(Modifier.fillMaxWidth(), radius = 24) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("纪念日", color = XC.text, fontSize = 13.sp, fontWeight = W500)
                    Spacer(Modifier.weight(1f))
                    Text("${s.recordDays.size} 个事件", color = XC.textFaint, fontSize = 10.5.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (s.recordDays.isEmpty()) {
                    EmptyDaysHint("还没有纪念日。进 Your Days · 纪念日 → 事件管理 → 新增。")
                } else {
                    val sorted = s.recordDays.sortedByDescending { it.id == s.activeRecordDayId }
                    sorted.forEach { item ->
                        val p = recordDayProgress(item)
                        val active = item.id == s.activeRecordDayId
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (active) {
                                    Box(
                                        Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                                            .background(XC.teal),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    item.name.ifEmpty { "未命名" },
                                    color = XC.text,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    p?.let { "${it.elapsedDays} 天" } ?: "--",
                                    color = XC.teal,
                                    fontSize = 14.sp,
                                    fontWeight = W500,
                                    fontFamily = NumFont,
                                )
                            }
                            if (p != null) {
                                Spacer(Modifier.height(6.dp))
                                MiniTrack(p.ratio, XC.teal)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (p.started)
                                        "从 ${p.startKey} 起 · 累计 ${p.span.shortText}"
                                    else
                                        "起始日 ${p.startKey} 还没到",
                                    color = XC.textFaint,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (item != sorted.last()) {
                            Box(Modifier.fillMaxWidth().height(0.7.dp).background(XC.line))
                        }
                    }
                }
            }
        }

        Text(
            "倒计时按目标日倒数，纪念日从起始日累计；当前事件在 Your Days 页切换。",
            color = XC.textFaint,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyDaysHint(text: String) {
    Text(
        text,
        color = XC.textFaint,
        fontSize = 11.5.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}
