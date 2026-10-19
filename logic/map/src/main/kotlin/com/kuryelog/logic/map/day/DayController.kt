package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.ShiftFollower
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.PoiSet
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.settings.MapPrefs
import com.kuryelog.logic.map.ui.ToastTone
import java.util.Locale
import org.json.JSONObject

/** Titreşim şiddeti (şerit `:app`'te uygular). */
enum class Haptic { NONE, MEDIUM, STRONG }

/** Toast şeridi isteği (MAP.md 11.14): [text] seçili dilde. */
data class DayToast(val text: String, val tone: ToastTone = ToastTone.GREEN, val seconds: Int = 4, val haptic: Haptic = Haptic.NONE)

/** Bir girdinin sonucu (MAP.md 11.14): olaylar, kaydedilecek mi, toast'lar, titreşim, güncel görünüm. */
class DayStep(val events: List<DayEvent>, val save: Boolean, val toasts: List<DayToast>, val haptic: Boolean, val view: DayView)

/** `:app`'e giden görünüm: gün durumu + canlı ışık ölçer ([lightSinceMs], [lightAt]) + ışık katmanı sürümü + biten son gün. */
class DayView(
    val state: DayState,
    val lightSinceMs: Long?,
    val lightAt: GeoPoint? = null,
    val lightsRev: Int = 0,
    val lastDay: DayState? = null,
)

/**
 * Saf gün durum makinesi (MAP.md 11.14, 11.17): girdiler fix ([onFix]), aşama okuması ([onStages]), vardiya ([onShift]), tık ([onTick]); komutlar [startDay], [endDay],
 * [markLight], [markSidewalk], [setDestination], [startHomeReturn], [reviewLight]. Tercihler (`shift.v1`, `shift.last`, `courier.follow.v1`, `lights.v1`,
 * `sidewalk.zones.v1`, `day.shift.handled.v1`) burada yazılır; sürüş günlüğü, gün raporu, görev sinyalleri ve toast şeridi Android `DayRuntime`'dadır.
 * Gün açılış/kapanışı vardiyadandır (11.2): karar [ShiftFollower]'ındır.
 */
class DayController(private val prefs: MapPrefs, private val lang: () -> Lang) {
    private var engine = ShiftEngine()
    private val follower = CourierStageFollower()
    private val tracker = LightTracker()
    private val lights = LightsDb(prefs)
    private val sidewalks = SidewalkMarks(prefs)
    private var handledEndMs: Long?
    private var handledStartMs: Long?
    private var shiftFollower: ShiftFollower
    private var latestFix: DayFix? = null
    private var lastSaveMs = 0L
    private var followSaved = ""
    private var lightsRev = 0

    /** Biten son gün (`shift.last`). */
    var lastDay: DayState? = null
        private set

    /** Düzeltilmemiş harita noktaları ve yol grafiği (yoksa yol bilgisi 11.8 adım 6). */
    private var pois: PoiSet? = null
    private var graph: RoadGraph? = null

    /** Ayar `lightMaxM` (3–40 m, varsayılan 5). */
    var lightMaxM = 5.0

    /** Ev konumu (`:core`'dan; hiçbir olaya yazılmaz). */
    var home: GeoPoint? = null

    init {
        followSaved = prefs.getString(FOLLOW_KEY) ?: ""
        runCatching { if (followSaved.isNotEmpty()) follower.restore(JSONObject(followSaved)) }
        val stored = read(STATE_KEY)
        if (stored != null && stored.active) engine = ShiftEngine(stored)
        lastDay = read(LAST_KEY)
        val handled = runCatching { JSONObject(prefs.getString(HANDLED_KEY).takeUnless { it.isNullOrEmpty() } ?: "{}") }.getOrDefault(JSONObject())
        handledEndMs = (handled.opt("end") as? Number)?.toLong()
        handledStartMs = (handled.opt("start") as? Number)?.toLong()
        shiftFollower = ShiftFollower(handledEndMs, handledStartMs)
    }

