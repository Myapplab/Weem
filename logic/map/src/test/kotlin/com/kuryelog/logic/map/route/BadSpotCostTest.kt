package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.route.TestGraphs.p
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `router_test.dart` "Regresyon: tek kötü nokta yalnız ±25 m kesimi cezalandırır, bütün yolu dışlamaz" (A6.2a, MAP.md 3.14).
 * 1.3.0'da tek yüzey işareti 400 m'lik kenarın tamamını dışlıyordu. İşaret deposu Bölüm 9 (A6.11) kararına bağlı; girdi doğrudan
 * `marks` ile. Dart `SurfaceMark.fromJson({... 'removed': true})` → `copy(removed = true)`. Uygulama: A6.4.
 */
class BadSpotCostTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun spot(id: String, north: Double = 200.0, day: Int = 0) = SurfaceMark(
        id = id, way = 100, lat = lat(north), lon = lon(0.0),
        at = OffsetDateTime.of(2026, 10, 1 + day, 9, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(), label = SurfaceLabel.POTHOLE,
    )

    private fun plan(g: com.kuryelog.logic.map.graph.RoadGraph, marks: List<SurfaceMark>) = Planner.computePlans(
        PlanRequest(g, a, listOf(b), RideParams(), emptySet(), emptySet(), marks = marks),
    ).plans[RideMode.TIME]!!

    @Test
    fun `Regresyon tek kötü nokta yalnız 25 m kesimi cezalandırır, bütün yolu dışlamaz`() {
        val g = TestGraphs.directAndDetour()
        val single = plan(g, listOf(spot("one")))
        assertEquals(400.0, single.rideM, 5.0)
        assertTrue(single.legs.single().warnings.isEmpty())
        // Farklı günlerde aynı yer (30 m içinde) doğrulanır: kötüden kaçın o kesimi dışlar.
        assertEquals(800.0, plan(g, listOf(spot("one"), spot("two", north = 215.0, day = 1))).rideM, 5.0)
        // Aynı gün iki basış doğrulama sayılmaz.
        assertEquals(400.0, plan(g, listOf(spot("one"), spot("again", north = 210.0))).rideM, 5.0)
        // Silinmiş işaret etkisizdir.
        assertEquals(400.0, plan(g, listOf(spot("one").copy(removed = true), spot("two", north = 215.0, day = 1))).rideM, 5.0)
    }
}
