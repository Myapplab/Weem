package com.kuryelog.app.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kuryelog.logic.map.ui.PuckStyle

/**
 * Konum imleci (MAP.md 6.5; `logic/map` `ui.PuckStyle` ölçüleri): rota açık sürerken ve hareket yönü [course] belliyse küçük beyaz kenarlı mavi ok (`puck-arrow`,
 * gidiş yönüne döner); dururken ya da rota yokken nokta (+ [beam] yönü biliniyorsa koni) (`puck-dot`). [arrow] rota açık mı. Harita yüzeyleri aynı çizimi [PuckBitmaps]
 * ile bit eşlemi olarak kullanır; yöne göre döndürmeyi yüzey yapar.
 */
@Composable
fun LocationPuck(course: Double?, arrow: Boolean, modifier: Modifier = Modifier, beam: Double? = course) {
    val showArrow = PuckStyle.showsArrow(arrow, course)
    Canvas(modifier.size(PuckStyle.CANVAS.dp).testTag(if (showArrow) "puck-arrow" else "puck-dot")) {
        val k = size.minDimension / PuckStyle.CANVAS.toFloat()
        drawIntoNative(k) { c ->
            if (showArrow) {
                c.save(); c.rotate(course!!.toFloat(), 30f, 30f); PuckBitmaps.drawArrow(c); c.restore()
            } else {
                if (beam != null) { c.save(); c.rotate(beam.toFloat(), 30f, 30f); PuckBitmaps.drawBeam(c); c.restore() }
                PuckBitmaps.drawDot(c)
            }
        }
    }
}

private fun DrawScope.drawIntoNative(scale: Float, block: (android.graphics.Canvas) -> Unit) {
    drawContext.canvas.nativeCanvas.let { c ->
        c.save(); c.scale(scale, scale); block(c); c.restore()
    }
}

/** İmlecin iki yüzeyde ortak bit eşlemleri (60×60 dp kutu, merkez 30,30; ok ve koni kuzeye bakar). */
object PuckBitmaps {
    private const val C = 30f

    fun drawDot(c: android.graphics.Canvas) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PuckStyle.SHADOW_COLOR; maskFilter = BlurMaskFilter(PuckStyle.SHADOW_BLUR.toFloat(), BlurMaskFilter.Blur.NORMAL) }
        c.drawCircle(C, C, PuckStyle.SHADOW_RADIUS.toFloat(), shadow)
        c.drawCircle(C, C, PuckStyle.RING_RADIUS.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawCircle(C, C, PuckStyle.DOT_RADIUS.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PuckStyle.DOT_COLOR })
    }

    /** Yön konisi: r 28, ±30°, merkezde %55 → kenarda %0. */
    fun drawBeam(c: android.graphics.Canvas) {
        val r = PuckStyle.BEAM_RADIUS.toFloat()
        val rgb = PuckStyle.BEAM_COLOR and 0xFFFFFF
        val a = (PuckStyle.BEAM_ALPHA * 255).toInt() shl 24
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(C, C, r, a or rgb, rgb, Shader.TileMode.CLAMP) }
        val half = PuckStyle.BEAM_HALF_ANGLE_DEG.toFloat()
        // Kuzey = −90° (Android yayı doğudan saat yönüne).
        c.drawArc(RectF(C - r, C - r, C + r, C + r), -90f - half, 2 * half, true, p)
    }

    fun drawArrow(c: android.graphics.Canvas) {
        val pts = PuckStyle.arrowPoints(C.toDouble(), C.toDouble(), PuckStyle.ARROW_SIZE)
        val path = android.graphics.Path().apply {
            moveTo(pts[0].first.toFloat(), pts[0].second.toFloat())
            for (i in 1 until pts.size) lineTo(pts[i].first.toFloat(), pts[i].second.toFloat())
            close()
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PuckStyle.SHADOW_COLOR; maskFilter = BlurMaskFilter(PuckStyle.ARROW_SHADOW_BLUR.toFloat(), BlurMaskFilter.Blur.NORMAL) }
        c.save(); c.translate(0f, 1f); c.drawPath(path, shadow); c.restore()
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = PuckStyle.ARROW_STROKE.toFloat(); strokeJoin = Paint.Join.ROUND
        })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PuckStyle.DOT_COLOR })
    }

    /** [density] dp → px; [arrow] ok, değilse nokta (+ [beam] koni). Kuzeye bakar: yüzey işaretin yönüyle döndürür. */
    fun render(density: Float, arrow: Boolean, beam: Boolean): Bitmap {
        val px = (PuckStyle.CANVAS * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.scale(density, density)
        if (arrow) drawArrow(c) else { if (beam) drawBeam(c); drawDot(c) }
        return bmp
    }
}
