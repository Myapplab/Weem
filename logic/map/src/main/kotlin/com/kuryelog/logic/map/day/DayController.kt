package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.ShiftFollower
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.settings.MapPrefs
import org.json.JSONObject

/** Toast şeridi isteği (MAP.md 11.14): [text] seçili dilde. */
data class DayToast(val text: String)

/** Bir girdinin sonucu (MAP.md 11.14): olaylar, kaydedilecek mi, toast'lar, titreşim, güncel görünüm. */
class DayStep(val events: List<DayEvent>, val save: Boolean, val toasts: List<DayToast>, val haptic: Boolean, val view: DayView)

/** `:app`'e giden görünüm: gün durumu + ışık sayacı ([lightAt] ışık yeri). */
class DayView(val state: DayState, val lightSinceMs: Long?, val lightAt: GeoPoint? = null)

/**
 * Saf gün durum makinesi (MAP.md 11.14, 11.17): girdiler fix, aşama okuması, vardiya, tık; komutlar `startDay`, `markLight`, `setDestination`…; yan etkiler (tercihler,
 * günlük, rapor, toast şeridi) Android `DayRuntime`'dadır. Tercihler: `shift.v1`, `shift.last`, `courier.follow.v1`, `lights.v1`, `sidewalk.zones.v1`, `day.shift.handled.v1`.
 */
class DayController(private val prefs: MapPrefs, private val lang: () -> Lang) {
    /** Yol bilgisi kaynağı: (lat, lon, son kabul edilen fix'in doğruluğu) → [RoadInfo]; `RoadInfoBuilder.at` bağlanır, yoksa boş bilgi. */
    var roads: (Double, Double, Double) -> RoadInfo = { _, _, _ -> RoadInfo() }

    val lights: LightsDb = LightsDb.fromJsonString(prefs.getString(LIGHTS_KEY))
    val sidewalks: SidewalkMarks = SidewalkMarks.fromJsonString(prefs.getString(SIDEWALK_KEY))

    private var engine = restoreEngine()
    private val tracker = LightTracker()
    private val follower = CourierStageFollower().also { f ->
        prefs.getString(FOLLOW_KEY)?.takeIf { it.isNotEmpty() }?.let { runCatching { f.restore(JSONObject(it)) } }
    }
    private val shiftFollower = ShiftFollower(handled().first, handled().second)
    private var latestAccuracy = Double.POSITIVE_INFINITY
    private var lastSavedMs = 0L

    /** Biten son gün (`shift.last`). */
    var lastDay: DayState? = prefs.getString(LAST_KEY)?.takeIf { it.isNotEmpty() }?.let { runCatching { DayState.fromJson(JSONObject(it)) }.getOrNull() }
        private set

    val view: DayView get() = DayView(engine.s, tracker.sinceMs, tracker.lightAt)

    private val road: (Double, Double) -> RoadInfo = { lat, lon -> roads(lat, lon, latestAccuracy) }

    fun startDay(atMs: Long, reason: String = "manual"): DayStep {
        if (engine.s.active) return step(emptyList(), save = false)
        tracker.reset()
        val ev = engine.startDay(atMs, reason)
        if (ev.isNotEmpty()) follower.dayChanged()
        return step(ev, save = true, nowMs = atMs)
    }

    fun endDay(atMs: Long, reason: String = "manual"): DayStep {
        if (!engine.s.active) return step(emptyList(), save = false)
        val start = engine.s.startedAtMs ?: atMs
        val at = atMs.coerceAtLeast(start)
        finishHomeReturn(at)
        val ev = ArrayList<DayEvent>()
        tracker.finish(at, engine.s.current != null)?.let { engine.s.stops.add(it); ev += DayEvent("stop", it.endMs, mapOf("stop" to it.toJson())) }
        ev += engine.endDay(at, road, reason)
        val merged = StopReconciler.reconcile(engine.s, ev, null)
        if (ev.any { it.type == "day_end" }) follower.dayChanged()
        val finished = engine.s
        lastDay = finished
        prefs.setString(LAST_KEY, finished.toJson().toString())
        prefs.setString(DAY_KEY, "")
        val s = step(merged, save = true, nowMs = at, finished = finished)
        engine = ShiftEngine()
        return DayStep(s.events, s.save, s.toasts, s.haptic, view).also { saveFollower() }
    }

