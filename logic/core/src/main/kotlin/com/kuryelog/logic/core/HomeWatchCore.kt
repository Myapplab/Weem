package com.kuryelog.logic.core

import org.json.JSONObject

/**
 * Vardiya dışında ev izleyici (CORE.md 1.7; RS `HomeLocation.kt:61-168` `HomeWatcher`): ev kayıtlı, vardiya kapalı, içe aktarma yok
 * ve "her zaman konum" izni varken konum dinler; evden çıkış ([HomeShift.Decision.START]) vardiyayı `HOME_START` ile açar. İstek
 * planı [HomeWatchPlan]. Süreç başına bir tane; servis tik'leri, vardiya başı/sonu ve uygulama açılışı [refresh] çağırır (en çok 5 sn'de bir).
 * Süreler monoton saatte.
 */
class HomeWatchCore(
    private val home: HomeRepository,
    private val log: LogStore,
    private val platform: Platform,
    private val shiftOn: () -> Boolean,
    private val importing: () -> Boolean,
    /** (ince konum, arka plan konumu) izinleri. */
    private val permissions: () -> Pair<Boolean, Boolean>,
) {
    interface Platform {
        /** [mode] isteği (eskisi kaldırılmış olarak): LOW dengeli güç 30 sn, HIGH GPS 2 sn. İzin yoksa `SecurityException`, başka hata da fırlatabilir. */
        fun request(mode: HomeWatchPlan.Mode)
        fun remove()
        /** `startForegroundService(HOME_START + homeRevision)`; Android arka plandan reddederse fırlatır. */
        fun startShift(revision: Long)
        /** 5101 "Evden çıktın": dokununca aynı `HOME_START` isteği (ön plan servisi olarak). */
        fun showHomeStart(revision: Long)
    }

    private val plan = HomeWatchPlan()
    var listening = false
        private set
    /** Kayıtlı istek (null: yok). */
    var mode: HomeWatchPlan.Mode? = null
        private set
    private var lastCheck = 0L

    fun resetPause() { lastCheck = 0L }
    fun resetPlan() { plan.reset(); lastCheck = 0L }
    /** Ev değişti: eski GPS penceresi ve bekleme de atılır. */
    fun homeChanged() { stop(); resetPlan() }
    /** Vardiya başı/sonu ya da ev değişti: 5 sn beklemeden. */
    fun recheck(now: Long) { resetPause(); refresh(now) }

    fun refresh(now: Long) {
        if (lastCheck != 0L && now - lastCheck < CHECK_MS) return
        lastCheck = now
        val (fine, background) = permissions()
        if (!home.mayWatch(shiftOn(), importing(), fine, background)) { if (listening) stop(); plan.reset(); return }
        val m = plan.tick(now)
        if (!listening || m != mode) start(m)
    }

    private fun start(next: HomeWatchPlan.Mode) {
        try {
            if (listening) runCatching { platform.remove() }
            platform.request(next)
            listening = true; mode = next
        } catch (e: SecurityException) {
            listening = false; mode = null
            log.event("home_watch_no_permission")
        } catch (e: Exception) {
            listening = false; mode = null
            log.event("home_watch_error", JSONObject().put("err", e.javaClass.simpleName))
        }
    }

    fun stop() {
        if (!listening) return
        runCatching { platform.remove() }
        listening = false; mode = null
    }

    /** İzleyicinin fix'i; [now] monoton saat. */
    fun onFix(fix: HomeShift.Fix, now: Long) {
        if (shiftOn() || importing()) return
        val decision = home.observe(fix, false)
        if (decision != HomeShift.Decision.START) {
            // E6: ayrılmanın olası olduğunu söyleyen düşük güçlü fix GPS'e geçirir (pencereden sonra geri).
            val d = home.engine()?.distanceToHomeM(fix) ?: return
            val next = plan.onFix(d, fix.accuracyM, fix.speedMps, now)
            if (next != mode) start(next)
            return
        }
        plan.reset()
        val revision = home.point()?.updatedAt ?: 0L
        try {
            platform.startShift(revision)
        } catch (e: Exception) {
            // Android arka plandan ön plan servisini reddedebilir; tek dokunuş ister (RS 0.22.2).
            log.event("home_auto_start_failed", JSONObject().put("err", e.javaClass.simpleName))
            runCatching { platform.showHomeStart(revision) }
        }
    }

    companion object {
        const val CHECK_MS = 5_000L
    }
}
