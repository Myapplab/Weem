package com.kuryelog.logic.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Plan 4.4 mesajları (A2.5): JSON gidiş-dönüşü (ayrıştırılmış değerle eşit), bugünkü feed yüklerinin okunması (`X_protocol` P1–P9),
 * bilinmeyen alanın yok sayılması, ölçülmeyen sayının null kalması, başka şemanın reddi ve gizlilik (müşteri adı/telefonu yok).
 */
class MessagesTest {
    private val now = 1_792_228_260_000L

    private fun reparse(j: JSONObject) = JSONObject(j.toString())
    private fun withExtra(j: JSONObject) = reparse(j).put("someFutureField", JSONObject().put("x", 1))

    private val offer = OfferSummary(
        offerKey = "offer-1792228255000-1", platform = CourierPlatform.WOLT, firstSeenAtMs = now - 5_000, lastSeenAtMs = now - 1_000,
        visibleNow = true, expiresAtMs = now + 40_000, displayedFeeAzn = 3.94, platformDistanceKm = 2.4, restaurant = "Borani Sahil",
        pickupZone = OfferSummary.Zone.Z1, dropoffZone = OfferSummary.Zone.Z3, dropoffKnown = true,
        shown = OfferSummary.Shown(minLo = 9.0, minHi = 17.0, km = 2.0, stops = 1, readyInMin = 5.0, multiplier = 1.2, boostPct = 20.0),
        score = 7, netAzn = 3.1, dropoffAddress = "Uzeyir Hajibayli str. 17, AZ1000 Baku", dropoffLat = 40.3777, dropoffLon = 49.892,
        rangeWarnKm = 6.4,
    )

    @Test fun offerSummaryRoundTripAndPrivacy() {
        assertEquals(offer, OfferSummary.parse(reparse(offer.toJson())))
        assertEquals(offer, OfferSummary.parse(withExtra(offer.toJson())))
        val keys = offer.toJson().keys().asSequence().toSet()
        assertEquals(setOf("schemaVersion", "offerKey", "platform", "firstSeenAtMs", "lastSeenAtMs", "visibleNow", "expiresAtMs", "displayedFeeAzn",
            "platformDistanceKm", "restaurant", "pickupZone", "dropoffZone", "dropoffKnown", "shown", "score", "netAzn", "dropoffAddress",
            "dropoffLat", "dropoffLon", "rangeWarnKm"), keys)
        // Unmeasured stays null, never 0.
        val bare = OfferSummary("k", CourierPlatform.BOLT, 1L, 2L, false)
        val j = reparse(bare.toJson())
        assertTrue(j.isNull("displayedFeeAzn") && j.isNull("score") && j.getJSONObject("shown").isNull("minLo"))
        assertEquals(bare, OfferSummary.parse(j))
    }

    @Test fun offerSummaryCutsAndRejects() {
        val long = offer.copy(restaurant = "r".repeat(300), score = 11, displayedFeeAzn = Double.NaN)
        val back = OfferSummary.parse(reparse(long.toJson()))!!
        assertEquals(200, back.restaurant!!.length)
        assertNull(back.score)
        assertNull("NaN is not a value", back.displayedFeeAzn)
        assertNull(OfferSummary.parse(reparse(offer.toJson()).put("schemaVersion", 2)))
        assertNull(OfferSummary.parse(reparse(offer.toJson()).put("platform", "uber")))
        assertNull(OfferSummary.parse(reparse(offer.toJson()).apply { remove("offerKey") }))
        assertNull(OfferSummary.parse("{oops"))
        val halfPoint = OfferSummary.parse(reparse(offer.toJson()).put("dropoffLon", 999.0))!!
        assertNull(halfPoint.dropoffLat); assertNull(halfPoint.dropoffLon)
        assertNull(OfferSummary.parse(reparse(offer.toJson()).put("rangeWarnKm", -1.0))!!.rangeWarnKm)
    }

