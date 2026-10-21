package com.kuryelog.logic.link

import com.kuryelog.logic.core.BatterySummary
import com.kuryelog.logic.core.CourierPlatform
import com.kuryelog.logic.core.DaySummary
import com.kuryelog.logic.core.EnergyBlock
import com.kuryelog.logic.core.EnergyRequest
import com.kuryelog.logic.core.EnergyResult
import com.kuryelog.logic.core.NavKind
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.core.NavTarget
import com.kuryelog.logic.core.OfferSummary
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.core.TripStages
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** LINK.md 4.2–4.3: tür tablosu, yük gidiş-dönüşü, saat düzeltmesi, S5 süzgeci. */
class LinkPayloadsTest {
    private val t0 = 1_760_000_000_000L
    private val zero = LinkPayloads.Decoder(0)

    private val offer = OfferSummary(
        "wolt:1", CourierPlatform.WOLT, t0, t0 + 2_000, true, t0 + 60_000, 4.5, 3.2, "Kafe", OfferSummary.Zone.Z2, OfferSummary.Zone.Z3, true,
        OfferSummary.Shown(minLo = 10.0, minHi = 15.0, km = 3.2, stops = 1), 7, 3.1, "Nizami küç. 5, giriş 2, mərtəbə 3", 40.4, 49.8,
    )
    private val stages = TripStages(listOf(
        TripStages.Trip("t1", CourierPlatform.WOLT.pkg, true, TripStages.Stage.TO_DROPOFF, null, null,
            listOf(TripStages.Event(TripStages.Stage.TO_PICKUP, t0, 0, "gps"), TripStages.Event(TripStages.Stage.TO_DROPOFF, t0 + 600_000))),
        TripStages.Trip("t0", CourierPlatform.BOLT.pkg, false, null, TripStages.Outcome.DELIVERED, t0 - 1_000, emptyList()),
    ))
    private val shift = ShiftState(true, startedAtMs = t0, generatedAtMs = t0 + 5)
    private val day = DaySummary("2026-10-20", mapOf(CourierPlatform.WOLT to DaySummary.PlatformDay(4, 20.0, 15.5, 180.0)), 12.0)
    private val target = NavTarget("r1", 40.41, 49.86, NavKind.CUSTOMER, NavOrigin.INTENT, "com.wolt.courierapp", t0, "Nizami küç. 5, giriş 2")
    private val request = EnergyRequest("e1:1:2:x", "w:1-2", EnergyRequest.Kind.LEG, t0, t0 + 900_000)
    private val result = EnergyResult("e1:1:2:x", EnergyBlock(JSONObject().put("source", "surus_paneli").put("wh", 12.5)))
    private val battery = BatterySummary(t0, true, 800, 64.0, 80.0, 31.0, 18.5)
    private val route = RouteStatus(RouteStatus.Target(40.41, 49.86, NavKind.RESTAURANT, NavOrigin.INTENT, t0, "com.wolt.courierapp"),
        RouteStatus.Route(1_250.0, 4, t0 + 1_000), t0 + 1_000)

    @Test
    fun topicTableMatchesThePlan() {
        val names = LinkTopic.entries.map { it.type }
        assertEquals(listOf("ShiftState", "OfferSummary", "TripStages", "DaySummary", "NavTarget", "EnergyRequest", "EnergyResult",
            "BatterySummary", "RouteStatus", "TheftState", "TheftCommand", "LinkRole"), names)
        // S6 ve plan 4.4: hava ve saniyelik scooter verisi tür değil.
        assertFalse(names.any { it.contains("Weather") || it.contains("ScooterLive") })
        for (t in LinkTopic.entries) assertTrue(t.type, Envelope.TYPE.matches(t.type) && !t.type.startsWith("link."))
        val provider = LinkTopic.entries.filter { it.from == LinkRole.PROVIDER }.map { it.type }
        assertEquals(listOf("ShiftState", "OfferSummary", "TripStages", "DaySummary", "NavTarget", "EnergyRequest", "TheftCommand"), provider)
        assertEquals(listOf("EnergyResult", "BatterySummary", "RouteStatus", "TheftState"), LinkTopic.entries.filter { it.from == LinkRole.RECEIVER }.map { it.type })
        // Teslim zorunlu: aşamalar, hedef, enerji (plan 4.5); TTL'ler Messenger sınırlarında.
        assertEquals(setOf("TripStages", "NavTarget", "EnergyRequest", "EnergyResult", "TheftCommand"), LinkTopic.entries.filter { it.reliable }.map { it.type }.toSet())
        for (t in LinkTopic.entries) assertTrue(t.type, t.ttlMs in 1_000L..MessengerConfig.DAY_MS)
        // S11 ve LINK.md 1.3.
        assertFalse(LinkRole.PROVIDER.scooterAllowed)
        assertTrue(LinkRole.RECEIVER.scooterAllowed && LinkRole.OFF.scooterAllowed)
        assertTrue(LinkRole.RECEIVER.listenOnly && !LinkRole.PROVIDER.listenOnly)
        assertEquals(LinkRole.OFF, LinkRole.of("bilinmiyor"))
        assertEquals(LinkRole.RECEIVER, LinkRole.of("receiver"))
    }

