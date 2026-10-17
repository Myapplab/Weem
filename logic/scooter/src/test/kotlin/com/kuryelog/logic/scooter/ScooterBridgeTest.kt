package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.EnergyBlock
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.ScooterLive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * RT `ScooterBridgeTest.kt` (6 test; SCOOTER.md 13.2): kaynaksız hiçbir şey değişmez; kaynakla scooter'ın enerji bloğu şemanın tam
 * şeklinde düşer; hiçbir scooter hatası ya da bozuk değer sipariş kaydını bozamaz ya da değer uyduramaz. Eski `Source` →
 * [ScooterBridgeImpl.Source]; `EnergySegments.energy()` → [EnergyShape.notMeasured]; `fromLegs`/`fromIdle` bacak satırları
 * `:economics`'te (A4.1) olduğundan pencere denetimi köprü çağrılarıyla yapılır.
 */
class ScooterBridgeTest {
    /** Sürüş Paneli 0.3.0 `ScooterEnergy.block()`'un kurduğu blok (anahtarlar, iç içelik ve değer türleri orada olduğu gibi). */
    private fun spBlock(source: String = "surus_paneli"): Map<String, Any?> = linkedMapOf(
        "source" to source, "power_verified" to false,
        "internal" to linkedMapOf<String, Any?>("pct_start" to 80.0, "pct_end" to 78.0, "v_avg" to 50.2, "a_avg" to -6.5),
        "external" to linkedMapOf<String, Any?>("pct_start" to 64.0, "pct_end" to 61.0, "v_avg" to 49.8, "a_avg" to -3.1),
        "external_attached" to true, "external_feeding" to true, "vxa_w_avg" to 480.5, "wh" to 12.25, "esc_w_avg" to null,
        "temp_c" to 24.0, "scooter_speed_kmh_avg" to 18.4, "odometer_km_start" to 1201.3, "odometer_km_end" to 1203.9)

    private class Fake(val block: Map<String, Any?>?, val battery: Double? = 77.0) : ScooterBridgeImpl.Source {
        val windows = ArrayList<Pair<Long, Long>>(); val accepted = ArrayList<Pair<String, Long>>(); val done = ArrayList<String>()
        override fun energy(fromWallMs: Long, toWallMs: Long): Map<String, Any?>? { windows += fromWallMs to toWallMs; return block }
        override fun batteryPct(): Double? = battery
        override fun orderAccepted(tripId: String, atWallMs: Long) { accepted += tripId to atWallMs }
        override fun orderDone(tripId: String, atWallMs: Long) { done += tripId }
    }
    private object Broken : ScooterBridgeImpl.Source {
        override fun energy(fromWallMs: Long, toWallMs: Long): Map<String, Any?>? = throw IllegalStateException("link lost")
        override fun batteryPct(): Double? = throw IllegalStateException("link lost")
        override fun orderAccepted(tripId: String, atWallMs: Long) = throw IllegalStateException("link lost")
        override fun orderDone(tripId: String, atWallMs: Long) = throw IllegalStateException("link lost")
    }

    private fun bridge(source: ScooterBridgeImpl.Source) = ScooterBridgeImpl(source, MutableStateFlow<ScooterLive?>(null), MutableSharedFlow())
    private fun energy(b: ScooterBridge, from: Long, to: Long): EnergyBlock? = runBlocking { b.energy(from, to) }
    private fun keys(j: JSONObject): Map<String, Any?> = j.keys().asSequence().associateWith { k -> j.optJSONObject(k)?.let(::keys) }
    private val schemaKeys = keys(EnergyShape.notMeasured().json)

    @Test fun withoutSourceNothingChanges() {
        // Köprü yok (NONE): enerji ve pil yok, çağrılar etkisiz; çağıran bunu "ölçülmedi" sayar (`not_measured`, tüm anahtarlar null).
        assertNull(energy(ScooterBridge.NONE, 1_000, 2_000))
        assertNull(ScooterBridge.NONE.batteryPct())
        ScooterBridge.NONE.orderAccepted("t1", 1_000)
        val shape = EnergyShape.notMeasured()
        assertEquals("not_measured", shape.source); assertFalse(shape.measured)
        // Anahtar kümesi sözleşmedir (JVM'deki org.json sırayı korumaz; Android'inki korur).
        assertEquals(setOf("source", "power_verified", "internal", "external", "external_attached", "external_feeding", "vxa_w_avg", "wh", "esc_w_avg",
            "temp_c", "scooter_speed_kmh_avg", "odometer_km_start", "odometer_km_end"), shape.json.keys().asSequence().toSet())
        assertEquals(setOf("pct_start", "pct_end", "v_avg", "a_avg"), shape.json.getJSONObject("internal").keys().asSequence().toSet())
        for (k in shape.json.keys()) if (k != "source" && k != "power_verified" && k != "internal" && k != "external") assertTrue(k, shape.json.isNull(k))
    }

