package com.haofan.xiaoxinxin.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haofan.xiaoxinxin.core.DetailMode
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.HomeButton
import com.haofan.xiaoxinxin.core.MoneyChip
import com.haofan.xiaoxinxin.core.ProgressMetric
import com.haofan.xiaoxinxin.core.fmt
import com.haofan.xiaoxinxin.core.percentText
import kotlinx.coroutines.delay

/**
 * Your Money · 薪酬二级页。
 *
 * 结构照 macOS 2.x：顶部口径切换 → 大字 + 进度条 → 三个打卡按钮 → 8 个信息模块 → 工时校正。
 * 隐私模式下所有工资金额换成百分比，锁图标可临时看一眼（60 秒）。
 */
@Composable
fun SalaryScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val s by EarnRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showUnlock by remember { mutableStateOf(false) }
    var hoursInput by remember { mutableStateOf("") }
    var correctHint by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }

    val info = s.progressInfo(DetailMode.SALARY)
    val chips = MoneyChip.parse(s.hiddenMoneyChips)
    val metric = ProgressMetric.of(s.progressMetric)

    if (showUnlock) {
        PinDialog(
            title = "临时查看",
            message = "输入 4 位密码，60 秒后自动恢复百分比显示",
            confirmText = "解锁",
            verify = { EarnRepo.verifyPin(it) },
            onDismiss = { showUnlock = false },
            onConfirm = {
                EarnRepo.unlockTemporarily(60)
                showUnlock = false
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimen.PageH),
    ) {
        PageHeader(
            title = "薪酬进度",
            subtitle = "Your Money · 今日已到账与工时",
            onBack = onBack,
        ) {
            if (s.privacyEnabled) {
                GlassRoundButton(
                    active = s.privacyActive,
                    onClick = {
                        if (s.privacyActive) showUnlock = true else EarnRepo.relock()
                    },
                ) {
                    Text(
                        if (s.privacyActive) "🔒" else "👁",
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // —— 主卡 ——
        GlassCard(Modifier.fillMaxWidth(), radius = 26, strong = true) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                if (HomeButton.METRIC.key !in s.hiddenButtons) {
                    MetricSwitcher(current = metric) { EarnRepo.setProgressMetric(it.key) }
                    Spacer(Modifier.height(16.dp))
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    if (s.salary > 0) {
                        Text(
                            info.leading,
                            color = if (info.isMoney) XC.gold else XC.text,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = NumFont,
                            letterSpacing = (-1.5).sp,
                            maxLines = 1,
                        )
                        if (info.trailing.isNotEmpty()) {
                            Text(
                                info.trailing,
                                color = XC.textDim,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    } else {
                        Text(
                            "设置月薪后开始计费",
                            color = XC.textDim,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (s.privacyEnabled) PrivacyBadge(s.privacyActive)
                }

                Spacer(Modifier.height(14.dp))
                ProgressMeter(info.ratio, height = 11)
                Spacer(Modifier.height(10.dp))
                ProgressCaption(info)

                Spacer(Modifier.height(16.dp))
                PunchRow(
                    working = s.working,
                    canOvertime = !s.working && s.salary > 0 &&
                        (s.scheduledEnd(now)?.let { !EarnRepo.nowLocal(now).isBefore(it) } == true),
                    onClockIn = { if (s.salary <= 0) onOpenSettings() else EarnRepo.clockIn() },
                    onClockOut = { EarnRepo.clockOut() },
                    onOvertime = { EarnRepo.clockIn() },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // —— 8 个信息模块 ——
        if (chips.isNotEmpty()) {
            SectionLabel("信息模块", "设置里可逐项隐藏")
            Spacer(Modifier.height(2.dp))
            chips.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { chip ->
                        InfoTile(
                            title = chip.title,
                            value = chipValue(chip, s, now),
                            accent = chip == MoneyChip.MONTH_EARNED,
                            modifier = Modifier.weight(1f),
                            sub = chipSub(chip, s),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        // —— 工时校正 ——
        SectionLabel("工时校正", "手动对齐已工作时长")
        Spacer(Modifier.height(2.dp))
        GlassCard(Modifier.fillMaxWidth(), radius = 18) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Field(
                            label = "今日已工作",
                            value = hoursInput,
                            onChange = { hoursInput = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                            suffix = "小时",
                            kb = KeyboardType.Number,
                            placeholder = fmt(s.workedSeconds(now) / 3600, 2),
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Box(
                        Modifier
                            .height(46.dp)
                            .width(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim)))
                            .liquidPress(
                                onClick = {
                                    val h = hoursInput.toDoubleOrNull()
                                    correctHint = if (h == null || h < 0) {
                                        "请输入 0 以上的小时数"
                                    } else {
                                        EarnRepo.correctWork(h)
                                        hoursInput = ""
                                        "已校正为 ${fmt(h, 2)} 小时"
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("应用", color = XC.onGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (correctHint.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FormHint(correctHint, ok = correctHint.startsWith("已"))
                }
                Spacer(Modifier.height(8.dp))
                Hint("用于忘打卡 / 中途离开：直接把今日累计工时设成这个数，金额按费率同步对齐。")
            }
        }

        if (s.logText.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                s.logText,
                color = XC.textFaint,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun MetricSwitcher(current: ProgressMetric, onPick: (ProgressMetric) -> Unit) {
    Column {
        SegmentedPick(
            options = ProgressMetric.salaryCases.take(2),
            current = current,
            label = { it.title.removeSuffix("进度") },
            onPick = onPick,
        )
        Spacer(Modifier.height(6.dp))
        SegmentedPick(
            options = ProgressMetric.salaryCases.drop(2),
            current = current,
            label = { it.title.removeSuffix("进度") },
            onPick = onPick,
        )
        Spacer(Modifier.height(6.dp))
        Text(current.detail, color = XC.textFaint, fontSize = 10.sp)
    }
}

@Composable
private fun PunchRow(
    working: Boolean,
    canOvertime: Boolean,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onOvertime: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (!working) {
            PunchButton("打卡上班", primary = true, modifier = Modifier.weight(1f), onClick = onClockIn)
            if (canOvertime) {
                PunchButton("加班打卡", primary = false, modifier = Modifier.weight(1f), onClick = onOvertime)
            }
        } else {
            PunchButton("打卡下班", primary = true, modifier = Modifier.weight(1f), onClick = onClockOut)
            PunchButton("加班打卡", primary = false, modifier = Modifier.weight(1f), onClick = onOvertime)
        }
    }
}

@Composable
private fun PunchButton(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (primary) Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim))
                else Brush.verticalGradient(listOf(XC.ghostA, XC.ghostB))
            )
            .liquidPress(onClick = onClick, shape = RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) XC.onGold else XC.gold,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

/** 每个信息模块的取值：隐私模式下换成百分比口径 */
private fun chipValue(chip: MoneyChip, s: com.haofan.xiaoxinxin.core.EarnState, now: Long): String {
    val p = s.privacyActive
    return when (chip) {
        MoneyChip.SALARY -> s.displayMoney(s.salary, 0)
        MoneyChip.WORKDAYS -> "${s.workDays} 天"
        MoneyChip.DAY_RATE -> s.displayMoney(s.ratePerDay)
        MoneyChip.HOUR_RATE -> s.displayMoney(s.ratePerHour)
        MoneyChip.PER_SEC -> s.displayMoney(s.ratePerSec, 4)
        MoneyChip.WORKED -> {
            if (p) percentText(s.todayHoursRatio)
            else {
                val m = s.workedMinutes(now)
                "${m / 60} 时 ${m % 60} 分"
            }
        }
        MoneyChip.MONTH_EARNED -> if (p) percentText(s.monthMoneyRatio) else s.displayMoney(s.monthEarned(now))
        MoneyChip.MONTH_OT -> {
            if (p) {
                val over = (s.monthHoursRatio - 1.0).coerceAtLeast(0.0)
                if (over <= 0) "未超出" else "+${percentText(over)}"
            } else {
                val m = (s.monthOTSeconds(now) / 60).toInt()
                if (m <= 0) "0 分" else "${m / 60} 时 ${m % 60} 分"
            }
        }
    }
}

private fun chipSub(chip: MoneyChip, s: com.haofan.xiaoxinxin.core.EarnState): String = when (chip) {
    MoneyChip.WORKDAYS -> if (s.isAutoDays) "自动推算" else "手动指定"
    MoneyChip.WORKED -> if (s.working) "计费中" else "已停表"
    MoneyChip.MONTH_EARNED -> "本月 ${percentText(s.monthMoneyRatio, 0)}"
    else -> ""
}
