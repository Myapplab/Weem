package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `route_warnings_h2_h3_test.dart` (3) + `yolgo_rules_test.dart` sapma testi (1; A6.2b, MAP.md 7.4, 7.7, 7.15).
 * H2: "kapı ~N m uzakta" uyarısı yeni rotada hep, diğerinde durak/uyarı değişince; H3: başarısız yeniden rota eski rotayı korur, 10 sn ya da 30 m sonra denenir.
 * Bulgu düzeltmesi (MAP.md 7.17): "8 sn" kuralı değil; iki taze sapma fix'i 8 sn **beklemeden** yeniden rota ister.
 */
class RerouteGateTest {
    @Test
    fun `H2 uyarı yeni rotada hep, sapma ve batarya hesabında yalnız durak ya da uyarı değişince`() {
        val a = "stop-1|kapı 40 m"
        val b = "stop-2|kapı 40 m"
        assertTrue(shouldShowRouteWarning(ReplanReason.ROUTE, a, a))
        assertFalse(shouldShowRouteWarning(ReplanReason.REROUTE, a, a))
        assertFalse(shouldShowRouteWarning(ReplanReason.BATTERY, a, a))
        assertTrue(shouldShowRouteWarning(ReplanReason.REROUTE, b, a))
        assertTrue(shouldShowRouteWarning(ReplanReason.REROUTE, a, null))
    }

    @Test
    fun `H3 sapma ve batarya hesabı başarısızsa eski rota korunur, yeni rota kural ve saat değişince korunmaz`() {
        assertTrue(keepPlanOnFailure(ReplanReason.REROUTE))
        assertTrue(keepPlanOnFailure(ReplanReason.BATTERY))
        assertFalse(keepPlanOnFailure(ReplanReason.ROUTE))
        assertFalse(keepPlanOnFailure(ReplanReason.PREFS))
        assertFalse(keepPlanOnFailure(ReplanReason.HOURS))
    }

    @Test
    fun `H3 başarısız denemeden sonra 10 sn ya da 30 m hareket olmadan yeniden denenmez`() {
        val g = RerouteGate()
        val t0 = 1_792_152_000_000L
        val here = GeoPoint(40.4093, 49.8671)
        fun north(m: Double) = GeoPoint(here.lat + m / 111195.08, here.lon)
        fun fire(s: Int, at: GeoPoint): Boolean {
            val t = t0 + s * 1000L
            return g.feed(distanceM = 80.0, accuracyM = 5.0, fixAtMs = t, nowMs = t, here = at)
        }
        assertFalse(fire(0, here))
        assertTrue(fire(1, here)) // iki fix: dene
        g.failed(t0 + 1000, here)
        for (s in 2 until 11) assertFalse("sn $s", fire(s, north(s.toDouble())))
        assertTrue(fire(11, north(11.0))) // 10 sn geçti
        g.failed(t0 + 11_000, north(11.0))
        assertFalse(fire(12, north(20.0)))
        assertTrue(fire(13, north(45.0))) // 30 m'den fazla gitti
    }

    @Test
    fun `iki taze sapma fixi 8 sn beklemeden yeniden rota ister`() {
        val gate = RerouteGate()
        val now = 1_791_000_000_000L
        fun feed(second: Int, accuracy: Double = 5.0, distance: Double = 35.0, age: Int = 0) =
            gate.feed(distanceM = distance, accuracyM = accuracy, fixAtMs = now + (second - age) * 1000L, nowMs = now + second * 1000L)
        assertFalse(feed(0)); assertTrue(feed(1))
        assertFalse(feed(2)); assertTrue(feed(3))
        assertFalse(feed(4, accuracy = 100.0))
        assertFalse(feed(5, age = 20))
        assertFalse(feed(6, distance = 10.0))
    }
}
