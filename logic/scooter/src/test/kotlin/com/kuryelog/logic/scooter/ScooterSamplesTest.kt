package com.kuryelog.logic.scooter

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * RT `ScooterSamplesTest.kt` (5 test; `fileIsBackedUpAndExported` yedek/dışa aktarma kısmı `:core` `BackupFiles`'a gider, A4.3; burada
 * dosya adı sözleşmesi ve konum yok denetimi) ve RT `ScooterOrderEnergyTest.kt`'nin adım (`step`) testleri (SCOOTER.md 13.2).
 * Birleşik Sürüş Paneli: bir vardiyanın 1 Hz scooter satırları (economics-scooter.jsonl) şemayı, işaretleri ve null'ları korur.
 */
class ScooterSamplesTest {
    private val riding = ScooterSamples.Values(1_760_000_000_123L, 5_000L, 18.43, -452.37, 78.0, 52.594, -8.611, 61.0, 49.807, -0.42, 24.04, 4373.46)
    private val keys = listOf("schemaVersion", "kind", "wall_ms", "mono_ns", "source", "scooter_id", "scooter_id_source", "power_verified", "speed_kmh",
        "vxa_w", "int_pct", "int_v", "int_a", "ext_pct", "ext_v", "ext_a", "temp_c", "odometer_km")

    @Test fun rowHasTheSchemaKeysAndRoundedValues() {
        val j = ScooterSamples.row(riding, "scooter-1", "default_single_scooter")
        assertEquals(keys.toSet(), j.keys().asSequence().toSet())
        assertEquals("scooter_sample", j.getString("kind")); assertEquals("surus_paneli", j.getString("source")); assertFalse(j.getBoolean("power_verified"))
        assertEquals(5_000_000_000L, j.getLong("mono_ns")); assertEquals(1_760_000_000_123L, j.getLong("wall_ms"))
        assertEquals(18.4, j.getDouble("speed_kmh"), 0.0); assertEquals(52.59, j.getDouble("int_v"), 0.0); assertEquals(-8.61, j.getDouble("int_a"), 0.0)
        assertEquals(78.0, j.getDouble("int_pct"), 0.0); assertEquals(4373.5, j.getDouble("odometer_km"), 0.0); assertEquals(24.0, j.getDouble("temp_c"), 0.0)
        // Written compactly: no long binary fractions in a row written every second.
        assertTrue(j.toString(), Regex("\"int_v\":52\\.59[,}]").containsMatchIn(j.toString()))
    }

    @Test fun powerIsConsumptionPositiveCurrentsKeepTheBmsSign() {
        val j = ScooterSamples.row(riding, "scooter-1", "default_single_scooter")
        assertEquals(452.4, j.getDouble("vxa_w"), 0.0) // panel −452 W (discharge) = 452 W consumed, like energy.vxa_w_avg
        assertEquals(-0.42, j.getDouble("ext_a"), 0.0) // + charge / − discharge, like energy.external.a_avg
        val charging = ScooterSamples.row(riding.copy(chargeW = 35.0, intA = 0.7), "scooter-2", "settings")
        assertEquals(-35.0, charging.getDouble("vxa_w"), 0.0); assertEquals(0.7, charging.getDouble("int_a"), 0.0)
        assertEquals("scooter-2", charging.getString("scooter_id")); assertEquals("settings", charging.getString("scooter_id_source"))
        assertEquals("0.0", ScooterSamples.row(riding.copy(chargeW = 0.01), "s", "settings").get("vxa_w").toString()) // never "-0.0"
    }

    @Test fun missingOrBrokenValuesAreNullNeverInvented() {
        val j = ScooterSamples.row(riding.copy(speedKmh = null, chargeW = Double.NaN, extPct = null, extV = null, extA = Double.POSITIVE_INFINITY, odometerKm = null), "scooter-1", "default_single_scooter")
        for (k in listOf("speed_kmh", "vxa_w", "ext_pct", "ext_v", "ext_a", "odometer_km")) assertTrue(k, j.isNull(k))
        assertEquals(keys.toSet(), j.keys().asSequence().toSet())
        assertNotNull(JSONObject(j.toString())) // the line parses back
    }

