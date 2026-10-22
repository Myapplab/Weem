package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavState
import com.kuryelog.logic.map.nav.Stop
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `offer_navigation_removed_test.dart` ilk 2 test (A6.2b, MAP.md 6.1, 7.15, 16.3) — UYARLANIR (Y1/Y4 ekran kuralı; son 2 test çevrilmez: kaldırılmış teklif sayfası, veri seti
 * tetikleri Bölüm 13): varış odağında yalnız harita + alttaki adres kartı, uyarılar görünür · ana ekranda menü ve kayıt noktası yok (WEEM'de Ayarlar sekme kabuğundadır, harita
 * ekranında ayar simgesi yok; kayıt durumu Yollarım → Sürüş kayıtları'nda, `RideLogStatusTextTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w380dp-h844dp-port-xxhdpi")
class MapTabTest {
    @get:Rule val compose = createComposeRule()

    private val now = MapTestFixtures.AT

    @Test
    fun varisOdagindaYalnizHaritaKonumVeAltttakiAdresUyarilarGorunur() {
        val stop = Stop(GeoPoint(40.41, 49.87), "Test küçəsi 5", unitNote = "Blok A · kat 3")
        val nav = NavState(stops = listOf(stop), plans = mapOf(RideMode.BATTERY to Plan(RideMode.BATTERY, listOf(0), emptyList())))
        compose.setContent { WeemTheme(false) { MapTab(MapTabState(DayView(DayState(), null), nav = nav, arrivalFocus = true, nowMs = now), Lang.TR) } }
        for (tag in listOf("vertical-metrics", "shift-home", "shift-end", "show-panels", "smart-locate", "map-search", "route-fab")) {
            compose.onAllNodes(hasTestTag(tag)).assertCountEquals(0)
        }
        val card = compose.onNodeWithTag("arrival-address")
        card.assertExists()
        compose.onAllNodes(hasText("Test küçəsi 5") and hasAnyAncestor(hasTestTag("arrival-address"))).assertCountEquals(1)
        compose.onAllNodes(hasText("Blok A · kat 3") and hasAnyAncestor(hasTestTag("arrival-address"))).assertCountEquals(1)
        assertTrue("altta: Rawstats balonu/kartı üstte", card.getBoundsInRoot().top > (844 / 2).dp)
        for (slot in listOf("toast-banner", "reminder-slot", "task-alerts")) compose.onNodeWithTag(slot).assertExists()
    }

    @Test
    fun anaEkranMenusuzKayitNoktasiYok() {
        compose.setContent { WeemTheme(false) { MapTab(MapTabState(DayView(DayState(), null), nowMs = now), Lang.TR) } }
        compose.onAllNodes(hasTestTag("main-menu")).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("record-chip")).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("open-settings")).assertCountEquals(0)
        compose.onNodeWithTag("map-search").assertExists()
        compose.onAllNodes(hasText("Kayıt sürüyor")).assertCountEquals(0) // durum Yollarım → Sürüş kayıtları'nda
    }
}
