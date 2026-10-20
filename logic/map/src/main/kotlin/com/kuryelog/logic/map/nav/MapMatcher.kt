package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.WayFlag

/** Fix'in oturduğu kenar (MAP.md 7.7): [forward] çizim yönünde mi; [wrongWay] tek yönün tersine (contraflow yoksa). */
data class MatchInfo(
    val edge: Int,
    val osmId: Long,
    val name: String,
    val cls: RoadClass,
    val forward: Boolean,
    val wrongWay: Boolean,
    val dist: Double,
)

/** Onaysız tek yön sokakta ~40 m ters gidilince bir kez; [mainRoad] ana caddede (onay verilemez, yalnız uyarı). [name] boşsa adsız yol. */
data class WrongWayEvent(val osmId: Long, val name: String, val mainRoad: Boolean)

/** `map_matcher.dart` (MAP.md 7.7 adım 1): GPS noktasını yol ağındaki kenara oturtur (ters yön tespiti, Ses± hangi sokağa). */
class MapMatcher(val graph: RoadGraph) {
    var current: MatchInfo? = null
        private set

    private var wwWay: Long? = null
    private var wwMeters = 0.0
    private var lastPt: GeoPoint? = null
    private val suggested = HashSet<Long>()

    /** [accept]: oturtulabilecek kenarlar ("yol yok" hariç); [allowedEdges]: ana caddede bile ters yön izni verilen kesimler. */
    fun update(fix: NavFix, approved: Set<Long>, accept: ((Int) -> Boolean)? = null, allowedEdges: Set<Int> = emptySet()): Pair<MatchInfo?, WrongWayEvent?> {
        if (fix.accuracyM > 35) return current to null
        val heading = fix.headingDeg
        val moving = (fix.speedMps ?: 0.0) >= 2.0 && heading != null && heading >= 0
        val hit = graph.nearestEdge(fix.lat, fix.lon, maxDist = 30.0, accept = accept, heading = if (moving) heading else null)
            ?: graph.nearestEdge(fix.lat, fix.lon, maxDist = 30.0, accept = accept)
        val here = GeoPoint(fix.lat, fix.lon)
        val step = lastPt?.distanceTo(here) ?: 0.0
        lastPt = here
        if (hit == null) {
            current = null
            return null to null
        }
        val e = hit.edge
        val flags = graph.flagsOf(e)
        val forward = if (moving) Geo.angleDiff(heading!!, hit.segBearing) <= 90 else current?.forward ?: true
        val against = if (forward) (flags and WayFlag.ONEWAY_BWD) != 0 else (flags and WayFlag.ONEWAY_FWD) != 0
        val wrongWay = moving && against && (flags and WayFlag.CONTRAFLOW) == 0
        val info = MatchInfo(e, graph.osmIdOf(e), graph.nameOf(e), graph.classOf(e), forward, wrongWay, hit.dist)
        current = info
        var event: WrongWayEvent? = null
        if (wrongWay && info.osmId !in approved && e !in allowedEdges) {
            if (wwWay != info.osmId) {
                wwWay = info.osmId
                wwMeters = 0.0
            }
            wwMeters += step
            if (wwMeters >= 40 && suggested.add(info.osmId)) event = WrongWayEvent(info.osmId, info.name, info.cls.isMainRoad)
        } else {
            wwWay = null
            wwMeters = 0.0
        }
        return info to event
    }
}
