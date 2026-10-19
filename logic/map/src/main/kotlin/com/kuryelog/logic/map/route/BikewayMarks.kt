package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.settings.MapPrefs

/** Elle işaretlenmiş bisiklet yolları (OSM way id → ad); yalnız bu telefonda (`MAP.md` 5.1). */
data class BikewayMarks(val ways: Set<Long> = emptySet(), val names: Map<Long, String> = emptyMap())

/** `streets.bikeway.v1` StringList `"<wayId>|<ad>"` üstünde kalıcı depo. İskelet (A6.2a): gövdeler A6.8'de. */
class BikewayMarksStore(private val prefs: MapPrefs) {
    val marks: BikewayMarks get() = TODO("A6.8")

    fun mark(way: Long, name: String): Unit = TODO("A6.8")
    fun unmark(way: Long): Unit = TODO("A6.8")
    fun isMarked(way: Long): Boolean = TODO("A6.8")

    companion object {
        const val KEY = "streets.bikeway.v1"
    }
}
