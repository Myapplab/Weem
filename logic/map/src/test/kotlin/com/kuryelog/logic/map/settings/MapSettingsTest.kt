package com.kuryelog.logic.map.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `bikeway_e13_test.dart` "E13 ayarlar" (ayar kısmı, 1 test) + `v140_features_test.dart` ayar testleri (2); `MAP.md` 5.2, 10.12, 14.2.
 * Sapma (S2, `MAP.md` 14.2): `autoStartDay`/`autoEndDay`/`followCourierStages` `MapSettings`'e girmez (tek vardiya kuralı `:core`); eski
 * testin son iki beklentisi "yazılan JSON bu anahtarları içermez" olarak uyarlandı.
 */
class MapSettingsTest {
    @Test
    fun `E13 ayarlar - varsayılan 0,85 ve katman açık - eski ayar dosyası bozulmaz - aralık 0,50 ile 1,00`() {
        val d = MapSettings()
        assertEquals(85.0, d.bikewayCostPct, 0.0)
        assertTrue(d.showBikewayLayer)
        assertEquals(0.85, d.paramsFor(null).bikewayFactor, 1e-9)
        val old = MapSettingsCodec.decode("""{"qualityTolerancePct":12,"turnPenaltyS":8}""")
        assertEquals(85.0, old.bikewayCostPct, 0.0)
        assertTrue(old.showBikewayLayer)
        assertEquals(12.0, old.qualityTolerancePct, 0.0)
        val back = MapSettingsCodec.decode(MapSettingsCodec.encode(d.copy(bikewayCostPct = 70.0, showBikewayLayer = false)))
        assertEquals(70.0, back.bikewayCostPct, 0.0)
        assertFalse(back.showBikewayLayer)
        assertEquals(0.5, MapSettingsCodec.decode("""{"bikewayCostPct":20}""").paramsFor(null).bikewayFactor, 0.0)
        assertEquals(1.0, MapSettingsCodec.decode("""{"bikewayCostPct":140}""").paramsFor(null).bikewayFactor, 0.0)
        assertEquals(0.7, com.kuryelog.logic.map.route.RideParams(bikewayFactor = 0.7).withMaxGrade(0.1).bikewayFactor, 0.0)
    }

    @Test
    fun `ayarlar - eski JSON yeni alanlarda varsayılanı alır`() {
        val s = MapSettingsCodec.decode("""{"theme":"light"}""")
        assertEquals(5.0, s.lightMaxM, 0.0) // Ekim planı madde 14 (H11'in 25'i geri alındı)
        assertEquals(12.0, s.turnPenaltyS, 0.0)
        val back = MapSettingsCodec.decode(MapSettingsCodec.encode(s.copy(lightMaxM = 9.0, turnPenaltyS = 0.0)))
        assertEquals(9.0 to 0.0, back.lightMaxM to back.turnPenaltyS)
        assertEquals(0.0, back.paramsFor(80.0).turnPenaltyS, 0.0)
    }

    @Test
    fun `eski voice yok sayılır - kayıtlı enerji ve hız parametreleri korunur`() {
        val old = JSONObject(
            """{"voice":{"obsolete":true},"flatWhPerM":0.02,"climbWhPerM":0.5,"climbSafety":1.46,"flatKmh":24,"climbKmh":16,
               "mainRoadPenalty":1.7,"walkRadius":220,"turnPenaltyS":8,"autoStartDay":false,"autoEndDay":false}""",
        )
        val s = MapSettingsCodec.decode(old.toString())
        val back = JSONObject(MapSettingsCodec.encode(s))
        assertFalse(back.has("voice"))
        for (k in listOf("flatWhPerM", "climbWhPerM", "climbSafety", "flatKmh", "climbKmh", "mainRoadPenalty", "walkRadius", "turnPenaltyS")) {
            assertEquals(k, old.getDouble(k), back.getDouble(k), 0.0)
        }
        val p = s.paramsFor(80.0)
        assertEquals(listOf(0.02, 0.5, 1.46), listOf(p.flatWhPerM, p.climbWhPerM, p.climbSafety))
        assertEquals(listOf(24.0, 16.0, 1.7, 220.0, 8.0), listOf(p.flatKmh, p.climbKmh, p.mainRoadPenalty, p.walkRadius, p.turnPenaltyS))
        // S2 uyarlaması: tek vardiya kuralı :core'da; bu anahtarlar yazılmaz.
        assertFalse(back.has("autoStartDay") || back.has("autoEndDay") || back.has("followCourierStages"))
    }
}
