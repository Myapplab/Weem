package com.kuryelog.app.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.kuryelog.app.ui.isDark
import com.kuryelog.app.ui.palette
import com.kuryelog.logic.map.weather.WeatherGlyph
import com.kuryelog.logic.map.weather.WeatherInk
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hava simgesi (S42; kullanıcı: "hava durumu ikonları çok kötü"): dolgulu, renkli çizim — ışınlı güneş diski, hilal ve yıldızlar, bulut arkasında güneş/ay,
 * iki katlı kapalı bulut, sis çizgileri, çisenti noktaları, eğik yağmur, kar tanesi, şimşek, rüzgâr. Yavaş hareket: ışınlar döner, yıldızlar parlar, damla ve
 * kar düşer, sis ve rüzgâr kayar, şimşek çakar. [animate] false: ilk karede durur (S38: haritadaki hap). Renkler `WeatherInk`.
 *
 * Bulut, hilal, şimşek ve rüzgâr silüetleri Google Material Symbols Rounded'dan (`cloud`, `dark_mode`, `bolt` dolu; `air`; Apache License 2.0, yollar
 * değiştirilmeden; `viewBox 0 -960 960 960`); güneş, yıldız, damla, kar ve sis burada çizilir. Çizim 100 birimlik kutuda, ölçekle büyür.
 */
@Composable
internal fun WeatherGlyphIcon(glyph: WeatherGlyph, size: Dp, animate: Boolean = true, modifier: Modifier = Modifier) {
    val phase = if (!animate) 0f
    else rememberInfiniteTransition(label = "weather").animateFloat(0f, 1f, infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    val ink = WeatherInk.of(palette().isDark)
    Canvas(modifier.size(size)) {
        val u = min(this.size.width, this.size.height) / 100f
        scale(u, u, pivot = Offset.Zero) { drawGlyph(glyph, ink, phase) }
    }
}

private const val CYCLE_MS = 3200

private fun DrawScope.drawGlyph(glyph: WeatherGlyph, ink: WeatherInk, t: Float) {
    when (glyph) {
        WeatherGlyph.SUN -> sun(ink, Offset(50f, 50f), 23f, t)
        WeatherGlyph.MOON -> {
            moon(ink, Rect(14f, 16f, 78f, 80f))
            star(ink, Offset(77f, 22f), 7.5f, twinkle(t, 0f))
            star(ink, Offset(87f, 44f), 5f, twinkle(t, .5f))
        }
        WeatherGlyph.PARTLY_DAY -> {
            sun(ink, Offset(37f, 37f), 15f, t, rays = 8, rayIn = 21f, rayOut = 29f, rayWidth = 5.5f)
            cloud(ink, Rect(22f, 40f, 95f, 93f), drift(t, 2f))
        }
        WeatherGlyph.PARTLY_NIGHT -> {
            moon(ink, Rect(10f, 8f, 56f, 54f))
            star(ink, Offset(66f, 15f), 5.5f, twinkle(t, 0f))
            cloud(ink, Rect(22f, 40f, 95f, 93f), drift(t, 2f))
        }
        WeatherGlyph.CLOUD -> {
            cloud(ink, Rect(42f, 14f, 94f, 52f), drift(t, -2f), back = true)
            cloud(ink, Rect(6f, 30f, 86f, 88f), drift(t, 2f))
        }
        WeatherGlyph.FOG -> {
            cloud(ink, Rect(14f, 6f, 86f, 58f), 0f)
            fogLine(ink, 70f, 16f, 70f, drift(t, 3f))
            fogLine(ink, 81f, 30f, 86f, drift(t, -3f))
            fogLine(ink, 92f, 22f, 62f, drift(t, 2f))
        }
        WeatherGlyph.DRIZZLE -> {
            cloud(ink, Rect(10f, 6f, 90f, 64f), 0f)
            for (i in 0 until 3) {
                val f = fall(t, i, 3)
                drawCircle(Color(ink.rain).copy(alpha = fade(f)), 4.4f, Offset(32f + i * 18f - f * 3f, 73f + f * 20f))
            }
        }
        WeatherGlyph.RAIN -> {
            cloud(ink, Rect(10f, 4f, 90f, 62f), 0f)
            for (i in 0 until 4) {
                val f = fall(t, i, 4)
                val x = 26f + i * 16f - f * 5f
                val y = 68f + f * 20f
                drawLine(Color(ink.rain).copy(alpha = fade(f)), Offset(x, y), Offset(x - 4f, y + 11f), 5.5f, StrokeCap.Round)
            }
        }
        WeatherGlyph.SNOW -> {
            cloud(ink, Rect(10f, 4f, 90f, 62f), 0f)
            for (i in 0 until 3) {
                val f = fall(t, i, 3)
                flake(ink, Offset(28f + i * 22f + sin((f + i) * 2 * PI).toFloat() * 3f, 74f + f * 16f), 8f, f * 60f, fade(f))
            }
        }
        WeatherGlyph.THUNDER -> {
            cloud(ink, Rect(8f, 4f, 92f, 65f), 0f, storm = true)
            // Yarım döngüde bir kısa çakma (ilk kare tam parlak: durağan hapta da öyle görünür).
            val flash = if (t % .5f > .44f) .35f else 1f
            shape(Glyphs.bolt, Rect(38f, 50f, 68f, 98f), Brush.verticalGradient(listOf(Color(ink.bolt), Color(ink.boltEdge)), 50f, 98f), Color(ink.boltEdge), 1.8f, flash)
        }
        WeatherGlyph.WIND -> translate(drift(t, 3f), 0f) {
            shape(Glyphs.air, Rect(6f, 14f, 94f, 86f), SolidColor(Color(ink.wind)), null, 0f)
        }
    }
}

/** Işınlı disk: ışınlar [t] ile 45° döner (döngüde bir ışın aralığı; dikiş görünmez). */
private fun DrawScope.sun(ink: WeatherInk, c: Offset, r: Float, t: Float, rays: Int = 8, rayIn: Float = 31f, rayOut: Float = 43f, rayWidth: Float = 7f) {
    val ray = Color(ink.ray)
    rotate(t * 360f / rays, c) {
        for (i in 0 until rays) {
            val a = i * 2 * PI / rays
            drawLine(ray, Offset(c.x + (cos(a) * rayIn).toFloat(), c.y + (sin(a) * rayIn).toFloat()), Offset(c.x + (cos(a) * rayOut).toFloat(), c.y + (sin(a) * rayOut).toFloat()), rayWidth, StrokeCap.Round)
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color(ink.sunCore), Color(ink.sunEdge)), Offset(c.x - r * .3f, c.y - r * .3f), r * 1.4f), r, c)
}