    /** Today's feed v2 offer (OfferFeed.kt:80-116; feed_v2.schema.json): v1 fields at the root, the summary in `v2`. */
    @Test fun todaysFeedV2OfferIsRead() {
        val feed = JSONObject().put("id", "offer-1792228255000-1").put("platformPackage", "com.wolt.courierapp").put("firstObservedAtMs", now - 5_000)
            .put("observedAtMs", now - 1_000).put("source", "accessibility").put("complete", true).put("currency", "AZN").put("displayedFeeAzn", 3.94)
            .put("feeBasis", "gross").put("feeScope", "unknown").put("platformDistanceKm", 2.4).put("platformDistanceScope", "unknown")
            .put("remainingOfferMs", JSONObject.NULL).put("restaurant", "Borani Sahil").put("pickupAddress", "Neftchilar ave. 1")
            .put("dropoffAddress", "Uzeyir Hajibayli str. 17, AZ1000 Baku")
            .put("v2", JSONObject().put("offerKey", "offer-1792228255000-1").put("platform", "wolt").put("firstSeenAtMs", now - 5_000)
                .put("lastSeenAtMs", now - 1_000).put("visibleNow", true).put("expiresAtMs", now + 40_000).put("expirySource", "documented_upper_bound")
                .put("shown", JSONObject().put("minLo", 9).put("minHi", 17).put("km", 2).put("kmScope", "unknown").put("stops", 1).put("readyInMin", 5)
                    .put("approachMin", JSONObject.NULL).put("deliveryMin", JSONObject.NULL).put("totalMin", JSONObject.NULL).put("multiplier", 1.2)
                    .put("boostPct", 20).put("demandLabel", JSONObject.NULL))
                .put("pickupZone", "Z1").put("dropoffZone", "Z3").put("dropoffKnown", true).put("optional", false).put("duringTripId", JSONObject.NULL)
                .put("parse", JSONObject().put("feeSource", "accessibility").put("confidence", "high")))
        val s = OfferSummary.fromFeedV2(reparse(feed))!!
        assertEquals(offer.copy(score = null, netAzn = null, dropoffLat = null, dropoffLon = null, rangeWarnKm = null), s)
        assertNull("no v2: not a v2 offer", OfferSummary.fromFeedV2(reparse(feed).apply { remove("v2") }))
    }

    @Test fun tripStagesRoundTripAndTodaysFeed() {
        val stages = TripStages(listOf(
            TripStages.Trip("t1", "com.wolt.courierapp", false, null, TripStages.Outcome.DELIVERED, now - 60_000, listOf(
                TripStages.Event(TripStages.Stage.TO_PICKUP, now - 900_000, pickup = 0),
                TripStages.Event(TripStages.Stage.AT_PICKUP, now - 600_000, pickup = 0, source = "gps"),
                TripStages.Event(TripStages.Stage.TO_DROPOFF, now - 500_000),
                TripStages.Event(TripStages.Stage.AT_DROPOFF, now - 120_000))),
            TripStages.Trip("t2", "com.bolt.deliverycourier", true, TripStages.Stage.TO_PICKUP, null, null,
                listOf(TripStages.Event(TripStages.Stage.TO_PICKUP, now - 30_000, pickup = 0))),
        ))
        assertEquals(stages, TripStages.parse(reparse(stages.toJson())))
        assertTrue(stages.busy)
        assertFalse(TripStages.EMPTY.busy)
        // Today's feed writes "version" (TripStages.kt:177).
        val today = reparse(stages.toJson()).apply { remove("schemaVersion"); put("version", 1) }
        assertEquals(stages, TripStages.parse(today))
        assertNull(TripStages.parse(JSONObject().put("version", 2).put("trips", JSONArray())))
        // A broken event or trip is dropped, the rest kept.
        val j = reparse(stages.toJson())
        j.getJSONArray("trips").getJSONObject(1).getJSONArray("events").put(JSONObject().put("stage", "teleported").put("atMs", now))
        j.getJSONArray("trips").put(JSONObject().put("platformPackage", "x"))
        assertEquals(stages, TripStages.parse(j))
    }

