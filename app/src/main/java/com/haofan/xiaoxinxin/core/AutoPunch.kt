package com.haofan.xiaoxinxin.core

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * 自动打卡上班的调度与触发。
 *
 * ══ 为什么用系统闹钟 ══════════════════════════════════════════
 * 到点自动下班靠 tick() 就行 —— 那时 App 本来就在跑（悬浮窗或前台）。
 * 自动上班不行：9 点打卡时用户多半没开 App，进程可能是死的。
 * 唯一能在 App 未运行时唤醒我们的东西就是 AlarmManager。
 *
 * 精度取舍：setExactAndAllowWhileIdle 在 Android 12+ 需要「闹钟和提醒」授权，
 * 没授权自动降级 setWindow（±10 分钟窗口），打卡场景完全够用 ——
 * 毕竟 App 没运行的时段本来就不计费，晚几分钟打卡只是晚几分钟开始滚数字。
 *
 * ══ 开机自启动 ═══════════════════════════════════════════════
 * 本应用没有「开机后常驻」的需求（计费靠打卡时刻现算，不靠进程活着）。
 * 所谓自启动，实际是 BOOT_COMPLETED 后把上面的闹钟重新排上 ——
 * 重启会清掉 AlarmManager 里所有闹钟，不补排的话自动上班就断了。
 * 厂商杀后台（小米/华为等）可能拦掉 BOOT_COMPLETED，提示页里已说明。
 */
object AutoPunch {

    private const val CHANNEL = "auto_punch"
    private const val REQ_ALARM = 4001

    const val ACTION_CLOCK_IN = "com.haofan.xiaoxinxin.AUTO_CLOCK_IN"

    /** 重新排下一次「自动上班」闹钟。autoClockIn 关闭时取消所有已排闹钟。
     *  幂等：随时调用都安全（改设置、开机、启动 App 都会调）。 */
    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(ctx)
        am.cancel(pi) // 无论开关都先清旧的，避免改了上班时间还按旧的响

        val s = EarnRepo.state.value
        if (!s.autoClockIn) return
        val trigger = nextWorkStart(s.workStart, s.workEnd) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // 未授权精确闹钟：退到窗口闹钟，系统保证在 [trigger, trigger+10min] 内响
                am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 10 * 60_000L, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (_: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 10 * 60_000L, pi)
        }
    }

    /** 下一个班次开始的 epoch millis。已过今天的上班时刻就排明天（不补打）。 */
    private fun nextWorkStart(startStr: String, endStr: String): Long? {
        val t = try {
            LocalTime.parse(startStr.trim())
        } catch (_: DateTimeParseException) {
            return null
        }
        var d = LocalDateTime.now().toLocalDate()
        var at = d.atTime(t)
        val end = try { LocalTime.parse(endStr.trim()) } catch (_: DateTimeParseException) { return null }
        // 跨零点班（如 22:00–06:00）：今天 22:00 的班其实是「今晚开始」，不算已过
        val startAfterEnd = t >= end
        if (at.isBefore(LocalDateTime.now()) && !startAfterEnd) {
            d = d.plusDays(1)
            at = d.atTime(t)
        }
        if (at.isBefore(LocalDateTime.now())) at = at.plusDays(1)
        return at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun alarmIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ_ALARM,
            Intent(ctx, AutoPunchReceiver::class.java).setAction(ACTION_CLOCK_IN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 到点打卡后发一条安静的通知（进通知栏，不出声），让用户知道今天已经开始计费。 */
    fun notifyClockedIn(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "自动打卡", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { setSound(null, null) }
            )
        }
        val s = EarnRepo.state.value
        val text = "${timeStr(EarnRepo.nowLocal())} 已自动打卡上班，今日到账 ¥${fmt(s.displayEarned())}"
        val n: Notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("小薪薪")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("小薪薪")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
            }
        try {
            nm.notify(4002, n)
        } catch (_: Exception) {
        }
    }
}

/**
 * 两个触发口共用一个 receiver：
 *  - BOOT_COMPLETED / MY_PACKAGE_REPLACED → 恢复闹钟（仅在「开机自启动」开关打开时）；
 *  - AUTO_CLOCK_IN → 判定并打卡，然后排明天的。
 * exported=false：只收系统广播和自己的 PendingIntent，外部应用唤不起来。
 */
class AutoPunchReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        EarnRepo.init(ctx.applicationContext)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (EarnRepo.state.value.autoBoot) AutoPunch.reschedule(ctx)
            }
            AutoPunch.ACTION_CLOCK_IN -> {
                if (EarnRepo.autoClockInIfNeeded()) AutoPunch.notifyClockedIn(ctx)
                AutoPunch.reschedule(ctx) // 排下一个班次（明天）
            }
        }
    }
}

/** 计划上班时刻（跨零点班次落到前一天）。与 Logic.shiftEndTime 对称。 */
fun scheduledStart(now: LocalDateTime, startStr: String, endStr: String): LocalDateTime? {
    val t = try {
        LocalTime.parse(startStr.trim())
    } catch (_: DateTimeParseException) {
        return null
    }
    val end = try {
        LocalTime.parse(endStr.trim())
    } catch (_: DateTimeParseException) {
        return null
    }
    val startToday = now.toLocalDate().atTime(t)
    return if (t >= end && now.isBefore(now.toLocalDate().atTime(end))) startToday.minusDays(1)
    else startToday
}
