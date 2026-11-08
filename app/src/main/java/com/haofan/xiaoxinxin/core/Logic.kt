package com.haofan.xiaoxinxin.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

// 纯逻辑层：与 macOS 版 Logic.swift 一一对应，不含任何 Android 依赖，可单独跑单测。

const val WORK_HOURS: Double = 8.0

/** 每日实际计费工时 = 下班 − 上班 − 休息；支持跨零点（如 22:00–06:00）。
 *  输入非法（格式错 / 时长过短）时回退默认 8 小时。 */
fun effectiveWorkHours(startStr: String, endStr: String, breakMinutes: Double): Double {
    fun parse(s: String): Double? {
        val p = s.trim().split(":")
        if (p.size != 2) return null
        val h = p[0].toDoubleOrNull() ?: return null
        val m = p[1].toDoubleOrNull() ?: return null
        if (h < 0 || h >= 24 || m < 0 || m >= 60) return null
        return h + m / 60
    }
    val s = parse(startStr) ?: return WORK_HOURS
    val e = parse(endStr) ?: return WORK_HOURS
    var span = e - s
    if (span <= 0) span += 24
    val hours = span - breakMinutes / 60
    return if (hours >= 0.5) hours else WORK_HOURS
}

// MARK: - 2026 年法定节假日（国办发〔2025〕放假安排）

val HOLIDAY_RANGES: List<Pair<String, String>> = listOf(
    "2026-01-01" to "2026-01-03", // 元旦
    "2026-02-15" to "2026-02-23", // 春节
    "2026-04-04" to "2026-04-06", // 清明
    "2026-05-01" to "2026-05-05", // 劳动节
    "2026-06-19" to "2026-06-21", // 端午
    "2026-09-25" to "2026-09-27", // 中秋
    "2026-10-01" to "2026-10-07", // 国庆
)

val EXTRA_WORKDAYS: Set<String> = setOf(
    "2026-01-04", "2026-02-14", "2026-02-28",
    "2026-05-09", "2026-09-20", "2026-10-10",
)

private val KEY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

fun dateKey(d: LocalDate): String = String.format(Locale.US, "%04d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)

/** 严格校验 yyyy-MM-dd（格式 + 真实日历有效性，拒绝 2026-02-30 这类）。 */
fun isValidDayKey(s: String): Boolean {
    if (s.length != 10) return false
    return try {
        LocalDate.parse(s, KEY_FMT).toString() == s
    } catch (_: Exception) {
        false
    }
}

val HOLIDAYS: Set<String> by lazy {
    buildSet {
        for ((a, b) in HOLIDAY_RANGES) {
            var d = LocalDate.parse(a)
            val end = LocalDate.parse(b)
            while (!d.isAfter(end)) {
                add(dateKey(d))
                d = d.plusDays(1)
            }
        }
    }
}

// MARK: - 2.0 自定义工作日规则
//
// java.time.DayOfWeek.value：1=周一 … 7=周日。
// 1.x 把「周一~周五 + 法定节假日」写死；2.0 起由调用方每次传入规则，
// 手填天数优先级最高（>0 时直接采用，不再逐日算）。

data class WorkdayRule(
    /** 允许计薪的星期，ISO 序号：1=周一 … 7=周日 */
    val weekdays: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** 是否叠加国办法定节假日 / 调休补班 */
    val useLegalHolidays: Boolean = true,
    /** 手动指定每月工作日天数，>0 时直接采用 */
    val manualDays: Int? = null,
) {
    companion object {
        val DEFAULT = WorkdayRule()
    }
}

fun isWorkDate(d: LocalDate, rule: WorkdayRule): Boolean {
    val key = dateKey(d)
    if (rule.useLegalHolidays) {
        if (key in HOLIDAYS) return false
        if (key in EXTRA_WORKDAYS) return true
    }
    return d.dayOfWeek.value in rule.weekdays
}

fun monthWorkDays(date: LocalDate, rule: WorkdayRule): Int {
    rule.manualDays?.let { if (it > 0) return it }
    var n = 0
    var d = date.withDayOfMonth(1)
    while (d.month == date.month && d.year == date.year) {
        if (isWorkDate(d, rule)) n++
        d = d.plusDays(1)
    }
    return n
}

/** 兼容 1.x 调用：不传规则 = 默认法定口径。 */
fun isWorkDate(d: LocalDate): Boolean = isWorkDate(d, WorkdayRule.DEFAULT)
fun monthWorkDays(date: LocalDate): Int = monthWorkDays(date, WorkdayRule.DEFAULT)

fun fmt(n: Double, dec: Int = 2): String =
    String.format(Locale.US, "%,.${dec}f", n)

fun timeStr(d: LocalDateTime): String =
    String.format(Locale.US, "%02d:%02d", d.hour, d.minute)

/** 当前班次的计划下班时刻。
 *  同日班（start < end）→ 当天 end；跨零点班（start >= end）→ now 已过当天 start 则为次日，
 *  否则为当天。时间文本非法返回 null。 */
fun shiftEndTime(now: LocalDateTime, startStr: String, endStr: String): LocalDateTime? {
    fun comps(s: String): Pair<Int, Int>? {
        val p = s.trim().split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h < 0 || h >= 24 || m < 0 || m >= 60) return null
        return h to m
    }
    val (eh, em) = comps(endStr) ?: return null
    val (sh, sm) = comps(startStr) ?: return null
    val endToday = now.toLocalDate().atTime(LocalTime.of(eh, em))
    val startToday = now.toLocalDate().atTime(LocalTime.of(sh, sm))
    val crossMidnight = (eh * 60 + em) <= (sh * 60 + sm)
    if (!crossMidnight) return endToday
    return if (!now.isBefore(startToday)) endToday.plusDays(1) else endToday
}

