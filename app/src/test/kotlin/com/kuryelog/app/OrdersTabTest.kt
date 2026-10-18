package com.kuryelog.app

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.economics.EconomicsObserver
import com.kuryelog.logic.economics.OfferScore
import com.kuryelog.logic.economics.OfferScoreBook
import com.kuryelog.logic.orders.CourierRecords
import com.kuryelog.logic.orders.LogOrdersSource
import com.kuryelog.logic.orders.OfferState
import com.kuryelog.logic.orders.OrdersSource
import com.kuryelog.logic.orders.PickupRecord
import com.kuryelog.logic.orders.RecordJson
import com.kuryelog.logic.orders.TripPhase
import com.kuryelog.logic.orders.TripRecord
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A3.8: Siparişler sekmesi JVM'de gerçekten çizilir (Robolectric + Compose testi; cihaz yok): Bugün kartı, gün sayfası başlığı, sola kaydırma
 * eski güne, "Günün tüm ayrıntıları", satıra dokununca sipariş kartı, ortalamaya dokununca liste; E4 (A4.1) "Teklif puanı" sekmesi ve
 * 1–10 sorusu. Sentetik kayıtlar (uydurma restoran).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class OrdersTabTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val baku = ZoneId.of("Asia/Baku")
    private val min = 60_000L
    private val t0 = ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, baku).toInstant().toEpochMilli()

    private class FixedClock(val now: Long, val zoneId: ZoneId) : Clock {
        override fun wallMs() = now
        override fun monoMs() = now
        override fun zone() = zoneId
    }

    private fun trip(id: String, at: Long, restaurant: String, gross: Double): TripRecord {
        val pkg = "com.wolt.courierapp"
        val obs = CourierObservation(pkg, at - min, ScreenStage.OFFER, true, "accessibility", emptyList(), emptyList(), gross = gross, km = 2.0, minLo = 10, minHi = 14, restaurant = restaurant)
        return TripRecord(id, pkg, OfferState("o-$id", pkg, at - min, obs), at, TripPhase.FINISHED).also { t ->
            t.pickups.add(PickupRecord(restaurant, "Test Cad. 1", arrivedAt = at + 5 * min, pickedAt = at + 9 * min, arrivalSource = "gps_dwell"))
            t.completedAt = at + 20 * min
        }
    }

    private val clock = FixedClock(t0 + 5 * 60 * min, baku)
    private val log by lazy { LogStore(File(tmp.root, "logs"), now = { clock.now }, zone = { baku }) }
    private val book by lazy {
        val env = object : EconomicsObserver.Env {
            override val log = this@OrdersTabTest.log
            override val prefs = KuryelogPrefs.Memory()
            override val clock: Clock = this@OrdersTabTest.clock
        }
        OfferScoreBook(EconomicsObserver(env), log::day, clock::wallMs)
    }

    private fun source(): OrdersSource {
        val db = CourierRecords.Memory()
        db.saveTrip(RecordJson.trip(trip("a", t0, "Test Döner", 3.0)))
        db.saveTrip(RecordJson.trip(trip("b", t0 - 24 * 60 * min, "Test Kafe", 4.5)))
        return OrdersAccess.withOfferScores(LogOrdersSource(log, db, KuryelogPrefs.Memory(), clock), book)
    }

    private fun show() = compose.setContent { WeemTheme(dark = false) { OrdersTab(Lang.TR, { source() }, { book }) } }

    /** Dikey kaydırmayı ana kapsayıcıdan yapar (sayfa kaydırıcısı yataydır; performScrollTo onu bulur). */
    private fun reveal(text: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(text)
        repeat(8) {
            val shown = runCatching { node.assertIsDisplayed() }.isSuccess
            if (shown) return node
            compose.onRoot().performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.2f) }
            compose.waitForIdle()
        }
        return node.also { it.assertIsDisplayed() }
    }

    @Test fun todayCardAndTheNewestDayPageAreShown() {
        show()
        compose.onNodeWithText("Net kazanç").assertIsDisplayed()
        compose.onAllNodesWithText("3.00 ₼").onFirst().assertIsDisplayed()
        reveal("16 Ekim 2026 · Bugün · Cuma")
        reveal("08:59  Test Döner")
        reveal("1 / 2 gün")
    }

    @Test fun swipingLeftGoesToTheOlderDay() {
        show()
        reveal("08:59  Test Döner").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        reveal("15 Ekim 2026 · Perşembe")
        reveal("2 / 2 gün")
        reveal("08:59  Test Kafe")
    }

    @Test fun dayDetailOpensAndGoesBack() {
        show()
        reveal("Günün tüm ayrıntıları").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Zaman çizelgesi").onFirst().assertExists()
        compose.onNodeWithText("Geri").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Net kazanç").assertExists()
    }

    @Test fun tappingARowOpensTheOrderCardAndAnAverageOpensItsList() {
        show()
        reveal("08:59  Test Döner").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Zaman çizelgesi").onFirst().assertExists()
        compose.onNodeWithText("Kapat").performClick()
        compose.waitForIdle()
        reveal("restoranda").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Restoranda · Bugün").assertIsDisplayed()
    }

    @Test fun offerScoreTabRatesAnOrder() {
        // Uygulamanın puanı teklif anında yazılmış (teklif 08:59, bugünün dosyası).
        log.append(OfferScore.FILE, JSONObject().put("schemaVersion", 1).put("kind", "offer_score").put("offer_id", "o-a").put("score", 7).toString(), log.day(t0 - min))
        show()
        compose.onNodeWithText("Teklif puanı").performClick()
        compose.waitForIdle()
        reveal("Ortalama fark: — (iki puanı da olan sipariş yok)")
        reveal("Uygulama 7 · Sen —")
        reveal("08:59 · Wolt · Test Döner").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bu teklife puanın (1–10)").assertIsDisplayed()
        compose.onNodeWithText("8").performClick()
        compose.waitForIdle()
        reveal("Uygulama 7 · Sen 8 (fark -1)")
        reveal("Ortalama fark (uygulama − sen): -1.0 · 1 sipariş")
        val rows = File(log.dayDir(log.day(t0 - min)), OfferScore.RATINGS).readLines().map(::JSONObject)
        assertEquals(listOf(8), rows.map { it.getInt("rating") })
        assertEquals("o-a", rows.single().getString("offer_id"))
        assertEquals(OfferScore.VIA_DAY, rows.single().getString("via"))
        // Puan sekmesinde platform seçicisi ve "Günün tüm ayrıntıları" yok; sipariş kartında "Teklif puanı" satırı.
        compose.onAllNodesWithText("Tüm siparişler").assertCountEquals(0)
        compose.onAllNodesWithText("Günün tüm ayrıntıları").assertCountEquals(0)
        compose.onNodeWithText("Siparişler").performClick()
        compose.waitForIdle()
        reveal("08:59  Test Döner").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Teklif puanı: Uygulama 7 · Sen 8 (fark -1)").assertExists()
    }
}
