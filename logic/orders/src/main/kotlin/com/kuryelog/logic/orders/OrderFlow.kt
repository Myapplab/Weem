package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.CaptureSink
import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.capture.DeliveryDetails
import com.kuryelog.logic.capture.NavDistance
import com.kuryelog.logic.capture.NoteFields
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.EndedShiftMinutes
import com.kuryelog.logic.core.GpsFix
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.core.ManualSessionPolicy
import com.kuryelog.logic.core.NavKind
import com.kuryelog.logic.core.OfferSummary
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.ShiftMachine
import com.kuryelog.logic.core.WeatherPort
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.TimeZone

/**
 * `:economics`'in sipariş akışına takıldığı noktalar (ORDERS.md 4.14): Rawstats `OrderTracker`'ın `EconomicsRecorder`,
 * `WaitObservation`, `ShadowMode` çağrıları aynı yerde ve sırada. `:orders` tanımlar, `:economics` uygular (A4.1), `:app` bağlar;
 * A3.5–A3.7'de [NONE]: ekonomi dosyaları yazılmaz (gölge modda bilinen fark, ORDERS.md 14).
 */
interface OrderObserver {
    /** Açılış (`econ.restart`): bekleyen ve düşen teklifler. */
    fun restart(now: Long, kept: Collection<OfferState>, dropped: List<OfferState>) {}
    /** Açılışta veritabanından sonra (Rawstats `:259-264`): açık boşta dönemi ve açık beklemeler sansürlü satır olarak yazılır. */
    fun restored() {}
    /** Her kare, oturumlar yazıldıktan sonra (4.3 adım 13: bekleme nedenleri, `waitCore.observe`, `economics-exposure.jsonl`). */
    fun waits(o: CourierObservation, events: List<CoreEvent>, now: Long) {}
    /** Her kare, teklif anı eklentilerinden sonra, olaylar işlenmeden önce (4.3 adım 17: gölge karar, `econ.frame`). */
    fun frame(o: CourierObservation, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore, open: List<OnlineSessions.Session>, now: Long) {}
    /** Olay üreten öbür girişler (dokunuş, nakit toplama, konum, navigasyon bağı, elle teslim): `econ.events`. */
    fun events(now: Long, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore, open: List<OnlineSessions.Session>, idleUpdate: Boolean = true) {}
    /** Ret dokunuşu (bekleme nedeni `after_decline_observed`). */
    fun declineTap(pkg: String, at: Long) {}
    /** Dokunuşun olayları, oturumlara işlenirken (Rawstats `:488`: ret → bekleme nedeni `after_decline_observed`). */
    fun clicked(pkg: String, events: List<CoreEvent>) {}
    fun distance(now: Long, m: Double) {}
    /** Vardiya sonu, çekirdek durmadan önce (`waitCore.stop`, açık beklemeler). */
    fun stopping(now: Long) {}
    /** Vardiya sonu (`econ.stop`), [events] çekirdeğin kapanış olayları. */
    fun stop(now: Long, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore) {}
    /** Kullanıcı iptal bildirdi (bekleme kırılır). */
    fun cancellation(pkg: String) {}

    object NONE : OrderObserver
}

/** ORDERS.md 13.3: teklif özetinin ekonomi alanları (bölgeler, R4 puanı, net); `:economics` uygular, `:app` bağlar. */
interface OfferAnnotator {
    fun annotate(offer: OfferState, summary: OfferSummary): OfferSummary = summary

    object NONE : OfferAnnotator
}

/**
 * Rawstats `OrderTracker` (OrderTracker.kt @ 6388c04) saf kısmı: karar ve sıra (ORDERS.md 4.1–4.13). Android'e ait olanlar [Env]'de
 * (`courier.db`, tercihler, gün dosyaları, saat, pil, son konum, adres çözme, bildirim/pencere istekleri); Android `:orders` (A3.7)
 * tek örneği tutar, `Capture.sink` ve `ShiftListeners`'a bağlar. Her giriş `@Synchronized` (Rawstats: tek kilit).
 *
 * WEEM farkları (ORDERS.md 16.1): YolGo akışları (`OfferFeed`, `FeedChanges`, `YolGoNavReader`, hava önbelleği) yok, yerine
 * `OrderEvents` için [offers]/[stages] ve `NavPort.route` ([navigation]), `WeatherPort` ([Env.weather]); ekonomi kaydedicileri
 * [OrderObserver]; resmî toplamlar, CASH PENDING, cep nakdi, balon ve kart kararları (A3.6) Rawstats'taki yerlerinde.
 */
class OrderFlow(private val env: Env) : CaptureSink, ShiftMachine.Hooks {
    /** Android'in ve öbür modüllerin verdikleri. */
    interface Env {
        val log: LogStore
        val prefs: KuryelogPrefs.Values
        val db: CourierRecords
        val clock: Clock
        val scooter: ScooterBridge get() = ScooterBridge.NONE
        val weather: WeatherPort get() = WeatherPort.NONE
        val observer: OrderObserver get() = OrderObserver.NONE
        /** Teklif özetinin bölge, puan ve net alanları (`:economics`, ORDERS.md 13.3); `NONE` veri uydurmaz. */
        val annotator: OfferAnnotator get() = OfferAnnotator.NONE
        /** `Store.importing` (ZIP içe aktarma sürüyor). */
        fun importing(): Boolean = false
        fun phoneBattery(): Int? = null
        /** A3.11: gölge mod açık mı (`.dev` + [OrderSettings.SHADOW]); Android yapı türünü de bilir, sürüm yapısı hep false. */
        fun shadowMode(): Boolean = OrderSettings.shadowMode(prefs)
        /** `I18n.tl`. */
        fun tl(tr: String, az: String): String = tr
        /** Android `Geocoder.getFromLocationName(adres, 3)` ilk koordinatlı sonuç, arka planda; [done] kilit altında çağrılmaz. */
        fun geocode(address: String, done: (DoubleArray?) -> Unit) {}
        /** K32: nakit bahşiş sorusu (`CashTipPrompt.ask`, Android). */
        fun askCashTip(trip: TripRecord) {}
        /** M15: yeni vardiyanın başında "Şu an cebinde ne kadar nakit var?" (`PocketCashPrompt.ask`, Android); cevap [recordPocketCash]'e gelir. */
        fun askPocketCash(shiftStartMs: Long) {}
        /** E14/E16: bildirim 12 (`DeliveryReminder.notify`, Android); [update] true = sessiz güncelleme. Pencereler [reminder]'ı okur. */
        fun notifyReminder(reminder: Reminder, update: Boolean = false) {}
    }

