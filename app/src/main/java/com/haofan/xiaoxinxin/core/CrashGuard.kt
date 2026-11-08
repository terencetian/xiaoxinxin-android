package com.haofan.xiaoxinxin.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃守门员：任何未捕获异常先落盘再交给系统。
 *
 * 为什么需要：小米 HyperOS 拿 logcat 麻烦，用户复现一次闪退后，
 * 堆栈会自动出现在「下载」文件夹里（Download/xiaoxinxin-crash-*.txt），
 * 用文件管理器或微信就能发回来，不用连电脑。
 *
 * 写两份：
 *  1. 公共 Downloads（MediaStore，API 29+ 免权限，用户最容易拿到）
 *  2. 应用专属外部目录（/sdcard/Android/data/.../files/，连 adb 时 run-as 兜底）
 *
 * 只记录最近 5 份，防止反复闪退把存储塞满。处理完原样交还系统默认处理器，
 * 不吞崩溃、不阻止系统弹窗。
 */
object CrashGuard {

    private const val MAX_KEEP = 5

    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(ctx.applicationContext, e) }
            prev?.uncaughtException(t, e)
        }
    }

    private fun write(ctx: Context, e: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.appendLine("小薪薪崩溃日志 $stamp")
        sb.appendLine("model=${Build.MANUFACTURER} ${Build.MODEL}  android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("version=${com.haofan.xiaoxinxin.BuildConfig.VERSION_NAME}")
        sb.appendLine()
        sb.appendLine(android.util.Log.getStackTraceString(e))

        val text = sb.toString()

        // 1) 公共 Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "xiaoxinxin-crash-$stamp.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        }

        // 2) 应用专属目录 + 轮转清理
        runCatching {
            val dir = File(ctx.getExternalFilesDir(null), "crash").apply { mkdirs() }
            File(dir, "xiaoxinxin-crash-$stamp.txt").writeText(text)
            dir.listFiles()?.sortedByDescending { it.name }?.drop(MAX_KEEP)?.forEach { it.delete() }
        }
    }
}
