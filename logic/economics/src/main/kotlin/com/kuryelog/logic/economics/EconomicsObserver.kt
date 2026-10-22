package com.kuryelog.logic.economics

import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.core.OfferSummary
import com.kuryelog.logic.core.RangePort
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.WeatherPort
import com.kuryelog.logic.orders.CoreEvent
import com.kuryelog.logic.orders.DeliveryCore
import com.kuryelog.logic.orders.OfferAnnotator
import com.kuryelog.logic.orders.OfferState
import com.kuryelog.logic.orders.OfferWeather
import com.kuryelog.logic.orders.OnlineSessions
import com.kuryelog.logic.orders.OrderObserver
import com.kuryelog.logic.orders.OverlayText
import com.kuryelog.logic.orders.TripRecord
import com.kuryelog.logic.orders.from
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Rawstats `OrderTracker`'ın ekonomi kısmı (OrderTracker.kt:97-215 @ 6388c04, kancalar `:253-264, 287-289, 333-349, 364-365,
 * 467, 485-489, 714, 738, 741, 788`) aynı sırayla: `:orders` `OrderFlow` her kancayı Rawstats'ın çağırdığı yerde çağırır
 * (ORDERS.md 4.14), burada `EconomicsRecorder`, `WaitObservation`, `ShadowMode`, `OfferScore` ve `EnergySegments` satırları
 * yazılır. Kilit `OrderFlow`'unki (her kanca onun `@Synchronized` girişlerinden gelir). Ağ yok; satırlarda müşteri verisi yok.
 *
 * WEEM farkları (ECONOMICS.md 4): feed v1/v2 ve YolGo akışları yok (`courierState` yalnız gölge karar için); hava `WeatherPort`'tan;
 * scooter enerjisi `ScooterBridge.energy`; [OfferAnnotator] teklif özetine bölge, puan ve net ekler.
 */
class EconomicsObserver(private val env: Env) : OrderObserver, OfferAnnotator {
    /** Android'in verdikleri (`:orders` ile aynı depo, tercihler ve saat). */
    interface Env {
        val log: LogStore
        val prefs: KuryelogPrefs.Values
        val clock: Clock
        val scooter: ScooterBridge get() = ScooterBridge.NONE
        val weather: WeatherPort get() = WeatherPort.NONE
        /** WEEM N5 (A10.4e): batarya ve eve sürüş (`:map`); bağlı değilse `NONE` → girdi yok, uyarı yok. */
        val range: RangePort get() = RangePort.NONE
        /** N5 rota hesabı için arka plan; JVM testinde hemen çalışır. */
        fun background(task: () -> Unit) = task()
        /** `SystemClock.elapsedRealtimeNanos` (Android); JVM'de `System.nanoTime`. */
        fun monoNs(): Long = System.nanoTime()
    }

    private val waitCore = WaitObservation()
    private val exposureAt = HashMap<String, Long>()
    private val declineIntentAt = HashMap<String, Long>()
    /** SPEC 19 shadow mode (WP8): the v2 verdict of every offer, written into its economics-offers.jsonl row and never shown. */
    private val shadow = ShadowMode()
    /** SPEC 2.1.0 section 17.2 rows: economics-offers/-legs/-idle/-penalties.jsonl and waits v2. */
    private val econ = EconomicsRecorder().also { r -> r.verdictShadow = { shadow.verdictFor(it) } }
    /** WEEM N5 (A10.4e): teklif başına menzil girdileri. */
    private val range = OfferRange({ env.range }, env::background)
    /** The order core of the last hook (E4: the trip of an accepted offer). */
    private var core: DeliveryCore? = null
    private var idleKey = ""
    private var idleSavedAt = 0L
    private var openWaitsKey = ""
    private var openWaitsAt = 0L

    private fun now() = env.clock.wallMs()
    private fun failure(source: String, e: Throwable) = env.log.failure(source, e)

    // ------------------------------------------------------------------ OrderObserver (ORDERS.md 4.14)

    override fun restart(now: Long, kept: Collection<OfferState>, dropped: List<OfferState>) {
        // Offers pending when the process died: not exported until seen again (K21), outcome not observed (SPEC 17.2 recording_stopped).
        writeRows(econ.restart(now, kept, dropped, feeBasis()))
    }

    override fun restored() {
        try {
            writeRows(econ.restoreIdle(env.prefs.getString(OPEN_IDLE, null)))
            env.prefs.put(mapOf(OPEN_IDLE to null))
        } catch (e: Exception) { failure("economics_idle", e) }
        try { restoreOpenWaits() } catch (e: Exception) { failure("open_waits", e) }
    }

    override fun waits(o: CourierObservation, events: List<CoreEvent>, now: Long) {
        val pkg = o.pkg
        for (e in events) when (e.type) {
            "delivered" -> waitCore.nextCause(pkg, "after_delivery_observed")
            "rejected" -> waitCore.nextCause(pkg, if (now - (declineIntentAt[pkg] ?: Long.MIN_VALUE) in 0..30000) "after_decline_observed" else "after_offer_disappearance")
        }
        val zone = env.prefs.getString(PREF_ZONE, "unknown") ?: "unknown"
        val vehicle = env.prefs.getString(PREF_VEHICLE, "unknown") ?: "unknown"
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Baku")).apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        saveWaits(waitCore.observe(o, vehicle, zone, hour)); persistOpenWaits(now)
        if (now - (exposureAt[pkg] ?: 0) >= 5000 || events.isNotEmpty()) {
            exposureAt[pkg] = now
            env.log.append(EXPOSURE, JSONObject().put("at", now).put("pkg", pkg)
                .put("stage", o.stage.name).put("complete", o.complete).put("source", o.source)
                .put("demand", o.demand ?: "unknown").put("vehicleMode", vehicle).put("zone", zone).put("bakuHour", hour)
                .put("dispatchAvailabilityVerified", false).toString())
        }
    }

    override fun frame(o: CourierObservation, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore,
                       open: List<OnlineSessions.Session>, now: Long) {
        this.core = core
        val pkg = o.pkg
        // Shadow verdict at the first complete offer frame (again only if fee or km change); logged with the offer row, never shown.
        val offer = core.pending[pkg]
        val rangeKey = offer?.takeIf { rangeOn() }?.let { range.key(it.id) }
        shadow.frame(o, offer, rangeKey) { shadowContext(pkg, now, core, open, offer) }?.error?.let { failure("shadow_verdict", it) }
        writeRows(econ.frame(o, events, before, core, open, hereZone(now), feeBasis())); persistIdle(now)
    }

    override fun events(now: Long, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore,
                        open: List<OnlineSessions.Session>, idleUpdate: Boolean) {
        this.core = core
        writeRows(econ.events(now, events, before, core, open, feeBasis(), idleUpdate = idleUpdate))
    }

    override fun declineTap(pkg: String, at: Long) { declineIntentAt[pkg] = at }

    override fun clicked(pkg: String, events: List<CoreEvent>) {
        events.forEach { e -> if (e.type == "rejected") waitCore.nextCause(pkg, "after_decline_observed") }
    }

    override fun distance(now: Long, m: Double) { econ.distance(now, m) }

    override fun stopping(now: Long) {
        saveWaits(waitCore.stop()); persistOpenWaits(now(), force = true)
    }

    override fun stop(now: Long, events: List<CoreEvent>, before: Map<String, OfferState>, core: DeliveryCore) {
        this.core = core
        writeRows(econ.stop(now, events, before, core, feeBasis())); persistIdle(now, force = true)
    }

    override fun cancellation(pkg: String) {
        waitCore.breakWait(pkg, "manual_annotation")?.let { saveWaits(listOf(it)) }
        persistOpenWaits(now(), force = true)
        waitCore.nextCause(pkg, "after_cancellation_user_reported")
    }

    // ------------------------------------------------------------------ OfferAnnotator (ORDERS.md 13.2–13.3)

    /**
     * Bölgeler feed v2'deki gibi (`EconomicsLogs.pickupZone/dropoffZone`: yalnız kartta Plus Code ya da DMS varsa); puan, kartın
     * gölge değerlendirmesinin E4 puanı ([OfferScore.row] teklif sonuçlanınca aynısını yazar); net, rozetin net satırı
     * ([OverlayText.net]: komisyon ayarlı değilse null). Değerlendirme yoksa puan null: veri uydurulmaz.
     */
    override fun annotate(offer: OfferState, summary: OfferSummary): OfferSummary {
        val entry = shadow.entry(offer.id)
        val score = entry?.takeIf { it.error == null }?.let { OfferScore.of(it.result, it.gross?.takeIf { g -> g.isFinite() } ?: offer.data.gross).value }
        return summary.copy(pickupZone = OfferSummary.Zone.of(EconomicsLogs.pickupZone(offer)),
            dropoffZone = OfferSummary.Zone.of(EconomicsLogs.dropoffZone(offer)), score = score,
            netAzn = OverlayText.net(offer.data.gross, commissionSet(offer.pkg)), rangeWarnKm = rangeWarnKm(offer))
    }

    /**
     * WEEM N5 (A10.4e, T1-1 a): teklifin v2 değerlendirmesi `BATTERY_ESCAPE_NOT_MET` verdiyse bırakış → ev km (rozetin "Batarya yetmeyebilir ·
     * eve ~X km" satırı, haritanın "eve dön?" kartı); ayar kapalıysa, değerlendirme yoksa ya da km bilinmiyorsa null. Kabul yine kurye'de.
     */
    override fun rangeWarnKm(offer: OfferState): Double? {
        if (!rangeOn()) return null
        val e = shadow.entry(offer.id)?.takeIf { it.error == null && it.batteryEscapeNotMet } ?: return null
        return e.input?.optJSONObject("offer")?.takeIf { it.has("destToChargerKm") }?.optDouble("destToChargerKm")?.takeIf { it.isFinite() }
    }

    private fun rangeOn() = env.prefs.getBoolean(KuryelogPrefs.RANGE_WARN, true)

    // ------------------------------------------------------------------ E4 readers and the courier's own score (OrderTracker.kt:150-161)

    /** E4: the day's offer scores (the first row per offer: given at offer time, never changed) and my ratings (the newest per offer). */
    fun offerScores(day: String): Map<String, JSONObject> = dayRows(day, OfferScore.FILE) { OfferScore.scores(it) }
    fun offerRatings(day: String): Map<String, JSONObject> = dayRows(day, OfferScore.RATINGS) { OfferScore.ratings(it) }
    private fun dayRows(day: String, file: String, read: (Sequence<String>) -> Map<String, JSONObject>): Map<String, JSONObject> = try {
        val f = File(env.log.dayDir(day), file)
        if (!f.exists()) emptyMap() else f.useLines(block = read)
    } catch (e: Exception) { emptyMap() }
    /** E4: my own 1–10 for [trip]'s offer, a new row in its offer's day (a correction is a newer row); never touches the app's score. */
    fun rateOffer(trip: TripRecord, rating: Int, via: String): Boolean {
        val o = trip.offer ?: return false
        return env.log.append(OfferScore.RATINGS, OfferScore.ratingRow(o.id, trip.id, trip.pkg, rating, now(), via).toString(), env.log.day(o.firstAt))
    }

    // ------------------------------------------------------------------ rows

    private fun writeRows(rows: List<EconomicsRecorder.Row>) {
        for (r in rows) {
            env.log.append(r.file, r.json.toString(), r.day)
            // E4: an offer resolved: its 1–10 score from the engine's evaluation of the card (at offer time), once.
            if (r.file == EconomicsLogs.OFFERS) scoreOffer(r.json)
            // Energy schema rows next to each trip's legs and each closed idle spell (null until a scooter measured them).
            val energy = when (r.file) {
                EconomicsLogs.LEGS -> EnergySegments.fromLegs(r.json, energyStamp(), scooterEnergy)
                EconomicsLogs.IDLE -> listOfNotNull(EnergySegments.fromIdle(r.json, energyStamp(), scooterEnergy))
                else -> emptyList()
            }
            for (e in energy) env.log.append(EnergySegments.FILE, e.toString(), r.day)
        }
    }

    /** E4: offers whose score row this process wrote (a restart may write a second one; readers keep the first). */
    private val scored = LinkedHashSet<String>()
    /** E4: the offer's score row ([OfferScore.row]) in the day it was first seen, with the engine and mapping versions. */
    private fun scoreOffer(offerRow: JSONObject) {
        val id = offerRow.optString("offerKey").takeIf { it.isNotEmpty() } ?: return
        if (!scored.add(id)) return
        while (scored.size > 512) scored.remove(scored.first())
        try {
            val trip = if (offerRow.optString("decisionKind") == "accepted_observed") core?.trips?.values?.firstOrNull { it.offer?.id == id }?.id else null
            val row = OfferScore.row(offerRow, shadow.entry(id), trip, now())
            env.log.append(OfferScore.FILE, row.toString(), env.log.day(offerRow.optLong("firstSeenAtMs")))
        } catch (e: Exception) { failure("offer_score", e) }
    }

    /**
     * WEEM (2 telefon, LINK.md 4.6): `ScooterBridge.late` — pencerenin ölçülmemiş satırına düzeltme satırı (başladığı ve bittiği gün
     * dosyalarında aranır). Yazıldıysa true; hata kaydedilir.
     */
    fun lateEnergy(fromWallMs: Long, toWallMs: Long, energy: JSONObject): Boolean = try {
        var done = false
        for (day in linkedSetOf(env.log.day(fromWallMs), env.log.day(toWallMs))) {
            val f = java.io.File(env.log.dayDir(day), EnergySegments.FILE)
            if (!f.exists()) continue
            val rows = f.useLines { l -> l.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList() }
            val row = EnergySegments.corrected(rows, fromWallMs, toWallMs, energy, energyStamp()) ?: continue
            done = env.log.append(EnergySegments.FILE, row.toString(), day)
            break
        }
        done
    } catch (e: Exception) { failure("late_energy", e); false }

    /** Energy of a leg / idle window from the scooter link (`ScooterBridge.energy`); all null while no scooter source is connected. */
    private val scooterEnergy = EnergySegments.EnergyOf { start, end ->
        if (end == null || end <= start) EnergySegments.energy()
        else runCatching { runBlocking { env.scooter.energy(start, end) } }.getOrNull()?.json ?: EnergySegments.energy()
    }
    private fun energyStamp(): EnergySegments.Stamp {
        val now = now()
        val set = env.prefs.getString(EnergySegments.PREF_SCOOTER, null)
        return EnergySegments.Stamp(now, env.monoNs(), set ?: EnergySegments.DEFAULT_SCOOTER,
            if (set != null) "settings" else "default_single_scooter", env.weather.current()?.let { OfferWeather.from(it, now) })
    }

    /** Engine context of an offer of [pkg]: `commission:<pkg>` (else the global `commission`), vehicle, mode, courier state. */
    private fun shadowContext(pkg: String, now: Long, core: DeliveryCore, open: List<OnlineSessions.Session>, offer: OfferState?): ShadowEvaluator.Context =
        ShadowEvaluator.Context.of(courierState(now, core, open), commissionSet(pkg), env.prefs.getString(PREF_VEHICLE, null),
            env.prefs.getString(ShadowEvaluator.APP_MODE_KEY, ShadowEvaluator.SHADOW), rangeInputs(offer, now))

    /** N5: ayar açıkken teklifin menzil girdileri (ev `home.lat/lon`, son taze konum); hata değerlendirmeyi düşürmez. */
    private fun rangeInputs(offer: OfferState?, now: Long): ShadowEvaluator.Range? {
        if (offer == null || !rangeOn()) return null
        return try {
            val lat = env.prefs.getString(KuryelogPrefs.HOME_LAT, null)?.toDoubleOrNull()
            val lon = env.prefs.getString(KuryelogPrefs.HOME_LON, null)?.toDoubleOrNull()
            val home = if (lat != null && lon != null) OfferGeo.LatLon(lat, lon) else null
            val here = env.log.lastLocation?.takeIf { now - it.atMs in 0..FRESH_FIX_MS }?.let { OfferGeo.LatLon(it.lat, it.lon) }
            range.inputs(offer, home, here)
        } catch (e: Exception) { failure("range_inputs", e); null }
    }
    private fun courierState(now: Long, core: DeliveryCore, open: List<OnlineSessions.Session>): JSONObject {
        val l = env.log.lastLocation?.takeIf { now - it.atMs in 0..FRESH_FIX_MS }
        return econ.courierState(now, core, open.map { it.pkg }, hereZone(now), l?.speedMs?.let { it * 3.6 })
    }
    /** `commission:<pkg>`, else the global `commission`; null when neither key exists (the fee basis is then unknown). WEEM: `contains` yok, NaN varsayılanıyla aynı soru. */
    private fun commissionSet(pkg: String): Double? {
        val own = env.prefs.getFloat("commission:$pkg", Float.NaN); val all = env.prefs.getFloat("commission", Float.NaN)
        val c = if (!own.isNaN()) own else if (!all.isNaN()) all else null
        return c?.toDouble()
    }
    /** SPEC 17.2: the displayed fee is gross; `gross_before_park` once a commission is set for the platform (or globally). */
    private fun feeBasis(): (String) -> String = { pkg -> if (commissionSet(pkg) != null) "gross_before_park" else "unknown" }
    /** Coarse zone of the courier's last fresh GPS fix (no coordinate is logged). */
    private fun hereZone(now: Long): String = env.log.lastLocation?.takeIf { now - it.atMs in 0..FRESH_FIX_MS }
        ?.let { OfferGeo.coarseZone(it.lat, it.lon) } ?: OfferGeo.UNKNOWN

    /** The open courier-level idle spell survives a process death as a censored row (like the open waits). */
    private fun persistIdle(now: Long, force: Boolean = false) {
        val key = econ.idleKey()
        if (!force && key == idleKey && (key.isEmpty() || now - idleSavedAt < 10_000)) return
        idleKey = key; idleSavedAt = now
        env.prefs.put(mapOf(OPEN_IDLE to econ.encodeIdle()))
    }
    private fun saveWaits(rows: List<WaitObservation.Episode>) = writeRows(econ.waits(rows))
    /** Persists open wait windows so a process death becomes a censored episode
     *  instead of a silent loss. Writes only when the set of windows changes or
     *  every 10 s while one is open; never on every screen read. */
    private fun persistOpenWaits(now: Long, force: Boolean = false) {
        val snaps = waitCore.snapshot()
        val key = snaps.joinToString("|") { "${it.pkg}:${it.start}:${it.cause}:${it.demand}:${it.vehicle}:${it.zone}:${it.hour}" }
        if (!force && key == openWaitsKey && (snaps.isEmpty() || now - openWaitsAt < 10_000)) return
        openWaitsKey = key; openWaitsAt = now
        env.prefs.put(mapOf(OPEN_WAITS to if (snaps.isEmpty()) null else WaitObservation.encode(snaps)))
    }
    private fun restoreOpenWaits() {
        val snaps = WaitObservation.decode(env.prefs.getString(OPEN_WAITS, null))
        env.prefs.put(mapOf(OPEN_WAITS to null))
        if (snaps.isNotEmpty()) saveWaits(waitCore.restoreCensored(snaps))
    }

    companion object {
        /** Tercih anahtarları (VERI_SOZLESMESI; Rawstats ile aynı). */
        const val OPEN_IDLE = "economicsIdle.v1"
        const val OPEN_WAITS = "openWaits.v1"
        const val PREF_ZONE = "economics.zone"
        const val PREF_VEHICLE = "economics.vehicle"
        const val EXPOSURE = "economics-exposure.jsonl"
        const val FRESH_FIX_MS = 120_000L
    }
}
