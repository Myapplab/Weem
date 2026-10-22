package com.kuryelog.app.scooter

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A10.4d: Sürüş Paneli zemini yalnız kendi alanına çizilir. `drawColor` kırpmasız yazılım tuvalinde (görsel tur, donanım
 * hızlandırması olmayan pencere) Scooter sekmesinin üstündeki Compose başlığını ve durum satırını örtüyordu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DashboardViewDrawTest {
    /** Görünümü [w]×[h] piksel boyunda, üstünde ve altında [h] boş bant bırakarak çizer; bantlarda boyanmış piksel sayısını verir. */
    private fun paintedOutside(w: Int, h: Int, dark: Boolean = true): Int {
        val v = DashboardView(ApplicationProvider.getApplicationContext())
        v.style(dark = dark, lang = Lang.AZ)
        v.show(null)
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, 3 * h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.MAGENTA)
        c.translate(0f, h.toFloat())
        v.draw(c)
        var n = 0
        for (y in (0 until h) + (2 * h until 3 * h)) for (x in 0 until w) if (bmp.getPixel(x, y) != Color.MAGENTA) n++
        return n
    }

    @Test fun backgroundStaysInsideTheView() {
        val d = ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
        assertEquals(0, paintedOutside((400 * d).toInt(), (600 * d).toInt()))
    }

    /** Kısa dar dikey görünüm (küçük pencere, 230–310 dp yüksek): kartlar alttan taşmaz, gösterge küçülür. */
    @Test fun shortPortraitKeepsTheCardsInside() {
        val d = ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
        for (h in listOf(232, 260, 300)) assertEquals("h=$h dp", 0, paintedOutside((240 * d).toInt(), (h * d).toInt(), dark = false))
    }

    /** Kompakt ızgara (230 dp altı) da zemini kendi alanına çizer. */
    @Test fun compactGridStaysInside() {
        val d = ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
        assertEquals(0, paintedOutside((360 * d).toInt(), (180 * d).toInt()))
    }
}
