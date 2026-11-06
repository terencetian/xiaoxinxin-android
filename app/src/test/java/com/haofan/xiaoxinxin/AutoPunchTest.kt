package com.haofan.xiaoxinxin

import com.haofan.xiaoxinxin.core.scheduledStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * 自动打卡上班的纯逻辑部分。
 * 跨零点班次的「计划上班时刻」和 shiftEndTime 是同一条轴的两端，
 * 一端错整条班次就错 —— 这里专门盯它。
 */
class AutoPunchTest {

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min)

    @Test
    fun normalShift_startIsToday() {
        // 10:15 看一个 09:00–18:00 的班：计划上班是今天 09:00
        val now = at(2026, 3, 10, 10, 15)
        assertEquals(at(2026, 3, 10, 9, 0), scheduledStart(now, "09:00", "18:00"))
    }

    @Test
    fun normalShift_beforeStart_stillToday() {
        // 08:00 看同一个班（闹钟提前的场景）：计划上班还是今天 09:00
        val now = at(2026, 3, 10, 8, 0)
        assertEquals(at(2026, 3, 10, 9, 0), scheduledStart(now, "09:00", "18:00"))
    }

    @Test
    fun crossMidnight_evening() {
        // 23:30 处于 22:00–06:00 的班中：计划上班是今天 22:00
        val now = at(2026, 3, 10, 23, 30)
        assertEquals(at(2026, 3, 10, 22, 0), scheduledStart(now, "22:00", "06:00"))
    }

    @Test
    fun crossMidnight_afterMidnight_landsOnYesterday() {
        // 凌晨 01:00 处于昨晚开始的班：计划上班落到昨天 22:00
        val now = at(2026, 3, 10, 1, 0)
        assertEquals(at(2026, 3, 9, 22, 0), scheduledStart(now, "22:00", "06:00"))
    }

    @Test
    fun invalidTime_returnsNull() {
        val now = at(2026, 3, 10, 10, 0)
        assertNull(scheduledStart(now, "9点", "18:00"))
        assertNull(scheduledStart(now, "25:00", "18:00"))
    }

    @Test
    fun wholeDayShift_startEqualsEnd() {
        // 00:00–00:00 视为全天班（start == end 是跨零点的退化情形）：上班=今天 00:00
        val now = at(2026, 3, 10, 12, 0)
        assertEquals(at(2026, 3, 10, 0, 0), scheduledStart(now, "00:00", "00:00"))
    }
}
