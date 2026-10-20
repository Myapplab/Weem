package com.kuryelog.logic.map.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `LightsDb` karakterizasyonu (MAP.md 11.11 "Yeni"): biçim, bozuk satır, 25 m `lookup`, `nearest` yalnız `yes ≠ no`, `record` + `setManual` son cevap kazanır, `waitS` birikir. */
class LightsDbTest {
    private val lat = 40.4093
    private val lon = 49.8671
    private fun north(m: Double) = lat + m / 111195.08

    @Test
    fun `biçim lat lon yes no waitS, bozuk satır atlanır, waitS yoksa 0`() {
        val db = LightsDb(listOf("40.4093,49.8671,2,1,35", "bozuk", "x,49.8,1,0,0", "40.41,49.87,0,1", "40.42,49.88,1"))
        assertEquals(listOf("40.4093,49.8671,2,1,35", "40.41,49.87,0,1,0"), db.toLines())
        assertEquals(1, db.confirmed)
    }

    @Test
    fun `lookup 25 m içindeki en yakın nokta, yes eşit no ise bilinmiyor`() {
        val db = LightsDb(listOf("40.4093,49.8671,2,1,0", "40.5,49.9,1,1,0"))
        assertEquals(true, db.lookup(north(24.0), lon))
        assertNull(db.lookup(north(40.0), lon))
        assertNull("yes == no: bilinmiyor", db.lookup(40.5, 49.9))
    }

    @Test
    fun `nearest yalnız yes ne no noktalarda 55 m altında`() {
        val db = LightsDb(listOf("40.4093,49.8671,1,1,0", "40.4097,49.8671,0,2,0"))
        val s = db.nearest(lat, lon)!!
        assertFalse(s.exists)
        assertEquals(1, db.spots.size)
        assertNull(db.nearest(north(200.0), lon))
    }

    @Test
    fun `record yeni nokta ya da yakın nokta, setManual son cevap kazanır waitS birikir`() {
        val db = LightsDb()
        db.record(lat, lon, true, 40)
        db.record(north(10.0), lon, true, 20)
        assertEquals(listOf("40.4093,49.8671,2,0,60"), db.toLines())
        db.record(lat, lon, false, 99)
        assertEquals("40.4093,49.8671,2,1,60", db.toLines().single())
        db.setManual(north(5.0), lon, false)
        val line = db.toLines().single().split(",")
        assertEquals(listOf("0", "1", "60"), line.subList(2, 5))
        assertTrue(db.lookup(north(5.0), lon) == false)
    }

    @Test
    fun `JSON dizi dizgesi gidiş dönüş, bozuk dizge boş`() {
        val db = LightsDb(listOf("40.4093,49.8671,1,0,12"))
        assertEquals(db.toLines(), LightsDb.fromJsonString(db.toJsonString()).toLines())
        assertTrue(LightsDb.fromJsonString("{bozuk").toLines().isEmpty())
    }
}
