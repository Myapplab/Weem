package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.app.map.MapTestFixtures.fix
import com.kuryelog.app.map.MapTestFixtures.km
import com.kuryelog.app.map.MapTestFixtures.plan
import com.kuryelog.app.map.MapTestFixtures.scooterLive
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.nav.RouteProgress
import com.kuryelog.logic.map.ride.BatteryState
import com.kuryelog.logic.map.ui.HudLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `:app` Robolectric `MapMetricsTest` — sol gösterge sütunu (MAP.md 7.11, 10.5, 16.3), 7 test: `remaining_km_e12_test.dart` gösterge metinleri (3: AZ, panel, başlık; ölçüm
 * kuralları 4 test `RouteProgressTest`'te), `v150_features_test.dart` "ana ekran parçaları" 2 (sol göstergeler, batarya kutusu; hız penceresi `SpeedSheetTest`'te),
 * `v170_features_test.dart` batarya 2 (rota yokken canlı batarya gizli; Rawstats canlıyken batarya ve harici). Dart döngüleri (dil) tek test içinde.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-port-xxhdpi")
class MapMetricsTest {
    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(MapMetricsState())
    private var lang by mutableStateOf(Lang.TR)

    private fun show() = compose.setContent { WeemTheme(false) { MapMetrics(state, lang) } }

    private fun withRoute(fixNorth: Double, eastM: Double = 0.0, walkM: Double = 40.0): MapMetricsState {
        val p = plan(walkM)
        return MapMetricsState(plan = p, measure = RouteProgress.measure(p, fix(fixNorth, eastM)))
    }

    private fun remainingText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("metric-remaining")))

    @Test
    fun azDayanacagaQalanVeAyniKm() {
        lang = Lang.AZ
        state = withRoute(500.0)
        show()
        compose.onNodeWithTag("metric-remaining").assertExists()
        remainingText(km(state.measure!!.remainingM)).assertExists()
        remainingText("Dayanacağa qalan").assertExists()
        compose.onAllNodes(hasText("Kalan")).assertCountEquals(0)
    }

    @Test
    fun acilirPanelToplamVeAyriKalanKmGosterirRotaDisindaToplamKalir() {
        val p = plan()
        state = withRoute(500.0)
        show()
        val total = km(p.rideM)
        for (l in Lang.entries) {
            lang = l
            state = withRoute(500.0)
            compose.waitForIdle()
            val remaining = if (l == Lang.TR) "Kalan" else "Qalan"
            compose.onNodeWithTag("metric-time").performClick()
            compose.waitForIdle()
            compose.onAllNodes(hasText("$total · $remaining 0.5 km", substring = true)).assertCountEquals(1)
            state = withRoute(500.0, eastM = 120.0) // rota dışı: ölçüm onRoute=false
            compose.waitForIdle()
            compose.onAllNodes(hasTestTag("metric-remaining")).assertCountEquals(0)
            compose.onAllNodes(hasText(total, substring = true)).assertCountEquals(1)
            compose.onAllNodes(hasText("$remaining 0.5 km", substring = true)).assertCountEquals(0)
        }
    }

    @Test
    fun kalanMesafeBasligiVeYurumeAltYazisi() {
        for (l in Lang.entries) {
            lang = l
            state = withRoute(500.0)
            if (l == Lang.TR) show()
            compose.waitForIdle()
            val title = if (l == Lang.TR) "Durağa kalan" else "Dayanacağa qalan"
            val subtitle = if (l == Lang.TR) "yürüme dahil" else "piyada daxil"
            remainingText(title).assertExists()
            remainingText(subtitle).assertExists()
            remainingText(km(state.measure!!.remainingM)).assertExists()
        }
    }

    @Test
    fun solGostergelerEkranlaOrantiliRotaYokkenSureHizBataryaYok() {
        show()
        val width = compose.onNodeWithTag("vertical-metrics").getBoundsInRoot().let { it.right - it.left }
        assertEquals(HudLayout.metricsWidthDp(390.0), width.value.toDouble(), 0.5)
        for (tag in listOf("metric-time", "speed-popup", "metric-battery", "metric-surface")) compose.onAllNodes(hasTestTag(tag)).assertCountEquals(0)
    }

    @Test
    fun rotaVarkenBataryaKutusuCikarVeCanliPenceresiniAcar() {
        state = withRoute(0.0)
        show()
        // 1.7.0: elle % girme kalktı; canlı değer yokken "—".
        compose.onAllNodes(hasText("—") and hasAnyAncestor(hasTestTag("metric-battery"))).assertCountEquals(1)
        val h = compose.onNodeWithTag("metric-battery").getBoundsInRoot().let { it.bottom - it.top }
        assertTrue(h in 48.dp..80.dp)
        compose.onNodeWithTag("metric-battery").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("scooter-sheet").assertExists()
        compose.onAllNodes(hasText("Scooter bataryası")).assertCountEquals(0)
    }

    @Test
    fun rotaYokkenCanliBataryaDaGizli() {
        state = MapMetricsState(battery = BatteryState(livePercent = 78.0, liveExternalPercent = 64.0), scooter = scooterLive())
        show()
        compose.onAllNodes(hasTestTag("metric-battery")).assertCountEquals(0)
        compose.onAllNodes(hasText("78%")).assertCountEquals(0)
    }

    @Test
    fun rawstatsCanliykenBataryaVeHaricBatarya() {
        state = withRoute(0.0).copy(battery = BatteryState(livePercent = 78.0, liveExternalPercent = 64.0), scooter = scooterLive())
        show()
        compose.onAllNodes(hasText("78%")).assertCountEquals(1)
        compose.onAllNodes(hasText("+64%")).assertCountEquals(1)
        compose.onNodeWithTag("metric-battery").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("scooter-sheet").assertExists()
        compose.onAllNodes(hasText("310 W")).assertCountEquals(1)
    }
}
