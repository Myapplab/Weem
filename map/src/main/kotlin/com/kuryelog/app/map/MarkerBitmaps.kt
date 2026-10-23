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

    /** Yer işaretleri (MAP.md 5.3): metro `#E30613` daire + beyaz M; AVM `#7B4DFF` yuvarlak kutu; ışık `#222222` daire + `#FFB13D` ışık; yemek/market palet `food` dairesi. */
    fun poi(density: Float, kind: MarkerKind, dark: Boolean): Bitmap = draw(density) { c ->
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        fun letter(t: String, size: Float) {
            glyph.textSize = size
            c.drawText(t, SIZE / 2, SIZE / 2 - (glyph.descent() + glyph.ascent()) / 2, glyph)
        }
        fun disc(r: Float, color: Int) = c.drawCircle(SIZE / 2, SIZE / 2, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        when (kind) {
            MarkerKind.POI_METRO -> { disc(13f, 0xFFE30613.toInt()); letter("M", 15f) }
            MarkerKind.POI_MALL -> {
                c.drawRoundRect(2f, 2f, SIZE - 2, SIZE - 2, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7B4DFF.toInt() })
                letter("A", 15f)
            }
            MarkerKind.POI_LIGHT -> {
                disc(8f, 0xFF222222.toInt())
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB13D.toInt() }
                for (dy in listOf(-3.2f, 0f, 3.2f)) c.drawCircle(SIZE / 2, SIZE / 2 + dy, 1.6f, dot)
            }
            MarkerKind.POI_LIGHT_ROUTE -> routeLight(c)
            else -> {
                disc(13f, MapPalette.of(dark).food)
                letter(
                    when (kind) { MarkerKind.POI_FASTFOOD -> "F"; MarkerKind.POI_CAFE -> "C"; MarkerKind.POI_MARKET -> "T"; else -> "R" },
                    14f,
                )
            }
        }
    }

    /**
     * S38 rota ışığı: gerçek trafik ışığı gibi dikey kutu (16×30 dp, beyaz kenar, gölge) ve kırmızı/sarı/yeşil üç lamba; sıradan ışık işaretinin (16 dp daire)
     * yaklaşık iki katı, uzaktan seçilir.
     */
    private fun routeLight(c: Canvas) {
        val w = 16f
        val h = 30f
        val l = SIZE / 2 - w / 2
        val t = SIZE / 2 - h / 2
        c.drawRoundRect(l, t + 1f, l + w, t + h + 1f, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL) })
        c.drawRoundRect(l, t, l + w, t + h, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1E1E.toInt() })
        c.drawRoundRect(l, t, l + w, t + h, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f })
        val lamp = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((i, color) in listOf(0xFFFF3B30, 0xFFFFB13D, 0xFF34C759).withIndex()) {
            lamp.color = color.toInt()
            c.drawCircle(SIZE / 2, t + 6f + i * 9f, 3.6f, lamp)
        }
    }

    /** İki yüzeyin ortak bit eşlem anahtarı ve çizimi. */
    fun key(mk: MapMarker, dark: Boolean): String = when (mk.kind) {
        MarkerKind.REACHED -> "reached-$dark"
        MarkerKind.STOP_FIRST -> "stop1-${mk.label}-$dark"
        MarkerKind.STOP -> "stop-${mk.label}-$dark"
        else -> "${mk.kind.name.lowercase()}-$dark"
    }

    fun of(mk: MapMarker, density: Float, dark: Boolean): Bitmap = when (mk.kind) {
        MarkerKind.REACHED -> reached(density, dark)
        MarkerKind.STOP_FIRST -> stop(density, mk.label, true, dark)
        MarkerKind.STOP -> stop(density, mk.label, false, dark)
        else -> poi(density, mk.kind, dark)
    }

    private fun draw(density: Float, block: (Canvas) -> Unit): Bitmap {
        val px = (SIZE * density).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { val c = Canvas(it); c.scale(density, density); block(c) }
    }
}