    /** Konum okuması: fix yaşı −5…+30 sn dışındaysa yok sayılır; motor + ışık ölçer (MAP.md 11.14). */
    fun onFix(fix: DayFix, nowMs: Long): DayStep {
        if (!engine.s.active) return step(emptyList(), save = false)
        val age = nowMs - fix.atMs
        if (age < -5_000 || age > 30_000) return step(emptyList(), save = false)
        latestAccuracy = fix.accuracy
        val ev = ArrayList<DayEvent>(engine.feed(fix, road))
        finishHomeReturnOnArrival(fix)?.let { ev += it }
        val s = engine.s
        val orderStop = s.phase == OrderPhase.AT_RESTAURANT || s.phase == OrderPhase.READY_FOR_CUSTOMER || s.phase == OrderPhase.AT_CUSTOMER
        val measured = tracker.feed(fix, road, orderStop, inOrder = s.current != null)
        if (measured != null) s.stops.add(measured)
        val merged = StopReconciler.reconcile(s, ev, measured)
        return step(merged, save = false, nowMs = nowMs)
    }

    /** Aşama okuması ([feed] null = okuma yok); [lastPoint] son konum. Aşama izleyicinin durumu `courier.follow.v1`'e yazılır (restoran adı yok). */
    fun onStages(feed: FollowerFeed?, nowMs: Long, lastPoint: GeoPoint?): DayStep {
        val before = engine.s.current?.n
        val ev = follower.sync(engine, feed, nowMs, lastPoint?.lat, lastPoint?.lon)
        if (ev.any { it.type == "order_start" } && before == null) finishHomeReturn(nowMs)
        saveFollower()
        return step(ev, save = ev.isNotEmpty(), nowMs = nowMs)
    }

    /** İkinci eksik okuma için kısa yeniden okuma gerekir mi (2 sn sonra). */
    val awaitingSecondRead: Boolean get() = follower.awaitingSecondRead

    /** `ShiftPort.state` değişimi ya da tık: vardiyaya bakarak günü açar/kapatır (MAP.md 11.2). */
    fun onShift(state: ShiftState?, nowMs: Long): DayStep {
        val d = shiftFollower.follow(state, engine.s.active, engine.s.startedAtMs)
        saveHandled()
        return when (d.action) {
            ShiftFollower.Action.START_DAY -> startDay(minOf(d.atMs ?: nowMs, nowMs), "manual")
            ShiftFollower.Action.END_DAY -> endDay(minOf(d.atMs ?: nowMs, nowMs), state?.endReason ?: "manual")
            ShiftFollower.Action.NONE -> step(emptyList(), save = false)
        }
    }

    /** 5 sn tık: kalıcılık kuralı ve görünüm tazeleme. */
    fun onTick(nowMs: Long): DayStep = step(emptyList(), save = false, nowMs = nowMs)

    fun setDestination(point: GeoPoint, nowMs: Long, restaurant: Boolean, name: String? = null): DayStep =
        step(engine.setDestination(point, nowMs, restaurant, name), save = true, nowMs = nowMs)

    /** Elle ışık işareti (uzun basış satırı 16/17); gün kapalıyken de çalışır. */
    fun markLight(lat: Double, lon: Double, exists: Boolean): DayStep {
        lights.setManual(lat, lon, exists)
        prefs.setString(LIGHTS_KEY, lights.toJsonString())
        val text = (if (exists) DayText.LIGHT_SAVED else DayText.LIGHT_HIDDEN).get(lang())
        return DayStep(emptyList(), false, listOf(DayToast(text)), false, view)
    }

    /** Elle kaldırım işareti (uzun basış satırı 5/6): ölçer sıfırlanır; olay `stop_surface_override`. */
    fun markSidewalk(point: GeoPoint, remove: Boolean, nowMs: Long): DayStep {
        sidewalks.mark(point, remove)
        prefs.setString(SIDEWALK_KEY, sidewalks.toJsonString())
        tracker.reset()
        val ev = if (engine.s.active) listOf(DayEvent("stop_surface_override", nowMs, mapOf("surface" to if (remove) "unknown" else "sidewalk"))) else emptyList()
        val text = (if (remove) DayText.SIDEWALK_REMOVED else DayText.SIDEWALK_SAVED).get(lang())
        return DayStep(ev, false, listOf(DayToast(text)), false, view)
    }

    /** "Eve dön" ölçümü başlar: gün açık, süren sipariş yok ve ölçüm sürmüyor (MAP.md 11.13). */
    fun startHomeReturn(nowMs: Long): DayStep {
        val s = engine.s
        if (!s.active || s.current != null || s.homeReturnAtMs != null) return step(emptyList(), save = false)
        s.homeReturnAtMs = nowMs
        s.homeStartM = s.distM
        return step(listOf(DayEvent("home_return_start", nowMs)), save = true, nowMs = nowMs)
    }

    /** Eve varış denetimi için ev konumu ([home] null = ev yok); runtime her fix'ten önce verir. */
    var home: GeoPoint? = null

