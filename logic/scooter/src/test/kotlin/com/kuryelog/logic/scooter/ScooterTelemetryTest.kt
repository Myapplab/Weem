package com.kuryelog.logic.scooter

import org.junit.Assert.*
import org.junit.Test

/** Kayıt satırı, 1 Hz örnek ve ayar metni (NB `BleCapture`'da testsizdi; WEEM eki, 7 Ekim saha baytlarıyla). */
class ScooterTelemetryTest {
    private fun read(key: String): NinebotMetrics.Read = NinebotMetrics.vehicle().first { it.key == key }

    /** 2026-10-07 saha baytları (DashboardTest ile aynı): hız 8,5; iç paket 53,0 V / 3,2 A; pil %94. */
    private fun metrics(): NinebotMetrics {
        val m = NinebotMetrics(); val pack = NinebotMetrics.battery(0x07)
        m.accept(read("scooter_speed_kmh"), byteArrayOf(0x55, 0x00), 1000)
        m.accept(pack[0], byteArrayOf(0xb3.toByte(), 0x14), 1000)
        m.accept(pack[1], byteArrayOf(0x3c, 0x01), 1000)
        m.accept(read("battery_percent"), byteArrayOf(0x5e, 0x00), 1000)
        m.accept(read("odometer_km"), byteArrayOf(0x10, 0x27, 0x00, 0x00), 1000)
        return m
    }

    @Test fun noValuesMeansNoRowAndNoSample() {
        assertNull(ScooterTelemetry.telemetry(emptyList(), 1000))
        assertNull(ScooterTelemetry.sample(emptyList(), 1, 1, ""))
        assertEquals("Scooter ölçümü bekleniyor • GPS ayrı kaydedilir", ScooterTelemetry.measurements(emptyList(), 1000))
    }

    @Test fun telemetryKeepsFreshValueAndLastValueApart() {
        val v = metrics().values()
        val fresh = ScooterTelemetry.telemetry(v, 1500)!!
        assertEquals("community_g3_candidate", fresh.getString("mapping")); assertFalse(fresh.getBoolean("device_verified"))
        assertEquals(8.5, fresh.getDouble("scooter_speed_kmh"), 1e-9)
        assertEquals("voltage_times_current", fresh.getString("dashboard_power_source"))
        val f = fresh.getJSONObject("fields").getJSONObject("scooter_speed_kmh")
        assertTrue(f.getBoolean("fresh")); assertEquals(8.5, f.getDouble("value"), 1e-9); assertEquals(500L, f.getLong("age_ms"))
        // 10 sn sonra hız (3 sn tazelik) bayat: `value` null, `last_value` kalır; güç yok.
        val stale = ScooterTelemetry.telemetry(v, 11_000)!!
        val s = stale.getJSONObject("fields").getJSONObject("scooter_speed_kmh")
        assertFalse(s.getBoolean("fresh")); assertTrue(s.isNull("value")); assertEquals(8.5, s.getDouble("last_value"), 1e-9)
        assertTrue(stale.isNull("scooter_speed_kmh")); assertTrue(stale.isNull("dashboard_power_w")); assertTrue(stale.isNull("dashboard_power_source"))
    }

    @Test fun exportedTelemetryPassesTheAllowlistWithoutSecrets() {
        val row = ScooterTelemetry.telemetry(metrics().values(), 1500)!!
        val sample = org.json.JSONObject().put("elapsed_ms", 1).put("scooter_telemetry", row)
        val clean = LogExport.payload("sample", sample, false)!!.getJSONObject("scooter_telemetry")
        assertTrue(clean.getJSONObject("fields").has("scooter_speed_kmh"))
        assertEquals(8.5, clean.getDouble("scooter_speed_kmh"), 1e-9)
    }

    @Test fun sampleUsesTheFirstKnownExternalBoard() {
        val s = ScooterTelemetry.sample(metrics().values(), 1_000_500, 1500, "06")!!
        assertEquals(1_000_500L, s.wallMs); assertEquals(1500L, s.monoMs)
        assertTrue(s.externalKnown); assertEquals(8.5, s.speedKmh!!, 1e-9)
        assertFalse(s.bothPacks()) // harici paket bilinir ama okunmuyor (D10)
        assertFalse(ScooterTelemetry.sample(metrics().values(), 1, 1500, "")!!.externalKnown)
    }

    @Test fun measurementsTextListsFreshValuesAndSkipsSweep() {
        val text = ScooterTelemetry.measurements(metrics().values(), 1500)
        assertTrue(text, text.startsWith("G3 ölçümleri • sahada karşılaştırılmalı\nGüç: 167 W (V×A • doğrulanmamış)"))
        assertTrue(text, text.contains("\nHız (aday): 8.50 km/h")); assertTrue(text, text.contains("\nPil: 94.00 %"))
        assertTrue(text, text.contains("Pil [07] • Voltaj: 52.99 V"))
        assertFalse(ScooterTelemetry.measurements(metrics().values(), 20_000).contains("Hız (aday)")) // bayat değer listelenmez
    }
}
