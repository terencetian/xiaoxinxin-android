package com.haofan.xiaoxinxin.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 一天的历史流水。 */
data class DayLog(val earned: Double, val otSeconds: Double)

/** 2.0 三个二级页：Your Money 薪酬 / Your Days 倒计时 / Your Days 纪念日 */
enum class DetailMode(val key: String) {
    SALARY("salary"), COUNTDOWN("countdown"), RECORD_DAY("recordDay");

    val module: String get() = if (this == SALARY) "Your Money" else "Your Days"
    val title: String
        get() = when (this) {
            SALARY -> "薪酬进度"
            COUNTDOWN -> "倒计时"
            RECORD_DAY -> "纪念日"
        }

    companion object {
        fun of(key: String): DetailMode = entries.firstOrNull { it.key == key } ?: SALARY
    }
}

/**
 * 进度条统一数据源：所有统计口径都算出这一份，首页卡片 / 二级页 / 悬浮岛 / 通知共用。
 * 与 macOS ProgressInfo 字段一致。
 */
data class ProgressInfo(
    /** 原值，加班可 > 1 */
    val ratio: Double = 0.0,
    /** 条内填充 0...1 */
    val fill: Double = 0.0,
    /** 超出 100% 的部分（0...1，画第二段） */
    val overflow: Double = 0.0,
    /** 大字主段 */
    val leading: String = "¥0.00",
    /** 大字小字段（如「 天」） */
    val trailing: String = "",
    val caption: String = "今日已到账",
    val footer: String = "",
    val isMoney: Boolean = true,
    val privacy: Boolean = false,
    val metric: ProgressMetric = ProgressMetric.TODAY_SALARY,
    val mode: DetailMode = DetailMode.SALARY,
) {
    val hasData: Boolean get() = leading.isNotEmpty() && leading != "--"
    /** 拆成「条内填充 + 溢出段」两个比例，UI 直接画 */
    val overflowRatio: Double get() = (ratio - 1.0).coerceIn(0.0, 1.0)
}

/**
 * 全部可持久化状态。金额一律由「打卡时刻 + 当前时间」现算，
 * 不依赖定时器累加 —— 所以进程被冻结或杀死后恢复，数字依然准确。
 */
