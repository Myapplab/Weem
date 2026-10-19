package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.concurrent.thread

/**
 * YolGo `log_store_test.dart` son 3 test (A6.2b, MAP.md 12.2, 12.3, 12.13): rota değişimi ayrı sürüş açar (ilk sürüş `reroute`'u içerir, ikincisi içermez; gün dosyası
 * 9 satır; sonda `ride.active.v1` yok) · eşzamanlı boşaltma ve Bakü gece yarısı veriyi kaybetmez (iki olay başlangıç gününün dosyasında) · etkin sürüş silinemez.
 * `DateTime(2026,10,2,10)` yerel → sabit ms; gece yarısı testi sahte [FakeClock] (bölge Bakü).
 */
class RideLogWriterTest {
    private lateinit var dir: File
    private val prefs = MemoryMapPrefs()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("duzyol-logs-").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `rota değişimi ayrı sürüş açar, yeniden rota ve günlük geçmişi korunur`() {
        val clock = FakeClock(ms("2026-10-02T06:00:00Z")) // 10:00 Bakü
        val logger = RideLogWriter(dir, prefs, clock)
        val dayId = "yolgo-${clock.nowMs}"
        logger.setDay(dayId)
        logger.event("route"); logger.event("reroute"); logger.event("arrive")
        logger.setDay(dayId)
        logger.event("route"); logger.event("route_clear")
        logger.flush()
        val entries = logger.records()
        val rides = entries.filter { it.kind == "ride" }
        assertEquals(2, rides.size)
        assertTrue(rides.last().file.readText().contains("reroute"))
        assertFalse(rides.first().file.readText().contains("reroute"))
        assertEquals(9, entries.single { it.kind == "day" }.file.readLines().size)
        assertNull(prefs.getString("ride.active.v1"))
    }

    @Test
    fun `eşzamanlı flush ve gece yarısı günlükleri veriyi kaybetmez`() {
        val clock = FakeClock(ms("2026-10-02T19:59:59Z")) // 23:59:59 Bakü
        val logger = RideLogWriter(dir, prefs, clock)
        logger.setDay("yolgo-${clock.nowMs}")
        logger.event("before_midnight")
        val first = thread { logger.flush() }
        clock.nowMs = ms("2026-10-02T20:00:00Z") // 00:00 Bakü
        logger.event("after_midnight")
        val second = thread { logger.flush() }
        first.join(); second.join()
        logger.flush()
        val text = File(dir, "2026-10-02.jsonl").readText()
        assertTrue(text.contains("before_midnight"))
        assertTrue(text.contains("after_midnight"))
        assertFalse(File(dir, "2026-10-03.jsonl").exists())
    }

    @Test
    fun `aktif sürüş tek kayıt silme ile kaybedilmez`() {
        val clock = FakeClock(ms("2026-10-02T06:00:00Z"))
        val logger = RideLogWriter(dir, prefs, clock)
        logger.setDay("yolgo-${clock.nowMs}")
        logger.event("route")
        val ride = logger.records().single { it.kind == "ride" }
        assertThrows(IllegalStateException::class.java) { logger.delete(ride) }
        logger.enabled = false
        logger.flush()
        logger.delete(ride)
        assertFalse(ride.file.exists())
        assertEquals("day", logger.records().single().kind)
    }
}
