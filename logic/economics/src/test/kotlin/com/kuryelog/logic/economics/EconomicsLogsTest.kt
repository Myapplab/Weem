package com.kuryelog.logic.economics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EconomicsLogsTest.kt @ 6388c04, aynı girdi → aynı çıktı; farklar ECONOMICS.md "WEEM" satırlarında.

/**
 * SPEC 2.1.0 §17.2 writers (WP7): every row comes from the real core, waits, online sessions and recorder, fed with the
 * corpus screens. With `KL_ROWS_OUT=<dir>` every row is also dumped for the schema check:
 * `python3 -I docs/ekonomi-v2/araclar/validate_rows.py <dir>` (jsonschema against kuryelog_logs_v2 / feed_v2).
 */
class EconomicsLogsTest {
    private val wolt = "com.wolt.courierapp"; private val bolt = "com.bolt.food.courier"
    /** 2026-10-06 18:00 Baku. */
    private val t0 = 1791295200000L
    private fun obs(pkg: String, at: Long, vararg text: String, actions: List<String> = emptyList(), source: String = "accessibility") =
        CourierParser.parse(pkg, text.toList(), actions, at, source)
    private fun boltOffer(at: Long, vararg extra: String) = obs(bolt, at, "Decline", "Cook Box by Merkez Lahmacun Nizami", "Mehemmed Naxcivani 15", "~8 min", "~14 min", "22 min, 4,49 ₼",
        *extra, actions = listOf("22 min, 4,49 ₼", "Decline"))
    private fun boltWaiting(at: Long) = obs(bolt, at, "1.6x", "1.7x", "Looking for orders", "Go offline")
    private fun boltPickup(at: Long) = obs(bolt, at, "Order is being prepared", "Arrive in 11 min", "Xengeland", "Mehdi Hüseyn 99", "Orders to pick up:", "2", "#AAAA1", "Items (0)", "#BBBB2")
    private fun woltOffer(at: Long, restaurant: String = "Izmir Doner Tantuni No1", fee: String = "₼2.31") = obs(wolt, at, "Ready in 3 min", "Decline", fee,
        "2 stops (2.8 km) • 8–16 min", restaurant, "9RQW+344 Baku, Baku, AZ1000", "Customer drop-off", "Test 2, Bakı", "Accept", actions = listOf("Accept", "Decline"))
    private fun woltWaiting(at: Long, vararg extra: String) = obs(wolt, at, "Expect a task soon", "Delivery demand", "Medium", "Hər sifariş üçün əlavə 50% from 08:00", *extra)
    private fun woltOffline(at: Long, vararg extra: String) = obs(wolt, at, "Baku Central", "Delivery demand", "Medium", "Go online", *extra)
    private fun woltBan(at: Long) = obs(wolt, at, "Unable to go online", "You've been set offline until 21:46 for declining too many deliveries.", "Go online",
        "Delivery demand Medium", "Hər sifariş üçün əlavə 40% from 12:00")
    private fun woltPickup(at: Long) = obs(wolt, at, "Pickup 3 min", "Pickup from", "Donerist-Kebabist Sahil", "Sabail district, Uzeyir Hajibeyov str., 27, AZ1000 Baku",
        actions = listOf("Collect"))
    private fun woltCard(at: Long, name: String) = obs(wolt, at, "Dropoff to", name, "Test küçəsi 39k, Bakı", "INFO", "ITEMS", "Address details",
        "Order details", "From", "Punkap", "#408", "Difficulties with the delivery?", "Send a message", "Order delivered!",
        actions = listOf("Send a message", "Order delivered!"))
    private fun bakuHour(at: Long) = Calendar.getInstance(TimeZone.getTimeZone("Asia/Baku")).apply { timeInMillis = at }.get(Calendar.HOUR_OF_DAY)

    /** Mirrors OrderTracker.onScreen / onClick / stop / distance around the pure recorder. */
    private inner class Rig(commission: Boolean = true) {
        val core = DeliveryCore().also { it.start() }
        val waits = WaitObservation()
        val sessions = OnlineSessions()
        val rec = EconomicsRecorder()
        val rows = ArrayList<EconomicsRecorder.Row>()
        var zone = "Z2"
        val basis: (String) -> String = { if (commission) "gross_before_park" else "unknown" }
        private val declineIntent = HashMap<String, Long>()
        fun frame(o: CourierObservation): List<CoreEvent> {
            sessions.observe(o)
            val before = core.pending.values.associateBy { it.id }
            val ev = core.observe(o)
            ev.forEach { sessions.event(it, 1) }
            for (e in ev) when (e.type) {
                "delivered" -> waits.nextCause(o.pkg, "after_delivery_observed")
                "rejected" -> waits.nextCause(o.pkg, if (o.at - (declineIntent[o.pkg] ?: Long.MIN_VALUE) in 0..30000) "after_decline_observed" else "after_offer_disappearance")
            }
            rows += rec.waits(waits.observe(o, "g3", "merkez", bakuHour(o.at)))
            rows += rec.frame(o, ev, before, core, sessions.openSessions(), zone, basis)
            return ev
        }
        fun click(pkg: String, text: String, at: Long): List<CoreEvent> {
            if (CourierParser.isDecline(text)) declineIntent[pkg] = at
            val before = core.pending.values.associateBy { it.id }
            val ev = core.click(pkg, text, at)
            ev.forEach { sessions.event(it) ; if (it.type == "rejected") waits.nextCause(pkg, "after_decline_observed") }
            rows += rec.events(at, ev, before, core, sessions.openSessions(), basis)
            return ev
        }
        fun distance(at: Long, m: Double) { core.addDistance(m); rec.distance(at, m) }
        fun stop(at: Long) {
            sessions.stop(); rows += rec.waits(waits.stop())
            val before = core.pending.values.associateBy { it.id }
            rows += rec.stop(at, core.stop(at), before, core, basis)
        }
        fun of(file: String) = rows.filter { it.file == file }.map { it.json }
    }

