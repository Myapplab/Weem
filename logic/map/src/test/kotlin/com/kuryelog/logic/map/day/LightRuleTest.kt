package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.settings.MapSettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `light_rule_m14_test.dart` F9.2 kural (4) + F9.1 varsayılan (1) + `v140_features_test.dart` Y11 (3) = 8 test (A6.2b, MAP.md 11.8, 11.16): ışığa ≤5 m ve
 * ((yoldayım ve yaya yolunda değilim) ya da yaya yoluna ≤1 m); AVM ve elle kaldırım dışlanır; ışık bilinmiyorsa değil. F9.1 göç testleri (2) çevrilmez
 * (S1: `lightMaxMv2/v3` göçü yazılmaz, MAP.md 14.2).
 */
class LightRuleTest {
    @Test
    fun `4 m yolda (şeritte ya da arabaların yanında) ışık`() {
        assertTrue(RoadInfo(onRoad = true, known = true, lightM = 4.0, surface = StopSurface.ROAD).atLight)
        assertTrue(RoadInfo(onRoad = true, known = true, lightM = 4.0, roadside = true).atLight)
        assertTrue(RoadInfo(onRoad = true, known = true, lightM = 5.0).atLight)
    }

    @Test
    fun `4 m yaya yolunun üstünde (1 m) ışık, yaya yoluna 3 m ve yolda değil değil`() {
        assertTrue(RoadInfo(known = true, lightM = 4.0, footM = 0.5, surface = StopSurface.SIDEWALK).atLight)
        assertTrue(RoadInfo(known = true, lightM = 4.0, footM = 1.0, surface = StopSurface.SIDEWALK).atLight)
        assertFalse(RoadInfo(known = true, lightM = 4.0, footM = 3.0, surface = StopSurface.SIDEWALK).atLight)
        assertFalse("ne yolda ne yaya yolunda", RoadInfo(known = true, lightM = 4.0).atLight)
    }

    @Test
    fun `ışığa 6 m ve daha uzak değil (yolda da olsa)`() {
        assertFalse(RoadInfo(onRoad = true, known = true, lightM = 6.0, surface = StopSurface.ROAD).atLight)
        assertFalse(RoadInfo(known = true, lightM = 12.0, footM = 0.0, surface = StopSurface.SIDEWALK).atLight)
    }

    @Test
    fun `AVM ve elle işaretli kaldırım hâlâ dışlanır, ışık bilinmiyorsa değil`() {
        assertFalse(RoadInfo(onRoad = true, known = true, lightM = 3.0, mall = true).atLight)
        assertFalse(RoadInfo(known = true, lightM = 3.0, footM = 0.0, surface = StopSurface.SIDEWALK, manualSidewalk = true).atLight)
        assertFalse(RoadInfo(onRoad = true, lightM = 3.0).atLight)
    }

    @Test
    fun `F9_1 varsayılan 5 m`() {
        assertEquals(5.0, MapSettings().lightMaxM, 0.0)
        assertEquals(5.0, RoadInfo().maxLightM, 0.0)
        assertEquals(5.0, MapSettingsCodec.decode("{}").lightMaxM, 0.0)
    }

    // --- v140_features_test Y11 ---

    @Test
    fun `ayardaki mesafeden uzak duruş ışık sayılmaz, yakın sayılır (5 m ayarıyla)`() {
        val far = RoadInfo(onRoad = true, known = true, lightM = 8.0, surface = StopSurface.ROAD, maxLightM = 5.0)
        val near = RoadInfo(onRoad = true, known = true, lightM = 4.0, surface = StopSurface.ROAD, maxLightM = 5.0)
        assertFalse(far.atLight)
        assertTrue(near.atLight)
        val wider = RoadInfo(onRoad = true, known = true, lightM = 8.0, maxLightM = 10.0)
        assertTrue("Ayarlardaki mesafe kullanılır", wider.atLight)
    }

    @Test
    fun `AVM ışık olamaz, ışık yarıçapında yol kenarı ışıktır`() {
        assertFalse(RoadInfo(onRoad = true, known = true, lightM = 2.0, mall = true).atLight)
        assertTrue(RoadInfo(onRoad = true, known = true, lightM = 2.0, roadside = true).atLight)
        assertFalse(RoadInfo(onRoad = true, known = true, lightM = 2.0, mall = true).allowsTrafficLight)
    }

    @Test
    fun `yol kenarı yarım şerit genişliğinden uzak ve GPS iyi`() {
        assertTrue(StopSemantics.isRoadside(accuracy = 5.0, roadM = 5.0, cls = RoadClass.RESIDENTIAL))
        assertFalse(StopSemantics.isRoadside(accuracy = 5.0, roadM = 3.0, cls = RoadClass.RESIDENTIAL))
        assertFalse(StopSemantics.isRoadside(accuracy = 5.0, roadM = 5.0, cls = RoadClass.PRIMARY))
        assertFalse("kötü GPS ile karar verilmez", StopSemantics.isRoadside(accuracy = 15.0, roadM = 5.0, cls = RoadClass.RESIDENTIAL))
    }
}
