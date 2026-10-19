package com.kuryelog.app.map

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.app.map.MapTestFixtures.report4
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.ShiftEngine
import com.kuryelog.logic.map.ui.HudLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `hud_layout_test.dart` 1. test (1; 1. tanım 320/390 px × açık/koyu: 4 çalışma; A6.2b, MAP.md 6.12, 6.14): harita kontrolleri taşmaz; gün kartı simgelerle başlar, kaydırınca açılır,
 * "Restorana vardın", `add-second-order`/`order-next` yok, üç düğme ≥48 dp; hava yuvarlağı kapalıyken yalnız "23°" ve kompakt boy, dokununca "Şimdi" + dört saat + yenile; dışarı dokunuş
 * (hava) kartı katlar; aşama ilerleyince "Müşteriye gidiyorsun". Yazı tipi: Inter yerine sistem; altın görüntüler (`HUD_CAPTURE`) çevrilmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class MapTabLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val now = MapTestFixtures.AT

    private fun day(phase: OrderPhase): DayView {
        val e = ShiftEngine().also { it.startDay(now); it.startOrder(now); it.stageFromFeed(phase, now) }
        return DayView(e.s, null)
    }

    @Test
    fun haritaKontrolleriVeAsamaGecisiTasmaz() {
        var width by mutableStateOf(320)
        var dark by mutableStateOf(false)
        var state by mutableStateOf(MapTabState(day = day(OrderPhase.AT_RESTAURANT), weather = WeatherView(report = report4()), nowMs = now))
        compose.setContent {
            // Dart'ta 4 ayrı çalışma: her (genişlik × tema) çifti yeni bir MapTab kurar (gün kartı katlı başlar).
            WeemTheme(dark) { key(width, dark) { Box(Modifier.width(width.dp).height(844.dp)) { MapTab(state, Lang.TR) } } }
        }
        for (w in listOf(320, 390)) for (d in listOf(false, true)) {
            width = w; dark = d
            state = state.copy(day = day(OrderPhase.AT_RESTAURANT))
            compose.waitForIdle()
            // 1.7.0: gün kartı simgelerle başlar; soldan sağa kaydırınca açılır.
            compose.onNodeWithTag("shift-collapsed").assertExists()
            compose.onNodeWithTag("shift-collapsed").performTouchInput { swipeRight() }
            compose.waitForIdle()
            compose.onAllNodes(hasText("Restorana vardın", substring = true)).assertCountEquals(1)
            compose.onAllNodes(hasTestTag("add-second-order")).assertCountEquals(0)
            compose.onAllNodes(hasTestTag("order-next")).assertCountEquals(0)
            for (k in listOf("shift-home", "shift-stats", "shift-end")) {
                val b = compose.onNodeWithTag(k).getBoundsInRoot()
                assertTrue("$k $w $d", b.bottom - b.top >= 48.dp)
            }
            // Kapalıyken yalnız şimdiki saat (yuvarlak); dokununca dört saat.
            compose.onAllNodes(hasTestTag("weather-toggle")).assertCountEquals(0)
            compose.onAllNodes(hasText("23°")).assertCountEquals(1)
            val chip = compose.onNodeWithTag("live-weather").getBoundsInRoot()
            assertEquals(HudLayout.WEATHER_COMPACT_DP.dp, chip.right - chip.left)
            compose.onNodeWithTag("weather-compact").performClick()
            compose.waitForIdle()
            compose.onAllNodes(hasText("Şimdi")).assertCountEquals(1)
            compose.onNodeWithTag("weather-toggle").performClick()
            compose.waitForIdle()
            compose.onAllNodes(hasText("Şimdi")).assertCountEquals(1)
            compose.onAllNodes(hasText("21:00", substring = true)).assertCountEquals(2)
            compose.onNodeWithTag("weather-refresh").assertExists()
            // Hava'ya dokunmak gün kartının dışı: kart simgelere döndü; tekrar kaydırılır.
            compose.onNodeWithTag("shift-collapsed").assertExists()
            compose.onNodeWithTag("shift-collapsed").performTouchInput { swipeRight() }
            compose.waitForIdle()
            state = state.copy(day = day(OrderPhase.TO_CUSTOMER)) // ekran okuması "müşteriye" dedi
            compose.waitForIdle()
            compose.onAllNodes(hasText("Müşteriye gidiyorsun", substring = true)).assertCountEquals(1)
            compose.onAllNodes(hasTestTag("order-next")).assertCountEquals(0)
        }
    }
}