    private fun def(file: String) = when (file) {
        EconomicsLogs.OFFERS -> "offerRecord"; EconomicsLogs.LEGS -> "tripLegs"; EconomicsLogs.IDLE -> "idleSpell"
        EconomicsLogs.PENALTIES -> "penaltyObservation"; EconomicsLogs.WAITS -> "waitSegmentV2"; else -> error(file)
    }
    /** Dump for `validate_rows.py` and the PII check every writer must pass. */
    private fun check(rows: List<EconomicsRecorder.Row>) {
        for (r in rows) {
            val s = r.json.toString()
            for (secret in listOf("Customer", "Test 2", "Test küçəsi", "Naxcivani", "Hüseyn", "Izmir", "Donerist", "Uzeyir", "Xengeland", "Merkez", "#408", "9RQW"))
                assertFalse("$secret in ${r.file}: $s", s.contains(secret))
            for (k in listOf("lat", "lon", "name", "phone", "address", "note", "apartment", "door_code", "raw", "texts")) assertFalse("$k in $s", s.contains("\"$k\""))
        }
        dump(rows.map { def(it.file) to it.json })
    }
    private fun dump(rows: List<Pair<String, JSONObject>>) {
        val dir = System.getenv("KL_ROWS_OUT") ?: return
        val f = java.io.File(dir).apply { mkdirs() }.resolve("EconomicsLogsTest.jsonl")
        rows.forEach { (d, j) -> f.appendText(JSONObject().put("def", d).put("row", j).toString() + "\n") }
    }

    // ---------------------------------------------------------------- economics-offers.jsonl

    @Test fun acceptedBoltOfferKeepsTheCardAndThePayoutBasis() {
        val r = Rig()
        r.frame(boltWaiting(t0)); r.frame(boltOffer(t0 + 20_000)); r.click(bolt, "22 min, 4,49 ₼", t0 + 23_000)
        val ev = r.frame(boltPickup(t0 + 25_000))
        assertEquals("accepted", ev.first { it.type == "accepted" }.type)
        val row = r.of(EconomicsLogs.OFFERS).single()
        assertEquals("accepted_observed", row.getString("decisionKind")); assertEquals("bolt", row.getString("platform"))
        assertEquals(t0 + 20_000, row.getLong("firstSeenAtMs")); assertEquals(t0 + 25_000, row.getLong("decisionAtMs"))
        assertEquals(4.49, row.getDouble("feeAzn"), 1e-9); assertEquals("gross_before_park", row.getString("feeBasis"))
        val shown = row.getJSONObject("shown")
        assertEquals(8, shown.getInt("approachMin")); assertEquals(14, shown.getInt("deliveryMin")); assertEquals(22, shown.getInt("totalMin"))
        assertEquals("unknown", row.getString("pickupZone")); assertEquals("unknown", row.getString("dropoffZone"))
        assertEquals(t0 + 80_000, row.getLong("expiresAtMs")); assertEquals("documented_upper_bound", row.getString("expirySource"))
        assertFalse(row.getBoolean("countsAsDecline")); assertTrue(row.isNull("duringTripId")); assertTrue(row.isNull("verdictShadow"))
        assertEquals("unknown", Rig(commission = false).let { x -> x.frame(boltOffer(t0)); x.frame(boltPickup(t0 + 2_000)); x.of(EconomicsLogs.OFFERS).single().getString("feeBasis") })
        // WP8 sets the shadow evaluator; its verdict lands in the row unchanged.
        val shadow = Rig().also { x -> x.rec.verdictShadow = { o -> JSONObject().put("specVersion", "2.1.0").put("verdict", "NO_VERDICT").put("surplusMid", JSONObject.NULL)
            .put("surplusLo", JSONObject.NULL).put("surplusHi", JSONObject.NULL).put("kappa", JSONObject.NULL).put("g", 4.0).put("gSource", "prior")
            .put("tauMid", JSONObject.NULL).put("rideKm", JSONObject.NULL).put("computedAtMs", o.firstAt).put("displayed", false) } }
        shadow.frame(boltOffer(t0)); shadow.frame(boltPickup(t0 + 2_000))
        assertEquals("NO_VERDICT", shadow.of(EconomicsLogs.OFFERS).single().getJSONObject("verdictShadow").getString("verdict"))
        check(shadow.rows)
        check(r.rows)
    }

