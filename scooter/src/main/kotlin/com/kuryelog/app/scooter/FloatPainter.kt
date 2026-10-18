package com.kuryelog.app.scooter

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.OverlayPalette
import com.kuryelog.logic.scooter.NinebotDashboard
import com.kuryelog.logic.scooter.PanelField
import com.kuryelog.logic.scooter.TripMeter

/**
 * Sürüş Paneli renk rolleri (NB `Ui.palette`): `OverlayPalette` = NB `RawPalette`. Panel, top ve PiP ızgarası aynı görünümü paylaşır.
 */
internal class Ink(p: OverlayPalette) {
    val bg = p.bg
    val surface = p.surface
    val surfaceHigh = p.raised
    val line = p.line
    val text = p.text
    val muted = p.muted
    val accent = p.accentText
    val accentInk = p.accentInk
    val power = p.positive
    val record = p.positive
    val danger = p.danger
    val warn = p.warn
    val warnBg = p.warnBg
    val hero = p.hero

    companion object {
        private val light = Ink(OverlayPalette.forMode(false))
        private val dark = Ink(OverlayPalette.forMode(true))
        fun of(dark: Boolean): Ink = if (dark) this.dark else light
    }
}

/** Seçilen panel değerlerini çizer: yuvarlak top, açılmış bar ve PiP ızgarası tek görünüm (NB `FloatPainter.java` satır satır). */
internal class FloatPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val value = Paint(Paint.ANTI_ALIAS_FLAG)
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var ink: Ink = Ink.of(true)
    var lang: Lang = Lang.TR

    /** Ayarlardaki yazı boyutu çarpanı (1 = sığdırılmış boy); değer yine hücreden taşmaz. */
    var textScale = 1f

    init {
        value.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        value.fontFeatureSettings = "'tnum'"
        value.textAlign = Paint.Align.CENTER
        caption.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        caption.textAlign = Paint.Align.CENTER
    }

    /** Topta ilk seçilen değer ve birimi. */
    fun ball(c: Canvas, cx: Float, cy: Float, r: Float, f: PanelField, s: NinebotDashboard.Snapshot?, t: TripMeter.Stats?, alpha: Int) {
        fill.style = Paint.Style.FILL
        fill.color = (ink.surface and 0x00ffffff) or 0xf5000000.toInt()
        c.drawCircle(cx, cy, r, fill)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = r * .06f
        fill.color = ink.accent
        fill.alpha = if (alpha == 255) 220 else 90
        c.drawCircle(cx, cy, r - r * .03f, fill)
        fill.style = Paint.Style.FILL
        cell(c, cx - r * .92f, cy - r * .72f, cx + r * .92f, cy + r * .72f, f, s, t, alpha)
    }

    /** Yuvarlatılmış bar (yatay ya da dikey), değer başına bir hücre. */
    fun bar(c: Canvas, w: Float, h: Float, fields: List<PanelField>, horizontal: Boolean, s: NinebotDashboard.Snapshot?, t: TripMeter.Stats?, alpha: Int) {
        val r = Math.min(w, h) / 2f
        rect.set(0f, 0f, w, h)
        fill.style = Paint.Style.FILL
        fill.color = (ink.surface and 0x00ffffff) or 0xf5000000.toInt()
        c.drawRoundRect(rect, r, r, fill)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = r * .06f
        fill.color = ink.accent
        fill.alpha = if (alpha == 255) 200 else 90
        rect.inset(r * .03f, r * .03f)
        c.drawRoundRect(rect, r, r, fill)
        fill.style = Paint.Style.FILL
        val n = fields.size
        val pad = r * if (horizontal) .6f else .35f
        for (i in 0 until n) {
            if (horizontal) {
                val cw = (w - 2 * pad) / n
                val x = pad + cw * i
                cell(c, x, 0f, x + cw, h, fields[i], s, t, alpha)
                if (i > 0) divider(c, x, h * .25f, x, h * .75f)
            } else {
                val ch = (h - 2 * pad) / n
                val y = pad + ch * i
                cell(c, 0f, y, w, y + ch, fields[i], s, t, alpha)
                if (i > 0) divider(c, w * .25f, y, w * .75f, y)
            }
        }
    }

    /** Sistem PiP penceresi ızgarası: iki değere kadar tek satır, üç-dört değerde 2 × 2. */
    fun grid(c: Canvas, w: Float, h: Float, fields: List<PanelField>, s: NinebotDashboard.Snapshot?, t: TripMeter.Stats?, alpha: Int) {
        c.drawColor(ink.bg)
        val n = fields.size
        val cols = if (n <= 2) n else 2
        val rows = if (n <= 2) 1 else 2
        val cw = w / cols
        val ch = h / rows
        for (i in 0 until n) {
            val x = cw * (i % cols)
            val y = ch * (i / cols)
            cell(c, x, y, x + cw, y + ch, fields[i], s, t, alpha)
        }
    }

    private fun divider(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 1.5f
        fill.color = ink.line
        c.drawLine(x1, y1, x2, y2, fill)
        fill.style = Paint.Style.FILL
    }

    /** Değer hücrenin izin verdiği kadar büyük, birim altında; güç ana paneldeki gibi yeşil. */
    fun cell(c: Canvas, l: Float, t: Float, r: Float, b: Float, f: PanelField, s: NinebotDashboard.Snapshot?, trip: TripMeter.Stats?, alpha: Int) {
        val w = r - l
        val h = b - t
        val cx = (l + r) / 2
        val v = f.value(s, trip)
        var size = Math.min(h * .46f, w * .42f) * textScale
        size = Math.min(size, h * .62f)
        value.textSize = size
        val room = w * if (textScale > 1f) .94f else .86f
        if (value.measureText(v) > room) size *= room / value.measureText(v)
        value.textSize = size
        value.color = if (f == PanelField.POWER) ink.power else ink.text
        value.alpha = alpha
        var cs = Math.max(9f, Math.min(h * .15f * Math.max(1f, textScale), size * .42f))
        caption.textSize = cs
        caption.color = ink.muted
        caption.alpha = alpha
        val label = f.caption(lang)
        val labelWidth = caption.measureText(label)
        if (labelWidth > w * .86f) { cs *= w * .86f / labelWidth; caption.textSize = cs }
        val block = size * .74f + cs * 1.35f
        val top = t + (h - block) / 2
        c.drawText(v, cx, top + size * .74f, value)
        c.drawText(label, cx, top + block, caption)
    }
}
