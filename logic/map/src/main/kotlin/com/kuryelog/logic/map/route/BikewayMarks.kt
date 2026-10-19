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

    val marks: BikewayMarks get() = TODO("A6.8")
    fun mark(way: Long, name: String): Unit = TODO("A6.8")
    fun unmark(way: Long): Unit = TODO("A6.8")
    fun isMarked(way: Long): Boolean = TODO("A6.8")
}
