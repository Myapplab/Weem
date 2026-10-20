package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.route.StreetRatios
import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `router_test.dart` "cadde oranı: tek nokta yalnız kendi 50 m kesimini sayar; ölçülmüş kötü kesim eklenir" (A6.2a,
 * MAP.md 3.14, 9.11). **Uyarlama (SORU-Q1):** WEEM 1.0'da yüzey ölçümü (`SurfaceSummary`, Y13) yok; çevrilen kısım yalnız nokta
 * satırları (boş → 400 m / 0; tek nokta 50 m, %13, metinde "%13"). Dart'taki ölçülmüş 50 m + iki nokta ≈ 110 m ile güveni 0,2
 * ölçümün sayılmaması satırları Y13 ile (`TEST_ENVANTERI` "sonra"). Uygulama: A6.11.
 */
class StreetRatioTest {
    private fun spot(id: String, north: Double = 200.0) = SurfaceMark(
        id = id, way = 100, lat = lat(north), lon = lon(0.0),
        at = OffsetDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(), label = SurfaceLabel.POTHOLE,
    )

    @Test
    fun `cadde oranı tek nokta yalnız kendi 50 m kesimini sayar`() {
        val g = TestGraphs.directAndDetour()
        val none = StreetRatios.of(g, 100, emptyList())
        assertEquals(400.0, none.totalM, 5.0)
        assertEquals(0.0, none.badM, 0.0)
        val one = StreetRatios.of(g, 100, listOf(spot("one")))
        assertEquals(50.0, one.badM, 1.0)
        assertEquals(13, one.percent)
        assertTrue(StreetRatios.text(one).contains("%13"))
    }
}
