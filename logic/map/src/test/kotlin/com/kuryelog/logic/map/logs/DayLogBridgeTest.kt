package com.kuryelog.logic.map.logs

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.day.DayController
import com.kuryelog.logic.map.day.DayFix
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Gün motoru adımları → sürüş günlüğü (MAP.md 12.2, 14.5): gün kimliği, `shift_` öneki, sürüş açılıp kapanması, gün kapanışı. */
class DayLogBridgeTest {
    private lateinit var dir: File
    private val clock = FakeClock()

    @Before fun setUp() { dir = Files.createTempDirectory("duzyol-logs-").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test
    fun `gün başlayıp biterken olaylar shift öneki ve gün kimliğiyle yazılır`() {
        val writer = RideLogWriter(dir, MemoryMapPrefs(), clock)
        val bridge = DayLogBridge(writer)
        val day = DayController(MemoryMapPrefs(), { Lang.TR })
        bridge.onStep(day.startDay(clock.nowMs, "home_departure"))
        bridge.onStep(day.onFix(DayFix(clock.nowMs, 40.4, 49.8, 3.0, 5.0), clock.nowMs))
        clock.advance(60_000)
        bridge.onStep(day.endDay(clock.nowMs, "manual"))
        assertEquals(null, writer.activeDayId)
        val rec = writer.records()
        val log = rec.single { it.kind == "day" }
        val lines = log.file.readLines().map { JSONObject(it) }
        val names = lines.filter { it.optString("k") == "ev" }.map { it.getString("e") }
        assertTrue(names.toString(), names.first() == "ride_start" && "shift_day_start" in names && "shift_day_end" in names)
        assertEquals("ride_end", names.last())
        assertTrue(lines.all { it.getString("dayId") == "yolgo-${clock.nowMs - 60_000}" })
        val ride = rec.single { it.kind == "ride" }
        assertTrue(ride.file.readText().contains("shift_day_end"))
    }
}
