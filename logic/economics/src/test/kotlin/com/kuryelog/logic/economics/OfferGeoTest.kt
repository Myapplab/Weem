package com.kuryelog.logic.economics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats OfferGeoTest.kt @ 6388c04, aynı girdi → aynı çıktı; farklar ECONOMICS.md "WEEM" satırlarında.

/** WP2: every row of `economics/offer_geo_vectors.json` (made by `docs/ekonomi-v2/vektorler/make_vectors.py`). */
class OfferGeoTest {
    private val kinds = setOf("plus_short", "plus_full", "encode", "dms", "card", "zone", "hourBlock")

    private fun vectors(): JSONObject {
        val stream = javaClass.classLoader.getResourceAsStream("economics/offer_geo_vectors.json")
        assertNotNull("economics/offer_geo_vectors.json not on the test classpath", stream)
        return JSONObject(stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun rows(kind: String): List<JSONObject> {
        val all = vectors().getJSONArray("rows")
        val out = (0 until all.length()).map { all.getJSONObject(it) }.filter { it.getString("kind") == kind }
        assertTrue("no '$kind' rows", out.isNotEmpty())
        return out
    }

    private fun assertNear(msg: String, expected: JSONArray, lat: Double, lon: Double) {
        assertEquals("$msg lat", expected.getDouble(0), lat, 1e-7)
        assertEquals("$msg lon", expected.getDouble(1), lon, 1e-7)
    }

    private fun isBaku(ref: JSONArray) =
        ref.getDouble(0) == OfferGeo.BAKU_REFERENCE.lat && ref.getDouble(1) == OfferGeo.BAKU_REFERENCE.lon

    @Test fun everyRowKindIsKnownAndPresent() {
        val all = vectors().getJSONArray("rows")
        val seen = (0 until all.length()).map { all.getJSONObject(it).getString("kind") }.toSet()
        assertEquals(kinds, seen)
    }

    @Test fun tablesMatchVectorHeader() {
        val v = vectors()
        assertNear("hub", v.getJSONArray("hub"), OfferGeo.HUB_DEFAULT.lat, OfferGeo.HUB_DEFAULT.lon)
        assertNear("bakuReference", v.getJSONArray("bakuReference"), OfferGeo.BAKU_REFERENCE.lat, OfferGeo.BAKU_REFERENCE.lon)
        val zones = v.getJSONArray("zones")
        assertEquals(zones.length(), OfferGeo.ZONES.size)
        OfferGeo.ZONES.forEachIndexed { i, (zone, lo, hi) ->
            val z = zones.getJSONArray(i)
            assertEquals(z.getString(0), zone)
            assertEquals(z.getDouble(1), lo, 0.0)
            if (z.isNull(2)) assertEquals(Double.POSITIVE_INFINITY, hi, 0.0) else assertEquals(z.getDouble(2), hi, 0.0)
        }
        val blocks = v.getJSONArray("hourBlocks")
        assertEquals(blocks.length(), OfferGeo.HOUR_BLOCKS.size)
        OfferGeo.HOUR_BLOCKS.forEachIndexed { i, (block, lo, hi) ->
            val b = blocks.getJSONArray(i)
            assertEquals(b.getString(0), block)
            assertEquals(b.getInt(1), lo)
            assertEquals(b.getInt(2), hi)
        }
    }

    @Test fun plusShortRecoversNearestCode() {
        for (r in rows("plus_short")) {
            val text = r.getString("text")
            val ref = r.getJSONArray("ref")
            val msg = "$text near $ref"
            assertEquals(msg, r.getString("full"), OfferGeo.recoverNearest(text, ref.getDouble(0), ref.getDouble(1)))
            val c = OfferGeo.plusCodeCenter(text, ref.getDouble(0), ref.getDouble(1))
            assertNotNull(msg, c)
            assertNear(msg, r.getJSONArray("center"), c!!.lat, c.lon)
            if (isBaku(ref)) {
                val d = OfferGeo.plusCodeCenter(text)
                assertNotNull("$text without GPS", d)
                assertNear("$text without GPS", r.getJSONArray("center"), d!!.lat, d.lon)
            }
        }
    }

    @Test fun plusFullCentersIgnoreReference() {
        for (r in rows("plus_full")) {
            val text = r.getString("text")
            for (c in listOf(OfferGeo.plusCodeCenter(text), OfferGeo.plusCodeCenter(text, -33.9, 151.2))) {
                assertNotNull(text, c)
                assertNear(text, r.getJSONArray("center"), c!!.lat, c.lon)
            }
            assertEquals(text, text, OfferGeo.recoverNearest(text, 0.0, 0.0))
        }
    }

    @Test fun encodeMatchesReference() {
        for (r in rows("encode")) {
            val ll = r.getJSONArray("latLng")
            assertEquals("$ll", r.getString("full10"), OfferGeo.encode(ll.getDouble(0), ll.getDouble(1), 10))
            assertEquals("$ll", r.getString("full11"), OfferGeo.encode(ll.getDouble(0), ll.getDouble(1), 11))
        }
    }

    @Test fun dmsParsesOrReturnsNull() {
        for (r in rows("dms")) {
            val text = r.getString("text")
            val p = OfferGeo.dmsLatLon(text)
            if (r.isNull("latLng")) assertNull(text, p)
            else {
                assertNotNull(text, p)
                assertNear(text, r.getJSONArray("latLng"), p!!.lat, p.lon)
            }
        }
    }

    @Test fun cardTextTriesDmsThenPlusCodeAndNeverGeocodes() {
        for (r in rows("card")) {
            val text = r.getString("text")
            val ref = r.optJSONArray("ref")
            val p = OfferGeo.fromCardText(text, ref?.getDouble(0), ref?.getDouble(1))
            if (r.isNull("latLng")) {
                assertNull(text, p)
                assertTrue(text, r.isNull("source"))
            } else {
                assertNotNull(text, p)
                assertNear(text, r.getJSONArray("latLng"), p!!.lat, p.lon)
                assertEquals(text, r.getString("source"), p.source)
            }
        }
    }

    @Test fun zonesMatchRefZoneOf() {
        val hub = OfferGeo.HUB_DEFAULT
        for (r in rows("zone")) {
            val ll = r.getJSONArray("latLng")
            val lat = ll.getDouble(0)
            val lon = ll.getDouble(1)
            assertEquals("$ll km", r.getDouble("kmFromHub"), OfferGeo.haversineKm(hub.lat, hub.lon, lat, lon), 1e-7)
            assertEquals("$ll", r.getString("zone"), OfferGeo.coarseZone(lat, lon))
        }
    }

    @Test fun hourBlocksMatchRefHourBlock() {
        for (r in rows("hourBlock")) {
            val m = r.getDouble("minuteOfDay")
            assertEquals("minute $m", r.getString("block"), OfferGeo.hourBlock(m))
        }
    }

    @Test fun noCoordinateGivesUnknownOrNull() {
        assertEquals(OfferGeo.UNKNOWN, OfferGeo.coarseZone(null, null))
        assertEquals(OfferGeo.UNKNOWN, OfferGeo.coarseZone(40.4, null))
        assertEquals(OfferGeo.UNKNOWN, OfferGeo.coarseZone(Double.NaN, 49.8))
        assertNull(OfferGeo.fromCardText("Mirzəağa Əliyev 249, Bakı, 1014"))
        assertNull(OfferGeo.fromCardText("Mertebe 6, tel 055 123 45 67, AZ1000"))
        // not valid Open Location Codes: odd prefix, misplaced separator, out-of-range first digit, one digit after '+'
        for (bad in listOf("C9RQW+344", "9RQW344", "9RQ+W344", "WWGFCV58+PR", "8HGFCV58+P", "Baku")) {
            assertNull(bad, OfferGeo.plusCodeCenter(bad))
        }
        assertNull(OfferGeo.fromCardText("C9RQW+344 Baku"))
    }

    @Test fun lowerCaseCodeAndGpsReference() {
        val c = OfferGeo.plusCodeCenter("9rqw+344", 40.37, 49.80)
        assertNotNull(c)
        assertEquals(40.3876375, c!!.lat, 1e-7)
        assertEquals(49.8453281, c.lon, 1e-7)
    }

    @Test fun hubOverrideMovesZones() {
        // the user's real Wolt pickup is ~3.03 km from the default hub (SPEC 8.1): from there the default hub is Z2
        val pickup = OfferGeo.LatLon(40.3876375, 49.8453281)
        assertEquals("Z1", OfferGeo.coarseZone(pickup.lat, pickup.lon, pickup))
        assertEquals("Z2", OfferGeo.coarseZone(OfferGeo.HUB_DEFAULT.lat, OfferGeo.HUB_DEFAULT.lon, pickup))
    }
}
