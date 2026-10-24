package com.kuryelog.logic.map.graph

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.route.TestGraphs
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * KNG1 harita dosyası (MAP.md 2.5, 2.13, 5.2): YolGo `router_test.dart` "graf kurulur ve dosyaya yazılıp okunabilir" (1) ve
 * `bikeway_e13_test.dart` "E13 harita dosyası" (2). Dosya little-endian'dır (Dart `Endian.host`). Uygulama: A6.3.
 */
class RoadGraphFileTest {
    @Test
    fun `graf kurulur ve dosyaya yazılıp okunabilir`() {
        val g = TestGraphs.directAndDetour(hill = 10.0)
        // M sadece doğrudan yolun iç noktası → kavşak değil. Düğümler: A, B, E, F
        assertEquals(4, g.nodeCount)
        assertEquals(4, g.edgeCount) // A-B (doğrudan), A-E, E-F, F-B
        val g2 = RoadGraph.deserialize(g.serialize())
        assertEquals(g.edgeCount, g2.edgeCount)
        assertArrayEquals(g.climbF, g2.climbF, 0f)
        assertEquals(g.names, g2.names)
    }

    @Test
    fun `bellek eşlemeli dosyadan toplu okunan graf baytı baytına aynı`() {
        // S39: MapDataController grafı dosyadan bellek eşlemeli okur (yığına kopya yok); toplu okuma tek tek okumayla aynı sonucu vermeli.
        val g = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "lane"))
        val bytes = g.serialize()
        val file = java.io.File.createTempFile("graph", ".bin").apply { deleteOnExit(); writeBytes(bytes) }
        val mapped = java.io.RandomAccessFile(file, "r").use { f ->
            RoadGraph.deserialize(f.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, f.length()))
        }
        assertArrayEquals(bytes, mapped.serialize())
        assertEquals(g.names, mapped.names)
        // Kısa dosya yine "bozuk" der (toplu okuma sınırı aşmaz).
        try {
            RoadGraph.deserialize(bytes.copyOf(bytes.size / 2))
            fail("FormatException bekleniyordu")
        } catch (_: FormatException) {
        }
    }

    @Test
    fun `yeni graf bisiklet bilgisini taşır, kaydedip okuyunca aynı`() {
        val g = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "lane"))
        assertTrue(g.hasBikewayData)
        val back = RoadGraph.deserialize(g.serialize())
        assertTrue(back.hasBikewayData)
        val bikeWays = (0 until back.edgeCount).filter { back.isBikeway(it) }.map { back.osmIdOf(it) }.toSet()
        assertEquals(TestGraphs.EAST_WAYS, bikeWays)
        assertNull(BikewayText.mapWarning(back, Lang.TR))
    }

    @Test
    fun `eski harita dosyası sessizce bozulmaz, okunur ve bisiklet bilgisi yok, güncelle der`() {
        // Biçim (KNG1 v2) aynı kaldı: eski uygulama yeni dosyayı, yeni uygulama eski dosyayı okur.
        assertEquals(2, RoadGraph.FORMAT_VERSION)
        val g = TestGraphs.parallel()
        val bytes = g.serialize()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(9 * 4, 0) // E13 öncesi başlık: özellik alanı 0
        val old = RoadGraph.deserialize(bytes)
        assertEquals(g.edgeCount, old.edgeCount)
        assertFalse(old.hasBikewayData)
        assertTrue(BikewayText.mapWarning(old, Lang.TR)!!.contains("İndir / güncelle"))
        assertTrue(BikewayText.mapWarning(old, Lang.AZ)!!.contains("Yüklə / yenilə"))
        // Daha eski biçim (v1) yine açıkça reddedilir (denetleyici "yeniden indir" der).
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 1)
        try {
            RoadGraph.deserialize(bytes)
            fail("FormatException bekleniyordu")
        } catch (_: FormatException) {
        }
    }
}