    @Test fun navTargetRoundTrip() {
        val t = NavTarget("r1", 40.3777, 49.892, NavKind.CUSTOMER, NavOrigin.INTENT, "com.wolt.courierapp", now, "Test küçəsi 1, giriş 2, mərtəbə 3")
        assertEquals(t, NavTarget.parse(reparse(t.toJson())))
        assertEquals(t, NavTarget.parse(withExtra(t.toJson())))
        assertEquals(t.copy(sourcePackage = null), NavTarget.parse(reparse(t.toJson()).put("sourcePackage", "bad package!")))
        assertNull(NavTarget.parse(reparse(t.toJson()).put("lat", 91.0)))
        assertNull(NavTarget.parse(reparse(t.toJson()).put("kind", "home")))
        assertFalse(t.toJson().keys().asSequence().any { it.contains("phone") || it.contains("name") || it.contains("note") })
    }

    /** Today's `nav/v1/current` (YolGoNav.kt:36-61). */
    @Test fun routeStatusFromTodaysNavFeed() {
        fun feed() = JSONObject().put("schemaVersion", 1).put("source", "yolgo").put("generatedAtMs", now).put("updatedAtMs", now - 500)
            .put("target", JSONObject().put("lat", 40.3777).put("lon", 49.892).put("kind", "restaurant").put("origin", "map").put("requestedAtMs", now - 60_000)
                .put("sourcePackage", "com.wolt.courierapp"))
            .put("route", JSONObject().put("remainingM", 1234.5).put("remainingTurns", 3).put("measuredAtMs", now - 1_000))
        val r = RouteStatus.fromNavFeed(feed().toString())!!
        assertEquals(RouteStatus(RouteStatus.Target(40.3777, 49.892, NavKind.RESTAURANT, NavOrigin.MAP, now - 60_000, "com.wolt.courierapp"),
            RouteStatus.Route(1234.5, 3, now - 1_000), now - 500), r)
        assertEquals(r, RouteStatus.parse(reparse(r.toJson())))
        assertEquals("updatedAtMs defaults to generatedAtMs", now, RouteStatus.fromNavFeed(feed().apply { remove("updatedAtMs") }.toString())!!.updatedAtMs)
        assertNull("broken target dropped, route kept", RouteStatus.fromNavFeed(feed().put("target", JSONObject().put("lat", 1)).toString())!!.target)
        assertNull("turns out of range drop the route", RouteStatus.fromNavFeed(feed().put("route", JSONObject().put("remainingM", 5).put("remainingTurns", 2000)
            .put("measuredAtMs", now)).toString())!!.route)
        assertNotNull(RouteStatus.fromNavFeed(feed().put("route", JSONObject().put("remainingM", 5).put("remainingTurns", JSONObject.NULL).put("measuredAtMs", now)).toString())!!.route)
        assertNull(RouteStatus.fromNavFeed(feed().put("source", "other").toString()))
        assertNull(RouteStatus.fromNavFeed(feed().put("pad", "x".repeat(4000)).toString()))
        assertNull(RouteStatus.fromNavFeed("{"))
        // Home route: no target at all.
        val home = RouteStatus(null, RouteStatus.Route(300.0, null, now), now)
        assertEquals(home, RouteStatus.parse(reparse(home.toJson())))
        assertEquals(r.target, r.freshTarget(now))
        assertNull(r.freshTarget(now + RouteStatus.TARGET_MAX_AGE_MS))
        assertNull(r.freshRoute(now + 30_000))
    }

