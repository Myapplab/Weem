package com.kuryelog.logic.map.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `light_rule_m14_test.dart` F9.3 (5; A6.2b, MAP.md 11.9, 11.11, 11.16): 2 dk üstü duruş ışık sayılmaz, `light?` + `longWait` ile gün sonunda sorulur; `long` alanı
 * yazılır/okunur; Onayla → ışık listesinde, Reddet → hiçbirinde; `lightReviewTeaches`.
 */
class LongLightTest {
    private val t0 = DayFixtures.ms("2026-10-16T12:00:00Z")
    private val lat0 = 40.4093
    private val lon0 = 49.8671
    private fun north(m: Double) = lat0 + m / 111195.08
    private val atLight = RoadInfo(onRoad = true, known = true, lightM = 3.0, surface = StopSurface.ROAD, lightLat = 40.40931, lightLon = 49.86712)

    private fun measure(waitS: Int): StopRec? {
        val t = LightTracker()
        for (s in 0..waitS) t.feed(DayFix(t0 + s * 1000L, north(0.0), lon0, speedMps = 0.0), { _, _ -> atLight })
        var rec: StopRec? = null
        var s = waitS + 1
        while (s <= waitS + 10 && rec == null) {
            rec = t.feed(DayFix(t0 + s * 1000L, north((s - waitS) * 5.0), lon0, speedMps = 5.0), { _, _ -> atLight })
            s++
        }
        return rec
    }

    private fun engineStop(waitS: Int): StopRec {
        val e = ShiftEngine().also { it.startDay(t0) }
        var t = t0
        var m = 0.0
        fun ride(seconds: Int, kmh: Double) {
            for (i in 0 until seconds) {
                t += 1000
                m += kmh / 3.6
                e.feed(DayFix(t, north(m), lon0, speedMps = kmh / 3.6)) { _, _ -> atLight }
            }
        }
        ride(10, 20.0); ride(waitS, 0.0); ride(10, 20.0)
        return e.s.stops.single()
    }

    @Test
    fun `ölçer 100 sn light, 150 sn light? ve longWait, ışığın yeri kayıtlı`() {
        val short = measure(100)!!
        assertEquals("light" to false, short.kind to short.longWait)
        val long = measure(150)!!
        assertEquals("light?" to true, long.kind to long.longWait)
        assertNotNull(long.lightLat)
        assertFalse(long.isLight)
    }

    @Test
    fun `motor 60 sn light, 150 sn light? ve longWait`() {
        assertEquals("light", engineStop(60).kind)
        val long = engineStop(150)
        assertEquals("light?" to true, long.kind to long.longWait)
        assertNotNull(long.lightLat)
    }

    @Test
    fun `kayıt biçimi yeni alan long, eski kayıtta yok false`() {
        val long = measure(150)!!
        val j = long.toJson()
        assertTrue(j.getBoolean("long"))
        assertEquals("light?", j.getString("kind"))
        assertTrue(StopRec.fromJson(j).longWait)
        val old = org.json.JSONObject(j.toString()).also { it.remove("long") }
        assertFalse(StopRec.fromJson(old).longWait)
        assertFalse(measure(100)!!.toJson().has("long"))
    }

    @Test
    fun `gün sonu uzun duruş soru listesinde, Onayla ışık listesinde, Reddet hiçbirinde`() {
        val day = DayState()
        day.stops.addAll(listOf(measure(150)!!, measure(140)!!, measure(30)!!))
        assertEquals(2, day.lightCandidates.size)
        assertEquals(1, day.lights.size)
        day.stops[0].answer = "light"
        day.stops[1].answer = "not"
        assertTrue(day.lightCandidates.isEmpty())
        assertEquals(2, day.lights.size)
    }

    @Test
    fun `öğrenme uzun duruşta Reddet ışığı silmez, Onayla evet öğretir, adayda ikisi de öğretir`() {
        val long = measure(150)!!
        assertEquals(true, LightReview.teaches(long, isLight = true))
        assertNull(LightReview.teaches(long, isLight = false))
        val cand = StopRec(startMs = t0, endMs = t0 + 20_000, lat = lat0, lon = lon0, kind = "light?", onRoad = true)
        assertEquals(true, LightReview.teaches(cand, isLight = true))
        assertEquals(false, LightReview.teaches(cand, isLight = false))
    }
}