    val core = DeliveryCore()
    private val sessions = OnlineSessions()
    private var sessionsSavedAt = 0L
    /** The navigation app's own summary line ("8 min · 2,1 km"), for the live line on the cards. */
    @Volatile private var navSummary: String? = null
    @Volatile private var navSummaryAt = 0L
    private val restaurantCoords = HashMap<String, DoubleArray>()
    /** D9: the shift's GPS fixes for Wolt restaurant arrivals (memory only). */
    private val venues = VenueArrival()
    /** D9 + E8: the navigation target of the pickup first, the address geocode after it. */
    private fun venueOf(t: TripRecord, i: Int): VenueArrival.Venue? = NavBinding.preferred(navVenues["${t.id}:$i"],
        restaurantCoords["${t.id}:$i"]?.let { VenueArrival.Venue(it[0], it[1], VenueArrival.SOURCE_GEOCODE) })
    /** E8: the restaurant coordinate of a pickup from the navigation target ([NavBinding.pickupOf]); memory only. */
    private val navVenues = HashMap<String, VenueArrival.Venue>()
    /** M20: the navigation distance to pickup [i] of [t]: the route's remaining metres when its target is that pickup, Google Maps'
     *  summary while the order is at its restaurant stage; the newer fresh one ([ArrivalDistance.pick]). */
    private fun navOf(t: TripRecord, i: Int): ArrivalDistance.Source? {
        val y = route?.takeIf { s -> s.target?.requestedAtMs == navBound && navVenues.containsKey("${t.id}:$i") }?.route
            ?.let { ArrivalDistance.Source(it.remainingM, it.measuredAtMs, ArrivalDistance.YOLGO) }
        val m = NavDistance.metres(navSummary)?.takeIf { t.phase == TripPhase.TO_RESTAURANT || t.phase == TripPhase.WAITING }
            ?.let { ArrivalDistance.Source(it, navSummaryAt, ArrivalDistance.MAPS) }
        return ArrivalDistance.pick(y, m, null, env.clock.wallMs())
    }
    /** M19: the navigation target when it is a customer requested on this leg, else the customer's geocoded address. */
    private fun customerCoord(t: TripRecord, c: CustomerRecord): VenueArrival.Venue? {
        val target = route?.target?.takeIf { it.kind == NavKind.CUSTOMER && it.requestedAtMs >= (legStart(t) ?: t.acceptedAt) }
        return target?.let { VenueArrival.Venue(it.lat, it.lon, VenueArrival.SOURCE_YOLGO) }
            ?: c.lat?.let { la -> c.lon?.let { lo -> VenueArrival.Venue(la, lo, c.coordinateSource ?: VenueArrival.SOURCE_GEOCODE) } }
    }
    /** M20: the navigation distance to the current customer of [t] (customer target of this leg, Google Maps on the customer leg). */
    private fun customerNavOf(t: TripRecord): ArrivalDistance.Source? {
        val y = route?.takeIf { s -> s.target?.let { it.kind == NavKind.CUSTOMER && it.requestedAtMs >= (legStart(t) ?: t.acceptedAt) } == true }?.route
            ?.let { ArrivalDistance.Source(it.remainingM, it.measuredAtMs, ArrivalDistance.YOLGO) }
        val m = NavDistance.metres(navSummary)?.takeIf { t.phase == TripPhase.TO_CUSTOMER }?.let { ArrivalDistance.Source(it, navSummaryAt, ArrivalDistance.MAPS) }
        return ArrivalDistance.pick(y, m, null, env.clock.wallMs())
    }
    /** E8: the last navigation state ([navigation], WEEM `NavPort.route`) and the request already tied to a pickup. */
    private var route: RouteStatus? = null
    private var navBound: Long? = null
    /** E8: which order each courier app showed when (the screen of a navigation request); memory only. */
    private val trail = ScreenTrail()
    /** E8: when each order ended (for `stages`). */
    private val tripEnds = HashMap<String, Long>()
    /** D6: the courier app's last notification per package (a new offer is announced before its card). */
    private val notifyAt = HashMap<String, Long>()
    /** K27 boost changes and Bolt's 30-minute slots ([BoostLog.FILE]). */
    private val boosts = BoostLog()
    /** K21: offers last seen before this come from before a process restart (`econ.restartedAtMs`). */
    var restartedAtMs: Long = Long.MIN_VALUE
        private set

    // Teşhis (Rawstats `Store.offers`, `lastOfferAt`, `seen`, `seenOffers`; `:capture` tanı kartı okur).
    @Volatile var offerCount = 0
        private set
    @Volatile var lastOfferAt = 0L
        private set
    /** WEEM (ORDERS.md 4.11): paket başına son ekran karesi anı (Rawstats `Store.seen`). */
    val seen = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val seenOffers = java.util.concurrent.ConcurrentHashMap<String, Int>()

    val pending: OfferState? @Synchronized get() = core.pending.values.firstOrNull()
    private var initialized = false
    private var lastPersist = 0L
    private val geocodes = HashSet<String>()
    private val snapshotHashes = HashMap<String, Int>()
    var reminder: Reminder? = null
        private set
    /** [kind] "customer" or "restaurant"; a restaurant card puts [headline] (ready / ready in N min) first. */
    data class Reminder(val trip: String, val customer: String, val title: String, val details: DeliveryDetails, val estimate: String, val revision: Long,
        val kind: String = "customer", val headline: String? = null, val body: String? = null,
        /** E14 bubble: the restaurant's name and its E15 ready state. */
        val place: String? = null, val ready: Readiness.State? = null,
        /** F7.3 customer bubble: the platform's drop-off deadline (K29) and how many orders go to this customer (R1). */
        val deadlineAt: Long? = null, val orders: Int = 1)
    data class Stats(val delivered: Int = 0, val net: Double = 0.0, val perHour: Double = 0.0, val accepted: Int = 0, val rejected: Int = 0, val uncertain: Int = 0)

    private fun canRecordScreen() = ManualSessionPolicy.canCapture(env.prefs.getBoolean(KuryelogPrefs.SHIFT, false),
        env.prefs.getBoolean(KuryelogPrefs.MANUAL_SESSION, false), env.importing())
    private fun shiftOn() = KuryelogPrefs.shiftOn(env.prefs)
    private fun now() = env.clock.wallMs()
    private fun day(t: Long = now()) = env.log.day(t)
    private fun tl(tr: String, az: String) = env.tl(tr, az)
    private fun failure(source: String, e: Throwable) = env.log.failure(source, e)

    @Synchronized fun init() {
        if (initialized) return
        KuryelogPrefs.ensureManualMode(env.prefs)
        try {
            // A delivery accepted before midnight must survive a restart after midnight;
            // otherwise it can neither finish nor be closed by the manual shift stop.
            val now = now()
            RecordJson.restorable(env.db.trips(day(now)), env.db.trips(day(now - 86_400_000L))).forEach { core.trips[it.id] = it }
            val dropped = ArrayList<OfferState>()
            env.db.session()?.optJSONArray("pending")?.objects()?.forEach { j ->
                val o = RecordJson.offer(j); if (day(o.firstAt) == day() && shiftOn()) core.pending[o.pkg] = o else dropped.add(o)
            }
            // Offers pending when the process died: not exported until seen again (K21), outcome not observed (SPEC 17.2 recording_stopped).
            env.observer.restart(now, core.pending.values, dropped)
            (core.pending.values + dropped).maxOfOrNull { it.lastSeenAt }?.let { restartedAtMs = maxOf(restartedAtMs, it + 1) }
            if (dropped.isNotEmpty()) env.db.session(JSONObject().put("pending", JSONArray(core.pending.values.map(RecordJson::offer))))
            if (shiftOn()) core.start()
        } catch (e: Exception) { failure("database", e) }
        initialized = true
        env.observer.restored()
        try {
            sessions.restore(env.prefs.getString(OPEN_SESSIONS, null))
            // Process died after the shift ended: close at the last online screen seen.
            if (!shiftOn()) saveSessions(sessions.stop("restart_after_shift"))
        } catch (e: Exception) { failure("online_sessions", e) }
        if (!env.prefs.getBoolean(MIGRATION_07, false)) {
            rebuildRecords()
            env.prefs.put(mapOf(MIGRATION_07 to true))
        }
    }

    @Synchronized fun start() {
        init(); core.start(); reminder = null; bubbleDismissed.clear(); venues.clear(); trail.clear(); navVenues.clear(); navBound = null
        persist(true)
    }
    private fun saveSessions(closed: List<OnlineSessions.Closed>, force: Boolean = false) {
        closed.forEach { env.log.append(ONLINE_SESSIONS_FILE, OnlineSessions.json(it).put("t", env.log.ts(it.endAt)).toString()) }
        val now = now()
        if (!force && closed.isEmpty() && now - sessionsSavedAt < 10_000) return
        sessionsSavedAt = now
        env.prefs.put(mapOf(OPEN_SESSIONS to sessions.encode()))
    }
    @Synchronized fun stop() {
        init(); saveSessions(sessions.stop(), force = true); env.observer.stopping(now())
        val now = now(); val before = core.pending.values.associateBy { it.id }; val events = core.stop(now)
        env.observer.stop(now, events, before, core)
        process(events); reminder = null; persist(true)
    }

    /** D21: an offer card was on screen until this moment (wall clock); 0 = none. Read by the badge every 500 ms. */
    @Volatile var offerVisibleUntil = 0L
    fun offerVisible(now: Long) = now < offerVisibleUntil
    /** F4.5b: an order is open (the home rule does not end the shift then). */
    @Synchronized fun hasOpenOrder(): Boolean = core.trips.values.any { it.active }