data class EarnState(
    // ── Your Money：计薪 ──────────────────────────────
    val salaryText: String = "",
    /** 手填每月工作天数；空 = 按工作日规则自动算 */
    val workDaysText: String = "",
    val workStart: String = "09:00",
    val workEnd: String = "18:00",
    val breakMin: String = "60",

    val clockInAt: Long? = null,           // epoch millis；null = 未计费
    val baseEarned: Double = 0.0,          // 已结束计费段的累计金额
    val workedBeforeSession: Double = 0.0, // 本日此前已工作秒数
    val overtimeSeconds: Double = 0.0,

    // ── 工作日规则（2.0）─────────────────────────────
    /** 每周计薪日，ISO 序号 1=周一 … 7=周日 */
    val customWeekdays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val useLegalHolidays: Boolean = true,

    // ── 自动打卡 / 启动 ──────────────────────────────
    val autoClockOut: Boolean = true,
    val autoClockIn: Boolean = false,
    val autoBoot: Boolean = false,

    // ── 悬浮岛 / 系统通知 ─────────────────────────────
    val floatEnabled: Boolean = false,
    val islandEnabled: Boolean = false,
    val floatX: Int = -1,                  // -1 表示未定位，首次贴顶居中
    val floatY: Int = -1,
    val floatMini: Boolean = false,

    // ── 主题（与 macOS 2.0 对齐的四套预设）─────────────
    val themeId: String = "sky",
    /** 背景不透明度，对所有主题生效（macOS 同样全局可调） */
    val themeAlpha: Double = 1.0,
    /** 自定义重点色 "r,g,b"（0-255），空 = 用预设 */
    val themeAccent: String = "",
    /** 自定义背景色 "r,g,b"，空 = 用预设（macOS 的 ColorPicker 无极调） */
    val themeBg: String = "",
    /** 自定义文本色 "r,g,b"，空 = 用预设 */
    val themeText: String = "",

    // ── 进度口径与模块显隐 ────────────────────────────
    val progressMetric: String = ProgressMetric.TODAY_SALARY.key,
    val hiddenHomeCards: Set<String> = emptySet(),
    val hiddenMoneyChips: Set<String> = emptySet(),
    val hiddenCountdownChips: Set<String> = emptySet(),
    val hiddenRecordChips: Set<String> = emptySet(),
    val hiddenButtons: Set<String> = emptySet(),

    // ── 状态栏通知显示项（对应 macOS 菜单栏显示）────────
    val statusBarKinds: Set<String> = setOf(StatusBarKind.MONEY_TODAY.key),
    val statusBarRotate: Boolean = false,
    val statusBarRotateSeconds: Int = 5,
    val barSymbol: String = "",

    // ── 隐私模式 ─────────────────────────────────────
    val privacyEnabled: Boolean = false,
    val privacyUnlockedUntil: Long = 0L,
    val pinSalt: String = "",
    val pinHash: String = "",

    // ── Your Days：两份互相独立的事件表 ─────────────────
    val countdowns: List<CountdownItem> = emptyList(),
    val activeCountdownId: String = "",
    val recordDays: List<RecordDayItem> = emptyList(),
    val activeRecordDayId: String = "",

    // ── 流水与杂项 ───────────────────────────────────
    val dayLog: Map<String, DayLog> = emptyMap(),
    val dayKey: String = "",               // 本份数据所属日期，用于跨天重置
    val deviceId: String = "",             // 预留：日后云端同步的设备标识
    val schemaVersion: Int = 2,
    val onboardingDone: Boolean = false,
    val logText: String = "",              // 界面底部那行状态提示，不持久化
) {
    // ── 派生：工作日 ─────────────────────────────────
    val workdayRule: WorkdayRule
        get() = WorkdayRule(
            weekdays = if (customWeekdays.isEmpty()) setOf(1, 2, 3, 4, 5) else customWeekdays,
            useLegalHolidays = useLegalHolidays,
            manualDays = workDaysText.toIntOrNull()?.takeIf { it in 1..31 },
        )

    val autoDays: Int get() = monthWorkDays(LocalDate.now(), workdayRule.copy(manualDays = null))

    val salary: Double get() = salaryText.toDoubleOrNull() ?: 0.0

    val workDays: Int get() = workdayRule.manualDays ?: autoDays

    val isAutoDays: Boolean get() = workdayRule.manualDays == null

    val effectiveHours: Double
        get() = effectiveWorkHours(workStart, workEnd, (breakMin.toIntOrNull() ?: 60).toDouble())

    val ratePerSec: Double
        get() = if (workDays <= 0 || effectiveHours <= 0) 0.0 else salary / workDays / effectiveHours / 3600

    val ratePerHour: Double get() = ratePerSec * 3600
    val ratePerDay: Double get() = if (workDays <= 0) 0.0 else salary / workDays

    val working: Boolean get() = clockInAt != null

    // ── 派生：金额与工时 ─────────────────────────────
    fun displayEarned(at: Long = System.currentTimeMillis()): Double {
        val start = clockInAt ?: return baseEarned
        return baseEarned + (at - start).coerceAtLeast(0L) / 1000.0 * ratePerSec
    }

    fun workedSeconds(at: Long = System.currentTimeMillis()): Double {
        val start = clockInAt ?: return workedBeforeSession
        return workedBeforeSession + (at - start).coerceAtLeast(0L) / 1000.0
    }

    fun workedMinutes(at: Long = System.currentTimeMillis()): Int = (workedSeconds(at) / 60).toInt()

    private fun nowLocal(at: Long): LocalDateTime =
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDateTime()

    fun scheduledEnd(at: Long = System.currentTimeMillis()): LocalDateTime? =
        shiftEndTime(nowLocal(at), workStart, workEnd)

    fun scheduledStart(at: Long = System.currentTimeMillis()): LocalDateTime? =
        shiftStartTime(nowLocal(at), workStart, workEnd)

    /** 加班中：当前计费段是在计划下班之后才打卡的。 */
    fun overtimeNow(at: Long = System.currentTimeMillis()): Boolean {
        val start = clockInAt ?: return false
        val end = scheduledEnd(at) ?: return false
        return !nowLocal(start).isBefore(end)
    }

    val overtimeMinutes: Int get() = (overtimeSeconds / 60).toInt()

    /** 本月累计进账 = 今日实时 + 历史流水。 */
    fun monthEarned(at: Long = System.currentTimeMillis()): Double {
        val today = dateKey(nowLocal(at).toLocalDate())
        val prefix = today.take(7)
        var sum = displayEarned(at)
        for ((k, v) in dayLog) if (k.startsWith(prefix) && k != today) sum += v.earned
        return sum
    }

    /** 本月累计加班秒数 = 今日加班 + 历史流水。 */
    fun monthOTSeconds(at: Long = System.currentTimeMillis()): Double {
        val today = dateKey(nowLocal(at).toLocalDate())
        val prefix = today.take(7)
        var sum = overtimeSeconds
        for ((k, v) in dayLog) if (k.startsWith(prefix) && k != today) sum += v.otSeconds
        return sum
    }

    /** 本月已工作秒数：按金额反推（费率恒定，误差可忽略），用于本月工时进度。 */
    fun monthWorkedSeconds(at: Long = System.currentTimeMillis()): Double =
        if (ratePerSec <= 0) 0.0 else monthEarned(at) / ratePerSec

    // ── 派生：四种进度口径 ───────────────────────────
    val todayHoursRatio: Double get() = hoursProgress(workedSeconds(), effectiveHours)
    val monthHoursRatio: Double
        get() = hoursProgress(monthWorkedSeconds(), workDays.toDouble() * effectiveHours)
    val todayMoneyRatio: Double get() = if (ratePerDay <= 0) 0.0 else displayEarned() / ratePerDay
    val monthMoneyRatio: Double get() = if (salary <= 0) 0.0 else monthEarned() / salary

    /** 兼容旧接口：条内填充比例（封顶 1）。 */
    fun progress(at: Long = System.currentTimeMillis()): Double =
        if (ratePerDay <= 0) 0.0 else (displayEarned(at) / ratePerDay).coerceIn(0.0, 1.0)

    // ── 隐私 ────────────────────────────────────────
    val privacyActive: Boolean
        get() = privacyEnabled && privacyUnlockedUntil < System.currentTimeMillis()

    val hasPin: Boolean get() = pinHash.isNotEmpty() && pinSalt.isNotEmpty()

    /** 金额文案：隐私模式下统一变成 -- */
    fun displayMoney(v: Double, dec: Int = 2, prefix: String = "¥"): String =
        if (privacyActive) "--" else prefix + fmt(v, dec)

    // ── Your Days：当前事件 ───────────────────────────
    val activeCountdown: CountdownItem?
        get() = countdowns.firstOrNull { it.id == activeCountdownId } ?: countdowns.firstOrNull()

    val activeRecordDay: RecordDayItem?
        get() = recordDays.firstOrNull { it.id == activeRecordDayId } ?: recordDays.firstOrNull()

    /** 统一进度出口：首页三张卡 / 三个二级页 / 通知共用。 */
    fun progressInfo(mode: DetailMode): ProgressInfo {
        var info = ProgressInfo(
            privacy = mode == DetailMode.SALARY && privacyActive,
            metric = ProgressMetric.of(progressMetric),
            mode = mode,
        )
        val todayPct = percentText(todayHoursRatio)

        when (mode) {
            DetailMode.SALARY -> {
                if (privacyActive) {
                    // 隐私模式：工资数字一律换成百分比；8h=100%，加班 4h → 150%
                    info = info.copy(
                        ratio = todayHoursRatio,
                        isMoney = false,
                        leading = todayPct,
                        trailing = "",
                        caption = "今日进度 · 隐私模式",
                        footer = "今日 $todayPct · 本月 ${percentText(monthHoursRatio)}（加班计入超出 100% 的部分）",
                    )
                } else when (ProgressMetric.of(progressMetric)) {
                    ProgressMetric.MONTH_SALARY -> info = info.copy(
                        ratio = monthMoneyRatio,
                        leading = "¥" + fmt(monthEarned()),
                        caption = "今日已到账 · 本月累计 ¥${fmt(monthEarned())}",
                        footer = "本月进度 ${percentText(monthMoneyRatio)} · 距月薪还差 ¥${
                            fmt((salary - monthEarned()).coerceAtLeast(0.0))
                        }",
                    )

                    ProgressMetric.TODAY_HOURS -> info = info.copy(
                        ratio = todayHoursRatio,
                        isMoney = false,
                        leading = percentText(todayHoursRatio),
                        caption = "今日工时进度",
                        footer = "今日已到账 ¥${fmt(displayEarned())} · 计划 ${fmt(effectiveHours, 1)} 小时" +
                            if (todayHoursRatio > 1) " · 加班部分已超出 100%" else "",
                    )

                    ProgressMetric.MONTH_HOURS -> info = info.copy(
                        ratio = monthHoursRatio,
                        isMoney = false,
                        leading = percentText(monthHoursRatio),
                        caption = "本月工时进度",
                        footer = "本月累计 ¥${fmt(monthEarned())} · 应出勤 $workDays 天 × ${fmt(effectiveHours, 1)} 小时",
                    )

                    ProgressMetric.TODAY_SALARY -> {
                        val earned = displayEarned()
                        val overtime = overtimeNow()
                        info = info.copy(
                            ratio = todayMoneyRatio,
                            leading = "¥" + fmt(earned),
                            caption = when {
                                overtime -> "加班计费中 · 今日已到账"
                                working -> "正在进账 · 今日已到账"
                                else -> "今日已到账"
                            },
                            footer = when {
                                salary <= 0 -> "在设置里填写月薪后开始累计进度"
                                earned >= ratePerDay && ratePerDay > 0 ->
                                    "今日进度 ${percentText(todayMoneyRatio)} · 已超出全天 ¥${fmt(earned - ratePerDay)}"
                                else ->
                                    "今日进度 ${percentText(todayMoneyRatio)} · 距离全天还差 ¥${fmt((ratePerDay - earned).coerceAtLeast(0.0))}"
                            },
                        )
                    }
                }
            }

            DetailMode.COUNTDOWN -> {
                // 倒计时页与隐私模式无关：本身不含任何工资金额
                info = info.copy(isMoney = false)
                val item = activeCountdown
                val p = item?.let { countdownProgress(it) }
                info = if (item != null && p != null) {
                    val targetText = if (item.hasCustomTime) "${p.targetKey} ${item.targetTimeText}" else p.targetKey
                    info.copy(
                        ratio = p.ratio,
                        leading = if (item.mode == "percent") percentText(p.ratio) else "${p.daysLeft}",
                        trailing = if (item.mode == "percent") "" else " 天",
                        caption = if (p.expired) "${item.name} · 已到期"
                        else "距离 ${item.name} · $targetText",
                        footer = if (p.expired)
                            "倒计时已结束 · 累计 ${p.elapsedDays} 天 / 总跨度 ${p.totalDays} 天"
                        else "还有 ${p.daysLeft} 天 · 已走 ${percentText(p.ratio)} · 起点 ${item.startKeyText ?: "自动"}",
                    )
                } else {
                    info.copy(
                        ratio = 0.0,
                        leading = "--",
                        trailing = "",
                        caption = "还没有倒计时",
                        footer = "进本页 → 事件管理 → 新增，建一个即可",
                    )
                }
            }

            DetailMode.RECORD_DAY -> {
                // 纪念日同样与隐私无关；2.4 起主口径是「累计时长」，不再走里程碑进度
                info = info.copy(isMoney = false, ratio = 0.0)
                val item = activeRecordDay
                val p = item?.let { recordDayProgress(it) }
                info = if (item != null && p != null) {
                    info.copy(
                        leading = "${p.elapsedDays}",
                        trailing = " 天",
                        caption = if (p.started) "${item.name} · 已相伴" else "${item.name} · 尚未开始",
                        footer = if (p.started)
                            "从 ${p.startKey} 起 · 累计 ${p.span.shortText}"
                        else "起始日 ${p.startKey} 还没到，到那天开始累计",
                    )
                } else {
                    info.copy(
                        leading = "--",
                        trailing = "",
                        caption = "还没有纪念日",
                        footer = "进本页 → 事件管理 → 新增，建一个即可",
                    )
                }
            }
        }
        return info
    }

    val moneyInfo: ProgressInfo get() = progressInfo(DetailMode.SALARY)
    val countdownInfo: ProgressInfo get() = progressInfo(DetailMode.COUNTDOWN)
    val recordDayInfo: ProgressInfo get() = progressInfo(DetailMode.RECORD_DAY)

    /** 按日期闭区间查询流水（空串=不限制），降序返回，今日不在内。 */
    fun dayLogRows(start: String, end: String, at: Long = System.currentTimeMillis()): List<Pair<String, DayLog>> {
        val today = dateKey(nowLocal(at).toLocalDate())
        return dayLog.entries
            .filter { (start.isEmpty() || it.key >= start) && (end.isEmpty() || it.key <= end) }
            .filter { it.key != today }
            .sortedByDescending { it.key }
            .map { it.key to it.value }
    }
}

