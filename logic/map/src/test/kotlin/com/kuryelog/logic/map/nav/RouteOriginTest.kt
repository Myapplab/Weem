package com.kuryelog.logic.map.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** YolGo `v150_features_test.dart` "rota başlangıcı (yanlış GPS sinyali alınamadı)" 3 test (A6.2b, MAP.md 7.6, 7.15): `routeOrigin` saf. */
class RouteOriginTest {
    private val now = 1_791_400_000_000L
    private fun pos(atMs: Long, lat: Double = 40.4, lon: Double = 49.85, acc: Double = 30.0) = NavFix(atMs, lat, lon, acc)
    private val minute = 60_000L

    @Test
    fun `haritadaki son konum 1 dk eski ve 90 m belirsizse rota oradan başlar`() {
        val last = pos(now - minute, acc = 90.0)
        val p = RouteOrigin.routeOrigin(last, null, now)
        assertNotNull(p)
        assertEquals(40.4, p!!.lat, 0.0)
    }

    @Test
    fun `en yeni geçerli konum seçilir`() {
        val a = pos(now - 2 * minute, lat = 40.1)
        val b = pos(now - 30_000, lat = 40.2)
        assertEquals(40.2, RouteOrigin.routeOrigin(a, b, now)!!.lat, 0.0)
    }

    @Test
    fun `çok eski, çok belirsiz ya da 0 0 konum kabul edilmez`() {
        assertNull(RouteOrigin.routeOrigin(pos(now - 4 * minute), null, now))
        assertNull(RouteOrigin.routeOrigin(pos(now, acc = 400.0), null, now))
        assertNull(RouteOrigin.routeOrigin(pos(now, lat = 0.0, lon = 0.0), null, now))
        assertNull(RouteOrigin.routeOrigin(null, null, now))
    }
}
