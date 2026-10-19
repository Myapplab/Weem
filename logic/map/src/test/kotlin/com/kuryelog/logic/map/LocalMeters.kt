package com.kuryelog.logic.map

import com.kuryelog.logic.map.geo.GeoPoint
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.cos

/** Yerel metre → enlem/boylam (Bakü civarı); YolGo testlerindeki `_lat/_lon/_p` yardımcıları. */
object LocalMeters {
    const val LAT0 = 40.40
    const val LON0 = 49.85

    fun lat(northM: Double): Double = LAT0 + northM / 111320
    fun lon(eastM: Double): Double = LON0 + eastM / (111320 * cos(LAT0 * Math.PI / 180))
    fun p(east: Double, north: Double): GeoPoint = GeoPoint(lat(north), lon(east))

    /** Dart `DateTime.utc(...)` karşılığı (UTC epoch ms). */
    fun utc(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(ZoneOffset.UTC).toEpochMilli()
}
