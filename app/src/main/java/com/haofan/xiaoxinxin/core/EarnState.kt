package com.haofan.xiaoxinxin.core

import java.time.LocalDate
import java.time.LocalDateTime

/** 一天的历史流水。 */
data class DayLog(val earned: Double, val otSeconds: Double)

/**
 * 全部可持久化状态。金额一律由「打卡时刻 + 当前时间」现算，
 * 不依赖定时器累加 —— 所以进程被冻结或杀死后恢复，数字依然准确。
 */
data class EarnState(
    val salaryText: String = "",
    val workDaysText: String = "",
    val workStart: String = "09:00",
    val workEnd: String = "18:00",
    val breakMin: String = "60",

    val clockInAt: Long? = null,          // epoch millis；null = 未计费
    val baseEarned: Double = 0.0,         // 已结束计费段的累计金额
    val workedBeforeSession: Double = 0.0, // 本日此前已工作秒数
    val overtimeSeconds: Double = 0.0,

    val autoClockOut: Boolean = true,
    val floatEnabled: Boolean = false,     // 自绘的顶部悬浮岛
    val islandEnabled: Boolean = false,    // 系统级实时通知（小米超级岛等）
    val floatX: Int = -1,                 // -1 表示未定位，首次贴右上
    val floatY: Int = -1,
    val floatMini: Boolean = false,        // 悬浮窗迷你形态

    val barSymbol: String = "",
    val dayLog: Map<String, DayLog> = emptyMap(),
    val dayKey: String = "",               // 本份数据所属日期，用于跨天重置
    val deviceId: String = "",             // 预留：日后云端同步的设备标识
    val schemaVersion: Int = 1,
    val logText: String = "",              // 首页底部那行状态提示，不持久化
) {
    val autoDays: Int get() = monthWorkDays(LocalDate.now())

    val salary: Double get() = salaryText.toDoubleOrNull() ?: 0.0

    val workDays: Int
        get() = workDaysText.toIntOrNull()?.takeIf { it in 1..31 } ?: autoDays

    val isAutoDays: Boolean
        get() = workDaysText.toIntOrNull()?.let { it == autoDays } ?: true

    val effectiveHours: Double
        get() = effectiveWorkHours(workStart, workEnd, (breakMin.toIntOrNull() ?: 60).toDouble())

    val ratePerSec: Double
        get() = if (workDays <= 0 || effectiveHours <= 0) 0.0 else salary / workDays / effectiveHours / 3600

    val ratePerHour: Double get() = ratePerSec * 3600
    val ratePerDay: Double get() = if (workDays <= 0) 0.0 else salary / workDays

    val working: Boolean get() = clockInAt != null

    /** 今日已到账金额（含正在进行的计费段）。 */
    fun displayEarned(at: Long = System.currentTimeMillis()): Double {
        val start = clockInAt ?: return baseEarned
        return baseEarned + (at - start).coerceAtLeast(0L) / 1000.0 * ratePerSec
    }

    fun progress(at: Long = System.currentTimeMillis()): Double =
        if (ratePerDay <= 0) 0.0 else (displayEarned(at) / ratePerDay).coerceIn(0.0, 1.0)

    /** 今日已工作秒数 = 已结束段 + 正在进行的计费段。 */
    fun workedSeconds(at: Long = System.currentTimeMillis()): Double {
        val start = clockInAt ?: return workedBeforeSession
        return workedBeforeSession + (at - start).coerceAtLeast(0L) / 1000.0
    }

    fun workedMinutes(at: Long = System.currentTimeMillis()): Int = (workedSeconds(at) / 60).toInt()

    private fun nowLocal(at: Long): LocalDateTime =
        java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()

    /** 当前班次的计划下班时刻（跨零点自动落到次日）。 */
    fun scheduledEnd(at: Long = System.currentTimeMillis()): LocalDateTime? =
        shiftEndTime(nowLocal(at), workStart, workEnd)

    /** 加班中：当前计费段是在计划下班之后才打卡的。 */
    fun overtimeNow(at: Long = System.currentTimeMillis()): Boolean {
        val start = clockInAt ?: return false
        val end = scheduledEnd(at) ?: return false
        val startLocal = nowLocal(start)
        return !startLocal.isBefore(end)
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

    /** 按日期闭区间查询流水（yyyy-MM-dd 字符串序即日期序；空串=不限制），降序返回，今日不在内。 */
    fun dayLogRows(start: String, end: String, at: Long = System.currentTimeMillis()): List<Pair<String, DayLog>> {
        val today = dateKey(nowLocal(at).toLocalDate())
        return dayLog.entries
            .filter { (start.isEmpty() || it.key >= start) && (end.isEmpty() || it.key <= end) }
            .filter { it.key != today }
            .sortedByDescending { it.key }
            .map { it.key to it.value }
    }
}