    private fun read(key: String): DayState? = runCatching {
        val raw = prefs.getString(key)
        if (raw.isNullOrEmpty()) null else DayState.fromJson(JSONObject(raw))
    }.getOrNull()

    val view: DayView
        get() = DayView(engine.s, tracker.sinceMs, tracker.lightAt, lightsRev, lastDay)

    /** Harita verisi (yol grafiği ve düzeltilmemiş POI'ler) bağlanır ya da kalkar. */
    fun bindMapData(pois: PoiSet?, graph: RoadGraph?) {
        this.pois = pois
        this.graph = graph
    }

    /** Işık katmanı için öğrenilmiş ışık yerleri. */
    val lightSpots: List<LightSpot> get() = lights.spots

    /** Gün raporundaki `lightsLearned`. */
    val lightsLearned: Int get() = lights.confirmed

    private fun road(lat: Double, lon: Double): RoadInfo =
        RoadInfoBuilder(pois, graph, lights, sidewalks, lightMaxM, latestFix?.accuracy).build(lat, lon)

    // ---------------------------------------------------------------- Gün

    fun startDay(atMs: Long, reason: String = "manual"): DayStep {
        tracker.reset()
        val ev = engine.startDay(atMs, reason)
        if (ev.isNotEmpty()) {
            follower.dayChanged()
            saveFollow()
        }
        return apply(ev, atMs, save = true)
    }

    /** `atMs` [gün başı, şimdi] aralığına sıkıştırılır; eve dönüş ölçümü biter, ölçer kapanır, gün raporu için [lastDay] yazılır, `shift.v1` silinir. */
    fun endDay(atMs: Long, nowMs: Long, reason: String = "manual"): DayStep {
        if (!engine.s.active) return apply(emptyList(), nowMs)
        val start = engine.s.startedAtMs
        val end = if (atMs > nowMs) nowMs else if (start != null && atMs < start) start else atMs
        HomeReturn.finish(engine.s, end)
        val measured = tracker.finish(end, inOrder = engine.s.current != null)
        if (measured != null) engine.s.stops.add(measured)
        val ended = engine.endDay(end, ::road, reason)
        if (ended.any { it.type == "day_end" }) {
            follower.dayChanged()
            saveFollow()
        }
        val ev = StopReconciler.reconcile(engine.s, ended, measured)
        val step = apply(ev, end, save = true)
        val day = engine.s
        lastDay = day
        prefs.setString(LAST_KEY, day.toJson().toString())
        prefs.remove(STATE_KEY)
        engine = ShiftEngine()
        return DayStep(step.events, true, step.toasts, step.haptic, view)
    }

    /** Vardiya durumu (`ShiftPort.state`): gün açılışı/kapanışı vardiyadan; her değişimde ve 5 sn tıkta çağrılır. Okuma yok (null) → hiçbir şey. */
    fun onShift(state: ShiftState?, nowMs: Long): DayStep {
        val s = engine.s
        // Süreç ölüyken vardiya bitip yenisi başladıysa eski gün yeni vardiyanın başlangıcıyla kapanır, yenisi açılır (11.14).
        if (state != null && state.active && s.active && state.startedAtMs != null && s.startedAtMs != null &&
            state.startedAtMs != s.startedAtMs && state.startedAtMs!! <= nowMs
        ) {
            val closed = endDay(state.startedAtMs!!, nowMs, "manual")
            val opened = startDay(state.startedAtMs!!, "manual")
            handled(start = state.startedAtMs)
            return DayStep(closed.events + opened.events, true, closed.toasts + opened.toasts, closed.haptic || opened.haptic, view)
        }
        val d = shiftFollower.follow(state, engine.s.active, engine.s.startedAtMs)
        if (d.handledEndMs != null || d.handledStartMs != null) handled(end = d.handledEndMs, start = d.handledStartMs)
        return when (d.action) {
            ShiftFollower.Action.START_DAY -> startDay(minOf(d.atMs!!, nowMs), "manual")
            ShiftFollower.Action.END_DAY -> endDay(d.atMs!!, nowMs, state?.endReason ?: "manual")
            ShiftFollower.Action.NONE -> apply(emptyList(), nowMs)
        }
    }

