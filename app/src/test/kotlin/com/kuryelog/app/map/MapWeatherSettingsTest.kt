package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `weather_section_test.dart` (4; A6.2b, MAP.md 10.9, 16.3). İlk 2 test bölüm düzeyinde (şerit anahtarı varsayılan açık ve saklanır; açıklama bilgi penceresinde); son 2 test
 * UYARLANIR (WEEM ayar yapısı: Daha fazla → Harita ayarları → "Hava durumu"; "Rawstats bilgileri" bölümü yok): Harita ayarlarında "Hava durumu" bir kez; kayıtlı kapalı ayar korunur.
 * Kalıcılık (`weather.strip.v1`) yerine geri çağrı değeri denetlenir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h2600dp-port-xxhdpi")
class MapWeatherSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun seritAnahtariVarsayilanAcikVeKapatilincaSaklanir() {
        var on by mutableStateOf(true) // varsayılan açık
        compose.setContent { WeemTheme(false) { MapWeatherSettings(on, { on = it }, Lang.TR) } }
        compose.onNodeWithTag("weather-strip-toggle").assertExists()
        compose.onNodeWithTag("weather-strip-toggle").performClick()
        compose.waitForIdle()
        assertFalse(on) // kapatıldı
    }

    @Test
    fun aciklamaBilgiPenceresindeHerZamanGorunmez() {
        compose.setContent { WeemTheme(false) { MapWeatherSettings(true, {}, Lang.TR) } }
        // Açıklama metni başlangıçta görünmez (ℹ diyaloğuna taşındı).
        compose.onAllNodes(hasText("Son bilinen konuma göre", substring = true)).assertCountEquals(0)
        compose.onNodeWithTag("weather-info").assertExists()
        compose.onNodeWithTag("weather-info").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("Son bilinen konuma göre", substring = true)).assertCountEquals(1) // diyalogda
    }

    @Test
    fun haritaAyarlarindaHavaDurumuBolumuVarRawstatsBilgileriYok() {
        compose.setContent { WeemTheme(false) { MapSettingsScreen(Lang.TR, weatherStrip = true, onWeatherStripChange = {}) } }
        compose.onAllNodes(hasText("Hava durumu")).assertCountEquals(1)
        compose.onAllNodes(hasText("Rawstats bilgileri")).assertCountEquals(0)
    }

    @Test
    fun kayitliKapaliAyarKorunur() {
        compose.setContent { WeemTheme(false) { MapSettingsScreen(Lang.TR, weatherStrip = false, onWeatherStripChange = {}) } }
        compose.onNodeWithText("Hava durumu").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("weather-strip-toggle").assertIsOff() // eski kayıtlı "kapalı" aynen okunur
    }
}
