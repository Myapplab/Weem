package com.kuryelog.logic.map.stats

import com.kuryelog.logic.map.day.StopRec
import java.util.Locale

/** Işık/aday satırının sokak adı (MAP.md 12.7): yol grafiğinde [MAX_M] içindeki en yakın kenarın adı, yoksa `lat, lon` (4 hane). */
object StreetLabel {
    const val MAX_M = 40.0

    /** [nearestName] grafın en yakın kenar adı (grafik yoksa/uzaksa null ya da boş). */
    fun of(s: StopRec, nearestName: (lat: Double, lon: Double, maxM: Double) -> String?): String =
        nearestName(s.lat, s.lon, MAX_M)?.takeIf { it.isNotEmpty() } ?: String.format(Locale.US, "%.4f, %.4f", s.lat, s.lon)
}
