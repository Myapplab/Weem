package com.kuryelog.logic.map.ride

import java.util.Locale

/** Gösterge biçimleri (`widgets.dart` `fmtMin`/`fmtKm`/`fmtPct`, `_v`; MAP.md 10.2, 10.5). */
object RideFormat {
    /** `fmtMin(s)`: 60 sn'den azsa "<1", değilse yuvarlanmış dakika. */
    fun minutes(seconds: Double): String = if (seconds < 60) "<1" else Math.round(seconds / 60).toString()

    /** `fmtKm(m)`: 1 000 m'den azsa "NNN m", değilse "x.x km". */
    fun distance(m: Double): String = if (m < 1000) "${Math.round(m)} m" else km(m)

    /** "x.x km" (sol sütundaki kalan mesafe her zaman km). */
    fun km(m: Double): String = String.format(Locale.US, "%.1f km", m / 1000)

    /** `fmtPct(v)`: 0–1 oranı "%NN". */
    fun percent(v: Double): String = "%${Math.round(v * 100)}"

    /** Değer ya da yoksa "—" (`_v`/`_value`). */
    fun value(n: Double?, unit: String, digits: Int = 0): String = if (n == null) "—" else String.format(Locale.US, "%.${digits}f", n) + unit
}
