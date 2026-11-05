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

fun isWorkDate(d: LocalDate): Boolean {
    val key = dateKey(d)
    if (key in HOLIDAYS) return false
    if (key in EXTRA_WORKDAYS) return true
    return d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY
}

fun monthWorkDays(date: LocalDate): Int {
    var n = 0
    var d = date.withDayOfMonth(1)
    while (d.month == date.month) {
        if (isWorkDate(d)) n++
        d = d.plusDays(1)
    }
    return n
}

/** 千分位 + 固定小数位，与 macOS 版 fmt() 输出一致。 */
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

/** 按 yyyy-MM-dd 取当日 0 点，用于流水落账。 */
fun dayStart(key: String): LocalDateTime = LocalDate.parse(key).atStartOfDay()
