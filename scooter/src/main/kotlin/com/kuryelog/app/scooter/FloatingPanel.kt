package com.kuryelog.app.scooter

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.PathInterpolator
import com.kuryelog.app.core.OwnOverlays
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.scooter.FloatGeometry
import com.kuryelog.logic.scooter.FloatRules
import com.kuryelog.logic.scooter.NinebotDashboard
import com.kuryelog.logic.scooter.PanelField
import com.kuryelog.logic.scooter.ScooterUiText as T

/**
 * Yüzen top (S7; SCOOTER.md 6.3; NB `FloatingPanel.java`): scooter bağlantısı açıkken ve WEEM arka plandayken başka uygulamaların
 * (Wolt/Bolt, harita) üstünde. Hemen sürüklenir, bırakınca en yakın kenara kayar. Dokun → iki ok (sağ: yatay bar, alt: dikey sütun);
 * açık bara dokun → kapanır. Çift dokun → Scooter ayarları; uzun bas → canlı panel; "×" hedefine bırak → `floating=false` (yalnız
 * ayardan geri açılır). "Diğer uygulamaların üzerinde göster" izni gerekir; yoksa canlı panel PiP'e düşer. Düz `View` + `WindowManager`
 * (Compose yaşam döngüsü sahibi gerekmez, R-S11). Topun dikdörtgeni [OwnOverlays]'e kayıtlı (ekran okuması maskeler, CAPTURE.md 7.1).
 * Ana iş parçacığında.
 */