    @Test fun daySummaryRoundTrip() {
        val d = DaySummary("2026-10-17", mapOf(CourierPlatform.WOLT to DaySummary.PlatformDay(12, 48.6, 41.2, 310.0),
            CourierPlatform.BOLT to DaySummary.PlatformDay(3, null, null, null)), cashAzn = 15.5)
        assertEquals(d, DaySummary.parse(reparse(d.toJson())))
        assertEquals(d, DaySummary.parse(withExtra(d.toJson())))
        assertNull(DaySummary.parse(reparse(d.toJson()).put("day", "2026-02-30")))
        val negative = reparse(d.toJson()).apply { getJSONObject("platforms").getJSONObject("bolt").put("orders", -1) }
        assertEquals(setOf(CourierPlatform.WOLT), DaySummary.parse(negative)!!.platforms.keys)
    }

    /** Today's `scooter/v1/live` (ScooterFeed.kt:203-214). */
    @Test fun scooterLiveFromTodaysFeedAndBatterySummary() {
        val feed = JSONObject().put("schemaVersion", 1).put("generatedAtMs", now).put("source", "rawstats_surus_paneli").put("linkActive", true).put("ageMs", 800)
            .put("scooter", JSONObject().put("internalPct", 64).put("externalPct", JSONObject.NULL).put("speedKmh", 21.5).put("powerW", 310)
                .put("voltageV", 48.2).put("currentA", 6.4).put("rangeKm", 23).put("tempC", 31).put("odometerKm", 1234.5))
            .put("trip", JSONObject().put("wh", 42.0).put("pctUsed", 6).put("avgMovingKmh", 19.4).put("km", 3.2).put("movingMs", 600_000).put("startedAtMs", now - 900_000))
        val live = ScooterLive.parse(feed.toString())!!
        assertEquals("rawstats_surus_paneli", live.source)
        assertEquals(64.0, live.scooter.internalPct!!, 0.0)
        assertNull(live.scooter.externalPct)
        assertEquals(600_000L, live.trip!!.movingMs)
        assertTrue(live.fresh)
        assertEquals(live, ScooterLive.parse(reparse(live.toJson())))
        assertFalse(live.copy(ageMs = 15_001).fresh)
        assertFalse(live.copy(ageMs = null).fresh)
        val down = ScooterLive.parse(JSONObject().put("schemaVersion", 1).put("generatedAtMs", now).put("linkActive", false).put("ageMs", JSONObject.NULL)
            .put("scooter", JSONObject()).put("trip", JSONObject.NULL))!!
        assertNull(down.trip); assertFalse(down.fresh)

        val b = BatterySummary.of(live, now)
        assertEquals(BatterySummary(now, true, 800, 64.0, null, 23.0, 31.0), b)
        assertEquals(b, BatterySummary.parse(reparse(b.toJson())))
        assertTrue(b.due(null))
        assertFalse(b.copy(atMs = now + 59_000, internalPct = 63.5).due(b))
        assertTrue("1 % change", b.copy(atMs = now + 5_000, internalPct = 63.0).due(b))
        assertTrue("60 s", b.copy(atMs = now + 60_000).due(b))
        assertTrue("link lost", b.copy(atMs = now + 1_000, linkActive = false).due(b))
    }

    @Test fun energyRequestAndResult() {
        val req = EnergyRequest("q1", "leg-t1-0", EnergyRequest.Kind.LEG, now - 600_000, now)
        assertEquals(req, EnergyRequest.parse(reparse(req.toJson())))
        assertNull("an open or reversed window is not measured", EnergyRequest.parse(reparse(req.toJson()).put("toWallMs", now - 600_000)))
        val block = EnergyBlock(JSONObject().put("source", "surus_paneli").put("power_verified", false).put("wh", 12.5)
            .put("internal", JSONObject().put("pct_start", 70).put("pct_end", 66)).put("temp_c", JSONObject.NULL))
        val res = EnergyResult("q1", block)
        val back = EnergyResult.parse(reparse(res.toJson()))!!
        assertEquals(res, back)
        assertTrue(back.energy.measured)
        assertFalse(EnergyBlock.notMeasured().measured)
        assertNotEquals(block, EnergyBlock.notMeasured())
    }