    @Test
    fun everyPayloadRoundTrips() {
        assertEquals(shift, zero.shift(LinkPayloads.shift(shift)))
        assertEquals(listOf(offer), zero.offers(LinkPayloads.offers(listOf(offer))))
        assertEquals(emptyList<OfferSummary>(), zero.offers(LinkPayloads.offers(emptyList())))
        assertEquals(stages, zero.stages(LinkPayloads.stages(stages)))
        assertEquals(day, zero.day(LinkPayloads.day(day)))
        assertEquals(target, zero.navTarget(LinkPayloads.navTarget(target)))
        assertEquals(request, zero.energyRequest(LinkPayloads.energyRequest(request)))
        assertEquals(result, zero.energyResult(LinkPayloads.energyResult(result)))
        assertEquals(battery, zero.battery(LinkPayloads.battery(battery)))
        assertEquals(route, zero.route(LinkPayloads.route(route)))
        assertEquals(LinkRole.PROVIDER, zero.role(LinkPayloads.role(LinkRole.PROVIDER)))
    }

    @Test
    fun peerTimesAreMovedToTheLocalClock() {
        // Eşin saati bizimkinden 7,3 sn ileride: yerel = eşin − 7 300.
        val d = LinkPayloads.Decoder(7_300)
        val s = d.shift(LinkPayloads.shift(shift))!!
        assertEquals(t0 - 7_300, s.startedAtMs)
        assertEquals(t0 + 5 - 7_300, s.generatedAtMs)
        assertEquals(0L, d.shift(LinkPayloads.shift(ShiftState(false)))!!.generatedAtMs)
        val o = d.offers(LinkPayloads.offers(listOf(offer)))!!.single()
        assertEquals(listOf(t0 - 7_300, t0 + 2_000 - 7_300, t0 + 60_000 - 7_300), listOf(o.firstSeenAtMs, o.lastSeenAtMs, o.expiresAtMs))
        val st = d.stages(LinkPayloads.stages(stages))!!
        assertEquals(listOf(t0 - 7_300, t0 + 600_000 - 7_300), st.trips[0].events.map { it.atMs })
        assertEquals(t0 - 1_000 - 7_300, st.trips[1].endedAtMs)
        assertEquals(t0 - 7_300, d.navTarget(LinkPayloads.navTarget(target))!!.requestedAtMs)
        val r = d.energyRequest(LinkPayloads.energyRequest(request))!!
        assertEquals(t0 - 7_300 to t0 + 900_000 - 7_300, r.fromWallMs to r.toWallMs)
        assertEquals(request.requestId, r.requestId)
        assertEquals(t0 - 7_300, d.battery(LinkPayloads.battery(battery))!!.atMs)
        val ro = d.route(LinkPayloads.route(route))!!
        assertEquals(listOf(t0 - 7_300, t0 + 1_000 - 7_300, t0 + 1_000 - 7_300), listOf(ro.target!!.requestedAtMs, ro.route!!.measuredAtMs, ro.updatedAtMs))
        assertEquals(day, d.day(LinkPayloads.day(day)))
    }

    @Test
    fun brokenPayloadsAreDropped() {
        val bad = listOf(byteArrayOf(0xC3.toByte(), 0x28), "{".toByteArray(), "[]".toByteArray(), "{\"schemaVersion\":2}".toByteArray())
        for (b in bad) {
            assertNull(zero.shift(b)); assertNull(zero.offers(b)); assertNull(zero.stages(b)); assertNull(zero.navTarget(b))
            assertNull(zero.energyRequest(b)); assertNull(zero.battery(b)); assertNull(zero.route(b)); assertNull(zero.role(b))
        }
        assertNull(zero.role("{\"schemaVersion\":1,\"role\":\"kral\"}".toByteArray()))
        // Okuyucu bilinmeyen alanı yok sayar.
        val extra = JSONObject(String(LinkPayloads.navTarget(target))).put("yeniAlan", 1).toString().toByteArray()
        assertEquals(target, zero.navTarget(extra))
    }

    @Test
    fun customerPersonalDataNeverLeaves() {
        // Bugünkü tiplerin hiçbir anahtarı süzgece takılmaz (yukarıdaki gidiş-dönüş hepsini zaten süzgeçten geçirdi).
        for (k in listOf("customerName", "customer_phone", "phone", "notes", "deliveryNote", "comment", "instructions", "name", "customer", "recipientName"))
            try {
                ReceiverSafety.check(JSONObject().put("a", JSONArray().put(JSONObject().put(k, "x"))))
                fail(k)
            } catch (e: UnsafePayload) {
                assertEquals(k, e.key)
                assertFalse(e.message!!.contains("x"))
            }
        // Adres metni ve koordinat serbest (S5): değerlere bakılmaz.
        ReceiverSafety.check(JSONObject().put("dropoffAddress", "telefon: yok").put("label", "Nizami 5").put("dropoffLat", 40.0))
        val wire = String(LinkPayloads.offers(listOf(offer)))
        assertTrue(wire.contains("Nizami küç. 5") && wire.contains("40.4"))
    }
}