    @Test fun woltOfferRowHasZonesFromThePlusCodeAndStops() {
        val r = Rig()
        r.frame(woltWaiting(t0)); r.frame(woltOffer(t0 + 10_000)); r.frame(woltOffer(t0 + 12_000))
        r.click(wolt, "Decline", t0 + 14_000); r.frame(obs(wolt, t0 + 15_000, "Decline offer?", "Return to offer details", "Decline"))
        r.click(wolt, "Decline", t0 + 16_000)
        val row = r.of(EconomicsLogs.OFFERS).single()
        assertEquals("declined_tap_observed", row.getString("decisionKind")); assertTrue(row.getBoolean("countsAsDecline"))
        assertEquals(t0 + 12_000, row.getLong("lastSeenAtMs"))
        val shown = row.getJSONObject("shown")
        assertEquals(2, shown.getInt("stops")); assertEquals(8, shown.getInt("minLo")); assertEquals(16, shown.getInt("minHi"))
        assertEquals(3, shown.getInt("readyInMin")); assertEquals(2.8, shown.getDouble("km"), 1e-9); assertTrue(shown.isNull("totalMin"))
        // "9RQW+344 Baku" decodes near the Baku reference; "Test 2, Bakı" is a street, never looked up.
        val p = OfferGeo.fromCardText("9RQW+344 Baku, Baku, AZ1000")!!
        assertEquals(OfferGeo.coarseZone(p.lat, p.lon), row.getString("pickupZone")); assertNotEquals("unknown", row.getString("pickupZone"))
        assertEquals("unknown", row.getString("dropoffZone"))
        check(r.rows)
    }

    @Test fun everyCoreOutcomeMapsToTheSpecDecisionKind() {
        val r = Rig()
        r.frame(boltWaiting(t0))
        // documented window: still on screen at first seen + 60 s − 2 s → timeout
        r.frame(boltOffer(t0 + 1_000)); r.frame(boltOffer(t0 + 30_000)); r.frame(boltWaiting(t0 + 59_000)); r.frame(boltWaiting(t0 + 59_500))
        // gone early without a tap
        r.frame(boltOffer(t0 + 100_000)); r.frame(boltWaiting(t0 + 110_000)); r.frame(boltWaiting(t0 + 110_500))
        // a tap, then the waiting screen
        r.frame(boltOffer(t0 + 200_000)); r.click(bolt, "Decline", t0 + 201_000); r.frame(boltWaiting(t0 + 202_000)); r.frame(boltWaiting(t0 + 202_500))
        // Bolt "Optional" order declined: does not count
        r.frame(boltOffer(t0 + 300_000, "Optional")); r.click(bolt, "Decline", t0 + 301_000); r.frame(boltWaiting(t0 + 302_000)); r.frame(boltWaiting(t0 + 302_500))
        // Wolt: one card replaced by another, then the "N deliveries declined" banner
        r.frame(woltWaiting(t0 + 400_000)); r.frame(woltOffer(t0 + 401_000)); r.frame(woltOffer(t0 + 402_000, restaurant = "Bravo Market", fee = "₼3.10"))
        r.frame(woltWaiting(t0 + 403_000, "1 deliveries declined")); r.frame(woltWaiting(t0 + 403_100, "2 deliveries declined"))
        // pending at the manual shift stop
        r.frame(woltOffer(t0 + 500_000)); r.stop(t0 + 505_000)
        val kinds = r.of(EconomicsLogs.OFFERS).map { it.getString("decisionKind") to it.getBoolean("countsAsDecline") }
        assertEquals(listOf("timeout_inferred" to true, "disappeared_unknown" to true, "declined_tap_observed" to true, "declined_tap_observed" to false,
            "replaced_by_new_offer" to false, "declined_banner_observed" to true, "recording_stopped" to false), kinds)
        assertTrue(r.of(EconomicsLogs.OFFERS)[3].getBoolean("optional"))
        // The banner is also a penalty observation.
        assertEquals(listOf(1, 2), r.of(EconomicsLogs.PENALTIES).filter { it.getString("kind") == "declines_banner" }.map { it.getInt("declinedCountShown") })
        check(r.rows)
    }

    @Test fun offersPendingAtAProcessRestartAreRecordingStopped() {
        val o1 = OfferState("offer-a", bolt, t0, boltOffer(t0)).also { it.lastSeenAt = t0 + 5_000 }
        val o2 = OfferState("offer-b", wolt, t0 - 86_400_000L, woltOffer(t0 - 86_400_000L))
        val rec = EconomicsRecorder()
        val rows = rec.restart(t0 + 100_000, listOf(o1), listOf(o2)) { "unknown" }
        assertEquals(t0 + 5_001, rec.restartedAtMs)
        val dropped = rows.single().json
        assertEquals("offer-b", dropped.getString("offerKey")); assertEquals("recording_stopped", dropped.getString("decisionKind"))
        assertFalse(dropped.getBoolean("countsAsDecline")); assertEquals(t0 + 100_000, dropped.getLong("decisionAtMs"))
        // The restored offer is never seen again: its disappearance is not an observed decline.
        val core = DeliveryCore(); core.pending[bolt] = o1; core.start()
        val sessions = OnlineSessions()
        val out = ArrayList<EconomicsRecorder.Row>(rows)
        for (at in listOf(t0 + 101_000, t0 + 101_500)) {
            val f = boltWaiting(at); sessions.observe(f)
            val before = core.pending.values.associateBy { it.id }
            out += rec.frame(f, core.observe(f), before, core, sessions.openSessions(), "unknown") { "unknown" }
        }
        val restored = out.map { it.json }.single { it.optString("offerKey") == "offer-a" }
        assertEquals("recording_stopped", restored.getString("decisionKind")); assertFalse(restored.getBoolean("countsAsDecline"))
        // Seen again after the restart, an outcome is observed as usual.
        val seen = OfferState("offer-c", bolt, t0, boltOffer(t0)).also { it.lastSeenAt = t0 + 20_000 }
        assertEquals("disappeared_unknown", EconomicsLogs.decisionKind("rejected", "offer_to_idle_map", seen, rec.restartedAtMs))
        assertEquals("declined_tap_observed", EconomicsLogs.decisionKind("rejected", "courier_declined", o1, rec.restartedAtMs))
        check(out)
    }

