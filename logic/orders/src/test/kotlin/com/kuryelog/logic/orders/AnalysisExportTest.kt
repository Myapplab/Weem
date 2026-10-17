package com.kuryelog.logic.orders

// Rawstats AnalysisExportTest.kt @ 6388c04 (13 test), aynı girdi → aynı çıktı; yalnız paket ve içe aktarmalar WEEM'in.

import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.capture.DeliveryDetails
import com.kuryelog.logic.capture.NoteFields
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.Redactor

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/**
 * Anonymous analysis export (WP3): a synthetic day built with the real row builders
 * (RecordJson.trip/offer/observation; the Store.event, NotifService and WatchService row shapes)
 * must leave no customer string and no unrounded coordinate, keep fees/times/km and restaurant
 * data, and drop unknown files.
 */
class AnalysisExportTest {
    private val day = "2026-10-06"
    private val t0 = 1_791_273_600_000L // 2026-10-06 12:00 Baku
    private val wolt = "com.wolt.courierapp"
    private val bolt = "ee.mtakso.courier"
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Baku") }
    private fun ts(at: Long) = tsFmt.format(Date(at))
    private fun loc(lat: Double = 40.3951234, lon: Double = 49.8523456) = JSONObject().put("lat", lat).put("lon", lon).put("acc", 7.5).put("age_s", 2)

    /** Customer data placed into the synthetic day; none of it may survive (case-insensitive). */
    private val secrets = listOf("Aysel", "Testova", "Alici", "Sintetik", "Mehdi Huseyn", "Meet at my door", "Zəngi", "uşaq yatır", "BLOCK B", "4826",
        "123 45 67", "0551234567", "8P6D2", "#014", "Mirzəağa", "qapıdayam", "Litvin",
        "40.4093", "49.8671", "40.3779", "49.8462", "40.4012", "49.8512", "40.3951", "49.8523")
    private val forbiddenKeys = setOf("details", "customer_address", "raw", "nodes", "title", "big", "sub", "key", "door_code", "apartment", "address_details",
        "instructions", "floor", "entrance", "building", "check_id", "phone", "name", "items")

    // ---- the synthetic day, built like the app builds it ----
    private val woltOfferLines = listOf("Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000",
        "Customer drop-off", "Mirzəağa Əliyev 249, Bakı, 1014", "Accept")
    private val woltDropLines = listOf("Dropoff to", "Aysel Testova", "Sintetik kuce 81, Bakı", "Notes", "BLOCK B\n(Domofon kodu: 4826)", "Mertebe 6, tel 055 123 45 67",
        "Translate", "Order details", "From", "Izmir Doner Tantuni No1", "#014", "Order delivered!")
    private val boltDropLines = listOf("Drop off in -1 min", "Alici A. #8P6D2", "Items (1)", "Address", "Mehdi Huseyn 40, Baku", "Instructions", "Meet at my door",
        "Floor", "3", "Arrived", "Slide to confirm")
    private val woltPickupLines = listOf("Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes", "Restoran həyətin içindədir", "Translate",
        "Collect order", "#014", "Aysel T.", "ORDER DETAILS", "1 x Test", "Collect")

    private val offerObs = CourierParser.parse(wolt, woltOfferLines, listOf("Accept", "Decline"), t0)
    private val offer = OfferState("offer-$t0-1", wolt, t0, offerObs, 64, 40.3779871, 49.8462345, 12.5, 3200L, 55.0, t0 - 60_000, "manual").also {
        it.sessionId = "wolt-$t0"; it.lastSeenAt = t0 + 9_000
    }
    private val dropObs = CourierParser.parse(wolt, woltDropLines, emptyList(), t0 + 1_500_000)
    private val trip = TripRecord("trip-${t0 + 12_000}-2", wolt, offer, t0 + 12_000, TripPhase.FINISHED, expectedOrders = 2, completedAt = t0 + 2_400_000,
        distanceM = 5321.7, tip = 1.0, commissionPct = 20.0, latest = dropObs, deliverSeenFor = "name:aysel testova", deliverSeenAt = t0 + 2_390_000).also { t ->
        t.sessionId = "wolt-$t0"
        t.pickups.add(PickupRecord("Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000", t0 + 180_000, t0 + 600_000, t0 + 900_000, "screen", t0 + 700_000).also {
            it.note = "Restoran həyətin içindədir, zəng: 055 123 45 67"; it.code = "#014"; it.items = listOf("1 x Tantuni", "2 x Ayran")
            it.readyHintAt = t0 + 650_000; it.preparingAt = t0 + 610_000; it.reminderAt = t0 + 620_000
        })
        t.customers["#8P6D2"] = CustomerRecord("#8P6D2", DeliveryDetails(name = "Alici A.", address = "Mehdi Huseyn 40, Baku", instructions = "Meet at my door", floor = "3"),
            arrivedAt = t0 + 1_400_000, deliveredAt = t0 + 1_450_000, lat = 40.4012345, lon = 49.8512345, coordinateSource = "android_geocoder_address_estimate",
            detailsAt = t0 + 1_300_000, deadlineClock = 734)
        t.customers["name:aysel testova"] = CustomerRecord("name:aysel testova", DeliveryDetails(name = "Aysel Testova", address = "Sintetik kuce 81, Bakı",
            note = "Zəngi basmayın, uşaq yatır", entrance = "2", floor = "6", apartment = "33", doorCode = "4826", addressDetails = "BLOCK B (Domofon kodu: 4826)",
            phone = "0551234567"), arrivedAt = t0 + 2_300_000, deliveredAt = t0 + 2_390_000, lat = 40.4093123, lon = 49.8671456,
            coordinateSource = "android_geocoder_address_estimate", detailsAt = t0 + 2_000_000)
        t.currentCustomer = "name:aysel testova"
    }
    private val boltOffer = OfferState("offer-${t0 + 3_000_000}-3", bolt, t0 + 3_000_000,
        CourierParser.parse(bolt, listOf("Decline", "22 min, 4,49 ₼", "Customer drop-off", "Litvin Babayev 81, Bakı"), listOf("22 min, 4,49 ₼"), t0 + 3_000_000), 61,
        40.3951234, 49.8523456, 8.0, 1200L)

