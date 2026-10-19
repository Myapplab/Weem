package com.kuryelog.app.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.kuryelog.logic.map.ui.MapPalette

/** Durak ve varılan hedef işaretleri (MAP.md 6.6 madde 5; 34×34 dp): iki yüzey aynı bit eşlemi. */
object MarkerBitmaps {
    private const val SIZE = 34f

    /** Durak: mor daire, beyaz numara (15 pt w800); ilk durak tam, diğerleri %60. */
    fun stop(density: Float, number: String, first: Boolean, dark: Boolean): Bitmap = draw(density) { c ->
        val pink = MapPalette.of(dark).pink
        val alpha = if (first) 255 else 153
        c.drawCircle(SIZE / 2, SIZE / 2, SIZE / 2 - 1, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pink; this.alpha = alpha })
        c.drawCircle(SIZE / 2, SIZE / 2, SIZE / 2 - 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f })
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText(number, SIZE / 2, SIZE / 2 - (t.descent() + t.ascent()) / 2, t)
    }

    /** Varılan hedef (`reached-pin`): siyah zemin, yeşil 3 px halka + bulanık parıltı, bayrak. */
    fun reached(density: Float, dark: Boolean): Bitmap = draw(density) { c ->
        val green = MapPalette.of(dark).green
        c.drawCircle(SIZE / 2, SIZE / 2, SIZE / 2 - 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.OUTER) })
        c.drawCircle(SIZE / 2, SIZE / 2, SIZE / 2 - 3, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() })
        c.drawCircle(SIZE / 2, SIZE / 2, SIZE / 2 - 4.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.STROKE; strokeWidth = 3f })
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; textAlign = Paint.Align.CENTER }
        c.drawText("⚑", SIZE / 2, SIZE / 2 - (t.descent() + t.ascent()) / 2, t.apply { color = green })
    }

    /** Yüzeylerin ortak anahtarı: aynı anahtar aynı bit eşlemi (stil görüntüsü adı). */
    fun key(mk: MapMarker, dark: Boolean): String = when (mk.kind) {
        MarkerKind.REACHED -> "reached-$dark"
        MarkerKind.STOP_FIRST -> "stop1-${mk.label}-$dark"
        MarkerKind.STOP -> "stop-${mk.label}-$dark"
        MarkerKind.LIGHT_WAIT -> "lightwait-$dark"
        MarkerKind.POI_LIGHT -> "poi-l-$dark"
        else -> "poi-${mk.kind.name}-${mk.label}-${mk.name}-$dark"
    }

    fun bitmap(mk: MapMarker, density: Float, dark: Boolean): Bitmap = when (mk.kind) {
        MarkerKind.REACHED -> reached(density, dark)
        MarkerKind.STOP_FIRST -> stop(density, mk.label, true, dark)
        MarkerKind.STOP -> stop(density, mk.label, false, dark)
        MarkerKind.LIGHT_WAIT -> lightWait(density)
        else -> poi(density, mk, dark)
    }

    private const val NAME_W = 140f
    private const val NAME_H = 16f

    /** Yer simgesi (MAP.md 5.3): metro kırmızı daire "M" (30), AVM mor yuvarlak kutu (32), ışık koyu daire + sarı ışık (16), yemek turuncu daire + tür simgesi (30); adı altında. Merkez simgenin ortasıdır. */
    private fun poi(density: Float, mk: MapMarker, dark: Boolean): Bitmap {
        val icon = when (mk.kind) {
            MarkerKind.POI_METRO -> 30f
            MarkerKind.POI_MALL -> 32f
            MarkerKind.POI_LIGHT -> 16f
            else -> 30f
        }
        val extra = if (mk.name.isEmpty()) 0f else NAME_H + 2
        val half = icon / 2 + extra
        val w = if (mk.name.isEmpty()) icon else NAME_W
        val h = half * 2
        val px = (w * density).toInt().coerceAtLeast(1)
        val py = (h * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, py, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(density, density)
        val cx = w / 2
        val cy = half
        val food = MapPalette.of(dark).orange
        when (mk.kind) {
            MarkerKind.POI_METRO -> {
                c.drawCircle(cx, cy, icon / 2 - 1, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE30613.toInt() })
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
                c.drawText("M", cx, cy - (t.descent() + t.ascent()) / 2, t)
            }
            MarkerKind.POI_MALL -> {
                c.drawRoundRect(cx - icon / 2 + 1, cy - icon / 2 + 1, cx + icon / 2 - 1, cy + icon / 2 - 1, 9f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7B4DFF.toInt() })
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 16f; textAlign = Paint.Align.CENTER }
                c.drawText("🛍", cx, cy - (t.descent() + t.ascent()) / 2, t)
            }
            MarkerKind.POI_LIGHT -> {
                c.drawCircle(cx, cy, icon / 2 - .5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222222.toInt() })
                val d = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB13D.toInt() }
                for (i in -1..1) c.drawCircle(cx, cy + i * 3f, 1.2f, d)
            }
            else -> {
                c.drawCircle(cx, cy, icon / 2 - 1, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = food })
                val glyph = when (mk.label) { "f" -> "🍔"; "c" -> "☕"; "t" -> "🛒"; else -> "🍽" }
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 15f; textAlign = Paint.Align.CENTER }
                c.drawText(glyph, cx, cy - (t.descent() + t.ascent()) / 2, t)
            }
        }
        if (mk.name.isNotEmpty()) {
            val fg = if (dark) 0xFFE8EDF4.toInt() else 0xFF1A2330.toInt()
            val halo = if (dark) 0xFF10151C.toInt() else 0xFFFFFFFF.toInt()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            val ty = cy + icon / 2 + NAME_H - 4
            val shown = fit(p, mk.name, NAME_W - 4)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = halo
            c.drawText(shown, cx, ty, p)
            p.style = Paint.Style.FILL; p.color = fg
            c.drawText(shown, cx, ty, p)
        }
        return bmp
    }

    private fun fit(p: Paint, text: String, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var s = text
        while (s.length > 1 && p.measureText("$s…") > maxW) s = s.dropLast(1)
        return "$s…"
    }

    /** Işıkta bekleme işareti (`lightAt`): 24×24, koyu zemin, turuncu halka, ışık simgesi (YolGo nabzı sürümde yok: sabit). */
    private fun lightWait(density: Float): Bitmap {
        val s = 24f
        val px = (s * density).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            c.scale(density, density)
            c.drawCircle(s / 2, s / 2, s / 2 - 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1B.toInt() })
            c.drawCircle(s / 2, s / 2, s / 2 - 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF9F1C.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f })
            val d = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF9F1C.toInt() }
            for (i in -1..1) c.drawCircle(s / 2, s / 2 + i * 4f, 1.6f, d)
        }
    }

    private fun draw(density: Float, block: (Canvas) -> Unit): Bitmap {
        val px = (SIZE * density).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { val c = Canvas(it); c.scale(density, density); block(c) }
    }
}