    /** RT `fileIsBackedUpAndExported`'in scooter payı: ad ve konum yok (yedek/dışa aktarma ifadeleri `:core` BackupFiles testinde). */
    @Test fun fileNameIsTheContractAndTheRowHasNoPlace() {
        assertEquals("economics-scooter.jsonl", ScooterSamples.FILE)
        val noPlace = ScooterSamples.row(riding, "scooter-1", "default_single_scooter").keys().asSequence().toSet()
        assertTrue(noPlace.none { it in setOf("lat", "lon", "loc") })
    }

    /** With KL_ROWS_OUT set, dumps rows for docs/ekonomi-v2/araclar/validate_rows.py (`$defs/scooterSample`). */
    @Test fun dumpForSchemaCheck() {
        val dir = System.getenv("KL_ROWS_OUT") ?: return
        File(dir).mkdirs()
        val rows = listOf(ScooterSamples.row(riding, "scooter-1", "default_single_scooter"),
            ScooterSamples.row(riding.copy(extPct = null, extV = null, extA = null, chargeW = null, speedKmh = null), "scooter-2", "settings"))
        File(dir, "scooter-samples.jsonl").writeText(rows.joinToString("\n") { JSONObject().put("def", "scooterSample").put("row", it).toString() } + "\n")
    }

    // ---- RT ScooterOrderEnergyTest: adım payı ----

    @Test fun stepSplitsDischargeAndChargeLikeTheEnergyBlock() {
        val out = ScooterSamples.step(1_000, -360.0, 2_000, -360.0, 3_000)!!
        assertEquals(0.1, out.usedWh, 1e-12); assertEquals(0.0, out.recoveredWh, 0.0); assertEquals(1_000L, out.ms)
        val back = ScooterSamples.step(1_000, 72.0, 2_000, 72.0, 3_000)!!
        assertEquals(0.0, back.usedWh, 0.0); assertEquals(0.02, back.recoveredWh, 1e-12)
        // Trapezoid across a sign change: the step's average decides.
        assertEquals(0.04, ScooterSamples.step(0, -360.0, 1_000, 72.0, 3_000)!!.usedWh, 1e-12)
    }

    @Test fun gapsAndMissingPowerAreNotIntegrated() {
        assertNull(ScooterSamples.step(0, -100.0, 3_001, -100.0, 3_000)) // Bluetooth gap
        assertNull(ScooterSamples.step(2_000, -100.0, 2_000, -100.0, 3_000)) // same or older sample
        assertNull(ScooterSamples.step(2_000, -100.0, 1_000, -100.0, 3_000))
        assertNull(ScooterSamples.step(0, null, 1_000, -100.0, 3_000))
        assertNull(ScooterSamples.step(0, -100.0, 1_000, Double.NaN, 3_000))
        assertNotNull(ScooterSamples.step(0, -100.0, 3_000, -100.0, 3_000))
    }

    /** D10 (13 Ekim) adım payı: harici paket bilinip okunmamışsa adım `complete = false` ve Wh yok sayılır (siparişe `partialMs`). */
    @Test fun d10InternalOnlyStepIsMarkedIncomplete() {
        val step = ScooterSamples.step(0, 104.0, 1_000, 104.0, 3_000, complete = false)!!
        assertFalse(step.complete); assertEquals(0.0, step.usedWh, 0.0)
        assertEquals(1_000L, step.ms)
    }

    /** Örneklerden adım: iki paket okunuyorsa tam, harici bilinip okunmuyorsa eksik (RS `ScooterLink.sample`). */
    @Test fun stepBetweenSamplesFollowsBothPacks() {
        fun s(t: Long, w: Double, known: Boolean, extRead: Boolean) = ScooterEnergy.Sample(1_000_000 + t, t, 80.0, 52.0, -w / 52.0,
            if (extRead) 60.0 else null, if (extRead) 44.0 else null, if (extRead) -1.0 else null, w, 25.0, 18.0, 4373.0, known)
        val both = ScooterSamples.stepOf(s(0, -360.0, true, true), s(1_000, -360.0, true, true))!!
        assertTrue(both.complete); assertEquals(0.1, both.usedWh, 1e-12)
        val partial = ScooterSamples.stepOf(s(0, -360.0, true, true), s(1_000, -360.0, true, false))!!
        assertFalse(partial.complete)
        val noExternal = ScooterSamples.stepOf(s(0, -360.0, false, false), s(1_000, -360.0, false, false))!!
        assertTrue(noExternal.complete)
        assertNull(ScooterSamples.stepOf(null, s(1_000, -360.0, false, false)))
    }
}
