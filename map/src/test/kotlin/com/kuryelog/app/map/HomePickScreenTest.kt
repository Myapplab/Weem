package com.kuryelog.app.map

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `home_picker_test.dart` (5; 5. tanım Geri/İmtina ile 2; A6.2b, MAP.md 6.11, 6.14): girdi merkez, sabit iğne, 50 m daire, Kaydet sürüklenen noktayı `Double` olarak döner ·
 * girdi yoksa GPS (saklı ev kullanılmaz) · uzun basış noktayı seçer ve merkez olur · konum hatasında Kaydet pasif, İptal çalışır · AZ + koyu: "Ev məkanını seç", koyu tema, Geri ve
 * "İmtina" iptal döner. MapLibre yerine sahte yüzey; "tercih yazılmaz, ev okunmaz" → "yalnız sonuç döner, ev kaydına yazmaz" (sonuç doğrulaması A2.2 `HomePickResultTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomePickScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var surface: HomePickSurfaceState? = null
    private val results = ArrayList<HomePickOutcome>()
    private var locates = 0

    private fun show(initial: GeoPoint?, locate: suspend () -> GeoPoint = { locates++; GeoPoint(40.5, 49.9) }, lang: Lang = Lang.TR, dark: Boolean = false) {
        compose.setContent {
            HomePickScreen(initial, locate, lang, dark, onResult = { results += it }) { s ->
                surface = s
                Box(Modifier.fillMaxSize().testTag("fake-surface"))
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun girdiMerkezSabitIgneElliMetreDaireKaydetSurukleneniDondurur() {
        show(GeoPoint(40.4, 49.8))
        assertEquals(GeoPoint(40.4, 49.8), surface!!.center)
        assertEquals(50.0, surface!!.radiusM, 0.0)
        assertEquals(0, locates)
        val pin = compose.onNodeWithTag("home-pin").getBoundsInRoot()
        surface!!.onCenterChange(GeoPoint(40.41, 49.81)) // harita sürüklendi
        compose.waitForIdle()
        assertEquals("iğne ekranda sabit", pin, compose.onNodeWithTag("home-pin").getBoundsInRoot())
        compose.onNodeWithText("Kaydet").performClick()
        compose.waitForIdle()
        val saved = results.last() as HomePickOutcome.Saved
        assertEquals(40.41, saved.lat, 1e-8)
        assertEquals(49.81, saved.lon, 1e-8)
        assertEquals("yalnız sonuç döner; GPS'e ya da ev kaydına dokunmaz", 0, locates)
    }

    @Test
    fun girdiYoksaMevcutKonumKullanilirSaklıEvDegil() {
        show(null)
        assertEquals(1, locates)
        assertEquals(40.5, surface!!.center.lat, 0.0)
    }

    @Test
    fun uzunBasisBasilanNoktayiSecerVeMerkezOlur() {
        show(GeoPoint(40.4, 49.8))
        surface!!.onLongPress(GeoPoint(40.42, 49.82))
        compose.waitForIdle()
        assertEquals(GeoPoint(40.42, 49.82), surface!!.center)
        assertTrue("seçim sonuç döndürmez, Kaydet bekler", results.isEmpty())
    }

    @Test
    fun konumHatasindaKaydetPasifIptalCalisir() {
        show(null, locate = { throw IllegalStateException("GPS off") })
        compose.onNodeWithText("Kaydet").assertIsNotEnabled()
        compose.onNodeWithText("İptal").assertIsEnabled()
        compose.onNodeWithText("İptal").performClick()
        compose.waitForIdle()
        assertEquals(HomePickOutcome.Cancelled, results.last())
    }

    @Test
    fun azKoyuGeriVeImtinaIptalDondurur() {
        show(GeoPoint(40.4, 49.8), lang = Lang.AZ, dark = true)
        compose.onAllNodes(hasText("Ev məkanını seç")).assertCountEquals(1)
        compose.onNodeWithTag("home-pick-dark").assertExists()
        compose.onNodeWithText("İmtina").performClick()
        compose.waitForIdle()
        assertEquals(listOf<HomePickOutcome>(HomePickOutcome.Cancelled), results)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf<HomePickOutcome>(HomePickOutcome.Cancelled, HomePickOutcome.Cancelled), results)
    }
}