    @Test fun decisionKindTableOfSpec172() {
        val o = OfferState("x", bolt, 0, boltOffer(0))
        val table = mapOf(("accepted" to "active_screen") to "accepted_observed", ("rejected" to "courier_declined_confirmed") to "declined_tap_observed",
            ("rejected" to "courier_declined") to "declined_tap_observed", ("rejected" to "courier_declined_banner") to "declined_banner_observed",
            ("rejected" to "offer_to_idle_map") to "disappeared_unknown", ("offer_unconfirmed" to "offer_not_seen") to "disappeared_unknown",
            ("offer_unconfirmed" to "expired_inferred") to "timeout_inferred", ("offer_unconfirmed" to "replaced_without_idle") to "replaced_by_new_offer",
            ("offer_unconfirmed" to "manual_shift_stop") to "recording_stopped", ("offer_unconfirmed" to "restart") to "recording_stopped")
        for ((k, v) in table) assertEquals("$k", v, EconomicsLogs.decisionKind(k.first, k.second, o))
        // offer_to_idle_map while the card stayed until expiry − 2 s is a timeout (SPEC 17.2 table)
        o.lastSeenAt = 58_000; assertEquals("timeout_inferred", EconomicsLogs.decisionKind("rejected", "offer_to_idle_map", o))
        assertNull(EconomicsLogs.decisionKind("delivered", null, o)); assertNull(EconomicsLogs.decisionKind("offer", null, o))
        assertFalse(EconomicsLogs.countsAsDecline("timeout_inferred", optional = true))
        assertNull(EconomicsLogs.offerRecord(OfferState("y", "com.example.app", 0, boltOffer(0).copy(pkg = "com.example.app")), CoreEvent("rejected", "com.example.app", 1, "y"), "unknown", null))
    }

    // ---------------------------------------------------------------- economics-legs.jsonl

    @Test fun deliveredTripHasLegsWithGpsKm() {
        val r = Rig()
        r.frame(woltWaiting(t0)); r.frame(woltOffer(t0 + 10_000)); r.click(wolt, "Accept", t0 + 12_000)
        r.frame(woltPickup(t0 + 15_000)); r.distance(t0 + 200_000, 1_200.0)
        r.click(wolt, "Arrived", t0 + 300_000); r.distance(t0 + 400_000, 30.0)
        r.click(wolt, "Collect", t0 + 500_000); r.frame(woltCard(t0 + 510_000, "Customer A")); r.distance(t0 + 700_000, 2_500.0)
        r.click(wolt, "Arrived", t0 + 900_000); r.distance(t0 + 950_000, 40.0)
        r.click(wolt, "Order delivered!", t0 + 1_000_000); r.frame(woltWaiting(t0 + 1_002_000))
        val ev = r.frame(woltWaiting(t0 + 1_002_500))
        assertTrue(ev.any { it.type == "delivered" })
        val row = r.of(EconomicsLogs.LEGS).single()
        assertEquals("delivered", row.getString("outcome")); assertEquals(t0 + 15_000, row.getLong("acceptedAtMs"))
        assertEquals(t0 + 510_000, row.getLong("pickedUpAtMs")); assertEquals(t0 + 1_002_500, row.getLong("deliveredAtMs"))
        assertEquals((1_002_500 - 15_000) / 60000.0, row.getDouble("totalTaskMin"), 1e-3)
        assertEquals(12.0, row.getDouble("shownM"), 0.0); assertEquals("wolt_range_midpoint", row.getString("shownMSource")); assertEquals(3, row.getInt("readyInMin"))
        val legs = row.getJSONArray("legs")
        assertEquals(listOf("approach", "wait_at_venue", "to_dropoff", "handoff"), (0 until legs.length()).map { legs.getJSONObject(it).getString("kind") })
        assertEquals(listOf(1.2, 0.03, 2.5, 0.04), (0 until legs.length()).map { legs.getJSONObject(it).getDouble("km") })
        assertEquals(t0 + 300_000, legs.getJSONObject(0).getLong("endAtMs")); assertEquals(t0 + 1_002_500, legs.getJSONObject(3).getLong("endAtMs"))
        assertEquals(row.getString("offerKey"), r.of(EconomicsLogs.OFFERS).single().getString("offerKey"))
        assertTrue(row.isNull("stackedWith")); assertFalse(row.getBoolean("excludeFromCalibration"))
        check(r.rows)
    }

