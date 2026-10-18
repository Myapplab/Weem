package com.kuryelog.app.scooter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.scooter.NinebotDashboard
import com.kuryelog.logic.scooter.PanelField
import com.kuryelog.logic.scooter.ScooterPanel
import com.kuryelog.logic.scooter.ScooterUiText as T
import com.kuryelog.logic.scooter.TripMeter

/**
 * Canlı hız göstergesi (S2; NB `DashboardView.java` çizimi satır satır): aldığı yüksekliği doldurur, dikeyde gösterge + 2 × 2 kadran +
 * üç sayaç kadranı, yatayda gösterge solda. Değerler ara değerlenmez, GPS ile doldurulmaz; kare [ScooterPanel.STALE_MS]'den eskiyse
 * "VERİ BEKLENİYOR". Compose Scooter sekmesi bunu `AndroidView` ile taşır; PiP'de [compact] ızgarası.
 */
class DashboardView(context: Context) : View(context) {
    private var frame: NinebotDashboard.Snapshot? = null
    private var lastDrawnLive = false
    private var immersive = false
    private var compact = false
    private var trip: TripMeter.Stats? = null
    private var fields: List<PanelField> = PanelField.DEFAULT
    private var painter: FloatPainter? = null
    private var ink = Ink.of(true)
    private var lang = Lang.TR
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val number = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val d = context.resources.displayMetrics.density
    private var ringShader: Shader? = null
    private var shaderX = -1f
    private var shaderY = 0f
    private var shaderR = 0f
    private var shaderColor = 0

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // Tabular figures keep the digits from jumping sideways while the speed changes.
        number.typeface = BOLD
        number.fontFeatureSettings = "'tnum'"
        number.textAlign = Paint.Align.CENTER
    }

    /** Tema ve dil (WEEM ayarı); değişince yeniden çizilir. */
    fun style(dark: Boolean, lang: Lang) {
        val next = Ink.of(dark)
        if (next === ink && lang == this.lang) return
        ink = next
        this.lang = lang
        contentDescription = T.PANEL_DESC.get(lang)
        invalidate()
    }

    fun show(s: NinebotDashboard.Snapshot?) {
        val prev = frame
        val changed = if (prev == null) s != null else s == null || !sameShown(prev, s)
        frame = s
        if (changed || live() != lastDrawnLive) invalidate()
    }

    private fun sameShown(a: NinebotDashboard.Snapshot, b: NinebotDashboard.Snapshot): Boolean =
        a.speedKmh == b.speedKmh && a.powerW == b.powerW && a.voltageV == b.voltageV && a.currentA == b.currentA &&
            a.batteryPct == b.batteryPct && a.rangeKm == b.rangeKm && a.tempC == b.tempC

    fun refreshStale() { if (live() != lastDrawnLive) invalidate() }

    /** Tam ekran yalnız ipucu satırını değiştirir; boy düzenden gelir. */
    fun immersive(on: Boolean) { if (immersive != on) { immersive = on; invalidate() } }

    /** Yüzen panel için seçilen değerler; PiP penceresi aynılarını gösterir. */
    fun fields(f: List<PanelField>) { fields = f; if (compact) invalidate() }

    fun compact(on: Boolean) { if (compact != on) { compact = on; invalidate() } }

    /** Bağlantı başından ya da sipariş kabulünden beri toplamlar; null gizler. Yalnız değişince çizer. */
    fun trip(t: TripMeter.Stats?) {
        val before = ScooterPanel.tripLine(trip, lang)
        trip = t
        if (before != ScooterPanel.tripLine(t, lang)) invalidate()
    }

    private fun live(): Boolean = ScooterPanel.live(frame, SystemClock.elapsedRealtime())
    private fun dp(v: Float): Float = v * d

    override fun onDraw(c: Canvas) {
        c.drawColor(ink.bg)
        val w = width.toFloat()
        val h = height.toFloat()
        val s = frame
        val fresh = live()
        lastDrawnLive = fresh
        val alpha = if (fresh) 255 else 112
        val m = dp(16f)
        if (compact || h < dp(230f)) { compactPanel(c, s, alpha, w, h); return }
        if (w > h * 1.15f) { // Landscape: gauge on the left, the cards stacked on the right.
            val sz = Math.min(h - dp(24f), w * .52f)
            gauge(c, s, fresh, alpha, m + sz / 2f, h / 2f, sz)
            val x = m + sz + dp(16f)
            val cw = w - x - m
            val bh = Math.min(dp(110f), (h - dp(40f)) / 3f - dp(7f))
            val top = (h - (bh * 3 + dp(20f))) / 2f
            gaugeGrid(c, s, fresh, alpha, x, top, cw, bh, bh, 1f)
            tripDials(c, alpha, x, top + 2 * (bh + dp(10f)), cw, bh, 1f)
            return
        }
        // Portrait: the gauge takes the width; extra height (full screen) enlarges the cards below it.
        val baseCards = dp(84f) + dp(10f) + dp(80f) + dp(10f) + dp(72f)
        val hint = dp(26f)
        val sz = Math.max(dp(150f), Math.min(w - 2 * (if (immersive) dp(6f) else m), h - baseCards - hint - dp(28f)))
        val spare = Math.max(0f, h - sz - baseCards - hint - dp(28f))
        val k = Math.min(if (immersive) 1.7f else 1.45f, 1f + spare / baseCards)
        val cards = baseCards * k
        val used = sz + dp(12f) + cards + hint
        val top = Math.max(dp(8f), (h - used) / 2f)
        gauge(c, s, fresh, alpha, w / 2f, top + sz / 2f, sz)
        val y = top + sz + dp(12f)
        val ch = dp(84f) * k
        val th = dp(80f) * k
        gaugeGrid(c, s, fresh, alpha, m, y, w - 2 * m, ch, th, k)
        val ty = y + ch + dp(10f) * k
        val t3 = ty + th + dp(10f) * k
        val h3 = dp(72f) * k
        tripDials(c, alpha, m, t3, w - 2 * m, h3, k)
        text(c, ScooterPanel.hint(trip, immersive, lang), w / 2f, t3 + h3 + dp(19f), dp(11f), ink.muted, 255, false, Paint.Align.CENTER)
    }

    private fun gauge(c: Canvas, s: NinebotDashboard.Snapshot?, fresh: Boolean, alpha: Int, cx: Float, cy: Float, sz: Float) {
        val stroke = sz * .052f
        val r = sz / 2f - stroke
        if (shaderX != cx || shaderY != cy || shaderR != r || shaderColor != ink.accent) {
            shaderX = cx; shaderY = cy; shaderR = r; shaderColor = ink.accent
            ringShader = SweepGradient(cx, cy, intArrayOf(ink.accent, ink.power, ink.accent, ink.accent), floatArrayOf(0f, .45f, .7f, 1f)).also {
                val rot = Matrix()
                rot.setRotate(90f, cx, cy)
                it.setLocalMatrix(rot)
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke
        paint.shader = null
        paint.color = ink.surfaceHigh
        paint.alpha = 255
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, 135f, 270f, false, paint)
        val sweep = 270 * NinebotDashboard.gauge(if (fresh) s?.speedKmh else null)
        if (sweep > 0) {
            paint.shader = ringShader
            paint.alpha = alpha
            c.drawArc(rect, 135f, sweep, false, paint)
            paint.shader = null
        }
        // Ticks every 2 km/h inside the ring; 0/10/20/30 are labelled. The open lower section holds the power badge.
        paint.strokeWidth = Math.max(dp(1.5f), sz * .006f)
        for (i in 0..15) {
            val a = Math.toRadians(135.0 + i * 18)
            val major = i % 5 == 0
            val outer = r - stroke * .9f
            val inner = outer - if (major) sz * .035f else sz * .016f
            paint.color = if (major) ink.muted else ink.line
            paint.alpha = 255
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            c.drawLine(cx + cos * inner, cy + sin * inner, cx + cos * outer, cy + sin * outer, paint)
            if (major) {
                val lr = inner - sz * .045f
                text(c, (i * 2).toString(), cx + cos * lr, cy + sin * lr + sz * .014f, sz * .04f, ink.muted, 255, false, Paint.Align.CENTER)
            }
        }
        text(c, (if (fresh) T.SPEED else T.NO_DATA).get(lang), cx, cy - sz * .2f, Math.max(dp(10f), sz * .034f), ink.muted, 255, true, Paint.Align.CENTER)
        val speed = NinebotDashboard.speed(s?.speedKmh)
        var font = sz * .34f
        number.textSize = font
        val max = sz * .7f
        if (number.measureText(speed) > max) font *= max / number.measureText(speed)
        number.textSize = font
        number.color = ink.text
        number.alpha = alpha
        c.drawText(speed, cx, cy + sz * .1f, number)
        text(c, "km/h", cx, cy + sz * .18f, sz * .05f, ink.muted, alpha, false, Paint.Align.CENTER)
        // Power badge grows with the speed digits (0.2.13: fixed 23 dp).
        val watts = NinebotDashboard.watts(s?.powerW) + " W"
        val wf = sz * .082f
        number.textSize = wf
        val pw = Math.max(sz * .4f, number.measureText(watts) + sz * .12f)
        val bh = sz * .13f
        val py = cy + sz * .315f
        rect.set(cx - pw / 2, py - bh / 2, cx + pw / 2, py + bh / 2)
        paint.style = Paint.Style.FILL
        paint.color = ink.hero
        paint.alpha = 255
        c.drawRoundRect(rect, bh / 2, bh / 2, paint)
        number.color = ink.power
        number.alpha = alpha
        c.drawText(watts, cx, py + wf * .36f, number)
    }

    private fun compactPanel(c: Canvas, s: NinebotDashboard.Snapshot?, alpha: Int, w: Float, h: Float) {
        val p = painter ?: FloatPainter().also { painter = it }
        p.ink = ink
        p.lang = lang
        p.grid(c, w, h, fields, s, trip, alpha)
    }

    private fun tone(t: ScooterPanel.Tone): Int = when (t) {
        ScooterPanel.Tone.POSITIVE -> ink.record
        ScooterPanel.Tone.ACCENT -> ink.accent
        ScooterPanel.Tone.POWER -> ink.power
        ScooterPanel.Tone.WARN -> ink.warn
        ScooterPanel.Tone.DANGER -> ink.danger
    }

    /** 0.22.0: batarya, voltaj, akım ve sıcaklık hız göstergesi gibi küçük kadranlar, 2 × 2. */
    private fun gaugeGrid(c: Canvas, s: NinebotDashboard.Snapshot?, fresh: Boolean, alpha: Int, x: Float, y: Float, w: Float, h1: Float, h2: Float, k: Float) {
        val gap = dp(10f)
        val tw = (w - gap) / 2f
        val y2 = y + h1 + gap * k
        val pct = s?.batteryPct
        dialTile(c, x, y, tw, h1, T.BATTERY.get(lang), NinebotDashboard.whole(pct), "%", if (fresh) ScooterPanel.frac(pct, 0.0, 100.0) else null,
            tone(ScooterPanel.batteryTone(pct)), T.RANGE_SUB.get(lang).format(NinebotDashboard.oneDecimal(s?.rangeKm)), alpha, k)
        dialTile(c, x + tw + gap, y, tw, h1, T.VOLTAGE.get(lang), NinebotDashboard.oneDecimal(s?.voltageV), "V",
            if (fresh) ScooterPanel.frac(s?.voltageV, 30.0, 55.0) else null, ink.accent, null, alpha, k)
        dialTile(c, x, y2, tw, h2, T.CURRENT.get(lang), NinebotDashboard.oneDecimal(s?.currentA), "A",
            if (fresh) ScooterPanel.frac(s?.currentA?.let { Math.abs(it) }, 0.0, 30.0) else null, ink.power, null, alpha, k)
        val t = s?.tempC
        dialTile(c, x + tw + gap, y2, tw, h2, T.TEMP.get(lang), NinebotDashboard.whole(t), "°C", if (fresh) ScooterPanel.frac(t, 0.0, 70.0) else null,
            tone(ScooterPanel.tempTone(t)), null, alpha, k)
    }

    /** 0.22.2: sürüşün üç değeri (enerji, kullanılan pil, ortalama hız) de kadran. */
    private fun tripDials(c: Canvas, alpha: Int, x: Float, y: Float, w: Float, h: Float, k: Float) {
        val t = trip
        val gap = dp(8f)
        val tw = (w - 2 * gap) / 3f
        dialTile(c, x, y, tw, h, T.ENERGY.get(lang), NinebotDashboard.oneDecimal(t?.wh), "Wh", ScooterPanel.frac(t?.wh, 0.0, 100.0), ink.power, null, alpha, k)
        dialTile(c, x + tw + gap, y, tw, h, T.PCT_USED.get(lang), NinebotDashboard.whole(t?.pctUsed), "%", ScooterPanel.frac(t?.pctUsed, 0.0, 30.0), ink.warn, null, alpha, k)
        dialTile(c, x + 2 * (tw + gap), y, tw, h, T.AVG_SPEED.get(lang), NinebotDashboard.oneDecimal(t?.avgMovingKmh), "km/h",
            ScooterPanel.frac(t?.avgMovingKmh, 0.0, 30.0), ink.accent, null, alpha, k)
    }

    private fun dialTile(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, value: String, unit: String, f: Double?, color: Int, sub: String?, alpha: Int, k: Float) {
        rect.set(x, y, x + w, y + h)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = ink.surface
        paint.alpha = 255
        c.drawRoundRect(rect, dp(20f), dp(20f), paint)
        val pad = dp(10f)
        val dia = Math.min(h - 2 * pad, w * .46f)
        val stroke = Math.max(dp(4f), dia * .1f)
        val r = dia / 2f - stroke / 2f
        val cx = x + pad + dia / 2f
        val cy = y + h / 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke
        paint.color = ink.surfaceHigh
        paint.alpha = 255
        c.drawArc(rect, 135f, 270f, false, paint)
        if (f != null && f > 0) {
            paint.color = color
            paint.alpha = alpha
            c.drawArc(rect, 135f, (270 * f).toFloat(), false, paint)
        }
        paint.style = Paint.Style.FILL
        val tx = x + pad + dia + dp(10f)
        val room = Math.max(dp(1f), x + w - pad - tx)
        fittedText(c, label, tx, y + h * .3f, dp(10.5f) * k, room, ink.muted, 255, true)
        val v = if (NinebotDashboard.DASH == value) value else "$value $unit"
        fittedText(c, v, tx, y + h * if (sub == null) .68f else .6f, dp(22f) * k, room, ink.text, alpha, true)
        if (sub != null) fittedText(c, sub, tx, y + h * .84f, dp(11f) * k, room, ink.muted, alpha, false)
    }

    private fun fittedText(c: Canvas, value: String, x: Float, y: Float, size: Float, width: Float, color: Int, alpha: Int, bold: Boolean) {
        paint.typeface = if (bold) BOLD else REGULAR
        paint.textSize = size
        val measured = paint.measureText(value)
        val s = if (measured > width && measured > 0) size * Math.max(1f, width) / measured else size
        text(c, value, x, y, s, color, alpha, bold, Paint.Align.LEFT)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, alpha: Int, bold: Boolean, align: Paint.Align) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.color = color
        paint.alpha = alpha
        paint.textAlign = align
        paint.typeface = if (bold) BOLD else REGULAR
        c.drawText(value, x, y, paint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // Dokunma eylemi adlandırılmaz: WEEM kaynağında erişilebilirlik eylem sabitleri yasak (NoGestureTest); ipucu çizilen satırda.
        info.text = ScooterPanel.accessibility(frame, lang)
    }

    private companion object {
        val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
}