    // ------------------------------------------------------------------ ShiftMachine.Hooks (ORDERS.md 4.2)
    // `onNewShift`/`onEnd`'in cep nakdi ve CASH PENDING işleri [cashPendingHooks]'ta: `:app` önce onu, `core.dailyPack`'ten sonra bunu
    // kaydeder (ORDERS.md 4.2: CASH PENDING sonu günlük paketten önce, `stop` ondan sonra).
    override fun onRunning() = start()
    override fun onFix(fix: GpsFix, speedMps: Double?, nowMs: Long) { location(fix, speedMps, nowMs); tick(nowMs) }
    override fun onDistance(m: Double) = distance(m)
    override fun busy(): Boolean = hasOpenOrder()
    override fun onEnd(startMs: Long, nowMs: Long, reason: String) = stop()
    /** Rawstats `GpsService`: yeni vardiyada cep nakdi sorusu (M15, bildirim 4901) ve CASH PENDING başı (M17); bitişte CASH PENDING sonu. */
    val cashPendingHooks: ShiftMachine.Hooks = object : ShiftMachine.Hooks {
        override fun onNewShift(nowMs: Long, reason: String) {
            try { if (!shadowMode()) env.askPocketCash(nowMs) } catch (e: Exception) { failure("shift_start", e) }
            runCatching { cashPendingShift(CashPending.SHIFT_START, nowMs, nowMs) }
        }
        override fun onEnd(startMs: Long, nowMs: Long, reason: String) { runCatching { cashPendingShift(CashPending.SHIFT_END, startMs, nowMs) } }
    }

    // ------------------------------------------------------------------ CaptureSink (ORDERS.md 4.3; CAPTURE.md 7.1)
    override fun onScreen(pkg: String, texts: List<String>, actions: List<String>, at: Long, source: String,
                          spinner: Boolean, checked: Boolean?, parsed: CourierObservation) =
        onScreen(pkg, texts, actions, at, source, parsed.complete, spinner, parsed, checked)
    override fun onClick(pkg: String, label: String, at: Long) = click(pkg, label, at)
    override fun onNotification(pkg: String, text: String, at: Long) = notification(pkg, text, at)
    override fun onNavigation(lines: List<String>, at: Long) = navigationText(lines, at)
    override fun onDialer(texts: List<String>, at: Long) = dialerPhone(texts, at)

    /** [parsed]: the caller's own [CourierParser.parse] of this very frame (`:capture` parses it first); the parser is pure, so it is reused. */
    @Synchronized fun onScreen(pkg: String, texts: List<String>, actions: List<String>, now: Long, source: String = "accessibility", complete: Boolean = true,
                               spinner: Boolean = false, parsed: CourierObservation? = null, checked: Boolean? = null) {
        if (!canRecordScreen()) return
        init()
        seen[pkg] = now
        // F8.3: the platforms' own day totals (Wolt Daily details, cash collected, Bolt day headers).
        platformTotals(PlatformTotals.woltDaily(pkg, texts) + PlatformTotals.boltDays(pkg, texts) + cashTotals(pkg, texts), now)
        // K36: the platform's own earnings list is verified history, never an offer or order screen.
        if (platformHistory(pkg, texts, now, source)) return
        // D3(d): Wolt Finances "Cash collection" rows give the handover time of cash orders (the frame is parsed as usual too).
        cashCollections(pkg, texts, now)
        // M17: Finances "CASH PENDING" and "Cash offload" rows (numbers only).
        cashPending(pkg, texts, now)
        // K33: the slider spinner comes from the accessibility node classes, not from the texts.
        // K31: Wolt's delivery steps screen (cash step and amount) is read here, so the parser's harness contract is unchanged.
        val o = (parsed?.takeIf { it.pkg == pkg && it.at == now && it.source == source && it.complete == complete } ?: CourierParser.parse(pkg, texts, actions, now, source, complete))
            .let { if (spinner) it.copy(deliverSpinner = true) else it }
            .let { CashPayment.attach(it, CashPayment.parse(pkg, texts, checked)) }
        PlatformTotals.fromStats(o)?.let { platformTotals(listOf(it), now) }
        // D21: while an offer card is on screen the badge moves off Wolt's Decline (top right) and Accept/card (bottom).
        if (o.stage == ScreenStage.OFFER) offerVisibleUntil = now + OFFER_VISIBLE_MS
        else if (o.complete && o.stage != ScreenStage.UNKNOWN) offerVisibleUntil = 0L
        val opened = sessions.openSessions().size
        val closedSession = sessions.observe(o)
        val before = core.pending.values.associateBy { it.id }
        val events = core.observe(o).let { it + pickupArrivals(it, now) }
        // E8: the order on screen at each stage change (which pickup a navigation request was made for).
        if (o.complete && o.stage != ScreenStage.UNKNOWN)
            trail.record(now, pkg, o.stage, if (o.stage == ScreenStage.PICKUP || o.stage == ScreenStage.DROPOFF) core.shown(pkg)?.id else null)
        // D6: this frame brought the offer's fee: how long from its screenshot to the record (OCR runs after the capture).
        (core.pending[pkg] ?: core.active(pkg)?.offer)?.takeIf { it.feeAt == now && it.feeOcrMs == null }
            ?.let { it.feeOcrMs = (now() - now).coerceAtLeast(0) }
        events.forEach { e -> linkSession(e); val t = e.tripId?.let { core.trips[it] }; sessions.event(e, t?.customers?.size ?: 1, t?.deliveredInferredAt != null) }
        saveSessions(listOfNotNull(closedSession), force = events.isNotEmpty() || opened != sessions.openSessions().size)
        env.observer.waits(o, events, now)
        // K27: boosts are logged from every home/offer frame, before the offer takes the latest one.
        boosts.observe(o, day(now), env.log.locJson()).forEach { env.log.append(BoostLog.FILE, it.toString()) }
        core.pending[pkg]?.let { offer -> if (events.any { it.type == "offer" }) {
            offer.phoneBattery = env.phoneBattery()
            offer.notifiedAt = notifyAt[pkg]?.takeIf { offer.firstAt - it in 0..NOTIFY_TO_CARD_MS }
            // Scooter battery at offer time; null while no scooter source is connected.
            env.scooter.batteryPct()?.let { offer.scooterBatteryPct = it; offer.scooterObservedAt = now; offer.scooterSource = SCOOTER_SOURCE }
            offer.weather = env.weather.current()?.let { OfferWeather.from(it, now) }
            boosts.atOffer(o, now).let { b -> offer.boostValue = b.value; offer.boostUnit = b.unit; offer.boostSource = b.source; offer.boostSeenAt = b.seenAt }
            env.log.lastLocation?.let { l -> offer.lat = l.lat; offer.lon = l.lon; offer.gpsAccuracyM = l.accM; offer.gpsAgeMs = now - l.atMs }
        } }
        env.observer.frame(o, events, before, core, sessions.openSessions(), now)
        process(events)
        // Online state and promotion validity are recorded separately from offers.
        if (o.stage == ScreenStage.IDLE || o.stage == ScreenStage.OFFLINE || o.multipliers.isNotEmpty()) {
            val j = JSONObject().put("pkg", pkg).put("stage", o.stage.name).put("demand", o.demand).put("bonus_pct", o.bonusPct)
                .put("bonus_from", o.bonusFrom).put("multipliers", JSONArray(o.multipliers)).put("offline_until", o.offlineUntil)
            val hash = j.toString().hashCode()
            if (snapshotHashes["platform:$pkg"] != hash) { snapshotHashes["platform:$pkg"] = hash; j.put("t", env.log.ts(now)); env.log.append(PLATFORMS_FILE, j.toString()) }
        }
        // The order this frame showed (K34: with two open orders of one platform, not always the newest one).
        core.shown(pkg)?.let { t ->
            val c = t.customers[t.currentCustomer]
            if (c != null && c.lat == null && c.details.address != null) resolve(t.id, c.id, c.details.address!!)
            if (reminder?.trip == t.id && reminder?.customer == c?.id) reminder = reminder!!.copy(details = c!!.details.copy(), revision = c.detailsAt, deadlineAt = c.deadlineAt)
        }
        persist(events.isNotEmpty()); tick(now)
    }

