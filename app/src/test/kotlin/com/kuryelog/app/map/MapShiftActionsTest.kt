package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.ShiftEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `yolgo_sade_2_test.dart` widget 5 test (A6.2b, MAP.md 6.12, 6.14): alttaki gün/sipariş düğmeleri kalktı; vardiya evden çıkışla, sipariş aşamaları yalnız ekran
 * okumasından ilerler; altta yalnız Eve dön, İstatistik, Günü bitir. `:app` Robolectric (Compose), `Key('…')` → `testTag("…")`. UYARLAMA: "Günü Başlat yok" iddiası düşer —
 * WEEM `ShiftCard`'ta başlat düğmesi var (G1); bu satır yalnız harita eylemlerini sınar. Ayar testi çevrilmez (ayar yok, S1). Döngüler (dil × koyu, aşama) tek test içinde.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w380dp-h844dp-port-xxhdpi")
class MapShiftActionsTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_792_000_000_000L
    private var day by mutableStateOf(DayView(DayState(), null))
    private var lang by mutableStateOf(Lang.TR)
    private var dark by mutableStateOf(false)
    private var route by mutableStateOf(false)

    private fun dayWith(active: Boolean = true, order: OrderPhase = OrderPhase.NONE): DayView {
        val e = ShiftEngine()
        if (active) e.startDay(now)
        if (order != OrderPhase.NONE) {
            e.startOrder(now)
            e.stageFromFeed(order, now)
        }
        return DayView(e.s, null)
    }

    private fun show() = compose.setContent {
        WeemTheme(dark) { MapShiftActions(day, route, lang, onHome = {}, onStats = {}, onEnd = {}) }
    }

    private fun clickablesIn(tag: String) = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(tag)))

    /** Yalnız üç düğme: Eve dön · İstatistik · Günü bitir; ışık analizi, oynat simgesi ve kaldırılmış sipariş metinleri yok. */
    private fun onlyThree(root: String) {
        for (k in listOf("shift-home", "shift-stats", "shift-end")) compose.onNode(hasTestTag(k) and hasAnyAncestor(hasTestTag(root))).assertExists()
        clickablesIn(root).assertCountEquals(3)
        for (t in gone) compose.onAllNodes(hasText(t, substring = false) and hasAnyAncestor(hasTestTag(root))).assertCountEquals(0)
        for (k in listOf("order-next", "start-order", "add-second-order")) compose.onAllNodes(hasTestTag(k)).assertCountEquals(0)
    }

    private val gone = listOf(
        "Siparişi Başlat", "Sifarişə başla", "Müşteriye git", "Müştəriyə get", "Restorana vardım", "Restorana çatdım", "Restorandan aldım", "Restorandan götürdüm",
        "Müşteriye vardım", "Müştəriyə çatdım", "Teslim ettim", "Təhvil verdim", "İkinci siparişi ekle", "İkinci sifarişi əlavə et", "Vardım", "Çatdım", "Aldım", "Götürdüm",
        "Teslim", "Təhvil", "Sipariş", "Sifariş",
    )

    private fun centerY(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().let { (it.top + it.bottom) / 2 }
    private fun centerX(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().let { (it.left + it.right) / 2 }

    @Test
    fun katliGunKartiYalnizUcDugmeIsikAnaliziYok() {
        day = dayWith()
        show()
        for (l in Lang.entries) for (d in listOf(false, true)) {
            lang = l; dark = d
            compose.waitForIdle()
            compose.onNodeWithTag("shift-collapsed").assertExists()
            onlyThree("shift-collapsed")
            val tips = if (l == Lang.TR) listOf("Eve dön", "İstatistik", "Günü bitir") else listOf("Evə qayıt", "Statistika", "Günü tamamla")
            for (tip in tips) compose.onNode(hasContentDescription(tip) and hasAnyAncestor(hasTestTag("shift-collapsed"))).assertExists()
            // Tek sıra, sol kenarda.
            assertEquals(centerY("shift-home"), centerY("shift-stats"))
            assertEquals(centerY("shift-stats"), centerY("shift-end"))
            assertTrue(centerX("shift-home") < centerX("shift-stats"))
            assertTrue(centerX("shift-stats") < centerX("shift-end"))
            assertTrue(compose.onNodeWithTag("shift-collapsed").getBoundsInRoot().left < 20.dp)
        }
    }

    @Test
    fun gunKapaliykenEveDonVeGunuBitirPasifIstatistikAcik() {
        day = dayWith(active = false)
        show()
        onlyThree("shift-collapsed")
        compose.onNodeWithTag("shift-home").assertIsNotEnabled()
        compose.onNodeWithTag("shift-end").assertIsNotEnabled()
        compose.onNodeWithTag("shift-stats").assertIsEnabled()
        compose.onNodeWithTag("shift-collapsed").performTouchInput { swipeRight() }
        compose.waitForIdle()
        onlyThree("shift-card")
        compose.onAllNodes(hasText("otomatik", substring = true) and hasAnyAncestor(hasTestTag("shift-card"))).assertCountEquals(1)
    }

    @Test
    fun acikKartYalnizBilgiGosterir() {
        val expected = mapOf(
            OrderPhase.TO_RESTAURANT to "Restorana gidiyorsun", OrderPhase.AT_RESTAURANT to "Restorana vardın",
            OrderPhase.TO_CUSTOMER to "Müşteriye gidiyorsun", OrderPhase.AT_CUSTOMER to "Müşteriye vardın",
        )
        day = dayWith()
        show()
        for ((phase, text) in expected) {
            day = dayWith(order = phase)
            compose.waitForIdle()
            compose.onNodeWithTag("shift-collapsed").performTouchInput { swipeRight() }
            compose.waitForIdle()
            compose.onNodeWithTag("shift-card").assertExists()
            onlyThree("shift-card")
            compose.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("shift-card"))).assertCountEquals(1)
        }
    }

    @Test
    fun rotaVarkenTekSatirBilgiVeUcDugmeMenuYok() {
        day = dayWith(order = OrderPhase.TO_CUSTOMER)
        route = true
        show()
        compose.onNodeWithTag("shift-collapsed").performTouchInput { swipeRight() }
        compose.waitForIdle()
        onlyThree("shift-card")
        compose.onAllNodes(hasText("#1", substring = true) and hasAnyAncestor(hasTestTag("shift-card"))).assertCountEquals(1)
        compose.onAllNodes(hasTestTag("shift-menu")).assertCountEquals(0)
    }

    @Test
    fun yeniSiparisteKartAcilirYirmiSaniyeSonraKatlanir() {
        day = dayWith()
        show()
        day = dayWith(order = OrderPhase.TO_RESTAURANT) // ekran okuması yeni sipariş açtı
        compose.waitForIdle()
        compose.onNodeWithTag("shift-card").assertExists()
        compose.mainClock.advanceTimeBy(MapShiftActionsDefaults.COLLAPSE_AFTER_MS + 1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("shift-collapsed").assertExists()
    }
}
