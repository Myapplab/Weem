package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * E3 (Faz 4): the order's boost at offer time (K27/D11) and the temperature when it was accepted (the offer's K28 weather,
 * seconds before the accept) at the top of its card and in the order list. Not observed = "—" (never 0 or an old value).
 * Synthetic records (uydurma restoran adları).
 */
class BoostTempE3Test {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.courier"
    private val min = 60_000L
    private val tr = { a: String, _: String -> a }
    private val az = { _: String, b: String -> b }

    private fun obs(pkg: String) = CourierObservation(pkg, 10 * min, ScreenStage.OFFER, true, "ocr", emptyList(), emptyList(), gross = 2.61)
    private fun weather(t: Double?) = OfferWeather(t, null, null, null, null, null, 1, 9 * min, OfferWeather.SOURCE_YOLGO)
    private fun trip(pkg: String = wolt, offer: OfferState? = OfferState("o", pkg, 10 * min, obs(pkg))) =
        TripRecord("t", pkg, offer, 11 * min).also { t ->
            t.completedAt = 40 * min; t.phase = TripPhase.FINISHED
            t.pickups.add(PickupRecord("Test Döner", null, arrivedAt = 20 * min, pickedAt = 24 * min, arrivalSource = "gps_dwell"))
        }

    @Test fun woltPlanBoostAndTemperature() {
        val t = trip().also { it.offer!!.apply { boostValue = 30.0; boostUnit = BoostLog.UNIT_PCT; boostSource = BoostLog.SOURCE_PLAN; boostSeenAt = 9 * min; weather = weather(23.6) } }
        assertEquals("+%30", OrderHistory.boostText(t, tr))
        assertEquals("24°", OrderHistory.temperatureText(t))
        assertEquals("Boost +%30 (Wolt planı) · Sıcaklık 24° (kabul anı)", OrderHistory.boostTempLine(t, tr))
        assertEquals("Boost +%30 (Wolt planı) · Temperatur 24° (qəbul anı)", OrderHistory.boostTempLine(t, az))
        assertEquals("Restoranda 4 dk · Boost +%30 · 24°", OrderHistory.listLine(t, tr))
    }

    @Test fun boltMultiplierFromTheOfferCard() {
        val t = trip(bolt).also { it.offer!!.apply { boostValue = 1.5; boostUnit = BoostLog.UNIT_X; boostSource = BoostLog.SOURCE_OFFER; weather = weather(-2.4) } }
        assertEquals("1.5x", OrderHistory.boostText(t, tr))
        assertEquals("Boost 1.5x (teklif ekranı) · Sıcaklık -2° (kabul anı)", OrderHistory.boostTempLine(t, tr))
    }

    @Test fun seenWithoutBoostIsNotTheSameAsNotSeen() {
        val none = trip().also { it.offer!!.apply { boostValue = null; boostSource = BoostLog.SOURCE_HOME; boostSeenAt = 9 * min } }
        assertEquals("yok", OrderHistory.boostText(none, tr))
        assertEquals("Restoranda 4 dk · Boost yok · sıcaklık —", OrderHistory.listLine(none, tr))
        val unseen = trip().also { it.offer!!.boostSource = BoostLog.NOT_OBSERVED }
        assertEquals("—", OrderHistory.boostText(unseen, tr))
        assertEquals("Boost — (görülmedi) · Sıcaklık — (hava bilgisi gelmedi)", OrderHistory.boostTempLine(unseen, tr))
        assertEquals("Boost — (görünmədi) · Temperatur — (hava məlumatı gəlmədi)", OrderHistory.boostTempLine(unseen, az))
        // Older records (before K27) have no source at all.
        assertEquals("—", OrderHistory.boostText(trip(), tr))
    }

    @Test fun orderWithoutItsOfferCard() {
        val t = trip(offer = null)
        assertEquals("—", OrderHistory.boostText(t, tr))
        assertNull(OrderHistory.temperatureText(t))
        assertEquals("Boost — (teklif kartı görülmedi) · Sıcaklık — (teklif kartı görülmedi)", OrderHistory.boostTempLine(t, tr))
        assertEquals("Restoranda 4 dk · Boost — · sıcaklık —", OrderHistory.listLine(t, tr))
    }

    // ------------------------------------------------------------------ wiring (source checks; the screens are telefonda denenmedi)
    private fun main(): File = listOf("src/main", "app/src/main", "kuryelog/app/src/main").map(::File).firstOrNull { it.isDirectory }
        ?: error("KuryeLog kaynak klasörü bulunamadı: ${File(".").absolutePath}")

}
