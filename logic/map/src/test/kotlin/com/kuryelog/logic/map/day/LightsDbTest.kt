package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `LightsDb` (MAP.md 11.11; YolGo `shift_controller.dart:725-814`): karakterizasyon (eski test yok). Tercih `lights.v1`, öğe `"lat,lon,yes,no,waitS"`. */
class LightsDbTest {
    private val lat = 40.4
    private val lon = 49.85
    private fun north(m: Double) = lat + m / 111195.08

    @Test
    fun `biçim 6 haneli enlem boylam ve sıra korunur, bozuk satır atlanır`() {
        val prefs = MemoryMapPrefs(mapOf(LightsDb.KEY to listOf("40.400000,49.850000,2,1,30", "bozuk", "x,49.85,1,0,0", "40.41,49.86,1,0", "40.42,49.87,0,1,zz")))
        val db = LightsDb(prefs)
        db.record(40.5, 49.9, isLight = true, waitS = 12)
        val saved = prefs.getStringList(LightsDb.KEY)!!
        assertEquals(listOf("40.400000,49.850000,2,1,30", "40.410000,49.860000,1,0,0", "40.420000,49.870000,0,1,0", "40.500000,49.900000,1,0,12"), saved)
    }

    @Test
    fun `lookup 25 m içindeki en yakın noktaya bakar, yes eşit ya da nokta yoksa null`() {
        val db = LightsDb(MemoryMapPrefs(mapOf(LightsDb.KEY to listOf("40.4,49.85,2,1,0", "40.5,49.85,1,1,0"))))
        assertEquals(true, db.lookup(north(0.0), lon))
        assertEquals(true, db.lookup(north(24.0), lon))
        assertNull(db.lookup(north(40.0), lon))
        assertNull("yes == no bilinmiyor", db.lookup(40.5, 49.85))
        db.record(lat, lon, isLight = false)
        db.record(lat, lon, isLight = false)
        assertEquals(false, db.lookup(lat, lon))
    }

    @Test
    fun `nearest ve spots yalnız yes ve no farklı noktaları sayar`() {
        val db = LightsDb(MemoryMapPrefs(mapOf(LightsDb.KEY to listOf("40.4,49.85,1,0,0", "40.4003,49.85,1,1,0", "40.4006,49.85,0,2,0"))))
        assertEquals(listOf(true, false), db.spots.map { it.exists })
        assertEquals(1, db.confirmed)
        assertTrue(db.nearest(40.4001, 49.85)!!.exists)
        assertNull("55 m dışı", db.nearest(40.41, 49.85))
        assertFalse(db.nearest(40.4006, 49.85)!!.exists)
    }

    @Test
    fun `record 25 m içinde yoksa yeni nokta, varsa ışıksa yes ve waitS birikir, değilse no`() {
        val db = LightsDb(MemoryMapPrefs())
        db.record(lat, lon, isLight = true, waitS = 20)
        db.record(north(10.0), lon, isLight = true, waitS = 15)
        assertEquals(1, db.confirmed)
        db.record(north(5.0), lon, isLight = false)
        assertEquals(true, db.lookup(lat, lon))
        val prefs = MemoryMapPrefs()
        val d2 = LightsDb(prefs)
        d2.record(lat, lon, true, 20); d2.record(lat, lon, true, 15)
        assertEquals(listOf("40.400000,49.850000,2,0,35"), prefs.getStringList(LightsDb.KEY))
    }

    @Test
    fun `reviewLight iki çağrısı son cevap kazanır, konum taşınır, waitS birikir`() {
        val prefs = MemoryMapPrefs()
        val db = LightsDb(prefs)
        db.record(lat, lon, true, 10)
        db.record(lat, lon, true, 10)
        // gün sonu "Reddet" (aday): record(isLight=false) + setManual(false)
        db.record(north(8.0), lon, false, 25)
        db.setManual(north(8.0), lon, false)
        assertEquals(false, db.lookup(lat, lon))
        assertEquals(listOf("${"%.6f".format(java.util.Locale.ROOT, north(8.0))},49.850000,0,1,20"), prefs.getStringList(LightsDb.KEY))
    }

    @Test
    fun `setManual 25 m içindeki en yakın noktayı siler ve basılan yere yazar`() {
        val db = LightsDb(MemoryMapPrefs(mapOf(LightsDb.KEY to listOf("40.4,49.85,3,0,40", "40.4002,49.85,1,0,0"))))
        db.setManual(north(20.0), lon, exists = false)
        assertEquals(2, db.spots.size)
        assertEquals(false, db.spots.last().exists)
    }
}
