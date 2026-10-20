package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.nav.NavFix
import com.kuryelog.logic.map.nav.NavRecording
import com.kuryelog.logic.map.nav.ReplanReason
import com.kuryelog.logic.map.route.Plan
import kotlin.math.roundToLong

/**
 * Rota kaydı bağı (`_record`, `_recordFix`, `_finishRecording`; MAP.md 12.5): [NavRecording] uygulaması. Yeni rota (ya da kayıt yokken) önceki kaydı `new_route` ile
 * bitirip yenisini açar; fix'ler ve yeniden hesaplar [RouteRecorder]'a gider; bitişte `route_end` olayı ve (ayar açık, ≥ 2 iz noktası) rota dosyası yazılır.
 * [logEvent] sürüş günlüğü olayı, [writeRoute] `<gün>_route_<ms>.json` dosyasını yazar (Android `RideLogRuntime`), [onSpeed] ortalama hız göstergesi (ortalama, hareketli).
 */
class RouteRecording(
    private val nowMs: () -> Long,
    private val loggingEnabled: () -> Boolean,
    private val logEvent: (String, Map<String, Any?>) -> Unit,
    private val writeRoute: (startedAtMs: Long, json: String) -> Unit,
    private val onSpeed: (avgKmh: Double?, movingKmh: Double?) -> Unit = { _, _ -> },
) : NavRecording {
    private var rec: RouteRecorder? = null

    val active: Boolean get() = rec != null

    @Synchronized
    override fun planned(reason: ReplanReason, plan: Plan, atMs: Long) {
        if (plan.legs.isEmpty()) return
        val ride = plan.legs.flatMap { it.ride }
        val r = rec
        if (reason == ReplanReason.ROUTE || r == null) {
            finishLocked("new_route", atMs)
            rec = RouteRecorder(atMs, plan.mode.name.lowercase(), ride, plan.legs.first().destination, plan.rideM, plan.timeS)
            onSpeed(null, null)
        } else {
            r.replanned(atMs, reason.name.lowercase(), ride)
        }
    }

    @Synchronized
    override fun fix(fix: NavFix, offRouteM: Double?) {
        val r = rec ?: return
        r.addFix(fix.atMs, fix.lat, fix.lon, fix.accuracyM, (fix.speedMps ?: 0.0).coerceAtLeast(0.0), offRouteM)
        onSpeed(r.avgKmh(nowMs()), r.movingKmh())
    }

    @Synchronized
    override fun finish(reason: String, atMs: Long) = finishLocked(reason, atMs)

    private fun finishLocked(reason: String, atMs: Long) {
        val r = rec ?: return
        rec = null
        onSpeed(null, null)
        val json = r.toJson(atMs, reason)
        val avg = r.avgKmh(atMs)
        val data = mutableMapOf<String, Any?>("reason" to reason, "dev" to r.deviations.size, "m" to r.distM.roundToLong())
        if (avg != null) data["avg"] = (avg * 10).roundToLong() / 10.0
        logEvent("route_end", data)
        if (!loggingEnabled() || r.trackSize < 2) return
        writeRoute(r.startedAtMs, json.toString())
    }
}
