package com.kuryelog.logic.core

import org.json.JSONObject

/**
 * G1 vardiyanın kararları ve sırası (CORE.md 1.2–1.5; RS `GpsService.kt`): hangi istek vardiyayı açar, hangi olay ve tercih hangi
 * sırayla yazılır, ev kuralı ne zaman bitirir, H4. Android ön plan servisi yalnız [Platform]'u uygular; diğer modüllerin vardiya
 * başı/sonu işleri [Hooks]'tan (Aşama 2'de boş). Tek iş parçacığında çağrılır (servisin ana iş parçacığı).
 */
class ShiftMachine(
    private val prefs: KuryelogPrefs.Values,
    private val log: LogStore,
    private val home: HomeRepository,
    private val clock: Clock,
    private val platform: Platform,
    private val hooks: Hooks = Hooks.NONE,
    private val importing: () -> Boolean = { false },
    private val lang: () -> Lang = { Lang.TR },
    distance: (GpsFix, GpsFix) -> Double = GpsFix::haversineM,
    /** D8 birikimi süreç boyunca (Rawstats `Store.distanceM`): servis yeniden yaratılsa da aynı süreçte kalır. */
    val track: GpsTrack = GpsTrack(distance),
) {
    /** Android'in yaptıkları. Hata fırlatabilecekler belirtilmiş; gerisi fırlatmamalı (fırlatırsa makine kaydeder ve sürer). */
    interface Platform {
        /** `startForeground(SHIFT_FGS, …, LOCATION)`; Android reddederse fırlatır. */
        fun startForeground()
        fun stopForeground()
        fun stopSelf()
        /** GPS sağlayıcısı, 2000 ms, 0 m. İzin yoksa `SecurityException`, başka hata da fırlatabilir. */
        fun requestGps()
        fun removeGps()
        /** H4 bildirimi ([NotificationIds.SHIFT_INTERRUPTED]): dokununca `MANUAL_START` ile ön plan servisi. */
        fun showShiftInterrupted()
        fun cancel(id: Int)
        /** Ev izleyici yeniden denetlensin (vardiyada dinlemez, vardiya bitince dinler). */
        fun recheckHomeWatch()
        /** Vardiya durumu değişti (A2.5 `ShiftPort.state`). */
        fun published(state: ShiftState) {}
    }

    /** Diğer modüllerin vardiya işleri (A2.5'te `ShiftPort` dinleyicileri). Her biri korumalı çağrılır; hata vardiyayı durdurmaz. */
    interface Hooks {
        /** Yeni vardiya (RS: scooter odometresi sıfır, cepteki nakit sorusu, CASH PENDING başlangıcı). Kaynak `shift_start`. */
        fun onNewShift(nowMs: Long, reason: String) {}
        /** Her başlatmada, yapışkan yeniden başlatma dahil (RS: `OrderTracker.start`, `ScooterLink.onShiftStart`, ekran okuma denetimi). Kaynak `shift_orders`. */
        fun onRunning() {}
        /** Ev kuralından sonra, `gps.csv`'ye yazılmış her fix (RS: sipariş varış/bekleme, scooter yeniden bağlanma, sipariş tik'i). Kaynak `gps_orders`. */
        fun onFix(fix: GpsFix, speedMps: Double?, nowMs: Long) {}
        /** D8 yol sayılan adım (RS `OrderTracker.distance`). */
        fun onDistance(m: Double) {}
        /** Açık (aktif) sipariş var: ev kuralı bitirmez (F4.5b; RS `OrderTracker.hasOpenOrder`). */
        fun busy(): Boolean = false
        /** Scooter'ın vardiya odometresi (D8; `:scooter`). */
        fun odometer(): ShiftDistance.Odo? = null
        /** Vardiya bitti; `shift` false yazılmadan önce (RS: CASH PENDING sonu, günlük paket, `OrderTracker.stop`, `ScooterLink.onShiftEnd`, odometre sıfır). Kaynak `shift_end`. */
        fun onEnd(startMs: Long, nowMs: Long, reason: String) {}

        object NONE : Hooks
    }

    enum class StartResult { STICKY, NOT_STICKY }

    private var lastSave = 0L

    fun shiftOn(): Boolean = KuryelogPrefs.shiftOn(prefs)

    /**
     * `onStartCommand` (CORE.md 1.2). [action] null = Android'in yapışkan yeniden başlatması; [homeRevision] `HOME_START`'ın
     * `homeRevision` ekstrası (yoksa null).
     */
    fun onStartCommand(action: String?, homeRevision: Long?): StartResult {
        KuryelogPrefs.ensureManualMode(prefs)
        if (action == ACTION_STOP) {
            // D8: yeni süreç (eskisi vardiyada öldürüldü) 0 km'den başlar; kayıtlı değer sayılır.
            track.distanceM = maxOf(track.distanceM, prefs.getFloat(KuryelogPrefs.DIST, 0f).toDouble())
            stop(HomeShift.REASON_MANUAL)
            return StartResult.NOT_STICKY
        }
        // K38: kayıtlı evden çıkış elle başlatma gibi sayılır (ev yoksa ya da istek eski evin revizyonuyla geldiyse asla).
        val homeStart = action == ACTION_HOME_START && runCatching { home.acceptsRevision(homeRevision) }.getOrDefault(false)
        if (action == ACTION_HOME_START) safe { platform.cancel(NotificationIds.HOME_AUTO_START) }
        if (!ManualSessionPolicy.mayStart(action == ACTION_START || homeStart, shiftOn(), importing())) {
            refuse(action)
            return StartResult.NOT_STICKY
        }
        try {
            platform.startForeground()
        } catch (e: Exception) {
            log.event("gps_error", JSONObject().put("err", e.toString()))
            if (ManualSessionPolicy.onStartFailure(action == null, shiftOn()) == ManualSessionPolicy.StartFailure.INTERRUPTED) {
                // H4: vardiya açık kalır (shift_end yok); bildirime dokunmak Android'in servisi başlatmaya izin verdiği kullanıcı eylemi.
                prefs.put(mapOf(KuryelogPrefs.SHIFT_INTERRUPTED_AT to clock.wallMs()))
                log.event("shift_interrupted", JSONObject().put("err", e.toString()))
                safe { platform.showShiftInterrupted() }
                publish()
            } else {
                log.lastError = CoreText.START_FAILED.get(lang()) + e.message
            }
            safe { platform.stopSelf() }
            return StartResult.NOT_STICKY
        }
        val now = clock.wallMs()
        // RS 16 Eki: buradan sonra hata veren adım kaydedilir ve vardiya sürer.
        guarded("shift_resume", log) {
            val interruptedAt = prefs.getLong(KuryelogPrefs.SHIFT_INTERRUPTED_AT, 0L)
            if (interruptedAt > 0L) {
                if (prefs.getBoolean(KuryelogPrefs.SHIFT, false)) log.event("shift_resumed", JSONObject().put("interruptedAt", interruptedAt))
                prefs.put(mapOf(KuryelogPrefs.SHIFT_INTERRUPTED_AT to null))
                platform.cancel(NotificationIds.SHIFT_INTERRUPTED)
            }
        }
        if (!prefs.getBoolean(KuryelogPrefs.SHIFT, false)) {
            val reason = if (homeStart) HomeShift.REASON_DEPARTURE else HomeShift.REASON_MANUAL
            prefs.put(mapOf(KuryelogPrefs.SHIFT to true, KuryelogPrefs.MANUAL_SESSION to true, KuryelogPrefs.AUTO_STARTED to false,
                KuryelogPrefs.SHIFT_START to now, KuryelogPrefs.DIST to 0f, KuryelogPrefs.SHIFT_START_REASON to reason))
            track.reset()
            log.lastLocation = null
            guarded("shift_start", log) { home.changeGuard { if (homeStart) it.endArmed = true else it.onManualStart() } }
            log.event("shift_start", JSONObject().put("auto", homeStart).put("reason", reason))
            guarded("shift_start", log) { publish() }
            guarded("shift_start", log) { hooks.onNewShift(now, reason) }
        } else {
            // Çalışan vardiyanın yapışkan yeniden başlaması: bu sürecin saydığının altına inmez.
            track.distanceM = maxOf(track.distanceM, prefs.getFloat(KuryelogPrefs.DIST, 0f).toDouble())
            guarded("shift_start", log) { publish() }
        }
        guarded("shift_orders", log) { hooks.onRunning() }
        requestGps()
        safe { platform.recheckHomeWatch() }
        return StartResult.STICKY
    }

    /** RS 16 Eki: ön plan diye başlatılan istek durmadan önce `startForeground`'a ulaşmalı; yoksa Android bütün uygulamayı kapatır. */
    private fun refuse(action: String?) {
        if (action in FOREGROUND_ACTIONS) runCatching { platform.startForeground(); platform.stopForeground() }
            .onFailure { log.event("gps_error", JSONObject().put("err", it.toString())) }
        safe { platform.stopSelf() }
    }

    private fun requestGps() {
        try {
            platform.removeGps()
            platform.requestGps()
        } catch (e: SecurityException) {
            log.event("gps_no_permission")
        } catch (e: Exception) {
            log.event("gps_error", JSONObject().put("err", e.toString()))
        }
    }

    /** Konum geri çağrısı (CORE.md 1.3): hata kaydedilir, fırlatılmaz. */
    fun onLocation(fix: GpsFix) { guarded("gps", log) { onFix(fix) } }

    private fun onFix(f: GpsFix) {
        if (!shiftOn()) return
        if (!track.fresh(f, clock.monoMs())) return
        val derived = track.derivedSpeed(f)
        val speed = if (f.hasSpeed) f.speed.toDouble() else derived
        track.seen(f)
        log.lastLocation = LogStore.Loc(f.lat, f.lon, f.accuracy.toDouble(), f.timeMs)
        // K38: eve dönüş vardiyayı bitirir (yarıçap dışına bir kez çıktıktan sonra); bitiş fix'i gps.csv'ye yazılmaz.
        val busy = runCatching { hooks.busy() }.getOrDefault(false)
        if (home.observe(f.homeFix(), true, busy) == HomeShift.Decision.END) { stop(HomeShift.REASON_ARRIVAL); return }
        if (!java.io.File(log.dayDir(), "gps.csv").exists()) log.append("gps.csv", GpsFix.CSV_HEADER)
        log.append("gps.csv", f.csvLine(log.ts(f.timeMs)))
        val step = track.accumulate(f)
        if (step > 0) guarded("gps_orders", log) { hooks.onDistance(step) }
        val now = clock.wallMs()
        if (now - lastSave > SAVE_MS) {
            lastSave = now
            prefs.put(mapOf(KuryelogPrefs.DIST to track.distanceM.toFloat()))
        }
        guarded("gps_orders", log) { hooks.onFix(f, speed, now) }
    }

    fun onProviderDisabled() { log.event("gps_disabled") }

    /**
     * Bitirme (CORE.md 1.4): [reason] `manual` ya da `home_arrival`. `shift` false yazılana dek hiçbir adım fırlatmaz (bir adımın
     * hatası vardiyayı açık bırakmaz).
     */
    fun stop(reason: String) {
        if (!shiftOn()) { safe { platform.stopSelf() }; return }
        val start = prefs.getLong(KuryelogPrefs.SHIFT_START, clock.wallMs())
        // D8: GPS (bu süreç ya da eskisinin kayıtlı değeri) ve scooter odometresi; büyüğü yazılır.
        guarded("shift_end", log) {
            val d = ShiftDistance.result(track.distanceM, prefs.getFloat(KuryelogPrefs.DIST, 0f).toDouble(), runCatching { hooks.odometer() }.getOrNull())
            track.distanceM = d.gpsKm * 1000.0
            log.event("shift_end", JSONObject().put("km", d.km).put("km_gps", d.gpsKm).put("km_scooter", d.scooterKm ?: JSONObject.NULL)
                .put("km_from_scooter", d.fromScooter).put("min", (clock.wallMs() - start) / 60000.0).put("reason", reason))
        }
        if (reason == HomeShift.REASON_MANUAL) guarded("shift_end", log) { home.changeGuard { it.onManualStop() } }
        guarded("shift_end", log) { hooks.onEnd(start, clock.wallMs(), reason) }
        prefs.put(mapOf(KuryelogPrefs.SHIFT to false, KuryelogPrefs.MANUAL_SESSION to false, KuryelogPrefs.DIST to track.distanceM.toFloat(),
            KuryelogPrefs.SHIFT_ENDED_AT to clock.wallMs(), KuryelogPrefs.SHIFT_END_REASON to reason))
        guarded("shift_end", log) { publish() }
        for (id in NotificationIds.SHIFT_END_CANCEL) safe { platform.cancel(id) }
        safe { platform.recheckHomeWatch() }
        safe { platform.removeGps() }
        safe { platform.stopForeground() }
        safe { platform.stopSelf() }
    }

    /** Tercihlerden `ShiftState` v1 (CORE.md 1.8). */
    fun state(): ShiftState = ShiftState.parse(ShiftStateSnapshot.json(shiftOn(), prefs.getLong(KuryelogPrefs.SHIFT_START, 0L),
        prefs.getLong(KuryelogPrefs.SHIFT_ENDED_AT, 0L), prefs.getString(KuryelogPrefs.SHIFT_END_REASON, null),
        prefs.getLong(KuryelogPrefs.SHIFT_INTERRUPTED_AT, 0L), clock.wallMs()).toString())!!

    private fun publish() = platform.published(state())

    /** Fırlatmaması gereken platform çağrısı fırlatırsa: kaydet, sür. */
    private inline fun safe(block: () -> Unit) { guarded("shift", log, block) }

    companion object {
        const val ACTION_STOP = "com.kuryelog.app.STOP"
        const val ACTION_START = "com.kuryelog.app.MANUAL_START"
        /** K38: ev izleyici gönderir (yalnız ev kayıtlıyken); ekstra [EXTRA_HOME_REVISION]. */
        const val ACTION_HOME_START = "com.kuryelog.app.HOME_START"
        const val EXTRA_HOME_REVISION = "homeRevision"
        /** Yalnız `startForegroundService` / `PendingIntent.getForegroundService` ile gelenler (`STOP` düz başlatma). */
        val FOREGROUND_ACTIONS = setOf(ACTION_START, ACTION_HOME_START)
        const val SAVE_MS = 15_000L
    }
}
