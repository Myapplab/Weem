package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.FocusCamera
import com.kuryelog.logic.map.nav.NavState
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RouteGeometry
import com.kuryelog.logic.map.route.RoutePoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** Rota çizgisi: [dashed] kesikli 8/6; [z] büyük olan üstte; renk ARGB. */
data class RouteStroke(val id: String, val points: List<GeoPoint>, val color: Int, val widthDp: Double, val dashed: Boolean = false, val z: Int = 0)

/** Durak rozeti: [number] ziyaret sırası (1'den), [first] etkin durak; [reached] varılan hedefin yeşil iğnesi. */
data class RoutePin(val id: String, val point: GeoPoint, val number: Int, val first: Boolean, val reached: Boolean = false)

/**
 * Rota katmanı (YolGo `map_view.dart:480-575, 692-753`; MAP.md 7.11 "Rota çizimi"): öteki modun planı soluk gri 4 px; etkin planın her bacağında parlama 14 px (mavi %18),
 * eğime göre renkli çekirdek 6 px (`forGrade(Δh / max(d, 20))`, aynı renkli parçalar tek çizgi), itilerek yürünen parçalar kesikli 6 px, baştaki yaya yolu yürüyüşü kesikli 4 px,
 * sondaki yürüme düz 4 px (yürüme rengi). Rozetler bacak hedeflerinde 1, 2…; varılan iğne yeşil. [fit] yeni rotada bütün rotayı + konumu ekrana sığdırır.
 */
object RouteLayer {
    const val GLOW_WIDTH = 14.0
    const val CORE_WIDTH = 6.0
    const val WALK_WIDTH = 4.0
    const val GLOW_ALPHA = 0.18
    const val FIT_MAX_ZOOM = 16.5

    /** OSM/MapLibre yüzeyinde sığdırma kenar payı (sol, üst, sağ, alt; dp); Google'da her yanda [GOOGLE_FIT_PAD]. */
    val FIT_PADDING = intArrayOf(40, 170, 40, 230)
    const val GOOGLE_FIT_PAD = 32

    /** Yeni rotada sığdırmadan sonra konuma dönüş (ms). */
    const val REFOLLOW_AFTER_FIT_MS = 5_000L

    fun strokes(nav: NavState, palette: MapPalette): List<RouteStroke> {
        val active = nav.plan
        val out = ArrayList<RouteStroke>()
        for ((mode, plan) in nav.plans) {
            if (plan === active) continue
            plan.legs.forEachIndexed { i, l ->
                if (l.ride.size >= 2) out += RouteStroke("other-${mode.name}-$i", l.ride.map(::pt), MapPalette.OTHER_PLAN, MapPalette.OTHER_PLAN_WIDTH, z = 0)
            }
        }
        if (active == null) return out
        val glow = (palette.blue and 0xFFFFFF) or (Math.round(GLOW_ALPHA * 255).toInt() shl 24)
        active.legs.forEachIndexed { i, leg ->
            val pts = leg.ride
            if (pts.size >= 2) {
                out += RouteStroke("glow-$i", pts.map(::pt), glow, GLOW_WIDTH, z = 1)
                var color = gradeColor(palette, pts[0], pts[1])
                var run = arrayListOf(pt(pts[0]))
                var k = 0
                for (j in 1 until pts.size) {
                    val c = gradeColor(palette, pts[j - 1], pts[j])
                    if (c != color) {
                        out += RouteStroke("core-$i-${k++}", run, color, CORE_WIDTH, z = 2)
                        run = arrayListOf(pt(pts[j - 1]))
                        color = c
                    }
                    run += pt(pts[j])
                }
                out += RouteStroke("core-$i-$k", run, color, CORE_WIDTH, z = 2)
            }
            RouteGeometry.pushedRuns(leg).forEachIndexed { j, r -> out += RouteStroke("push-$i-$j", r.map(::pt), palette.walk, CORE_WIDTH, dashed = true, z = 3) }
            if (leg.startWalk.size >= 2) out += RouteStroke("start-walk-$i", leg.startWalk.map(::pt), palette.walk, WALK_WIDTH, dashed = true, z = 3)
            if (leg.walk.size >= 2) out += RouteStroke("walk-$i", leg.walk.map(::pt), palette.walk, WALK_WIDTH, z = 3)
        }
        return out
    }