    @Test fun scooterBlockKeepsTheSchemaShape() {
        val e = energy(bridge(Fake(spBlock())), 1_000, 2_000)!!.json
        assertEquals(schemaKeys, keys(e))
        assertEquals("surus_paneli", e.getString("source")); assertFalse(e.getBoolean("power_verified"))
        assertEquals(12.25, e.getDouble("wh"), 1e-9); assertEquals(480.5, e.getDouble("vxa_w_avg"), 1e-9); assertTrue(e.isNull("esc_w_avg"))
        assertEquals(78.0, e.getJSONObject("internal").getDouble("pct_end"), 1e-9); assertEquals(-3.1, e.getJSONObject("external").getDouble("a_avg"), 1e-9)
        assertTrue(e.getBoolean("external_attached")); assertTrue(e.getBoolean("external_feeding"))
        assertEquals(1203.9, e.getDouble("odometer_km_end"), 1e-9)
    }

    @Test fun malformedValuesBecomeNullNeverInvented() {
        val bad = spBlock().toMutableMap().apply {
            put("wh", Double.NaN); put("vxa_w_avg", Double.POSITIVE_INFINITY); put("temp_c", 24); put("external_feeding", "yes")
            put("internal", mapOf("pct_start" to 80.0)); remove("external"); put("extra_key", 1.0); remove("power_verified")
        }
        val e = energy(bridge(Fake(bad)), 1_000, 2_000)!!.json
        assertEquals(schemaKeys, keys(e))
        assertTrue(e.isNull("wh")); assertTrue(e.isNull("vxa_w_avg")); assertEquals(24.0, e.getDouble("temp_c"), 1e-9)
        assertTrue(e.isNull("external_feeding")); assertFalse(e.getBoolean("power_verified"))
        assertEquals(80.0, e.getJSONObject("internal").getDouble("pct_start"), 1e-9); assertTrue(e.getJSONObject("internal").isNull("a_avg"))
        assertTrue(e.getJSONObject("external").isNull("pct_start"))
        // An unknown source rejects the block: the row stays valid and claims nothing.
        assertEquals(EnergyShape.notMeasured(), energy(bridge(Fake(spBlock(source = "esc_guess"))), 1_000, 2_000))
        assertEquals(EnergyShape.notMeasured(), energy(bridge(Fake(null)), 1_000, 2_000))
    }

    @Test fun openWindowIsNotMeasuredAndLegsUseTheirOwnWindow() {
        val fake = Fake(spBlock()); val b = bridge(fake)
        assertEquals("not_measured", energy(b, 2_000, 2_000)!!.source) // pencere açık/boş
        assertEquals("not_measured", energy(b, 2_000, 1_000)!!.source) // ters
        assertTrue(fake.windows.isEmpty())
        assertEquals("surus_paneli", energy(b, 1_000, 2_000)!!.source)
        assertEquals(listOf(1_000L to 2_000L), fake.windows)
    }

    @Test fun scooterFailuresNeverReachTheOrderLog() {
        val b = bridge(Broken)
        assertEquals(EnergyShape.notMeasured(), energy(b, 1_000, 2_000))
        assertNull(b.batteryPct())
        b.orderAccepted("t1", 1_000); b.orderDone("t1", 2_000) // atmaz
        for (v in listOf(Double.NaN, -1.0, 100.5)) assertNull(v.toString(), bridge(Fake(spBlock(), battery = v)).batteryPct())
        val fake = Fake(spBlock(), battery = 64.0); val ok = bridge(fake)
        assertEquals(64.0, ok.batteryPct()!!, 1e-9)
        ok.orderAccepted("t9", 5_000); assertEquals(listOf("t9" to 5_000L), fake.accepted)
        ok.orderDone("t9", 6_000); assertEquals(listOf("t9"), fake.done)
    }

    /** With KL_ROWS_OUT set, dumps scooter-filled energy blocks for docs/ekonomi-v2/araclar/validate_rows.py (energySegment `energy` payı). */
    @Test fun dumpForSchemaCheck() {
        val dir = System.getenv("KL_ROWS_OUT") ?: return
        File(dir).mkdirs()
        val b = bridge(Fake(spBlock()))
        val rows = JSONArray().put(energy(b, 1_000, 2_000)!!.json).put(EnergyShape.notMeasured().json)
        File(dir, "scooter-energy.jsonl").writeText((0 until rows.length()).joinToString("\n") { JSONObject().put("def", "energyBlock").put("row", rows.get(it)).toString() } + "\n")
    }
}