    /** OrderTracker.process row. */
    private fun orderRow(type: String, at: Long, pkg: String, offer: OfferState?, trip: TripRecord?, reason: String? = null): JSONObject {
        val j = JSONObject().put("t", ts(at)).put("type", type).put("pkg", pkg).put("offer_id", offer?.id).put("id", trip?.id).put("reason", reason)
            .put("session_id", trip?.sessionId ?: offer?.sessionId)
        trip?.let { j.put("record", RecordJson.trip(it)); j.put("gross", it.gross); j.put("net", it.net); j.put("total_min", it.actualMinutes); j.put("wait_min", it.waitMinutes); j.put("km", it.distanceM / 1000); j.put("restaurant", it.pickups.firstOrNull()?.restaurant) }
        offer?.let { j.put("offer", RecordJson.offer(it)) }
        j.put("loc", loc()); j.put("phone_battery_pct", 63)
        return j
    }
    /** Store.event row. */
    private fun event(type: String, at: Long, extra: JSONObject? = null): JSONObject { val o = extra ?: JSONObject(); o.put("t", ts(at)); o.put("type", type); o.put("loc", loc()); return o }
    /** WatchService TYPE_VIEW_CLICKED row: the labels of the clicked node's subtree, distinct, joined with spaces (not redacted). */
    private fun click(at: Long, pkg: String, labels: List<String>) = event("click", at, JSONObject().put("pkg", pkg).put("text", labels.distinct().joinToString(" ").trim()))

    /** A posted notification: NotifService writes its notifs.jsonl row and, through OrderTracker.notification, a notification_hint event. */
    private class Posted(val at: Long, val pkg: String, val title: String?, val text: String?, val big: String?, val sub: String?)
    private val posted = listOf(
        Posted(t0 - 5_000, wolt, "Wolt Courier", "New order request", "You have a new delivery task ₼3.94", null),
        Posted(t0 + 700_000, wolt, "Izmir Doner Tantuni No1", "Order is ready for pickup", null, null),
        Posted(t0 + 2_200_000, wolt, "Aysel Testova", "Mən qapıdayam, 055 123 45 67", "Mən qapıdayam. Sintetik kuce 81, Bakı", "Chat"))
    /** NotifService row (posted = sbn.postTime). */
    private fun notif(p: Posted) = JSONObject().put("t", ts(p.at)).put("posted", ts(p.at - 100)).put("ev", "posted")
        .put("pkg", p.pkg).put("key", "0|${p.pkg}|17|null|10234").put("ongoing", false).put("channel", "orders").put("title", NoteFields.redact(p.title))
        .put("text", NoteFields.redact(p.text)).put("big", NoteFields.redact(p.big)).put("sub", NoteFields.redact(p.sub)).put("loc", loc())
    /** OrderTracker.notification row from NotifService: title/text/big/sub joined, NoteFields.redact (phones only), observed_at = sbn.postTime. */
    private fun notifHint(p: Posted) = event("notification_hint", p.at, JSONObject().put("pkg", p.pkg)
        .put("text", NoteFields.redact(listOfNotNull(p.title, p.text, p.big, p.sub).distinct().joinToString("\n"))).put("observed_at", p.at - 100))
    /** OrderTracker.notification row from WatchService (TYPE_NOTIFICATION_STATE_CHANGED: the event texts joined). */
    private fun watchHint(at: Long, pkg: String, texts: List<String>) = event("notification_hint", at, JSONObject().put("pkg", pkg)
        .put("text", NoteFields.redact(texts.joinToString("\n"))).put("observed_at", at))
    /** WatchService.accept row. */
    private fun screen(at: Long, pkg: String, texts: List<String>, actions: List<String>, source: String = "ocr"): JSONObject {
        val obs = CourierParser.parse(pkg, texts, actions, at, source)
        val nodes = JSONArray(texts.map { JSONArray().put(it).put("").put("$pkg:id/text").put("android.widget.TextView").put("0 0 100 50").put(false) })
        return JSONObject().put("t", ts(at)).put("pkg", pkg).put("window", 42).put("bounds", "0 0 1080 2400").put("source", source).put("offerLike", obs.stage == ScreenStage.OFFER)
            .put("stage", obs.stage.name).put("texts", JSONArray(texts.map { NoteFields.redact(it) })).put("actions", JSONArray(actions.map { NoteFields.redact(it) }))
            .put("nodes", if (source == "accessibility") nodes else null).put("phone_battery_pct", 63).put("loc", loc())
    }
    private fun jsonl(rows: List<JSONObject>) = rows.joinToString("\n", postfix = "\n") { it.toString() }

