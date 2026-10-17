package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * D15/D23 (13 Ekim planı): Wolt 2.202.6 kazanç listesi. Satırlar 11–12 Ekim saha karelerinin OCR metni (restoran adları
 * gerçek, kişisel veri yok): tarih başlığı "11 0CT 2026", tutar bazen "Task offer"dan önce, "Extra waiting time" ve "Tip"
 * üstündeki teslimata ait (tutar etiketten önce ya da sonra), aynı teslim OCR ad/saat farkıyla bir kez yazılır.
 */
class PlatformListD15Test {
    private val wolt = "com.wolt.courierapp"
    private val tz = TimeZone.getTimeZone("Asia/Baku")
    private fun at(day: String, clock: String) = PlatformHistory.toMs(day, clock, tz)

    /** 11 Oct 19:30:53: Borani's extra waiting amount before its label, Bonbon's after it. */
    private val oct11 = listOf("11 0CT 2026", "AZN 18.24", "Deliveries", "ZO0 28", "18:51", "Task offer (2.61 km)", "AZN 3.79",
        "Hatay Usulü Dönər Fəvvarələr Meydanı", "18:38", "Task offer (1.03 km)", "AZN 2.31", "Borani Sahil", "18:01", "Task offer (2.01 km)", "AZN 2.61",
        "AZN 0.30", "Extra waiting time", "Bravo Supermarket Winter Park", "17:45", "Task offer (1.89 km)", "AZN 2.27", "Bonbon PATISSERIE", "17:09",
        "Task offer (4.2 km)", "AZN 4.83", "Extra waiting time", "AZN 0.50", "King Doner Shamahinka", "16:30", "Task offer (1.11 km)", "AZN 1.63")
    /** 11 Oct 19:51:03: OCR variants "ZO0 28" kept, "Dönar Favvarələr". */
    private val oct11Later = listOf("T", "11 0CT 2026", "AZN 22.05", "Deliveries", "SMOKING SHOP 6th Parallel", "19:18", "Task offer (1.42 km)", "AZN 1.97",
        "BIR-IKI Döner Caspian", "19:07", "Task offer (0.82 km)", "AZN 1.84", "ZOO 28", "18:51", "Task offer (2.61 km)", "AZN 3.79",
        "Hatay Usulü Dönar Favvarələr Meydanı", "18:38", "Task offer (1.03 km)", "AZN 2.31")
    /** 12 Oct 18:42:03: Firuze's fee read before "Task offer". */
    private val oct12 = listOf("T", "12 0CT 2026", "AZN 32.66", "Deliveries", "Firuze Restaurant", "18:04", "AZN 2.77", "Task offer (2.5 km)",
        "Shaurma 1 - Bayıl", "17:40", "Task offer (2.64 km)", "AZN 2.37", "McDonald's & McCafe Grand Hayat", "14:54", "Task offer (0.55 km)", "AZN 1.32",
        "Beauty Is You AF Mall", "14:26", "Task offer (3.95 km)", "AZN 3.85")
    /** 12 Oct 19:54:52: the same McDonald's row read as 14:57, with its tip; Beauty "ls" for "Is". */
    private val oct12Later = listOf("T", "12 0CT 2026", "AZN 39.91", "Deliveries", "Arishta", "18:57", "Task offer (5.45 km)", "AZN 4.49",
        "McDonald's & McCafe Grand Hayat", "14:57", "Task offer (0.55 km)", "AZN 1.32", "Tip", "AZN 1.15", "Beauty ls You AF Mall", "14:26",
        "Task offer (3.95 km)", "AZN 3.85")

    private class Log { val keys = HashMap<String, MutableSet<String>>(); val taken = HashMap<String, MutableSet<String>>(); val written = HashMap<String, MutableList<JSONObject>>() }
    private fun write(texts: List<String>, now: Long, log: Log, trips: List<TripRecord> = emptyList()) =
        PlatformHistory.records(PlatformHistory.parse(wolt, texts, now, tz)!!, now, "ocr", wolt, tz, { log.keys.getOrPut(it) { HashSet() } },
            { log.taken.getOrPut(it) { HashSet() } }, { d -> trips.filter { PlatformHistory.dayOf(it.acceptedAt, tz) == d } }, { log.written.getOrPut(it) { ArrayList() } })

