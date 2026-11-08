package com.haofan.xiaoxinxin.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

// Your Days 两套数据模型：与 macOS Logic.swift 的 CountdownItem / RecordDayItem 一一对应。
// 时间一律用 java.time，跨月 / 闰年 / 跨年顺延全部交给日历库，不自己算天数。

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")

/** 自然日差（先归一到当天 0 点，跨月跨年安全）。 */
fun calendarDays(from: LocalDate, to: LocalDate): Int =
    ChronoUnit.DAYS.between(from, to).toInt()

/** 2.4：纪念日的「累计时长」= 起始日到今天，拆成 X 年 Y 个月 Z 天 */
data class ElapsedSpan(
    val years: Int = 0,
    val months: Int = 0,
    val days: Int = 0,
    val totalDays: Int = 0,
) {
    val shortText: String
        get() {
            val parts = mutableListOf<String>()
            if (years > 0) parts += "$years 年"
            if (months > 0) parts += "$months 个月"
            if (days > 0 || parts.isEmpty()) parts += "$days 天"
            return parts.joinToString(" ")
        }
}

fun elapsedSpan(from: LocalDate, to: LocalDate = LocalDate.now()): ElapsedSpan {
    if (to.isBefore(from)) return ElapsedSpan()
    val p = Period.between(from, to)
    return ElapsedSpan(
        years = p.years,
        months = p.months,
        days = p.days,
        totalDays = calendarDays(from, to),
    )
}

// MARK: - 倒计时（按天维度走数，可带目标时刻）

data class CountdownItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val month: Int = 1,
    val day: Int = 1,
    /** null = 每年重复；有值 = 一次性目标 */
    val year: Int? = null,
    /** "days" = 剩余天数；"percent" = 完成百分比 */
    val mode: String = "days",
    /** 创建日 yyyy-MM-dd（一次性目标的百分比起点） */
    val createdKey: String = "",
    // 2.4：用户可自己指定起始日期与目标时刻；留空 = 沿用旧口径
    val startYear: Int? = null,
    val startMonth: Int? = null,
    val startDay: Int? = null,
    val targetHour: Int? = null,
    val targetMinute: Int? = null,
) {
    val isYearly: Boolean get() = year == null
    val modeTitle: String get() = if (mode == "percent") "百分比" else "天数"

    /** 是否设置了自定义起始日 */
    val hasCustomStart: Boolean
        get() {
            val y = startYear ?: return false
            val m = startMonth ?: return false
            val d = startDay ?: return false
            if (y !in 1970..2100 || m !in 1..12 || d !in 1..31) return false
            return try {
                LocalDate.of(y, m, d)
                true
            } catch (_: Exception) {
                false
            }
        }

    val startKeyText: String?
        get() = if (!hasCustomStart) null
        else String.format("%04d-%02d-%02d", startYear!!, startMonth!!, startDay!!)

    /** 目标时刻文本 HH:mm */
    val targetTimeText: String
        get() = String.format("%02d:%02d", targetHour ?: 0, targetMinute ?: 0)
    val hasCustomTime: Boolean get() = (targetHour ?: 0) != 0 || (targetMinute ?: 0) != 0

    val valid: Boolean
        get() {
            if (month !in 1..12 || day !in 1..31) return false
            year?.let { if (it !in 1970..2100) return false }
            targetHour?.let { if (it !in 0..23) return false }
            targetMinute?.let { if (it !in 0..59) return false }
            if (startYear != null || startMonth != null || startDay != null) {
                if (!hasCustomStart) return false
            }
            // 用闰年校验月日组合（允许 2/29）
            return try {
                LocalDate.of(2024, month, day)
                true
            } catch (_: Exception) {
                false
            }
        }
}

/** 2.4：自定义起始日必须早于目标日（每年重复的目标日跨年顺延，不做该校验） */
fun countdownRangeError(item: CountdownItem): String? {
    if (!item.hasCustomStart) return null
    val y = item.year ?: return null
    val s = LocalDate.parse(item.startKeyText ?: return null, DAY_FMT)
    val t = try {
        LocalDate.of(y, item.month, item.day)
    } catch (_: Exception) {
        return null
    }
    return if (!t.isAfter(s)) "起始日必须早于目标日" else null
}

data class CountdownProgress(
    val daysLeft: Int,       // 距目标日剩余自然日（当天为 0）
    val totalDays: Int,      // 起点 → 目标日的总天数（百分比分母）
    val elapsedDays: Int,
    val ratio: Double,       // 0...1，进度条走数用
    val targetKey: String,   // yyyy-MM-dd
    val expired: Boolean,    // 一次性目标已过
)

