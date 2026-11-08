package com.haofan.xiaoxinxin.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
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

    /** 悬浮岛布局版本：2 = 贴顶药丸岛。老用户升级时据此丢弃旧坐标。 */
    private const val FLOAT_LAYOUT_VERSION = 2

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(EarnState())
    val state: StateFlow<EarnState> = _state.asStateFlow()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        // 先跑一次性主题迁移，再读存档 —— 否则读到的还是旧主题
        migrateDefaultThemeToSky(p)
        var s = load(p)
        // 2.7：悬浮岛 / 通知支持 MONEY·DAYS 轮播。老用户如果从没配过显示项
        // （还是出厂的单项「今日已赚」+ 不轮播），直接给到新体验：
        // 轮播 今日已赚 → 倒计时 → 纪念日。自己配过的原样保留。
        if (!p.getBoolean("statusBarAutoConfigured27", false)) {
            p.edit().putBoolean("statusBarAutoConfigured27", true).apply()
            if (s.statusBarKinds == setOf(StatusBarKind.MONEY_TODAY.key) && !s.statusBarRotate) {
                s = s.copy(
                    statusBarKinds = setOf(
                        StatusBarKind.MONEY_TODAY.key,
                        StatusBarKind.DAYS_COUNTDOWN.key,
                        StatusBarKind.DAYS_RECORD.key,
                    ),
                    statusBarRotate = true,
                )
            }
        }
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
        customWeekdays = decodeInts(p.getString("customWeekdays", "") ?: "").ifEmpty { setOf(1, 2, 3, 4, 5) },
        useLegalHolidays = p.getBoolean("useLegalHolidays", true),
        autoClockOut = p.getBoolean("autoClockOut", true),
        autoClockIn = p.getBoolean("autoClockIn", false),
        autoBoot = p.getBoolean("autoBoot", false),
        floatEnabled = p.getBoolean("floatEnabled", false),
        islandEnabled = p.getBoolean("islandEnabled", false),
        // 布局 v1 是「挂在状态栏下面的小胶囊」，坐标和 v2 的贴顶岛不通用；
        // 升级时把旧坐标丢掉，否则用户装完还停在老位置，会以为改动没生效。
        floatX = if (p.getInt("floatLayoutVer", 1) >= FLOAT_LAYOUT_VERSION) p.getInt("floatX", -1) else -1,
        floatY = if (p.getInt("floatLayoutVer", 1) >= FLOAT_LAYOUT_VERSION) p.getInt("floatY", -1) else -1,
        floatMini = p.getBoolean("floatMini", false),
        themeId = migrateTheme(p.getString("themeId", null), p.getString("themeMode", null)),
        themeAlpha = p.getFloat("themeAlpha", 1f).toDouble(),
        themeAccent = p.getString("themeAccent", "") ?: "",
        themeBg = p.getString("themeBg", "") ?: "",
        themeText = p.getString("themeText", "") ?: "",
        progressMetric = p.getString("progressMetric", ProgressMetric.TODAY_SALARY.key)
            ?: ProgressMetric.TODAY_SALARY.key,
        hiddenHomeCards = decodeStrings(p.getString("hiddenHomeCards", "") ?: ""),
        hiddenMoneyChips = decodeStrings(p.getString("hiddenMoneyChips", "") ?: ""),
        hiddenCountdownChips = decodeStrings(p.getString("hiddenCountdownChips", "") ?: ""),
        hiddenRecordChips = decodeStrings(p.getString("hiddenRecordChips", "") ?: ""),
        hiddenButtons = decodeStrings(p.getString("hiddenButtons", "") ?: ""),
        statusBarKinds = decodeStrings(p.getString("statusBarKinds", "") ?: "")
            .ifEmpty { setOf(StatusBarKind.MONEY_TODAY.key) },
        statusBarRotate = p.getBoolean("statusBarRotate", false),
        statusBarRotateSeconds = p.getInt("statusBarRotateSeconds", 5),
        barSymbol = p.getString("barSymbol", "") ?: "",
        privacyEnabled = p.getBoolean("privacyEnabled", false),
        privacyUnlockedUntil = p.getLong("privacyUnlockedUntil", 0L),
        pinSalt = p.getString("pinSalt", "") ?: "",
        pinHash = p.getString("pinHash", "") ?: "",
        countdowns = decodeCountdowns(p.getString("countdowns", "") ?: ""),
        activeCountdownId = p.getString("activeCountdownId", "") ?: "",
        recordDays = decodeRecordDays(p.getString("recordDays", "") ?: ""),
        activeRecordDayId = p.getString("activeRecordDayId", "") ?: "",
        dayLog = decodeDayLog(p.getString("dayLog", "{}") ?: "{}"),
        dayKey = p.getString("dayKey", "") ?: "",
        onboardingDone = p.getBoolean("onboardingDone", false),
        deviceId = p.getString("deviceId", "")?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { p.edit().putString("deviceId", it).apply() },
    )

    /** 1.x 的 themeMode（cream / rainbow / mint / system）→ 2.0 的四套主题 */
    private fun migrateTheme(themeId: String?, themeMode: String?): String {
        themeId?.takeIf { it.isNotEmpty() }?.let { return it }
        return when (themeMode) {
            "mint" -> "mint"
            else -> "sky"
        }
    }

    /**
     * 2.0 默认主题迁移 —— 对齐 macOS 2.3 的 `themeDefaultSky23`。
     *
     * 1.x 存档里可能是 cream / rainbow / mint / system，2.0 早期还可能是 Liquid Glass。
     * macOS 从 2.3 起把默认主题从 Liquid Glass 换成「白 + 天蓝」（最中性），但只迁移
     * **没动过配色**的旧存档：动过色卡或调过透明度的，一律保留用户自己的配色，绝不覆盖。
     *
     * 整段只跑一次，跑完就打标记，避免把用户后来手动选的薄荷绿又拉回天蓝。
     */
    private fun migrateDefaultThemeToSky(p: SharedPreferences) {
        if (p.getBoolean("themeSkyDefault", false)) return
        p.edit().putBoolean("themeSkyDefault", true).apply()

        // 已经是天蓝，没什么可迁的
        val cur = migrateTheme(p.getString("themeId", null), p.getString("themeMode", null))
        if (cur == "sky") return

        // 动过色卡 → 保留用户配色
        val customBg = p.getString("themeBg", "") ?: ""
        val customText = p.getString("themeText", "") ?: ""
        val customAccent = p.getString("themeAccent", "") ?: ""
        if (customBg.isNotEmpty() || customText.isNotEmpty() || customAccent.isNotEmpty()) return

        // 动过透明度 → 也保留。1.0 是「没调过」的默认值，0.58 是 Liquid Glass 预设值，
        // 两个都算原样，其余视为用户手动调过。
        val alpha = p.getFloat("themeAlpha", 1f).toDouble()
        val alphaUntouched = alpha >= 0.98 || kotlin.math.abs(alpha - 0.58) < 0.02
        if (!alphaUntouched) return

        p.edit()
            .putString("themeId", "sky")
            .putString("themeMode", "sky")
            .remove("themeAlpha")
            .apply()
    }

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
            .putString("customWeekdays", encodeInts(s.customWeekdays))
            .putBoolean("useLegalHolidays", s.useLegalHolidays)
            .putBoolean("autoClockOut", s.autoClockOut)
            .putBoolean("autoClockIn", s.autoClockIn)
            .putBoolean("autoBoot", s.autoBoot)
            .putBoolean("floatEnabled", s.floatEnabled)
            .putBoolean("islandEnabled", s.islandEnabled)
            .putInt("floatX", s.floatX)
            .putInt("floatY", s.floatY)
            .putInt("floatLayoutVer", FLOAT_LAYOUT_VERSION)
            .putBoolean("floatMini", s.floatMini)
            .putString("themeId", s.themeId)
            .putFloat("themeAlpha", s.themeAlpha.toFloat())
            .putString("themeAccent", s.themeAccent)
            .putString("themeBg", s.themeBg)
            .putString("themeText", s.themeText)
            .putString("progressMetric", s.progressMetric)
            .putString("hiddenHomeCards", encodeStrings(s.hiddenHomeCards))
            .putString("hiddenMoneyChips", encodeStrings(s.hiddenMoneyChips))
            .putString("hiddenCountdownChips", encodeStrings(s.hiddenCountdownChips))
            .putString("hiddenRecordChips", encodeStrings(s.hiddenRecordChips))
            .putString("hiddenButtons", encodeStrings(s.hiddenButtons))
            .putString("statusBarKinds", encodeStrings(s.statusBarKinds))
            .putBoolean("statusBarRotate", s.statusBarRotate)
            .putInt("statusBarRotateSeconds", s.statusBarRotateSeconds)
            .putString("barSymbol", s.barSymbol)
            .putBoolean("privacyEnabled", s.privacyEnabled)
            .putLong("privacyUnlockedUntil", s.privacyUnlockedUntil)
            .putString("pinSalt", s.pinSalt)
            .putString("pinHash", s.pinHash)
            .putString("countdowns", encodeCountdowns(s.countdowns))
            .putString("activeCountdownId", s.activeCountdownId)
            .putString("recordDays", encodeRecordDays(s.recordDays))
            .putString("activeRecordDayId", s.activeRecordDayId)
            .putString("dayLog", encodeDayLog(s.dayLog))
            .putString("dayKey", s.dayKey)
            .putBoolean("onboardingDone", s.onboardingDone)
            .putString("deviceId", s.deviceId)
            .apply()
    }

    // ---------------- JSON 编解码 ----------------

    private fun encodeStrings(v: Set<String>): String {
        val a = JSONArray()
        v.forEach { a.put(it) }
        return a.toString()
    }

    private fun decodeStrings(raw: String): Set<String> {
        if (raw.isEmpty()) return emptySet()
        return try {
            val a = JSONArray(raw)
            buildSet { for (i in 0 until a.length()) add(a.getString(i)) }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun encodeInts(v: Set<Int>): String = v.sorted().joinToString(",")

    private fun decodeInts(raw: String): Set<Int> =
        raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun encodeCountdowns(list: List<CountdownItem>): String {
        val a = JSONArray()
        list.forEach { c ->
            a.put(
                JSONObject()
                    .put("id", c.id).put("name", c.name)
                    .put("m", c.month).put("d", c.day)
                    .put("y", c.year ?: JSONObject.NULL)
                    .put("mode", c.mode).put("created", c.createdKey)
                    .put("sy", c.startYear ?: JSONObject.NULL)
                    .put("sm", c.startMonth ?: JSONObject.NULL)
                    .put("sd", c.startDay ?: JSONObject.NULL)
                    .put("th", c.targetHour ?: JSONObject.NULL)
                    .put("tm", c.targetMinute ?: JSONObject.NULL)
            )
        }
        return a.toString()
    }

    private fun decodeCountdowns(raw: String): List<CountdownItem> {
        if (raw.isEmpty()) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                CountdownItem(
                    id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    month = o.optInt("m", 1),
                    day = o.optInt("d", 1),
                    year = o.optIntOrNull("y"),
                    mode = o.optString("mode", "days"),
                    createdKey = o.optString("created", ""),
                    startYear = o.optIntOrNull("sy"),
                    startMonth = o.optIntOrNull("sm"),
                    startDay = o.optIntOrNull("sd"),
                    targetHour = o.optIntOrNull("th"),
                    targetMinute = o.optIntOrNull("tm"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeRecordDays(list: List<RecordDayItem>): String {
        val a = JSONArray()
        list.forEach { r ->
            a.put(
                JSONObject()
                    .put("id", r.id).put("name", r.name)
                    .put("y", r.year).put("m", r.month).put("d", r.day)
                    .put("target", r.targetDays).put("mode", r.mode)
            )
        }
        return a.toString()
    }

    private fun decodeRecordDays(raw: String): List<RecordDayItem> {
        if (raw.isEmpty()) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                RecordDayItem(
                    id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    year = o.optInt("y", LocalDate.now().year),
                    month = o.optInt("m", 1),
                    day = o.optInt("d", 1),
                    targetDays = o.optInt("target", 0),
                    mode = o.optString("mode", "days"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

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

    fun clockIn(auto: Boolean = false) {
        val now = System.currentTimeMillis()
        val s = _state.value
        val ot = s.overtimeNow(now)
        val t = timeStr(nowLocal(now))
        update {
            it.copy(
                clockInAt = now,
                logText = if (auto) "$t 已到上班时间，自动打卡上班，金额开始滚动"
                else if (ot) "$t 加班打卡，已过下班时间，按原费率继续计费"
                else "$t 打卡上班，金额开始滚动",
            )
        }
    }

    /** 闹钟到点自动打卡上班。只在闹钟里调用，不在 tick 里兜底 ——
     *  用户 15 点才打开 App 就强行补打 9 点的卡，等于替他虚报了 6 小时工时。 */
    fun autoClockInIfNeeded(): Boolean {
        val now = System.currentTimeMillis()
        val s = _state.value
        if (!s.autoClockIn || s.working) return false
        val end = s.scheduledEnd(now) ?: return false
        val start = s.scheduledStart(now) ?: return false
        val nowL = nowLocal(now)
        // now 落在 [班次开始, 计划下班) 才算「到点上班」；已过下班不补打
        return if (!nowL.isBefore(start) && nowL.isBefore(end)) {
            clockIn(auto = true)
            true
        } else false
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
            update {
                it.copy(
                    workedBeforeSession = before,
                    baseEarned = before * s.ratePerSec,
                    logText = "已校正为 ${fmt(hours, 2)} 小时，金额同步对齐",
                )
            }
        } else {
            update {
                it.copy(
                    workedBeforeSession = target,
                    baseEarned = target * s.ratePerSec,
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

    // ---------------- 设置项：计薪 ----------------

    fun setSalary(v: String) = update { it.copy(salaryText = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setWorkDays(v: String) = update { it.copy(workDaysText = v.filter { c -> c.isDigit() }.take(2)) }
    fun setBreakMin(v: String) = update { it.copy(breakMin = v.filter { c -> c.isDigit() }.take(3)) }
    fun setWorkStart(v: String) = update { it.copy(workStart = v) }
    fun setWorkEnd(v: String) = update { it.copy(workEnd = v) }

    fun toggleWeekday(dow: Int) = update {
        val next = if (dow in it.customWeekdays) it.customWeekdays - dow else it.customWeekdays + dow
        it.copy(customWeekdays = next.ifEmpty { it.customWeekdays })
    }

    fun setUseLegalHolidays(v: Boolean) = update { it.copy(useLegalHolidays = v) }

    fun resetWeekdays() = update { it.copy(customWeekdays = setOf(1, 2, 3, 4, 5), useLegalHolidays = true) }

    // ---------------- 设置项：自动化 ----------------

    fun setAutoClockOut(v: Boolean) = update { it.copy(autoClockOut = v) }
    fun setAutoClockIn(v: Boolean) = update { it.copy(autoClockIn = v) }
    fun setAutoBoot(v: Boolean) = update { it.copy(autoBoot = v) }

    // ---------------- 设置项：悬浮岛 / 通知 ----------------

    fun setFloatEnabled(v: Boolean) = update { it.copy(floatEnabled = v) }
    fun setIslandEnabled(v: Boolean) = update { it.copy(islandEnabled = v) }
    fun setFloatPosition(x: Int, y: Int) = update { it.copy(floatX = x, floatY = y) }
    fun setFloatMini(v: Boolean) = update { it.copy(floatMini = v) }
    fun setBarSymbol(v: String) = update { it.copy(barSymbol = v.take(2)) }

    fun toggleBarKind(kind: StatusBarKind) = update {
        val cur = it.statusBarKinds
        val next = if (kind.key in cur) {
            // 至少保留一项，否则通知栏就空了
            if (cur.size <= 1) cur else cur - kind.key
        } else cur + kind.key
        it.copy(statusBarKinds = next)
    }

    fun setStatusBarRotate(v: Boolean) = update { it.copy(statusBarRotate = v) }
    fun setStatusBarRotateSeconds(v: Int) = update { it.copy(statusBarRotateSeconds = v.coerceIn(2, 60)) }

    // ---------------- 设置项：主题 ----------------

    fun setTheme(id: String) = update { it.copy(themeId = id) }
    fun setThemeAlpha(v: Double) = update { it.copy(themeAlpha = v.coerceIn(0.15, 1.0)) }
    fun setThemeAccent(rgb: String) = update { it.copy(themeAccent = rgb) }
    fun setThemeBg(rgb: String) = update { it.copy(themeBg = rgb) }
    fun setThemeText(rgb: String) = update { it.copy(themeText = rgb) }

    // ---------------- 设置项：口径与显隐 ----------------

    fun setProgressMetric(key: String) = update { it.copy(progressMetric = key) }

    /** 首页三张卡至少留一张 */
    fun setHomeCardVisible(card: String, visible: Boolean) = update {
        val next = if (visible) it.hiddenHomeCards - card else it.hiddenHomeCards + card
        val visibleCount = 3 - next.size
        if (visibleCount < 1) it else it.copy(hiddenHomeCards = next)
    }

    fun setChipVisible(mode: DetailMode, chip: String, visible: Boolean) = update {
        when (mode) {
            DetailMode.SALARY -> it.copy(
                hiddenMoneyChips = if (visible) it.hiddenMoneyChips - chip else it.hiddenMoneyChips + chip
            )
            DetailMode.COUNTDOWN -> it.copy(
                hiddenCountdownChips = if (visible) it.hiddenCountdownChips - chip else it.hiddenCountdownChips + chip
            )
            DetailMode.RECORD_DAY -> it.copy(
                hiddenRecordChips = if (visible) it.hiddenRecordChips - chip else it.hiddenRecordChips + chip
            )
        }
    }

    fun hideAllChips(mode: DetailMode, hide: Boolean) = update {
        val all = when (mode) {
            DetailMode.SALARY -> MoneyChip.entries.map { it.key }.toSet()
            DetailMode.COUNTDOWN -> CountdownChip.entries.map { it.key }.toSet()
            DetailMode.RECORD_DAY -> RecordDayChip.entries.map { it.key }.toSet()
        }
        when (mode) {
            DetailMode.SALARY -> it.copy(hiddenMoneyChips = if (hide) all else emptySet())
            DetailMode.COUNTDOWN -> it.copy(hiddenCountdownChips = if (hide) all else emptySet())
            DetailMode.RECORD_DAY -> it.copy(hiddenRecordChips = if (hide) all else emptySet())
        }
    }

    fun setButtonVisible(id: String, visible: Boolean) = update {
        it.copy(hiddenButtons = if (visible) it.hiddenButtons - id else it.hiddenButtons + id)
    }

    // ---------------- 隐私模式 ----------------

    private fun digest(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt::$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun verifyPin(pin: String): Boolean {
        val s = _state.value
        if (!s.hasPin) return true
        return digest(pin, s.pinSalt) == s.pinHash
    }

    /** 开启隐私模式：必须给一个 4 位数字密码。返回错误提示，null = 成功。 */
    fun enablePrivacy(pin: String): String? {
        if (pin.length != 4 || pin.any { !it.isDigit() }) return "密码必须是 4 位数字"
        val salt = UUID.randomUUID().toString()
        update {
            it.copy(
                pinSalt = salt,
                pinHash = digest(pin, salt),
                privacyEnabled = true,
                privacyUnlockedUntil = 0L,
                logText = "隐私模式已开启：工资数字改为百分比进度",
            )
        }
        return null
    }

    fun disablePrivacy() = update {
        it.copy(
            privacyEnabled = false,
            privacyUnlockedUntil = 0L,
            logText = "隐私模式已关闭，金额恢复显示",
        )
    }

    /** 已有 PIN 时直接开启隐私模式（不重设密码）。 */
    fun enablePrivacyWithExistingPin() = update {
        it.copy(
            privacyEnabled = true,
            privacyUnlockedUntil = 0L,
            logText = "隐私模式已开启：工资数字改为百分比进度",
        )
    }

    /** 临时看一眼真实数字（默认 60 秒后自动恢复隐私展示） */
    fun unlockTemporarily(seconds: Long = 60) = update {
        it.copy(privacyUnlockedUntil = System.currentTimeMillis() + seconds * 1000)
    }

    fun relock() = update { it.copy(privacyUnlockedUntil = 0L) }

    /** 修改 4 位密码：先校验旧密码，再重算哈希（沿用原 salt）。返回错误提示，null = 成功。 */
    fun changePin(oldPin: String, newPin: String): String? {
        val s = _state.value
        if (!s.hasPin) return "当前没有设置密码"
        if (digest(oldPin, s.pinSalt) != s.pinHash) return "原密码不正确"
        if (newPin.length != 4 || newPin.any { !it.isDigit() }) return "新密码必须是 4 位数字"
        update {
            it.copy(
                pinHash = digest(newPin, s.pinSalt),
                privacyUnlockedUntil = 0L,
                logText = "隐私密码已修改",
            )
        }
        return null
    }

    fun completeOnboarding() = update { it.copy(onboardingDone = true) }

    // ---------------- Your Days：倒计时 ----------------

    /** 新增 / 修改倒计时。返回错误提示，null = 成功。 */
    fun upsertCountdown(item: CountdownItem, isNew: Boolean): String? {
        if (!item.valid) return "月份 1–12、日期 1–31，年份留空表示每年重复"
        countdownRangeError(item)?.let { return it }
        val s = _state.value
        if (item.name.isBlank()) return "给它起个名字吧"
        val list = if (isNew) s.countdowns + item
        else s.countdowns.map { if (it.id == item.id) item else it }
        update {
            it.copy(
                countdowns = list,
                activeCountdownId = if (isNew || it.activeCountdownId.isEmpty()) item.id else it.activeCountdownId,
                logText = if (isNew) "已新增倒计时「${item.name}」" else "已更新「${item.name}」",
            )
        }
        return null
    }

    fun deleteCountdown(id: String) = update {
        val list = it.countdowns.filterNot { c -> c.id == id }
        it.copy(
            countdowns = list,
            activeCountdownId = if (it.activeCountdownId == id) (list.firstOrNull()?.id ?: "") else it.activeCountdownId,
            logText = "已删除 1 个倒计时",
        )
    }

    fun setActiveCountdown(id: String) = update { it.copy(activeCountdownId = id) }

    // ---------------- Your Days：纪念日 ----------------

    fun upsertRecordDay(item: RecordDayItem, isNew: Boolean): String? {
        if (!item.valid) return "起始日期不合法（年份 1970–2100）"
        if (item.name.isBlank()) return "给它起个名字吧"
        val s = _state.value
        val list = if (isNew) s.recordDays + item
        else s.recordDays.map { if (it.id == item.id) item else it }
        update {
            it.copy(
                recordDays = list,
                activeRecordDayId = if (isNew || it.activeRecordDayId.isEmpty()) item.id else it.activeRecordDayId,
                logText = if (isNew) "已新增纪念日「${item.name}」" else "已更新「${item.name}」",
            )
        }
        return null
    }

    fun deleteRecordDay(id: String) = update {
        val list = it.recordDays.filterNot { r -> r.id == id }
        it.copy(
            recordDays = list,
            activeRecordDayId = if (it.activeRecordDayId == id) (list.firstOrNull()?.id ?: "") else it.activeRecordDayId,
            logText = "已删除 1 个纪念日",
        )
    }

    fun setActiveRecordDay(id: String) = update { it.copy(activeRecordDayId = id) }

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