    /** K36 dedupe and matched trips per day (loaded from that day's log once, so a revisit or restart writes nothing twice). */
    private val historyKeys = HashMap<String, MutableSet<String>>()
    private val historyTaken = HashMap<String, MutableSet<String>>()
    /** D15: the rows written per day, so an OCR variant of a row (name or clock misread) is not written twice. */
    private val historyWritten = HashMap<String, MutableList<JSONObject>>()
    private var historyHash = 0
    private fun dayFile(day: String, name: String) = File(env.log.dayDir(day), name)
    private fun historyDay(day: String) {
        if (historyKeys.containsKey(day)) return
        val f = dayFile(day, PlatformHistory.FILE)
        val (k, t, w) = if (f.exists()) f.useLines { PlatformHistory.loaded(it) } else Triple(HashSet<String>(), HashSet<String>(), ArrayList<JSONObject>())
        historyKeys[day] = k; historyTaken[day] = t; historyWritten[day] = w
    }

    /** True when the frame is a Wolt Finances / Bolt All deliveries list (its new rows are written to [PlatformHistory.FILE]). */
    private fun platformHistory(pkg: String, texts: List<String>, now: Long, source: String): Boolean {
        val tz = TimeZone.getTimeZone(env.clock.zone())
        val rows = PlatformHistory.parse(pkg, texts, now, tz) ?: return false
        val hash = (pkg + texts.joinToString("\n")).hashCode()
        if (hash == historyHash) return true
        historyHash = hash
        val stored by lazy { stored() }
        val out = PlatformHistory.records(rows, now, source, pkg, tz,
            keys = { d -> historyDay(d); historyKeys.getValue(d) }, taken = { d -> historyDay(d); historyTaken.getValue(d) },
            tripsOn = { d -> (stored + core.trips.values).associateBy { it.id }.values.filter { day(OrderHistory.listTime(it, platform(it.pkg))) == d || day(it.acceptedAt) == d } },
            written = { d -> historyDay(d); historyWritten.getValue(d) })
        for ((day, j) in out) env.log.append(PlatformHistory.FILE, j.toString(), day)
        // D15: "Extra waiting time" / "Tip" below a row go to that row's order (also when the row was written in an earlier frame).
        var liveChanged = false
        for (r in rows) if (r.extraWait != null || r.tip != null) {
            val day = r.day ?: PlatformHistory.dayOf(now, tz)
            historyDay(day)
            val id = PlatformHistory.owner(r, day, historyWritten.getValue(day)) ?: continue
            val t = core.trips[id] ?: stored.firstOrNull { it.id == id } ?: continue
            var changed = false
            r.extraWait?.let { if (t.extraWaitAzn != it) { t.extraWaitAzn = it; changed = true } }
            r.tip?.let { if (t.platformTipAzn != it) { t.platformTipAzn = it; changed = true } }
            if (changed && core.trips.containsKey(id)) liveChanged = true
            else if (changed) runCatching { env.db.saveTrip(RecordJson.trip(t, withPhone = true)) }
        }
        // D3(c): a matched row verifies the order's delivery (an unverified one loses its "doğrulanmadı" label).
        val by = if (pkg in CourierParser.WOLT) "wolt_earnings_list" else "bolt_deliveries_list"
        for ((_, j) in out) j.optJSONObject("match")?.optString("trip_id", "")?.takeIf { it.isNotEmpty() }?.let { id ->
            if (core.listVerified(id, by, now)) return@let
            if (core.trips.containsKey(id)) return@let
            // An older day's order (not in memory): its courier.db row is updated, the logs are never rewritten.
            stored.firstOrNull { it.id == id && it.verifiedBy == null }?.let { t ->
                t.verifiedBy = by; t.verifiedAt = now
                runCatching { env.db.saveTrip(RecordJson.trip(t, withPhone = true)) }
            }
        }
        if (out.isNotEmpty() || liveChanged) persist(true)
        return true
    }

    /** F8.5: the newest platform totals of [day] ([PlatformTotals.FILE]; empty when none were read). */
    fun platformTotals(day: String): List<PlatformTotals.Total> = try {
        val f = dayFile(day, PlatformTotals.FILE)
        if (!f.exists()) emptyList() else f.useLines { PlatformTotals.latest(it.asIterable()) }
    } catch (e: Exception) { emptyList() }

    /** K36 rows of [day] for the order cards (empty when none were read). */
    fun platformHistoryRows(day: String): List<JSONObject> = jsonRows(day, PlatformHistory.FILE)
    private fun jsonRows(day: String, name: String): List<JSONObject> = try {
        val f = dayFile(day, name)
        if (!f.exists()) emptyList() else f.useLines { s -> s.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList() }
    } catch (e: Exception) { emptyList() }

    /** F8.3: the last total written per [PlatformTotals.key] (an unchanged one is not written again), and the cash rows seen
     *  per day (a day's total is the sum of every row read in this process, so a scroll adds the rest). */
    private val totalsWritten = HashMap<String, PlatformTotals.Total>()
    private val cashSeen = HashMap<String, LinkedHashSet<PlatformHistory.CashRow>>()
    private fun cashTotals(pkg: String, texts: List<String>): List<PlatformTotals.Total> =
        PlatformHistory.cashRows(pkg, texts).onEach { cashSeen.getOrPut(it.day) { LinkedHashSet() }.add(it) }.map { it.day }.distinct()
            .flatMap { PlatformTotals.woltCash(cashSeen.getValue(it).toList()) }
    private fun platformTotals(totals: List<PlatformTotals.Total>, now: Long) {
        for (t in totals) {
            val k = PlatformTotals.key(t)
            if (totalsWritten[k] == t) continue
            totalsWritten[k] = t
            env.log.append(PlatformTotals.FILE, PlatformTotals.record(t, now).toString(), PlatformTotals.dayOf(t.period) ?: day(now))
        }
    }
    /** Texts of the last Finances frame whose cash rows were applied (an unchanged screen is not matched again). */
    private var cashHash = 0
    /** D3(d): each "Cash collection" row of a Wolt Finances frame corrects or completes the delivery of its cash order. */
    private fun cashCollections(pkg: String, texts: List<String>, now: Long) {
        val rows = PlatformHistory.cashRows(pkg, texts).takeIf { it.isNotEmpty() } ?: return
        val hash = texts.joinToString("\n").hashCode()
        if (hash == cashHash) return
        cashHash = hash
        val tz = TimeZone.getTimeZone(env.clock.zone())
        val events = ArrayList<CoreEvent>()
        for (r in rows) {
            val at = PlatformHistory.toMs(r.day, r.clock, tz)
            if (at > now + 60_000) continue
            val t = PlatformHistory.cashMatch(r, at, core.trips.values) ?: continue
            events += core.cashCollected(t.id, at, r.amount, now)
        }
        if (events.isEmpty()) return
        env.observer.events(now, events, emptyMap(), core, sessions.openSessions())
        process(events); persist(true)
    }

