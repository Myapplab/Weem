package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.settings.MapPrefs

/**
 * E13: elle işaretlenmiş bisiklet yolları (OSM way id → ad). Yalnız bu telefonda, kalıcı; rotada ve haritada OSM bayrağı gibi
 * davranır (MAP.md 5.1). Tercih `streets.bikeway.v1`, öğe `"<wayId>|<ad>"`.
 */
data class BikewayMarks(val ways: Set<Long> = emptySet(), val names: Map<Long, String> = emptyMap())

class BikewayMarksStore(private val prefs: MapPrefs) {
    companion object {
        const val KEY = "streets.bikeway.v1"
    }

    private val ways = LinkedHashMap<Long, String>()

    init {
        for (item in prefs.getStringList(KEY).orEmpty()) {
            val bar = item.indexOf('|')
            val id = (if (bar < 0) item else item.substring(0, bar)).trim().toLongOrNull() ?: continue
            ways[id] = if (bar >= 0 && bar < item.length - 1) item.substring(bar + 1) else ""
        }
    }

    val marks: BikewayMarks
        get() = BikewayMarks(ways.keys.toSet(), ways.filterValues { it.isNotEmpty() })

    /** [name] boşsa ad yazılmaz. */
    fun mark(way: Long, name: String) {
        ways[way] = name
        save()
    }

    fun unmark(way: Long) {
        if (ways.remove(way) != null) save()
    }

    fun isMarked(way: Long): Boolean = way in ways

    private fun save() = prefs.setStringList(KEY, ways.map { (w, n) -> "$w|$n" })
}
