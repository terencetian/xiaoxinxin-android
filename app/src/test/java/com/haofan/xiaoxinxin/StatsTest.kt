package com.haofan.xiaoxinxin

import com.haofan.xiaoxinxin.core.DayLog
import com.haofan.xiaoxinxin.core.StatRange
import com.haofan.xiaoxinxin.core.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 统计层断言。故意挑「有份跨月、有空格子」的数据，专治月初/上月的边界。 */
class StatsTest {

    // 2026-11-06 是周五；本周一 = 11-02
    private val today: LocalDate = LocalDate.parse("2026-11-06")
    private val logs = mapOf(
        "2026-10-30" to DayLog(200.0, 3600.0),
        "2026-10-31" to DayLog(100.0, 0.0),
        "2026-11-02" to DayLog(160.0, 0.0),
        "2026-11-03" to DayLog(160.0, 1800.0),
        "2026-11-04" to DayLog(0.0, 7200.0),
    )

    @Test
    fun bounds_weekStartsMonday() {
        val (from, to) = Stats.bounds(StatRange.WEEK, today)
        assertEquals("2026-11-02", from.toString())
        assertEquals("2026-11-06", to.toString())
    }

    @Test
    fun bounds_lastMonthCrossesYear() {
        val jan = LocalDate.parse("2026-01-15")
        val (from, to) = Stats.bounds(StatRange.LAST_MONTH, jan)
        assertEquals("2025-12-01", from.toString())
        assertEquals("2025-12-31", to.toString())
    }

    @Test
    fun days_mergesLiveTodayAndSkipsMissing() {
        val d = Stats.days(StatRange.MONTH, today, logs, todayEarned = 88.0, todayOT = 600.0)
        assertEquals(6, d.size)                       // 11-01 … 11-06
        val t = d.last()
        assertEquals("2026-11-06", t.key)
        assertEquals(88.0, t.earned, 1e-9)
        assertEquals(600.0, t.otSeconds, 1e-9)
        assertEquals(true, t.present)
        // 11-01 是周日且没记录 → 不出现
        assertEquals(false, d.first().present)
    }

    @Test
    fun summarize_ignoresAbsentDays() {
        val d = Stats.days(StatRange.MONTH, today, logs, todayEarned = 40.0, todayOT = 0.0)
        val s = Stats.summarize("本月", d)
        // 11-02 160 + 11-03 160 + 11-04 0 + 11-05(无) + 11-06 40 = 360
        assertEquals(360.0, s.earned, 1e-9)
        assertEquals(4, s.days)                       // 11-04 有加班记录，算出勤
        assertEquals(90.0, s.avgEarned, 1e-9)
        assertEquals(1800.0 + 7200.0, s.otSeconds, 1e-9)
    }

    @Test
    fun byMonth_aggregatesTwelveRows() {
        val m = Stats.byMonth(logs, 2026)
        assertEquals(12, m.size)
        assertEquals(300.0, m.first { it.month == "2026-10" }.earned, 1e-9)
        assertEquals(320.0, m.first { it.month == "2026-11" }.earned, 1e-9)
        assertEquals(0, m.first { it.month == "2026-03" }.days)
    }

    @Test
    fun weekOverWeek_samePeriodOnly() {
        // 今天周五，上周同期 = 10-26(一) ~ 10-30(五)；
        // 只有 10-30 有 ¥200，但本周同期（11-02~11-06）是 320 + 今日 0 = 320 → +60%
        // 注：同期是「同星期几为止」，不是「同天数」，所以上一周周五也算进来
        assertEquals(0.6, Stats.weekOverWeek(today, logs, 0.0)!!, 1e-9)
        // 把 10-30 抹掉，上周同期彻底没记录 → 不能瞎比，返回 null
        assertNull(Stats.weekOverWeek(today, logs - "2026-10-30", 0.0))
        // 补上上周同期数据后应能算出百分比
        val l2 = logs + mapOf(
            "2026-10-26" to DayLog(100.0, 0.0),
            "2026-10-27" to DayLog(100.0, 0.0),
        )
        val pct = Stats.weekOverWeek(today, l2, 0.0)!!
        // 本周（周一~周五）= 320 + 今日 0 = 320；上周（10-26~10-30）= 100+100+200 = 400 → -20%
        assertEquals(-0.2, pct, 1e-9)
    }

    @Test
    fun otText_formats() {
        assertEquals("—", Stats.otText(0.0))
        assertEquals("0 时 30 分", Stats.otText(1800.0))
        assertEquals("2 时 0 分", Stats.otText(7200.0))
    }
}
