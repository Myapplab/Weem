package com.kuryelog.logic.economics

import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/**
 * WEEM (2 telefon, LINK.md 4.6): Receiver'ın enerji yanıtı satır yazıldıktan sonra gelince aynı pencerenin ölçülmemiş satırına
 * düzeltme satırı; okuyucular dönem başına son satırı alır. Ölçülmüş satır ve ölçülmemiş yanıt değişmez.
 */
class LateEnergyTest {
    @get:Rule val tmp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    /** 2026-10-06 18:00 Baku. */
    private val t0 = 1791295200000L
    private var now = t0 + 3_600_000
    private val stamp = EnergySegments.Stamp(now, 42L, "scooter-1", "default_single_scooter", null)
    private val measured = JSONObject().put("source", "surus_paneli").put("power_verified", true).put("wh", 11.5)

    private fun idle(id: String, from: Long, to: Long) =
        EnergySegments.fromIdle(JSONObject().put("spellId", id).put("startAtMs", from).put("endAtMs", to).put("kmRidden", 1.0), stamp)!!

    @Test
    fun correctionReplacesOnlyAnUnmeasuredWindow() {
        val rows = listOf(idle("s1", t0, t0 + 60_000), idle("s2", t0 + 60_000, t0 + 120_000))
        val later = stamp.copy(wallMs = now + 5_000, monoNs = 99L)
        val c = EnergySegments.corrected(rows, t0 + 60_000, t0 + 120_000, measured, later)!!
        assertEquals("s2", c.getString("spellId"))
        assertEquals(11.5, c.getJSONObject("energy").getDouble("wh"), 0.0)
        assertEquals(now + 5_000, c.getLong("wall_ms"))
        assertEquals(99L, c.getLong("mono_ns"))
        assertEquals("not_measured", rows[1].getJSONObject("energy").getString("source"))
        // Pencere yok, ölçülmemiş yanıt, zaten ölçülmüş satır: düzeltme yok.
        assertNull(EnergySegments.corrected(rows, t0, t0 + 1, measured, later))
        assertNull(EnergySegments.corrected(rows, t0, t0 + 60_000, EnergySegments.energy(), later))
        assertNull(EnergySegments.corrected(rows + c, t0 + 60_000, t0 + 120_000, measured, later))
        // Okuyucu dönem başına son satırı alır.
        assertEquals(11.5, DayReport.idleWh(rows + c), 0.0)
    }

    @Test
    fun observerAppendsTheCorrectionToTheWindowsDay() {
        val log = LogStore(File(tmp.root, "logs"), now = { now }, zone = { baku })
        val env = object : EconomicsObserver.Env {
            override val log = log
            override val prefs = KuryelogPrefs.Memory()
            override val clock = object : Clock { override fun wallMs() = now; override fun monoMs() = now; override fun zone() = baku }
        }
        val legs = JSONObject().put("tripId", "t1").put("pkg", "com.wolt.courierapp").put("legs", JSONArray()
            .put(JSONObject().put("kind", "approach").put("startAtMs", t0).put("endAtMs", t0 + 300_000).put("km", 1.2)))
        for (r in EnergySegments.fromLegs(legs, stamp)) log.append(EnergySegments.FILE, r.toString(), log.day(t0))
        val obs = EconomicsObserver(env)
        assertTrue(obs.lateEnergy(t0, t0 + 300_000, measured))
        assertFalse(obs.lateEnergy(t0, t0 + 300_000, measured))
        assertFalse(obs.lateEnergy(t0 + 1, t0 + 2, measured))
        val lines = File(log.dayDir(log.day(t0)), EnergySegments.FILE).readLines().map(::JSONObject)
        assertEquals(2, lines.size)
        assertEquals("surus_paneli", lines[1].getJSONObject("energy").getString("source"))
        assertEquals("t1", lines[1].getString("tripId"))
    }
}