/** 状态栏 / 通知能显示的内容：MONEY 系 4 种、DAYS 系 2 种，可多选 + 轮播。 */
enum class StatusBarKind(val key: String, val group: String, val title: String, val short: String) {
    MONEY_TODAY("moneyToday", "MONEY", "今日已赚", "今日已赚"),
    MONEY_MONTH("moneyMonth", "MONEY", "本月累计", "本月累计"),
    MONEY_TODAY_PCT("moneyTodayPct", "MONEY", "今日进度", "今日%"),
    MONEY_MONTH_PCT("moneyMonthPct", "MONEY", "本月进度", "本月%"),
    DAYS_COUNTDOWN("daysCountdown", "DAYS", "倒计时剩余", "倒计时"),
    DAYS_RECORD("daysRecord", "DAYS", "纪念日累计", "纪念日"),
    ;

    val detail: String
        get() = when (this) {
            MONEY_TODAY -> "今日已到账金额（隐私模式自动显示百分比）"
            MONEY_MONTH -> "本月累计进账（隐私模式自动显示百分比）"
            MONEY_TODAY_PCT -> "今日工时进度，计划 8h = 100%"
            MONEY_MONTH_PCT -> "本月工时进度"
            DAYS_COUNTDOWN -> "当前倒计时还剩几天，过期显示「已到」"
            DAYS_RECORD -> "当前纪念日已经累计几天"
        }

