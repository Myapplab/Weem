package com.kuryelog.logic.link

import com.kuryelog.logic.core.BatterySummary
import com.kuryelog.logic.core.DaySummary
import com.kuryelog.logic.core.EnergyBlock
import com.kuryelog.logic.core.EnergyRequest
import com.kuryelog.logic.core.EnergyResult
import com.kuryelog.logic.core.EnergyStep
import com.kuryelog.logic.core.FailureSink
import com.kuryelog.logic.core.LateEnergy
import com.kuryelog.logic.core.NavPort
import com.kuryelog.logic.core.NavTarget
import com.kuryelog.logic.core.OfferSummary
import com.kuryelog.logic.core.OrderEvents
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.core.ShiftPort
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.core.TripStages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** Köprünün gördüğü bağlantı (`:link` `LinkRuntime`; testte sahte). Yayınlama hatası çağırana atılır. */
interface LinkPort {
    fun linked(): Boolean
    fun publishLatest(type: String, payload: ByteArray, ttlMs: Long): UUID
    fun publishReliable(type: String, payload: ByteArray, ttlMs: Long): UUID
}

/**
 * Roller ve mesajlar (plan 4.4–4.5, LINK.md 4): bir telefonun modülleri ile öteki telefon arasındaki köprü. Saf; Android katmanı
 * (`:app` `TwoPhone`) yerel akışları buraya verir (`provider*`/`receiver*`), gelen iletileri [incoming]'e, saati [clock]'a, bağlantı
 * durumunu [linkChanged]'e iletir ve saniyede bir [tick] çağırır. Modüller farkı görmez: Provider'da `:orders`/`:economics`/`:map`
 * [scooter]'ı (uzak `ScooterBridge`), Receiver'da `:map` [orders]'ı (uzak `OrderEvents`) kullanır; Provider'da [nav] rotayı Receiver'a
 * yollar, ulaşamazsa yerelde açar.
 *
 * İş parçacığı güvenli (tek kilit); [local] çağrıları kilit dışında. Hiçbir yerel değer uydurulmaz: veri gelmediyse null/boş.
 */
