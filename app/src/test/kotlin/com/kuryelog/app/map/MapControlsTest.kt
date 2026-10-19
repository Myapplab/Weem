package com.kuryelog.app.map

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.ShiftEngine
import com.kuryelog.logic.map.ui.CameraMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `v170_features_test.dart` ana ekran 2 test (A6.2b, MAP.md 6.12, 16.3): gün kartı üç simgeyle başlar (tek sıra); sipariş değişikliğinde açılır, 20 sn sonra ya da dışarı
 * dokununca döner, kaydırınca açılır · alt yer düğmesi konum düğmesiyle aynı boy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-port-xxhdpi")
class MapControlsTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_792_000_000_000L

    private fun dayWith(order: OrderPhase = OrderPhase.NONE): DayView {
        val e = ShiftEngine().also { it.startDay(now) }
        if (order != OrderPhase.NONE) {
            e.startOrder(now)
            e.stageFromFeed(order, now)
        }
        return DayView(e.s, null)
    }

    @Test
    fun gunKartiUcSimgeylePaslarDegisiklikteAcilirYirmiSnSonraVeDisariDokununcaDoner() {
        var day by mutableStateOf(dayWith())
        var signal by mutableIntStateOf(0)
        compose.setContent {
            WeemTheme(false) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.testTag("outside").fillMaxWidth().height(200.dp).clickable { signal++ })
                    MapShiftActions(day, hasRoute = false, lang = Lang.TR, onHome = {}, onStats = {}, onEnd = {}, collapseSignal = signal)
                }
            }
        }
        compose.onNodeWithTag("shift-collapsed").assertExists()
        // Eve dön · İstatistik · Günü bitir, sol kenarda tek sıra; ışık analizi yok.
        compose.onAllNodes(hasContentDescription("Işık analizi") and hasAnyAncestor(hasTestTag("shift-collapsed"))).assertCountEquals(0)
        fun cy(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().let { (it.top + it.bottom) / 2 }
        fun cx(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().let { (it.left + it.right) / 2 }
        assertEquals(cy("shift-home"), cy("shift-stats"))
        assertEquals(cy("shift-stats"), cy("shift-end"))
        assertTrue(cx("shift-stats") > cx("shift-home") + 30.dp)
        assertTrue(compose.onNodeWithTag("shift-collapsed").getBoundsInRoot().left < 20.dp)
        // Soldan sağa kaydırınca açılır.
        compose.onNodeWithTag("shift-collapsed").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("shift-card").assertExists()
        compose.onAllNodes(hasTestTag("start-order")).assertCountEquals(0)
        // Dışarı dokununca simgelere döner.
        compose.onNodeWithTag("outside").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shift-collapsed").assertExists()
        // Ekran okuması sipariş açınca kart açılır (yalnız bilgi); 20 sn sonra simgelere döner.
        day = dayWith(OrderPhase.TO_RESTAURANT)
        compose.waitForIdle()
        compose.onAllNodes(hasText("Restorana gidiyorsun", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasTestTag("order-next")).assertCountEquals(0)
        compose.mainClock.advanceTimeBy(MapShiftActionsDefaults.COLLAPSE_AFTER_MS - 1_000)
        compose.onAllNodes(hasTestTag("shift-collapsed")).assertCountEquals(0)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag("shift-collapsed").assertExists()
        assertEquals(20_000L, MapShiftActionsDefaults.COLLAPSE_AFTER_MS)
    }

    @Test
    fun altYerDugmesiKonumDugmesiyleAyniBoy() {
        compose.setContent {
            WeemTheme(false) {
                Column {
                    SmartLocateButton(CameraMode.FOLLOW_HEADING, onTap = {})
                    RouteFab(onClick = {})
                }
            }
        }
        val fab = compose.onNodeWithTag("route-fab").getBoundsInRoot()
        val locate = compose.onNodeWithTag("smart-locate").getBoundsInRoot()
        assertEquals(locate.right - locate.left, fab.right - fab.left)
        assertEquals(locate.bottom - locate.top, fab.bottom - fab.top)
    }
}