    private val panelLines = listOf("● 12.4 km", "•12.4km", "Wolt · Aysel Testova · Müşteriye gidiliyor", "Wolt · 2.31₼ · net 1.85₼ · Izmir Doner Tantuni No1",
        "Bugün: 3 sipariş", "Bu gün: 2 sifariş", "Bolt Food · Sipariş bekleniyor", "Wolt • 3.94 AZN • Teklif", "Bolt Food · Oflayn", "Wolt · Sifariş · Restorana gedilir")

    private fun input(): LinkedHashMap<String, String> {
        val screens = listOf(
            screen(t0, wolt, woltOfferLines.take(7) + panelLines[3] + woltOfferLines.drop(7) + panelLines[0], listOf("Accept", "Decline")),
            screen(t0 + 600_000, wolt, listOf(panelLines[1], panelLines[7]) + woltPickupLines, listOf("Collect"), "accessibility"),
            // A panel line between "Dropoff to" and the name must not shift the label modes.
            screen(t0 + 1_500_000, wolt, listOf(panelLines[0], "Bugün: 3 sipariş") + woltDropLines.take(1) + panelLines[2] + woltDropLines.drop(1), emptyList()),
            screen(t0 + 1_400_000, bolt, boltDropLines + panelLines[6] + panelLines[5] + panelLines[8] + panelLines[9], listOf("Slide to confirm")),
            screen(t0 + 1_410_000, bolt, listOf("${panelLines[0]}\nWolt · Aysel Testova · Müşteriye gidiliyor\nBugün: 3 sipariş"), emptyList()),
            // A clickable drop-off card: WatchService.walk puts every text of the card into actions too.
            screen(t0 + 1_600_000, wolt, woltDropLines, woltDropLines, "accessibility"),
            screen(t0 + 1_610_000, bolt, boltDropLines + panelLines[6], boltDropLines + panelLines[6] + listOf("Complete delivery", "Alici A. Mehdi Huseyn 40"), "accessibility"))
        val orders = listOf(
            orderRow("offer", t0, wolt, offer, null),
            orderRow("accepted", t0 + 12_000, wolt, offer, trip),
            orderRow("delivered", t0 + 2_400_000, wolt, null, trip),
            JSONObject().put("t", ts(t0 + 2_410_000)).put("type", "record_snapshot").put("record", RecordJson.trip(trip)),
            orderRow("offer", t0 + 3_000_000, bolt, boltOffer, null),
            orderRow("rejected", t0 + 3_030_000, bolt, boltOffer, null, "offer_to_idle_map"))
        val events = listOf(
            event("shift_start", t0 - 600_000, JSONObject().put("auto", false)),
            click(t0 + 10_000, wolt, listOf("Accept")),
            click(t0 + 1_420_000, bolt, listOf("Order #8P6D2", "Call", "055 123 45 67")),
            // Card clicks: the subtree's labels carry the customer's name and address.
            click(t0 + 1_430_000, wolt, listOf("Dropoff to", "Aysel Testova", "Sintetik kuce 81, Bakı", "Call")),
            click(t0 + 3_010_000, bolt, listOf("Customer drop-off", "Litvin Babayev 81, Bakı", "22 min, 4,49 ₼")),
            click(t0 + 3_020_000, bolt, listOf("22 min, 4,49 ₼", "22 min, 4,49 ₼")),
            click(t0 + 2_390_000, wolt, listOf("Slide to confirm", "Order delivered")),
            watchHint(t0 + 700_000, wolt, listOf("Order #8P6D2 is ready for pickup")),
            watchHint(t0 + 2_200_050, wolt, listOf("Aysel Testova", "Mən qapıdayam, 055 123 45 67")),
            event("customer_coordinate_unavailable", t0 + 2_000_500, JSONObject().put("trip", trip.id).put("customer", "name:aysel testova")),
            event("cancellation_user_reported", t0 + 3_100_000, JSONObject().put("pkg", bolt).put("platformActionPerformed", false)),
            event("screenshot_unavailable", t0 + 3_200_000, JSONObject().put("pkg", wolt).put("code", 3)),
            event("gps_error", t0 + 3_300_000, JSONObject().put("err", "java.lang.SecurityException: Aysel Testova")),
            event("gps_disabled", t0 + 3_400_000),
            event("debug_dump", t0 + 3_500_000, JSONObject().put("pkg", wolt).put("text", "Aysel Testova Sintetik kuce 81")),
            event("shift_end", t0 + 3_600_000, JSONObject().put("km", 42.3).put("min", 360.5))) + posted.map(::notifHint)
        val notifs = posted.map(::notif) + JSONObject().put("t", ts(t0 + 2_300_000)).put("ev", "removed").put("pkg", wolt).put("key", "0|$wolt|17|null|10234")
        val gps = "t,lat,lon,acc_m,speed_ms,bearing,alt_m\n" + listOf(0L, 2_000L, 4_000L).joinToString("") { d ->
            "${ts(t0 + d)},${40.40931234 + d / 1e9},${49.86714561 - d / 1e9},4.5,3.2,182.0,12.0\n" }
        val errors = jsonl(listOf(JSONObject().put("t", ts(t0 + 50)).put("source", "database")
            .put("error", "database: SQLiteException: near 055 123 45 67 " + "x".repeat(300))))
        return linkedMapOf(
            "$day/screens.jsonl" to jsonl(screens) + "{not json\n\n",
            "$day/orders.jsonl" to jsonl(orders),
            "$day/events.jsonl" to jsonl(events),
            "$day/notifs.jsonl" to jsonl(notifs),
            "$day/gps.csv" to gps,
            "$day/errors.jsonl" to errors,
            "$day/platforms.jsonl" to jsonl(listOf(JSONObject().put("pkg", wolt).put("stage", "IDLE").put("demand", "high").put("bonus_pct", 20).put("bonus_from", "18:00")
                .put("multipliers", JSONArray(listOf(1.2))).put("t", ts(t0 - 1_000)).put("loc", loc().put("provider", "fused")))),
            "$day/economics-waits.jsonl" to jsonl(listOf(JSONObject().put("schemaVersion", 1).put("pkg", wolt).put("startAtMs", t0 - 300_000).put("endAtMs", t0)
                .put("durationMinutes", 5.0).put("eventObserved", true).put("cause", "after_delivery_observed").put("endReason", "offer").put("demand", "unknown")
                .put("vehicleMode", "g3").put("zone", "unknown").put("bakuHour", 12).put("dispatchAvailabilityVerified", false))),
            "$day/economics-exposure.jsonl" to jsonl(listOf(JSONObject().put("at", t0).put("pkg", wolt).put("stage", "OFFER").put("complete", true).put("source", "ocr")
                .put("demand", "unknown").put("vehicleMode", "g3").put("zone", "unknown").put("bakuHour", 12).put("dispatchAvailabilityVerified", false))),
            "$day/online-sessions.jsonl" to jsonl(listOf(JSONObject().put("schemaVersion", 2).put("pkg", wolt).put("sessionId", "wolt-$t0").put("startAtMs", t0 - 600_000)
                .put("endAtMs", t0 + 3_600_000).put("offers", 2).put("t", ts(t0 + 3_600_000)))),
            "$day/scooter.jsonl" to jsonl(listOf(JSONObject().put("t", ts(t0)).put("battery_pct", 55.0).put("lat", 40.40123456).put("lon", 49.85123456))),
            "restaurants.json" to JSONObject().put("izmir doner tantuni no1", JSONObject().put("n", 3).put("name", "Izmir Doner Tantuni No1")
                .put("address", "9RQW+344 Baku, Baku, AZ1000").put("wait_min", 4.5)).toString(),
            "$day/debug.txt" to "Aysel Testova 055 123 45 67\n",
            "$day/screens.jsonl.bak" to jsonl(screens),
            "notes.txt" to "Aysel Testova\n",
            "$day/sub/screens.jsonl" to jsonl(screens),
            "../$day/orders.jsonl" to jsonl(orders))
    }