private fun DrawScope.moon(ink: WeatherInk, box: Rect) {
    val edge = ink.moonEdge.takeIf { it ushr 24 != 0 }?.let(::Color)
    shape(Glyphs.moon, box, Brush.linearGradient(listOf(Color(ink.moonLight), Color(ink.moonDark)), box.topLeft, box.bottomRight), edge, 2.2f)
}

/** Dört köşeli parıltı; [alpha] parlama. */
private fun DrawScope.star(ink: WeatherInk, c: Offset, r: Float, alpha: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, Color(ink.star).copy(alpha = alpha))
}

/** Bulut: üstten alta degrade + kenar; [back] arkadaki soluk bulut (kenarsız), [storm] fırtına bulutu; [dx] yatay kayma. */
private fun DrawScope.cloud(ink: WeatherInk, box: Rect, dx: Float, back: Boolean = false, storm: Boolean = false) {
    val (top, bottom, edge) = when {
        storm -> Triple(ink.stormTop, ink.stormBottom, ink.stormEdge)
        back -> Triple(ink.backTop, ink.backBottom, null)
        else -> Triple(ink.cloudTop, ink.cloudBottom, ink.cloudEdge)
    }
    translate(dx, 0f) {
        shape(Glyphs.cloud, box, Brush.verticalGradient(listOf(Color(top), Color(bottom)), box.top, box.bottom), edge?.let(::Color), 2.4f)
    }
}

private fun DrawScope.fogLine(ink: WeatherInk, y: Float, x1: Float, x2: Float, dx: Float) =
    drawLine(Color(ink.fog), Offset(x1 + dx, y), Offset(x2 + dx, y), 6f, StrokeCap.Round)

/** Altı kollu kar tanesi (üç çizgi). */
private fun DrawScope.flake(ink: WeatherInk, c: Offset, r: Float, deg: Float, alpha: Float) {
    val color = Color(ink.snow).copy(alpha = alpha)
    rotate(deg, c) {
        for (i in 0 until 3) {
            val a = i * PI / 3
            val dx = (cos(a) * r).toFloat()
            val dy = (sin(a) * r).toFloat()
            drawLine(color, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), 3.6f, StrokeCap.Round)
        }
    }
}