    /**
     * Gün sonu ışık cevabı: duruş [stopStartMs] ile bulunur; `answer`, `LightsDb` öğrenmesi ve olay `light_answer`. Duruş yoksa hiçbir şey.
     */
    fun reviewLight(stopStartMs: Long, isLight: Boolean, nowMs: Long): DayStep {
        val day = engine.s.takeIf { it.stops.any { s -> s.startMs == stopStartMs } } ?: lastDay?.takeIf { it.stops.any { s -> s.startMs == stopStartMs } }
            ?: return step(emptyList(), save = false)
        val stop = day.stops.first { it.startMs == stopStartMs }
        stop.answer = if (isLight) "light" else "not"
        val placeLat = stop.lightLat ?: stop.lat
        val placeLon = stop.lightLon ?: stop.lon
        val teach = LightReview.teaches(stop, isLight)
        if (teach != null) {
            lights.record(placeLat, placeLon, teach, stop.durS)
            lights.setManual(placeLat, placeLon, teach)
            prefs.setString(LIGHTS_KEY, lights.toJsonString())
        }
        if (day === lastDay) prefs.setString(LAST_KEY, day.toJson().toString())
        val data = HashMap<String, Any?>()
        data["la"] = round5(stop.lat); data["lo"] = round5(stop.lon); data["light"] = isLight; data["dur"] = stop.durS
        stop.junctionM?.let { data["jm"] = Math.round(it) }
        if (stop.longWait) data["long"] = true
        return step(listOf(DayEvent("light_answer", nowMs, data)), save = day === engine.s, nowMs = nowMs)
    }

    private fun finishHomeReturn(nowMs: Long) {
        val s = engine.s
        val at = s.homeReturnAtMs ?: return
        s.homeTimeS += maxOf(0L, (nowMs - at) / 1000).toDouble()
        s.homeDistM += maxOf(0.0, s.distM - s.homeStartM)
        s.homeReturnAtMs = null
    }

    private fun finishHomeReturnOnArrival(fix: DayFix): DayEvent? {
        val s = engine.s
        val h = home ?: return null
        if (s.homeReturnAtMs == null) return null
        if (fix.accuracy > 20 || fix.speedMps < 0 || fix.speedMps >= 1.3) return null
        if (h.distanceTo(GeoPoint(fix.lat, fix.lon)) > 35.0) return null
        finishHomeReturn(fix.atMs)
        return DayEvent("home_return_arrived", fix.atMs)
    }

    private fun round5(v: Double) = Math.round(v * 1e5) / 1e5

    private fun step(events: List<DayEvent>, save: Boolean, nowMs: Long? = null, finished: DayState? = null): DayStep {
        val l = lang()
        var haptic = false
        val toasts = ArrayList<DayToast>()
        for (e in events) {
            DayToasts.of(e, l)?.let { (t, h) -> toasts += t; haptic = haptic || h }
        }
        val s = engine.s
        val dirty = save || events.isNotEmpty() || (nowMs != null && nowMs - lastSavedMs >= SAVE_EVERY_MS)
        if (finished == null && dirty && nowMs != null) {
            if (s.active) prefs.setString(DAY_KEY, s.toJson().toString()) else prefs.setString(DAY_KEY, "")
            lastSavedMs = nowMs
        }
        return DayStep(events, dirty, toasts, haptic, view)
    }

    private fun saveFollower() = prefs.setString(FOLLOW_KEY, follower.toJson().toString())

    private fun saveHandled() {
        val (e, s) = shiftFollower.handled()
        val j = JSONObject()
        e?.let { j.put("end", it) }
        s?.let { j.put("start", it) }
        prefs.setString(HANDLED_KEY, j.toString())
    }

    private fun handled(): Pair<Long?, Long?> = prefs.getString(HANDLED_KEY)?.takeIf { it.isNotEmpty() }?.let {
        runCatching {
            val j = JSONObject(it)
            (if (j.has("end")) j.getLong("end") else null) to (if (j.has("start")) j.getLong("start") else null)
        }.getOrNull()
    } ?: (null to null)

    private fun restoreEngine(): ShiftEngine {
        val raw = prefs.getString(DAY_KEY)?.takeIf { it.isNotEmpty() } ?: return ShiftEngine()
        val st = runCatching { DayState.fromJson(JSONObject(raw)) }.getOrNull() ?: return ShiftEngine()
        return if (st.active) ShiftEngine(st) else ShiftEngine()
    }

    companion object {
        const val DAY_KEY = "shift.v1"
        const val LAST_KEY = "shift.last"
        const val FOLLOW_KEY = "courier.follow.v1"
        const val LIGHTS_KEY = "lights.v1"
        const val SIDEWALK_KEY = "sidewalk.zones.v1"
        const val HANDLED_KEY = "day.shift.handled.v1"
        private const val SAVE_EVERY_MS = 15_000L
    }
}
