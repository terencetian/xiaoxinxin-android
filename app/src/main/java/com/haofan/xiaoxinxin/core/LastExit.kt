package com.haofan.xiaoxinxin.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 上次退出原因自检（ApplicationExitInfo）。
 *
 * 背景：v2.7.0 的 CrashGuard 只能抓 Java 层未捕获异常；有台小米装上点「开始使用」
 * 闪退却没有任何日志 → 说明异常发生在处理器覆盖不到的地方（原生崩溃 / ANR /
 * 被系统杀）。系统会自己记录每种退出原因，下次打开时问一下系统就知道上次怎么死的。
 *
 * API 34+ 还能拿到系统留存的 trace（原生崩溃是 tombstone，ANR 是 traces.txt），
 * 等于不用连电脑就能拿到 logcat 级别的证据。
 *
 * 只报「异常退出」（崩溃/ANR/被杀/低内存），正常退出和用户划走不报；
 * 报过一次就记标记，不反复弹。
 */
object LastExit {

    /** 值得报告的退出原因（用系统常量名映射，避免裸数字）。 */
    private val INTERESTING = setOf(
        ApplicationExitInfo.REASON_CRASH,                  // Java 崩溃
        ApplicationExitInfo.REASON_CRASH_NATIVE,          // 原生崩溃
        ApplicationExitInfo.REASON_ANR,                   // 应用无响应
        ApplicationExitInfo.REASON_LOW_MEMORY,            // 低内存被杀
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_OTHER,
    )

    const val MAX_TRACE = 240 * 1024

    data class Report(
        val reason: Int,
        val reasonName: String,
        val description: String,
        val timestamp: Long,
        val trace: String?,
    ) {
        fun summary(): String = buildString {
            appendLine("上次退出时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))}")
            appendLine("退出原因：$reasonName (code=$reason)")
            if (description.isNotBlank()) appendLine("系统描述：$description")
            appendLine("机型：${Build.MANUFACTURER} ${Build.MODEL}  Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App 版本：${com.haofan.xiaoxinxin.BuildConfig.VERSION_NAME}")
            trace?.let {
                appendLine()
                appendLine("===== 系统 trace =====")
                appendLine(it)
            }
        }
    }

    fun reasonNameOf(code: Int): String = when (code) {
        ApplicationExitInfo.REASON_CRASH -> "Java 崩溃（未捕获异常）"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "原生崩溃（native crash）"
        ApplicationExitInfo.REASON_ANR -> "应用无响应（ANR）"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "系统低内存被杀"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "资源占用过高被杀"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "初始化失败"
        ApplicationExitInfo.REASON_OTHER -> "其他（含被厂商框架终止）"
        else -> "正常/其他退出"
    }

    /** 读最近一次退出记录。不是异常退出返回 null。 */
    fun collect(ctx: Context): Report? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val exits: List<ApplicationExitInfo> =
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 3) ?: return null
        val last = exits.firstOrNull() ?: return null
        if (last.reason !in INTERESTING) return null
        // 只看 7 天内的，太旧的没有诊断价值
        if (System.currentTimeMillis() - last.timestamp > 7L * 24 * 3600 * 1000) return null

        val trace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                last.traceInputStream?.use { input ->
                    val buf = ByteArrayOutputStream()
                    val tmp = ByteArray(16 * 1024)
                    var total = 0
                    while (total < MAX_TRACE) {
                        val n = input.read(tmp, 0, minOf(tmp.size, MAX_TRACE - total))
                        if (n < 0) break
                        buf.write(tmp, 0, n)
                        total += n
                    }
                    String(buf.toByteArray(), Charsets.UTF_8)
                }
            }.getOrNull()
        } else null

        return Report(
            reason = last.reason,
            reasonName = reasonNameOf(last.reason),
            description = last.description.orEmpty(),
            timestamp = last.timestamp,
            trace = trace?.takeIf { it.isNotBlank() },
        )
    }

    /** 落盘到「下载」，用户可以原样转发。 */
    fun saveToDownloads(ctx: Context, r: Report) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(r.timestamp))
            val text = r.summary()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "xiaoxinxin-exit-$stamp.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        }
    }

    // ---- 已报告标记（时间戳+原因，重复弹） ----
    private const val KEY = "exitReportedKeys"
    private const val PREF = "xiaoxinxin"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun wasReported(ctx: Context, r: Report): Boolean =
        prefs(ctx).getString(KEY, "")?.split("|")?.contains("${r.timestamp}-${r.reason}") == true

    fun markReported(ctx: Context, r: Report) {
        val p = prefs(ctx)
        val old = p.getString(KEY, "").orEmpty()
        val next = (old.split("|").filter { it.isNotBlank() } + "${r.timestamp}-${r.reason}")
            .takeLast(10)
            .joinToString("|")
        p.edit().putString(KEY, next).apply()
    }
}