    @Synchronized fun click(pkg: String, text: String, at: Long) {
        if (!canRecordScreen()) return
        init(); if (CourierParser.isDecline(text)) env.observer.declineTap(pkg, at)
        val before = core.pending.values.associateBy { it.id }
        val events = core.click(pkg, text, at)
        events.forEach { e -> sessions.event(e) }
        env.observer.clicked(pkg, events)
        env.observer.events(at, events, before, core, sessions.openSessions())
        process(events); if (events.isNotEmpty()) saveSessions(emptyList(), force = true); persist(true)
    }
    /** Each offer and order keeps the online session it belongs to. */
    private fun linkSession(e: CoreEvent) {
        val id = sessions.currentId(e.pkg)
        when (e.type) {
            "offer" -> { core.pending[e.pkg]?.takeIf { it.id == e.offerId && it.sessionId == null }?.let { it.sessionId = id } }
            "accepted" -> { e.tripId?.let { core.trips[it] }?.takeIf { it.sessionId == null }?.let { t -> t.sessionId = t.offer?.sessionId ?: id } }
        }
    }
    @Synchronized fun notification(pkg: String, text: String, at: Long) {
        if (!canRecordScreen()) return
        init()
        // Notification hints are not proof of acceptance, price or rejection.
        env.log.event("notification_hint", JSONObject().put("pkg", pkg).put("text", NoteFields.redact(text)).put("observed_at", at))
        lastOfferAt = at
        if (at > (notifyAt[pkg] ?: Long.MIN_VALUE)) notifyAt[pkg] = at
        // "Order is ready" / "ready in N min" only feeds the restaurant card.
        core.readinessNotice(pkg, text, at)
    }
    private fun process(events: List<CoreEvent>) {
        for (e in events) {
            val trip = e.tripId?.let { core.trips[it] }
            if (e.type == "accepted" && trip != null) {
                trip.commissionPct = commission(trip.pkg)
                trip.priceIsNet = false
                env.scooter.orderAccepted(trip.id, e.at)
            }
            if (trip != null && e.type in ORDER_ENDS) { core.closeEnergy(trip); env.scooter.orderDone(trip.id, e.at); if (!trip.active) tripEnds[trip.id] = e.at }
            val j = JSONObject().put("t", env.log.ts(e.at)).put("type", e.type).put("pkg", e.pkg).put("offer_id", e.offerId).put("id", e.tripId).put("reason", e.reason)
                .put("session_id", trip?.sessionId ?: core.pending[e.pkg]?.takeIf { it.id == e.offerId }?.sessionId ?: sessions.currentId(e.pkg))
            trip?.let { j.put("record", RecordJson.trip(it)); j.put("gross", it.gross); j.put("net", it.net); j.put("total_min", it.actualMinutes); j.put("wait_min", it.waitMinutes); j.put("km", it.distanceM / 1000); j.put("restaurant", it.pickups.firstOrNull()?.restaurant) }
            core.pending[e.pkg]?.takeIf { it.id == e.offerId }?.let { j.put("offer", RecordJson.offer(it)); env.db.saveOffer(RecordJson.offer(it)) }
            if (e.offerId != null && e.type != "offer") {
                // Looked up by id: an offer seen before midnight still gets its result.
                val saved = env.db.offer(e.offerId)
                if (saved != null) env.db.saveOffer(saved, e.type)
            }
            if (e.type == "offer") { offerCount++; lastOfferAt = e.at; seenOffers[e.pkg] = (seenOffers[e.pkg] ?: 0) + 1 }
            env.log.locJson()?.let { j.put("loc", it) }; j.put("phone_battery_pct", env.phoneBattery())
            env.log.append(ORDERS_FILE, j.toString())
            // K32: a delivered cash order (real or K34-inferred) asks how much cash was received, once per trip (no answer yet): an
            // unverified delivery's later real signal or Finances cash row (D3) does not ask again.
            if ((e.type == "delivered" || e.type == "delivered_inferred") && trip != null && !shadowMode() && CashTips.shouldAsk(trip) && cashTipFor(trip) == null &&
                cashAsked.add(trip.id)) runCatching { env.askCashTip(trip) }
        }
    }
    /** K32: trips whose cash question was posted by this process (memory only; after a restart it may be asked once more). */
    private val cashAsked = HashSet<String>()
    /** What courier.db last got for each trip / pending offer and the session row: an unchanged row is not written again. */
    private val savedTrips = HashMap<String, String>()
    private val savedOffers = HashMap<String, String>()
    private var savedSession: String? = null
    private fun forgetSaved() { savedTrips.clear(); savedOffers.clear(); savedSession = null }
    /**
     * Runs at least every 1.5 s during a shift (tick, screens). Only changed rows, in one transaction.
     */
    @Synchronized private fun persist(force: Boolean = false) {
        val now = now(); if (!force && now - lastPersist < 1500) return
        lastPersist = now
        try { env.db.batch {
            for (t in core.trips.values) {
                // The number from the dialer stays on this phone only (courier.db, allowBackup=false, no device transfer).
                val full = RecordJson.trip(t, withPhone = true); val fullText = full.toString()
                if (savedTrips[t.id] != fullText) { env.db.saveTrip(full); savedTrips[t.id] = fullText }
                val j = RecordJson.trip(t)
                // Raw data belongs in screens.jsonl; snapshots retain structured state and its provenance.
                val portable = JSONObject(j.toString()); portable.remove("latest")
                val hash = portable.toString().hashCode()
                if (snapshotHashes[t.id] != hash && (force || now % 10000 < 2000)) {
                    snapshotHashes[t.id] = hash
                    env.log.append(ORDERS_FILE, JSONObject().put("t", env.log.ts()).put("type", "record_snapshot").put("record", j).toString())
                }
            }
            for (o in core.pending.values) { val j = RecordJson.offer(o); val text = j.toString(); if (savedOffers[o.id] != text) { env.db.saveOffer(j); savedOffers[o.id] = text } }
            val session = JSONObject().put("pending", JSONArray(core.pending.values.map(RecordJson::offer)))
            val sessionText = session.toString()
            if (sessionText != savedSession) { env.db.session(session); savedSession = sessionText }
        } } catch (e: Exception) { forgetSaved(); failure("database", e) }
    }

    /** A3.11: gölge modda pencereler ve bildirim 12 çizilmez; [reminder] durumu ve kayıtları (`reminderAt`) Rawstats'taki gibi sürer. */
    fun shadowMode(): Boolean = env.shadowMode()
    private fun notifyReminder(r: Reminder, update: Boolean = false) { if (!shadowMode()) env.notifyReminder(r, update) }