    /** Today's `weather/current` (OfferWeather.kt:18-25; RS OfferWeatherK28Test). */
    @Test fun weatherFromTodaysFeedAndRoundTrip() {
        fun feed(at: Long, source: String = "yolgo_open_meteo") = JSONObject().put("schemaVersion", 1).put("source", source).put("fetched_at", at)
            .put("temp_c", 18.4).put("feels_c", JSONObject.NULL).put("rain_mm", 0.4).put("rain_pct", 40).put("wind_kmh", 22).put("gust_kmh", 35)
            .put("code", 61).put("is_day", 1).toString()
        val w = WeatherNow.fromYolGoFeed(feed(now - 20 * 60_000L), now)!!
        assertEquals(WeatherNow(now - 20 * 60_000L, 18.4, null, 0.4, 40.0, 22.0, 35.0, 61), w)
        assertNull(WeatherNow.fromYolGoFeed(feed(now - 91 * 60_000L), now))
        assertNull(WeatherNow.fromYolGoFeed(feed(now + 60_000L), now))
        assertNull(WeatherNow.fromYolGoFeed(feed(now, "other"), now))
        assertNull(WeatherNow.fromYolGoFeed("{oops", now)); assertNull(WeatherNow.fromYolGoFeed(null, now))
        assertEquals(w, WeatherNow.parse(reparse(w.toJson())))
        assertEquals("open_meteo", w.toJson().getString("source"))
        assertTrue(w.fresh(now)); assertFalse(w.fresh(now + WeatherNow.MAX_AGE_MS))
    }

    /** Today's `shift/v1/state` (ShiftStateFeed.kt:15-22) and the message are the same fields. */
    @Test fun shiftStateRoundTrip() {
        val s = ShiftState(true, now - 3_600_000, null, null, interrupted = true, generatedAtMs = now)
        assertEquals(s, ShiftState.parse(s.toJson().toString()))
        val today = ShiftStateSnapshot.json(false, now - 3_600_000, now, "home_arrival", 0L, now)
        assertEquals(ShiftState(false, now - 3_600_000, now, "home_arrival", false, now), ShiftState.parse(today.toString()))
        assertTrue(JsonEq.same(today, ShiftState.parse(today.toString())!!.toJson()))
        assertFalse(s.toJson().keys().asSequence().any { it.contains("lat") || it.contains("lon") })
    }

    @Test fun everyMessageCarriesSchemaVersionOne() {
        val all = listOf(offer.toJson(), TripStages.EMPTY.toJson(), NavTarget("r", 1.0, 2.0, NavKind.UNKNOWN, NavOrigin.ORDER, null, 1L).toJson(),
            RouteStatus(null, null, 1L).toJson(), DaySummary("2026-10-17").toJson(), ScooterLive(1L, false, null).toJson(),
            BatterySummary(1L, false).toJson(), EnergyRequest("q", "s", EnergyRequest.Kind.IDLE, 1L, 2L).toJson(),
            EnergyResult("q", EnergyBlock.notMeasured()).toJson(), WeatherNow(1L).toJson(), ShiftState(false).toJson())
        for (j in all) assertEquals(j.toString(), 1, j.getInt("schemaVersion"))
    }

    @Test fun jsonEqualityIsByValue() {
        assertTrue(JsonEq.same(JSONObject("""{"a":1,"b":[1,2.0,{"c":null}]}"""), JSONObject("""{"b":[1.0,2,{"c":null}],"a":1.0}""")))
        assertFalse(JsonEq.same(JSONObject("""{"a":1}"""), JSONObject("""{"a":1,"b":2}""")))
        assertFalse(JsonEq.same(JSONObject("""{"a":"1"}"""), JSONObject("""{"a":1}""")))
    }
}
