package com.haofan.xiaoxinxin.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * 统计页的数据层：纯函数，不碰 Android，可单测。
 *
 * 原则：
 *  - 金额一律 Double 秒，最后才格式化；
 *  - 「今日」永远取实时值，历史取 dayLog，二者不重复计算；
 *  - 没有记录的日子返回 absent，图表画成空槽而不是 0 值柱，避免把「没上班」画成「挣了 0 元」。
 */

/** 某一天的聚合结果。present = false 表示这天没有打卡记录。 */
data class DayStat(
    val key: String,
    val weekday: Int,          // 1 = 周一 … 7 = 周日
    val earned: Double,
    val otSeconds: Double,
    val present: Boolean,
    val isToday: Boolean,
)

/** 一段时间内的汇总。 */
data class PeriodStat(
    val label: String,
    val earned: Double,
    val otSeconds: Double,
    val days: Int,             // 有记录的天数
    val avgEarned: Double,     // 按有记录的天数算
)

/** 月度统计里的一行。 */
data class MonthStat(
    val month: String,         // yyyy-MM
    val earned: Double,
    val otSeconds: Double,
    val days: Int,
)

/** 统计区间。 */
enum class StatRange(val key: String, val label: String) {
    WEEK("week", "本周"),
    MONTH("month", "本月"),
    LAST_MONTH("lastMonth", "上月"),
    YEAR("year", "本年"),
    ;

    companion object {
        fun of(key: String): StatRange = entries.firstOrNull { it.key == key } ?: MONTH
    }
}

object Stats {

    private const val WEEKDAY_CN = "一二三四五六日"

    fun weekdayShort(d: LocalDate): String = WEEKDAY_CN[d.dayOfWeek.value - 1].toString()

    /** 区间对应的起止日期（闭区间）。 */
    fun bounds(range: StatRange, today: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
        // 本周按周一起算，国人的习惯
        StatRange.WEEK -> today.minusDays((today.dayOfWeek.value - 1).toLong()) to today
        StatRange.MONTH -> today.withDayOfMonth(1) to today
        StatRange.LAST_MONTH -> {
            val last = YearMonth.from(today).minusMonths(1)
            last.atDay(1) to last.atEndOfMonth()
        }
        StatRange.YEAR -> today.withDayOfYear(1) to today
    }

    /**
     * 把区间内的每一天铺平成列表，供柱状图和列表使用。
     *
     * @param logs 历史流水（key 为 yyyy-MM-dd）
     * @param todayKey 今天的日期 key
     * @param todayEarned 今天的实时金额
     * @param todayOT 今天的实时加班秒数
     */
    fun days(
        range: StatRange,
        today: LocalDate,
        logs: Map<String, DayLog>,
        todayEarned: Double,
        todayOT: Double,
    ): List<DayStat> {
        val (from, to) = bounds(range, today)
        val tk = dateKey(today)
        val out = ArrayList<DayStat>()
        var d = from
        while (!d.isAfter(to)) {
            val k = dateKey(d)
            val isToday = k == tk
            val v = logs[k]
            out += DayStat(
                key = k,
                weekday = d.dayOfWeek.value,
                earned = if (isToday) todayEarned else (v?.earned ?: 0.0),
                otSeconds = if (isToday) todayOT else (v?.otSeconds ?: 0.0),
                present = isToday && (todayEarned > 0 || todayOT > 0) || v != null,
                isToday = isToday,
            )
            d = d.plusDays(1)
        }
        return out
    }

    /** 汇总：只统计有记录的日子，避免月初第一天就被前面 20 个「0 元」拉低均值。 */
    fun summarize(label: String, days: List<DayStat>): PeriodStat {
        val present = days.filter { it.present }
        val earned = present.sumOf { it.earned }
        val ot = present.sumOf { it.otSeconds }
        return PeriodStat(
            label = label,
            earned = earned,
            otSeconds = ot,
            days = present.size,
            avgEarned = if (present.isEmpty()) 0.0 else earned / present.size,
        )
    }

    /** 按月聚合，最近在前。年视图用。 */
    fun byMonth(logs: Map<String, DayLog>, year: Int): List<MonthStat> =
        (1..12).map { m ->
            val prefix = String.format(java.util.Locale.US, "%04d-%02d", year, m)
            val rows = logs.filterKeys { it.startsWith(prefix) }
            MonthStat(
                month = prefix,
                earned = rows.values.sumOf { it.earned },
                otSeconds = rows.values.sumOf { it.otSeconds },
                days = rows.size,
            )
        }

    /** 加班折算：加班秒数 → 「X 时 Y 分」，0 显示「—」。 */
    fun otText(sec: Double): String {
        val m = (sec / 60).toInt()
        return when {
            m <= 0 -> "—"
            else -> "${m / 60} 时 ${m % 60} 分"
        }
    }

    /** 加班金额：按当前时薪折算，仅供参考。 */
    fun otEarned(otSec: Double, ratePerHour: Double): Double = otSec / 3600.0 * ratePerHour

    /**
     * 周对比：本周 vs **上周同期**（今天是周四就只比到上周四）。
     *
     * 两个坑：
     *  - 上周同期完全没有记录 → 不做对比（否则除以 0 或显示「暴增 1000%」）
     *  - 上周整周有记录、但同期这几天没上过班 → 也不算对比，避免把「上周后几天才上班」
     *    误读成「这周爆涨」
     */
    fun weekOverWeek(
        today: LocalDate,
        logs: Map<String, DayLog>,
        todayEarned: Double,
    ): Double? {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val lastMonday = monday.minusWeeks(1)
        val elapsed = (today.toEpochDay() - monday.toEpochDay()).toInt()
        var last = 0.0
        var hasLast = false
        for (i in 0..elapsed) {
            val k = dateKey(lastMonday.plusDays(i.toLong()))
            logs[k]?.let { last += it.earned; hasLast = true }
        }
        if (!hasLast || last <= 0) return null
        var cur = todayEarned
        for (i in 0 until elapsed) {
            val k = dateKey(monday.plusDays(i.toLong()))
            logs[k]?.let { cur += it.earned }
        }
        return (cur - last) / last
    }
}
