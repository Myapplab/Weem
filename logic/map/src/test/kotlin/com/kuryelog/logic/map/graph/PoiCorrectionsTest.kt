package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `places.local.v1` düzeltmeleri (MAP.md 5.3 "Yeni"; eski testi yok): `replaces` kuralları, kaydet-değiştir, bozuk öğe, sıra. */
class PoiCorrectionsTest {
    private val base = listOf(Poi(40.40, 49.85, 'r', "Eski Ad", "node/1"), Poi(40.41, 49.86, 'c', "Kafe", ""), Poi(40.42, 49.87, 'f', "Başka", "node/3"))

    @Test
    fun `düzeltme osmRef ile orijinalin yerini alır, yeni işletme eklenir`() {
        val c = PoiCorrections(MemoryMapPrefs())
        c.save(Poi(40.40, 49.85, 'r', "Yeni Ad", "node/1"), base[0])
        c.save(Poi(40.50, 49.90, 'f', "Yeni İşletme"), null)
        val out = c.apply(base)
        assertEquals(listOf("Kafe", "Başka", "Yeni Ad", "Yeni İşletme"), out.map { it.name })
    }

    @Test
    fun `osmRef yoksa aynı tür ve 1 m içindeki orijinal değişir`() {
        val c = PoiCorrections(MemoryMapPrefs())
        c.save(Poi(40.41, 49.86, 'c', "Düzgün Kafe"), base[1])
        assertEquals(listOf("Eski Ad", "Başka", "Düzgün Kafe"), c.apply(base).map { it.name })
        assertTrue(c.fixes.single().replaces(Poi(40.410005, 49.86, 'c', "Kafe")))
        assertTrue(!c.fixes.single().replaces(Poi(40.41, 49.86, 'r', "Kafe")))
    }

    @Test
    fun `aynı orijinali tekrar düzeltince eskisi değişir ve orijinal bilgisi ilkinden kalır`() {
        val prefs = MemoryMapPrefs()
        val c = PoiCorrections(prefs)
        c.save(Poi(40.40, 49.85, 'r', "Birinci", "node/1"), base[0])
        val first = c.fixes.single().poi
        c.save(Poi(40.40, 49.85, 'r', "İkinci", "node/1"), first)
        assertEquals(1, c.fixes.size)
        assertEquals("node/1", c.fixes.single().originalRef)
        assertEquals(listOf("Kafe", "Başka", "İkinci"), c.apply(base).map { it.name })
        val again = PoiCorrections(prefs)
        assertEquals("İkinci", again.fixes.single().poi.name)
    }

    @Test
    fun `bozuk öğe atlanır, liste sırası korunur`() {
        val prefs = MemoryMapPrefs(mapOf(PoiCorrections.KEY to listOf("""{"lat":200,"lon":49,"kind":"r","name":"x"}""", "bozuk", """{"lat":40.1,"lon":49.1,"kind":"t","name":"Bakkal"}""", """{"lat":40.2,"lon":49.2,"kind":"r","name":"Ad"}""")))
        assertEquals(listOf("Bakkal", "Ad"), PoiCorrections(prefs).fixes.map { it.poi.name })
    }
}
