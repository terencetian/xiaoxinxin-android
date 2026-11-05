package com.haofan.xiaoxinxin

import com.haofan.xiaoxinxin.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 与 macOS 版 test_logic.swift 一一对应的断言集。 */
class LogicTest {

    private fun d(s: String): LocalDate = LocalDate.parse(s)

    @Test
    fun monthWorkDays_matchesCalendar() {
        assertEquals(21, monthWorkDays(d("2026-11-01"))) // 无节假日
        assertEquals(16, monthWorkDays(d("2026-02-01"))) // 28 天 - 春节 6 个工作日 + 2 天补班
        assertEquals(18, monthWorkDays(d("2026-10-01"))) // 国庆 5 个工作日 + 1 天补班
    }

    @Test
    fun isWorkDate_holidaysAndMakeupDays() {
        assertFalse(isWorkDate(d("2026-01-01"))) // 元旦
        assertFalse(isWorkDate(d("2026-02-17"))) // 春节
        assertTrue(isWorkDate(d("2026-02-14")))  // 春节补班周六
        assertTrue(isWorkDate(d("2026-10-10")))  // 国庆补班周六
        assertFalse(isWorkDate(d("2026-11-01"))) // 普通周日
        assertTrue(isWorkDate(d("2026-11-04")))  // 普通周三
    }

    @Test
    fun rate_allTiers() {
        val salary = 3500.0
        val days = 21
        val hours = effectiveWorkHours("09:00", "18:00", 60.0)
        assertEquals(8.0, hours, 1e-9)
        assertEquals(0.005787, salary / days / hours / 3600, 1e-6)
        assertEquals(166.666, salary / days, 1e-3)
        assertEquals(20.833, salary / days / hours, 1e-3)
    }

    @Test
    fun effectiveWorkHours_cases() {
        fun h(s: String, e: String, b: Double) = effectiveWorkHours(s, e, b)
        assertEquals(8.0, h("09:00", "18:00", 60.0), 1e-9)
        assertEquals(8.0, h("9:30", "18:00", 30.0), 1e-9)
        assertEquals(7.0, h("22:00", "06:00", 60.0), 1e-9)   // 跨零点
        assertEquals(10.5, h("08:00", "20:00", 90.0), 1e-9)
        assertEquals(WORK_HOURS, h("abc", "18:00", 60.0), 1e-9)   // 非法格式回退
        assertEquals(WORK_HOURS, h("09:00", "09:30", 60.0), 1e-9) // 工时过短回退
    }

    @Test
    fun shiftEndTime_sameDayAndCrossMidnight() {
        val now = LocalDateTime.of(2026, 11, 5, 10, 0)
        val sameDay = shiftEndTime(now, "09:00", "18:00")
        assertEquals("18:00", timeStr(sameDay!!))
        assertEquals("2026-11-05", dateKey(sameDay.toLocalDate()))

        // 22:00 后打卡，下班 06:00 → 次日
        val late = LocalDateTime.of(2026, 11, 5, 22, 30)
        val cross = shiftEndTime(late, "22:00", "06:00")
        assertEquals("2026-11-06", dateKey(cross!!.toLocalDate()))
        assertEquals("06:00", timeStr(cross))

        // 跨零点班但在上班时刻之前 → 当天 06:00
        val early = LocalDateTime.of(2026, 11, 5, 2, 0)
        val same = shiftEndTime(early, "22:00", "06:00")
        assertEquals("2026-11-05", dateKey(same!!.toLocalDate()))

        assertNull(shiftEndTime(now, "09:00", "abc"))
    }

    @Test
    fun isValidDayKey_strict() {
        assertTrue(isValidDayKey("2026-11-05"))
        assertTrue(isValidDayKey("2024-02-29"))   // 闰年
        assertFalse(isValidDayKey("2026-02-30"))  // 2/30
        assertFalse(isValidDayKey("2026-13-01"))  // 13 月
        assertFalse(isValidDayKey("2026-1-5"))    // 位数不足
        assertFalse(isValidDayKey("abc"))
    }

    @Test
    fun fmt_thousandsSeparator() {
        assertEquals("1,234.50", fmt(1234.5))
        assertEquals("0.01", fmt(0.01))
        assertEquals("7.20", fmt(7.2, 2))
        assertEquals("100", fmt(100.4, 0))
    }
}
