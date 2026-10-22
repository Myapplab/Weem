package com.kuryelog.app.map

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.EditText
import com.kuryelog.app.core.OwnOverlays
import com.kuryelog.app.core.WeemFonts
import com.kuryelog.logic.core.OverlayPalette
import com.kuryelog.logic.map.calibration.CalibrationPhase
import com.kuryelog.logic.map.calibration.CalibrationState
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Yol kalibrasyonunun yüzen paneli (YolGo `RoadCalibrationOverlay`, RAW Stats FloatingPanel/FloatPainter'dan): uygulamaların üstünde, hemen sürüklenir,
 * kenar konumunu hatırlar; kapalıyken yuvarlak top, açıkken hap şeklinde çubuk (BAŞLA · BİTİR · YOL KALİTESİ 1–10 · SÜRE · HIZ MODU). Seçimler
 * [action] ile servise gider. Wolt/Bolt'a dokunmaz. Cihazda denenmedi.
 */
internal class RoadCalibrationOverlay(private val context: Context, private val action: (String, Map<String, Any?>) -> Unit) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences("road_calibration_overlay_v1", Context.MODE_PRIVATE)
    private val d = context.resources.displayMetrics.density
    private val ball = (76 * d).roundToInt()
    private val margin = (12 * d).roundToInt()
    private val view = Panel(context)
    private val lp = WindowManager.LayoutParams(
        ball, ball, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }
    private var state = CalibrationState()
    private var expanded = false
    private var options: String? = null
    private var slide = 1f
    private var animator: ValueAnimator? = null
    private var dialog: AlertDialog? = null
    private var attached = false
    private var ballX = 0
    private var ballY = 0
    private val phase get() = state.phase
    private val busy get() = phase != CalibrationPhase.IDLE

    private fun screen(): Rect = if (android.os.Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds
    else Rect(0, 0, context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)

    fun show() {
        val s = screen()
        ballX = prefs.getInt("x", s.width() - ball - margin).coerceIn(margin, max(margin, s.width() - ball - margin))
        ballY = prefs.getInt("y", s.height() / 3).coerceIn(margin, max(margin, s.height() - ball - margin))
        lp.x = ballX; lp.y = ballY; wm.addView(view, lp); attached = true
    }

    fun render(next: CalibrationState) {
        val oldPhase = phase
        state = next
        if (busy) { expanded = true; options = null }
        if ((oldPhase == CalibrationPhase.DONE || oldPhase == CalibrationPhase.ERROR) && phase == CalibrationPhase.IDLE) expanded = false
        resize(); view.invalidate()
    }

    fun resize() {
        if (!attached) return
        val s = screen()
        lp.width = if (expanded) s.width() - 2 * margin else ball
        lp.height = if (expanded) ((if (options == null) 108 else if (options == "quality") 210 else 166) * d).roundToInt() else ball
        lp.x = if (expanded) margin else ballX.coerceIn(margin, max(margin, s.width() - ball - margin))
        lp.y = ballY.coerceIn(margin, max(margin, s.height() - lp.height - margin))
        runCatching { wm.updateViewLayout(view, lp) }
    }

    fun close() {
        animator?.cancel(); dialog?.dismiss()
        if (attached) runCatching { wm.removeView(view) }
        attached = false
    }

    private fun choices(name: String) {
        if (busy) return
        options = if (options == name) null else name
        animator?.cancel(); slide = 0f; resize()
        animator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 240; addUpdateListener { slide = it.animatedValue as Float; view.invalidate() }; start() }
    }

    private fun choose(key: String, value: Any) { action("set", mapOf(key to value)); options = null; resize(); view.invalidate() }

    private fun customDuration() {
        val input = EditText(context).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "1–600 sn"; setSingleLine(true) }
        val dlg = AlertDialog.Builder(context).setTitle("Özel süre (sn)").setView(input).setNegativeButton("Vazgeç", null).setPositiveButton("Seç", null).create()
        dlg.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = input.text.toString().toIntOrNull()
                if (n == null || n !in 1..600) input.error = "1–600 saniye gir" else { choose("seconds", n); dlg.dismiss() }
            }
            input.requestFocus(); dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog = dlg; dlg.show()
    }

    private fun tap(x: Float, y: Float) {
        if (!expanded) { expanded = true; resize(); return }
        if (y < 30 * d) { if (!busy) { expanded = false; options = null; resize() }; return }
        if (y >= 108 * d && options != null && !busy) {
            val values = when (options) { "quality" -> (1..10).map { it.toString() }; "seconds" -> listOf("15", "30", "60", "90", "custom"); else -> listOf("fixed", "variable") }
            val columns = if (options == "quality") 5 else values.size
            val row = if (options == "quality" && y >= 158 * d) 1 else 0
            val selected = values[row * columns + (x / (view.width.toFloat() / columns)).toInt().coerceIn(0, columns - 1)]
            when (options) {
                "quality" -> choose("qualityLevel", selected.toInt())
                "seconds" -> if (selected == "custom") customDuration() else choose("seconds", selected.toInt())
                else -> choose("mode", selected)
            }
            return
        }
        when ((x / (view.width / 5f)).toInt().coerceIn(0, 4)) {
            0 -> if (!busy && state.ready) action("start", emptyMap())
            1 -> if (phase == CalibrationPhase.COUNTDOWN || phase == CalibrationPhase.RECORDING) action("finish", emptyMap())
            2 -> choices("quality")
            3 -> choices("seconds")
            4 -> choices("mode")
        }
    }

    private inner class Panel(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragged = false
        private val slop = ViewConfiguration.get(c).scaledTouchSlop
        // A10.4d (`docs/TASARIM.md` 4): renkler `OverlayPalette`'ten (telefonun gece/gündüz ayarı); metinler YolGo'daki gibi.
        // Çizim başına bir kez çözülür; gece/gündüz değişince yeniden çizilir.
        private var pal: OverlayPalette = OwnOverlays.palette(c)
        private val surface get() = (pal.surface and 0x00ffffff) or 0xf5000000.toInt()
        private val accent get() = pal.accentText

        init { contentDescription = "Yol kalitesi kalibrasyonu; açmak veya kapatmak için dokun, taşımak için sürükle"; isClickable = true }

        private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int = pal.text, room: Float = width.toFloat()) {
            p.style = Paint.Style.FILL; p.color = color; p.textAlign = Paint.Align.CENTER
            p.typeface = WeemFonts.get(WeemFonts.SEMIBOLD); p.textSize = size * d
            val measured = p.measureText(value)
            if (measured > room * .94f) p.textSize *= room * .94f / measured
            c.drawText(value, x, y, p)
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            pal = OwnOverlays.palette(context)
            val w = width.toFloat(); val h = height.toFloat(); val barH = 108 * d
            p.style = Paint.Style.FILL; p.color = if (phase == CalibrationPhase.DONE) (pal.positiveBg and 0x00ffffff) or 0xf5000000.toInt() else surface
            if (!expanded) c.drawCircle(w / 2, h / 2, w / 2, p) else c.drawRoundRect(0f, 0f, w, barH, 34 * d, 34 * d, p)
            p.style = Paint.Style.STROKE; p.color = accent; p.strokeWidth = 2 * d
            if (!expanded) c.drawCircle(w / 2, h / 2, w / 2 - d, p) else c.drawRoundRect(d, d, w - d, barH - d, 34 * d, 34 * d, p)
            if (!expanded) { text(c, "≈", w / 2, h * .53f, 32f, accent); text(c, "KALİBRE", w / 2, h * .76f, 10f); return }
            val title = when (phase) {
                CalibrationPhase.COUNTDOWN -> if (state.waitingOffset) "Dur · GPS bekleniyor" else "${state.remaining} · Hazırlan"
                CalibrationPhase.RECORDING -> "Kalibrasyon başladı · ${state.remaining} sn"
                CalibrationPhase.SAVING -> "Kalibrasyon kaydediliyor"
                CalibrationPhase.DONE -> "Kalibrasyon bitti"
                CalibrationPhase.ERROR -> "Kalibrasyon hatası"
                CalibrationPhase.IDLE -> "Sabit hızla sür · Yol kalibrasyonu             ×"
            }
            text(c, title, w / 2, 22 * d, 13f, accent)
            if (phase == CalibrationPhase.DONE || phase == CalibrationPhase.ERROR) { text(c, state.message, w / 2, 65 * d, 13f, room = w - 20 * d); return }
            val quality = when (state.quality) { "veryGood" -> "ÇOK İYİ"; "good" -> "İYİ"; "middle" -> "ORTA"; "bad" -> "KÖTÜ"; "veryBad" -> "ÇOK KÖTÜ"; else -> "SEÇ" }
            val level = state.qualityLevel?.let { "$it/10 " } ?: ""
            val duration = state.seconds.let { if (it > 0) "$it sn" else "SEÇ" }
            val labels = listOf("BAŞLA", "BİTİR", "$level$quality", duration, if (state.mode == "variable") "DEĞİŞKEN" else "SABİT")
            val captions = listOf("", "", "YOL KALİTESİ", "SÜRE", "HIZ MODU")
            for (i in 0..4) {
                val enabled = if (i == 1) phase == CalibrationPhase.RECORDING || phase == CalibrationPhase.COUNTDOWN else !busy && (i != 0 || state.ready)
                text(c, labels[i], (i + .5f) * w / 5, 58 * d, 12f, if (enabled) accent else (pal.muted and 0x00ffffff) or 0x80000000.toInt(), w / 5)
                text(c, captions[i], (i + .5f) * w / 5, 74 * d, 8f, pal.muted, w / 5)
            }
            if (phase == CalibrationPhase.RECORDING) {
                val path = Path(); val t = SystemClock.uptimeMillis() / 180.0
                for (x in 0..width step 3) { val y = (94 * d + sin(x / (20 * d) + t) * 5 * d).toFloat(); if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y) }
                p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = accent; c.drawPath(path, p); postInvalidateDelayed(40)
            }
            if (options != null) {
                val values = when (options) { "quality" -> (1..10).map { "$it/10" }; "seconds" -> listOf("15 sn", "30 sn", "60 sn", "90 sn", "ÖZEL"); else -> listOf("SABİT HIZ", "DEĞİŞKEN HIZ") }
                c.save(); c.clipRect(0f, barH, w, h); c.translate((1 - slide) * w, 0f)
                p.style = Paint.Style.FILL; p.color = surface; c.drawRoundRect(0f, barH + 4 * d, w, h, 24 * d, 24 * d, p)
                val columns = if (options == "quality") 5 else values.size
                for (i in values.indices) text(c, values[i], (i % columns + .5f) * w / columns, (143 + 44 * (i / columns)) * d, 13f, accent, w / columns)
                c.restore()
            }
        }

        override fun performClick(): Boolean { super.performClick(); return true }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; dragged = false; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (hypot(dx.toDouble(), dy.toDouble()) > slop) dragged = true
                    if (dragged) {
                        val s = screen()
                        lp.x = (startX + dx).roundToInt().coerceIn(0, max(0, s.width() - lp.width)); lp.y = (startY + dy).roundToInt().coerceIn(0, max(0, s.height() - lp.height))
                        runCatching { wm.updateViewLayout(this, lp) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        ballY = lp.y
                        if (!expanded) { val s = screen(); ballX = if (lp.x + ball / 2 < s.width() / 2) margin else s.width() - ball - margin; resize() }
                        prefs.edit().putInt("x", ballX).putInt("y", ballY).apply()
                    } else { performClick(); tap(e.x, e.y) }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { dragged = false; return true }
            }
            return super.onTouchEvent(e)
        }
    }
}