    @Test fun woltDayHeaderFeeBeforeTaskExtraAndTip() {
        assertEquals("2026-10-11", PlatformHistory.dateHeader("11 0CT 2026")); assertEquals("2026-10-12", PlatformHistory.dateHeader("12 Oct 2026"))
        assertEquals("2026-10-08", PlatformHistory.dateHeader("8 October 2026")); assertNull(PlatformHistory.dateHeader("6 110ct 2026"))
        val rows = PlatformHistory.parse(wolt, oct11, at("2026-10-11", "19:31"), tz)!!
        assertEquals(listOf("ZO0 28", "Hatay Usulü Dönər Fəvvarələr Meydanı", "Borani Sahil", "Bravo Supermarket Winter Park", "Bonbon PATISSERIE", "King Doner Shamahinka"), rows.map { it.restaurant })
        assertTrue(rows.all { it.day == "2026-10-11" && it.daySource == "screen_header" })
        assertEquals(listOf(null, null, 0.30, null, 0.50, null), rows.map { it.extraWait })
        assertEquals(listOf(3.79, 2.31, 2.61, 2.27, 4.83, 1.63), rows.map { it.amount })
        val firuze = PlatformHistory.parse(wolt, oct12, at("2026-10-12", "18:43"), tz)!!.first()
        assertEquals("Firuze Restaurant", firuze.restaurant); assertEquals("18:04", firuze.clock); assertEquals(2.77, firuze.amount, 1e-9); assertEquals(2.5, firuze.distanceKm!!, 1e-9)
        val mc = PlatformHistory.parse(wolt, oct12Later, at("2026-10-12", "19:55"), tz)!!
        assertEquals(listOf("Arishta", "McDonald's & McCafe Grand Hayat", "Beauty ls You AF Mall"), mc.map { it.restaurant })
        assertEquals(1.15, mc[1].tip!!, 1e-9); assertNull(mc[1].extraWait)
    }

    @Test fun oneDeliveryReadTwiceIsWrittenOnce() {
        val log = Log()
        assertEquals(6, write(oct11, at("2026-10-11", "19:31"), log).size)
        // "ZOO 28" for "ZO0 28" and "Dönar Favvarələr": the same rows; SMOKING and BIR-IKI are new.
        assertEquals(listOf("SMOKING SHOP 6th Parallel", "BIR-IKI Döner Caspian"), write(oct11Later, at("2026-10-11", "19:51"), log).map { it.second.getString("restaurant") })
        val log12 = Log()
        assertEquals(4, write(oct12, at("2026-10-12", "18:43"), log12).size)
        // 14:57 for 14:54 (D15: clock misread) and "Beauty ls You": nothing new but Arishta.
        val later = write(oct12Later, at("2026-10-12", "19:55"), log12)
        assertEquals(listOf("Arishta"), later.map { it.second.getString("restaurant") })
        assertEquals(5, log12.written.getValue("2026-10-12").size)
    }

    @Test fun twoLikeRowsOnOneFrameAreTwoDeliveries() {
        // Two orders from one restaurant, same fee and km, 3 minutes apart, shown together: two deliveries.
        val both = listOf("12 0CT 2026", "Deliveries", "McDonald's & McCafe Grand Hayat", "14:57", "Task offer (0.55 km)", "AZN 1.32",
            "McDonald's & McCafe Grand Hayat", "14:54", "Task offer (0.55 km)", "AZN 1.32")
        val log = Log()
        assertEquals(2, write(both, at("2026-10-12", "16:00"), log).size)
        assertEquals(0, write(both, at("2026-10-12", "16:01"), log).size)
        // After one of them was written alone, the frame showing both still adds the second one.
        val log2 = Log()
        assertEquals(1, write(both.take(6), at("2026-10-12", "16:00"), log2).size)
        assertEquals(1, write(both, at("2026-10-12", "16:01"), log2).size)
        assertEquals(0, write(both.take(2) + both.drop(6), at("2026-10-12", "16:02"), log2).size)
    }

