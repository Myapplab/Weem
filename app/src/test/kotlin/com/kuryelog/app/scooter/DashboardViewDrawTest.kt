package com.kuryelog.app.scooter

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    @Test fun backgroundStaysInsideTheView() {
        val v = DashboardView(ApplicationProvider.getApplicationContext())
        v.style(dark = true, lang = Lang.TR)
        v.show(null)
        v.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 400, 600)
        val bmp = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.MAGENTA)
        c.translate(0f, 200f)
        v.draw(c)
        assertEquals(Color.MAGENTA, bmp.getPixel(200, 100))
        assertNotEquals(Color.MAGENTA, bmp.getPixel(2, 202))
    }
}