    @Test fun tripClosedWithoutConfirmationAndStackedTrips() {
        val r = Rig()
        r.frame(boltWaiting(t0)); r.frame(boltOffer(t0 + 1_000)); r.frame(boltPickup(t0 + 5_000))
        r.frame(woltWaiting(t0 + 6_000)); r.frame(woltOffer(t0 + 7_000)); r.frame(woltPickup(t0 + 9_000))
        r.stop(t0 + 60_000)
        val legs = r.of(EconomicsLogs.LEGS)
        assertEquals(2, legs.size); assertTrue(legs.all { it.getString("outcome") == "unknown" && it.isNull("deliveredAtMs") && it.isNull("totalTaskMin") })
        val b = legs.single { it.getString("pkg") == bolt }; val w = legs.single { it.getString("pkg") == wolt }
        assertEquals(w.getString("tripId"), b.getString("stackedWith")); assertEquals(b.getString("tripId"), w.getString("stackedWith"))
        assertEquals("bolt_eta_sum", b.getString("shownMSource")); assertEquals(22.0, b.getDouble("shownM"), 0.0)
        // no GPS distance at all: km unknown, not 0
        val leg = b.getJSONArray("legs").getJSONObject(0)
        assertEquals("approach", leg.getString("kind")); assertTrue(leg.isNull("km")); assertTrue(leg.isNull("endAtMs"))
        // a manual correction after the stop adds no second row
        r.rows += r.rec.events(t0 + 70_000, r.core.confirmDelivered(b.getString("tripId"), t0 + 70_000), emptyMap(), r.core, emptyList(), r.basis)
        assertEquals(2, r.of(EconomicsLogs.LEGS).size)
        check(r.rows)
    }

    @Test fun shownMinutesFollowSpec5() {
        assertEquals(12.0 to "wolt_range_midpoint", EconomicsLogs.shownMinutes(woltOffer(0)))
        assertEquals(22.0 to "bolt_eta_sum", EconomicsLogs.shownMinutes(boltOffer(0)))
        val button = obs(bolt, 0, "Decline", "Cook Box", "22 min, 4,49 ₼", actions = listOf("22 min, 4,49 ₼", "Decline"))
        assertEquals(22.0 to "bolt_button_total", EconomicsLogs.shownMinutes(button))
        assertEquals(null to "missing", EconomicsLogs.shownMinutes(null))
    }

    // ---------------------------------------------------------------- economics-idle.jsonl

    @Test fun idleSpellIsCourierLevelAcrossBothApps() {
        val r = Rig()
        r.zone = "Z1"; r.frame(woltWaiting(t0)); r.frame(boltWaiting(t0 + 60_000))
        r.distance(t0 + 120_000, 800.0); r.zone = "Z2"
        r.frame(woltOffer(t0 + 300_000)); r.frame(woltWaiting(t0 + 310_000)); r.frame(woltWaiting(t0 + 310_500))
        r.frame(boltOffer(t0 + 400_000)); r.frame(boltPickup(t0 + 420_000))
        val first = r.of(EconomicsLogs.IDLE).single()
        assertEquals(t0, first.getLong("startAtMs")); assertEquals(t0 + 420_000, first.getLong("endAtMs"))
        assertEquals("offer_accepted", first.getString("endEvent")); assertFalse(first.getBoolean("censored"))
        assertEquals(1, first.getJSONObject("offersSeen").getInt(wolt)); assertEquals(1, first.getJSONObject("offersSeen").getInt(bolt))
        val apps = first.getJSONArray("appsOnline").let { a -> (0 until a.length()).associate { a.getJSONObject(it).getString("pkg") to a.getJSONObject(it).getLong("onlineMs") } }
        assertEquals(mapOf(wolt to 420_000L, bolt to 360_000L), apps)
        assertEquals("Z1", first.getString("zoneStart")); assertEquals("Z2", first.getString("zoneEnd")); assertEquals("B4", first.getString("hourBlockStart"))
        assertEquals(0.8, first.getDouble("kmRidden"), 1e-9); assertTrue(first.getBoolean("repositioning"))
        // Busy on Bolt: no spell. The shift stop closes nothing then.
        r.stop(t0 + 500_000)
        assertEquals(1, r.of(EconomicsLogs.IDLE).size)
        check(r.rows)
    }

    @Test fun idleSpellEndsWhenAllAppsGoOfflineOrTheShiftStops() {
        val r = Rig()
        r.frame(woltWaiting(t0)); r.frame(woltOffline(t0 + 60_000))
        r.frame(woltWaiting(t0 + 120_000)); r.frame(woltBan(t0 + 180_000))
        r.frame(woltWaiting(t0 + 4 * 3_600_000L)); r.stop(t0 + 4 * 3_600_000L + 60_000)
        assertEquals(listOf("all_offline", "penalty_offline", "shift_end"), r.of(EconomicsLogs.IDLE).map { it.getString("endEvent") })
        assertTrue(r.of(EconomicsLogs.IDLE).all { it.getBoolean("censored") })
        assertEquals(60_000L, r.of(EconomicsLogs.IDLE)[0].getLong("endAtMs") - t0)
        check(r.rows)
    }