/** 当前班次的计划上班时刻（shiftEndTime 的对称函数，供自动打卡上班使用）。 */
fun shiftStartTime(now: LocalDateTime, startStr: String, endStr: String): LocalDateTime? {
    fun comps(s: String): Pair<Int, Int>? {
        val p = s.trim().split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h < 0 || h >= 24 || m < 0 || m >= 60) return null
        return h to m
    }
    val (eh, em) = comps(endStr) ?: return null
    val (sh, sm) = comps(startStr) ?: return null
    val startToday = now.toLocalDate().atTime(LocalTime.of(sh, sm))
    val crossMidnight = (eh * 60 + em) <= (sh * 60 + sm)
    if (!crossMidnight) return startToday
    return if (!now.isBefore(startToday)) startToday else startToday.minusDays(1)
}

/** 按 yyyy-MM-dd 取当日 0 点，用于流水落账。 */
fun dayStart(key: String): LocalDateTime = LocalDate.parse(key).atStartOfDay()

// MARK: - 2.0 进度条统计内容
//
// 进度条不再绑死「今日已赚 ÷ 日薪」：所有口径统一算出一个 ratio（可 > 1，
// 例如 8 小时工时 + 4 小时加班 = 1.5），由 UI 决定怎么画、怎么标数字。

enum class ProgressMetric(val key: String) {
    TODAY_SALARY("todaySalary"),   // 今日工资进度
    MONTH_SALARY("monthSalary"),   // 本月工资进度
    TODAY_HOURS("todayHours"),     // 今日工时进度（可超 100%）
    MONTH_HOURS("monthHours"),     // 本月工时进度（可超 100%）
    ;

    val title: String
        get() = when (this) {
            TODAY_SALARY -> "今日工资进度"
            MONTH_SALARY -> "本月工资进度"
            TODAY_HOURS -> "今日工时进度"
            MONTH_HOURS -> "本月工时进度"
        }

    val detail: String
        get() = when (this) {
            TODAY_SALARY -> "今日已到账 ÷ 日薪"
            MONTH_SALARY -> "本月累计 ÷ 月薪"
            TODAY_HOURS -> "今日已工作 ÷ 计划工时，加班可超 100%"
            MONTH_HOURS -> "本月已工作 ÷ 本月应出勤工时"
        }

    val isMoney: Boolean get() = this == TODAY_SALARY || this == MONTH_SALARY

    companion object {
        fun of(key: String): ProgressMetric = entries.firstOrNull { it.key == key } ?: TODAY_SALARY
        val salaryCases: List<ProgressMetric> = entries.toList()
    }
}

/** 工时进度：workedSeconds ÷ 计划工时，加班时自然超过 1 */
fun hoursProgress(workedSeconds: Double, plannedHours: Double): Double {
    if (plannedHours <= 0 || workedSeconds <= 0) return 0.0
    return workedSeconds / (plannedHours * 3600)
}

/** 把可 >1 的进度安全地折成「条内填充比例」，超出部分由 UI 另画第二段 */
fun clampedProgress(r: Double): Double = r.coerceIn(0.0, 1.0)

/** 百分比文案：加班 4h（8h 为满）→ 150.0% */
fun percentText(r: Double, dec: Int = 1): String =
    String.format(Locale.US, "%.${dec}f%%", (r.coerceAtLeast(0.0)) * 100)