    /** WEEM (ORDERS.md 4.6): YolGo akışlarının duyurusu yok; [offers]/[stages] okuyanlar her an tazesini alır. */
    @Synchronized fun tick(now: Long) {
        if (!shiftOn() || !core.running) { reminder = null; return }
        bindNavTarget(now)
        val l = env.log.lastLocation
        // WEEM: Loc'ta `hasAccuracy` yok; Android bilinmeyen doğruluğu 0 verir, gerçek fix hep > 0 (ORDERS.md 16.1).
        val acc = l?.accM?.takeIf { it > 0.0 }
        // E16: the customer card opens on whichever comes first: 300 m to the address in a straight line, 300 m left on the
        // route or its last 2 turns (WEEM `NavPort.route`; Rawstats: YolGo's E8 payload). Replaced the 1,000 m / 700 m trigger of the Google Maps/Waze notification.
        for (t in core.trips.values.filter { it.active && (it.phase == TripPhase.TO_CUSTOMER || it.phase == TripPhase.AT_CUSTOMER) }) {
            val c = t.customers[t.currentCustomer] ?: continue
            if (c.reminderAt != null || c.deliveredAt != null) continue
            val leg = CustomerCard.routeFor(route, legStart(t), now)
            // The navigation target is the place the courier app's "Navigate" sent (sharper than the address geocode).
            val target = leg?.let { route?.target }
            val straight = l?.let { CustomerCard.straight(it.atMs, it.lat, it.lon, acc, target?.lat ?: c.lat, target?.lon ?: c.lon, now) }
            val why = CustomerCard.trigger(straight, leg) ?: continue
            c.reminderAt = now
            reminder = Reminder(t.id, c.id, "${platform(t.pkg)} · ${c.details.name ?: c.id}", c.details.copy(),
                CustomerCard.reason(why, straight, leg) { tr, az -> tl(tr, az) }, c.detailsAt,
                deadlineAt = c.deadlineAt, orders = 1 + t.sameDropoff.count { core.trips[it]?.active == true })
            notifyReminder(reminder!!); persist(true)
        }
        // E14: restaurant bubble — 50 m straight from the restaurant (D9's coordinate: the navigation target, else the geocode) or its
        // arrival seen; until Collect or 150 m away. Replaced the 1,000 m route / 700 m straight / ≤3 min trigger. The first
        // opening per pickup posts the restaurant notification ([PickupRecord.reminderAt]); a bubble the courier closed stays closed.
        for (t in core.trips.values.filter { it.active && (it.phase == TripPhase.TO_RESTAURANT || it.phase == TripPhase.WAITING) }) {
            val i = t.pickups.indexOfLast { it.pickedAt == null }; if (i < 0) continue
            val p = t.pickups[i]
            val key = "${t.id}:$i"
            if (p.address != null && restaurantCoords[key] == null) resolveRestaurant(key, p.address!!)
            val open = reminder?.let { it.kind == "restaurant" && it.trip == t.id && it.customer == "pickup-$i" } == true
            if (open || key in bubbleDismissed) continue
            val d = venueDistance(t, i, now)
            if (!RestaurantBubble.visible(false, d, p.arrivedAt != null, false)) continue
            val first = p.reminderAt == null
            if (first) p.reminderAt = now
            reminder = restaurantReminder(t, i, live(now) ?: placeText(d, p.arrivedAt != null), now)
            if (first) notifyReminder(reminder!!)
            persist(true)
        }
        val r = reminder
        if (r != null) {
            val t = core.trips[r.trip]
            if (r.kind == "restaurant") {
                val i = r.customer.removePrefix("pickup-").toIntOrNull()
                val p = if (t != null && i != null) t.pickups.getOrNull(i) else null
                if (t == null || i == null || p == null || !t.active || p.pickedAt != null || (t.phase != TripPhase.TO_RESTAURANT && t.phase != TripPhase.WAITING)) {
                    reminder = null
                } else if (!RestaurantBubble.visible(true, venueDistance(t, i, now), p.arrivedAt != null, false)) {
                    reminder = null // E14: 150 m away from the restaurant without Collect: the bubble closes (it may open again at 50 m)
                } else {
                    val next = restaurantReminder(t, i, live(now) ?: r.estimate, r.revision)
                    if (next != r) {
                        // E15: the counter moved (a minute passed, "now", "ready"): the notification follows, silently.
                        if (next.headline != r.headline) notifyReminder(next, update = true)
                        reminder = next
                    }
                }
            } else if (t == null || !t.active || t.currentCustomer != r.customer || t.customers[r.customer]?.deliveredAt != null) {
                reminder = null
            } else {
                // E16: the route's remaining metres and turns while it leads to this customer, else the navigation notification's line.
                val l2 = CustomerCard.routeFor(route, legStart(t), now)?.let { CustomerCard.live(it) { tr, az -> tl(tr, az) } } ?: live(now)
                if (l2 != null && l2 != r.estimate) reminder = r.copy(estimate = l2)
            }
        }
        persist()
    }
    /** E14: straight-line metres from the courier's fresh fix to pickup [i]'s restaurant (D9's coordinate), or null. */
    private fun venueDistance(t: TripRecord, i: Int, now: Long): Double? = env.log.lastLocation?.let { l ->
        RestaurantBubble.distance(l.atMs, l.lat, l.lon, l.accM.takeIf { it > 0.0 }, venueOf(t, i), now)
    }
    private fun placeText(d: Double?, arrived: Boolean): String = when {
        arrived -> tl("Restorandasın", "Restorandasan")
        d != null -> tl("Restorana yaklaşık ${d.toInt()} m (kuş uçuşu)", "Restorana təxminən ${d.toInt()} m (düz xətt)")
        else -> tl("Restorana yakınsın", "Restorana yaxınsan")
    }
    /** Remaining route from the navigation notification, refreshed live; null when not recent. */
    private fun live(now: Long): String? = navSummary?.takeIf { now - navSummaryAt in 0..20_000 }?.let { tl("Kalan: $it", "Qalıb: $it") }
    private fun restaurantReminder(t: TripRecord, i: Int, estimate: String, revision: Long): Reminder {
        val p = t.pickups[i]
        val order = (listOfNotNull(p.code) + p.items).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        val body = listOfNotNull(p.note?.let { tl("Restoran notu: ", "Restoran qeydi: ") + it },
            order?.let { tl("Sipariş: ", "Sifariş: ") + it }, p.address).joinToString("\n\n")
        val now = now()
        return Reminder(t.id, "pickup-$i", "${platform(t.pkg)} · ${p.restaurant ?: tl("Restoran", "Restoran")}", DeliveryDetails(), estimate, revision,
            "restaurant", readiness(p, now), body, p.restaurant ?: tl("Restoran", "Restoran"), Readiness.of(p, now))
    }
    /** First line of the restaurant card and notification: the E15 ready counter ([Readiness.headline]: minutes left, time up, ready). */
    private fun readiness(p: PickupRecord, now: Long): String = Readiness.headline(Readiness.of(p, now)) { tr, az -> tl(tr, az) }
    private fun resolveRestaurant(key: String, address: String) {
        if (!geocodes.add("restaurant:$key:$address")) return
        env.geocode(address) { result -> if (result != null) synchronized(this) { restaurantCoords[key] = doubleArrayOf(result[0], result[1]) } }
    }
    private fun resolve(tripId: String, customerId: String, address: String) {
        val key = "$tripId:$customerId:$address"; if (!geocodes.add(key)) return
        env.geocode(address) { result -> synchronized(this) {
            val c = core.trips[tripId]?.customers?.get(customerId) ?: return@synchronized
            if (c.details.address != address) return@synchronized
            if (result != null) {
                val current = env.log.lastLocation
                if (current == null || VenueArrival.metres(current.lat, current.lon, result[0], result[1]) < 50000) {
                    c.lat = result[0]; c.lon = result[1]; c.coordinateSource = "android_geocoder_address_estimate"
                    persist(true)
                }
            } else env.log.event("customer_coordinate_unavailable", JSONObject().put("trip", tripId).put("customer", customerId))
        } }
    }
    /** D9: a Wolt pickup just collected ([CoreEvent] `picked_up`): its arrival from the GPS stay before it ([VenueArrival.collected]). */
    private fun pickupArrivals(events: List<CoreEvent>, now: Long): List<CoreEvent> = events.filter { it.type == "picked_up" }.flatMap { e ->
        val t = e.tripId?.let { core.trips[it] } ?: return@flatMap emptyList()
        val i = t.pickups.indexOfLast { it.pickedAt == e.at }
        if (i < 0) emptyList() else venues.collected(core, t.id, i, ::venueOf, now)
    }
    /** D9: every GPS fix of the shift ([speedMps]: the fix's own speed or the one derived from the previous fix). An open Wolt pickup
     *  whose geocoded restaurant the courier stayed at gets its arrival (and the restaurant stage) now. */
    @Synchronized fun location(fix: GpsFix, speedMps: Double?, now: Long) {
        if (!shiftOn() || !core.running) return
        init()
        // The fix's age on the monotonic clock, so its time matches the screen frames' wall clock.
        val age = (env.clock.monoMs() - fix.elapsedMs).coerceAtLeast(0)
        venues.add(VenueArrival.Fix(now - age, fix.lat, fix.lon, if (fix.hasAccuracy) fix.accuracy.toDouble() else null, speedMps?.let { it * 3.6 }))
        // M19: the customer arrival from the same fix.
        val events = (venues.live(core, ::venueOf, now, ::navOf) + venues.customers(core, ::customerCoord, now, ::customerNavOf)).takeIf { it.isNotEmpty() } ?: return
        env.observer.events(now, events, emptyMap(), core, sessions.openSessions(), idleUpdate = false)
        process(events); persist(true)
    }
    /** E8: the navigation state (WEEM `NavPort.route`; Rawstats `YolGoNavReader`), kept in memory. */
    @Synchronized fun navigation(state: RouteStatus?, now: Long) {
        if (!shiftOn() || !core.running) return
        init(); route = state; bindNavTarget(now)
    }
    /**
     * E8 + D9: a navigation request received while Wolt showed an order's pickup screen gives that pickup's restaurant
     * coordinate, before the address geocode ([NavBinding.pickupOf]). Logged without coordinates (only how far the geocode was);
     * an open pickup is checked at once against the GPS fixes already kept.
     */
    private fun bindNavTarget(now: Long) {
        val target = route?.target ?: return
        if (target.requestedAtMs == navBound) return
        val (t, i) = NavBinding.pickupOf(target, core.trips.values, trail, now) ?: return
        navBound = target.requestedAtMs
        val key = "${t.id}:$i"
        navVenues[key] = VenueArrival.Venue(target.lat, target.lon, VenueArrival.SOURCE_YOLGO)
        val gap = restaurantCoords[key]?.let { VenueArrival.metres(it[0], it[1], target.lat, target.lon).toInt() }
        env.log.event("yolgo_venue", JSONObject().put("trip", t.id).put("pickup", i).put("kind", target.kind.key)
            .put("source_package", target.sourcePackage ?: JSONObject.NULL).put("geocode_gap_m", gap ?: JSONObject.NULL))
        val events = venues.live(core, ::venueOf, now, ::navOf).takeIf { it.isNotEmpty() } ?: return
        env.observer.events(now, events, emptyMap(), core, sessions.openSessions(), idleUpdate = false)
        process(events); persist(true)
    }
    @Synchronized fun distance(m: Double) { if (shiftOn()) { core.addDistance(m); env.observer.distance(now(), m) } }
    /** Merged Sürüş Paneli, once a second while the scooter link reads values: the step's energy goes to the order on screen. */
    @Synchronized fun scooterEnergy(usedWh: Double, recoveredWh: Double, ms: Long, complete: Boolean) { if (shiftOn()) core.addEnergy(usedWh, recoveredWh, ms, complete) }
    /** E14: pickups ("trip:index") whose restaurant bubble the courier closed: it does not open again for them (memory only). */
    private val bubbleDismissed = HashSet<String>()
    @Synchronized fun dismissReminder() {
        reminder?.takeIf { it.kind == "restaurant" }?.let { bubbleDismissed += it.trip + ":" + it.customer.removePrefix("pickup-") }
        reminder = null
    }
    @Synchronized fun markCancellationObservation(pkg: String) {
        if (!canRecordScreen()) return
        env.observer.cancellation(pkg)
        env.log.event("cancellation_user_reported", JSONObject().put("pkg", pkg).put("platformActionPerformed", false))
    }
    /** E16: when the leg to the current customer began: the order's Collect, or the previous customer's handover (a bundle). */
    private fun legStart(t: TripRecord): Long? = (listOfNotNull(OrderHistory.pickedAt(t)) + t.customers.values.mapNotNull { it.deliveredAt }).maxOrNull()
    /** Navigation notification text (the live line of the cards). Nothing from it is written to disk. */
    fun navigationText(lines: List<String>, at: Long) {
        if (!canRecordScreen()) return
        NavDistance.summary(lines)?.let { navSummary = it; navSummaryAt = at }
    }
    /** True while a customer card is open, so the dialer may be read for that customer's number. */
    @Synchronized override fun awaitingPhone(now: Long): Boolean = core.running && (core.callTarget(now) != null || core.trips.values.any { t ->
        t.active && (t.phase == TripPhase.TO_CUSTOMER || t.phase == TripPhase.AT_CUSTOMER) && now - (seen[t.pkg] ?: 0) in 0..PHONE_WINDOW_MS })
    /** Number shown by the dialer after the courier tapped the call button. Kept in memory only. */
    @Synchronized fun dialerPhone(texts: List<String>, now: Long) {
        if (!canRecordScreen()) return
        val phone = texts.flatMap { NoteFields.phones(it) }.firstOrNull() ?: return
        // The customer whose call button was tapped gets the number, even if the note had none.
        val (t, c) = core.callTarget(now) ?: core.trips.values.filter { it.active && (it.phase == TripPhase.TO_CUSTOMER || it.phase == TripPhase.AT_CUSTOMER) && now - (seen[it.pkg] ?: 0) in 0..PHONE_WINDOW_MS }
            .maxByOrNull { it.latest?.at ?: it.acceptedAt }?.let { t -> t.customers[t.currentCustomer]?.let { t to it } } ?: return
        if (c.details.phone == phone) return
        c.details.phone = phone
        if (reminder?.trip == t.id && reminder?.customer == c.id) reminder = reminder!!.copy(details = c.details.copy(), revision = now)
    }
    @Synchronized fun onlineToday(): List<OnlineSessions.Closed> {
        init()
        val f = dayFile(day(), ONLINE_SESSIONS_FILE)
        val done = if (f.exists()) f.readLines().mapNotNull { runCatching { OnlineSessions.closed(JSONObject(it)) }.getOrNull() } else emptyList()
        val now = now()
        val open = sessions.openSessions().map { OnlineSessions.Closed(it.pkg, it.startAt, maxOf(it.lastOnlineAt, now), it.startObserved, false, "open", it.offers, it.accepted, it.delivered,
            it.id, it.rejected, it.offerIds.toList(), it.tripIds.toList(), it.lastOnlineAt, it.startEvidence) }
        return (done + open).sortedBy { it.startAt }
    }
    @Synchronized fun showDetails(id: String) {
        val t = core.trips[id] ?: return; val c = t.customers[t.currentCustomer] ?: return
        reminder = Reminder(t.id, c.id, "${platform(t.pkg)} · ${c.details.name ?: c.id}", c.details.copy(), tl("Teslimat bilgileri", "Çatdırılma məlumatları"), c.detailsAt,
            deadlineAt = c.deadlineAt, orders = 1 + t.sameDropoff.count { core.trips[it]?.active == true })
    }
    @Synchronized fun confirmDelivered(id: String) {
        init(); val now = now(); val events = core.confirmDelivered(id, now)
        env.observer.events(now, events, emptyMap(), core, sessions.openSessions()); process(events); persist(true)
    }
    @Synchronized fun setTip(id: String, tip: Double) { core.trips[id]?.let { it.tip = tip; it.tipSource = "user_manual"; persist(true) } }