    @Test fun idleSpellSurvivesAProcessDeathAsACensoredGap() {
        val r = Rig()
        r.frame(woltWaiting(t0)); r.frame(woltOffer(t0 + 30_000)); r.frame(woltOffer(t0 + 40_000))
        val raw = r.rec.encodeIdle()!!
        val row = EconomicsRecorder().restoreIdle(raw).single().json
        assertEquals("gap", row.getString("endEvent")); assertTrue(row.getBoolean("censored")); assertEquals(t0 + 40_000, row.getLong("endAtMs"))
        assertEquals(1, row.getJSONObject("offersSeen").getInt(wolt))
        assertTrue(EconomicsRecorder().restoreIdle(null).isEmpty()); assertTrue(EconomicsRecorder().restoreIdle("{broken").isEmpty())
        // 30 min without any frame: censored at the last one
        r.frame(woltWaiting(t0 + 60_000)); r.frame(woltWaiting(t0 + 60_000 + EconomicsRecorder.GAP_MS + 1))
        val gap = r.of(EconomicsLogs.IDLE).single()
        assertEquals("gap", gap.getString("endEvent")); assertEquals(t0 + 60_000, gap.getLong("endAtMs"))
        assertTrue(r.rows.filter { it.file == EconomicsLogs.IDLE }.all { it.day == "2026-10-06" })
        check(r.rows + EconomicsRecorder.Row(EconomicsLogs.IDLE, row))
    }

    // ---------------------------------------------------------------- economics-penalties.jsonl

    @Test fun offlineForDeclinesCarriesTheWindowCount() {
        val r = Rig()
        r.frame(woltWaiting(t0))
        for (at in listOf(t0 + 10_000, t0 + 40_000, t0 + 70_000)) {
            r.frame(woltOffer(at)); r.click(wolt, "Decline", at + 5_000); r.frame(woltWaiting(at + 6_000)); r.frame(woltWaiting(at + 7_000))
        }
        r.frame(woltBan(t0 + 100_000)); r.frame(woltBan(t0 + 101_000))
        val p = r.of(EconomicsLogs.PENALTIES).single()
        assertEquals("offline_for_declines", p.getString("kind")); assertEquals(3, p.getInt("countInWindowAtTrigger"))
        // 21:46 Baku on the same day
        assertEquals(t0 + (3 * 60 + 46) * 60_000L, p.getLong("offlineUntilMs"))
        assertEquals("set offline until 21:46 for declining too many deliveries", p.getString("rawReasonRedacted"))
        val state = r.rec.courierState(t0 + 102_000, r.core, emptyList(), "unknown", null)
        assertEquals(t0 + (3 * 60 + 46) * 60_000L, state.getJSONObject("wolt").getLong("offlineUntilMs"))
        assertEquals("declines", state.getJSONObject("wolt").getString("offlineReason")); assertEquals(3, state.getJSONObject("wolt").getInt("declinesInWindow"))
        check(r.rows)
    }

    @Test fun scheduledOfflineStateIsNotTheQuickLink() {
        val r = Rig()
        r.frame(woltWaiting(t0, "Scheduled offline")); r.frame(woltOffline(t0 + 10_000, "Scheduled offline"))
        assertTrue(r.of(EconomicsLogs.PENALTIES).isEmpty())
        val state = obs(wolt, t0 + 20_000, "You're offline", "Scheduled offline until 22:00", "Go online")
        assertEquals(ScreenStage.OFFLINE, state.stage); assertTrue(EconomicsLogs.scheduledOffline(state))
        r.frame(state); r.frame(obs(wolt, t0 + 21_000, "You're offline", "Scheduled offline until 22:00", "Go online"))
        val p = r.of(EconomicsLogs.PENALTIES).single()
        assertEquals("scheduled_offline", p.getString("kind")); assertEquals(t0 + 4 * 3_600_000L, p.getLong("offlineUntilMs"))
        check(r.rows)
    }

    @Test fun performancePagesAreOneSnapshotPerVisit() {
        val r = Rig()
        r.frame(boltWaiting(t0))
        r.frame(obs(bolt, t0 + 1_000, "Performance", "This week", "Acceptance rate 92%"))
        r.frame(obs(bolt, t0 + 2_000, "Performance", "This week", "Acceptance rate 92%", "Completion rate", "98.5%", "Rating", "4.93", "Completed deliveries", "120"))
        r.frame(obs(bolt, t0 + 3_000, "Performance", "This week", "Acceptance rate 92%", "Completion rate", "98.5%", "Rating", "4.93", "Completed deliveries", "120"))
        assertTrue(r.of(EconomicsLogs.PENALTIES).isEmpty())
        r.frame(boltWaiting(t0 + 4_000))
        r.frame(obs(wolt, t0 + 5_000, "Your stats", "TODAY", "WEEK", "MONTH", "CUSTOM", "Deliveries completed", "4", "Earnings (estimate)", "AZN 16.79"))
        r.stop(t0 + 6_000)
        val (b, w) = r.of(EconomicsLogs.PENALTIES).let { it.single { x -> x.getString("platform") == "bolt" } to it.single { x -> x.getString("platform") == "wolt" } }
        assertEquals("performance_snapshot", b.getString("kind")); assertEquals(t0 + 1_000, b.getLong("observedAtMs"))
        assertEquals(0.92, b.getDouble("acceptanceRate"), 1e-12); assertEquals(0.985, b.getDouble("completionRate"), 1e-12)
        assertEquals(4.93, b.getDouble("rating"), 1e-12); assertEquals(120, b.getInt("tasksDone")); assertEquals("This week", b.getString("periodLabel"))
        assertEquals(16.79, w.getDouble("earningsAzn"), 1e-9); assertEquals(4, w.getInt("tasksDone")); assertTrue(w.isNull("periodLabel"))
        assertEquals(0.92, r.rec.courierState(t0, r.core, emptyList(), "unknown", null).getJSONObject("bolt").getDouble("acceptanceRate"), 1e-12)
        check(r.rows)
    }

