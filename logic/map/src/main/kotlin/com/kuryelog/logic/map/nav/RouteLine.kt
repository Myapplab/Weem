package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.LocalProjection
import com.kuryelog.logic.map.route.RoutePoint

/** Rota boyunca uzaklık (`route_line.dart`; MAP.md 7.9): kümülatif haversine uzunluğu ve bir noktanın çizgi üzerindeki izdüşümü. */
class RouteLine(val points: List<RoutePoint>) {
    private val cum = DoubleArray(points.size)

    init {
        for (i in 1 until points.size) cum[i] = cum[i - 1] + Geo.haversine(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
    }

    val lengthM: Double get() = if (cum.isEmpty()) 0.0 else cum.last()

    /** (çizgi başından uzaklık, çizgiye dik uzaklık); çizgi <2 noktaysa dik uzaklık sonsuz. */
    fun project(lat: Double, lon: Double): Pair<Double, Double> {
        if (points.size < 2) return 0.0 to Double.POSITIVE_INFINITY
        val proj = LocalProjection(lat, lon)
        var best = Double.POSITIVE_INFINITY
        var along = 0.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val r = Geo.pointToSegment(0.0, 0.0, proj.x(a.lon), proj.y(a.lat), proj.x(b.lon), proj.y(b.lat))
            if (r.dist < best) {
                best = r.dist
                along = cum[i] + (cum[i + 1] - cum[i]) * r.t
            }
        }
        return along to best
    }

    companion object {
        /** `_distanceToPolyline` (`nav_controller.dart:964-974`): parçalara en kısa uzaklık; çizgi <2 nokta → 0. */
        fun distanceTo(lat: Double, lon: Double, line: List<RoutePoint>): Double {
            if (line.size < 2) return 0.0
            val proj = LocalProjection(lat, lon)
            var best = Double.POSITIVE_INFINITY
            for (i in 0 until line.size - 1) {
                val a = line[i]
                val b = line[i + 1]
                val d = Geo.pointToSegment(0.0, 0.0, proj.x(a.lon), proj.y(a.lat), proj.x(b.lon), proj.y(b.lat)).dist
                if (d < best) best = d
            }
            return best
        }
    }
}