class FloatingPanel private constructor(private val context: Context) {
    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)
    private val lang: Lang = Store.lang(context)
    private val ink = Ink.of(OwnOverlays.dark(context))
    private val fields: List<PanelField> = fields(context)
    private val ballField: PanelField = FloatRules.ballField(LoggerPreferences.get(context).getString(FloatRules.PREF_BALL_FIELD, ""), fields)
    private val density = context.resources.displayMetrics.density
    private val ball: Int
    private val thickPct: Int
    private val hShare: Double
    private val vShare: Double
    private val textPort: Int
    private val textLand: Int
    private val margin = Math.round(FloatRules.MARGIN_DP * density)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private val view: Ball
    private val lp: WindowManager.LayoutParams
    private var open = FloatGeometry.Direction.NONE
    private var ballX: Int
    private var ballY: Int
    private var anim: ValueAnimator? = null
    private var drawn: NinebotDashboard.Snapshot? = null
    private var drawnAt = 0L
    private var redrawQueued = false
    private val redraw = Runnable { redrawQueued = false; drawnAt = SystemClock.uptimeMillis(); view.invalidate() }
    private val onFrame: (NinebotDashboard.Snapshot?) -> Unit = { frame -> if (frame == null || !frame.sameValues(drawn)) { drawn = frame; invalidate() } }
    private val tick = object : Runnable {
        override fun run() {
            if (!Scooter.active) { hide(); return }
            invalidate()
            MAIN.postDelayed(this, FloatRules.TICK_MS)
        }
    }
    private val boundsSource: () -> Rect? = { bounds() }

    init {
        val sp = LoggerPreferences.get(context)
        val s = ScooterSizes
        ball = Math.round(s[0].clamp(sp.getInt(s[0].key, s[0].default)) * density)
        thickPct = sp.getInt(s[1].key, s[1].default)
        hShare = s[2].clamp(sp.getInt(s[2].key, s[2].default)) / 100.0
        vShare = s[3].clamp(sp.getInt(s[3].key, s[3].default)) / 100.0
        textPort = s[4].clamp(sp.getInt(s[4].key, s[4].default))
        textLand = s[5].clamp(sp.getInt(s[5].key, s[5].default))
        view = Ball(context)
        lp = WindowManager.LayoutParams(
            ball, ball, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val sc = screen()
        ballX = FloatRules.initialX(if (sp.contains(FloatRules.PREF_X)) sp.getInt(FloatRules.PREF_X, 0) else null, sc[0], ball, margin)
        ballY = FloatRules.initialY(if (sp.contains(FloatRules.PREF_Y)) sp.getInt(FloatRules.PREF_Y, 0) else null, sc[1], ball, margin)
        lp.x = ballX
        lp.y = ballY
    }

    private fun attach() {
        wm.addView(view, lp)
        Scooter.frameListeners.add(onFrame)
        OwnOverlays.add(boundsSource)
        MAIN.post(tick)
    }

    private fun detach() {
        target.remove()
        arrows.remove()
        anim?.cancel()
        Scooter.frameListeners.remove(onFrame)
        OwnOverlays.remove(boundsSource)
        MAIN.removeCallbacks(tick)
        MAIN.removeCallbacks(redraw)
        try { wm.removeView(view) } catch (ignored: RuntimeException) { }
    }

    private fun invalidate() {
        if (redrawQueued) return
        val wait = FloatRules.redrawWait(SystemClock.uptimeMillis(), drawnAt)
        if (wait <= 0) redraw.run() else { redrawQueued = true; MAIN.postDelayed(redraw, wait) }
    }

    private fun screen(): IntArray {
        val b = wm.currentWindowMetrics.bounds
        return intArrayOf(b.width(), b.height())
    }

    /** Ekrandaki dikdörtgen (top ya da açık bar). */
    private fun bounds(): Rect? {
        val at = IntArray(2)
        view.getLocationOnScreen(at)
        val w = view.width
        val h = view.height
        return if (w > 0 && h > 0) Rect(at[0], at[1], at[0] + w, at[1] + h) else Rect(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height)
    }

    /** Fırlatma yönüne açılır: uzunluk [FloatGeometry] (4 değerde yatayda uzun kenarın %80'i, dikeyde %60'ı). */
    private fun expand(dir: FloatGeometry.Direction) {
        val s = screen()
        val h = dir.horizontal()
        val len = FloatGeometry.length(fields.size, h, s[0], s[1], ball, margin, if (h) hShare else vShare)
        val toEnd = dir == FloatGeometry.Direction.RIGHT || dir == FloatGeometry.Direction.DOWN
        val start = FloatGeometry.start(if (h) ballX else ballY, ball, len, toEnd, if (h) s[0] else s[1], margin)
        val thick = FloatGeometry.thickness(ball, thickPct)
        val across = FloatGeometry.cross(if (h) ballY else ballX, ball, thick, if (h) s[1] else s[0], margin)
        arrows.remove()
        open = dir
        animate(if (h) start else across, if (h) across else start, if (h) len else thick, if (h) thick else len)
    }

    private fun collapse() {
        arrows.remove()
        open = FloatGeometry.Direction.NONE
        animate(ballX, ballY, ball, ball)
    }

    private fun animate(x: Int, y: Int, w: Int, h: Int) {
        anim?.cancel()
        val x0 = lp.x
        val y0 = lp.y
        val w0 = lp.width
        val h0 = lp.height
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FloatRules.ANIM_MS
            interpolator = PathInterpolator(.2f, 0f, 0f, 1f)
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                lp.x = Math.round(x0 + (x - x0) * f)
                lp.y = Math.round(y0 + (y - y0) * f)
                lp.width = Math.round(w0 + (w - w0) * f)
                lp.height = Math.round(h0 + (h - h0) * f)
                try { wm.updateViewLayout(view, lp) } catch (ignored: RuntimeException) { }
            }
            start()
        }
    }

    private fun textScale(landscape: Boolean): Float = (if (landscape) textLand else textPort) / 100f

    /** Sürüklerken alt kenara yakın yuvarlak "×" hedefi; dokunuş almaz. */
    private val target = CloseTarget()

    private inner class CloseTarget {
        var v: View? = null
        var hot = false
        var size = 0
        var cx = 0
        var cy = 0

        fun show() {
            if (v != null) return
            size = Math.round(FloatRules.CLOSE_DP * density)
            val s = screen()
            cx = s[0] / 2
            cy = s[1] - Math.round(FloatRules.CLOSE_BOTTOM_DP * density)
            val nv = object : View(context) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                val line = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(c: Canvas) {
                    val r = width / 2f * if (hot) 1f else .82f
                    fill.color = if (hot) 0xf0d9534f.toInt() else 0xd0263445.toInt()
                    c.drawCircle(width / 2f, height / 2f, r, fill)
                    line.color = Color.WHITE
                    line.strokeWidth = r * .12f
                    line.strokeCap = Paint.Cap.ROUND
                    val a = r * .36f
                    val x = width / 2f
                    val y = height / 2f
                    c.drawLine(x - a, y - a, x + a, y + a, line)
                    c.drawLine(x - a, y + a, x + a, y - a, line)
                }
            }
            nv.contentDescription = T.CLOSE_TARGET.get(lang)
            val p = WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            p.gravity = Gravity.TOP or Gravity.START
            p.x = cx - size / 2
            p.y = cy - size / 2
            v = try { wm.addView(nv, p); nv } catch (e: RuntimeException) { null }
        }

        /** Topun merkezi bırakmaya yetecek kadar yakın: hedef büyür ve kızarır. */
        fun near(ballCx: Int, ballCy: Int): Boolean {
            val t = v ?: return false
            val h = FloatRules.nearClose(ballCx, ballCy, cx, cy, size)
            if (h != hot) {
                hot = h
                t.invalidate()
                if (h) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            return h
        }

        fun remove() {
            val t = v ?: return
            try { wm.removeView(t) } catch (ignored: RuntimeException) { }
            v = null
            hot = false
        }
    }

    /** Dokununca topun yanında iki yuvarlak ok: yan → yatay bar, alt/üst → dikey sütun; 4 sn sonra kaybolur. */
    private val arrows = Arrows()

    private inner class Arrows {
        var side: View? = null
        var cross: View? = null
        val timeout = Runnable { remove() }

        fun shown(): Boolean = side != null

        fun show() {
            remove()
            val size = Math.round(FloatRules.ARROW_DP * density)
            val gap = Math.round(FloatRules.ARROW_GAP_DP * density)
            val s = screen()
            val a = FloatRules.arrows(ballX, ballY, ball, size, gap, s[0], s[1])
            side = add(size, a.sideX, a.sideY, a.sideAngle, T.OPEN_H.get(lang)) { expand(a.side) }
            cross = add(size, a.crossX, a.crossY, a.crossAngle, T.OPEN_V.get(lang)) { expand(a.cross) }
            MAIN.postDelayed(timeout, FloatRules.ARROWS_MS)
        }

        private fun add(size: Int, x: Int, y: Int, angle: Float, label: String, action: () -> Unit): View? {
            val v = object : View(context) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                val line = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(c: Canvas) {
                    val r = width / 2f
                    fill.color = 0xe0263445.toInt()
                    c.drawCircle(r, r, r, fill)
                    line.color = Color.WHITE
                    line.strokeWidth = r * .16f
                    line.strokeCap = Paint.Cap.ROUND
                    c.save()
                    c.rotate(angle, r, r)
                    val a = r * .34f
                    c.drawLine(r - a, r, r + a, r, line)
                    c.drawLine(r + a, r, r, r - a, line)
                    c.drawLine(r + a, r, r, r + a, line)
                    c.restore()
                }
            }
            v.contentDescription = label
            v.setOnClickListener { action() }
            val p = WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
            )
            p.gravity = Gravity.TOP or Gravity.START
            p.x = x
            p.y = y
            try { wm.addView(v, p) } catch (e: RuntimeException) { return null }
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(150).start()
            return v
        }

        fun remove() {
            MAIN.removeCallbacks(timeout)
            for (v in arrayOf(side, cross)) if (v != null) try { wm.removeView(v) } catch (ignored: RuntimeException) { }
            side = null
            cross = null
        }
    }

    /** Sürüklemeden sonra top yakın kenara kayar ve yer kaydedilir. */
    private fun settle() {
        val s = screen()
        val x = FloatRules.settleX(ballX, ball, s[0], margin)
        val y = FloatRules.settleY(ballY, ball, s[1], margin)
        ballX = x
        ballY = y
        animate(x, y, ball, ball)
        LoggerPreferences.get(context).edit().putInt(FloatRules.PREF_X, x).putInt(FloatRules.PREF_Y, y).apply()
    }

    private fun openApp(target: String) {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(Scooter.OPEN_EXTRA, target)
        try { context.startActivity(i) } catch (ignored: RuntimeException) { }
    }

    @SuppressLint("ViewConstructor")
    private inner class Ball(c: Context) : View(c) {
        private val painter = FloatPainter().also { it.ink = ink; it.lang = lang }
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val doubleTap = ViewConfiguration.getDoubleTapTimeout()
        private var lastTapAt = 0L
        private var downAt = 0L
        private var velocity: VelocityTracker? = null
        private val flingPx = FloatRules.FLING_DP_PER_S * density
        private val longPress = Runnable {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            arrows.remove()
            openApp(Scooter.OPEN_PANEL)
        }
        private val singleTap = Runnable { if (arrows.shown()) arrows.remove() else arrows.show() }

        init { contentDescription = T.BALL_DESC.get(lang) }

        override fun onDraw(c: Canvas) {
            painter.textScale = textScale(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            val s = Scooter.frame
            val t = Scooter.trip.stats()
            val alpha = if (s == null) 120 else 255
            val w = width
            val h = height
            if (w <= ball + 1 && h <= ball + 1 && open == FloatGeometry.Direction.NONE) painter.ball(c, w / 2f, h / 2f, Math.min(w, h) / 2f, ballField, s, t, alpha)
            else painter.bar(c, w.toFloat(), h.toFloat(), fields, if (open != FloatGeometry.Direction.NONE) open.horizontal() else w >= h, s, t, alpha)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val dx = e.rawX - downX
            val dy = e.rawY - downY
            val closed = open == FloatGeometry.Direction.NONE
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    velocity?.recycle()
                    velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                    downAt = SystemClock.uptimeMillis()
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longPressed = false
                    anim?.cancel()
                    if (closed) postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocity?.addMovement(e)
                    if (!dragging && closed && !longPressed && (Math.abs(dx) > slop / 2f || Math.abs(dy) > slop / 2f)) {
                        dragging = true
                        removeCallbacks(longPress)
                        removeCallbacks(singleTap)
                        arrows.remove()
                        target.show()
                    }
                    if (dragging) {
                        val s = screen()
                        lp.x = FloatRules.dragTo(startX, dx, ball, s[0])
                        lp.y = FloatRules.dragTo(startY, dy, ball, s[1])
                        ballX = lp.x
                        ballY = lp.y
                        try { wm.updateViewLayout(this, lp) } catch (ignored: RuntimeException) { }
                        target.near(ballX + ball / 2, ballY + ball / 2)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPress)
                    if (longPressed) return true
                    val vt = velocity
                    if (dragging && vt != null && SystemClock.uptimeMillis() - downAt < FloatRules.FLING_WINDOW_MS) {
                        vt.addMovement(e)
                        vt.computeCurrentVelocity(1000)
                        val vx = vt.xVelocity
                        val vy = vt.yVelocity
                        if (Math.hypot(vx.toDouble(), vy.toDouble()) > flingPx) {
                            dragging = false
                            target.remove()
                            lp.x = startX; lp.y = startY; ballX = startX; ballY = startY
                            try { wm.updateViewLayout(this, lp) } catch (ignored: RuntimeException) { }
                            expand(FloatGeometry.direction(vx, vy, 1f))
                            return true
                        }
                    }
                    if (dragging) {
                        dragging = false
                        val drop = target.near(ballX + ball / 2, ballY + ball / 2)
                        target.remove()
                        if (drop) { dismiss(context); return true }
                        settle()
                        return true
                    }
                    if (!closed) { collapse(); return true }
                    performClick()
                    val now = SystemClock.uptimeMillis()
                    if (now - lastTapAt < doubleTap) {
                        lastTapAt = 0
                        removeCallbacks(singleTap)
                        arrows.remove()
                        openApp(Scooter.OPEN_SETTINGS)
                    } else {
                        lastTapAt = now
                        postDelayed(singleTap, doubleTap.toLong())
                    }
                    return true
                }
                // 0.18.0: a touch anywhere else folds an opened bar back into the ball (the touch still reaches that app). The arrows
                // wait a moment so a tap on one of them still lands.
                MotionEvent.ACTION_OUTSIDE -> {
                    if (!closed) collapse() else if (arrows.shown()) MAIN.postDelayed({ arrows.remove() }, FloatRules.OUTSIDE_ARROWS_MS)
                    return false
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPress)
                    dragging = false
                    target.remove()
                    return true
                }
                else -> return true
            }
        }

        override fun performClick(): Boolean = super.performClick()
    }

    companion object {
        private val MAIN = Handler(Looper.getMainLooper())
        private val ScooterSizes = com.kuryelog.logic.scooter.ScooterSettings.SIZES
        private val screens = FloatRules.Screens()
        private var instance: FloatingPanel? = null

        fun enabled(c: Context): Boolean = LoggerPreferences.get(c).getBoolean(FloatRules.PREF_ON, true)
        fun permitted(c: Context): Boolean = Settings.canDrawOverlays(c)
        fun fields(c: Context): List<PanelField> = PanelField.parse(LoggerPreferences.get(c).getString(FloatRules.PREF_FIELDS, ""))

        /** Top ya da bar ekranda (bağlantı iş parçacığı okur: görünürken tam tempoda okur). */
        val shown: Boolean get() = Scooter.Viewers.ball

        /** WEEM ekranı başladı: top gizlenir (NB `activityStarted`). */
        fun screenStarted() {
            screens.started()
            hide()
        }

        /** WEEM ekranı durdu; [allowShow]=false (PiP'ten çıkış) sayacı dengeler ama top göstermez. Sonuncusu da durduysa 400 ms sonra top. */
        fun screenStopped(c: Context, allowShow: Boolean = true) {
            screens.stopped()
            if (!allowShow) return
            val app = c.applicationContext
            MAIN.postDelayed({
                if (FloatRules.show(screens.started, Scooter.active, enabled(app), permitted(app))) show(app)
            }, FloatRules.SHOW_DELAY_MS)
        }

        /** "×" üstüne bırakıldı: "Yüzen panel" kapanır, yalnız ayar geri açar. */
        fun dismiss(c: Context) {
            LoggerPreferences.get(c).edit().putBoolean(FloatRules.PREF_ON, false).apply()
            hide()
        }

        private fun show(app: Context) {
            if (instance != null || !enabled(app)) return
            val f = FloatingPanel(app)
            instance = f
            try {
                f.attach()
                Scooter.Viewers.ball = true
            } catch (e: RuntimeException) {
                instance = null
            }
        }

        fun hide() {
            val f = instance ?: return
            Scooter.Viewers.ball = false
            f.detach()
            instance = null
        }
    }
}