    @Test fun declinesBannerOncePerShownNumber() {
        val r = Rig()
        for (i in 0 until 5) r.frame(woltWaiting(t0 + i * 1_000L, "1 deliveries declined"))
        r.frame(woltWaiting(t0 + 10_000, "2 deliveries declined")); r.frame(woltWaiting(t0 + 11_000, "2 deliveries declined"))
        assertEquals(listOf(1, 2), r.of(EconomicsLogs.PENALTIES).map { it.getInt("declinedCountShown") })
        check(r.rows)
    }

    // ---------------------------------------------------------------- economics-waits.jsonl v2

    @Test fun waitSpellRowsAreSegmentsWithAvailability() {
        val r = Rig()
        // A delivery ends 18:53; the waiting map is first seen 18:54 and the hour changes at 19:00.
        val base = t0 + 53 * 60_000L
        r.frame(woltOffer(base - 600_000)); r.click(wolt, "Accept", base - 598_000); r.frame(woltPickup(base - 590_000))
        r.click(wolt, "Collect", base - 300_000); r.frame(woltCard(base - 290_000, "Customer A")); r.click(wolt, "Order delivered!", base - 2_000)
        r.frame(woltWaiting(base)); r.frame(woltWaiting(base + 500))
        var at = base + 20_000
        while (at < t0 + 60 * 60_000L) { r.frame(woltWaiting(at)); at += 20_000 }
        r.zone = "Z3"
        while (at < t0 + 61 * 60_000L) { r.frame(woltWaiting(at)); at += 20_000 }
        r.frame(woltOffer(at))
        val rows = r.of(EconomicsLogs.WAITS)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.getInt("schemaVersion") == 2 && it.getString("spellId") == rows[0].getString("spellId") })
        assertEquals(listOf(0, 1), rows.map { it.getInt("segmentIndex") })
        assertEquals(listOf("context_changed", "visible_offer"), rows.map { it.getString("endReason") })
        assertEquals(listOf(false, true), rows.map { it.getBoolean("eventObserved") })
        assertEquals(listOf(18, 19), rows.map { it.getInt("bakuHour") }); assertEquals(listOf("B4", "B4"), rows.map { it.getString("hourBlock") })
        assertEquals(rows[0].getLong("endAtMs"), rows[1].getLong("startAtMs"))
        assertTrue(rows.all { it.getString("availableSinceSource") == "delivered_event" && it.getLong("availableSinceMs") == base })
        assertEquals("merkez", rows[0].getString("zone")); assertEquals("Z2", rows[0].getString("coarseZone")); assertEquals("Z3", rows[1].getString("coarseZone"))
        assertEquals(true, rows[0].getBoolean("boostActive"))
        assertEquals(rows[1].getLong("endAtMs") - rows[1].getLong("startAtMs"), rows[1].getLong("visibleMs"))
        assertTrue(r.rows.filter { it.file == EconomicsLogs.WAITS }.all { it.day == "2026-10-06" })
        check(r.rows)
    }

    @Test fun waitAcrossMidnightGoesToTheDayItStarted() {
        val r = Rig()
        val late = t0 + 5 * 3_600_000L + 59 * 60_000L + 30_000 // 23:59:30
        r.frame(woltWaiting(late)); r.frame(woltWaiting(late + 20_000)); r.frame(woltWaiting(late + 40_000)); r.frame(woltOffer(late + 50_000))
        val rows = r.rows.filter { it.file == EconomicsLogs.WAITS }
        assertEquals(2, rows.size); assertTrue(rows.all { it.day == "2026-10-06" })
        assertEquals(listOf(23, 0), rows.map { it.json.getInt("bakuHour") }); assertEquals(listOf("B5", "B0"), rows.map { it.json.getString("hourBlock") })
        assertEquals("first_idle_frame", rows[0].json.getString("availableSinceSource")); assertEquals(late, rows[0].json.getLong("availableSinceMs"))
        // a process restart writes the censored spell with the v1 end reason
        val w = WaitObservation(); w.observe(woltWaiting(late), "g3", "merkez", 23)
        val restarted = EconomicsRecorder().waits(WaitObservation().restoreCensored(w.snapshot(), "process_restart"))
        assertEquals("process_restart", restarted.single().json.getString("endReason"))
        check(r.rows + restarted)
    }

    // ---------------------------------------------------------------- feed v2 objects (SPEC 17.1)

    @Test fun feedV2OfferObjects() {
        val c = DeliveryCore(); c.start()
        c.observe(boltOffer(t0)); c.observe(woltOffer(t0 + 1_000))
        val b = EconomicsLogs.feedOfferV2(c.pending[bolt]!!, t0 + 2_000, null)!!
        assertFalse(b.getBoolean("dropoffKnown")); assertEquals(8, b.getJSONObject("shown").getInt("approachMin"))
        assertEquals(14, b.getJSONObject("shown").getInt("deliveryMin")); assertTrue(b.getBoolean("visibleNow"))
        assertEquals("accessibility", b.getJSONObject("parse").getString("feeSource")); assertEquals("high", b.getJSONObject("parse").getString("confidence"))
        val w = EconomicsLogs.feedOfferV2(c.pending[wolt]!!, t0 + 9_000, "trip-1")!!
        assertTrue(w.getBoolean("dropoffKnown")); assertEquals(2, w.getJSONObject("shown").getInt("stops")); assertFalse(w.getBoolean("visibleNow"))
        assertEquals("trip-1", w.getString("duringTripId")); assertEquals(t0 + 61_000, w.getLong("expiresAtMs"))
        assertNull(EconomicsLogs.feedOfferV2(OfferState("x", "com.example.app", 0, boltOffer(0)), 0, null))
        val unknown = EconomicsLogs.unknownCourierState()
        assertEquals(0, unknown.getJSONArray("appsOnline").length()); assertEquals("unknown", unknown.getString("hereZone"))
        assertEquals(60.0, unknown.getJSONObject("wolt").getDouble("windowMin"), 0.0); assertEquals("unknown", unknown.getJSONObject("bolt").getString("acceptanceRateSource"))
        c.observe(boltPickup(t0 + 3_000))
        val rec = EconomicsRecorder()
        val state = rec.courierState(t0 + 4_000, c, listOf(bolt, wolt, bolt), "Z2", 12.34)
        val trip = state.getJSONArray("activeTrips").getJSONObject(0)
        assertEquals("to_pickup", trip.getString("stage")); assertEquals("unknown", trip.getString("dropZone")); assertEquals(bolt, trip.getString("platformPackage"))
        assertEquals(listOf(bolt, wolt), (0 until 2).map { state.getJSONArray("appsOnline").getString(it) }); assertEquals(12.3, state.getDouble("speedKmh"), 0.0)
        for (s in listOf(b.toString(), w.toString(), state.toString())) for (secret in listOf("Naxcivani", "Test 2", "9RQW", "Izmir", "Cook Box")) assertFalse(s.contains(secret))
        dump(listOf("feed:offerV2" to b, "feed:offerV2" to w, "feed:courierState" to unknown, "feed:courierState" to state))
    }

    // ---------------------------------------------------------------- plumbing

    @Test fun newLogFilesAreInTheBackupList() {
        assertEquals(emptySet<String>(), EconomicsLogs.NEW_FILES.toSet() - BackupFiles.logNames)
        for (f in EconomicsLogs.NEW_FILES) assertEquals("2026-10-06/$f", BackupFiles.checkedPath("2026-10-06/$f"))
        assertTrue(EconomicsLogs.WAITS in BackupFiles.logNames)
    }

    @Test fun restoredOffersKeepTheCardValuesOfTheNewLogs() {
        val c = DeliveryCore(); c.start(); c.observe(boltOffer(t0, "Optional")); c.observe(boltOffer(t0 + 1_000))
        val o = c.pending[bolt]!!
        val back = RecordJson.offer(org.json.JSONObject(RecordJson.offer(o).toString()))
        assertTrue(back.optional); assertEquals(o.fieldSource, back.fieldSource); assertEquals(8, back.data.approachMin); assertEquals(14, back.data.deliveryMin)
        assertEquals(o.data.multipliers, back.data.multipliers); assertEquals(o.lastSeenAt, back.lastSeenAt)
        val waiting = RecordJson.observation(RecordJson.observation(woltWaiting(t0)))
        assertEquals("Medium", waiting.demand); assertEquals(50, waiting.bonusPct)
        // old rows without the new keys still load
        val old = RecordJson.offer(o).also { it.remove("optional"); it.remove("field_source"); it.getJSONObject("data").remove("approach_min") }
        val legacy = RecordJson.offer(old); assertFalse(legacy.optional); assertTrue(legacy.fieldSource.isEmpty()); assertNull(legacy.data.approachMin)
    }

    @Test fun timelineAndOdometer() {
        val t = Timeline<String>()
        t.record(10, "Z1"); t.record(20, "Z1"); t.record(30, "Z2"); t.record(25, "Z3")
        assertNull(t.at(5)); assertEquals("Z1", t.at(24)); assertEquals("Z3", t.at(26)); assertEquals("Z2", t.at(99))
        assertEquals(listOf("Z1", "Z3", "Z2"), t.during(15, 40))
        val o = Odometer()
        assertNull(o.km(0, 100))
        o.add(1_000, 100.0); o.add(2_000, 50.0); o.add(20_000, 300.0)
        assertEquals(0.15, o.km(0, 10_000)!!, 1e-9); assertEquals(0.3, o.km(10_000, 30_000)!!, 1e-9); assertEquals(450.0, o.total, 0.0)
        assertEquals(t0 + 13 * 60_000L + 5 * 60_000L * 0, EconomicsLogs.clockToMs("18:13", t0))
        assertEquals(t0 + 24 * 3_600_000L - 60 * 60_000L, EconomicsLogs.clockToMs("17:00", t0)); assertNull(EconomicsLogs.clockToMs("25:00", t0))
    }
}