    private var pendingWritten: Pair<Double, Long>? = null
    private val offloadsWritten = HashSet<String>()
    /** M17: the last Wolt "CASH PENDING" seen (value to wall clock), kept across restarts; null when never seen. */
    fun lastPending(): Pair<Double, Long>? = env.prefs.let { p ->
        p.getString(PREF_PENDING, null)?.toDoubleOrNull()?.let { it to p.getLong(PREF_PENDING_AT, 0L) } }
    private fun cashPending(pkg: String, texts: List<String>, now: Long) {
        CashPending.pending(pkg, texts)?.let { v ->
            env.prefs.put(mapOf(PREF_PENDING to v.toString(), PREF_PENDING_AT to now))
            if (CashPending.shouldWrite(pendingWritten, v, now) && env.log.append(CashPending.FILE, CashPending.pendingRow(v, now).toString())) pendingWritten = v to now
        }
        for (r in PlatformHistory.offloadRows(pkg, texts)) {
            if (offloadsWritten.add("${r.day} ${r.clock} ${r.amount}")) env.log.append(CashPending.FILE, CashPending.offloadRow(r, now).toString(), r.day)
        }
    }
    /**
     * M17: the shift's start or end ([kind]) with the last known cash pending; at the end also one snapshot row per order of the
     * shift (the order records are not changed). Written to the shift start's day folder.
     */
    @Synchronized fun cashPendingShift(kind: String, shiftStartMs: Long, now: Long) {
        init()
        val last = lastPending()
        val day = day(shiftStartMs)
        env.log.append(CashPending.FILE, CashPending.shiftRow(kind, shiftStartMs, last?.first, last?.second, now).toString(), day)
        if (kind != CashPending.SHIFT_END) return
        for (t in core.trips.values.filter { it.acceptedAt >= shiftStartMs }) env.log.append(CashPending.FILE, CashPending.orderSnapshot(t.id, shiftStartMs, last?.first, now).toString(), day)
    }
    /** M17: every [CashPending.FILE] row of [day]; empty when missing or unreadable. */
    fun cashPendingRows(day: String): List<JSONObject> = jsonRows(day, CashPending.FILE)
    /** M15: every [PocketCash.FILE] row of [day] (the shift start's day folder); empty when missing or unreadable. */
    fun pocketCashRows(day: String): List<JSONObject> = jsonRows(day, PocketCash.FILE)
    /** M15: the courier's answer to "how much cash is in your pocket?" for the shift started at [shiftStartMs]. */
    fun recordPocketCash(shiftStartMs: Long, amount: Double, via: String): JSONObject? {
        val row = PocketCash.row(shiftStartMs, amount, now(), via)
        return row.takeIf { env.log.append(PocketCash.FILE, it.toString(), day(shiftStartMs)) }
    }

