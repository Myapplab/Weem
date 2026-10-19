package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.NavKind
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.map.route.Plan

/** Etkin bacağın bir fix için ölçümü (MAP.md 7.9, `NavFeedLeg`): [onRoute] iç alandır, `RouteStatus.Route`'ta yoktur. */
data class RouteMeasure(val remainingM: Double, val remainingTurns: Int, val measuredAtMs: Long, val onRoute: Boolean)

/** Ölçüm saf işlevleri (MAP.md 7.9). İskelet (A6.2b). */
object RouteProgress {
    const val OFF_ROUTE_M = 40.0

    /**
     * Etkin (ilk) bacağın sürüş çizgisine izdüşüm: `kalan = max(0, uzunluk − along) + walkM` (0,1 m'ye yuvarlanır), `kalanDönüş` = along'dan sonraki dönüşler,
     * `onRoute` = dik uzaklık ≤ [OFF_ROUTE_M]. Plan, fix ya da bacak yoksa null.
     */
    fun measure(plan: Plan?, fix: NavFix?): RouteMeasure? = TODO("A6.6")
}

/** Hedef alanı → `RouteStatus.Target`: durak yoksa ya da ev rotasıysa null; lat/lon 6 hane; `kind` restoran/müşteri/bilinmiyor; istek saati yoksa ilk görülme anı. */
object RouteStatusBuilder {
    fun target(stop: Stop?, seenAtMs: Long): RouteStatus.Target? = TODO("A6.6")

    /** `RouteStatus` (`NavPort.route`): hedef + ölçüm (`onRoute` düşer). */
    fun status(target: RouteStatus.Target?, route: RouteMeasure?, updatedAtMs: Long): RouteStatus = TODO("A6.6")

    fun kind(stop: Stop): NavKind = TODO("A6.6")
}

/**
 * `NavFeedGate`: yayın kapısı — açılışta bir kez; hedef ya da rotanın varlığı değişince; dönüş sayısı değişince; kalan ≥20 m değişince; rotadayken son yayından
 * 10 sn sonra (rota dışında kalp atışı yok). İskelet (A6.2b).
 */
class RouteStatusGate {
    fun due(target: RouteStatus.Target?, route: RouteMeasure?, nowMs: Long): Boolean = TODO("A6.6")

    fun sent(target: RouteStatus.Target?, route: RouteMeasure?, nowMs: Long): Unit = TODO("A6.6")
}

/** Canlı ölçüm (`NavFeedRouteController`): yalnız doğruluk ≤50 m fix'lerle ölçer; bacak değişince çizgiyi yeniden kurar; açılışta son konumu ölçer. */
class RouteProgressTracker {
    val measure: RouteMeasure? get() = TODO("A6.6")

    fun setPlan(plan: Plan?): Unit = TODO("A6.6")

    fun onFix(fix: NavFix): Unit = TODO("A6.6")
}
