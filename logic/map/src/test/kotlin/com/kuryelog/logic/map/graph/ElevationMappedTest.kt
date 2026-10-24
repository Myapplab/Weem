package com.kuryelog.logic.map.graph

import com.kuryelog.logic.map.geo.BBox
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S39: diskteki SRTM karoları bellek eşlemeli okunur (yığında 2 × 26 MB yok); örnekleme bayt dizisiyle aynı. */
class ElevationMappedTest {
    @Test
    fun `bellek eşlemeli karo bayt dizisiyle aynı yüksekliği verir`() {
        val size = 1201
        val buf = ByteBuffer.allocate(size * size * 2)
        for (r in 0 until size) for (c in 0 until size) buf.putShort(((r * 7 + c * 3) % 900 - 20).toShort())
        buf.putShort((5 * size + 5) * 2, (-32768).toShort()) // boşluk
        val bytes = buf.array()
        val dir = Files.createTempDirectory("dem").toFile().apply { deleteOnExit() }
        File(dir, "N40E049.hgt").writeBytes(bytes)
        val mapped = ElevationModel.loadLocal(BBox(40.2, 49.2, 40.8, 49.8), dir)
        assertTrue(mapped.available)
        val heap = ElevationModel(listOf(HgtTile(40, 49, bytes)))
        for ((lat, lon) in listOf(40.5 to 49.5, 40.9958 to 49.0042, 40.123 to 49.987, 40.0 to 49.999)) {
            assertEquals(heap.at(lat, lon), mapped.at(lat, lon), 0.0)
        }
    }
}
