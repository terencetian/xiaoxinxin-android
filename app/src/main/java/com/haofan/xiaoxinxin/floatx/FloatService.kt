package com.haofan.xiaoxinxin.floatx

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.haofan.xiaoxinxin.MainActivity
import com.haofan.xiaoxinxin.R
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.EarnState
import com.haofan.xiaoxinxin.core.LiveUpdate
import com.haofan.xiaoxinxin.core.fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 前台服务：维持通知栏的「实时更新通知」（Android 16 上会被系统提升成岛）
 * 以及一枚贴在顶部的悬浮药丸岛。
 *
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

        /**
         * 两个开关共用同一个前台服务：悬浮岛要它来画，系统岛要它来发通知。
         * 只要还有一个开着就得跑；都关了才停。
         */
        fun needed(ctx: Context) {
            val s = EarnRepo.state.value
            syncVisibility(ctx, s.floatEnabled || s.islandEnabled)
        }

        /** 设置页拨完开关调用：先决定该不该跑，再让已在跑的服务就地应用新状态。 */
        fun applyToggle(ctx: Context) {
            needed(ctx)
            val s = EarnRepo.state.value
            if (s.floatEnabled || s.islandEnabled) {
                runCatching {
                    ctx.startService(Intent(ctx, FloatService::class.java).setAction("sync"))
                }
            }
        }

        /** 主界面在前台时自动收起悬浮岛 —— 否则它会压在自己的标题栏上。 */
        @Volatile
        private var appForeground = false

        fun setAppForeground(ctx: Context, fg: Boolean) {
            if (appForeground == fg) return
            appForeground = fg
            if (!EarnRepo.state.value.floatEnabled) return
            val i = Intent(ctx, FloatService::class.java)
                .setAction(if (fg) "hide_overlay" else "show_overlay")
            runCatching { ctx.startService(i) }
        }
    }

    private lateinit var wm: WindowManager
    private var view: FloatingView? = null
    private var lp: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var animator: ValueAnimator? = null
    private var ticks = 0

    private var screenW = 0
    private var screenH = 0
    private var topInset = 0

    override fun onCreate() {
        super.onCreate()
        EarnRepo.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        computeMetrics()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(EarnRepo.state.value))
        if (EarnRepo.state.value.floatEnabled) addOverlay()
        ticker = scope.launch {
            while (true) {
                val s = EarnRepo.state.value
                EarnRepo.tick()
                view?.update(s)
                // 通知每 3 秒刷一次：金额每秒只涨 0.6 分，刷太快只是耗电
                if (++ticks % 3 == 0) notify(buildNotification(EarnRepo.state.value))
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "stop" -> {
                EarnRepo.setFloatEnabled(false)
                EarnRepo.setIslandEnabled(false)
                stopSelf()
                return START_NOT_STICKY
            }
            "hide_overlay" -> setOverlayVisible(false)
            "show_overlay" -> setOverlayVisible(true)
            "sync" -> applyToggles()
        }
        if (!EarnRepo.state.value.floatEnabled && !EarnRepo.state.value.islandEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /** 设置页拨了开关后，就地增删悬浮窗、重发通知，不用把服务重启一遍。 */
    private fun applyToggles() {
        val s = EarnRepo.state.value
        if (s.floatEnabled) {
            if (view == null) addOverlay() else setOverlayVisible(true)
        } else {
            view?.let { runCatching { wm.removeView(it) } }
            view = null
            lp = null
            overlayAttached = false
        }
        notify(buildNotification(s))
    }

    override fun onDestroy() {
        ticker?.cancel()
        animator?.cancel()
        scope.cancel()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- 尺寸 ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun computeMetrics() {
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        topInset = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) error("legacy")
            val w = getSystemService(WindowManager::class.java)
            w.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                ).top
        }.getOrElse {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) resources.getDimensionPixelSize(id) else dp(28)
        }
    }

    private fun sizeOf(m: FloatingView.Mode): Pair<Int, Int> = when (m) {
        FloatingView.Mode.MINI -> dp(88) to dp(32)
        FloatingView.Mode.PILL -> dp(132) to dp(36)
        FloatingView.Mode.CARD -> dp(316) to dp(150)
    }.let { (w, h) -> w to h + touchPad() }

    /**
     * 状态栏区域里的触摸会被系统 StatusBar 窗口吃掉，覆盖层抢不到。
     * 所以视觉上贴顶、窗口底部再挂一条透明触摸带 —— 药丸画在上面，
     * 手指点在它下面一点也照样有效。
     */
    private fun touchPad(): Int = dp(20)

    /**
     * 药丸的默认纵坐标：上探到状态栏里约 45%，下半截露在状态栏下面。
     *
     * 为什么不能完全贴顶：状态栏窗口盖在覆盖层之上，落在状态栏范围内的触摸
     * 由它接管，覆盖层一个事件都收不到（实测 114px 以上全部被吃掉）。
     * 上探 45% 已经是「看起来长在屏幕上」和「点得到」之间的平衡点。
     */
    /**
     * 拖动/布局的下界保护栏 —— 药丸最多探进状态栏 16dp。
     *
     * 状态栏范围内的触摸由系统 StatusBar 接管，覆盖层收不到（实测整个状态栏高度全被吃掉），
     * 所以一旦让药丸滑得更靠上，用户就会觉得「点不动了」，同时还挡住了时间和信号。
     */
    private fun guardY(): Int = (topInset - dp(16)).coerceAtLeast(0)

    private fun islandTop(winH: Int): Int {
        val visualH = winH - touchPad()
        return (topInset - (visualH * 0.45f).toInt()).coerceAtLeast(guardY())
    }

    // ---------------- 悬浮岛 ----------------

    private fun addOverlay() {
        if (view != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val s = EarnRepo.state.value
        val v = FloatingView(this)
        v.update(s)
        val mode = if (s.floatMini) FloatingView.Mode.MINI else FloatingView.Mode.PILL
        v.mode = mode
        v.morph = 0f

        val (w, h) = sizeOf(mode)
        val cx = if (s.floatX >= 0) s.floatX else screenW / 2
        val y = if (s.floatY >= 0) s.floatY else islandTop(h)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 不设这个的话窗口会被系统挡在挖孔/状态栏下方，贴顶和触摸都会失效
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            x = (cx - w / 2).coerceIn(0, (screenW - w).coerceAtLeast(0))
            this.y = y.coerceIn(guardY(), (screenH - h).coerceAtLeast(guardY()))
        }
        runCatching { wm.addView(v, p) }.onSuccess {
            view = v
            lp = p
            attachTouch(v, p)
            overlayAttached = true
            if (appForeground) setOverlayVisible(false)
        }
    }

    private var overlayAttached = false

    private fun setOverlayVisible(visible: Boolean) {
        val v = view ?: return
        val p = lp ?: return
        if (visible == overlayAttached) return
        overlayAttached = visible
        if (visible) runCatching { wm.addView(v, p) } else runCatching { wm.removeView(v) }
    }

    /** 把窗口摆到 (cx, y)，以中心对齐，窗口宽高变化时不会左右乱跳。 */
    private fun place(w: Int, h: Int, cx: Int, y: Int) {
        val v = view ?: return
        val p = lp ?: return
        p.width = w
        p.height = h
        p.x = (cx - w / 2).coerceIn(0, (screenW - w).coerceAtLeast(0))
        p.y = y.coerceIn(guardY(), (screenH - h).coerceAtLeast(guardY()))
        runCatching { wm.updateViewLayout(v, p) }
    }

    /** 在三种形态之间做形变动画：宽度高度插值，内部内容靠 morph 交叉过渡。 */
    private fun animateTo(target: FloatingView.Mode) {
        val v = view ?: return
        val p = lp ?: return
        animator?.cancel()

        val fromW = p.width
        val fromH = p.height
        val (toW, toH) = sizeOf(target)
        val fromMorph = v.morph
        val toMorph = if (target == FloatingView.Mode.CARD) 1f else 0f
        val cx = p.x + fromW / 2
        val y = p.y

        v.mode = target
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            // 轻微过冲：展开时像「弹出来」，收起时像「吸回去」，比匀速减速有生气
            interpolator = OvershootInterpolator(0.75f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                val w = (fromW + (toW - fromW) * t).roundToInt()
                val h = (fromH + (toH - fromH) * t).roundToInt()
                v.morph = fromMorph + (toMorph - fromMorph) * t
                place(w, h, cx, y)
            }
            start()
        }
    }

    private fun attachTouch(v: FloatingView, p: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var lastTapAt = 0L
        val handler = Handler(Looper.getMainLooper())
        var longPress: Runnable? = null
        var longFired = false
        var singleTap: Runnable? = null

        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = p.x
                    startY = p.y
                    moved = false
                    longFired = false
                    longPress = Runnable {
                        longFired = true
                        EarnRepo.setFloatEnabled(false)
                        toast("悬浮岛已隐藏，可在设置里重新打开")
                        stopSelf()
                    }.also { handler.postDelayed(it, 700) }
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > dp(5) || abs(dy) > dp(5))) {
                        moved = true
                        longPress?.let { handler.removeCallbacks(it) }
                        animator?.cancel()
                    }
                    if (moved) {
                        p.x = startX + dx.toInt()
                        // 顶部保护栏：再往上就滑进状态栏，既挡住时间和信号，又点不到
                        p.y = (startY + dy.toInt())
                            .coerceIn(guardY(), (screenH - p.height).coerceAtLeast(guardY()))
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPress?.let { handler.removeCallbacks(it) }
                    if (longFired) return@setOnTouchListener true
                    if (moved) {
                        settle()
                    } else {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTapAt < 300) {
                            singleTap?.let { handler.removeCallbacks(it) }
                            singleTap = null
                            lastTapAt = 0
                            // 双击：迷你（只显示金额）↔ 药丸
                            animateTo(
                                if (v.mode == FloatingView.Mode.MINI) FloatingView.Mode.PILL
                                else FloatingView.Mode.MINI
                            )
                            EarnRepo.setFloatMini(v.mode == FloatingView.Mode.MINI)
                        } else {
                            lastTapAt = now
                            // 延时执行，避免和双击抢
                            singleTap = Runnable {
                                animateTo(
                                    if (v.mode == FloatingView.Mode.CARD) FloatingView.Mode.PILL
                                    else FloatingView.Mode.CARD
                                )
                                EarnRepo.setFloatMini(false)
                            }.also { handler.postDelayed(it, 280) }
                        }
                    }
                }
            }
            true
        }
    }

    /** 松手后贴边：靠得近就吸到边上，否则留在原地；顺便把位置存下来。 */
    private fun settle() {
        val p = lp ?: return
        val h = p.height
        val cx = p.x + p.width / 2
        val margin = dp(10)
        val edge = p.width / 2 + margin
        val target = when {
            cx < screenW * 0.35f -> edge
            cx > screenW * 0.65f -> screenW - edge
            else -> cx
        }
        val startCx = cx
        val y = p.y
        val w = p.width
        if (target != startCx) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 160
                interpolator = DecelerateInterpolator()
                addUpdateListener { a ->
                    val t = a.animatedValue as Float
                    place(w, h, (startCx + (target - startCx) * t).roundToInt(), y)
                }
                start()
            }
        }
        EarnRepo.setFloatPosition(target, y.coerceIn(guardY(), (screenH - h).coerceAtLeast(guardY())))
    }

    private fun toast(msg: String) {
        runCatching { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

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
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, FloatService::class.java).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val money = "¥" + fmt(s.displayEarned())
        val mins = s.workedMinutes()
        val title = getString(if (s.working) R.string.notif_title_tracking else R.string.notif_title_idle)
        val text = if (s.working) "$money · 已工作 ${mins / 60}h${mins % 60}m" else "点开打卡开始计费"

        // Android 16：走系统「实时更新」，满足条件会被提升成岛。
        // 只有用户自己打开了「灵动岛」开关才这么发 —— 否则就是一条普通常驻通知。
        if (s.islandEnabled && Build.VERSION.SDK_INT >= 36 && LiveUpdate.allowed(this)) {
            return LiveUpdate.build(
                this, CHANNEL_ID, title, text, money,
                (s.progress() * 100).toInt(), pi,
            ) {
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@FloatService, R.drawable.ic_stat_money),
                        getString(R.string.notif_action_stop),
                        stopPi,
                    ).build()
                )
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_money)
            .setContentTitle(title)
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

