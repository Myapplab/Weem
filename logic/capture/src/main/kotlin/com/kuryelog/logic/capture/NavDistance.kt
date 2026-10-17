package com.kuryelog.logic.capture

/**
 * The trip summary of a navigation app's ongoing notification (Google Maps, Waze, Yandex, 2GIS, HERE), shown live on the
 * cards. WEEM: its own route comes through `NavPort.route` (`:orders`, ORDERS.md); this summary stays for the other apps.
 * E16: the customer card no longer opens from this notification's distance.
 *
 * Only a line that carries both a duration and a distance ("8 min · 2,1 km") is a trip summary. A distance alone
 * ("150 m", "Turn left in 300 m") is the next manoeuvre and is ignored.
 */
object NavDistance {
    val packages = setOf("com.google.android.apps.maps", "com.waze", "ru.yandex.yandexnavi", "ru.yandex.yandexmaps",
        "ru.dublgis.dgismobile", "com.here.app.maps")
    private const val UNITS = "min|mins|minute|minutes|dk|dak|deq|deqiqe|мин|h|hr|hrs|saat|ч"
    /** Unicode word edges written out, so "3 мин" (Yandex, 2GIS) counts as a duration on the JVM too (`summary` as well,
 *  WEEM: CAPTURE.md 7.5; Rawstats' `summary` used `\b`, which JDK 21 does not end after "мин"). 16 Oct: this was
     *  `Regex("(?U)" + …)`; Android's regex engine (ICU) has no `(?U)` flag, so on the phone the whole object failed to load
     *  and the first notification closed the app (0.25.0). Only constructs ICU and the JVM both read go here ([IcuRegexTest]). */
    private val durationU = Regex("(?<![\\p{L}\\p{N}_])[0-9]+[\\s\\p{Z}]*(?:$UNITS)(?![\\p{L}\\p{N}_])")
    private val distance = Regex("(\\d+(?:[.,]\\d+)?)\\s*(km|m|км|м)(?![a-zа-я])")
    /** The trip-summary line itself ("8 min · 2,1 km"), shown live on the cards; kept in memory only. */
    fun summary(lines: List<String>): String? = lines.firstOrNull { line ->
        val n = CourierParser.norm(line)
        durationU.containsMatchIn(n) && distance.findAll(n).any { m -> !durationU.matches(m.value) }
    }?.trim()?.take(60)

    /** M20: the remaining distance of a [summary] line in metres ("1 min · 5 m" → 5, "8 min · 2,1 km" → 2100); null without one. */
    fun metres(summary: String?): Double? {
        val n = CourierParser.norm(summary ?: return null)
        // Unicode word boundaries, so "3 мин" (Yandex, 2GIS) counts as a duration too.
        if (!durationU.containsMatchIn(n)) return null
        val m = distance.findAll(n).lastOrNull { !durationU.matches(it.value) } ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return if (m.groupValues[2] == "km" || m.groupValues[2] == "км") v * 1000.0 else v
    }
}
