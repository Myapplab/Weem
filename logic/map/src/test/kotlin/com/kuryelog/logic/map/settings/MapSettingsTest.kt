package com.kuryelog.logic.map.settings

import com.kuryelog.logic.map.route.RideParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `settings.v1` okuma ve türetilenler (MAP.md 14.2): YolGo `bikeway_e13_test.dart` "ayarlar" (1) ve `v140_features_test.dart`
 * "ayarlar" (2) testleri (A6.2a, MAP.md 5.2, 10.12). Uyarlama: `AppSettings` → `MapSettings` + `MapSettingsCodec`; `autoStartDay`,
 * `autoEndDay`, `followCourierStages` ve `voice` WEEM'de ayarda yoktur (14.2: tek vardiya kuralı `:core`), bu iddialar düştü.
 * Uygulama: A6.4.
 */
class MapSettingsTest {
    private fun roundTrip(s: MapSettings) = MapSettingsCodec.decode(MapSettingsCodec.toJson(s).toString())

    @Test
    fun `ayarlar varsayılan x0,85 ve katman açık, eski ayar dosyası bozulmaz, aralık 0,50–1,00`() {
        val d = MapSettings()
        assertEquals(85.0, d.bikewayCostPct, 0.0)
        assertTrue(d.showBikewayLayer)
        assertEquals(0.85, d.paramsFor(null).bikewayFactor, 1e-9)
        val old = MapSettingsCodec.decode("""{"qualityTolerancePct":12,"turnPenaltyS":8}""")
        assertEquals(85.0, old.bikewayCostPct, 0.0)
        assertTrue(old.showBikewayLayer)
        assertEquals(12.0, old.qualityTolerancePct, 0.0)
        val back = roundTrip(d.copy(bikewayCostPct = 70.0, showBikewayLayer = false))
        assertEquals(70.0, back.bikewayCostPct, 0.0)
        assertFalse(back.showBikewayLayer)
        assertEquals(0.5, MapSettingsCodec.decode("""{"bikewayCostPct":20}""").paramsFor(null).bikewayFactor, 0.0)
        assertEquals(1.0, MapSettingsCodec.decode("""{"bikewayCostPct":140}""").paramsFor(null).bikewayFactor, 0.0)
        assertEquals(0.7, RideParams(bikewayFactor = 0.7).withMaxGrade(0.1).bikewayFactor, 0.0)
    }

    @Test
    fun `ayarlar eski JSON yeni alanlarda varsayılanı alır`() {
        val s = MapSettingsCodec.decode("""{"theme":"light"}""")
        assertEquals(5.0, s.lightMaxM, 0.0) // Ekim planı madde 14
        assertEquals(12.0, s.turnPenaltyS, 0.0)
        val back = roundTrip(s.copy(lightMaxM = 9.0, turnPenaltyS = 0.0))
        assertEquals(9.0, back.lightMaxM, 0.0)
        assertEquals(0.0, back.turnPenaltyS, 0.0)
        assertEquals(0.0, back.paramsFor(80.0).turnPenaltyS, 0.0)
    }

    @Test
    fun `eski voice yok sayılır, kayıtlı enerji ve hız parametreleri korunur`() {
        val old = """{"voice":{"obsolete":true},"flatWhPerM":0.02,"climbWhPerM":0.5,"climbSafety":1.46,"flatKmh":24,"climbKmh":16,
            "mainRoadPenalty":1.7,"walkRadius":220,"turnPenaltyS":8}"""
        val s = MapSettingsCodec.decode(old)
        val json = MapSettingsCodec.toJson(s)
        assertFalse(json.has("voice"))
        for ((k, v) in mapOf(
            "flatWhPerM" to 0.02, "climbWhPerM" to 0.5, "climbSafety" to 1.46, "flatKmh" to 24.0, "climbKmh" to 16.0,
            "mainRoadPenalty" to 1.7, "walkRadius" to 220.0, "turnPenaltyS" to 8.0,
        )) {
            assertEquals(k, v, json.getDouble(k), 0.0)
        }
        val p = s.paramsFor(80.0)
        assertEquals(listOf(0.02, 0.5, 1.46), listOf(p.flatWhPerM, p.climbWhPerM, p.climbSafety))
        assertEquals(listOf(24.0, 16.0, 1.7, 220.0, 8.0), listOf(p.flatKmh, p.climbKmh, p.mainRoadPenalty, p.walkRadius, p.turnPenaltyS))
    }
}
