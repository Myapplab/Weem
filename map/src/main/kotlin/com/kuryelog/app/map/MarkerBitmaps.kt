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

    private fun draw(density: Float, block: (Canvas) -> Unit): Bitmap {
        val px = (SIZE * density).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { val c = Canvas(it); c.scale(density, density); block(c) }
    }
}