/** Silüeti [box]'a oranı korunarak ortalar; dolgu [fill], varsa kenar [edge] ([edgeWidth] birim). */
private fun DrawScope.shape(glyph: Glyph, box: Rect, fill: Brush, edge: Color?, edgeWidth: Float, alpha: Float = 1f) {
    val k = min(box.width / glyph.bounds.width, box.height / glyph.bounds.height)
    val left = box.left + (box.width - glyph.bounds.width * k) / 2
    val top = box.top + (box.height - glyph.bounds.height * k) / 2
    withTransform({
        translate(left, top)
        scale(k, k, pivot = Offset.Zero)
        translate(-glyph.bounds.left, -glyph.bounds.top)
    }) {
        drawPath(glyph.path, fill, alpha = alpha)
        if (edge != null) drawPath(glyph.path, edge, alpha = alpha, style = Stroke(edgeWidth / k, join = StrokeJoin.Round))
    }
}

/** Döngü içinde [offset] kaydırmalı yumuşak parlama (0,45–1). */
private fun twinkle(t: Float, offset: Float) = .45f + .55f * ((sin((t + offset) * 2 * PI) + 1) / 2).toFloat()

/** Yatay salınım (±[amp] birim). */
private fun drift(t: Float, amp: Float) = (sin(t * 2 * PI) * amp).toFloat()

/** [n] tanenin [i]'incisinin düşüş konumu (0–1); yarım aralık kaydırmalı: ilk karede (durağan hapta) hepsi görünür. */
private fun fall(t: Float, i: Int, n: Int) = (t + (i + .5f) / n) % 1f

/** Düşen damla/tanenin görünürlüğü: başta belirir, sonda söner. */
private fun fade(f: Float) = when {
    f < .15f -> f / .15f
    f > .8f -> (1f - f) / .2f
    else -> 1f
}

/** Silüet yolu ve sınırları (Material Symbols koordinatı; sınırlar eğriler örneklenerek bir kez ölçüldü). */
private class Glyph(d: String, val bounds: Rect) {
    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
}

private object Glyphs {
    val cloud = Glyph(
        "M260-160q-91 0-155.5-63T40-377q0-78 47-139t123-78q25-92 100-149t170-57q117 0 198.5 81.5T760-520q69 8 114.5 59.5T920-340q0 75-52.5 127.5T740-160H260Z",
        Rect(40f, -800f, 920f, -160f),
    )
    val moon = Glyph(
        "M480-120q-151 0-255.5-104.5T120-480q0-138 90-239.5T440-838q13-2 23 3.5t16 14.5q6 9 6.5 21t-7.5 23q-17 26-25.5 55t-8.5 61q0 90 63 153t153 63q31 0 61.5-9t54.5-25q11-7 22.5-6.5T819-479q10 5 15.5 15t3.5 24q-14 138-117.5 229T480-120Z",
        Rect(120f, -838.5f, 838.5f, -120f),
    )
    val bolt = Glyph(
        "M360-360H236q-24 0-35.5-21.5T203-423l299-430q10-14 26-19.5t33 .5q17 6 25 21t6 32l-32 259h155q26 0 36.5 23t-6.5 43L416-100q-11 13-27 17t-31-3q-15-7-23.5-21.5T328-139l32-221Z",
        Rect(195.3f, -875.1f, 755.5f, -81.5f),
    )
    val air = Glyph(
        "M460-160q-32 0-59.5-16T356-220q-11-20-.5-40t32.5-20q13 0 23 8t18 18q5 7 13.5 10.5T460-240q17 0 28.5-11.5T500-280q0-17-11.5-28.5T460-320H120q-17 0-28.5-11.5T80-360q0-17 11.5-28.5T120-400h340q50 0 85 35t35 85q0 50-35 85t-85 35ZM120-560q-17 0-28.5-11.5T80-600q0-17 11.5-28.5T120-640h500q25 0 42.5-17.5T680-700q0-25-17.5-42.5T620-760q-16 0-30 7.5T568-731q-7 12-17 21.5t-24 9.5q-20 0-32.5-15t-6.5-32q14-42 50.5-67.5T620-840q58 0 99 41t41 99q0 58-41 99t-99 41H120Zm678 308q-20 9-39-2.5T740-288q0-14 9.5-23.5T771-328q14-8 21.5-22t7.5-30q0-25-17.5-42.5T740-440H120q-17 0-28.5-11.5T80-480q0-17 11.5-28.5T120-520h620q58 0 99 41t41 99q0 42-22 76.5T798-252Z",
        Rect(80f, -840f, 880f, -160f),
    )
}
