package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.LocalMeters.lat
import com.kuryelog.logic.map.LocalMeters.lon
import com.kuryelog.logic.map.LocalMeters.p
import com.kuryelog.logic.map.LocalMeters.utc
import com.kuryelog.logic.map.TestGraphs
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `test/router_test.dart` "Regresyon: tek kötü nokta yalnız ±25 m kesimi cezalandırır, bütün yolu dışlamaz" (1 test); `MAP.md` 3.14.
 * 1.3.0: tek yüzey işareti 400 m'lik kenarın tamamını dışlıyor/×4 yapıyordu → 800 m'lik dolambaç seçiliyordu.
 */
class BadSpotCostTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)

    private fun spot(id: String, north: Double = 200.0, day: Int = 0) = SurfaceMark(
        id = id, way = 100, lat = lat(north), lon = lon(0.0),
        at = utc(2026, 10, 1 + day, 9), label = SurfaceLabel.POTHOLE,
    )

    private fun ride(marks: List<SurfaceMark>): Double {
        val request = PlanRequest(TestGraphs.main(), a, listOf(b), RideParams(), approved = emptySet(), blocked = emptySet(), marks = marks)
        return computePlans(request).plans.getValue(RideMode.TIME).let { it.rideM }
    }

    @Test
    fun `tek kötü nokta yalnız 25 m kesimi cezalandırır, bütün yolu dışlamaz`() {
        val request = PlanRequest(TestGraphs.main(), a, listOf(b), RideParams(), approved = emptySet(), blocked = emptySet(), marks = listOf(spot("one")))
        val single = computePlans(request).plans.getValue(RideMode.TIME)
        assertEquals(400.0, single.rideM, 5.0)
        assertTrue(single.legs.single().warnings.isEmpty())
        // Farklı günlerde aynı yer (30 m içinde) doğrulanır: "kötüden kaçın" o kesimi dışlar.
        assertEquals(800.0, ride(listOf(spot("one"), spot("two", north = 215.0, day = 1))), 5.0)
        // Aynı gün iki basış doğrulama sayılmaz.
        assertEquals(400.0, ride(listOf(spot("one"), spot("again", north = 210.0))), 5.0)
        // Silinmiş işaret etkisizdir.
        assertEquals(400.0, ride(listOf(spot("one").copy(removed = true), spot("two", north = 215.0, day = 1))), 5.0)
    }
}
