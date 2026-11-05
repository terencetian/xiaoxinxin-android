package com.haofan.xiaoxinxin.core

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.haofan.xiaoxinxin.R

/**
 * Android 16 的「实时更新通知」（Live Updates）。
 *
 * 系统会把满足全部条件的常驻通知提升到状态栏胶囊 / 锁屏 / 通知栏顶部，
 * 各厂商把这个形态分别叫：小米超级岛、OPPO 流体云、vivo 原子岛、原生 Live Updates。
 * 条件是：ProgressStyle 等四种样式之一 + ongoing + 有 title + 无自定义 RemoteViews +
 * 渠道不是 IMPORTANCE_MIN + 声明 POST_PROMOTED_NOTIFICATIONS + 主动 requestPromotedOngoing。
 *
 * 提升与否由系统决定，第三方无法强制 —— 用 canPostPromotedNotifications() 探测。
 */
object LiveUpdate {

    /** 系统是否允许本应用发送可提升的通知（API 36 以下恒为 false）。 */
    fun allowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.canPostPromotedNotifications()
        }.getOrDefault(false)
    }

    /**
     * 构造一条可提升的实时更新通知。
     * progressPercent：0..100，对应今日进度；0 表示还没开始计费。
     */
    @RequiresApi(36)
    fun build(
        ctx: Context,
        channelId: String,
        title: String,
        text: String,
        shortText: String,
        progressPercent: Int,
        contentIntent: PendingIntent?,
        configure: Notification.Builder.() -> Unit = {},
    ): Notification {
        val b = Notification.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_stat_money)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            // 状态栏胶囊里显示的短文案，务必短
            .setShortCriticalText(shortText)
            // 关键：明确请求提升。平台 Builder 没暴露这个方法，走 compat 用的同一个 extra 键
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            .setStyle(
                Notification.ProgressStyle()
                    .setProgress(progressPercent.coerceIn(0, 100))
                    .setProgressTrackerIcon(
                        android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat_money)
                    )
            )
        if (contentIntent != null) b.setContentIntent(contentIntent)
        b.configure()
        return b.build()
    }
}
