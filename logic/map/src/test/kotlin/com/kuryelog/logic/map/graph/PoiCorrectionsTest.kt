package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.MemoryMapPrefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `places.local.v1` (MAP.md 5.3; Dart `poi_corrections.dart`): `replaces`, kaydet-değiştir, bozuk öğe, sıra. Karakterizasyon (eski test yok). */
class PoiCorrectionsTest {
    private val orig = Poi(40.4, 49.85, 'r', "Eski Ad", "node/1")
    private val set = PoiSet(listOf(orig, Poi(40.41, 49.86, 'f', "Başka", "node/2")))

    @Test
    fun `replaces osmRef doluysa kimlikle, boşsa aynı tür ve 1 m içinde`() {
        val byRef = PoiCorrection(Poi(40.4, 49.85, 'r', "Yeni"), originalRef = "node/1")
        assertTrue(byRef.replaces(orig)); assertTrue(!byRef.replaces(Poi(40.4, 49.85, 'r', "x", "node/9")))
        val byPlace = PoiCorrection(Poi(40.4, 49.85, 'c', "Yeni"), originalLat = 40.4, originalLon = 49.85, originalKind = 'r')
        assertTrue(byPlace.replaces(Poi(40.4 + 0.5 / 111195.0, 49.85, 'r', "y")))
        assertTrue(!byPlace.replaces(Poi(40.4 + 2 / 111195.0, 49.85, 'r', "y")))
        assertTrue(!byPlace.replaces(Poi(40.4, 49.85, 'f', "y")))
        assertTrue(!PoiCorrection(Poi(40.4, 49.85, 'r', "Eklenen")).replaces(orig))
    }

    @Test
    fun `düzeltme orijinalin yerini alır, eklenen işletme küme sonuna girer, silme yok`() {
        val c = PoiCorrections(MemoryMapPrefs())
        assertNull(c.corrected(null))
        assertEquals(2, c.corrected(set)!!.length)
        c.save(Poi(orig.lat, orig.lon, 'r', "Yeni Ad", orig.osmRef), original = orig)
        c.save(Poi(40.5, 49.9, 't', "Yeni Market"))
        val shown = c.corrected(set)!!.items
        assertEquals(setOf("Yeni Ad", "Başka", "Yeni Market"), shown.map { it.name }.toSet())
        assertTrue(shown.none { it.name == "Eski Ad" })
        assertEquals(3, shown.size)
    }

    @Test
    fun `aynı orijinale ikinci düzeltme eskisini değiştirir ve orijinal bilgisini korur, liste sonuna eklenir`() {
        val prefs = MemoryMapPrefs()
        val c = PoiCorrections(prefs)
        c.save(Poi(orig.lat, orig.lon, 'r', "Bir", orig.osmRef), original = orig)
        c.save(Poi(40.5, 49.9, 't', "Market"))
        val first = c.all.first().poi
        c.save(Poi(orig.lat, orig.lon, 'c', "İki", orig.osmRef), original = first)
        assertEquals(listOf("Market", "İki"), c.all.map { it.poi.name })
        val kept = c.all.last()
        assertEquals("node/1", kept.originalRef); assertEquals('r', kept.originalKind); assertEquals(orig.lat, kept.originalLat!!, 0.0)
        assertEquals(2, prefs.getStringList(PoiCorrections.KEY)!!.size)
        assertEquals(3, c.rev)
    }

    @Test
    fun `yeniden açılışta okunur, bozuk ve aralık dışı öğe atlanır`() {
        val good = JSONObject().put("lat", 40.4).put("lon", 49.85).put("kind", "r").put("name", "İyi").put("osmRef", "node/7")
            .put("originalRef", "node/1").put("originalLat", JSONObject.NULL).put("originalLon", JSONObject.NULL).put("originalKind", JSONObject.NULL)
        val outOfRange = JSONObject().put("lat", 95.0).put("lon", 49.85).put("kind", "r").put("name", "Dışarda")
        val prefs = MemoryMapPrefs(mapOf(PoiCorrections.KEY to listOf(good.toString(), "{bozuk", outOfRange.toString(), """{"lat":40.4}""")))
        val c = PoiCorrections(prefs)
        assertEquals(listOf("İyi"), c.all.map { it.poi.name })
        assertEquals("node/1", c.all.single().originalRef)
        assertTrue(c.all.single().replaces(orig))
    }
}
