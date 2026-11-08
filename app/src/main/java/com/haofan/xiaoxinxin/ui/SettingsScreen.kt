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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haofan.xiaoxinxin.BuildConfig
import com.haofan.xiaoxinxin.core.CountdownChip
import com.haofan.xiaoxinxin.core.DetailMode
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.HomeButton
import com.haofan.xiaoxinxin.core.HomeCard
import com.haofan.xiaoxinxin.core.IslandDoctor
import com.haofan.xiaoxinxin.core.IslandReport
import com.haofan.xiaoxinxin.core.MoneyChip
import com.haofan.xiaoxinxin.core.ProgressMetric
import com.haofan.xiaoxinxin.core.RecordDayChip
import com.haofan.xiaoxinxin.core.StatusBarKind
import com.haofan.xiaoxinxin.core.dateKey
import com.haofan.xiaoxinxin.core.fmt
import com.haofan.xiaoxinxin.core.percentText
import com.haofan.xiaoxinxin.floatx.FloatService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 2.0 设置页：三分区 19 组，与 macOS 2.x 的设置结构一一对应。
 *
 *   Your Money —— salary / workdays / time / metric / moneyChips / correct / ledger
 *   Your Days  —— countdown / chips / recordDay / recordChips
 *   通用       —— theme / layout / buttons / statusbar / privacy / look / window / about
 *
 * macOS 的「菜单栏显示」在 Android 上落到「状态栏与通知」：同一套显示项，
 * 但渲染到通知栏 / 悬浮岛，而不是菜单栏文字。
 */
private const val DEV_NAME = "逍狗屁屁"
private const val DEV_TAGLINE = "让每一秒都看得见价值"
private const val REPO_URL = "github.com/terencetian"

private val ALL_GROUPS = listOf(
    "salary", "workdays", "time", "metric", "moneyChips", "correct", "ledger",
    "countdown", "chips", "recordDay", "recordChips",
    "theme", "layout", "buttons", "statusbar", "privacy", "look", "window", "about",
)

private val RELEASE_HISTORY = listOf(
    Triple("2.7.1", "2026-11-08", "状态栏图标对齐新版（¥｜沙漏）；新增上次异常退出诊断（原生崩溃/ANR 也能记录）；崩溃日志落盘 CrashGuard"),

    Triple("2.7.0", "2026-11-08", "悬浮岛 / 通知 / 灵动岛支持 MONEY·DAYS 轮播显示（设置页可配显示项与间隔）；三处配色跟随主题实时换装；统计页新增 MONEY / DAYS 双标签"),
    Triple("2.6.2", "2026-11-08", "版本号与 macOS 对齐（Build 43）；默认主题改为天蓝、液态玻璃按压反馈、玻璃滑块、引导页、底栏不再遮挡卡片"),
    Triple("2.0.0", "2026-11-08", "对齐 macOS 2.0：三模块分页、四套主题、19 组设置、隐私模式、事件管理"),
    Triple("1.9.10", "2026-11-06", "首页版本号改读 BuildConfig"),
    Triple("1.9.8", "2026-11-05", "到点自动上班 / 统计页 / 玻璃底栏三 Tab"),
)

