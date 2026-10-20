package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.MemoryRoadFiles
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.surface.MarkStore
import com.kuryelog.logic.map.ui.Toasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WEEM eki (MAP.md 9.10, 9.11): "aynıysa yazma", 30 sn kısıtı, okunamayan dosyanın `damaged` kopyası, yeniden açılış. */
class RoadFilesTest {
    private class CountingFiles : RoadFiles {
        val mem = MemoryRoadFiles()
        val writes = ArrayList<String>()
        override fun read(name: String) = mem.read(name)
        override fun write(name: String, text: String) { writes += name; mem.write(name, text) }
    }

    private fun book(files: RoadFiles, clock: FakeClock) = RoadBook(files, StreetPrefsStore(MemoryMapPrefs()), Toasts(), { MapSettings() }, clock)

    private fun drive(b: RoadBook, clock: FakeClock, seconds: Int, north0: Double = 0.0) {
        var n = north0
        repeat(seconds) {
            clock.advance(1000)
            n += 5
            b.onFix(LearnFix(clock.nowMs, 40.4 + n / 111320, 49.85, 18.0, way = 5, name = "Yol"))
        }
    }

    @Test
    fun `sürüşte 30 sn'de bir yazılır, değişmeyen dosya yeniden yazılmaz`() {
        val clock = FakeClock()
        val files = CountingFiles()
        val b = book(files, clock)
        b.startDay("yolgo-${clock.nowMs}")
        drive(b, clock, 29)
        assertTrue(files.writes.isEmpty())
        drive(b, clock, 2)
        assertTrue(RoadBook.WAYS in files.writes)
        assertTrue(RoadBook.DAYS in files.writes)
        val n = files.writes.size
        drive(b, clock, 31, north0 = 155.0)
        val later = files.writes.drop(n)
        assertTrue(RoadBook.WAYS in later)
        assertTrue("değişmeyen puan dosyası yeniden yazılmaz", RoadBook.RATE !in later && RoadBook.GHOST !in later)
        val m = files.writes.size
        b.onPause()
        b.onPause()
        assertTrue(files.writes.size - m <= 2)
        val again = book(files, clock)
        assertEquals(b.state.k.ways[5L]!!.m, again.state.k.ways[5L]!!.m, 1.0)
        assertEquals("Yol", again.state.k.nameOf(5))
    }

    @Test
    fun `okunamayan dosya damaged kopyası alınır, boş başlar, sonra üstüne yazılır`() {
        val clock = FakeClock()
        val files = MemoryRoadFiles()
        files.write(RoadBook.RATE, "{bozuk")
        files.write(RoadBook.DAYS, "[]")
        files.write(MarkStore.FILE, "{\"x\":1}")
        val b = book(files, clock)
        assertEquals("{bozuk", files.read("${RoadBook.RATE}.damaged-${clock.nowMs}"))
        assertEquals("[]", files.read(RoadBook.DAYS_DAMAGED))
        assertNotNull(files.read("${MarkStore.FILE}.damaged-${clock.nowMs}"))
        assertTrue(b.state.k.ratings.isEmpty())
        b.rate(3, 40)
        assertEquals(40, RoadKnowledge.decodeMap(files.read(RoadBook.RATE)) { (it as Number).toInt() }[3L])
    }

    @Test
    fun `eski 1 ve -1 puanları göç eder, gün kapalıyken öğrenme yok`() {
        val clock = FakeClock()
        val files = MemoryRoadFiles()
        files.write(RoadBook.RATE, "{\"1\":1,\"2\":-1,\"3\":55}")
        val b = book(files, clock)
        assertEquals(mapOf(1L to 90, 2L to 25, 3L to 55), b.state.k.ratings.toMap())
        drive(b, clock, 10)
        assertNull(b.state.k.ways[5L])
    }

    @Test
    fun `gün sonu süren geçişi kapatır ve zorla yazar`() {
        val clock = FakeClock()
        val files = CountingFiles()
        val b = book(files, clock)
        b.startDay("yolgo-${clock.nowMs}")
        drive(b, clock, 5)
        b.endDay()
        assertTrue(RoadBook.WAYS in files.writes)
        drive(b, clock, 5, north0 = 25.0)
        assertEquals(20.0, b.state.k.ways[5L]!!.m, 1.0)
    }
}
