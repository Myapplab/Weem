package com.kuryelog.logic.orders.dataset

import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.core.WeatherNow
import com.kuryelog.logic.orders.DeliveryCore
import com.kuryelog.logic.orders.OfferState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** WEEM kayıt düzeni (MAP.md 13.11, 13.18 "Yeni"): iç teklif durumundan satır, adres metni yazılmaz, hava 20 dk kuralı, yoklama satırı yok, seyreltme, vardiya satırları. */
class DatasetRecorderTest {
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.food.courier"
    private val zone = ZoneOffset.ofHours(4)
    private val t0 = 1_791_295_200_000L

    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String>) = CourierParser.parse(pkg, text.toList(), actions, at)
    private fun woltOffer(at: Long) = obs(
        wolt, at, "Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000", "Customer drop-off", "Test 2, Bakı", "Accept",
        actions = listOf("Accept", "Decline"),
    )
    private fun boltOffer(at: Long) = obs(
        bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼", actions = listOf("22 min, 4,49 ₼", "Decline"),
    )

    private fun states(): List<OfferState> {
        val c = DeliveryCore(); c.start(); c.observe(boltOffer(t0)); c.observe(woltOffer(t0 + 1_000))
        return c.pending.values.toList()
    }

    private fun recorder() = DatasetRecorder({ zone }, { "baku" })

    @Test
    fun `iç teklif durumundan satır kurulur, adres metni ve müşteri verisi yazılmaz`() {
        val (day, rows) = recorder().rows(states(), t0 + 5_000, 777, 5_000, DatasetContract.point(40.4091, 49.867), null)
        assertEquals(DatasetContract.day(t0 + 5_000, zone), day)
        assertEquals(2, rows.size)
        val w = rows.first { it.getString("platform") == "wolt" }
        assertEquals(2.31, w.getDouble("feeAzn"), 0.0)
        assertEquals("baku", w.getString("region"))
        assertEquals("+04:00".let { 240 }, w.getInt("utcOffsetMinutes"))
        assertEquals(40.409, w.getJSONObject("courier").getDouble("lat"), 0.0)
        assertTrue(w.has("pickup")) // Plus Code gözlenmiş kurye noktasıyla çözülür
        val text = rows.joinToString { it.toString() }
        for (secret in listOf("Test 2", "Izmir Doner", "Naxcivani", "Customer drop-off", "9RQW")) assertFalse(secret, text.contains(secret))
        assertEquals("observer", w.getString("readTrigger"))
        assertEquals(t0 + 1_000, w.getLong("rawstatsChangedAtMs"))
        assertEquals(4_000L, w.getLong("feedDelayMs"))
    }

    @Test
    fun `hava önbelleği yalnız 20 dakikadan tazeyse eklenir`() {
        val fresh = WeatherNow(t0 + 5_000 - 1_200_000, tempC = 21.5, windKmh = 12.0, rainMm = 0.0)
        val rows = recorder().rows(states(), t0 + 5_000, 1, 0, null, fresh).second
        val r = rows.first()
        assertEquals(21.5, r.getDouble("temperatureC"), 0.0)
        assertEquals("existing_weather_cache", r.getJSONObject("field_sources").getString("temperatureC"))
        val old = WeatherNow(t0 + 5_000 - 1_200_001, tempC = 21.5)
        val stale = recorder().rows(states(), t0 + 5_000, 1, 0, null, old).second.first()
        assertTrue(stale.isNull("temperatureC"))
        assertEquals("unavailable", stale.getJSONObject("field_sources").getString("temperatureC"))
    }

    @Test
    fun `seyreltme aynı teklifi 5 saniyede bir yazar, teklif gidince son görülmeyi`() {
        val r = recorder()
        val offers = states()
        assertEquals(2, r.rows(offers, t0, 1, 0, null, null).second.size)
        assertEquals(0, r.rows(offers, t0 + 300, 1, 300, null, null).second.size)
        offers.forEach { it.lastSeenAt += 500 }
        assertEquals(0, r.rows(offers, t0 + 800, 1, 800, null, null).second.size) // yalnız lastSeen değişti: bekletildi
        val gone = r.rows(emptyList(), t0 + 1_100, 1, 1_100, null, null).second
        assertEquals(2, gone.size) // son görülme kaybolmaz
        assertTrue(gone.all { it.getLong("lastSeenAtMs") > it.getLong("firstSeenAtMs") - 1 })
    }

    @Test
    fun `bilinmeyen platform satır üretmez, okuma satırı dosyası yok`() {
        val c = DeliveryCore(); c.start()
        val unknown = com.kuryelog.logic.orders.OfferState("offer-1-1", "com.other.app", t0, woltOffer(t0).copy(pkg = "com.other.app"))
        assertNull(recorder().feedOffer(unknown))
        assertEquals("yolgo-live.jsonl", DatasetRecorder.FILE)
    }

    @Test
    fun `vardiya satırları açıkken bitişsiz, kapanışta vardiyanın değdiği bütün günlerde`() {
        val start = 1_791_100_000_000L
        val open = DatasetShifts.row(start, null, start, 5)!!
        assertTrue(open.isNull("shiftEndMs"))
        assertEquals("open_shift", open.getString("shiftEnd_source"))
        assertEquals("yolgo_shift", open.getString("shiftStart_source"))
        val end = start + 6 * 3_600_000L
        val closed = DatasetShifts.row(start, end, end, 6)!!
        assertEquals(end, closed.getLong("shiftEndMs"))
        assertNull(DatasetShifts.row(0, null, 1, 1))
        assertNull(DatasetShifts.row(start, start - 1, 1, 1))
        assertEquals(1, DatasetShifts.days(start, null, zone).size)
        assertTrue(DatasetShifts.days(start, start + 40 * 3_600_000L, zone).size >= 2)
    }

    @Test
    fun `elle ekleme doğrulaması`() {
        val ok = DatasetManual.save("weekly_payout", mapOf("platform" to "wolt", "amountAzn" to "250,5", "periodStart" to "2026-10-05", "periodEnd" to ""), 1, 2)
        assertEquals(250.5, ok.row!!.getDouble("amountAzn"), 0.0)
        assertTrue(ok.row!!.isNull("periodEnd"))
        assertEquals("user_entered", ok.row!!.getJSONObject("field_sources").getString("amountAzn"))
        assertEquals("manual", ok.row!!.getString("source"))
        assertEquals(DatasetManual.Error.PAYOUT_REQUIRED, DatasetManual.save("weekly_payout", mapOf("amountAzn" to "5"), 1, 2).error)
        assertEquals(DatasetManual.Error.NUMBERS, DatasetManual.save("weekly_payout", mapOf("platform" to "bolt", "amountAzn" to "-1"), 1, 2).error)
        assertEquals(DatasetManual.Error.NUMBERS, DatasetManual.save("shift_note", mapOf("breakMinutes" to "1441"), 1, 2).error)
        assertEquals(DatasetManual.Error.DATE, DatasetManual.save("weekly_payout", mapOf("platform" to "bolt", "amountAzn" to "5", "periodStart" to "2026-02-30"), 1, 2).error)
        assertEquals(DatasetManual.Error.KIND, DatasetManual.save("note", emptyMap(), 1, 2).error)
        val note = DatasetManual.save("shift_note", mapOf("weather" to "rain", "otherApp" to "yok", "breakMinutes" to "15"), 1, 2).row!!
        assertEquals("rain", note.getString("weather")); assertTrue(note.isNull("otherApp"))
        assertEquals("unavailable", note.getJSONObject("field_sources").getString("otherApp"))
        val shot = DatasetManual.screenshotRow("wolt_earnings", DatasetManual.attachmentPath("wolt_earnings", "u-1", "image/png"), "image/png", 1, 2)
        assertEquals("attachments/wolt_earnings_u-1.png", shot.getString("file")); assertFalse(shot.getBoolean("content_read")); assertFalse(shot.getBoolean("ocr"))
        assertEquals("image", DatasetManual.extension("image/gif"))
        assertEquals(JSONObject.NULL, JSONObject().put("x", JSONObject.NULL).get("x"))
    }
}
