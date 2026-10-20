package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.NavKind
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.map.route.Plan
import com.kuryelog.logic.map.route.RouteLeg
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** Etkin bacağın bir fix için ölçümü (MAP.md 7.9, `NavFeedLeg`): [onRoute] iç alandır, `RouteStatus.Route`'ta yoktur. */
data class RouteMeasure(val remainingM: Double, val remainingTurns: Int, val measuredAtMs: Long, val onRoute: Boolean)

/** Bir bacağın izdüşüm çizgisi ve dönüşlerinin çizgi üstündeki yerleri (`NavFeedLeg`; her fix'te yeniden kurulmaz). */
class LegLine(val leg: RouteLeg) {
    private val line = RouteLine(leg.ride)
    private val turns = leg.turns.map { line.project(it.lat, it.lon).first }

    fun measure(fix: NavFix): RouteMeasure {
        val (along, off) = line.project(fix.lat, fix.lon)
        val remaining = max(0.0, line.lengthM - along) + leg.walkM
        return RouteMeasure((remaining * 10).roundToLong() / 10.0, turns.count { it > along }, fix.atMs, off <= RouteProgress.OFF_ROUTE_M)
    }
}

/** Ölçüm saf işlevleri (MAP.md 7.9). */
object RouteProgress {
    const val OFF_ROUTE_M = 40.0

    /** Canlı ölçüme giren fix'in en kötü doğruluğu (kaba konum yok sayılır). */
    const val MAX_ACCURACY_M = 50.0

    /**
     * Etkin (ilk) bacağın sürüş çizgisine izdüşüm: `kalan = max(0, uzunluk − along) + walkM` (0,1 m'ye yuvarlanır), `kalanDönüş` = along'dan sonraki dönüşler,
     * `onRoute` = dik uzaklık ≤ [OFF_ROUTE_M]. Plan, fix ya da bacak yoksa null.
     */
    fun measure(plan: Plan?, fix: NavFix?): RouteMeasure? {
        val leg = plan?.legs?.firstOrNull() ?: return null
        return LegLine(leg).measure(fix ?: return null)
    }
}

/** Hedef alanı → `RouteStatus.Target`: durak yoksa ya da ev rotasıysa null; lat/lon 6 hane; `kind` restoran/müşteri/bilinmiyor; istek saati yoksa ilk görülme anı. */
object RouteStatusBuilder {
    fun target(stop: Stop?, seenAtMs: Long): RouteStatus.Target? {
        if (stop == null || stop.purpose == StopPurpose.HOME) return null
        val p = stop.point
        if (!p.lat.isFinite() || !p.lon.isFinite() || abs(p.lat) > 90 || abs(p.lon) > 180) return null
        fun r6(x: Double) = Math.round(x * 1e6) / 1e6
        return RouteStatus.Target(r6(p.lat), r6(p.lon), kind(stop), stop.origin, stop.requestedAtMs ?: seenAtMs, stop.sourcePackage)
    }

    /** `RouteStatus` (`NavPort.route`): hedef + ölçüm (`onRoute` düşer). */
    fun status(target: RouteStatus.Target?, route: RouteMeasure?, updatedAtMs: Long): RouteStatus =
        RouteStatus(target, route?.let { RouteStatus.Route(it.remainingM, it.remainingTurns, it.measuredAtMs) }, updatedAtMs)

    fun kind(stop: Stop): NavKind = when (stop.restaurant) {
        true -> NavKind.RESTAURANT
        false -> NavKind.CUSTOMER
        null -> NavKind.UNKNOWN
    }
}

/**
 * `NavFeedGate`: yayın kapısı — açılışta bir kez; hedef ya da rotanın varlığı değişince; dönüş sayısı değişince; kalan ≥20 m değişince; rotadayken son yayından
 * 10 sn sonra (rota dışında kalp atışı yok).
 */
class RouteStatusGate {
    private var sent = false
    private var target: RouteStatus.Target? = null
    private var route: RouteMeasure? = null
    private var atMs: Long? = null

    /** Yayının içeriği son yayından değişti mi (hedef, rotanın varlığı, dönüş sayısı ya da ≥20 m). */
    fun changed(target: RouteStatus.Target?, route: RouteMeasure?): Boolean {
        if (!sent || target != this.target || (route == null) != (this.route == null)) return true
        val last = this.route
        if (route == null || last == null) return false
        return route.remainingTurns != last.remainingTurns || abs(route.remainingM - last.remainingM) >= MIN_MOVE_M
    }

    fun due(target: RouteStatus.Target?, route: RouteMeasure?, nowMs: Long): Boolean {
        if (changed(target, route)) return true
        val at = atMs
        return route != null && route.onRoute && at != null && nowMs - at >= HEARTBEAT_MS
    }

    fun sent(target: RouteStatus.Target?, route: RouteMeasure?, nowMs: Long) {
        sent = true
        this.target = target
        this.route = route
        atMs = nowMs
    }

    companion object {
        const val MIN_MOVE_M = 20.0
        const val HEARTBEAT_MS = 10_000L
    }
}

/** Canlı ölçüm (`NavFeedRouteController`): yalnız doğruluk ≤50 m fix'lerle ölçer; bacak değişince çizgiyi yeniden kurar; açılışta son konumu ölçer. */
class RouteProgressTracker {
    private var line: LegLine? = null
    private var fix: NavFix? = null

    var measure: RouteMeasure? = null
        private set

    /** Bacak değişmediyse çizgi korunur; ölçüm son fix'le yenilenir. */
    fun setPlan(plan: Plan?) {
        val first = plan?.legs?.firstOrNull()
        line = when {
            first == null -> null
            line?.leg === first -> line
            else -> LegLine(first)
        }
        measure = remeasure()
    }

    fun onFix(fix: NavFix) {
        if (fix.accuracyM.isFinite() && fix.accuracyM <= RouteProgress.MAX_ACCURACY_M) this.fix = fix
        measure = remeasure()
    }

    private fun remeasure(): RouteMeasure? {
        val l = line ?: return null
        return l.measure(fix ?: return null)
    }
}
