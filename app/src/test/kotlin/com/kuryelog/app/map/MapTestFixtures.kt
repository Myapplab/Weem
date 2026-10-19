package com.kuryelog.app.map

import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RideMode
import com.kuryelog.logic.map.route.RouteLeg
import com.kuryelog.logic.map.route.RoutePoint
import com.kuryelog.logic.map.weather.WeatherHour
import com.kuryelog.logic.map.weather.WeatherReport
import java.time.LocalDateTime

/** `:app` harita ekran testlerinin ortak girdileri (YolGo `remaining_km_e12_test`, `v170_features_test`, `hud_layout_test` yardımcıları). */
object MapTestFixtures {
    const val HOME_LAT = 40.4
    const val HOME_LON = 49.85
    const val AT = 1_792_000_000_000L

    fun fix(northM: Double, eastM: Double = 0.0, seconds: Int = 0, acc: Double = 5.0) =
        NavFix(AT + seconds * 1000L, HOME_LAT + northM / 111195, HOME_LON + eastM / 84675, acc, speedMps = 5.0, headingDeg = 0.0)

    /** 1 000 m kuzeye düz bacak; `rideM` kasıtlı farklı (9999): gösterge toplamı değil izdüşümü kullanır. */
    fun plan(walkM: Double = 40.0) = Plan(
        RideMode.TIME, listOf(0),
        listOf(
            RouteLeg(
                ride = (0..1000 step 50).map { RoutePoint(HOME_LAT + it / 111195.0, HOME_LON, 0.0) }, walk = emptyList(), rideM = 9999.0, walkM = walkM, timeS = 150.0,
                energyWh = 13.0, climbM = 0.0, wrongWayM = 0.0, cost = 1.0, streets = emptyList(), destination = GeoPoint(HOME_LAT + 1040 / 111195.0, HOME_LON),
            ),
        ),
    )

    fun km(m: Double) = String.format(java.util.Locale.US, "%.1f km", m / 1000)

    fun scooterLive(pct: Double = 78.0, ext: Double? = 64.0) = ScooterLive(
        generatedAtMs = AT, linkActive = true, ageMs = 400,
        scooter = ScooterLive.Values(internalPct = pct, externalPct = ext, speedKmh = 18.5, powerW = -310.0, voltageV = 40.1, rangeKm = 32.0, tempC = 24.0, odometerKm = 1234.5),
    )

    /** 4 saatlik rapor: 18:00'dan, sıcaklık 23, 22, 21, 20 (`hud_layout_test` `PreviewWeather`). */
    fun report4() = WeatherReport(AT, (0 until 4).map { WeatherHour(LocalDateTime.of(2026, 10, 2, 18 + it, 0), 23.0 - it, 18.0, if (it == 0) 40.0 else 10.0, 0.4, 12.0, 20.0, code = 61, isDay = true) })

    /** 8 saatlik rapor: 10:00'dan, sıcaklık 20…27 (`v160_y1_y2_test` `_EightHours`). */
    fun report8() = WeatherReport(AT, (0 until 8).map { WeatherHour(LocalDateTime.of(2026, 10, 9, 10 + it, 0), 20.0 + it, 20.0, 10.0, 0.0, 10.0, 12.0, code = 0, isDay = true) })
}
