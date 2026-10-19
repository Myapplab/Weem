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

    /** Güncel işaretler; her değişiklikte yeni nesne (kümenin sırası yazma sırasıdır). */
    var marks: BikewayMarks = load()
        private set

    private fun load(): BikewayMarks {
        val ways = LinkedHashSet<Long>()
        val names = HashMap<Long, String>()
        for (s in prefs.getStringList(KEY) ?: emptyList()) {
            val i = s.indexOf('|')
            val id = (if (i < 0) s else s.substring(0, i)).trim().toLongOrNull() ?: continue
            ways += id
            if (i >= 0 && i < s.length - 1) names[id] = s.substring(i + 1)
        }
        return BikewayMarks(ways, names)
    }

    private fun save(ways: Set<Long>, names: Map<Long, String>) {
        marks = BikewayMarks(ways, names)
        prefs.setStringList(KEY, ways.map { "$it|${names[it] ?: ""}" })
    }

    /** Bütün OSM yolunu işaretler (ad boşsa ad yazılmaz). */
    fun mark(way: Long, name: String) = save(LinkedHashSet(marks.ways) + way, marks.names + (if (name.isNotEmpty()) mapOf(way to name) else emptyMap()))

    fun unmark(way: Long) = save(LinkedHashSet(marks.ways) - way, marks.names - way)

    fun isMarked(way: Long): Boolean = way in marks.ways
}
