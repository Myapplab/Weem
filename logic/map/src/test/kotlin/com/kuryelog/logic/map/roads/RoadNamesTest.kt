package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YolGo `street_scores_test.dart` yedinci test (A6.2a, MAP.md 9.11): adsız yolun adı seçili dilde. Dosyanın öteki 11 testi
 * `SurfaceSummary` + kalibrasyon ister (Y13/Y14, sonra). Uygulama: A6.11.
 */
class RoadNamesTest {
    @Test
    fun `adsız yolun adı seçili dilde (AZ adsız yol)`() {
        val k = RoadKnowledge()
        k.names.putAll(mapOf(1L to "Nizami", 2L to ""))
        assertEquals(listOf("Nizami", "isimsiz yol", "isimsiz yol"), listOf(1L, 2L, 3L).map { k.nameOf(it, Lang.TR) })
        assertEquals(listOf("Nizami", "adsız yol", "adsız yol"), listOf(1L, 2L, 3L).map { k.nameOf(it, Lang.AZ) })
    }
}