    /** K32 newest cash answer of [trip] (its acceptance day's [CashTips.FILE]), or null. */
    fun cashTipFor(trip: TripRecord): JSONObject? = cashTipRows(day(trip.acceptedAt))[trip.id]
    /** K27 boost rows of [day] (for the day page statistics); empty when missing or unreadable. */
    fun boostRows(day: String): List<JSONObject> = jsonRows(day, BoostLog.FILE)
    /** K32 newest answer per trip id of [day]; empty when the log is missing or unreadable. */
    fun cashTipRows(day: String): Map<String, JSONObject> = try {
        val f = dayFile(day, CashTips.FILE)
        if (!f.exists()) emptyMap() else f.useLines { CashTips.latest(it) }
    } catch (e: Exception) { emptyMap() }
    /**
     * K32: the courier's answer to "how much cash did you receive?" (notification reply or the order card). Appends a new row
     * (the newest one wins, so a correction is just another answer) and sets the trip's tip = received − cash amount with
     * source `user_cash_input` (negative kept and flagged in the row). Null when the trip is unknown or not a cash order.
     * F4.6–F4.9: [cashOverride] typed in the window when the amount was not read; [tipSource] of the quick buttons.
     */
    @Synchronized fun recordCashTip(id: String, received: Double, via: String, cashOverride: Double? = null,
        tipSource: String = CashTips.SOURCE): JSONObject? {
        init()
        val live = core.trips[id]
        val t = live ?: stored().firstOrNull { it.id == id } ?: return null
        if (t.paymentMethod != CashPayment.CASH) return null
        val row = CashTips.record(t, received, now(), via, cashOverride, tipSource)
        if (!env.log.append(CashTips.FILE, row.toString(), day(t.acceptedAt))) return null
        val tip = CashTips.num(row, "tip_azn")
        if (t.cashAmount == null && cashOverride != null) t.cashAmount = CashTips.num(row, "cash_amount_azn")
        if (tip != null) {
            t.tip = tip; t.tipSource = tipSource
            // An older trip (no longer in memory) keeps its courier.db row current; the logs are never rewritten.
            if (live != null) persist(true) else runCatching { env.db.saveTrip(RecordJson.trip(t, withPhone = true)) }
        }
        return row
    }
    fun platform(pkg: String) = if (pkg.contains("wolt", true)) "Wolt" else if (pkg.contains("bolt", true) || pkg.contains("mtakso", true)) "Bolt Food" else pkg.substringAfterLast('.')
    /** `commission:<pkg>`, else the global `commission` (Float, %); 0 when neither is set. */
    fun commission(pkg: String? = null): Double = env.prefs.let { p -> p.getFloat(if (pkg == null) "commission" else "commission:$pkg", p.getFloat("commission", 0f)).toDouble() }
    /** `commission:<pkg>`, else the global `commission`; null when neither key exists. WEEM: `contains` yok, NaN varsayılanıyla aynı soru. */
    private fun commissionSet(pkg: String): Double? {
        val p = env.prefs
        val own = p.getFloat("commission:$pkg", Float.NaN); val all = p.getFloat("commission", Float.NaN)
        val c = if (!own.isNaN()) own else if (!all.isNaN()) all else null
        return c?.toDouble()
    }
    private val endedShifts = EndedShiftMinutes()
    @Synchronized fun stats(): Stats {
        init()
        val day = day()
        val today = core.trips.values.filter { day(it.acceptedAt) == day }
        // D3(c)/D4: unverified (inferred) deliveries count too; only their label differs.
        val done = today.filter { it.countsAsDelivered }
        val net = done.sumOf { it.net ?: 0.0 }
        var minutes = endedShifts.of(dayFile(day, "events.jsonl"))
        val start = env.prefs.getLong(KuryelogPrefs.SHIFT_START, now())
        if (shiftOn()) minutes += (now() - start).coerceAtLeast(0) / 60000.0
        val h = minutes / 60.0
        val rejected = env.db.offerCount(day, "rejected")
        return Stats(OrderHistory.deliveredOrders(done), net, if (h > 0.01) net / h else 0.0, today.sumOf { it.expectedOrders }, rejected,
            today.count { it.phase == TripPhase.END_UNCONFIRMED && !it.deliveredInferred })
    }
    /** [names]: the home page keeps restaurant/customer names; the overlay badge (K35) shows only the status. */
    @Synchronized fun badgeLines(names: Boolean = true): String {
        init()
        return OverlayText.badge(core, sessions, deliveredToday(), names, ::commissionSet, ::platform, ::tl)
    }
    /** [Stats.delivered] alone, for the K35 badge every 500 ms: without the SQL count, the shift minutes and the money sums. */
    private fun deliveredToday(): Int {
        val day = day()
        return OrderHistory.deliveredOrders(core.trips.values.filter { day(it.acceptedAt) == day })
    }
    fun phaseText(p: TripPhase): String = OverlayText.phase(p, ::tl)
    /** Order card status; a K34 inferred delivery is shown as an unverified delivery. */
    fun statusText(t: TripRecord): String = OrderHistory.status(t, ::tl)
    @Synchronized fun today(): List<TripRecord> { init(); val day = day(); return core.trips.values.filter { day(it.acceptedAt) == day }.reversed() }
    /** Every saved order of every day (Siparişler gün listesi); damaged rows are skipped. */
    @Synchronized fun stored(): List<TripRecord> { init(); return env.db.trips().mapNotNull { runCatching { RecordJson.trip(it) }.getOrNull() } }

    /** WEEM `OrderEvents.offers` (ORDERS.md 13.2): K21 kuralıyla dışa verilebilir teklifler. */
    @Synchronized fun offers(now: Long = now()) = OfferSummaries.of(core.running, core.pending.values, now, restartedAtMs, env.annotator)
    /** WEEM `OrderEvents.stages` (E8). */
    @Synchronized fun stages(now: Long = now()) = OrderStages.of(core.trips.values, { tripEnds[it] }, now)

    @Synchronized fun reloadImportedStats() {
        init(); endedShifts.clear(); rebuildRecords()
    }
    private fun rebuildRecords() {
        env.db.clear(); core.trips.clear(); core.pending.clear(); snapshotHashes.clear(); forgetSaved()
        val records = HashMap<String, Pair<Long, JSONObject>>()
        val offers = HashMap<String, Pair<Long, JSONObject>>()
        val results = HashMap<String, Pair<Long, String>>()
        env.log.logsRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
            File(dir, ORDERS_FILE).takeIf { it.exists() }?.forEachLine { line -> runCatching {
                val j = JSONObject(line); val at = LogTime.parseTs(j.optString("t"))
                val record = j.optJSONObject("record") ?: LegacyRecords.delivered(j)?.let(RecordJson::trip)
                record?.let { r -> val id = r.getString("id"); if (at >= (records[id]?.first ?: 0)) records[id] = at to r }
                j.optJSONObject("offer")?.let { o -> val id = o.getString("id"); if (at >= (offers[id]?.first ?: 0)) offers[id] = at to o }
                j.str("offer_id")?.let { id -> if (j.optString("type") != "offer" && at >= (results[id]?.first ?: 0)) results[id] = at to j.optString("type") }
            } }
        }
        records.values.forEach { env.db.saveTrip(it.second) }; offers.forEach { (id, p) -> env.db.saveOffer(p.second, results[id]?.second) }
        env.db.trips(day()).forEach { runCatching { RecordJson.trip(it) }.getOrNull()?.let { t -> core.trips[t.id] = t } }
    }

    companion object {
        const val OFFER_VISIBLE_MS = 2_500L
        const val NOTIFY_TO_CARD_MS = 30_000L
        const val PHONE_WINDOW_MS = 60_000L
        const val OPEN_SESSIONS = "onlineSessions.v1"
        const val PREF_PENDING = "cashPending.azn"
        const val PREF_PENDING_AT = "cashPending.at"
        const val MIGRATION_07 = "structuredMigration07"
        const val ORDERS_FILE = "orders.jsonl"
        const val PLATFORMS_FILE = "platforms.jsonl"
        const val ONLINE_SESSIONS_FILE = "online-sessions.jsonl"
        /** Rawstats `ScooterBridge.SOURCE` (teklif kaydındaki `scooter_source`). */
        const val SCOOTER_SOURCE = "surus_paneli"
        /** Events that close an order: its scooter energy joins the record, the scooter's trip meter stops counting for it. */
        val ORDER_ENDS = setOf("delivered", "delivered_inferred", "end_unconfirmed")
    }
}