    private fun handled(end: Long? = null, start: Long? = null) {
        if (end != null) handledEndMs = end
        if (start != null) handledStartMs = start
        val j = JSONObject()
        handledEndMs?.let { j.put("end", it) }
        handledStartMs?.let { j.put("start", it) }
        prefs.setString(HANDLED_KEY, j.toString())
    }

    /** 5 sn tık (yalnız gün açıkken): 15 sn kalıcılık kuralı ve görünüm tazeleme. */
    fun onTick(nowMs: Long): DayStep = apply(emptyList(), nowMs)

    // ---------------------------------------------------------------- Girdiler

    /** Konum okuması: fix yaşı −5…+30 sn dışındaysa yok sayılır; motor + ışık ölçer (MAP.md 11.14). */
    fun onFix(fix: DayFix, nowMs: Long): DayStep {
        if (!engine.s.active) return apply(emptyList(), nowMs)
        val age = nowMs - fix.atMs
        if (age < -5000 || age > 30000) return apply(emptyList(), nowMs)
        latestFix = fix
        val ev = ArrayList(engine.feed(fix, ::road))
        HomeReturn.arrived(engine.s, fix, home)?.let { ev += it }
        val ph = engine.s.phase
        val measured = tracker.feed(
            fix, ::road,
            orderStop = ph == OrderPhase.AT_RESTAURANT || ph == OrderPhase.READY_FOR_CUSTOMER || ph == OrderPhase.AT_CUSTOMER,
            inOrder = engine.s.current != null,
        )
        if (measured != null) engine.s.stops.add(measured)
        return apply(StopReconciler.reconcile(engine.s, ev, measured), nowMs, emit = true)
    }

    /** Aşama okuması ([feed] null = okuma yok); [lastPoint] son konum. Aşama izleyicinin durumu `courier.follow.v1`'e yazılır (restoran adı yok). */
    fun onStages(feed: FollowerFeed?, nowMs: Long, lastPoint: GeoPoint?): DayStep {
        val ev = follower.sync(engine, feed, nowMs, lastPoint?.lat, lastPoint?.lon)
        // Eve dönerken yeni sipariş açıldı: eve dönüş ölçümü biter.
        if (ev.any { it.type == "order_start" }) HomeReturn.finish(engine.s, nowMs)
        saveFollow()
        return apply(ev, nowMs, save = ev.isNotEmpty())
    }

    /** İkinci okuma gerekli mi (2 sn sonra bir kez; MAP.md 11.7). */
    val awaitingSecondRead: Boolean get() = follower.awaitingSecondRead

    // ---------------------------------------------------------------- Komutlar

    /** Navigasyonun hedef bağı: açık siparişe restoran/müşteri hedefi yazar (sipariş açmaz). */
    fun setDestination(point: GeoPoint, nowMs: Long, restaurant: Boolean, name: String? = null): DayStep =
        apply(engine.setDestination(point, nowMs, restaurant, name), nowMs, save = true)

    /** Elle ışık var/yok işareti (gün kapalıyken de çalışır; olay yazılmaz). */
    fun markLight(lat: Double, lon: Double, exists: Boolean, nowMs: Long): DayStep {
        lights.setManual(lat, lon, exists)
        lightsRev++
        val toast = DayToast((if (exists) DayText.LIGHT_SAVED else DayText.LIGHT_HIDDEN).get(lang()), ToastTone.ORANGE)
        return apply(emptyList(), nowMs, emit = true, save = true, extra = listOf(toast))
    }

