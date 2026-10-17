package com.kuryelog.logic.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Modüller arası arayüzler (plan 4.3, CORE.md 1.11). Modüller birbirini yalnız bunlarla görür; tek telefonda aynı süreçte doğrudan
 * çağrılır, 2 telefon modunda `:link` aynı arayüzün uzak uygulamasını takar (A8). Üretici modül yazılana kadar `NONE` uygulamaları
 * "veri yok" der (hiçbir şey uydurmaz).
 */

/** G1 vardiya (`:core` → hepsi). Başlatma ve bitirme elle (`manual`); evden çıkış/eve dönüş kuralın kendisi (CORE.md 1.6). */
interface ShiftPort {
    val state: StateFlow<ShiftState>
    fun start(reason: String = HomeShift.REASON_MANUAL)
    fun stop(reason: String = HomeShift.REASON_MANUAL)
}

/** Teklifler, sipariş aşamaları ve gün özeti (`:orders` → `:map`, `:economics`, `:link`). */
interface OrderEvents {
    val offers: StateFlow<List<OfferSummary>>
    val stages: StateFlow<TripStages>
    val day: StateFlow<DaySummary?>
    /** Açık (aktif) sipariş var: ev kuralı vardiyayı bitirmez (F4.5b; `ShiftMachine.Hooks.busy`). */
    fun busy(): Boolean = stages.value.busy

    object NONE : OrderEvents {
        override val offers: StateFlow<List<OfferSummary>> = MutableStateFlow<List<OfferSummary>>(emptyList()).asStateFlow()
        override val stages: StateFlow<TripStages> = MutableStateFlow(TripStages.EMPTY).asStateFlow()
        override val day: StateFlow<DaySummary?> = MutableStateFlow<DaySummary?>(null).asStateFlow()
    }
}

/** Scooter (`:scooter` → `:orders`, `:economics`, `:map`). [energy] uzakta yanıt gelene dek askıda kalır. */
interface ScooterBridge {
    fun orderAccepted(tripId: String, atWallMs: Long)
    fun orderDone(tripId: String, atWallMs: Long)
    /** Kapalı pencere `[from, to]`; ölçülemezse `not_measured` blok, kaynak yoksa null. */
    suspend fun energy(fromWallMs: Long, toWallMs: Long): EnergyBlock?
    /** İç batarya %, kare ≤15 sn tazeyse; yoksa null. */
    fun batteryPct(): Double?
    val live: StateFlow<ScooterLive?>

    object NONE : ScooterBridge {
        override fun orderAccepted(tripId: String, atWallMs: Long) {}
        override fun orderDone(tripId: String, atWallMs: Long) {}
        override suspend fun energy(fromWallMs: Long, toWallMs: Long): EnergyBlock? = null
        override fun batteryPct(): Double? = null
        override val live: StateFlow<ScooterLive?> = MutableStateFlow<ScooterLive?>(null).asStateFlow()
    }
}

/** Navigasyon (`:map` → `:orders`, `:link`). */
interface NavPort {
    val route: StateFlow<RouteStatus?>
    fun open(target: NavTarget)

    object NONE : NavPort {
        override val route: StateFlow<RouteStatus?> = MutableStateFlow<RouteStatus?>(null).asStateFlow()
        override fun open(target: NavTarget) {}
    }
}

/** Hava (`:map` → `:orders` R15, `:economics`). En çok 90 dk eski; yoksa null. */
interface WeatherPort {
    fun current(): WeatherNow?

    object NONE : WeatherPort {
        override fun current(): WeatherNow? = null
    }
}

/**
 * Bildirim (hepsi → `:core`; G4). Numara yalnız [NotificationIds] tablosundan; YolGo kökenli bildirimler [NotifyPolicy.TIMEOUT_MS]
 * sonra kalkar. Dokununca ne olacağı Android katmanının işi ([tapAction]: vardiya servisine gidecek eylem; null = uygulamayı aç).
 */
interface NotifyPort {
    fun post(n: Notify)
    fun cancel(id: Int)
}

data class Notify(
    val id: Int,
    val channel: String,
    val title: String,
    val text: String,
    /** 0: kendiliğinden kalkmaz. */
    val timeoutMs: Long = 0L,
    val ongoing: Boolean = false,
    val alertOnce: Boolean = true,
    val autoCancel: Boolean = true,
    val tapAction: String? = null,
) {
    init {
        require(NotificationIds.TABLE.values.any { id in it }) { "Bildirim numarası tabloda yok: $id" }
        require(timeoutMs >= 0)
    }
}

/**
 * Modüllerin vardiya işleri ([ShiftMachine.Hooks]) tek yerde (CORE.md 1.11): her modül kendi aşamasında [add] ile takar. Her dinleyici
 * ayrı korunur: birinin hatası ötekileri durdurmaz ([sink]'e kaydedilir). `busy` biri bile evet derse evet; odometre ilk ölçen.
 */
class ShiftListeners(private val sink: () -> FailureSink) : ShiftMachine.Hooks {
    private val list = java.util.concurrent.CopyOnWriteArrayList<Pair<String, ShiftMachine.Hooks>>()

    /** [name] hata kaydının kaynağına eklenir (`shift_start/<name>` gibi). */
    fun add(name: String, hooks: ShiftMachine.Hooks) { list += name to hooks }
    fun remove(hooks: ShiftMachine.Hooks) { list.removeAll { it.second === hooks } }

    private inline fun each(source: String, block: (ShiftMachine.Hooks) -> Unit) {
        for ((name, h) in list) guarded("$source/$name", sink()) { block(h) }
    }

    override fun onNewShift(nowMs: Long, reason: String) = each("shift_start") { it.onNewShift(nowMs, reason) }
    override fun onRunning() = each("shift_orders") { it.onRunning() }
    override fun onFix(fix: GpsFix, speedMps: Double?, nowMs: Long) = each("gps_orders") { it.onFix(fix, speedMps, nowMs) }
    override fun onDistance(m: Double) = each("gps_orders") { it.onDistance(m) }
    override fun onEnd(startMs: Long, nowMs: Long, reason: String) = each("shift_end") { it.onEnd(startMs, nowMs, reason) }
    override fun busy(): Boolean = list.any { (name, h) -> guarded("busy/$name", sink()) { h.busy() } == true }
    override fun odometer(): ShiftDistance.Odo? = list.firstNotNullOfOrNull { (name, h) -> guarded("odometer/$name", sink()) { h.odometer() } }
}