/**
 * 顶部悬浮药丸岛。纯 Canvas 绘制，自己管理三种形态：
 * MINI = 只有金额，PILL = 今日金额，CARD = 展开卡（今日/本月/工作时长/进度）。
 *
 * 形变由外部把窗口尺寸插值驱动，本视图用 morph(0..1) 做内容交叉过渡，
 * 这样窗口和画面永远是同一帧，不会出现「窗口到了、内容还在路上」。
 */
class FloatingView(ctx: Context) : View(ctx) {

    enum class Mode { MINI, PILL, CARD }

    private companion object {
        val GOLD = 0xFFFFD257.toInt()
        val SUB = 0x8CF2F5FA.toInt()
        val LABEL = 0xB3FFD257.toInt()
        val FAINT = 0x66F2F5FA.toInt()
        val TRACK = 0x26FFFFFF
    }

    var mode: Mode = Mode.PILL
        set(v) {
            field = v
            invalidate()
        }

    var morph: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    private var state: EarnState = EarnState()
    private val d = resources.displayMetrics.density
    private val sd = resources.displayMetrics.scaledDensity

    /** 窗口底部留出的透明触摸带高度，对应 FloatService.touchPad()。 */
    private val bottomPad = 20f * d

    private fun px(v: Float) = v * d
    private fun sp(v: Float) = v * sd