    /** Elle "kaldırımda bekliyorum" işareti ya da kaldırma: ölçer sıfırlanır; günlüğe `stop_surface_override` (gün kapalıysa yazılmaz). */
    fun markSidewalk(point: GeoPoint, remove: Boolean, nowMs: Long): DayStep {
        sidewalks.mark(point, remove)
        tracker.reset()
        val ev = DayEvent("stop_surface_override", nowMs, mapOf("surface" to if (remove) "unknown" else "sidewalk"))
        val toast = DayToast((if (remove) DayText.SIDEWALK_OFF else DayText.SIDEWALK_ON).get(lang()))
        return apply(listOf(ev), nowMs, emit = true, extra = listOf(toast))
    }

    fun startHomeReturn(nowMs: Long): DayStep = apply(HomeReturn.start(engine.s, nowMs), nowMs, save = true)

    /**
     * Gün sonu ışık cevabı (MAP.md 11.11): [day] biten günün durumu (gün raporundan; silinmiş gün için çağıran çağırmaz), [stopStartMs] duruşun `start` ms'i.
     * Cevap öğretir (`LightReview.teaches`): ışık yeri `lights.v1`'e `record` + `setManual` ile (son cevap kazanır). Olay `light_answer`; [DayStep.save] gün
     * raporunun yeniden yazılacağını söyler (`shift.last` burada güncellenir).
     */
    fun reviewLight(day: DayState, stopStartMs: Long, isLight: Boolean, nowMs: Long): DayStep {
        val stop = day.stops.firstOrNull { it.startMs == stopStartMs } ?: return apply(emptyList(), nowMs)
        stop.answer = if (isLight) "light" else "not"
        val lat = stop.lightLat ?: stop.lat
        val lon = stop.lightLon ?: stop.lon
        val teach = LightReview.teaches(stop, isLight)
        if (teach != null) {
            lights.record(lat, lon, teach, stop.durS)
            lights.setManual(lat, lon, teach)
            lightsRev++
        }
        val data = LinkedHashMap<String, Any?>()
        data["la"] = round(stop.lat, 5)
        data["lo"] = round(stop.lon, 5)
        data["light"] = isLight
        data["dur"] = stop.durS
        stop.junctionM?.let { data["jm"] = Math.round(it) }
        if (stop.longWait) data["long"] = true
        if (day.dayId != null && day.dayId == lastDay?.dayId) prefs.setString(LAST_KEY, day.toJson().toString())
        return apply(listOf(DayEvent("light_answer", nowMs, data)), nowMs, emit = true, save = true)
    }

    private fun round(v: Double, n: Int): Double = String.format(Locale.ROOT, "%.${n}f", v).toDouble()

    // ---------------------------------------------------------------- İç

    private fun saveFollow() {
        val f = follower.toJson().toString()
        if (f != followSaved) {
            followSaved = f
            prefs.setString(FOLLOW_KEY, f)
        }
    }

    /** `_apply` (MAP.md 11.14): olay yok ve emit/save yoksa hiçbir şey; kalıcılık (olay, [save] ya da son yazımdan ≥ 15 sn); görünüm. */
    private fun apply(
        events: List<DayEvent>,
        nowMs: Long,
        emit: Boolean = false,
        save: Boolean = false,
        extra: List<DayToast> = emptyList(),
    ): DayStep {
        val toasts = ArrayList<DayToast>()
        for (e in events) DayText.toastFor(e, lang())?.let { toasts += it }
        toasts += extra
        val did = save || events.isNotEmpty()
        if (engine.s.active && (did || nowMs - lastSaveMs >= SAVE_EVERY_MS)) {
            lastSaveMs = nowMs
            prefs.setString(STATE_KEY, engine.s.toJson().toString())
        } else if (!engine.s.active && did) {
            prefs.remove(STATE_KEY)
        }
        val haptic = toasts.any { it.haptic != Haptic.NONE }
        return DayStep(events, did, toasts, haptic, view)
    }

    companion object {
        const val STATE_KEY = "shift.v1"
        const val LAST_KEY = "shift.last"
        const val FOLLOW_KEY = "courier.follow.v1"
        const val HANDLED_KEY = "day.shift.handled.v1"
        const val SAVE_EVERY_MS = 15_000L
    }
}
