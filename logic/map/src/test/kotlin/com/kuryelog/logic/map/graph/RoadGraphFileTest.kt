package com.kuryelog.logic.map.graph

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.TestGraphs
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `test/router_test.dart` "graf kurulur ve dosyaya yazılıp okunabilir" (1) + `test/bikeway_e13_test.dart` "E13 harita dosyası"
 * (2); `MAP.md` 2.13, 5.2. KNG1 little-endian (`MAP.md` 2.15).
 */
class RoadGraphFileTest {
    @Test
    fun `graf kurulur ve dosyaya yazılıp okunabilir`() {
        val g = TestGraphs.main(hill = 10.0)
        // M sadece doğrudan yolun iç noktası → kavşak değil. Düğümler: A, B, E, F
        assertEquals(4, g.nodeCount)
        assertEquals(4, g.edgeCount) // A-B (doğrudan), A-E, E-F, F-B
        val g2 = RoadGraph.deserialize(g.serialize())
        assertEquals(g.edgeCount, g2.edgeCount)
        assertArrayEquals(g.climbF, g2.climbF, 0f)
        assertEquals(g.names, g2.names)
    }

    @Test
    fun `yeni graf bisiklet bilgisini taşır - kaydedip okuyunca aynı`() {
        val g = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "lane"))
        assertTrue(g.hasBikewayData)
        val back = RoadGraph.deserialize(g.serialize())
        assertTrue(back.hasBikewayData)
        val bikeWays = (0 until back.edgeCount).filter { back.isBikeway(it) }.map { back.osmIdOf(it) }.toSet()
        assertEquals(setOf(401L, 402L, 403L), bikeWays)
        assertNull(bikewayMapWarning(back, Lang.TR))
    }

    @Test
    fun `eski harita dosyası sessizce bozulmaz - okunur ve bisiklet bilgisi yok güncelle der`() {
        // Biçim (KNG1 v2) aynı kaldı: eski uygulama yeni dosyayı, yeni uygulama eski dosyayı okur.
        assertEquals(2, RoadGraph.FORMAT_VERSION)
        val g = TestGraphs.parallel()
        val bytes = g.serialize()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(9 * 4, 0) // E13 öncesi başlık: özellik alanı 0 (ofset 36)
        val old = RoadGraph.deserialize(bytes)
        assertEquals(g.edgeCount, old.edgeCount)
        assertFalse(old.hasBikewayData)
        assertTrue(bikewayMapWarning(old, Lang.TR)!!.contains("İndir / güncelle"))
        assertTrue(bikewayMapWarning(old, Lang.AZ)!!.contains("Yüklə / yenilə"))
        // Daha eski biçim (v1) yine açıkça reddedilir (denetleyici "yeniden indir" der).
        buf.putInt(4, 1)
        assertThrows(MapFormatException::class.java) { RoadGraph.deserialize(bytes) }
    }
}