private fun moment(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime? = try {
    LocalDate.of(year, month, day).atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
} catch (_: Exception) {
    null
}

/** 计算倒计时进度。年度重复：过了今年就顺延到明年；一次性：过期返回 expired=true。
 *  起点优先用用户设置的起始日，其次才是创建日 / 上一次同月同日；目标可带时刻，进度按秒算。 */
fun countdownProgress(item: CountdownItem, now: LocalDateTime = LocalDateTime.now()): CountdownProgress? {
    if (!item.valid) return null
    val today = now.toLocalDate()
    val thisYear = today.year
    val hour = item.targetHour ?: 0
    val minute = item.targetMinute ?: 0

    val target = if (item.year != null) {
        moment(item.year, item.month, item.day, hour, minute)
    } else {
        val t = moment(thisYear, item.month, item.day, hour, minute)
        if (t == null) null
        else if (!t.isBefore(now)) t else moment(thisYear + 1, item.month, item.day, hour, minute)
    } ?: return null

    var start: LocalDateTime? = item.startKeyText?.let {
        runCatching { LocalDate.parse(it, DAY_FMT).atStartOfDay() }.getOrNull()
    }
    if (start == null) {
        if (item.isYearly) {
            start = moment(target.year - 1, item.month, item.day, hour, minute)
        } else if (isValidDayKey(item.createdKey)) {
            start = runCatching { LocalDate.parse(item.createdKey, DAY_FMT).atStartOfDay() }.getOrNull()
        }
    }
    if (start == null || !start!!.isBefore(target)) {
        start = target.minusDays(365)
    }
    val begin = start!!

    val totalSeconds = ChronoUnit.SECONDS.between(begin, target).coerceAtLeast(60)
    val elapsedSeconds = ChronoUnit.SECONDS.between(begin, now).coerceIn(0, totalSeconds)
    val total = calendarDays(begin.toLocalDate(), target.toLocalDate()).coerceAtLeast(1)
    val elapsed = calendarDays(begin.toLocalDate(), minOf(now, target).toLocalDate()).coerceIn(0, total)
    val left = calendarDays(today, target.toLocalDate()).coerceAtLeast(0)
    return CountdownProgress(
        daysLeft = left,
        totalDays = total,
        elapsedDays = elapsed,
        ratio = (elapsedSeconds.toDouble() / totalSeconds).coerceIn(0.0, 1.0),
        targetKey = dateKey(target.toLocalDate()),
        expired = item.year != null && target.isBefore(now),
    )
}

// MARK: - 纪念日（从起始日累计天数，与倒计时方向相反）

data class RecordDayItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val year: Int = 2026,
    val month: Int = 1,
    val day: Int = 1,
    /** 0 = 自动按 100 天里程碑；>0 = 自定义目标天数 */
    val targetDays: Int = 0,
    /** "days" = 累计天数；"percent" = 里程碑百分比 */
    val mode: String = "days",
) {
    val milestoneStep: Int get() = if (targetDays > 0) targetDays else 100
    val modeTitle: String get() = if (mode == "percent") "百分比" else "天数"

    val valid: Boolean
        get() {
            if (year !in 1970..2100 || month !in 1..12 || day !in 1..31) return false
            if (targetDays < 0 || targetDays > 100000) return false
            return try {
                LocalDate.of(year, month, day)
                true
            } catch (_: Exception) {
                false
            }
        }
}

data class RecordDayProgress(
    val elapsedDays: Int,      // 从起始日到今天已记录的自然日
    val startKey: String,      // yyyy-MM-dd
    val milestoneStep: Int,
    val milestoneDone: Int,
    val daysToMilestone: Int,
    val milestoneIndex: Int,
    val ratio: Double,
    val started: Boolean,
    val span: ElapsedSpan = ElapsedSpan(), // 2.4：累计时长（X 年 Y 个月 Z 天）
)

/** 纪念日进度：起点为某个历史日期，按自然日累计。
 *  2.4 起主展示是「累计时长」，里程碑只作为附带信息保留。 */
fun recordDayProgress(item: RecordDayItem, now: LocalDate = LocalDate.now()): RecordDayProgress? {
    if (!item.valid) return null
    val start = runCatching { LocalDate.of(item.year, item.month, item.day) }.getOrNull() ?: return null
    val elapsed = calendarDays(start, now).coerceAtLeast(0)
    val step = item.milestoneStep.coerceAtLeast(1)
    val remainder = elapsed % step
    // 正好落在里程碑当天：显示 100%，第二天再归零重新走
    val done = if (elapsed > 0 && remainder == 0) step else remainder
    return RecordDayProgress(
        elapsedDays = elapsed,
        startKey = dateKey(start),
        milestoneStep = step,
        milestoneDone = done,
        daysToMilestone = step - done,
        milestoneIndex = elapsed / step,
        ratio = (done.toDouble() / step).coerceIn(0.0, 1.0),
        started = !start.isAfter(now),
        span = elapsedSpan(start, now),
    )
}
