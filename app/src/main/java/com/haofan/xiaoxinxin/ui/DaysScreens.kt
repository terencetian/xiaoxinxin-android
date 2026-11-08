package com.haofan.xiaoxinxin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haofan.xiaoxinxin.core.CountdownChip
import com.haofan.xiaoxinxin.core.CountdownItem
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.RecordDayChip
import com.haofan.xiaoxinxin.core.RecordDayItem
import com.haofan.xiaoxinxin.core.countdownProgress
import com.haofan.xiaoxinxin.core.dateKey
import com.haofan.xiaoxinxin.core.elapsedSpan
import com.haofan.xiaoxinxin.core.percentText
import com.haofan.xiaoxinxin.core.recordDayProgress
import kotlinx.coroutines.delay
import java.time.LocalDate

// ══════════════════════════════════════════════════════════════════
// Your Days：倒计时 + 纪念日。两页交互与排版完全一致，只是数据模型不同。
// 事件管理（新增 / 改名 / 改日期 / 删除 / 切换当前）内置在页内，不另开窗口。
// ══════════════════════════════════════════════════════════════════

@Composable
fun CountdownScreen(onBack: () -> Unit) {
    val s by EarnRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var managerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val item = s.activeCountdown
    val p = item?.let { countdownProgress(it) }
    val chips = CountdownChip.parse(s.hiddenCountdownChips)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimen.PageH),
    ) {
        PageHeader(
            title = "倒计时",
            subtitle = "Your Days · 距目标日还剩几天",
            onBack = onBack,
        ) {
            SmallChip(if (managerOpen) "收起管理" else "事件管理", selected = managerOpen) { managerOpen = !managerOpen }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth(), radius = 26, strong = true) {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (item != null && p != null) {
                    Text(
                        if (p.expired) "已到期" else if (item.mode == "percent") "完成进度" else "还剩",
                        color = XC.textFaint,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (item.mode == "percent") percentText(p.ratio) else "${p.daysLeft}",
                            color = XC.gold,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = NumFont,
                            letterSpacing = (-1.5).sp,
                            maxLines = 1,
                        )
                        if (item.mode != "percent") {
                            Text(
                                " 天",
                                color = XC.textDim,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ProgressMeter(p.ratio, height = 10)
                    Spacer(Modifier.height(10.dp))
                    ProgressCaption(s.progressInfo(com.haofan.xiaoxinxin.core.DetailMode.COUNTDOWN))
                } else {
                    Text("还没有倒计时", color = XC.textDim, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "下面点「新增」，填个名字和日期就行",
                        color = XC.textFaint,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (item != null && p != null && chips.isNotEmpty()) {
            SectionLabel("信息模块", "设置里可逐项隐藏")
            Spacer(Modifier.height(2.dp))
            chips.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { chip ->
                        InfoTile(
                            title = chip.title,
                            value = countdownChipValue(chip, item, p),
                            accent = chip == CountdownChip.REMAINING,
                            modifier = Modifier.weight(1f),
                            sub = countdownChipSub(chip, item),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        if (managerOpen) {
            CountdownManager(items = s.countdowns, activeId = s.activeCountdownId)
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun RecordDayScreen(onBack: () -> Unit) {
    val s by EarnRepo.state.collectAsState()
    var managerOpen by remember { mutableStateOf(false) }

    val item = s.activeRecordDay
    val p = item?.let { recordDayProgress(it) }
    val chips = RecordDayChip.parse(s.hiddenRecordChips)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimen.PageH),
    ) {
        PageHeader(
            title = "纪念日",
            subtitle = "Your Days · 从那天起，一共走了多久",
            onBack = onBack,
        ) {
            SmallChip(if (managerOpen) "收起管理" else "事件管理", selected = managerOpen) { managerOpen = !managerOpen }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth(), radius = 26, strong = true) {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (item != null && p != null) {
                    Text(
                        if (p.started) "累计" else "还没到起始日",
                        color = XC.textFaint,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${p.elapsedDays}",
                            color = XC.gold,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = NumFont,
                            letterSpacing = (-1.5).sp,
                            maxLines = 1,
                        )
                        Text(
                            " 天",
                            color = XC.textDim,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // 2.4 起主展示是累计时长，不再画进度条
                    Text(
                        p.span.shortText,
                        color = XC.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ProgressCaption(s.progressInfo(com.haofan.xiaoxinxin.core.DetailMode.RECORD_DAY))
                } else {
                    Text("还没有纪念日", color = XC.textDim, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text("在一起、入职、搬新家 —— 建一个开始累计", color = XC.textFaint, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (item != null && p != null && chips.isNotEmpty()) {
            SectionLabel("信息模块", "设置里可逐项隐藏")
            Spacer(Modifier.height(2.dp))
            chips.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { chip ->
                        InfoTile(
                            title = chip.title,
                            value = recordChipValue(chip, item, p),
                            accent = chip == RecordDayChip.DURATION,
                            modifier = Modifier.weight(1f),
                            sub = recordChipSub(chip, item),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        if (managerOpen) {
            RecordDayManager(items = s.recordDays, activeId = s.activeRecordDayId)
        }

        Spacer(Modifier.height(30.dp))
    }
}

// ---------------- chip 取值 ----------------

private fun countdownChipValue(chip: CountdownChip, item: CountdownItem, p: com.haofan.xiaoxinxin.core.CountdownProgress): String =
    when (chip) {
        CountdownChip.ELAPSED -> "${p.elapsedDays} 天"
        CountdownChip.REMAINING -> if (p.expired) "已到期" else "${p.daysLeft} 天"
        CountdownChip.TARGET -> if (item.hasCustomTime) "${p.targetKey} ${item.targetTimeText}" else p.targetKey
        CountdownChip.PERCENT -> percentText(p.ratio)
        CountdownChip.SPAN -> "${p.totalDays} 天"
        CountdownChip.NAME -> item.name
    }

private fun countdownChipSub(chip: CountdownChip, item: CountdownItem): String = when (chip) {
    CountdownChip.TARGET -> if (item.isYearly) "每年重复" else "仅一次"
    CountdownChip.PERCENT -> if (item.hasCustomStart) "起点 ${item.startKeyText}" else "起点自动"
    CountdownChip.NAME -> item.modeTitle
    else -> ""
}

private fun recordChipValue(chip: RecordDayChip, item: RecordDayItem, p: com.haofan.xiaoxinxin.core.RecordDayProgress): String =
    when (chip) {
        RecordDayChip.ELAPSED -> "${p.elapsedDays} 天"
        RecordDayChip.DURATION -> p.span.shortText
        RecordDayChip.START -> p.startKey
        RecordDayChip.MONTHS -> "${p.elapsedDays / 30} 个月"
        RecordDayChip.YEARS -> "${p.elapsedDays / 365} 年"
        RecordDayChip.NAME -> item.name
    }

private fun recordChipSub(chip: RecordDayChip, item: RecordDayItem): String = when (chip) {
    RecordDayChip.MONTHS, RecordDayChip.YEARS -> "按 30 / 365 天折算"
    RecordDayChip.NAME -> item.modeTitle
    else -> ""
}

// ---------------- 事件管理：倒计时 ----------------

@Composable
private fun CountdownManager(items: List<CountdownItem>, activeId: String) {
    var open by remember { mutableStateOf(true) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var sY by remember { mutableStateOf("") }
    var sM by remember { mutableStateOf("") }
    var sD by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var percent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<CountdownItem?>(null) }

    fun resetForm() {
        editingId = null; name = ""; month = ""; day = ""; year = ""
        sY = ""; sM = ""; sD = ""; time = ""; percent = false; error = ""
    }

    fun fillForm(it: CountdownItem) {
        editingId = it.id
        name = it.name
        month = it.month.toString()
        day = it.day.toString()
        year = it.year?.toString() ?: ""
        sY = it.startYear?.toString() ?: ""
        sM = it.startMonth?.toString() ?: ""
        sD = it.startDay?.toString() ?: ""
        time = if (it.hasCustomTime) it.targetTimeText else ""
        percent = it.mode == "percent"
        error = ""
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "删除倒计时",
            message = "「${target.name}」会被删掉，无法恢复。",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                EarnRepo.deleteCountdown(target.id)
                pendingDelete = null
                if (editingId == target.id) resetForm()
            },
        )
    }

    Spacer(Modifier.height(12.dp))
    SectionLabel("事件管理", "${items.size} 个倒计时")
    Spacer(Modifier.height(2.dp))
    GlassCard(Modifier.fillMaxWidth(), radius = 20) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidPress(
                        onClick = { open = !open },
                        shape = RoundedCornerShape(12.dp),
                        weight = PressWeight.Row,
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("倒计时列表", color = XC.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(if (open) "收起" else "展开", color = XC.textFaint, fontSize = 10.5.sp)
            }
            if (open) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    if (items.isEmpty()) {
                        Hint("还没有倒计时。下面填一个名字和日期，保存即可。")
                    } else {
                        items.forEach { it ->
                            val active = it.id == activeId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .liquidPress(
                                        onClick = { fillForm(it) },
                                        shape = RoundedCornerShape(10.dp),
                                        weight = PressWeight.Row,
                                    )
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (active) XC.gold else XC.trackFill)
                                        .clickable { EarnRepo.setActiveCountdown(it.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (active) Text("✓", color = XC.onGold, fontSize = 9.sp)
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        it.name.ifBlank { "未命名" },
                                        color = if (active) XC.text else XC.textDim,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        countdownSubtitle(it),
                                        color = XC.textFaint,
                                        fontSize = 10.sp,
                                    )
                                }
                                Text(
                                    "删除",
                                    color = XC.danger,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier
                                        .clickable { pendingDelete = it }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    Divider2()
                    Text(
                        if (editingId == null) "新增倒计时" else "编辑中 · 改完点保存",
                        color = XC.textDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(9.dp))
                    Field("名称", name, { name = it.take(12) }, placeholder = "元旦 / 生日 / 旅行")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1.2f)) {
                            Field("月", month, { month = it.filter { c -> c.isDigit() }.take(2) }, kb = KeyboardType.Number, placeholder = "1")
                        }
                        Box(Modifier.weight(1.2f)) {
                            Field("日", day, { day = it.filter { c -> c.isDigit() }.take(2) }, kb = KeyboardType.Number, placeholder = "1")
                        }
                        Box(Modifier.weight(1.6f)) {
                            Field("年", year, { year = it.filter { c -> c.isDigit() }.take(4) }, kb = KeyboardType.Number, placeholder = "留空=每年")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            Field("起始日 年", sY, { sY = it.filter { c -> c.isDigit() }.take(4) }, kb = KeyboardType.Number, placeholder = "留空=自动")
                        }
                        Box(Modifier.weight(1f)) {
                            Field("月", sM, { sM = it.filter { c -> c.isDigit() }.take(2) }, kb = KeyboardType.Number)
                        }
                        Box(Modifier.weight(1f)) {
                            Field("日", sD, { sD = it.filter { c -> c.isDigit() }.take(2) }, kb = KeyboardType.Number)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Field("目标时刻", time, { time = it.take(5) }, placeholder = "留空=00:00")
                    Spacer(Modifier.height(10.dp))
                    SegmentedPick(
                        options = listOf(false, true),
                        current = percent,
                        label = { if (it) "按百分比" else "按剩余天数" },
                    ) { percent = it }
                    Spacer(Modifier.height(8.dp))
                    Hint("年份留空 = 每年重复，过了今年自动顺延到明年；填了年份则是一次性目标。")
                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FormHint(error)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.weight(1f)) {
                            PrimaryButton(if (editingId == null) "保存" else "保存修改") {
                                val hh = time.substringBefore(":").trim().toIntOrNull()
                                val mm = time.substringAfter(":", "").trim().toIntOrNull()
                                val item = CountdownItem(
                                    id = editingId ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    month = month.toIntOrNull() ?: 1,
                                    day = day.toIntOrNull() ?: 1,
                                    year = year.toIntOrNull(),
                                    mode = if (percent) "percent" else "days",
                                    createdKey = items.firstOrNull { it.id == editingId }?.createdKey
                                        ?: dateKey(LocalDate.now()),
                                    startYear = sY.toIntOrNull(),
                                    startMonth = sM.toIntOrNull(),
                                    startDay = sD.toIntOrNull(),
                                    targetHour = if (time.isBlank()) null else hh,
                                    targetMinute = if (time.isBlank()) null else mm,
                                )
                                val err = EarnRepo.upsertCountdown(item, editingId == null)
                                if (err == null) resetForm() else error = err
                            }
                        }
                        if (editingId != null) {
                            Box(Modifier.weight(0.7f)) {
                                GhostButton("取消") { resetForm() }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun countdownSubtitle(it: CountdownItem): String {
    val repeat = if (it.isYearly) "每年" else "${it.year}"
    val tail = if (it.hasCustomTime) " ${it.targetTimeText}" else ""
    return "$repeat ${it.month} 月 ${it.day} 日$tail · ${it.modeTitle}"
}

// ---------------- 事件管理：纪念日 ----------------

@Composable
private fun RecordDayManager(items: List<RecordDayItem>, activeId: String) {
    var open by remember { mutableStateOf(true) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<RecordDayItem?>(null) }

    fun resetForm() {
        editingId = null; name = ""; year = ""; month = ""; day = ""; error = ""
    }

    fun fillForm(it: RecordDayItem) {
        editingId = it.id
        name = it.name
        year = it.year.toString()
        month = it.month.toString()
        day = it.day.toString()
        error = ""
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "删除纪念日",
            message = "「${target.name}」会被删掉，无法恢复。",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                EarnRepo.deleteRecordDay(target.id)
                pendingDelete = null
                if (editingId == target.id) resetForm()
            },
        )
    }

    Spacer(Modifier.height(12.dp))
    SectionLabel("事件管理", "${items.size} 个纪念日")
    Spacer(Modifier.height(2.dp))
    GlassCard(Modifier.fillMaxWidth(), radius = 20) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidPress(
                        onClick = { open = !open },
                        shape = RoundedCornerShape(12.dp),
                        weight = PressWeight.Row,
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("纪念日列表", color = XC.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(if (open) "收起" else "展开", color = XC.textFaint, fontSize = 10.5.sp)
            }
            if (open) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    if (items.isEmpty()) {
                        Hint("还没有纪念日。下面填个名字和起始日，保存即可。")
                    } else {
                        items.forEach { it ->
                            val active = it.id == activeId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .liquidPress(
                                        onClick = { fillForm(it) },
                                        shape = RoundedCornerShape(10.dp),
                                        weight = PressWeight.Row,
                                    )
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (active) XC.gold else XC.trackFill)
                                        .clickable { EarnRepo.setActiveRecordDay(it.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (active) Text("✓", color = XC.onGold, fontSize = 9.sp)
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        it.name.ifBlank { "未命名" },
                                        color = if (active) XC.text else XC.textDim,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    val sub = recordDayProgress(it)?.let { p ->
                                        "从 ${p.startKey} 起 · 累计 ${p.elapsedDays} 天"
                                    } ?: "${it.year}-${it.month}-${it.day}"
                                    Text(sub, color = XC.textFaint, fontSize = 10.sp)
                                }
                                Text(
                                    "删除",
                                    color = XC.danger,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier
                                        .clickable { pendingDelete = it }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    Divider2()
                    Text(
                        if (editingId == null) "新增纪念日" else "编辑中 · 改完点保存",
                        color = XC.textDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(9.dp))
                    Field("名称", name, { name = it.take(12) }, placeholder = "在一起 / 入职 / 搬家")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1.4f)) {
                            Field("起始年", year, { year = it.filter { c -> c.isDigit() }.take(4) }, kb = KeyboardType.Number, placeholder = "2020")
                        }
                        Box(Modifier.weight(1f)) {
                            Field("月", month, { month = it.filter { c -> c.isDigit() }.take(2) }, kb = KeyboardType.Number, placeholder = "1")
                        }
                        Box(Modifier.weight(1f)) {
                            Field("日", day, { day = it.filter { c -> c.isDigit() }.take(2) }, kb = KeyboardType.Number, placeholder = "1")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Hint("主展示是累计时长（X 年 Y 个月 Z 天）+ 累计天数，不做进度条。")
                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FormHint(error)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.weight(1f)) {
                            PrimaryButton(if (editingId == null) "保存" else "保存修改") {
                                val existing = items.firstOrNull { it.id == editingId }
                                val item = RecordDayItem(
                                    id = editingId ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    year = year.toIntOrNull() ?: LocalDate.now().year,
                                    month = month.toIntOrNull() ?: 1,
                                    day = day.toIntOrNull() ?: 1,
                                    targetDays = existing?.targetDays ?: 0,
                                    mode = existing?.mode ?: "days",
                                )
                                val err = EarnRepo.upsertRecordDay(item, editingId == null)
                                if (err == null) resetForm() else error = err
                            }
                        }
                        if (editingId != null) {
                            Box(Modifier.weight(0.7f)) {
                                GhostButton("取消") { resetForm() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 细分隔线 */
@Composable
private fun Divider2() {
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(XC.line),
    )
    Spacer(Modifier.height(10.dp))
}
