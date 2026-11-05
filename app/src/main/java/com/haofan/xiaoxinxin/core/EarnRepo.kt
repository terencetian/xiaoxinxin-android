package com.haofan.xiaoxinxin.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * 全局单例仓库：Activity、悬浮窗服务、通知都从这里读同一份状态。
 * 所有写操作立即落盘，进程随时被杀也不会丢账。
 */
object EarnRepo {

    private const val PREF = "xiaoxinxin"

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(EarnState())
    val state: StateFlow<EarnState> = _state.asStateFlow()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        var s = load(p)
        if (s.dayKey.isEmpty()) {
            s = s.copy(dayKey = todayKey())
            save(p, s)
        }
        _state.value = s
    }

    // ---------------- 读写 ----------------

    private fun todayKey(): String = dateKey(LocalDate.now())

    private fun load(p: SharedPreferences): EarnState = EarnState(
        salaryText = p.getString("salary", "") ?: "",
        workDaysText = p.getString("workDaysText", "") ?: "",
        workStart = p.getString("workStart", "09:00") ?: "09:00",
        workEnd = p.getString("workEnd", "18:00") ?: "18:00",
        breakMin = p.getString("breakMin", "60") ?: "60",
        clockInAt = if (p.getLong("clockInAt", -1L) >= 0) p.getLong("clockInAt", -1L) else null,
        baseEarned = p.getFloat("baseEarned", 0f).toDouble(),
        workedBeforeSession = p.getFloat("workedBeforeSession", 0f).toDouble(),
        overtimeSeconds = p.getFloat("overtimeSeconds", 0f).toDouble(),
        autoClockOut = p.getBoolean("autoClockOut", true),
        floatEnabled = p.getBoolean("floatEnabled", false),
        floatX = p.getInt("floatX", -1),
        floatY = p.getInt("floatY", -1),
        floatMini = p.getBoolean("floatMini", false),
        barSymbol = p.getString("barSymbol", "") ?: "",
        dayLog = decodeDayLog(p.getString("dayLog", "{}") ?: "{}"),
        dayKey = p.getString("dayKey", "") ?: "",
        deviceId = p.getString("deviceId", "")?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { p.edit().putString("deviceId", it).apply() },
    )

    private fun save(p: SharedPreferences, s: EarnState) {
        p.edit()
            .putString("salary", s.salaryText)
            .putString("workDaysText", s.workDaysText)
            .putString("workStart", s.workStart)
            .putString("workEnd", s.workEnd)
            .putString("breakMin", s.breakMin)
            .putLong("clockInAt", s.clockInAt ?: -1L)
            .putFloat("baseEarned", s.baseEarned.toFloat())
            .putFloat("workedBeforeSession", s.workedBeforeSession.toFloat())
            .putFloat("overtimeSeconds", s.overtimeSeconds.toFloat())
            .putBoolean("autoClockOut", s.autoClockOut)
            .putBoolean("floatEnabled", s.floatEnabled)
            .putInt("floatX", s.floatX)
            .putInt("floatY", s.floatY)
            .putBoolean("floatMini", s.floatMini)
            .putString("barSymbol", s.barSymbol)
            .putString("dayLog", encodeDayLog(s.dayLog))
            .putString("dayKey", s.dayKey)
            .putString("deviceId", s.deviceId)
            .apply()
    }

    private fun encodeDayLog(m: Map<String, DayLog>): String {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, JSONObject().put("e", v.earned).put("ot", v.otSeconds))
        return o.toString()
    }

    private fun decodeDayLog(raw: String): Map<String, DayLog> = try {
        val o = JSONObject(raw)
        val out = mutableMapOf<String, DayLog>()
        for (k in o.keys()) {
            val v = o.getJSONObject(k)
            out[k] = DayLog(v.optDouble("e", 0.0), v.optDouble("ot", 0.0))
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }

    /** 统一出口：更新状态 + 落盘。 */
    private fun update(block: (EarnState) -> EarnState) {
        val p = prefs ?: return
        val next = block(_state.value)
        _state.value = next
        save(p, next)
    }

    // ---------------- 打卡 ----------------

    fun clockIn() {
        val now = System.currentTimeMillis()
        val s = _state.value
        val ot = s.copy(clockInAt = now).overtimeNow(now)
        val t = timeStr(nowLocal(now))
        update {
            it.copy(
                clockInAt = now,
                logText = if (ot) "$t 加班打卡，已过下班时间，按原费率继续计费"
                else "$t 打卡上班，金额开始滚动",
            )
        }
    }

    fun clockOut(auto: Boolean = false) {
        val now = System.currentTimeMillis()
        val s = _state.value
        val start = s.clockInAt ?: return
        val elapsed = ((now - start).coerceAtLeast(0L)) / 1000.0
        val isOT = s.overtimeNow(now)
        val addOT = if (isOT) elapsed else 0.0
        val newBase = s.baseEarned + elapsed * s.ratePerSec
        val t = timeStr(nowLocal(now))
        val otMin = ((s.overtimeSeconds + addOT) / 60).toInt()
        update {
            it.copy(
                clockInAt = null,
                baseEarned = newBase,
                workedBeforeSession = it.workedBeforeSession + elapsed,
                overtimeSeconds = it.overtimeSeconds + addOT,
                logText = if (auto) "$t 已到下班时间，自动打卡下班，今日共到账 ¥${fmt(newBase)}"
                else "$t 下班打卡，今日共到账 ¥${fmt(newBase)}" + if (otMin > 0) "（含加班 $otMin 分钟）" else "",
            )
        }
    }

    /** 工时校正：把今日累计工作时长直接设为 hours 小时。 */
    fun correctWork(hours: Double) {
        val now = System.currentTimeMillis()
        val s = _state.value
        val target = (hours.coerceAtLeast(0.0)) * 3600
        if (s.clockInAt != null) {
            // 计费中：保留当前段起点，只把「之前已工作」部分调整到目标值
            val running = ((now - s.clockInAt).coerceAtLeast(0L)) / 1000.0
            val before = (target - running).coerceAtLeast(0.0)
            val earned = before * s.ratePerSec
            update {
                it.copy(
                    workedBeforeSession = before,
                    baseEarned = earned,
                    logText = "已校正为 ${fmt(hours, 2)} 小时，金额同步对齐",
                )
            }
        } else {
            val earned = target * s.ratePerSec
            update {
                it.copy(
                    workedBeforeSession = target,
                    baseEarned = earned,
                    logText = "已校正为 ${fmt(hours, 2)} 小时，金额同步对齐",
                )
            }
        }
    }

    // ---------------- 跨天 / 自动下班 ----------------

    /** 每次 tick 前调用：跨天则昨日落账、今日清零。 */
    fun checkDayRollover() {
        val s = _state.value
        val today = todayKey()
        if (today == s.dayKey) return
        val wasWorking = s.working
        val newLog = if (s.baseEarned > 0 || s.overtimeSeconds > 0)
            s.dayLog + (s.dayKey to DayLog(s.baseEarned, s.overtimeSeconds)) else s.dayLog
        update {
            it.copy(
                dayLog = prune(newLog, today),
                baseEarned = 0.0,
                workedBeforeSession = 0.0,
                overtimeSeconds = 0.0,
                dayKey = today,
                clockInAt = if (wasWorking) System.currentTimeMillis() else null,
                logText = if (wasWorking) "跨入新的一天，今日累计已重置，继续计费" else "新的一天，今日累计已重置",
            )
        }
    }

    /** 只保留本月与上月流水，防止无限增长。 */
    private fun prune(m: Map<String, DayLog>, today: String): Map<String, DayLog> {
        val cur = today.take(7)
        val prev = LocalDate.parse(today).minusMonths(1).let { dateKey(it).take(7) }
        return m.filterKeys { it.take(7) == cur || it.take(7) == prev }
    }

    /** 到点自动下班：仅对正常班次生效，加班打卡的段落不自动切断。 */
    fun autoClockOutIfNeeded() {
        val now = System.currentTimeMillis()
        val s = _state.value
        if (!s.autoClockOut || !s.working) return
        val start = s.clockInAt ?: return
        val end = s.scheduledEnd(now) ?: return
        val startLocal = nowLocal(start)
        if (startLocal.isBefore(end) && !nowLocal(now).isBefore(end)) clockOut(auto = true)
    }

    fun tick() {
        checkDayRollover()
        autoClockOutIfNeeded()
    }

    // ---------------- 设置项 ----------------

    fun setSalary(v: String) = update { it.copy(salaryText = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setWorkDays(v: String) = update { it.copy(workDaysText = v.filter { c -> c.isDigit() }.take(2)) }
    fun setBreakMin(v: String) = update { it.copy(breakMin = v.filter { c -> c.isDigit() }.take(3)) }
    fun setWorkStart(v: String) = update { it.copy(workStart = v) }
    fun setWorkEnd(v: String) = update { it.copy(workEnd = v) }
    fun setAutoClockOut(v: Boolean) = update { it.copy(autoClockOut = v) }
    fun setBarSymbol(v: String) = update { it.copy(barSymbol = v.take(2)) }

    fun setFloatEnabled(v: Boolean) = update { it.copy(floatEnabled = v) }
    fun setFloatPosition(x: Int, y: Int) = update { it.copy(floatX = x, floatY = y) }
    fun setFloatMini(v: Boolean) = update { it.copy(floatMini = v) }

    // ---------------- 流水明细 ----------------

    fun dayLogRows(start: String, end: String) = _state.value.dayLogRows(start, end)

    fun removeDayLog(key: String): Boolean {
        if (key !in _state.value.dayLog) return false
        update { it.copy(dayLog = it.dayLog - key, logText = "已删除 $key 的记录") }
        return true
    }

    /** 手动补记；返回错误提示，null/空串表示成功。今日以实时打卡为准，禁止手填。 */
    fun insertDayLog(key: String, earned: Double, otMinutes: Double): String? {
        if (!isValidDayKey(key)) return "日期格式应为 yyyy-MM-dd"
        if (key == todayKey()) return "今日以实时打卡为准，不能手动补记"
        if (earned < 0 || otMinutes < 0) return "金额与加班时长不能为负"
        update {
            it.copy(
                dayLog = it.dayLog + (key to DayLog(earned, otMinutes * 60)),
                logText = "已补记 $key",
            )
        }
        return null
    }

    fun nowLocal(at: Long = System.currentTimeMillis()): java.time.LocalDateTime =
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDateTime()
}
