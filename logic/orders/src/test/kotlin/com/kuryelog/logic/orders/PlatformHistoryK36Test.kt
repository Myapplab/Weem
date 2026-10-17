package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** K36: Wolt Finances / Bolt All deliveries rows as verified history. Screen labels and row formats are the ones quoted in
 *  docs/KURYELOG_ISTEKLER.md (K36, 8 Ekim); restaurant names, codes and the balance are synthetic. */
class PlatformHistoryK36Test {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.deliverycourier"
    private val tz = TimeZone.getTimeZone("Asia/Baku")
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = tz }
    private fun at(s: String) = fmt.parse(s)!!.time

    private val woltList = listOf("Gross earnings", "Deliveries", "AZN 15.30",
        "Test Burger Nərimanov", "12:40", "Task offer (1.68 km)", "AZN 2.91",
        "Pizza Testo", "13:22", "Task offer (2.05 km)", "AZN 3.96")
    private val boltList = listOf("All deliveries", "Balance", "25,40 ₼", "Cash", "-7,50 ₼",
        "8 October 2026", "10,90 ₼", "Deliveries: 3",
        "Test Dönər", "10/08, 13:52, #G55BB", "4,72 ₼",
        "Çay Evi Test", "10/08, 15:10, #K12AB", "3,46 ₼",
        "Test Plov", "10/08, 16:45, #Z90QQ", "2,72 ₼")

    private fun trip(id: String, pkg: String, accepted: String, restaurant: String?, done: String? = null, code: String? = null,
                     phase: TripPhase = if (done != null) TripPhase.FINISHED else TripPhase.TO_RESTAURANT, gross: Double? = null): TripRecord {
        val obs = CourierObservation(pkg, at(accepted), ScreenStage.OFFER, true, "test", emptyList(), emptyList(), gross = gross, restaurant = restaurant)
        val t = TripRecord(id, pkg, OfferState("o-$id", pkg, at(accepted) - 5000, obs), at(accepted), phase, mutableListOf(PickupRecord(restaurant, null)),
            completedAt = done?.let(::at))
        if (code != null) t.customers[code] = CustomerRecord(code)
        return t
    }

    private class Sink { val keys = HashMap<String, MutableSet<String>>(); val taken = HashMap<String, MutableSet<String>>(); val written = HashMap<String, MutableList<JSONObject>>() }
    /** Like OrderTracker.platformHistory: the rows already written per day go in too (D15). */
    private fun records(pkg: String, texts: List<String>, now: Long, trips: List<TripRecord>, sink: Sink = Sink()) =
        PlatformHistory.records(PlatformHistory.parse(pkg, texts, now, tz)!!, now, "accessibility", pkg, tz,
            { sink.keys.getOrPut(it) { HashSet() } }, { sink.taken.getOrPut(it) { HashSet() } }, { d -> trips.filter { PlatformHistory.dayOf(it.acceptedAt, tz) == d } },
            { sink.written.getOrPut(it) { ArrayList() } })

    @Test fun woltRowsAreParsedWithoutTheHeaderTotal() {
        val rows = PlatformHistory.parse(wolt, woltList, at("2026-10-08 20:00"), tz)!!
        assertEquals(2, rows.size)
        assertEquals("Test Burger Nərimanov", rows[0].restaurant); assertEquals("12:40", rows[0].clock)
        assertEquals(2.91, rows[0].amount, 1e-9); assertEquals(1.68, rows[0].distanceKm!!, 1e-9); assertEquals("AZN 2.91", rows[0].amountText)
        assertEquals("Pizza Testo", rows[1].restaurant); assertEquals(3.96, rows[1].amount, 1e-9)
        assertNull(rows[0].day); assertEquals("not_on_screen", rows[0].daySource)
    }

    @Test fun boltRowsKeepDecimalCommaDayHeaderAndCode() {
        val rows = PlatformHistory.parse(bolt, boltList, at("2026-10-09 10:00"), tz)!!
        assertEquals(3, rows.size)
        assertEquals("Test Dönər", rows[0].restaurant); assertEquals("13:52", rows[0].clock); assertEquals("#G55BB", rows[0].orderCode)
        assertEquals(4.72, rows[0].amount, 1e-9); assertEquals("4,72 ₼", rows[0].amountText)
        assertEquals("2026-10-08", rows[0].day); assertEquals("row_date_header_year", rows[0].daySource)
        assertEquals("10,90 ₼", rows[0].dayTotalText); assertEquals(3, rows[0].dayDeliveries)
        assertEquals(10.90, rows.sumOf { it.amount }, 1e-9)
        assertEquals(listOf("Test Dönər", "Çay Evi Test", "Test Plov"), rows.map { it.restaurant })
    }

    @Test fun otherScreensAreNotHistory() {
        assertNull(PlatformHistory.parse(wolt, listOf("New order", "AZN 2.91", "1.68 km", "Accept"), 0L, tz))
        assertNull(PlatformHistory.parse(bolt, listOf("Test Dönər", "4,72 ₼", "Accept"), 0L, tz))
        assertNull(PlatformHistory.parse("com.example", woltList, 0L, tz))
    }

    @Test fun matchesOrdersAndKeepsMissedRows() {
        val now = at("2026-10-08 20:00")
        // OCR variant of the name, accepted one minute after the list clock; the second Wolt order was never seen by KuryePlus.
        val w1 = trip("w1", wolt, "2026-10-08 12:41", "Test Burqer Nərimanov")
        val out = records(wolt, woltList, now, listOf(w1, trip("w9", wolt, "2026-10-08 13:30", "Pizza Testo")))
        assertEquals(2, out.size)
        val (day, a) = out[0]
        assertEquals("2026-10-08", day)
        assertEquals("w1", a.getJSONObject("match").getString("trip_id")); assertEquals("restaurant_time", a.getJSONObject("match").getString("by"))
        // D23: the Wolt clock is compared with the accept tap (the offer card's last frame, 5 s before the first task frame).
        assertEquals(55, a.getJSONObject("match").getLong("dt_s"))
        assertEquals(false, a.get("missed_by_kuryeplus"))
        val b = out[1].second
        assertTrue(b.isNull("match")); assertEquals(true, b.get("missed_by_kuryeplus")) // 13:30 is 8 min away from 13:22
        assertEquals("capture_day", a.getString("match_day"))
        for (j in listOf(a, b)) {
            assertEquals(1, j.getInt("schemaVersion")); assertEquals("platform_history", j.getString("source")); assertTrue(j.getBoolean("verified"))
            assertEquals("gross_earnings_list", j.getString("amount_basis"))
        }
    }

    @Test fun boltByCodeAndConfirmsInferredDelivery() {
        val now = at("2026-10-09 10:00")
        val b1 = trip("b1", bolt, "2026-10-08 13:20", "Test Doner", done = "2026-10-08 13:53", code = "#G55BB", gross = 2.41)
        // K34: closed as delivered_inferred when the next order reached its customer; Bolt lists it at 15:10.
        val b2 = trip("b2", bolt, "2026-10-08 14:40", "Cay Evi Test", phase = TripPhase.END_UNCONFIRMED)
        b2.deliveredInferredAt = at("2026-10-08 15:30"); b2.deliveredInferredBy = "b3"; b2.deliveredInferredReason = "next_order_at_customer"
        b2.customers["#K12AB"] = CustomerRecord("#K12AB")
        val out = records(bolt, boltList, now, listOf(b1, b2)).map { it.second }
        assertEquals("order_code", out[0].getJSONObject("match").getString("by"))
        assertEquals("delivered", out[0].getJSONObject("match").getString("trip_state"))
        assertEquals("unknown", out[0].getString("amount_basis"))
        assertEquals("b2", out[1].getJSONObject("match").getString("trip_id"))
        assertTrue(out[1].getBoolean("confirms_inferred_delivery"))
        assertEquals("delivered_inferred", out[1].getJSONObject("match").getString("trip_state"))
        assertEquals(true, out[2].get("missed_by_kuryeplus"))
        // The raw order is not rewritten: still the K34 inferred state.
        assertEquals(TripPhase.END_UNCONFIRMED, b2.phase); assertNull(b2.completedAt); assertTrue(b2.deliveredInferred)
        // Neither the balance, the cash line nor the order code is written.
        val text = out.joinToString("\n")
        for (bad in listOf("25,40", "-7,50", "Balance", "Cash", "#G55BB", "G55BB")) assertFalse(bad, text.contains(bad))
        assertTrue(out.all { it.getBoolean("has_order_code") })
    }

    /** Faz 2 hata taraması (14 Ekim): D15's same-delivery dedupe compares km; Bolt rows have none and were never written. */
    @Test fun boltRowsAreWrittenWithTheD15Dedupe() {
        val now = at("2026-10-09 10:00")
        val rows = PlatformHistory.parse(bolt, boltList, now, tz)!!
        assertTrue(rows.all { it.distanceKm == null })
        val written = HashMap<String, MutableList<JSONObject>>()
        val out = PlatformHistory.records(rows, now, "accessibility", bolt, tz, { HashSet() }, { HashSet() }, { emptyList() }, { written.getOrPut(it) { ArrayList() } })
        assertEquals(3, out.size); assertEquals(3, written.getValue("2026-10-08").size)
        assertEquals(listOf("13:52", "15:10", "16:45"), out.map { it.second.getString("clock") })
    }

    @Test fun eachRowIsWrittenOncePerVisitAndAcrossVisits() {
        val now = at("2026-10-09 10:00")
        val sink = Sink()
        assertEquals(3, records(bolt, boltList, now, emptyList(), sink).size)
        // Same frame again, a scrolled frame (last row and its header gone), a later visit: nothing new.
        assertEquals(0, records(bolt, boltList, now + 1000, emptyList(), sink).size)
        assertEquals(0, records(bolt, boltList.drop(8), now + 2000, emptyList(), sink).size)
        val lines = records(bolt, boltList, now, emptyList()).map { it.second.toString() }
        val (keys, _) = PlatformHistory.loaded(lines.asSequence() + sequenceOf("{\"schemaVersion\":9,\"key\":\"x\"}", "not json"))
        assertEquals(3, keys.size)
        assertEquals(sink.keys["2026-10-08"], keys)
    }

    @Test fun aMatchedTripIsNotMatchedTwice() {
        val now = at("2026-10-08 20:00")
        val sink = Sink()
        val w1 = trip("w1", wolt, "2026-10-08 12:40", "Test Burger Nərimanov")
        records(wolt, woltList.take(7), now, listOf(w1), sink)
        val again = records(wolt, listOf("Test Burger Nərimanov", "12:41", "Task offer (1.70 km)", "AZN 2.95"), now, listOf(w1), sink)
        assertEquals(true, again.single().second.get("missed_by_kuryeplus"))
    }

    @Test fun aWoltClockLaterThanTheCaptureIsUnknownNotMissed() {
        val out = records(wolt, woltList, at("2026-10-08 13:00"), emptyList()).map { it.second }
        assertEquals(true, out[0].get("missed_by_kuryeplus"))
        assertEquals(JSONObject.NULL, out[1].get("missed_by_kuryeplus"))
        assertTrue(out[1].isNull("listed_at"))
    }

    @Test fun cardsAndExports() {
        val rows = records(bolt, boltList, at("2026-10-09 10:00"), listOf(trip("b1", bolt, "2026-10-08 13:20", null, done = "2026-10-08 13:53", code = "#G55BB")))
            .map { it.second }
        val tr: (String, String) -> String = { t, _ -> t }
        assertEquals(setOf("b1"), PlatformHistory.byTrip(rows).keys)
        assertTrue(PlatformHistory.cardLine(PlatformHistory.byTrip(rows)["b1"]!!, tr).contains("4,72 ₼ · 13:52"))
        assertTrue(PlatformHistory.cardLine(rows[0], tr).contains("brüt/net belirsiz"))
        assertEquals(listOf("15:10 · Çay Evi Test · 3,46 ₼", "16:45 · Test Plov · 2,72 ₼"), PlatformHistory.missed(rows, "bolt").map(PlatformHistory::missedLine))
        assertTrue(PlatformHistory.missed(rows, "wolt").isEmpty())
        assertTrue(PlatformHistory.FILE in BackupFiles.logNames)
        // WEEM: `AnalysisExport.kindOf(...) == KEEP_ALL` ve `missed_by_kuryeplus` alanı ifadeleri `AnalysisExportTest`'te (A4.3, `:core`).
    }
}
