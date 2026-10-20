package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Sürüş günlüğü karakterizasyonu (MAP.md 12.2–12.3, 12.13 "Yeni"): açan/kapatan olaylar, `ride.active.v1` ile devam, gps seyreltme, `env`, `logging_off`. */
class RideLogWriterBehaviorTest {
    private lateinit var dir: File
    private val prefs = MemoryMapPrefs()
    private val clock = FakeClock()

    @Before fun setUp() { dir = Files.createTempDirectory("duzyol-logs-").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun lines(name: String) = File(dir, name).readLines().map { JSONObject(it) }
    private fun events(name: String) = lines(name).filter { it.optString("k") == "ev" }.map { it.getString("e") }
    private fun dayFile() = "${com.kuryelog.logic.core.LogTime.day(clock.nowMs, clock.zone())}.jsonl"

    @Test
    fun `sürüş açan olaylar sürüş açar, kapatanlar kapatır, reroute ve mode dokunmaz`() {
        val w = RideLogWriter(dir, prefs, clock)
        w.setDay("yolgo-${clock.nowMs}")
        w.event("mode"); w.event("reroute")
        assertNull(prefs.getString("ride.active.v1"))
        w.event("shift_order_start")
        assertTrue(prefs.getString("ride.active.v1")!!.contains("_ride_"))
        w.event("shift_leave_restaurant") // açık sürüş varsa bir şey olmaz
        w.event("shift_arrive_customer")
        assertNull(prefs.getString("ride.active.v1"))
        w.flush()
        assertEquals(listOf("mode", "reroute", "ride_start", "shift_order_start", "shift_leave_restaurant", "shift_arrive_customer", "ride_end"), events(dayFile()))
        val ride = w.records().single { it.kind == "ride" }
        assertEquals(listOf("ride_start", "shift_order_start", "shift_leave_restaurant", "shift_arrive_customer", "ride_end"), events(ride.name))
        assertEquals("shift_order_start", lines(ride.name).first().getString("reason"))
    }

    @Test
    fun `süreç yeniden başlayınca ride active v1 aynı sürüş dosyasına devam eder`() {
        val a = RideLogWriter(dir, prefs, clock)
        a.setDay("yolgo-${clock.nowMs}")
        a.event("route")
        a.flush()
        val name = prefs.getString("ride.active.v1")!!
        clock.advance(1000)
        val b = RideLogWriter(dir, prefs, clock)
        b.setDay("yolgo-${clock.nowMs - 1000}")
        b.event("reroute")
        b.flush()
        assertTrue(events(name).contains("reroute"))
        assertEquals(1, b.records().count { it.kind == "ride" })
    }

    @Test
    fun `gps 900 ms içinde atlanır, sp eksi bir yazılır, kapalıyken ve günsüz yazılmaz`() {
        val w = RideLogWriter(dir, prefs, clock)
        w.gps(clock.nowMs - 5000, 40.4, 49.8, 5.0, 3.0, 90.0, 10.0) // gün yokken de fix zamanı sayılır
        w.flush()
        assertFalse(File(dir, dayFile()).exists()) // gün yok
        w.setDay("yolgo-${clock.nowMs}")
        w.gps(clock.nowMs + 1, 40.4, 49.8, 5.04, -1.0, 90.0, 10.0)
        w.gps(clock.nowMs + 800, 40.4, 49.8, 5.0, 3.0, 90.0, 10.0)
        w.gps(clock.nowMs + 1000, 40.4000004, 49.8, 5.0, 3.0, 90.0, 10.0)
        w.flush()
        val gps = lines(dayFile()).filter { it.optString("k") == "gps" }
        assertEquals(2, gps.size)
        assertEquals(-1.0, gps[0].getDouble("sp"), 0.0)
        assertEquals(5.0, gps[0].getDouble("ac"), 0.0)
        assertEquals(40.4, gps[1].getDouble("la"), 0.0)
        assertTrue(gps[0].has("t") && gps[0].getString("dayId").startsWith("yolgo-"))
        w.enabled = false
        w.gps(clock.nowMs + 9000, 40.4, 49.8, 5.0, 3.0, 90.0, 10.0)
        w.flush()
        assertEquals(2, lines(dayFile()).count { it.optString("k") == "gps" })
    }

    @Test
    fun `env yalnız taze basınçla yazılır, yükseklik formülü, titreşim sıfır`() {
        val w = RideLogWriter(dir, prefs, clock)
        w.setDay("yolgo-${clock.nowMs}")
        w.env(null, null)
        w.env(1013.25, clock.nowMs - 3001)
        w.env(1013.25, clock.nowMs - 3000)
        w.env(900.0, clock.nowMs)
        w.flush()
        val env = lines(dayFile()).filter { it.optString("k") == "env" }
        assertEquals(2, env.size)
        assertEquals(0.0, env[0].getDouble("balt"), 0.0)
        assertEquals(44330 * (1 - Math.pow(900.0 / 1013.25, 0.1903)), env[1].getDouble("balt"), 0.01)
        assertEquals(0.0, env[1].getDouble("vr"), 0.0); assertEquals(0.0, env[1].getDouble("vm"), 0.0)
    }

    @Test
    fun `kayıt kapanınca açık sürüş logging_off ile biter, gün kapanınca day_scope_closed`() {
        val w = RideLogWriter(dir, prefs, clock)
        w.setDay("yolgo-${clock.nowMs}")
        w.event("route")
        w.enabled = false
        w.flush()
        assertEquals("logging_off", lines(w.records().single { it.kind == "ride" }.name).last().getString("reason"))
        w.enabled = true
        w.event("route")
        w.setDay(null)
        w.flush()
        assertNull(prefs.getString("ride.active.v1"))
        val last = w.records().filter { it.kind == "ride" }.maxByOrNull { it.name }!!
        assertEquals("day_scope_closed", lines(last.name).last().getString("reason"))
    }

    @Test
    fun `açık günün tarihi silinemez, kapanınca silinir, dışa aktarma ad`() {
        val w = RideLogWriter(dir, prefs, clock)
        val day = com.kuryelog.logic.core.LogTime.day(clock.nowMs, clock.zone())
        w.setDay("yolgo-${clock.nowMs}")
        w.event("mode")
        var failed = false
        try { w.deleteDay(day) } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        val export = w.export(File(dir, "exports"), day)
        assertEquals("WEEM_surus_$day.json", export.name)
        w.setDay(null)
        w.deleteDay(day)
        assertTrue(w.records().isEmpty())
    }
}
