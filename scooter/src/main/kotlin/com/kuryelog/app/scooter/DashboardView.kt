package com.kuryelog.app.scooter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.kuryelog.app.core.WeemFonts
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.scooter.NinebotDashboard
import com.kuryelog.logic.scooter.PanelField
import com.kuryelog.logic.scooter.ScooterPanel
import com.kuryelog.logic.scooter.ScooterUiText as T
import com.kuryelog.logic.scooter.TripMeter

/**
 * Canlı hız göstergesi (S2; NB `DashboardView.java` düzeni): aldığı yüksekliği doldurur. A10.4d (`docs/TASARIM.md` 11–12): dikeyde
 * gösterge kartı (köşe 24: 270° yay, iz `raised`, değer vurgu rengi, hız büyük ve sabit genişlikli rakam, güç hapı, ipucu) + 2 × 2
 * kadran kartı (köşe 16: değer solda, halka sağda) + sürüş kartı (enerji, kullanılan pil, ortalama hız); yatayda gösterge kartı solda.
 * Renkler `OverlayPalette`'ten (uygulama paletiyle aynı tablo), yazı Inter ([WeemFonts]). Değerler ara değerlenmez, GPS ile doldurulmaz;
 * kare [ScooterPanel.STALE_MS]'den eskiyse "VERİ BEKLENİYOR", değerler soluk. Compose Scooter sekmesi bunu `AndroidView` ile taşır;
 * PiP'de [compact] ızgarası.
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
    private val bolt: Drawable? = context.getDrawable(R.drawable.scooter_panel_bolt)?.mutate()
    private val touch: Drawable? = context.getDrawable(R.drawable.scooter_panel_touch)?.mutate()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // Tabular figures keep the digits from jumping sideways while the speed changes.
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
        val before = ScooterPanel.tripLine(trip, lang) to ScooterPanel.hint(trip, immersive, lang)
        trip = t
        if (before != ScooterPanel.tripLine(t, lang) to ScooterPanel.hint(t, immersive, lang)) invalidate()
    }

    private fun live(): Boolean = ScooterPanel.live(frame, SystemClock.elapsedRealtime())
    private fun dp(v: Float): Float = v * d

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // drawRect, not drawColor: a software canvas (no per-view clip) would otherwise paint over the Compose header above.
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = ink.bg
        paint.alpha = 255
        c.drawRect(0f, 0f, w, h, paint)
        val s = frame
        val fresh = live()
        lastDrawnLive = fresh
        val alpha = if (fresh) 255 else 112
        if (compact || h < dp(230f)) { compactPanel(c, s, alpha, w, h); return }
        val m = dp(16f)
        val gap = dp(GAP)
        val block = dp(BLOCK)
        if (w > h * 1.15f) { // Landscape: the gauge card on the left, the dial and trip cards stacked on the right.
            val top = dp(8f)
            val ch = h - 2 * top
            val gw = Math.min(w * .5f, ch * 1.25f)
            gaugeCard(c, s, fresh, alpha, m, top, gw, ch)
            val x = m + gw + dp(12f)
            val k = (ch - gap - block) / dp(2 * DIAL + TRIP)
            dials(c, s, fresh, alpha, x, top, w - x - m, dp(DIAL) * k, gap, k)
            tripCard(c, alpha, x, top + 2 * dp(DIAL) * k + gap + block, w - x - m, dp(TRIP) * k, k)
            return
        }
        // Portrait: the gauge card takes the width; extra height (full screen) first enlarges the gauge, then the cards below it.
        val cw = w - 2 * m
        val pad = dp(8f)
        val below = dp(2 * DIAL + TRIP) + gap + 2 * block
        var gh = Math.max(dp(150f), Math.min(cw * GAUGE_RATIO, h - 2 * pad - below))
        var spare = Math.max(0f, h - 2 * pad - gh - below)
        val grow = Math.max(0f, Math.min(spare * .5f, cw * 1.05f - gh))
        gh += grow
        spare -= grow
        // Short screens shrink the cards (not below 60 %) instead of cutting them off.
        val fit = (h - 2 * pad - gh) / below
        val k = if (fit < 1f) Math.max(.6f, fit) else Math.min(if (immersive) 1.7f else 1.45f, 1f + spare / below)
        val top = Math.max(pad, (h - gh - below * k) / 2f)
        gaugeCard(c, s, fresh, alpha, m, top, cw, gh)
        val y = top + gh + block * k
        dials(c, s, fresh, alpha, m, y, cw, dp(DIAL) * k, gap * k, k)
        tripCard(c, alpha, m, y + 2 * dp(DIAL) * k + gap * k + block * k, cw, dp(TRIP) * k, k)
    }

    private fun card(c: Canvas, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        rect.set(x, y, x + w, y + h)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = ink.surface
        paint.alpha = 255
        c.drawRoundRect(rect, radius, radius, paint)
        if (ink.dark) { // Night cards are separated by a 1 dp line (TASARIM.md 6: e0).
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = ink.line
            rect.inset(dp(.5f), dp(.5f))
            c.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }

    /** Gösterge kartı: yay, işaretler, hız, güç hapı ve ipucu kartın içine sığar (tasarım 358 × 262'de yarıçap 100). */
    private fun gaugeCard(c: Canvas, s: NinebotDashboard.Snapshot?, fresh: Boolean, alpha: Int, x: Float, y: Float, w: Float, h: Float) {
        card(c, x, y, w, h, dp(24f))
        val r = Math.min(w * .36f, h / 2.62f)
        val cx = x + w / 2f
        val cy = y + (h - 2.62f * r) / 2f + 1.28f * r
        val stroke = Math.max(dp(10f), r * .14f)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke
        paint.color = ink.surfaceHigh
        paint.alpha = 255
        c.drawArc(rect, 135f, 270f, false, paint)
        val sweep = 270 * NinebotDashboard.gauge(if (fresh) s?.speedKmh else null)
        if (sweep > 0) {
            paint.color = ink.accent
            paint.alpha = alpha
            c.drawArc(rect, 135f, sweep, false, paint)
        }
        // Ticks every 2 km/h inside the ring; 0/10/20/30 are labelled. The open lower section holds the power badge.
        for (i in 0..15) {
            val a = Math.toRadians(135.0 + i * 18)
            val major = i % 5 == 0
            val outer = r * .89f
            val inner = r * if (major) .82f else .85f
            paint.strokeWidth = Math.max(dp(1.5f), r * if (major) .025f else .02f)
            paint.color = if (major) ink.muted else ink.lineStrong
            paint.alpha = 255
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            c.drawLine(cx + cos * inner, cy + sin * inner, cx + cos * outer, cy + sin * outer, paint)
            if (major) {
                val lr = r * .70f
                val size = Math.max(dp(10f), r * .11f)
                text(c, (i * 2).toString(), cx + cos * lr, cy + sin * lr + size * .36f, size, ink.muted, 255, WeemFonts.SEMIBOLD, Paint.Align.CENTER)
            }
        }
        val label = Math.max(dp(11f), r * .13f)
        // "HIZ" sits between the 10 and 20 labels; the longer waiting label goes above them and shrinks to fit inside the ring.
        val title = (if (fresh) T.SPEED else T.NO_DATA).get(lang)
        val titleRoom = r * if (fresh) .7f else 1.05f
        paint.typeface = WeemFonts.get(WeemFonts.SEMIBOLD)
        paint.textSize = label
        paint.letterSpacing = .08f
        val titleWidth = paint.measureText(title)
        paint.letterSpacing = 0f
        val titleSize = if (titleWidth > titleRoom) label * titleRoom / titleWidth else label
        text(c, title, cx, cy - r * if (fresh) .455f else .6f, titleSize, if (fresh) ink.muted else ink.warn, 255, WeemFonts.SEMIBOLD,
            Paint.Align.CENTER, .08f)
        val speed = NinebotDashboard.speed(s?.speedKmh)
        number.typeface = WeemFonts.get(WeemFonts.EXTRABOLD)
        number.letterSpacing = -.04f
        // No reading yet: a small, faint dash instead of a large grey bar.
        var font = r * if (s?.speedKmh == null) .4f else .8f
        number.textSize = font
        val max = r * 1.3f
        if (number.measureText(speed) > max) font *= max / number.measureText(speed)
        number.textSize = font
        number.color = if (s?.speedKmh == null) ink.muted else ink.text
        number.alpha = alpha
        c.drawText(speed, cx, cy + r * .27f, number)
        number.letterSpacing = 0f
        text(c, "km/h", cx, cy + r * .5f, label, ink.muted, alpha, WeemFonts.SEMIBOLD, Paint.Align.CENTER)
        powerPill(c, s, alpha, cx, cy + r * .81f, r)
        // Hint row at the bottom of the card: scope (this order / connection) and what a tap does.
        val hint = ScooterPanel.hint(trip, immersive, lang)
        val hs = Math.max(dp(11f), r * .12f)
        paint.typeface = WeemFonts.get(WeemFonts.SEMIBOLD)
        paint.textSize = hs
        paint.letterSpacing = 0f
        val iconSize = hs * 1.35f
        val tw = paint.measureText(hint)
        val left = cx - (iconSize + dp(6f) + tw) / 2f
        val base = cy + r * 1.2f
        touch?.let { drawIcon(c, it, left, base - hs * .85f - (iconSize - hs) / 2f, iconSize, ink.muted) }
        text(c, hint, left + iconSize + dp(6f), base, hs, ink.muted, 255, WeemFonts.SEMIBOLD, Paint.Align.LEFT)
    }

    /** Güç hapı: güç zemini, şimşek simgesi ve watt (sabit genişlikli rakam). */
    private fun powerPill(c: Canvas, s: NinebotDashboard.Snapshot?, alpha: Int, cx: Float, cy: Float, r: Float) {
        val watts = NinebotDashboard.watts(s?.powerW) + " W"
        val wf = Math.max(dp(13f), r * .15f)
        number.typeface = WeemFonts.get(WeemFonts.BOLD)
        number.textSize = wf
        val iconSize = wf * 1.2f
        val ph = Math.max(dp(26f), r * .30f)
        val pw = iconSize + dp(4f) + number.measureText(watts) + dp(20f)
        rect.set(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2)
        paint.style = Paint.Style.FILL
        paint.color = ink.powerBg
        paint.alpha = 255
        c.drawRoundRect(rect, ph / 2, ph / 2, paint)
        val left = rect.left + dp(8f)
        bolt?.let { drawIcon(c, it, left, cy - iconSize / 2, iconSize, ink.power, alpha) }
        number.color = ink.power
        number.alpha = alpha
        number.textAlign = Paint.Align.LEFT
        c.drawText(watts, left + iconSize + dp(4f), cy + wf * .36f, number)
        number.textAlign = Paint.Align.CENTER
    }

    private fun drawIcon(c: Canvas, d: Drawable, x: Float, y: Float, size: Float, color: Int, alpha: Int = 255) {
        d.setTint(color)
        d.alpha = alpha
        d.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
        d.draw(c)
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

    /** 0.22.0: batarya, voltaj, akım ve sıcaklık, 2 × 2 kadran kartı (değer solda, halka sağda). */
    private fun dials(c: Canvas, s: NinebotDashboard.Snapshot?, fresh: Boolean, alpha: Int, x: Float, y: Float, w: Float, h: Float, gap: Float, k: Float) {
        val tw = (w - gap) / 2f
        val y2 = y + h + gap
        val pct = s?.batteryPct
        dialTile(c, x, y, tw, h, T.BATTERY.get(lang), NinebotDashboard.whole(pct), "%", if (fresh) ScooterPanel.frac(pct, 0.0, 100.0) else null,
            tone(ScooterPanel.batteryTone(pct)), T.RANGE_SUB.get(lang).format(NinebotDashboard.oneDecimal(s?.rangeKm)), alpha, k)
        dialTile(c, x + tw + gap, y, tw, h, T.VOLTAGE.get(lang), NinebotDashboard.oneDecimal(s?.voltageV), "V",
            if (fresh) ScooterPanel.frac(s?.voltageV, 30.0, 55.0) else null, ink.accent, null, alpha, k)
        dialTile(c, x, y2, tw, h, T.CURRENT.get(lang), NinebotDashboard.oneDecimal(s?.currentA), "A",
            if (fresh) ScooterPanel.frac(s?.currentA?.let { Math.abs(it) }, 0.0, 30.0) else null, ink.power, null, alpha, k)
        val t = s?.tempC
        dialTile(c, x + tw + gap, y2, tw, h, T.TEMP.get(lang), NinebotDashboard.whole(t), "°C", if (fresh) ScooterPanel.frac(t, 0.0, 70.0) else null,
            tone(ScooterPanel.tempTone(t)), null, alpha, k)
    }

    private fun dialTile(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, value: String, unit: String, f: Double?, color: Int, sub: String?, alpha: Int, k: Float) {
        card(c, x, y, w, h, dp(16f))
        val pad = dp(10f) * k
        val dia = Math.min(h - 2 * pad, Math.min(dp(44f) * k, w * .36f))
        val stroke = Math.max(dp(4f), dia * .11f)
        val r = dia / 2f - stroke / 2f
        val cx = x + w - pad - dia / 2f
        val cy = y + h / 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = stroke
        paint.color = ink.surfaceHigh
        paint.alpha = 255
        c.drawArc(rect, 0f, 360f, false, paint)
        if (f != null && f > 0) {
            paint.color = color
            paint.alpha = alpha
            c.drawArc(rect, -90f, (360 * f).toFloat(), false, paint)
        }
        paint.style = Paint.Style.FILL
        val tx = x + dp(14f) * k
        val room = Math.max(dp(1f), cx - r - stroke - dp(8f) - tx)
        val lines = if (sub == null) 2 else 3
        val ls = dp(12f) * k
        val vs = dp(22f) * k
        val ss = dp(12f) * k
        val block = ls * 1.33f + vs * 1.18f + (if (lines == 3) ss * 1.33f else 0f)
        var top = y + (h - block) / 2f
        fittedText(c, label, tx, top + ls, ls, room, ink.muted, 255, WeemFonts.SEMIBOLD)
        top += ls * 1.33f
        valueWithUnit(c, value, unit, tx, top + vs, vs, room, alpha)
        top += vs * 1.18f
        if (sub != null) fittedText(c, sub, tx, top + ss, ss, room, ink.muted, alpha, WeemFonts.SEMIBOLD)
    }

    /** 0.22.2: sürüşün üç değeri (enerji, kullanılan pil, ortalama hız) tek kartta, üç sütun. */
    private fun tripCard(c: Canvas, alpha: Int, x: Float, y: Float, w: Float, h: Float, k: Float) {
        card(c, x, y, w, h, dp(16f))
        val t = trip
        val padX = dp(14f) * k
        val gap = dp(8f)
        val col = (w - 2 * padX - 2 * gap) / 3f
        val ls = dp(12f) * k
        val vs = dp(16f) * k
        val top = y + (h - (ls * 1.33f + vs * 1.25f)) / 2f
        val cells = listOf(
            Triple(T.ENERGY.get(lang), NinebotDashboard.oneDecimal(t?.wh), "Wh"),
            Triple(T.PCT_USED.get(lang), NinebotDashboard.whole(t?.pctUsed), "%"),
            Triple(T.AVG_SPEED.get(lang), NinebotDashboard.oneDecimal(t?.avgMovingKmh), "km/h"),
        )
        for ((i, cell) in cells.withIndex()) {
            val cx = x + padX + i * (col + gap)
            fittedText(c, cell.first, cx, top + ls, ls, col, ink.muted, 255, WeemFonts.SEMIBOLD)
            valueWithUnit(c, cell.second, cell.third, cx, top + ls * 1.33f + vs, vs, col, alpha, unitScale = .75f, weight = WeemFonts.SEMIBOLD)
        }
    }

    /** Değer (sabit genişlikli rakam) ve yanında küçük birim; ikisi birlikte [width]'e sığar. Veri yoksa yalnız "—". */
    private fun valueWithUnit(c: Canvas, value: String, unit: String, x: Float, base: Float, size: Float, width: Float, alpha: Int,
                              unitScale: Float = .6f, weight: Int = WeemFonts.BOLD) {
        number.typeface = WeemFonts.get(weight)
        number.textAlign = Paint.Align.LEFT
        paint.typeface = WeemFonts.get(WeemFonts.SEMIBOLD)
        var vs = size
        var us = size * unitScale
        val dash = NinebotDashboard.DASH == value
        fun total(): Float {
            number.textSize = vs
            paint.textSize = us
            return number.measureText(value) + if (dash) 0f else dp(3f) + paint.measureText(unit)
        }
        val measured = total()
        if (measured > width && measured > 0) { val f = width / measured; vs *= f; us *= f; total() }
        number.color = ink.text
        number.alpha = alpha
        c.drawText(value, x, base, number)
        if (!dash) text(c, unit, x + number.measureText(value) + dp(3f), base, us, ink.muted, alpha, WeemFonts.SEMIBOLD, Paint.Align.LEFT)
        number.textAlign = Paint.Align.CENTER
    }

    private fun fittedText(c: Canvas, value: String, x: Float, y: Float, size: Float, width: Float, color: Int, alpha: Int, weight: Int) {
        paint.typeface = WeemFonts.get(weight)
        paint.letterSpacing = 0f
        paint.textSize = size
        val measured = paint.measureText(value)
        val s = if (measured > width && measured > 0) size * Math.max(1f, width) / measured else size
        text(c, value, x, y, s, color, alpha, weight, Paint.Align.LEFT)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, alpha: Int, weight: Int, align: Paint.Align, tracking: Float = 0f) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.color = color
        paint.alpha = alpha
        paint.textAlign = align
        paint.typeface = WeemFonts.get(weight)
        paint.letterSpacing = tracking
        c.drawText(value, x, y, paint)
        paint.letterSpacing = 0f
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // Dokunma eylemi adlandırılmaz: WEEM kaynağında erişilebilirlik eylem sabitleri yasak (NoGestureTest); ipucu çizilen satırda.
        info.text = ScooterPanel.accessibility(frame, lang)
    }

    private companion object {
        /** Tasarım ölçüleri (dp; `docs/tasarim/project/Scooter.dc.html`): kadran kartı, sürüş kartı, kart arası, bölüm arası. */
        const val DIAL = 74f
        const val TRIP = 64f
        const val GAP = 8f
        const val BLOCK = 10f
        /** Gösterge kartı yüksekliği / genişliği (262 / 358). */
        const val GAUGE_RATIO = .732f
    }
}