    @Test fun sameDeliveryRule() {
        fun row(r: String?, clock: String, amount: Double, km: Double?) = PlatformHistory.Row("wolt", r, clock, "2026-10-12", "screen_header", amount, "AZN $amount", km, null, null, null)
        val a = row("McDonald's & McCafe Grand Hayat", "14:54", 1.32, 0.55)
        assertTrue(PlatformHistory.sameDelivery(a, row("McDonald's & McCafe Grand Hayat", "14:57", 1.32, 0.55)))
        assertTrue(PlatformHistory.sameDelivery(a, row("McDonald's & McCafe Grand Hayat", "14:59", 1.32, 0.55)))
        assertFalse(PlatformHistory.sameDelivery(a, row("McDonald's & McCafe Grand Hayat", "15:00", 1.32, 0.55)))
        assertFalse(PlatformHistory.sameDelivery(a, row("McDonald's & McCafe Grand Hayat", "14:54", 1.33, 0.55)))
        assertFalse(PlatformHistory.sameDelivery(a, row("McDonald's & McCafe Grand Hayat", "14:54", 1.32, 0.56)))
        assertFalse(PlatformHistory.sameDelivery(a, row("Kenny's Baku", "14:54", 1.32, 0.55)))
        assertTrue(PlatformHistory.sameDelivery(row("ZO0 28", "18:51", 3.79, 2.61), row("ZOO 28", "18:51", 3.79, 2.61)))
        // Bolt rows (no km) are kept apart by their key only.
        val b = PlatformHistory.Row("bolt", "Test", "13:52", "2026-10-08", "row_date_header_year", 4.72, "4,72 ₼", null, "#G55BB", null, null)
        assertFalse(PlatformHistory.sameDelivery(b, b.copy(clock = "13:53")))
    }

    @Test fun extraWaitAndTipGoToTheRowsOrder() {
        fun trip(id: String, accepted: String, restaurant: String, gross: Double): TripRecord {
            val o = CourierObservation(wolt, at("2026-10-11", accepted), ScreenStage.OFFER, true, "ocr", emptyList(), emptyList(), gross = gross, restaurant = restaurant)
            return TripRecord(id, wolt, OfferState("o-$id", wolt, at("2026-10-11", accepted) - 4000, o), at("2026-10-11", accepted) + 30_000,
                TripPhase.FINISHED, mutableListOf(PickupRecord(restaurant, null)), completedAt = at("2026-10-11", accepted) + 1_200_000)
        }
        val bonbon = trip("bonbon", "17:09", "Bonbon PATISSERIE", 4.83); val borani = trip("borani", "18:01", "Borani Sahil", 2.61)
        val log = Log()
        val out = write(oct11, at("2026-10-11", "19:31"), log, listOf(bonbon, borani)).map { it.second }
        assertEquals(0.50, out.single { it.getString("restaurant") == "Bonbon PATISSERIE" }.getDouble("extra_wait_azn"), 1e-9)
        val rows = PlatformHistory.parse(wolt, oct11, at("2026-10-11", "19:31"), tz)!!
        assertEquals("bonbon", PlatformHistory.owner(rows[4], "2026-10-11", log.written.getValue("2026-10-11")))
        assertEquals("borani", PlatformHistory.owner(rows[2], "2026-10-11", log.written.getValue("2026-10-11")))
        // The extra fee is part of the order's earnings and net (D15: "ek bekleme ücreti olarak eklenir").
        bonbon.extraWaitAzn = 0.50; bonbon.commissionPct = 10.0
        assertEquals(5.33, bonbon.earnings!!, 1e-9); assertEquals(5.33 * 0.9, bonbon.net!!, 1e-9)
        assertEquals(4.83 + 0.50 + 2.61, OrderHistory.Day("2026-10-11", listOf(bonbon, borani)).gross, 1e-9)
        val back = RecordJson.trip(RecordJson.trip(bonbon.also { it.platformTipAzn = 1.15 }))
        assertEquals(0.50, back.extraWaitAzn, 1e-9); assertEquals(1.15, back.platformTipAzn!!, 1e-9)
        assertFalse(RecordJson.trip(borani).has("extra_wait_azn"))
        // A Wolt list clock is the accept time: the last frame of the offer card, not the first task frame.
        assertEquals(at("2026-10-11", "17:09") - 4000, OrderHistory.listTime(bonbon, "Wolt"))
    }
}