    private val export = AnalysisExport("fixed-test-secret".toByteArray(Charsets.UTF_8))
    private val inputs = input()
    private val out = export.texts(inputs, "0.15.0", t0 + 4_000_000)
    private fun rows(name: String) = out.getValue("$day/$name").lineSequence().filter { it.isNotBlank() }.map { JSONObject(it) }.toList()
    private fun inRows(name: String) = inputs.getValue("$day/$name").lineSequence().filter { it.isNotBlank() }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList()

    private fun walk(v: Any?, path: String, visit: (String, String, Any?) -> Unit) {
        when (v) {
            is JSONObject -> { val it = v.keys(); while (it.hasNext()) { val k = it.next(); visit(path, k, v.opt(k)); walk(v.opt(k), "$path.$k", visit) } }
            is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), "$path[$i]", visit)
        }
    }
    private fun decimals(d: Double) = java.math.BigDecimal(d.toString()).stripTrailingZeros().scale()

    @Test fun syntheticDayLeavesNoCustomerString() {
        val blob = out.values.joinToString("\n")
        for (s in secrets) assertFalse("customer data left: $s", blob.contains(s, ignoreCase = true))
        // The input really contained them (the test would be empty otherwise); RecordJson never writes the phone field itself.
        val raw = inputs.values.joinToString("\n")
        for (s in secrets - "0551234567") assertTrue("synthetic day lacks $s", raw.contains(s, ignoreCase = true))
    }

    @Test fun syntheticDayHasOnlyAllowedKeysAndRoundedCoordinates() {
        for ((path, text) in out) {
            if (!path.endsWith(".jsonl")) continue
            for (line in text.lineSequence().filter { it.isNotBlank() }) {
                val j = JSONObject(line) // valid JSON line
                walk(j, path) { at, k, v ->
                    assertFalse("$at.$k", k in forbiddenKeys)
                    if (k == "lat" || k == "lon") assertTrue("$at.$k=$v", v is Number && decimals((v as Number).toDouble()) <= 3)
                    if (k == "loc") assertTrue("$at.loc=$v", v is JSONObject && v.keys().asSequence().all { it in setOf("lat", "lon", "acc", "age_s") })
                    if (k == "text") assertTrue("$at.text", path.endsWith("events.jsonl") && j.getString("type") == "click")
                    if (k == "hint") assertTrue("$at.hint=$v", v in setOf("ready", "offer", "other"))
                    if (k == "customer") assertTrue("$at.customer=$v", v is String && (v as String).startsWith("name:"))
                }
            }
        }
        val gps = out.getValue("$day/gps.csv").lines().filter { it.isNotBlank() }
        assertEquals("t,lat,lon,acc_m,speed_ms,bearing,alt_m", gps[0])
        assertEquals(4, gps.size)
        for (row in gps.drop(1)) { val f = row.split(','); assertEquals(7, f.size); assertTrue(row, decimals(f[1].toDouble()) <= 3 && decimals(f[2].toDouble()) <= 3)
            assertEquals("4.5", f[3]); assertEquals("12.0", f[6]) }
        assertEquals("40.409", gps[1].split(',')[1]); assertEquals("49.867", gps[1].split(',')[2])
        val loc = rows("platforms.jsonl").single().getJSONObject("loc")
        assertEquals(40.395, loc.getDouble("lat"), 0.0); assertEquals(49.852, loc.getDouble("lon"), 0.0); assertEquals(7.5, loc.getDouble("acc"), 0.0)
        assertEquals(2, loc.getInt("age_s")); assertFalse(loc.has("provider"))
    }

    @Test fun feesTimesKmAndRestaurantDataAreUnchanged() {
        val inOrders = inRows("orders.jsonl"); val outOrders = rows("orders.jsonl")
        assertEquals(inOrders.size, outOrders.size)
        for ((a, b) in inOrders.zip(outOrders)) {
            for (k in listOf("t", "type", "pkg", "offer_id", "id", "reason", "session_id", "gross", "net", "total_min", "wait_min", "km", "restaurant", "phone_battery_pct"))
                assertEquals("$k of ${a.opt("type")}", a.opt(k)?.toString(), b.opt(k)?.toString())
            a.optJSONObject("offer")?.let { o -> val p = b.getJSONObject("offer")
                for (k in listOf("id", "pkg", "day", "at", "battery_pct", "gps_accuracy_m", "gps_age_ms", "scooter_battery_pct", "session_id", "last_seen_at")) assertEquals(k, o.opt(k)?.toString(), p.opt(k)?.toString())
                assertEquals(AnalysisExport.round3(o.getDouble("lat")), p.getDouble("lat"), 0.0)
                val d = o.getJSONObject("data"); val e = p.getJSONObject("data")
                for (k in listOf("pkg", "at", "stage", "complete", "source", "gross", "km", "min_lo", "min_hi", "stops", "restaurant", "restaurant_address", "ready_in"))
                    assertEquals("data.$k", d.opt(k)?.toString(), e.opt(k)?.toString())
                assertFalse(e.has("raw")); assertFalse(e.has("customer")); assertFalse(e.has("customer_address")); assertFalse(e.has("actions"))
            }
            a.optJSONObject("record")?.let { r -> val s = b.getJSONObject("record")
                for (k in listOf("id", "pkg", "day", "accepted_at", "phase", "completed_at", "expected_orders", "distance_m", "tip", "commission_pct", "price_is_net",
                    "missed_offer", "deliver_seen_at", "session_id")) assertEquals("record.$k", r.opt(k)?.toString(), s.opt(k)?.toString())
                val p = r.getJSONArray("pickups").getJSONObject(0); val q = s.getJSONArray("pickups").getJSONObject(0)
                for (k in listOf("restaurant", "address", "ready_at", "arrived_at", "picked_at", "arrival_source", "ready_observed_at", "ready_hint_at", "preparing_at", "reminder_at"))
                    assertEquals("pickup.$k", p.opt(k)?.toString(), q.opt(k)?.toString())
                assertEquals(2, q.getInt("itemsCount")); assertEquals("Restoran həyətin içindədir, zəng: [tel]", q.getString("note"))
                assertTrue(q.getString("code").matches(Regex("#[0-9]{3}")))
                val c = s.getJSONArray("customers")
                assertEquals(2, c.length())
                assertEquals(r.getJSONArray("customers").getJSONObject(0).getLong("delivered_at"), c.getJSONObject(0).getLong("delivered_at"))
                assertEquals(734, c.getJSONObject(0).getInt("deadline_clock"))
                assertEquals(40.409, c.getJSONObject(1).getDouble("lat"), 0.0); assertEquals(49.867, c.getJSONObject(1).getDouble("lon"), 0.0)
                assertEquals(s.getString("current_customer"), c.getJSONObject(1).getString("id"))
                assertEquals(s.getString("deliver_seen_for"), c.getJSONObject(1).getString("id"))
                assertTrue(s.has("latest") && !s.getJSONObject("latest").has("raw"))
            }
        }
        val delivered = outOrders.first { it.getString("type") == "delivered" }
        assertEquals(2.31, delivered.getDouble("gross"), 1e-9); assertEquals(2.31 * 0.8 + 1.0, delivered.getDouble("net"), 1e-9)
        assertEquals(5.3217, delivered.getDouble("km"), 1e-9); assertEquals(39.8, delivered.getDouble("total_min"), 1e-9); assertEquals(5.0, delivered.getDouble("wait_min"), 1e-9)
        // Files without customer data by design keep every key (coordinates still rounded).
        for (name in listOf("economics-waits.jsonl", "economics-exposure.jsonl", "online-sessions.jsonl"))
            assertEquals(name, inRows(name).single().toString().length, rows(name).single().toString().length)
        assertEquals(inRows("economics-waits.jsonl").single().getDouble("durationMinutes"), rows("economics-waits.jsonl").single().getDouble("durationMinutes"), 0.0)
        val scooter = rows("scooter.jsonl").single()
        assertEquals(55.0, scooter.getDouble("battery_pct"), 0.0); assertEquals(40.401, scooter.getDouble("lat"), 0.0); assertEquals(49.851, scooter.getDouble("lon"), 0.0)
        assertEquals(JSONObject(inputs.getValue("restaurants.json")).toString(), JSONObject(out.getValue("restaurants.json")).toString())
    }

    @Test fun screensDropPanelLinesAndNodesAndKeepCourierAppText() {
        val s = rows("screens.jsonl")
        assertEquals(7, s.size) // the broken line is dropped
        fun list(r: JSONObject, k: String) = r.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
        val all = s.flatMap { list(it, "texts") }
        for (p in panelLines) assertFalse(p, all.contains(p) || s.any { list(it, "actions").contains(p) })
        assertTrue(all.none { it.contains("Bugün") || it.contains("Bu gün") || it.contains(" · ") || it.contains("km") && it.startsWith("●") })
        for (keep in listOf("₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000", "Accept", "Decline", "Dropoff to",
            "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Collect order", "Drop off in -1 min", "Slide to confirm")) assertTrue(keep, all.contains(keep))
        // Same lines as redact.py gives without the panel (label modes not shifted by the panel line after "Dropoff to").
        val drop = s[2].getJSONArray("texts")
        val expected = Redactor("fixed-test-secret".toByteArray(Charsets.UTF_8)).lines(wolt, woltDropLines.map { NoteFields.redact(it)!! })
        assertEquals(expected, (0 until drop.length()).map { drop.getString(it) })
        assertTrue(drop.getString(1).startsWith("Name")); assertTrue(drop.getString(2).startsWith("[addr:"))
        val offerTexts = s[0].getJSONArray("texts")
        assertTrue(offerTexts.getString(7).startsWith("[addr:"))
        assertEquals(0, s[4].getJSONArray("texts").length())
        for (r in s) {
            assertFalse(r.has("nodes"))
            for (k in listOf("t", "pkg", "window", "bounds", "source", "offerLike", "stage", "phone_battery_pct", "loc")) assertTrue(k, r.has(k))
        }
        assertEquals("Slide to confirm", s[3].getJSONArray("actions").getString(0))
        assertEquals(listOf("Accept", "Decline"), list(s[0], "actions")); assertEquals(listOf("Collect"), list(s[1], "actions"))
        // Actions of a clickable card are exported as the texts show them (name, address and note redacted, labels kept).
        assertEquals(list(s[5], "texts"), list(s[5], "actions"))
        assertEquals(expected, list(s[5], "texts"))
        // Panel lines leave actions too; a known action outside texts is kept, any other one becomes [card].
        assertEquals(list(s[6], "texts") + listOf("Complete delivery", AnalysisExport.CARD), list(s[6], "actions"))
        assertTrue(list(s[6], "actions").containsAll(listOf("Address", "Instructions", "[note]", "Floor", "[n]", "Arrived", "Slide to confirm")))
    }

    @Test fun eventsKeepOnlyTheirAllowlist() {
        val ev = rows("events.jsonl")
        fun keys(j: JSONObject) = j.keys().asSequence().toSet()
        fun one(type: String) = ev.single { it.getString("type") == type }
        val base = setOf("t", "type", "loc")
        assertEquals(base + "auto", keys(one("shift_start")))
        assertEquals(base + setOf("km", "min"), keys(one("shift_end")))
        assertEquals(42.3, one("shift_end").getDouble("km"), 0.0); assertEquals(360.5, one("shift_end").getDouble("min"), 0.0)
        // A click keeps its text only when the whole text is a known label or action; card clicks (order id, customer name,
        // address, phone among the subtree's labels) become [card].
        val clicks = ev.filter { it.getString("type") == "click" }
        for (c in clicks) assertEquals(base + setOf("pkg", "text"), keys(c))
        assertEquals(listOf("Accept", "[card]", "[card]", "[card]", "22 min, 4,49 ₼", "Slide to confirm Order delivered"), clicks.map { it.getString("text") })
        // notification_hint: the joined notification text becomes the notifs.jsonl hint (WatchService rows, then one NotifService row per posted notification).
        val hints = ev.filter { it.getString("type") == "notification_hint" }
        for (h in hints) assertEquals(base + setOf("pkg", "hint", "observed_at"), keys(h))
        assertEquals(listOf("ready", "other", "offer", "ready", "other"), hints.map { it.getString("hint") })
        assertEquals(listOf(t0 + 700_000, t0 + 2_200_050) + posted.map { it.at - 100 }, hints.map { it.getLong("observed_at") })
        assertEquals(rows("notifs.jsonl").filter { it.getString("ev") == "posted" }.map { it.getString("hint") }, hints.drop(2).map { it.getString("hint") })
        val coord = rows("events.jsonl").single { it.getString("type") == "customer_coordinate_unavailable" }
        assertEquals(base + setOf("trip", "customer"), keys(coord)); assertEquals(trip.id, coord.getString("trip"))
        assertEquals(base + setOf("pkg", "platformActionPerformed"), keys(rows("events.jsonl").single { it.getString("type") == "cancellation_user_reported" }))
        for (t in listOf("screenshot_unavailable", "debug_dump")) assertEquals(t, base + "pkg", keys(rows("events.jsonl").single { it.getString("type") == t }))
        for (t in listOf("gps_error", "gps_disabled")) assertEquals(t, base, keys(rows("events.jsonl").single { it.getString("type") == t }))
        // The same order id gets the same pseudonym in screens and records of one export.
        val customers = rows("orders.jsonl").first { it.getString("type") == "delivered" }.getJSONObject("record").getJSONArray("customers")
        val id = customers.getJSONObject(0).getString("id")
        assertTrue(id, id.matches(Regex("#[0-9][A-Z][0-9][A-Z][0-9]")))
        assertTrue(out.getValue("$day/screens.jsonl").contains(" $id\""))
        assertEquals(coord.getString("customer"), customers.getJSONObject(1).getString("id"))
    }

    @Test fun notificationsKeepNoTextAndGetAHint() {
        val n = rows("notifs.jsonl")
        assertEquals(listOf("offer", "ready", "other", "other"), n.map { it.getString("hint") })
        for (r in n) for (k in listOf("title", "text", "big", "sub", "key")) assertFalse(k, r.has(k))
        assertEquals(setOf("t", "posted", "ev", "pkg", "ongoing", "channel", "loc", "hint"), n[0].keys().asSequence().toSet())
        assertEquals("removed", n[3].getString("ev"))
        assertEquals("ready", AnalysisExport.notificationHint("Ready in 5 min"))
        assertEquals("ready", AnalysisExport.notificationHint("Sifariş #408 hazırdır"))
        assertEquals("offer", AnalysisExport.notificationHint("Yeni təklif"))
        assertEquals("offer", AnalysisExport.notificationHint("AZN 4.49"))
        assertEquals("offer", AnalysisExport.notificationHint("Новый заказ"))
        assertEquals("other", AnalysisExport.notificationHint("You are online"))
        assertEquals("other", AnalysisExport.notificationHint(""))
    }

    @Test fun errorsAreMaskedAndClipped() {
        val e = rows("errors.jsonl").single()
        assertEquals(setOf("t", "source", "error"), e.keys().asSequence().toSet())
        assertEquals(200, e.getString("error").length)
        assertTrue(e.getString("error").startsWith("database: SQLiteException: near[tel] x"))
    }

    @Test fun unknownFilesAreExcludedAndTheMarkerDescribesTheExport() {
        assertEquals(listOf("$day/screens.jsonl", "$day/orders.jsonl", "$day/events.jsonl", "$day/notifs.jsonl", "$day/gps.csv", "$day/errors.jsonl",
            "$day/platforms.jsonl", "$day/economics-waits.jsonl", "$day/economics-exposure.jsonl", "$day/online-sessions.jsonl", "$day/scooter.jsonl",
            "restaurants.json", AnalysisExport.MARKER), out.keys.toList())
        for (p in listOf("$day/debug.txt", "$day/screens.jsonl.bak", "notes.txt", "$day/sub/screens.jsonl", "../$day/orders.jsonl", "/$day/orders.jsonl",
            "$day\\orders.jsonl", "2026-1-06/orders.jsonl", "$day/courier.db")) assertNull(p, AnalysisExport.kindOf(p))
        assertNull(export.text("$day/debug.txt", "x"))
        assertEquals(AnalysisExport.Kind.KEEP_ALL, AnalysisExport.kindOf("$day/economics-offers.jsonl"))
        assertEquals(AnalysisExport.Kind.KEEP_ALL, AnalysisExport.kindOf("$day/scooter_events.jsonl"))
        val m = JSONObject(out.getValue(AnalysisExport.MARKER))
        assertEquals(setOf("schema", "app", "versionName", "createdAtMs", "days", "files", "redaction"), m.keys().asSequence().toSet())
        assertEquals("kuryeplus-analysis-export/1", m.getString("schema")); assertEquals("com.kuryelog.app", m.getString("app"))
        assertEquals("0.15.0", m.getString("versionName")); assertEquals(t0 + 4_000_000, m.getLong("createdAtMs"))
        assertEquals(listOf(day), (0 until m.getJSONArray("days").length()).map { m.getJSONArray("days").getString(it) })
        val r = m.getJSONObject("redaction")
        assertEquals(3, r.getInt("locDecimals")); assertFalse(r.getBoolean("secretStored")); assertTrue(r.getBoolean("panelLinesRemoved"))
        val files = m.getJSONObject("files")
        assertEquals(12, files.length())
        assertEquals(7, files.getInt("$day/screens.jsonl")); assertEquals(6, files.getInt("$day/orders.jsonl")); assertEquals(19, files.getInt("$day/events.jsonl"))
        assertEquals(3, files.getInt("$day/gps.csv")); assertEquals(1, files.getInt("restaurants.json"))
    }

    @Test fun zipStreamsTheSameContentWithTheMarkerAtTheRoot() {
        val dir = Files.createTempDirectory("kl-analysis").toFile()
        try {
            val sources = inputs.map { (path, text) -> path to File(dir, path.replace('/', '_').replace('\\', '_')).also { it.writeText(text) } }
            val bytes = ByteArrayOutputStream()
            val marker = AnalysisExport("fixed-test-secret".toByteArray(Charsets.UTF_8)).zip(sources, bytes, "0.15.0", t0 + 4_000_000)
            val entries = LinkedHashMap<String, String>()
            ZipInputStream(bytes.toByteArray().inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes().toString(Charsets.UTF_8) } }
            assertEquals(out.keys.toList(), entries.keys.toList())
            for ((k, v) in out) if (k != AnalysisExport.MARKER) assertEquals(k, v, entries[k])
            assertEquals(marker.toString(), entries[AnalysisExport.MARKER])
            assertEquals(JSONObject(out.getValue(AnalysisExport.MARKER)).getJSONObject("files").toString(), marker.getJSONObject("files").toString())
        } finally { dir.deleteRecursively() }
    }

    @Test fun onlyKnownLabelsAndActionsSurviveAsClickText() {
        for (t in listOf("Accept", "Decline", "Collect", "Order delivered", "Slide to confirm", "Slide to confirm Order delivered", "Arrived", "Call", "Confirm",
            "Go online", "Qəbul et", "Accept (12s)", "22 min, 4,49 ₼", "Accept 22 min, 4,49 ₼", "₼2.31 · 8–16 min", "Return to offer details", "Profile"))
            assertTrue(t, AnalysisExport.isKnownAction(t))
        for (t in listOf("", " ", "Dropoff to Aysel Testova Sintetik kuce 81, Bakı Call", "Customer drop-off Litvin Babayev 81, Bakı 22 min, 4,49 ₼", "Call Aysel",
            "Aysel Testova", "Order #8P6D2 Call 055 123 45 67", "Accept Aysel 22 min, 4,49 ₼", "Meet at my door", "4826", "Collect order #014 Aysel T."))
            assertFalse(t, AnalysisExport.isKnownAction(t))
        assertEquals(AnalysisExport.CARD, export.clickText("Dropoff to Aysel Testova Sintetik kuce 81, Bakı Call"))
        assertEquals("Accept", export.clickText("Accept"))
    }

    @Test fun pseudonymsHoldInsideOneExportOnly() {
        val a = AnalysisExport(); val b = AnalysisExport()
        assertEquals(a.customerId("name:aysel testova"), a.customerId("name:aysel testova"))
        assertNotEquals(a.customerId("name:aysel testova"), b.customerId("name:aysel testova"))
        assertNotEquals(a.customerId("name:aysel testova"), a.customerId("name:aysel testovb"))
        assertTrue(a.orderId("#8P6D2").matches(Regex("#[0-9][A-Z][0-9][A-Z][0-9]")))
        assertEquals(a.orderId("#8P6D2"), a.customerId("#8P6D2"))
        assertTrue(a.customerId("other-key").startsWith("c:"))
        assertEquals(32, AnalysisExport.newSecret().size)
    }

    @Test fun panelLinesAreRecognisedInBothLanguages() {
        val own = listOf("● 12.4 km", "• 0.0 km", "·3,5 km", "Wolt · 3.94₼ · Izmir Doner", "Bolt Food · 4.49₼ · Teklif", "Wolt · 3.94₼ · Təklif", "Wolt · 3.94 AZN · Teklif",
            "Wolt · 3.94₼ · net 3.15₼ · Izmir Doner", "WOLT • 3,94 ₼ • TEKLIF", "Wolt - 3.94₼ - Teklif",
            "Wolt · Izmir Doner · Restorana gidiliyor", "Wolt · Izmir Doner · Restorana gedilir", "Wolt · Sipariş · Restoranda bekleniyor",
            "Wolt · Sifariş · Restoranda gözlənilir", "Bolt Food · Aysel T. · Müşteriye gidiliyor", "Bolt Food · Aysel T. · Müştəriyə gedilir",
            "Wolt · X · Müşteriye varıldı", "Wolt · X · Müştəriyə çatıldı", "Wolt · X · Teslim edildi", "Wolt · X · Çatdırıldı", "Wolt · X · Sonuç doğrulanamadı",
            "Wolt · X · Nəticə təsdiqlənmədi", "Wolt · Musteriye gidiliyor".let { "Wolt · Y · Musteriye gidiliyor" },
            "Wolt · Sipariş bekleniyor", "Bolt Food · Sifariş gözlənilir", "Wolt · Çevrimdışı", "Bolt Food · Oflayn", "courierapp · Oflayn",
            "Bugün: 0 sipariş", "Bu gün: 12 sifariş", "BUGUN: 3 SIPARIS", " Bugün:3 sipariş ")
        for (l in own) assertTrue(l, AnalysisExport.isPanelLine(l))
        val notOwn = listOf("", "₼2.31", "4.2 km", "2 stops (2.8 km) • 8–16 min", "Wolt", "Sipariş", "Teslim edildi", "Restorana gidiliyor", "Order delivered!",
            "AZN 16.79", "22 min, 4,49 ₼", "Bugün", "Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000", "Drop off in -1 min", "Ready in 3 min",
            "1 x Test", "Bolt Food", "Wolt · ", "Your stats", "Today: 3 orders")
        for (l in notOwn) assertFalse(l, AnalysisExport.isPanelLine(l))
        assertEquals(listOf("Dropoff to", "Aysel"), AnalysisExport.withoutPanel(listOf("● 1.0 km", "Dropoff to", "Wolt · Sipariş bekleniyor", "Aysel")))
        assertEquals(listOf("Notes\nKeep"), AnalysisExport.withoutPanel(listOf("Notes\nBugün: 1 sipariş\nKeep")))
    }

    @Test fun coordinatesRoundLikePython() {
        // Python: round(x, 3) on the exact binary value.
        val cases = mapOf(40.4095 to 40.41, 49.8665 to 49.867, 0.0005 to 0.001, -49.8675 to -49.867, 2.0005 to 2.001, 1.0005 to 1.0, 40.40931234 to 40.409)
        for ((x, y) in cases) assertEquals("$x", y, AnalysisExport.round3(x), 0.0)
        val j = AnalysisExport.geo(JSONObject().put("a", JSONArray().put(JSONObject().put("lat", "40.4093123").put("lon", 49.8671456).put("loc", JSONObject()
            .put("lat", 1.0005).put("lon", 2.0005).put("acc", 3).put("age_s", 4).put("x", "y"))))) as JSONObject
        val o = j.getJSONArray("a").getJSONObject(0)
        assertEquals(40.409, o.getDouble("lat"), 0.0); assertEquals(49.867, o.getDouble("lon"), 0.0)
        assertEquals("{\"lat\":1,\"lon\":2.001,\"acc\":3,\"age_s\":4}".length, o.getJSONObject("loc").toString().length)
        assertFalse(o.getJSONObject("loc").has("x"))
    }
}