    /**
     * 纯黑底。系统岛在 OLED 上就是用纯黑跟挖孔、边框融成一体，
     * 所以这里不能再用半透明深蓝 —— 那会显得像「一个浮在外面的卡片」。
     */
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f * d
        color = 0x33FFFFFF
    }

    /** 内侧一圈极淡高光：让纯黑药丸在深色壁纸上也有轮廓，不至于糊成一团。 */
    private val innerEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
        color = 0x14FFFFFF
    }
    private val moneyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GOLD
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUB }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LABEL
        typeface = Typeface.DEFAULT
    }
    private val faintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FAINT }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TRACK }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
    private val goldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
    private val rect = RectF()

    /**
     * 按比例缩放颜色自身的 alpha。
     * 注意不能用 Paint.setAlpha —— 它会把颜色里的 alpha 整个覆盖掉，
     * 于是 15% 白的轨道会长成一条纯白实线。
     */
    private fun tint(base: Int, f: Float): Int {
        val a = ((base ushr 24) * f.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (base and 0x00FFFFFF) or (a shl 24)
    }

    fun update(s: EarnState) {
        state = s
        invalidate()
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        // 底部这条是触摸带，只吃不画 —— 视觉高度和窗口高度不是一回事。
        val h = height.toFloat() - bottomPad
        if (w <= 0f || h <= 0f) return
        val m = morph

        // 药丸圆角：收起来是全圆角，展开是 26dp
        val r = if (mode == Mode.MINI) h / 2 else lerp(h / 2, px(26f), m)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, bgPaint)
        rect.set(d, d, w - d, h - d)
        canvas.drawRoundRect(rect, (r - d).coerceAtLeast(0f), (r - d).coerceAtLeast(0f), innerEdgePaint)
        rect.set(0.4f, 0.4f, w - 0.4f, h - 0.4f)
        canvas.drawRoundRect(rect, r, r, edgePaint)

        val money = "¥" + fmt(state.displayEarned())
        val pad = px(16f)

        if (mode == Mode.MINI) {
            moneyPaint.textSize = sp(14f)
            moneyPaint.color = GOLD
            val mw = moneyPaint.measureText(money)
            canvas.drawText(money, (w - mw) / 2f, h / 2f + moneyPaint.textSize * 0.36f, moneyPaint)
            return
        }

        // —— 收起态：呼吸金点 + 金额，整体居中，像岛里的一条实时读数 ——
        val pillF = 1f - m
        if (pillF > 0.02f) {
            var dotR = px(2.8f)
            if (state.working) {
                // 呼吸感：靠时间算半径，不用动画器
                val phase = (SystemClock.uptimeMillis() % 1600L) / 1600f
                dotR *= 0.8f + 0.35f * kotlin.math.sin(phase * 6.283f).toFloat()
            }
            moneyPaint.textSize = lerp(sp(15f), sp(27f), m)
            moneyPaint.color = tint(GOLD, pillF)
            val mw = moneyPaint.measureText(money)
            val gap = px(7f)
            val left = (w - (dotR * 2 + gap + mw)) / 2f
            val base = h / 2f + moneyPaint.textSize * 0.36f
            dotPaint.color = tint(GOLD, pillF)
            canvas.drawCircle(left + dotR, h / 2f, dotR, dotPaint)
            canvas.drawText(money, left + dotR * 2 + gap, base, moneyPaint)
        }

        // —— 展开态 ——
        if (m > 0.02f) {
            labelPaint.textSize = sp(10.5f)
            labelPaint.color = tint(LABEL, m)
            canvas.drawText("今日已到账", pad, px(28f), labelPaint)

            faintPaint.textSize = sp(10f)
            faintPaint.color = tint(FAINT, m)
            val month = "本月 ¥" + fmt(state.monthEarned())
            canvas.drawText(month, w - pad - faintPaint.measureText(month), px(28f), faintPaint)

            moneyPaint.textSize = lerp(sp(15f), sp(27f), m)
            moneyPaint.color = tint(GOLD, m)
            canvas.drawText(money, pad, lerp(h / 2f, px(60f), m), moneyPaint)

            subPaint.textSize = sp(10.5f)
            subPaint.color = tint(SUB, m)
            val mins = state.workedMinutes()
            val sub = if (state.working)
                "已工作 ${mins / 60}h${mins % 60}m · ${fmt(state.progress() * 100, 0)}%"
            else "未计费 · 点开打卡"
            canvas.drawText(sub, pad, px(84f), subPaint)

            // 进度条
            val barH = px(4f)
            val by = h - px(20f)
            trackPaint.color = tint(TRACK, m)
            val track = RectF(pad, by, w - pad, by + barH)
            canvas.drawRoundRect(track, barH / 2, barH / 2, trackPaint)
            val p = state.progress().toFloat().coerceIn(0f, 1f)
            if (p > 0f) {
                goldFill.color = tint(GOLD, m)
                val fill = RectF(pad, by, pad + track.width() * p, by + barH)
                canvas.drawRoundRect(fill, barH / 2, barH / 2, goldFill)
            }
        }
    }
}
