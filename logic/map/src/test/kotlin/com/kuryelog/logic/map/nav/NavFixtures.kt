package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteLeg
import com.kuryelog.logic.map.route.RoutePoint
import com.kuryelog.logic.map.route.RouteTurn

/** `nav_feed_test`/`remaining_km_e12_test`/`arrival_pin_test` ortak girdileri: kuzeye giden düz bacaklar ve fix üreticisi (Dart `fix()`, `north()`, `leg()`, `plan()`). */
object NavFixtures {
    const val LAT0 = 40.40
    const val LON0 = 49.85
    const val T0 = 1_791_295_270_000L

    fun north(m: Double) = RoutePoint(LAT0 + m / 111195.0, LON0, 0.0)

    fun fix(northM: Double, eastM: Double = 0.0, atMs: Long = T0, acc: Double = 5.0) = NavFix(
        atMs, LAT0 + northM / 111195.0, LON0 + eastM / (111195.0 * 0.7615), acc, speedMps = 5.0, headingDeg = 0.0,
    )

    /** 1 000 m kuzeye düz bacak, 300 ve 700 m'de iki dönüş, sonunda 40 m yürüme. */
    fun legWithTurns() = RouteLeg(
        ride = (0..1000 step 50).map { north(it.toDouble()) },
        walk = listOf(north(1000.0), north(1040.0)), rideM = 1000.0, walkM = 40.0, timeS = 150.0, energyWh = 13.0, climbM = 0.0, wrongWayM = 0.0,
        cost = 1.0, streets = listOf("Test"), destination = GeoPoint(LAT0 + 1040 / 111195.0, LON0),
        turns = listOf(
            RouteTurn(300.0, 90.0, "A", LAT0 + 300 / 111195.0, LON0),
            RouteTurn(700.0, -90.0, "B", LAT0 + 700 / 111195.0, LON0),
        ),
    )

    fun plan(mode: RideMode = RideMode.TIME) = Plan(mode, listOf(0), listOf(legWithTurns()))

    /**
     * `remaining_km_e12_test` planı: `rideM` kasıtlı farklı (9999) — göstergenin toplamı değil izdüşümü kullandığını sınar; dönüş yok.
     */
    fun planWalk(walkM: Double = 40.0) = Plan(
        RideMode.TIME, listOf(0),
        listOf(
            RouteLeg(
                ride = (0..1000 step 50).map { north(it.toDouble()) }, walk = emptyList(), rideM = 9999.0, walkM = walkM, timeS = 150.0, energyWh = 13.0,
                climbM = 0.0, wrongWayM = 0.0, cost = 1.0, streets = emptyList(), destination = GeoPoint(LAT0 + 1040 / 111195.0, LON0),
            ),
        ),
    )
}
