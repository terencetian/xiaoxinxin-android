package com.haofan.xiaoxinxin.floatx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.haofan.xiaoxinxin.MainActivity
import com.haofan.xiaoxinxin.R
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.EarnState
import com.haofan.xiaoxinxin.core.fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 前台服务：维持屏幕上的悬浮进账窗 + 通知栏常驻。
 * 金额永远由「打卡时刻 + 当前时间」现算，服务被系统重启也不会算错。
 */
class FloatService : Service() {

    companion object {
        private const val CHANNEL_ID = "xiaoxinxin_float"
        private const val NOTIF_ID = 1001

        fun syncVisibility(ctx: Context, show: Boolean) {
            val i = Intent(ctx, FloatService::class.java)
            if (show) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } else {
                ctx.stopService(i)
            }
        }
    }

    private lateinit var wm: WindowManager
    private var view: FloatingView? = null
    private var lp: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var notifCounter = 0

    override fun onCreate() {
        super.onCreate()
        EarnRepo.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(EarnRepo.state.value))
        addOverlay()
        ticker = scope.launch {
            while (true) {
                val s = EarnRepo.state.value
                EarnRepo.tick()
                view?.update(s)
                if (++notifCounter % 10 == 0) notify(buildNotification(EarnRepo.state.value))
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            EarnRepo.setFloatEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- 悬浮窗 ----------------

    private fun addOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val s = EarnRepo.state.value
        val v = FloatingView(this)
        v.update(s)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val w = dp(166)
        val h = dp(58)
        val metrics = resources.displayMetrics
        val p = WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (s.floatX >= 0) s.floatX else metrics.widthPixels - w - dp(12)
            y = if (s.floatY >= 0) s.floatY else dp(120)
        }
        runCatching { wm.addView(v, p) }.onSuccess {
            view = v
            lp = p
            attachTouch(v, p)
        }
    }

    private fun attachTouch(v: FloatingView, p: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var lastTap = 0L

        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) moved = true
                    if (moved) {
                        p.x = startX + dx.toInt()
                        p.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        EarnRepo.setFloatPosition(p.x, p.y)
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastTap < 320) {
                            // 双击：收起悬浮窗
                            EarnRepo.setFloatEnabled(false)
                            stopSelf()
                        } else {
                            v.toggleMini()
                            val targetH = if (v.isMini) dp(34) else dp(58)
                            val targetW = if (v.isMini) dp(112) else dp(166)
                            p.width = targetW
                            p.height = targetH
                            runCatching { wm.updateViewLayout(v, p) }
                        }
                        lastTap = now
                    }
                }
            }
            true
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------- 通知 ----------------

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        mgr.createNotificationChannel(ch)
    }

    private fun buildNotification(s: EarnState): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val earned = s.displayEarned()
        val text = if (s.working) "今日已到账 ¥${fmt(earned)}" else "未计费 · 点开打卡"
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, FloatService::class.java).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(if (s.working) R.string.notif_title_tracking else R.string.notif_title_idle))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .addAction(0, getString(R.string.notif_action_stop), stopPi)
            .build()
    }

    private fun notify(n: Notification) {
        runCatching { androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF_ID, n) }
    }
}

/** 纯 Canvas 绘制的悬浮窗：圆角卡片 + 金额 + 进度条。 */
class FloatingView(ctx: Context) : View(ctx) {

    var isMini = false
        private set

    private var state: EarnState = EarnState()
    private val density = resources.displayMetrics.density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE60E1A2E.toInt() }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
        color = 0x33FFD257
    }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD257.toInt() }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8CF2F5FA.toInt()
        textSize = 9.5f * density
    }

    private val moneyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD257.toInt()
        textSize = 17f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rect = RectF()

    fun toggleMini() {
        isMini = !isMini
        moneyPaint.textSize = (if (isMini) 13f else 17f) * density
        requestLayout()
        invalidate()
    }

    fun update(s: EarnState) {
        state = s
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = 14f * density
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, bgPaint)
        canvas.drawRoundRect(rect, r, r, strokePaint)

        val earned = state.displayEarned()
        val money = "¥" + fmt(earned)

        if (isMini) {
            canvas.drawText(money, 12f * density, height / 2f + moneyPaint.textSize / 3f, moneyPaint)
            return
        }

        // 左侧金色竖条
        val barW = 2.5f * density
        val barR = RectF(10f * density, 14f * density, 10f * density + barW, height - 14f * density)
        canvas.drawRoundRect(barR, barW / 2, barW / 2, goldPaint)

        canvas.drawText(money, 20f * density, 26f * density, moneyPaint)

        val label = if (state.working) {
            val m = state.workedMinutes()
            "已工作 ${m / 60}h${m % 60}m · ${fmt(state.progress() * 100, 0)}%"
        } else "未计费"

        canvas.drawText(label, 20f * density, 42f * density, subPaint)

        // 底部细进度条
        val padL = 20f * density
        val padR = 10f * density
        val y = height - 7f * density
        val track = RectF(padL, y, width - padR, y + 2.5f * density)
        canvas.drawRoundRect(track, 1.25f * density, 1.25f * density, trackPaint)
        val p = state.progress().toFloat().coerceIn(0f, 1f)
        if (p > 0f) {
            val fill = RectF(padL, y, padL + (track.width() * p), y + 2.5f * density)
            canvas.drawRoundRect(fill, 1.25f * density, 1.25f * density, goldPaint)
        }
    }
}