@Composable
fun SettingsScreen(onOpenDetail: (DetailMode) -> Unit = {}) {
    val s by EarnRepo.state.collectAsState()
    var open by remember { mutableStateOf(setOf("salary")) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    fun toggle(id: String) {
        open = if (id in open) open - id else open + id
    }

    // —— 流水明细 ——
    var startKey by remember { mutableStateOf("") }
    var endKey by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var newEarned by remember { mutableStateOf("") }
    var newOT by remember { mutableStateOf("") }
    var ledgerHint by remember { mutableStateOf("") }

    // —— 隐私 ——
    var showPinSetup by remember { mutableStateOf(false) }
    var showPinUnlock by remember { mutableStateOf(false) }
    var showPinVerify by remember { mutableStateOf(false) }
    var showPinOld by remember { mutableStateOf(false) }
    var showPinNew by remember { mutableStateOf(false) }
    var pendingOldPin by remember { mutableStateOf("") }
    var privacyError by remember { mutableStateOf("") }

    // —— 工时校正 ——
    var hoursInput by remember { mutableStateOf("") }
    var correctHint by remember { mutableStateOf("") }

    // —— 灵动岛检测 ——
    var probe by remember { mutableStateOf<IslandReport?>(null) }
    var probing by remember { mutableStateOf(false) }
    var probeHint by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { probe = IslandDoctor.probe(ctx, postTest = false) }
    }

    if (showPinSetup) {
        PinDialog(
            title = "开启隐私模式",
            message = "设一个 4 位数字密码。之后工资数字只显示百分比，临时查看要输密码（60 秒后自动恢复）。",
            confirmText = "开启",
            onDismiss = { showPinSetup = false },
            onConfirm = { pin ->
                val err = EarnRepo.enablePrivacy(pin)
                if (err == null) {
                    showPinSetup = false
                    privacyError = ""
                } else privacyError = err
            },
        )
    }
    if (showPinUnlock) {
        PinDialog(
            title = "临时查看",
            message = "输入 4 位密码，60 秒后自动恢复百分比显示",
            confirmText = "解锁",
            verify = { EarnRepo.verifyPin(it) },
            onDismiss = { showPinUnlock = false },
            onConfirm = {
                EarnRepo.unlockTemporarily(60)
                showPinUnlock = false
            },
        )
    }
    if (showPinVerify) {
        PinDialog(
            title = "开启隐私模式",
            message = "输入当前密码以开启隐私模式",
            confirmText = "开启",
            verify = { EarnRepo.verifyPin(it) },
            onDismiss = { showPinVerify = false },
            onConfirm = {
                EarnRepo.enablePrivacyWithExistingPin()
                showPinVerify = false
                privacyError = ""
            },
        )
    }
    if (showPinOld) {
        PinDialog(
            title = "修改密码",
            message = "输入当前 4 位密码",
            confirmText = "下一步",
            verify = { EarnRepo.verifyPin(it) },
            onDismiss = { showPinOld = false },
            onConfirm = {
                pendingOldPin = it
                showPinOld = false
                showPinNew = true
            },
        )
    }
    if (showPinNew) {
        PinDialog(
            title = "设置新密码",
            message = "设一个新的 4 位数字密码",
            confirmText = "修改",
            onDismiss = { showPinNew = false },
            onConfirm = { pin ->
                val err = EarnRepo.changePin(pendingOldPin, pin)
                if (err == null) {
                    showPinNew = false
                    privacyError = ""
                } else privacyError = err
            },
        )
    }

    val rows = s.dayLogRows(startKey, endKey)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimen.PageH),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("设置", color = XC.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.width(8.dp))
            Text("v${BuildConfig.VERSION_NAME}", color = XC.textFaint, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            SmallChip(if (open.size == ALL_GROUPS.size) "全部折叠" else "全部展开") {
                open = if (open.size == ALL_GROUPS.size) emptySet() else ALL_GROUPS.toSet()
            }
        }
        Spacer(Modifier.height(14.dp))

        // ══════════════ Your Money ══════════════
        SectionLabel("Your Money", "薪酬进度 · 只作用于薪酬卡与薪酬页")

        SettingGroup(
            title = "薪酬与工作天数",
            summary = if (s.privacyActive) "已隐藏"
            else if (s.salary > 0) "¥${fmt(s.salary, 0)}/月 · ${s.workDays} 天" else "未设置",
            open = "salary" in open,
            onToggle = { toggle("salary") },
        ) {
            Field("每月税后工资", s.salaryText, { EarnRepo.setSalary(it) }, "元", KeyboardType.Number, "输入每月税后工资")
            Spacer(Modifier.height(9.dp))
            Field(
                "每月工作天数",
                s.workDaysText,
                { EarnRepo.setWorkDays(it) },
                if (s.isAutoDays) "自动 ${s.autoDays} 天" else "天",
                KeyboardType.Number,
                "留空即按规则自动推算",
            )
            Spacer(Modifier.height(8.dp))
            Hint("留空按「每周工作日 + 法定节假日」实时算，本月 ${s.autoDays} 天；手填数字优先。")
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatBox("日薪", if (s.privacyActive) "--" else "¥${fmt(s.ratePerDay)}", Modifier.weight(1f))
                MiniStatBox("时薪", if (s.privacyActive) "--" else "¥${fmt(s.ratePerHour)}", Modifier.weight(1f))
                MiniStatBox("每秒", if (s.privacyActive) "--" else "¥${fmt(s.ratePerSec, 4)}", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "每周工作日",
            summary = "周${s.customWeekdays.sorted().joinToString("") { dow -> "一二三四五六日"[dow - 1].toString() }} · ${
                if (s.useLegalHolidays) "含法定节假日" else "只按周几"
            }",
            open = "workdays" in open,
            onToggle = { toggle("workdays") },
        ) {
            WeekdayPicker(s.customWeekdays) { EarnRepo.toggleWeekday(it) }
            Spacer(Modifier.height(11.dp))
            ToggleRow(
                "叠加法定节假日 / 调休补班",
                "关掉后只按上面选的周几算，适合排班制、大小周",
                s.useLegalHolidays,
            ) { EarnRepo.setUseLegalHolidays(it) }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("恢复默认（周一~周五）") { EarnRepo.resetWeekdays() }
            }
            Spacer(Modifier.height(8.dp))
            Hint("改完立刻生效：日薪、时薪、本月工时进度都会按新口径重算。")
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "工作时间 · 自动打卡",
            summary = "${s.workStart}–${s.workEnd} · 实际 ${fmt(s.effectiveHours, 1)}h/天",
            open = "time" in open,
            onToggle = { toggle("time") },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) { Field("上班", s.workStart, { EarnRepo.setWorkStart(it) }, "", KeyboardType.Text) }
                Box(Modifier.weight(1f)) { Field("下班", s.workEnd, { EarnRepo.setWorkEnd(it) }, "", KeyboardType.Text) }
                Box(Modifier.weight(0.9f)) { Field("休息", s.breakMin, { EarnRepo.setBreakMin(it) }, "分", KeyboardType.Number) }
            }
            Spacer(Modifier.height(8.dp))
            Hint("实际计费 ${fmt(s.effectiveHours, 1)} 小时/天 · 支持跨零点班次（如 22:00–06:00）")
            Spacer(Modifier.height(11.dp))
            ToggleRow("到点自动下班", "下班时间一到自动停表；加班段落不会被切断", s.autoClockOut) {
                EarnRepo.setAutoClockOut(it)
            }
            Spacer(Modifier.height(9.dp))
            ToggleRow(
                "到点自动上班",
                "上班时间一到自动打卡计费。靠系统闹钟触发，App 不开也生效",
                s.autoClockIn,
            ) {
                EarnRepo.setAutoClockIn(it)
                com.haofan.xiaoxinxin.core.AutoPunch.reschedule(ctx)
            }
            if (s.autoClockIn) {
                Spacer(Modifier.height(8.dp))
                Hint("Android 12+ 首次开启会引导授予「闹钟和提醒」；未授予也能用，触发时刻可能有 ±10 分钟误差。错过的时间不补打。")
            }
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "薪酬进度口径",
            summary = ProgressMetric.of(s.progressMetric).title,
            open = "metric" in open,
            onToggle = { toggle("metric") },
        ) {
            ProgressMetric.salaryCases.forEach { m ->
                val sel = m.key == s.progressMetric
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) XC.gold.copy(alpha = 0.10f) else Color.Transparent)
                        .liquidPress(
                            onClick = { EarnRepo.setProgressMetric(m.key) },
                            shape = RoundedCornerShape(10.dp),
                            weight = PressWeight.Row,
                        )
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(if (sel) XC.gold else XC.trackFill),
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Text("✓", color = XC.onGold, fontSize = 9.sp) }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, color = XC.text, fontSize = 12.5.sp)
                        Spacer(Modifier.height(1.dp))
                        Text(m.detail, color = XC.textFaint, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Hint("隐私模式开启时，无论选哪一档都统一显示工时百分比。")
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "薪酬页模块",
            summary = "${MoneyChip.parse(s.hiddenMoneyChips).size}/${MoneyChip.entries.size} 显示",
            open = "moneyChips" in open,
            onToggle = { toggle("moneyChips") },
        ) {
            MoneyChip.entries.forEach { c ->
                ChipToggleRow(c.title, c.detail, c.key !in s.hiddenMoneyChips) { on ->
                    EarnRepo.setChipVisible(DetailMode.SALARY, c.key, on)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("全部显示") { EarnRepo.hideAllChips(DetailMode.SALARY, false) }
                SmallChip("全部隐藏") { EarnRepo.hideAllChips(DetailMode.SALARY, true) }
            }
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "工时校正",
            summary = if (s.privacyActive) "已隐藏" else "今日 ${fmt(s.workedSeconds() / 3600, 2)} 小时",
            open = "correct" in open,
            onToggle = { toggle("correct") },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Field(
                        "今日已工作",
                        hoursInput,
                        { hoursInput = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        "小时",
                        KeyboardType.Number,
                        fmt(s.workedSeconds() / 3600, 2),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Box(
                    Modifier
                        .height(46.dp)
                        .width(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim)))
                        .liquidPress(
                            onClick = {
                                val h = hoursInput.toDoubleOrNull()
                                correctHint = if (h == null || h < 0) "请输入 0 以上的小时数"
                                else {
                                    EarnRepo.correctWork(h)
                                    hoursInput = ""
                                    "已校正为 ${fmt(h, 2)} 小时"
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text("应用", color = XC.onGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
            if (correctHint.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FormHint(correctHint, ok = correctHint.startsWith("已"))
            }
            Spacer(Modifier.height(8.dp))
            Hint("忘打卡、中途离开时用：直接把今日累计工时设成这个数，金额按费率同步对齐。")
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "流水明细",
            summary = if (rows.isEmpty()) "暂无记录" else "${rows.size} 条",
            open = "ledger" in open,
            onToggle = { toggle("ledger") },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) { Field("起始日期", startKey, { startKey = it }, "yyyy-MM-dd", KeyboardType.Text) }
                Box(Modifier.weight(1f)) { Field("结束日期", endKey, { endKey = it }, "yyyy-MM-dd", KeyboardType.Text) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallChip("本月") {
                    val now = LocalDate.now()
                    startKey = dateKey(now.withDayOfMonth(1))
                    endKey = dateKey(now)
                }
                SmallChip("清空") { startKey = ""; endKey = "" }
                Spacer(Modifier.weight(1f))
                Text("${rows.size} 条", color = XC.textFaint, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(9.dp))
            if (rows.isEmpty()) {
                Hint("这个区间还没有记录。今日数据以实时打卡为准，不入流水。")
            } else {
                rows.take(20).forEach { (key, v) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(key, color = XC.textDim, fontSize = 11.5.sp, fontFamily = NumFont)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (s.privacyActive) "--" else "¥${fmt(v.earned)}",
                            color = XC.gold,
                            fontSize = 12.5.sp,
                            fontFamily = NumFont,
                        )
                        if (v.otSeconds > 0) {
                            Spacer(Modifier.width(7.dp))
                            Text("加班 ${(v.otSeconds / 60).toInt()} 分", color = XC.orange, fontSize = 10.5.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "删除",
                            color = XC.danger,
                            fontSize = 10.5.sp,
                            modifier = Modifier
                                .clickable { EarnRepo.removeDayLog(key) }
                                .padding(4.dp),
                        )
                    }
                }
                if (rows.size > 20) {
                    Spacer(Modifier.height(4.dp))
                    Hint("只显示最近 20 条，收窄日期区间可看更早的记录。")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("手动补记", color = XC.textDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.4f)) { Field("日期", newKey, { newKey = it }, "yyyy-MM-dd", KeyboardType.Text) }
                Box(Modifier.weight(1f)) { Field("金额", newEarned, { newEarned = it }, "元", KeyboardType.Number) }
                Box(Modifier.weight(0.9f)) { Field("加班", newOT, { newOT = it }, "分", KeyboardType.Number) }
            }
            Spacer(Modifier.height(9.dp))
            PrimaryButton("写入流水", height = 42) {
                val err = EarnRepo.insertDayLog(
                    newKey.trim(),
                    newEarned.toDoubleOrNull() ?: 0.0,
                    newOT.toDoubleOrNull() ?: 0.0,
                )
                if (err == null) {
                    ledgerHint = "已补记 ${newKey.trim()}"
                    newKey = ""; newEarned = ""; newOT = ""
                } else ledgerHint = err
            }
            if (ledgerHint.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FormHint(ledgerHint, ok = ledgerHint.startsWith("已"))
            }
        }

        // ══════════════ Your Days ══════════════
        Spacer(Modifier.height(14.dp))
        SectionLabel("Your Days", "倒计时 · 纪念日 · 与薪酬数据完全独立")

        SettingGroup(
            title = "日期倒计时",
            summary = "${s.countdowns.size} 个 · ${s.activeCountdown?.name ?: "未设置"}",
            open = "countdown" in open,
            onToggle = { toggle("countdown") },
        ) {
            if (s.countdowns.isEmpty()) {
                Hint("还没有倒计时。进倒计时页 → 事件管理 → 新增，建一个即可。")
            } else {
                s.countdowns.forEach { c ->
                    val active = c.id == s.activeCountdownId
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(if (active) XC.gold else XC.trackFill)
                                .clickable { EarnRepo.setActiveCountdown(c.id) },
                            contentAlignment = Alignment.Center,
                        ) { if (active) Text("✓", color = XC.onGold, fontSize = 9.sp) }
                        Spacer(Modifier.width(9.dp))
                        Text(c.name, color = XC.text, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            "删除",
                            color = XC.danger,
                            fontSize = 10.5.sp,
                            modifier = Modifier.clickable { EarnRepo.deleteCountdown(c.id) }.padding(4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            GhostButton("去倒计时页新增 / 编辑") { onOpenDetail(DetailMode.COUNTDOWN) }
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "倒计时页模块",
            summary = "${CountdownChip.parse(s.hiddenCountdownChips).size}/${CountdownChip.entries.size} 显示",
            open = "chips" in open,
            onToggle = { toggle("chips") },
        ) {
            CountdownChip.entries.forEach { c ->
                ChipToggleRow(c.title, c.detail, c.key !in s.hiddenCountdownChips) { on ->
                    EarnRepo.setChipVisible(DetailMode.COUNTDOWN, c.key, on)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("全部显示") { EarnRepo.hideAllChips(DetailMode.COUNTDOWN, false) }
                SmallChip("全部隐藏") { EarnRepo.hideAllChips(DetailMode.COUNTDOWN, true) }
            }
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "纪念日",
            summary = "${s.recordDays.size} 个 · ${s.activeRecordDay?.name ?: "未设置"}",
            open = "recordDay" in open,
            onToggle = { toggle("recordDay") },
        ) {
            if (s.recordDays.isEmpty()) {
                Hint("还没有纪念日。进纪念日页 → 事件管理 → 新增，建一个即可。")
            } else {
                s.recordDays.forEach { r ->
                    val active = r.id == s.activeRecordDayId
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(if (active) XC.gold else XC.trackFill)
                                .clickable { EarnRepo.setActiveRecordDay(r.id) },
                            contentAlignment = Alignment.Center,
                        ) { if (active) Text("✓", color = XC.onGold, fontSize = 9.sp) }
                        Spacer(Modifier.width(9.dp))
                        Text(r.name, color = XC.text, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            "删除",
                            color = XC.danger,
                            fontSize = 10.5.sp,
                            modifier = Modifier.clickable { EarnRepo.deleteRecordDay(r.id) }.padding(4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            GhostButton("去纪念日页新增 / 编辑") { onOpenDetail(DetailMode.RECORD_DAY) }
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "纪念日页模块",
            summary = "${RecordDayChip.parse(s.hiddenRecordChips).size}/${RecordDayChip.entries.size} 显示",
            open = "recordChips" in open,
            onToggle = { toggle("recordChips") },
        ) {
            RecordDayChip.entries.forEach { c ->
                ChipToggleRow(c.title, c.detail, c.key !in s.hiddenRecordChips) { on ->
                    EarnRepo.setChipVisible(DetailMode.RECORD_DAY, c.key, on)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("全部显示") { EarnRepo.hideAllChips(DetailMode.RECORD_DAY, false) }
                SmallChip("全部隐藏") { EarnRepo.hideAllChips(DetailMode.RECORD_DAY, true) }
            }
        }

        // ══════════════ 通用 ══════════════
        Spacer(Modifier.height(14.dp))
        SectionLabel("通用", "主题 · 布局 · 通知 · 隐私")

        SettingGroup(
            title = "主题与配色",
            summary = AppTheme.of(s.themeId).label,
            open = "theme" in open,
            onToggle = { toggle("theme") },
        ) {
            ThemePicker(current = s.themeId) { EarnRepo.setTheme(it) }
            Spacer(Modifier.height(8.dp))
            Hint(AppTheme.of(s.themeId).detail)
            Spacer(Modifier.height(11.dp))
            Text("背景不透明度 ${(s.themeAlpha * 100).toInt()}%", color = XC.textDim, fontSize = 12.sp)
            GlassSlider(
                value = s.themeAlpha.toFloat(),
                onValueChange = { EarnRepo.setThemeAlpha(it.toDouble()) },
                valueRange = 0.15f..1f,
            )
            if (AppTheme.of(s.themeId) == AppTheme.LIQUID_GLASS) {
                Hint("Liquid Glass 调低能透出背景；不支持实时模糊的机型建议保持在 58% 以上。")
            } else {
                Hint("纯色主题调低也能透出背后光斑，玩出层次；不想透就拉回 100%。")
            }
            Spacer(Modifier.height(11.dp))
            ThemeColorRow(
                title = "背景色",
                current = s.themeBg,
                swatches = THEME_BG_SWATCHES,
                onPick = { EarnRepo.setThemeBg(it) },
                onReset = { EarnRepo.setThemeBg("") },
            )
            Spacer(Modifier.height(11.dp))
            ThemeColorRow(
                title = "文本色",
                current = s.themeText,
                swatches = THEME_TEXT_SWATCHES,
                onPick = { EarnRepo.setThemeText(it) },
                onReset = { EarnRepo.setThemeText("") },
            )
            Spacer(Modifier.height(11.dp))
            Text("重点色", color = XC.textDim, fontSize = 12.sp)
            Spacer(Modifier.height(7.dp))
            AccentPicker(current = s.themeAccent, onPick = { EarnRepo.setThemeAccent(it) })
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "首页布局",
            summary = "${HomeCard.parse(s.hiddenHomeCards).size} 张卡显示",
            open = "layout" in open,
            onToggle = { toggle("layout") },
        ) {
            HomeCard.entries.forEach { c ->
                ChipToggleRow(c.title, c.detail, c.key !in s.hiddenHomeCards) { on ->
                    EarnRepo.setHomeCardVisible(c.key, on)
                }
            }
            Spacer(Modifier.height(6.dp))
            Hint("至少保留一张进度卡；全关时会自动保留薪酬卡。")
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "首页按钮",
            summary = "${HomeButton.entries.size - s.hiddenButtons.size} 个显示",
            open = "buttons" in open,
            onToggle = { toggle("buttons") },
        ) {
            HomeButton.entries.forEach { b ->
                ChipToggleRow(b.title, b.detail, b.key !in s.hiddenButtons) { on ->
                    EarnRepo.setButtonVisible(b.key, on)
                }
            }
            Spacer(Modifier.height(6.dp))
            Hint("隐藏只是不显示，功能入口在二级页 / 设置里都还在。")
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "状态栏与通知",
            summary = "${s.statusBarKinds.size} 项 · ${if (s.statusBarRotate) "轮播" else "固定"}",
            open = "statusbar" in open,
            onToggle = { toggle("statusbar") },
        ) {
            Text("显示内容", color = XC.textDim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            StatusBarKind.entries.forEach { k ->
                ChipToggleRow("[${k.group}] ${k.title}", k.detail, k.key in s.statusBarKinds) {
                    EarnRepo.toggleBarKind(k)
                }
            }
            Spacer(Modifier.height(8.dp))
            Hint("至少保留一项。多选时可开启轮播：通知栏、悬浮药丸、系统岛（灵动岛）按同一节奏轮换显示。")
            Spacer(Modifier.height(6.dp))
            Hint("这三处配色跟随 App 当前主题（含自定义颜色与透明度），换主题立即换装。")
            Spacer(Modifier.height(10.dp))
            ToggleRow("多选时轮播", "按上面的顺序循环显示", s.statusBarRotate) {
                EarnRepo.setStatusBarRotate(it)
            }
            if (s.statusBarRotate) {
                Spacer(Modifier.height(8.dp))
                Field(
                    "轮播间隔（秒）",
                    s.statusBarRotateSeconds.toString(),
                    { EarnRepo.setStatusBarRotateSeconds(it.toIntOrNull() ?: 5) },
                    "秒",
                    KeyboardType.Number,
                )
            }
            Spacer(Modifier.height(10.dp))
            Field("通知栏金额前缀", s.barSymbol, { EarnRepo.setBarSymbol(it) }, "可留空", KeyboardType.Text)
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                "悬浮窗",
                "自己画的顶部药丸：可拖动、贴边吸附、点开看今日和本月。任何机型都能用",
                s.floatEnabled,
            ) {
                EarnRepo.setFloatEnabled(it)
                FloatService.applyToggle(ctx)
            }
            Spacer(Modifier.height(9.dp))
            val checking = probe == null && IslandDoctor.platformCapable()
            val islandOk = probe?.canPromote == true
            ToggleRow(
                "灵动岛 / 实况通知",
                when {
                    checking -> "正在检测系统支持…"
                    islandOk -> "交给系统渲染的实时通知，能进状态栏、锁屏和通知栏置顶"
                    else -> IslandDoctor.brandHint()
                },
                s.islandEnabled,
                enabled = islandOk,
            ) {
                EarnRepo.setIslandEnabled(it)
                FloatService.applyToggle(ctx)
            }
            if (!checking && !islandOk) {
                Spacer(Modifier.height(7.dp))
                Hint("这个开关灰掉不是 bug：系统没开放标准实时通知接口，改设置也打不开。上面的悬浮窗就是替代方案。")
            }
            Spacer(Modifier.height(10.dp))
            val r = probe
            if (r != null) {
                Text(r.verdict, color = if (r.promoted) XC.teal else XC.orange, fontSize = 11.5.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChip(if (probing) "检测中…" else "发一条测试岛通知") {
                        if (probing) return@SmallChip
                        probing = true
                        probeHint = ""
                        scope.launch {
                            val res = withContext(Dispatchers.IO) { IslandDoctor.probe(ctx, postTest = true) }
                            probe = res
                            probing = false
                            probeHint = if (res.promoted) "去看状态栏顶部 / 锁屏，应该出现胶囊了"
                            else "系统没提升。这属于厂商白名单，只能走悬浮岛"
                        }
                    }
                    SmallChip("复制报告") {
                        runCatching {
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("小薪薪体检", r.asText()))
                            probeHint = "已复制，可以直接发给我"
                        }
                    }
                }
                if (probeHint.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Hint(probeHint, XC.teal)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "隐私模式",
            summary = if (s.privacyEnabled) (if (s.privacyActive) "已开启 · 只显示百分比" else "临时查看中")
            else "未开启",
            open = "privacy" in open,
            onToggle = { toggle("privacy") },
        ) {
            ToggleRow(
                "开启隐私模式",
                "工资数字全部换成百分比进度：8 小时 = 100%，加班 4 小时 → 150%",
                s.privacyEnabled,
            ) { on ->
                if (on) {
                    if (s.hasPin) {
                        showPinVerify = true
                    } else {
                        showPinSetup = true
                    }
                } else {
                    EarnRepo.disablePrivacy()
                }
            }
            if (s.privacyEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.privacyActive) {
                        SmallChip("临时查看（60 秒）") { showPinUnlock = true }
                    } else {
                        SmallChip("立即恢复隐藏") { EarnRepo.relock() }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Hint("隐私模式同时作用于首页卡片、薪酬页、悬浮岛和通知栏；倒计时与纪念日本身没有金额，不受影响。")
            }
            // 有密码就允许改 —— 不论隐私当前是否开启（对齐 macOS「修改密码」）
            if (s.hasPin) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChip("修改密码") { showPinOld = true }
                }
            }
            if (privacyError.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FormHint(privacyError)
            }
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "外观与图标",
            summary = if (s.floatMini) "迷你条形态" else "药丸形态",
            open = "look" in open,
            onToggle = { toggle("look") },
        ) {
            ToggleRow(
                "悬浮岛用迷你条",
                "只显示金额，宽度减半，不挡内容。关掉是完整药丸（金额 + 进度 + 本月）",
                s.floatMini,
            ) { EarnRepo.setFloatMini(it); FloatService.applyToggle(ctx) }
            Spacer(Modifier.height(9.dp))
            Hint("应用图标、启动动画固定为品牌蓝 ¥ 字样，不单独提供切换 —— 保证桌面图标在任何主题下都认得出来。")
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "启动与数据",
            summary = if (s.autoBoot) "开机恢复闹钟" else "仅手动打开",
            open = "window" in open,
            onToggle = { toggle("window") },
        ) {
            ToggleRow(
                "重启后恢复自动上班",
                "手机重启会把闹钟清掉，打开这个开关可在开机后自动恢复上面的到点打卡",
                s.autoBoot,
            ) {
                EarnRepo.setAutoBoot(it)
                if (it) com.haofan.xiaoxinxin.core.AutoPunch.reschedule(ctx)
            }
            Spacer(Modifier.height(9.dp))
            Hint("本应用不需要开机常驻 —— 金额靠打卡时刻现算，进程被杀数字也不会丢。「自启动」只做一件事：重启后把自动上班的闹钟排回去。小米 / 华为等深度定制系统可能拦截开机广播，被拦时打开一次 App 即可恢复。")
            Spacer(Modifier.height(10.dp))
            Hint("数据全部保存在本机 SharedPreferences，App 不联网、不上传、不申请定位与通讯录权限。")
        }
        Spacer(Modifier.height(10.dp))

        SettingGroup(
            title = "版本与版权",
            summary = "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
            open = "about" in open,
            onToggle = { toggle("about") },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("开发者", color = XC.textFaint, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Text(DEV_NAME, color = XC.gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(DEV_TAGLINE, color = XC.textFaint, fontSize = 10.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                color = XC.gold,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NumFont,
            )
            Spacer(Modifier.height(3.dp))
            Text("© 2026 $DEV_NAME. All rights reserved.", color = XC.textFaint, fontSize = 10.5.sp)
            Spacer(Modifier.height(3.dp))
            Text(REPO_URL, color = XC.textFaint, fontSize = 10.5.sp, fontFamily = NumFont)
            Spacer(Modifier.height(7.dp))
            Text(
                "Sony 30th 主题灵感来自 PlayStation 30 周年，相关标识归 Sony Interactive Entertainment 所有。",
                color = XC.textFaint,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text("版本记录", color = XC.textDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            RELEASE_HISTORY.forEach { (v, d, note) ->
                Column(Modifier.padding(vertical = 3.dp)) {
                    Text("v$v · $d", color = XC.gold, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(note, color = XC.textFaint, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "$DEV_NAME · DEVELOPER",
            color = XC.textFaint,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------- 局部控件 ----------------

@Composable
private fun MiniStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(XC.fill)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = XC.text, fontSize = 12.sp, fontFamily = NumFont, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = XC.textFaint, fontSize = 9.5.sp)
    }
}

/** 周一到周日七个小格，点一下切换是否计薪 */
@Composable
private fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (1..7).forEach { dow ->
            val sel = dow in selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) XC.gold.copy(alpha = 0.14f) else XC.fill)
                    .border(
                        if (sel) 1.2.dp else 0.8.dp,
                        if (sel) XC.gold.copy(alpha = 0.7f) else XC.line,
                        RoundedCornerShape(10.dp),
                    )
                    .liquidPress(onClick = { onToggle(dow) }, shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "一二三四五六日"[dow - 1].toString(),
                    color = if (sel) XC.gold else XC.textFaint,
                    fontSize = 12.5.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 四套主题卡片 */
@Composable
private fun ThemePicker(current: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTheme.entries.forEach { t ->
            val sel = t.id == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) XC.gold.copy(alpha = 0.10f) else XC.fill)
                    .border(
                        if (sel) 1.3.dp else 0.8.dp,
                        if (sel) XC.gold.copy(alpha = 0.75f) else XC.lineStrong,
                        RoundedCornerShape(12.dp),
                    )
                    .liquidPress(onClick = { onPick(t.id) }, shape = RoundedCornerShape(12.dp))
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier
                        .height(18.dp)
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(5.dp))
                        .border(0.8.dp, XC.lineStrong, RoundedCornerShape(5.dp)),
                ) {
                    t.swatches.forEach { c -> Box(Modifier.weight(1f).fillMaxHeight().background(c)) }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    t.label,
                    color = if (sel) XC.gold else XC.textDim,
                    fontSize = 9.5.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

/** 色卡无极调节：9 个常用色 + 恢复预设 */
@Composable
private fun AccentPicker(current: String, onPick: (String) -> Unit) {
    val presets = listOf(
        "41,133,230" to Color(0xFF2985E6),
        "26,173,128" to Color(0xFF1AAD80),
        "214,0,20" to Color(0xFFD60014),
        "230,86,43" to Color(0xFFE6562B),
        "232,160,26" to Color(0xFFE8A01A),
        "123,92,255" to Color(0xFF7B5CFF),
        "18,168,160" to Color(0xFF12A8A0),
        "217,115,191" to Color(0xFFD973BF),
        "71,79,92" to Color(0xFF474F5C),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        presets.forEach { (rgb, c) ->
            val sel = current == rgb
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c)
                    .border(
                        if (sel) 2.dp else 0.8.dp,
                        if (sel) XC.text else XC.lineStrong,
                        RoundedCornerShape(8.dp),
                    )
                    .liquidPress(onClick = { onPick(rgb) }, shape = RoundedCornerShape(8.dp)),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallChip("用主题自带重点色", selected = current.isEmpty()) { onPick("") }
    }
}

/** 背景色预设：白 / 浅灰 / 深色 / 薄荷浅 / 天蓝浅 / 索尼灰（首项是「用预设」） */
private val THEME_BG_SWATCHES = listOf(
    "" to Color(0xFFFFFFFF),
    "245,246,248" to Color(0xFFF5F6F8),
    "26,28,33" to Color(0xFF1A1C21),
    "236,247,243" to Color(0xFFECF7F3),
    "235,243,251" to Color(0xFFEBF3FB),
    "209,212,214" to Color(0xFFD1D4D6),
)

/** 文本色预设：深 / 浅 / 灰（首项是「用预设」） */
private val THEME_TEXT_SWATCHES = listOf(
    "" to Color(0xFF14191F),
    "235,238,242" to Color(0xFFEBEEF2),
    "71,79,92" to Color(0xFF474F5C),
)

/** 背景 / 文本色无极调：一排预设色卡 + 恢复预设（对齐 macOS 的 ColorPicker） */
@Composable
private fun ThemeColorRow(
    title: String,
    current: String,
    swatches: List<Pair<String, Color>>,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
) {
    Text(title, color = XC.textDim, fontSize = 12.sp)
    Spacer(Modifier.height(7.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        swatches.forEach { (rgb, c) ->
            val sel = current == rgb
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c)
                    .border(
                        if (sel) 2.dp else 0.8.dp,
                        if (sel) XC.text else XC.lineStrong,
                        RoundedCornerShape(8.dp),
                    )
                    .liquidPress(onClick = { onPick(rgb) }, shape = RoundedCornerShape(8.dp)),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallChip("用主题自带色", selected = current.isEmpty()) { onReset() }
    }
}