class PhoneBridge(
    val role: LinkRole,
    private val link: LinkPort,
    private val wall: () -> Long,
    private val scope: CoroutineScope,
    private val local: Local = Local(),
    private val failures: FailureSink = FailureSink { _, _ -> },
    private val energyWaitMs: Long = ENERGY_WAIT_MS,
) {
    /**
     * Bu telefondaki yerel modüller. [nav]: Provider'da yedek (Receiver'a ulaşılamazsa), Receiver'da gelen hedefi açar. [scooter]:
     * Receiver'da enerji yanıtı ve sipariş sayacı. [shift]: Receiver'da Provider'ın vardiyasını izler. [navOpened]: Receiver'da hedef
     * açıldı (harita sekmesine geç).
     */
    class Local(
        val nav: NavPort = NavPort.NONE,
        val scooter: ScooterBridge = ScooterBridge.NONE,
        val shift: ShiftPort? = null,
        val navOpened: (NavTarget) -> Unit = {},
    )

    private val lock = Any()
    @Volatile private var offsetMs = 0L
    @Volatile private var up = false

    // --- Provider: giden ---
    private var sentShift: ShiftState? = null
    private var sentOffers: List<OfferSummary>? = null
    private var sentOffersAt = Long.MIN_VALUE / 2
    private var pendingOffers: List<OfferSummary>? = null
    private var sentStages: TripStages? = null
    private var sentDay: DaySummary? = null
    private var sentDayAt = Long.MIN_VALUE / 2
    private var pendingDay: DaySummary? = null
    private val navWaiting = LinkedHashMap<UUID, Pair<NavTarget, Long>>()
    private val energyWaiting = LinkedHashMap<String, EnergyWait>()

    // --- Provider: gelen ---
    private var battery: BatterySummary? = null
    private var batteryAt = 0L
    private val _live = MutableStateFlow<ScooterLive?>(null)
    private val _route = MutableStateFlow<RouteStatus?>(null)
    private val _late = MutableSharedFlow<LateEnergy>(extraBufferCapacity = 64)

    // --- Receiver: giden ---
    private var sentBattery: BatterySummary? = null
    private var sentFresh = false
    private var sentRoute: RouteStatus? = null
    private var sentRouteAt = Long.MIN_VALUE / 2
    private var pendingRoute: RouteStatus? = null
    private var routeDirty = false
    private val answered = LinkedHashMap<String, EnergyResult>()
    private val answering = HashSet<String>()

    // --- Receiver: gelen ---
    private val _offers = MutableStateFlow<List<OfferSummary>>(emptyList())
    private var offersAt = 0L
    private val _stages = MutableStateFlow(TripStages.EMPTY)
    private val _day = MutableStateFlow<DaySummary?>(null)
    private val _remoteShift = MutableStateFlow<ShiftState?>(null)
    private var handledStart: Long? = null
    private var handledEnd: Long? = null
    private var activeTrips: Set<String>? = null
    private val openedTargets = LinkedHashSet<String>()

    private val _peerRole = MutableStateFlow<LinkRole?>(null)
    /** Öteki telefonun bildirdiği rol (bağlanınca gelir). */
    val peerRole: StateFlow<LinkRole?> = _peerRole.asStateFlow()
    /** Receiver'da Provider'ın son vardiya durumu (yerel saate çevrilmiş). */
    val remoteShift: StateFlow<ShiftState?> = _remoteShift.asStateFlow()

    /** İki telefon aynı rolde: veri akmaz, iki ekranda uyarı. */
    fun conflict(): Boolean = role != LinkRole.OFF && _peerRole.value == role

    // ------------------------------------------------------------------ uzak portlar

    /** Receiver'da `:map`'in kullandığı uzak `OrderEvents` (Provider'ın teklifleri, aşamaları, gün özeti). */
    val orders: OrderEvents = object : OrderEvents {
        override val offers: StateFlow<List<OfferSummary>> = _offers.asStateFlow()
        override val stages: StateFlow<TripStages> = _stages.asStateFlow()
        override val day: StateFlow<DaySummary?> = _day.asStateFlow()
    }

    /**
     * Provider'da uzak `ScooterBridge` (S11: Provider scooter'a bağlanmaz). [ScooterBridge.live] Receiver'ın `BatterySummary`'sinden
     * (yalnız %, menzil, sıcaklık); saniyelik adım yok. [ScooterBridge.energy] isteği teslim zorunlu yollar ve bağlıyken en çok
     * [energyWaitMs] bekler (varsayılan 0: hemen null); zamanında gelmeyen yanıt [ScooterBridge.late]'e düşer (ekonomi düzeltme satırı yazar).
     */
    val scooter: ScooterBridge = object : ScooterBridge {
        override fun orderAccepted(tripId: String, atWallMs: Long) {}
        override fun orderDone(tripId: String, atWallMs: Long) {}
        override suspend fun energy(fromWallMs: Long, toWallMs: Long): EnergyBlock? = remoteEnergy(fromWallMs, toWallMs)
        override fun batteryPct(): Double? = _live.value?.takeIf { it.fresh }?.scooter?.internalPct
        override val live: StateFlow<ScooterLive?> = _live.asStateFlow()
        override val steps: SharedFlow<EnergyStep> = MutableSharedFlow<EnergyStep>().asSharedFlow()
        override val late: SharedFlow<LateEnergy> = _late.asSharedFlow()
    }

    /** Provider'da `NavPort`: rota Receiver'ın `RouteStatus`'u; [NavPort.open] hedefi Receiver'a yollar (ulaşamazsa yerelde açar). */
    val nav: NavPort = object : NavPort {
        override val route: StateFlow<RouteStatus?> = _route.asStateFlow()
        override fun open(target: NavTarget) {
            if (!forwardNav(target)) local.nav.open(target)
        }
    }

    // ------------------------------------------------------------------ bağlantı

    /** Saat farkı (eşin saati − bu telefonunki), `LinkRuntime.Listener.onClock`'tan. */
    fun clock(offsetMs: Long) { this.offsetMs = offsetMs }

    /** Bağlantı açıldı/koptu. Açılınca rol yollanır (son durumları `Messenger` kanal değişiminde kendisi yeniden yollar). */
    fun linkChanged(linked: Boolean, now: Long = wall()) {
        up = linked
        if (linked) publish(LinkTopic.ROLE, LinkPayloads.role(role))
        else synchronized(lock) {
            _peerRole.value = null
            if (role == LinkRole.RECEIVER) _offers.value = emptyList()
            refreshLive(now)
        }
    }

    // ------------------------------------------------------------------ Provider girdileri

    /** Vardiya: içerik değişince (yalnız `generatedAtMs` değişimi sayılmaz; RS E8 `FeedChanges`). */
    fun providerShift(s: ShiftState) {
        if (role != LinkRole.PROVIDER) return
        val send = synchronized(lock) { if (sentShift?.copy(generatedAtMs = 0) == s.copy(generatedAtMs = 0)) false else { sentShift = s; true } }
        if (send) publish(LinkTopic.SHIFT, LinkPayloads.shift(s))
    }

    /**
     * Teklifler (RS E8 `FeedChanges`): içerik değişince — kare saati `lastSeenAtMs` yalnız [OFFERS_REFRESH_MS]'lik dilime girince
     * değişim sayılır — ve teklif varken [OFFERS_REFRESH_MS]'de bir (Receiver sessizlikte listeyi boşaltır); iki gönderim arası en az
     * [OFFERS_MIN_GAP_MS] (plan 4.4 "≥200 ms"): aradaki son değişiklik [tick]'te mutlaka gider, gönderilmiş içeriğe dönen titreşim gitmez.
     */
    fun providerOffers(list: List<OfferSummary>, now: Long = wall()) {
        if (role != LinkRole.PROVIDER) return
        val send = synchronized(lock) {
            val last = sentOffers
            val changed = last == null || norm(last) != norm(list)
            val refresh = list.isNotEmpty() && now - sentOffersAt >= OFFERS_REFRESH_MS
            when {
                !changed && !refresh -> { pendingOffers = null; null }
                now - sentOffersAt < OFFERS_MIN_GAP_MS -> { pendingOffers = list; null }
                else -> { markOffers(list, now); list }
            }
        }
        send?.let { publish(LinkTopic.OFFERS, LinkPayloads.offers(it)) }
    }

    /** Aşamalar: her değişimde teslim zorunlu (plan 4.5). */
    fun providerStages(s: TripStages) {
        if (role != LinkRole.PROVIDER) return
        val send = synchronized(lock) { if (s == sentStages) false else { sentStages = s; true } }
        if (send) publish(LinkTopic.STAGES, LinkPayloads.stages(s))
    }

    /** Gün özeti: değişince, en çok dakikada bir (gün değişince hemen). */
    fun providerDay(d: DaySummary?, now: Long = wall()) {
        if (role != LinkRole.PROVIDER || d == null) return
        val send = synchronized(lock) {
            when {
                d == sentDay -> { pendingDay = null; false }
                sentDay?.day == d.day && now - sentDayAt < DAY_MIN_GAP_MS -> { pendingDay = d; false }
                else -> { sentDay = d; sentDayAt = now; pendingDay = null; true }
            }
        }
        if (send) publish(LinkTopic.DAY, LinkPayloads.day(d))
    }

    /**
     * Provider'da yakalanan hedef (Wolt/Bolt "Navigasyon"): bağlıysa Receiver'a gider ve `true` döner; [NAV_FALLBACK_MS] içinde onay
     * gelmezse [tick] rotayı yerelde açar. Bağlı değilse `false` (çağıran yerelde açar).
     */
    fun forwardNav(target: NavTarget, now: Long = wall()): Boolean {
        if (role != LinkRole.PROVIDER || !link.linked() || conflict()) return false
        val id = publish(LinkTopic.NAV_TARGET, LinkPayloads.navTarget(target)) ?: return false
        synchronized(lock) {
            navWaiting[id] = target to now
            while (navWaiting.size > 16) navWaiting.remove(navWaiting.keys.first())
        }
        return true
    }

    /** `LinkRuntime.Listener.onAcked`. */
    fun acked(type: String, id: UUID) {
        if (type == LinkTopic.NAV_TARGET.type) synchronized(lock) { navWaiting.remove(id) }
    }

    private class EnergyWait(val fromWallMs: Long, val toWallMs: Long, val result: CompletableDeferred<EnergyBlock> = CompletableDeferred()) {
        var late = false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun remoteEnergy(fromWallMs: Long, toWallMs: Long): EnergyBlock? {
        if (role != LinkRole.PROVIDER || toWallMs <= fromWallMs) return null
        val id = energyId(fromWallMs, toWallMs)
        val w = EnergyWait(fromWallMs, toWallMs)
        synchronized(lock) {
            energyWaiting[id] = w
            while (energyWaiting.size > MAX_ENERGY_WAITING) energyWaiting.remove(energyWaiting.keys.first())
        }
        val req = EnergyRequest(id, "w:$fromWallMs-$toWallMs", EnergyRequest.Kind.LEG, fromWallMs, toWallMs)
        if (publish(LinkTopic.ENERGY_REQUEST, LinkPayloads.energyRequest(req)) == null) {
            synchronized(lock) { energyWaiting.remove(id) }
            return null
        }
        val got = if (link.linked() && energyWaitMs > 0) withTimeoutOrNull(energyWaitMs) { w.result.await() } else null
        if (got != null) return got
        return synchronized(lock) {
            if (w.result.isCompleted) { energyWaiting.remove(id); w.result.getCompleted() } else { w.late = true; null }
        }
    }

    // ------------------------------------------------------------------ Receiver girdileri

    /** Scooter canlı değeri: `BatterySummary.due` (60 sn, %1, bağlantı değişimi) ya da tazelik değişimi. Hiç bağlanmadıysa bağlantısız özet. */
    fun receiverScooter(live: ScooterLive?, now: Long = wall()) {
        if (role != LinkRole.RECEIVER) return
        val b = live?.let { BatterySummary.of(it, now) } ?: BatterySummary(now, linkActive = false)
        // RS E8: kare yaşı değişim değildir, tazeliğini yitirmek (≤15 sn) değişimdir.
        val fresh = live?.fresh == true
        val send = synchronized(lock) {
            if (b.due(sentBattery) || fresh != sentFresh) { sentBattery = b; sentFresh = fresh; true } else false
        }
        if (send) publish(LinkTopic.BATTERY, LinkPayloads.battery(b))
    }

    /** Rota: değişince, en çok saniyede bir. Rota yoksa hedefsiz ve ölçümsüz durum. Ev rotasının hedefi zaten null. */
    fun receiverRoute(r: RouteStatus?, now: Long = wall()) {
        if (role != LinkRole.RECEIVER) return
        val v = r ?: RouteStatus(null, null, now)
        val send = synchronized(lock) {
            val same = sentRoute?.let { it.target == v.target && it.route == v.route } == true
            when {
                same -> { routeDirty = false; null }
                now - sentRouteAt < ROUTE_MIN_GAP_MS -> { pendingRoute = v; routeDirty = true; null }
                else -> { sentRoute = v; sentRouteAt = now; routeDirty = false; v }
            }
        }
        send?.let { publish(LinkTopic.ROUTE, LinkPayloads.route(it)) }
    }

    // ------------------------------------------------------------------ gelen

    /** Eşten gelen ileti (`LinkRuntime.Listener.onMessage`). Bilinmeyen tür, yanlış yön ve bozuk yük atılır. */
    fun incoming(type: String, payload: ByteArray, now: Long = wall()) {
        val topic = LinkTopic.of(type) ?: return
        if (topic.from != null && (topic.from == role || role == LinkRole.OFF)) return
        val d = LinkPayloads.Decoder(offsetMs)
        try {
            when (topic) {
                LinkTopic.ROLE -> _peerRole.value = d.role(payload)
                LinkTopic.SHIFT -> LinkPayloads.Decoder(0).shift(payload)?.let { raw -> d.shift(payload)?.let { mirrorShift(raw, it) } }
                LinkTopic.OFFERS -> d.offers(payload)?.let { synchronized(lock) { _offers.value = it; offersAt = now } }
                LinkTopic.STAGES -> d.stages(payload)?.let(::gotStages)
                LinkTopic.DAY -> d.day(payload)?.let { _day.value = it }
                LinkTopic.NAV_TARGET -> d.navTarget(payload)?.let { openTarget(it, now) }
                LinkTopic.ENERGY_REQUEST -> d.energyRequest(payload)?.let(::answerEnergy)
                LinkTopic.ENERGY_RESULT -> d.energyResult(payload)?.let(::gotEnergy)
                LinkTopic.BATTERY -> d.battery(payload)?.let { synchronized(lock) { battery = it; batteryAt = now; refreshLive(now) } }
                LinkTopic.ROUTE -> d.route(payload)?.let { _route.value = it.takeIf { r -> r.target != null || r.route != null } }
            }
        } catch (e: Exception) {
            failures.failure("link_${topic.type}", e)
        }
    }

    /**
     * Receiver'da vardiya izleme: Provider'ın her başlangıcı/bitişi bir kez işlenir; yerelde daha sonra elle verilen karar ezilmez
     * (yerel bitiş Provider'ın başlangıcından sonraysa yeniden başlatılmaz; yerel başlangıç Provider'ın bitişinden sonraysa durdurulmaz).
     */
    private fun mirrorShift(raw: ShiftState, s: ShiftState) {
        _remoteShift.value = s
        val port = local.shift ?: return
        val mine = port.state.value
        var start = false
        var stop = false
        synchronized(lock) {
            if (raw.active) {
                if (raw.startedAtMs != handledStart) {
                    handledStart = raw.startedAtMs
                    val st = s.startedAtMs
                    val myEnd = mine.endedAtMs
                    start = !mine.active && (st == null || myEnd == null || st >= myEnd)
                }
            } else if (raw.endedAtMs != handledEnd) {
                handledEnd = raw.endedAtMs
                val en = s.endedAtMs
                val myStart = mine.startedAtMs
                stop = mine.active && (en == null || myStart == null || en >= myStart)
            }
        }
        if (start) port.start(SHIFT_REASON)
        if (stop) port.stop(SHIFT_REASON)
    }

    /** Receiver'da sipariş sayacı: yeni açık sipariş → `orderAccepted`, kapanan → `orderDone`. İlk iletideki açıklar sürüyor sayılır. */
    private fun gotStages(s: TripStages) {
        _stages.value = s
        if (role != LinkRole.RECEIVER) return
        val open = s.trips.filter { it.active }.associateBy { it.tripId }
        val byId = s.trips.associateBy { it.tripId }
        val (opened, closed) = synchronized(lock) {
            val before = activeTrips
            activeTrips = open.keys
            if (before == null) emptyList<TripStages.Trip>() to emptyList()
            else open.values.filter { it.tripId !in before } to (before - open.keys).map { it to byId[it]?.endedAtMs }
        }
        for (t in opened) local.scooter.orderAccepted(t.tripId, t.events.firstOrNull()?.atMs ?: wall())
        for ((id, ended) in closed) local.scooter.orderDone(id, ended ?: wall())
    }

    /** Receiver'da gelen hedef: aynı istek bir kez; [LinkTopic.NAV_TARGET] TTL'sinden eski hedef açılmaz (Provider yerelde açtı). */
    private fun openTarget(t: NavTarget, now: Long) {
        if (role != LinkRole.RECEIVER) return
        val fresh = now - t.requestedAtMs <= LinkTopic.NAV_TARGET.ttlMs
        val first = synchronized(lock) {
            openedTargets.add(t.requestId).also { while (openedTargets.size > 64) openedTargets.remove(openedTargets.first()) }
        }
        if (!first || !fresh) return
        local.nav.open(t)
        local.navOpened(t)
    }

    /** Receiver: yerel scooter'dan enerji; aynı `requestId` aynı yanıtı alır (teslim zorunlu tekrar). Kaynak yoksa `not_measured`. */
    private fun answerEnergy(r: EnergyRequest) {
        if (role != LinkRole.RECEIVER) return
        val cached = synchronized(lock) { answered[r.requestId] ?: run { if (!answering.add(r.requestId)) return; null } }
        if (cached != null) { publish(LinkTopic.ENERGY_RESULT, LinkPayloads.energyResult(cached)); return }
        scope.launch {
            val block = runCatching { local.scooter.energy(r.fromWallMs, r.toWallMs) }.getOrNull() ?: EnergyBlock.notMeasured()
            val res = EnergyResult(r.requestId, block)
            synchronized(lock) {
                answering.remove(r.requestId)
                answered[r.requestId] = res
                while (answered.size > 64) answered.remove(answered.keys.first())
            }
            publish(LinkTopic.ENERGY_RESULT, LinkPayloads.energyResult(res))
        }
    }

    /** Provider: bekleyen istek tamamlanır; geç (ya da yeniden başlatmadan sonra) gelen yanıt pencereyle [ScooterBridge.late]'e. */
    private fun gotEnergy(r: EnergyResult) {
        if (role != LinkRole.PROVIDER) return
        val late = synchronized(lock) {
            val w = energyWaiting[r.requestId]
            when {
                w == null -> windowOf(r.requestId)?.let { LateEnergy(it.first, it.second, r.energy) }
                w.late -> { energyWaiting.remove(r.requestId); LateEnergy(w.fromWallMs, w.toWallMs, r.energy) }
                else -> { w.result.complete(r.energy); null }
            }
        }
        late?.let { _late.tryEmit(it) }
    }

    // ------------------------------------------------------------------ saat

    /** Saniyede bir: bekleyen teklif/gün/rota gönderimi, navigasyon yedeği, eskiyen uzak değerler. */
    fun tick(now: Long = wall()) {
        var offers: List<OfferSummary>? = null
        var day: DaySummary? = null
        var route: RouteStatus? = null
        val fallback = ArrayList<NavTarget>()
        synchronized(lock) {
            pendingOffers?.let { if (now - sentOffersAt >= OFFERS_MIN_GAP_MS) { markOffers(it, now); offers = it } }
            pendingDay?.let { if (now - sentDayAt >= DAY_MIN_GAP_MS) { sentDay = it; sentDayAt = now; pendingDay = null; day = it } }
            if (routeDirty && now - sentRouteAt >= ROUTE_MIN_GAP_MS) {
                pendingRoute?.let { sentRoute = it; sentRouteAt = now; route = it }
                routeDirty = false
            }
            val it = navWaiting.entries.iterator()
            while (it.hasNext()) {
                val (target, at) = it.next().value
                if (now - at >= NAV_FALLBACK_MS) { fallback += target; it.remove() }
            }
            if (role == LinkRole.RECEIVER && _offers.value.isNotEmpty() && now - offersAt > OFFERS_STALE_MS) _offers.value = emptyList()
            refreshLive(now)
        }
        offers?.let { publish(LinkTopic.OFFERS, LinkPayloads.offers(it)) }
        day?.let { publish(LinkTopic.DAY, LinkPayloads.day(it)) }
        route?.let { publish(LinkTopic.ROUTE, LinkPayloads.route(it)) }
        for (t in fallback) local.nav.open(t)
    }

    /** Provider'da uzak canlı değer: bağlıyken ve son özet [BATTERY_CURRENT_MS] içindeyse özetin kendisi; değilse bağlantısız. */
    private fun refreshLive(now: Long) {
        val b = battery ?: run { _live.value = null; return }
        val current = up && now - batteryAt <= BATTERY_CURRENT_MS && b.linkActive
        _live.value = ScooterLive(
            generatedAtMs = b.atMs, linkActive = current, ageMs = if (current) b.ageMs else null,
            scooter = ScooterLive.Values(internalPct = b.internalPct, externalPct = b.externalPct, rangeKm = b.rangeKm, tempC = b.tempC),
        )
    }

    private fun markOffers(list: List<OfferSummary>, now: Long) {
        sentOffers = list
        sentOffersAt = now
        pendingOffers = null
    }

    private fun norm(l: List<OfferSummary>) = l.map { it.copy(lastSeenAtMs = Math.floorDiv(it.lastSeenAtMs, OFFERS_REFRESH_MS)) }

    private fun publish(t: LinkTopic, payload: ByteArray): UUID? = try {
        if (t.reliable) link.publishReliable(t.type, payload, t.ttlMs) else link.publishLatest(t.type, payload, t.ttlMs)
    } catch (e: Exception) {
        failures.failure("link_publish_${t.type}", e)
        null
    }

    companion object {
        /** `ShiftPort.start/stop` nedeni (Receiver Provider'ı izlerken). */
        const val SHIFT_REASON = "link"
        const val OFFERS_MIN_GAP_MS = 200L
        const val OFFERS_REFRESH_MS = 5_000L
        /** Receiver bu süre teklif iletisi almazsa listeyi boşaltır (Provider teklif varken 5 sn'de bir yollar). */
        const val OFFERS_STALE_MS = 15_000L
        const val DAY_MIN_GAP_MS = 60_000L
        const val ROUTE_MIN_GAP_MS = 1_000L
        /** Receiver hedefi onaylamazsa Provider rotayı bu süre sonra kendisi açar. */
        const val NAV_FALLBACK_MS = 8_000L
        /**
         * Enerji yanıtı için bekleme: 0. Ekonomi satırı ekran okuma olayında, ana iş parçacığında `runBlocking` ile yazılır (`OrderFlow`
         * gözlemcisi); beklemek ANR'ye yol açar. Satır "ölçülmedi" yazılır, yanıt düzeltme satırı olur ([ScooterBridge.late]).
         */
        const val ENERGY_WAIT_MS = 0L
        /** Özet 60 sn'de bir gelir: bu süreden eski özet canlı sayılmaz. */
        const val BATTERY_CURRENT_MS = 75_000L
        private const val MAX_ENERGY_WAITING = 256
        private const val ENERGY_PREFIX = "e1"

        /** İstek kimliği pencereyi taşır (`e1:<from>:<to>:<rastgele>`): Provider yeniden başlasa da geç yanıt eşleşir. */
        fun energyId(fromWallMs: Long, toWallMs: Long): String = "$ENERGY_PREFIX:$fromWallMs:$toWallMs:${UUID.randomUUID()}"

        fun windowOf(requestId: String): Pair<Long, Long>? {
            val p = requestId.split(':')
            if (p.size != 4 || p[0] != ENERGY_PREFIX) return null
            val from = p[1].toLongOrNull() ?: return null
            val to = p[2].toLongOrNull()?.takeIf { it > from } ?: return null
            return from to to
        }
    }
}