    companion object {
        fun of(key: String): StatusBarKind? = entries.firstOrNull { it.key == key }
        fun parse(set: Set<String>): List<StatusBarKind> =
            entries.filter { it.key in set }.ifEmpty { listOf(MONEY_TODAY) }
    }
}

/** 一个显示项渲染出来的一段文案，供通知标题 / 悬浮岛副标题复用。 */fun statusBarText(s: EarnState, kind: StatusBarKind, at: Long = System.currentTimeMillis()): String =
    when (kind) {
        StatusBarKind.MONEY_TODAY ->
            if (s.privacyActive) "今日 ${percentText(s.todayHoursRatio, 0)}"
            else "今日 ${s.displayMoney(s.displayEarned(at))}"
        StatusBarKind.MONEY_MONTH ->
            if (s.privacyActive) "本月 ${percentText(s.monthHoursRatio, 0)}"
            else "本月 ${s.displayMoney(s.monthEarned(at))}"
        StatusBarKind.MONEY_TODAY_PCT -> "今日 ${percentText(s.todayHoursRatio, 0)}"
        StatusBarKind.MONEY_MONTH_PCT -> "本月 ${percentText(s.monthHoursRatio, 0)}"
        StatusBarKind.DAYS_COUNTDOWN -> s.activeCountdown?.let { item ->
            val p = countdownProgress(item)
            when {
                p == null -> "倒计时 --"
                p.expired -> "${item.name} 已到"
                else -> "${item.name} ${p.daysLeft} 天"
            }
        } ?: "倒计时 --"
        StatusBarKind.DAYS_RECORD -> s.activeRecordDay?.let { item ->
            val p = recordDayProgress(item)
            if (p == null) "纪念日 --" else "${item.name} ${p.elapsedDays} 天"
        } ?: "纪念日 --"
    }