    fun pins(nav: NavState): List<RoutePin> {
        val out = ArrayList<RoutePin>()
        nav.reached?.let { out += RoutePin("reached", it.point, 0, first = false, reached = true) }
        nav.plan?.legs?.forEachIndexed { i, l -> out += RoutePin("stop-$i", l.destination, i + 1, first = i == 0) }
        return out
    }

    /** Sığdırılacak noktalar: bacakların sürüş çizgileri + hedefler + konum (YolGo `_fitPlan`); 2'den azsa boş. */
    fun fitPoints(plan: Plan?, me: GeoPoint?): List<GeoPoint> {
        if (plan == null) return emptyList()
        val pts = ArrayList<GeoPoint>()
        for (l in plan.legs) l.ride.mapTo(pts, ::pt)
        for (l in plan.legs) pts += l.destination
        me?.let { pts += it }
        return if (pts.size < 2) emptyList() else pts
    }

    /**
     * [points]'i [widthDp]×[heightDp] görünümde [padding] (sol, üst, sağ, alt) payıyla sığdıran kamera (Web Mercator, 256 dp karo); zoom ≤ [maxZoom].
     * Kenar payı eşit değilse merkez kayar (alt pay büyükse rota ekranın üstüne oturur).
     */
    fun fit(points: List<GeoPoint>, widthDp: Double, heightDp: Double, padding: IntArray = FIT_PADDING, maxZoom: Double = FIT_MAX_ZOOM): FocusCamera? {
        if (points.isEmpty()) return null
        var south = points[0].lat; var north = south; var west = points[0].lon; var east = west
        for (p in points) { south = min(south, p.lat); north = max(north, p.lat); west = min(west, p.lon); east = max(east, p.lon) }
        val x0 = mx(west); val x1 = mx(east); val y0 = my(north); val y1 = my(south)
        val w = max(widthDp - padding[0] - padding[2], 1.0)
        val h = max(heightDp - padding[1] - padding[3], 1.0)
        val dx = max(x1 - x0, 1e-12)
        val dy = max(y1 - y0, 1e-12)
        val zoom = min(maxZoom, min(log2(w / (dx * TILE)), log2(h / (dy * TILE))))
        val world = TILE * Math.pow(2.0, zoom)
        // Görünümün ortası, kenar paylı kutunun ortasından ((sağ − sol)/2, (alt − üst)/2) dp kadar kaymış olur.
        val cx = (x0 + x1) / 2 + (padding[2] - padding[0]) / 2.0 / world
        val cy = (y0 + y1) / 2 + (padding[3] - padding[1]) / 2.0 / world
        return FocusCamera(latOf(cy), lonOf(cx), zoom)
    }

    private const val TILE = 256.0

    private fun pt(p: RoutePoint) = GeoPoint(p.lat, p.lon)

    private fun gradeColor(palette: MapPalette, a: RoutePoint, b: RoutePoint): Int {
        val d = Geo.haversine(a.lat, a.lon, b.lat, b.lon)
        return palette.forGrade((b.ele - a.ele) / max(d, 20.0))
    }

    private fun log2(v: Double) = ln(v) / ln(2.0)

    // Web Mercator, 0–1 birim kare (x doğuya, y güneye artar).
    private fun mx(lon: Double) = (lon + 180) / 360
    private fun my(lat: Double) = (1 - ln(tan(PI / 4 + lat * PI / 360)) / PI) / 2
    private fun lonOf(x: Double) = x * 360 - 180
    private fun latOf(y: Double) = (2 * atan(exp((1 - 2 * y) * PI)) - PI / 2) * 180 / PI
}