/**
 * 悬浮岛 / 通知里一格内容的统一数据源（2.7）。
 * 6 种显示项共用这套结构：大字 value、说明 caption、副行 sub、进度 ratio。
 * 通知文案与悬浮药丸都从这里取，保证两处口径永远一致。
 */
data class WidgetCell(
    val label: String,     // 小标题：今日已赚 / 倒计时剩余 …
    val value: String,     // 大字主值：¥123.45 / 37 天 / 45%
    val caption: String,   // 说明行：距离 生日 · 2026-12-25
    val sub: String,       // 副行：已工作 2h 15m · 45%
    val ratio: Double,     // 进度条 0..1
    val isMoney: Boolean,  // 是否金额系（工作状态徽标只在金额系显示）
)

fun widgetCell(s: EarnState, kind: StatusBarKind, at: Long = System.currentTimeMillis()): WidgetCell {
    val mins = s.workedMinutes(at)
    val workTail = if (s.working) "已工作 ${mins / 60}h${mins % 60}m" else "未计费 · 点开打卡"
    return when (kind) {
        StatusBarKind.MONEY_TODAY -> WidgetCell(
            label = "今日已赚",
            value = if (s.privacyActive) percentText(s.todayHoursRatio, 0)
            else s.displayMoney(s.displayEarned(at)),
            caption = when {
                s.privacyActive -> "今日进度 · 隐私模式"
                s.overtimeNow(at) -> "加班计费中 · 今日已到账"
                s.working -> "正在进账 · 今日已到账"
                else -> "今日已到账"
            },
            sub = workTail + if (s.working) " · ${fmt(s.progress(at) * 100, 0)}%" else "",
            ratio = s.progress(at),
            isMoney = true,
        )

        StatusBarKind.MONEY_MONTH -> WidgetCell(
            label = "本月累计",
            value = if (s.privacyActive) percentText(s.monthMoneyRatio, 0)
            else s.displayMoney(s.monthEarned(at)),
            caption = if (s.privacyActive) "本月进度 · 隐私模式" else "本月累计进账",
            sub = "本月进度 ${percentText(s.monthMoneyRatio, 0)} · 应出勤 ${s.workDays} 天",
            ratio = s.monthMoneyRatio.coerceIn(0.0, 1.0),
            isMoney = true,
        )

        StatusBarKind.MONEY_TODAY_PCT -> WidgetCell(
            label = "今日进度",
            value = percentText(s.todayHoursRatio, 0),
            caption = "今日工时进度",
            sub = workTail + if (s.working) " · 计划 ${fmt(s.effectiveHours, 1)}h" else "",
            ratio = s.todayHoursRatio.coerceIn(0.0, 1.0),
            isMoney = true,
        )

        StatusBarKind.MONEY_MONTH_PCT -> WidgetCell(
            label = "本月进度",
            value = percentText(s.monthHoursRatio, 0),
            caption = "本月工时进度",
            sub = "本月 ${if (s.privacyActive) "--" else "¥" + fmt(s.monthEarned(at))} · 应出勤 ${s.workDays} 天",
            ratio = s.monthHoursRatio.coerceIn(0.0, 1.0),
            isMoney = true,
        )

        StatusBarKind.DAYS_COUNTDOWN -> {
            val item = s.activeCountdown
            val p = item?.let { countdownProgress(it) }
            if (item == null || p == null) WidgetCell(
                label = "倒计时剩余",
                value = "--",
                caption = "还没有倒计时",
                sub = "进 App → 倒计时 → 事件管理",
                ratio = 0.0,
                isMoney = false,
            )
            else WidgetCell(
                label = "倒计时剩余",
                value = if (p.expired) "已到"
                else if (item.mode == "percent") percentText(p.ratio, 0)
                else "${p.daysLeft} 天",
                caption = if (p.expired) "${item.name} · 已到期"
                else "距离 ${item.name} · ${p.targetKey}",
                sub = if (p.expired) "累计 ${p.elapsedDays} 天" else "已走 ${percentText(p.ratio, 0)}",
                ratio = p.ratio,
                isMoney = false,
            )
        }

        StatusBarKind.DAYS_RECORD -> {
            val item = s.activeRecordDay
            val p = item?.let { recordDayProgress(it) }
            if (item == null || p == null) WidgetCell(
                label = "纪念日累计",
                value = "--",
                caption = "还没有纪念日",
                sub = "进 App → 纪念日 → 事件管理",
                ratio = 0.0,
                isMoney = false,
            )
            else WidgetCell(
                label = "纪念日累计",
                value = "${p.elapsedDays} 天",
                caption = if (p.started) "${item.name} · 已相伴" else "${item.name} · 尚未开始",
                sub = if (p.started) "累计 ${p.span.shortText}" else "起始日 ${p.startKey}",
                ratio = p.ratio,
                isMoney = false,
            )
        }
    }
}
