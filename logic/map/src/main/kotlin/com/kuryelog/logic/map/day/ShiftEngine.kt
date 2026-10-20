package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint

/**
 * Gün motoru (Dart `ShiftEngine`, MAP.md 11.3–11.6): km, hareket süresi, duruşlar ve siparişin aşamaları; aşamayı yalnız ekran okuması ilerletir
 * ([stageFromFeed], [deliverFromFeed]), motor varış/alış/teslim kararı vermez ve soru sormaz. Zaman epoch ms. Gün başı ve sipariş aşamaları (11.6) A6.5'te (harita gün kartı); `endDay`, `feed` (km, duruş) A6.8'de.
 */
class ShiftEngine(val s: DayState = DayState()) {
    companion object {
        const val STOP_MIN_S = 10
        const val STOP_RADIUS_M = 30.0
        const val STOP_KMH = 5.0
        const val DEPART_KMH = 10.0
        const val LIGHT_MAX_S = 180
        const val LIGHT_LONG_S = 120

        /** Aşama okumasından gelen olayların `source` değeri (kayıt dizgisi korunur). */
        const val FEED_SOURCE = "kuryeplus"

        /** Sipariş aşamalarının sırası (`none` = −1). */
        fun phaseRank(p: OrderPhase): Int = when (p) {
            OrderPhase.NONE -> -1
            OrderPhase.TO_RESTAURANT -> 0
            OrderPhase.AT_RESTAURANT -> 1
            OrderPhase.READY_FOR_CUSTOMER -> 2
            OrderPhase.TO_CUSTOMER -> 3
            OrderPhase.AT_CUSTOMER -> 4
        }
    }

    fun startDay(atMs: Long, reason: String? = null): List<DayEvent> {
        if (s.active) return emptyList()
        val r = reason ?: "manual"
        s.reset()
        s.active = true
        s.startedAtMs = atMs
        s.startReason = r
        return listOf(DayEvent("day_start", atMs, mapOf("reason" to r)))
    }

    fun endDay(atMs: Long, road: (Double, Double) -> RoadInfo, reason: String? = null): List<DayEvent> {
        if (!s.active) return emptyList()
        val ev = ArrayList<DayEvent>()
        cand?.let { ev += endStop(it, road); cand = null }
        for (o in listOfNotNull(s.current) + s.queuedOrders) {
            o.cancelled = true
            o.endedAtMs = atMs
            s.orders.add(o)
        }
        s.current = null
        s.queuedOrders.clear()
        s.phase = OrderPhase.NONE
        s.active = false
        s.endedAtMs = atMs
        s.endReason = reason ?: "manual"
        ev += DayEvent("day_end", atMs, mapOf("reason" to s.endReason))
        return ev
    }

    /** Gün açık değilse hiçbir şey yapmaz (sipariş günü kendiliğinden başlatmaz). */
    fun startOrder(atMs: Long): List<DayEvent> {
        if (!s.active || s.current != null) return emptyList()
        val o = OrderRec(nextNumber(), atMs)
        s.current = o
        s.phase = OrderPhase.TO_RESTAURANT
        return listOf(DayEvent("order_start", atMs, mapOf("n" to o.n)))
    }

    fun cancelOrder(atMs: Long): List<DayEvent> {
        val o = s.current ?: return emptyList()
        o.cancelled = true
        o.endedAtMs = atMs
        s.orders.add(o)
        activateNext()
        return listOf(DayEvent("order_cancel", atMs, mapOf("n" to o.n)))
    }

    /** Navigasyonun hedef bağı: sipariş açmaz; açık siparişin restoran/müşteri hedefini yazar (aşamayı geri sarmaz). */
    fun setDestination(point: GeoPoint, atMs: Long, restaurant: Boolean, name: String? = null): List<DayEvent> {
        val o = s.current ?: return emptyList()
        if (restaurant) {
            if (o.restLeftAtMs != null) return emptyList() // eldeki siparişe başka restoran yazma
            o.restTarget = point
            o.restName = name ?: ""
        } else if (o.restLeftAtMs != null) {
            o.customerTarget = point
        } else {
            return emptyList()
        }
        return listOf(DayEvent("order_target", atMs, mapOf("n" to o.n, "restaurant" to restaurant)))
    }

    /** Ekran okumasından aşama: saat [atMs] (son uygulanan ile şimdi arasına kırpılır). Yalnız ileri; görülmeyen aşamaya saat uydurulmaz. */
    fun stageFromFeed(phase: OrderPhase, atMs: Long, lat: Double? = null, lon: Double? = null): List<DayEvent> {
        if (s.current == null || phaseRank(s.phase) >= phaseRank(phase)) return emptyList()
        val ev = ArrayList<DayEvent>()
        if (phase == OrderPhase.AT_RESTAURANT) {
            ev += arrive(atMs, lat, lon)
        } else if (phase == OrderPhase.TO_CUSTOMER || phase == OrderPhase.AT_CUSTOMER) {
            if (s.phase == OrderPhase.TO_RESTAURANT) ev += pickedUp(atMs)
            if (s.phase == OrderPhase.AT_RESTAURANT) ev += depart(atMs)
            if (s.phase == OrderPhase.READY_FOR_CUSTOMER) ev += beginCustomerJourney(atMs)
            if (phase == OrderPhase.AT_CUSTOMER && s.phase == OrderPhase.TO_CUSTOMER) ev += arrive(atMs, lat, lon)
        }
        return fromFeed(ev)
    }

    fun deliverFromFeed(atMs: Long): List<DayEvent> {
        val o = s.current ?: return emptyList()
        if (s.phase == OrderPhase.AT_CUSTOMER) return fromFeed(depart(atMs))
        o.deliveredAtMs = atMs
        o.endedAtMs = atMs
        s.orders.add(o)
        s.delivered++
        activateNext()
        return fromFeed(listOf(DayEvent("delivered", atMs, mapOf("n" to o.n, "totalS" to o.totalS, "m" to Math.round(o.distM)))))
    }

    /** Saatsiz eski kural: süren sipariş [target]'a kadar (en çok 6 adım) ileri; [deliver] → müşteriden sonra teslim. */
    fun advanceFromFeed(target: OrderPhase, atMs: Long, lat: Double? = null, lon: Double? = null, deliver: Boolean = false): List<DayEvent> {
        val o = s.current ?: return emptyList()
        val ev = ArrayList<DayEvent>()
        var i = 0
        while (i < 6 && s.current === o) {
            if (!deliver && phaseRank(s.phase) >= phaseRank(target)) break
            val step = when (s.phase) {
                OrderPhase.TO_RESTAURANT, OrderPhase.TO_CUSTOMER -> arrive(atMs, lat, lon)
                OrderPhase.AT_RESTAURANT, OrderPhase.AT_CUSTOMER -> depart(atMs)
                OrderPhase.READY_FOR_CUSTOMER -> beginCustomerJourney(atMs)
                OrderPhase.NONE -> emptyList()
            }
            if (step.isEmpty()) break
            ev += step
            i++
        }
        return fromFeed(ev)
    }

    fun beginCustomerJourney(atMs: Long): List<DayEvent> {
        val o = s.current
        if (o == null || s.phase != OrderPhase.READY_FOR_CUSTOMER) return emptyList()
        if (o.customerDepartedAtMs == null) o.customerDepartedAtMs = atMs
        s.phase = OrderPhase.TO_CUSTOMER
        return listOf(DayEvent("customer_journey", atMs, mapOf("n" to o.n)))
    }

    private fun nextNumber(): Int = (listOf(0) + s.orders.map { it.n } + listOfNotNull(s.current?.n) + s.queuedOrders.map { it.n }).max() + 1

    /** Eski kayıtta sırada bekleyen sipariş varsa o sürer. */
    private fun activateNext() {
        s.current = if (s.queuedOrders.isEmpty()) null else s.queuedOrders.removeAt(0)
        s.phase = s.current?.phase ?: OrderPhase.NONE
    }

    /** Varış yalnız ekran okumasından gelir (`auto: true`). */
    private fun arrive(atMs: Long, lat: Double?, lon: Double?): List<DayEvent> {
        val o = s.current ?: return emptyList()
        val data = mapOf("n" to o.n, "auto" to true)
        if (s.phase == OrderPhase.TO_RESTAURANT) {
            o.restArrivedAtMs = atMs; o.restLat = lat; o.restLon = lon; o.restAuto = true
            s.phase = OrderPhase.AT_RESTAURANT
            return listOf(DayEvent("arrive_restaurant", atMs, data))
        }
        if (s.phase == OrderPhase.TO_CUSTOMER) {
            o.custArrivedAtMs = atMs; o.custLat = lat; o.custLon = lon; o.custAuto = true
            s.phase = OrderPhase.AT_CUSTOMER
            return listOf(DayEvent("arrive_customer", atMs, data))
        }
        return emptyList()
    }

    private fun depart(atMs: Long): List<DayEvent> {
        val o = s.current ?: return emptyList()
        if (s.phase == OrderPhase.AT_RESTAURANT) {
            o.restLeftAtMs = atMs
            s.phase = OrderPhase.READY_FOR_CUSTOMER
            return listOf(DayEvent("leave_restaurant", atMs, mapOf("n" to o.n, "waitS" to o.restWaitS)))
        }
        if (s.phase == OrderPhase.AT_CUSTOMER) {
            o.deliveredAtMs = atMs
            o.endedAtMs = atMs
            s.orders.add(o)
            s.delivered++
            activateNext()
            return listOf(DayEvent("delivered", atMs, mapOf("n" to o.n, "totalS" to o.totalS, "m" to Math.round(o.distM))))
        }
        return emptyList()
    }

    /** Alış görüldü, restorana varış görülmedi: [atMs]'te alındı, bekleme bilinmiyor (D17). */
    private fun pickedUp(atMs: Long): List<DayEvent> {
        val o = s.current
        if (o == null || s.phase != OrderPhase.TO_RESTAURANT) return emptyList()
        o.restLeftAtMs = atMs
        s.phase = OrderPhase.READY_FOR_CUSTOMER
        return listOf(DayEvent("leave_restaurant", atMs, mapOf("n" to o.n, "waitS" to o.restWaitS)))
    }

    private fun fromFeed(ev: List<DayEvent>): List<DayEvent> = ev.map { it.copy(data = it.data + ("source" to FEED_SOURCE)) }

    /** Bir konum okuması: km, hareket süresi, duruş. Eski ya da geriye giden zaman, 41° sıçraması, titreme km eklemez. */
    fun feed(fix: DayFix, road: (Double, Double) -> RoadInfo): List<DayEvent> {
        if (!s.active) return emptyList()
        if (!fix.accuracy.isFinite() || fix.accuracy < 0 || fix.accuracy > 50) return emptyList()
        if (!fix.lat.isFinite() || !fix.lon.isFinite() || Math.abs(fix.lat) > 90 || Math.abs(fix.lon) > 180) return emptyList()
        if (lastFeedAtMs != null && fix.atMs <= lastFeedAtMs!!) return emptyList()
        lastFeedAtMs = fix.atMs
        val kmh = if (fix.speedMps < 0) 0.0 else fix.speedMps * 3.6
        odometer(fix, kmh)
        val ev = ArrayList<DayEvent>()
        fast = if (kmh > DEPART_KMH) fast + 1 else 0
        var c = cand
        if (c == null && kmh <= STOP_KMH) {
            cand = Cand(fix.atMs, fix.lat, fix.lon)
            return ev
        }
        if (c != null) {
            val d = Geo.haversine(c.lat, c.lon, fix.lat, fix.lon)
            if (fast >= 2 || (d > STOP_RADIUS_M && fix.accuracy <= 25)) {
                ev += endStop(c, road)
                cand = if (kmh <= STOP_KMH) Cand(fix.atMs, fix.lat, fix.lon) else null
                return ev
            }
            if (kmh <= STOP_KMH) c.lastMs = fix.atMs
            if (!c.announced && c.lastMs - c.startMs >= STOP_MIN_S * 1000L) {
                c.announced = true
                ev += DayEvent("stopping", fix.atMs, mapOf("lat" to c.lat, "lon" to c.lon))
            }
        }
        return ev
    }

    private class Cand(val startMs: Long, val lat: Double, val lon: Double) {
        var lastMs: Long = startMs
        var announced = false
    }

    private var cand: Cand? = null
    private var fast = 0
    private var lastFeedAtMs: Long? = null
    private var prevT: Long? = null
    private var odoT: Long? = null
    private var odoLat = 0.0
    private var odoLon = 0.0

    private fun odometer(f: DayFix, kmh: Double) {
        if (f.accuracy > 35 || f.accuracy < 0 || !f.accuracy.isFinite()) return
        val prev = prevT
        if (prev != null && f.atMs <= prev) return
        prevT = f.atMs
        if (prev != null && kmh > STOP_KMH) {
            val dt = (f.atMs - prev) / 1000.0
            if (dt > 0 && dt <= 15) s.movingS += dt
        }
        val aT = odoT
        if (aT == null) {
            anchor(f)
            return
        }
        val d = Geo.haversine(odoLat, odoLon, f.lat, f.lon)
        val dt = (f.atMs - aT) / 1000.0
        if (dt <= 0) return
        if (dt > 30 || d / dt > 16) {
            anchor(f)
            return
        }
        if (f.speedMps >= 0 && kmh < 2.5) {
            anchor(f)
            return
        }
        if (d >= 3 && d >= 0.35 * f.accuracy) {
            s.distM += d
            s.current?.let { it.distM += d }
            anchor(f)
        }
    }

    private fun anchor(f: DayFix) {
        odoT = f.atMs; odoLat = f.lat; odoLon = f.lon
    }

    /** Duruş adayı biterken tür sırası: sipariş aşaması, AVM, ışık, kaldırım, diğer (MAP.md 11.4). */
    private fun endStop(c: Cand, road: (Double, Double) -> RoadInfo): List<DayEvent> {
        val durS = ((c.lastMs - c.startMs) / 1000).toInt()
        if (durS < STOP_MIN_S) return emptyList()
        val info = road(c.lat, c.lon)
        val inOrder = s.current != null
        val kind: String
        var longWait = false
        when {
            s.phase == OrderPhase.AT_RESTAURANT || s.phase == OrderPhase.READY_FOR_CUSTOMER || s.phase == OrderPhase.AT_CUSTOMER -> kind = "order"
            info.mall -> kind = "mall"
            info.atLight && durS <= LIGHT_MAX_S * 2 -> {
                if (durS > LIGHT_LONG_S) { kind = "light?"; longWait = true } else kind = "light"
            }
            info.surface == StopSurface.SIDEWALK -> kind = "sidewalk"
            else -> kind = "stop"
        }
        val isLight = kind == "light" || kind == "light?"
        val rec = StopRec(
            startMs = c.startMs, endMs = c.lastMs, lat = c.lat, lon = c.lon, kind = kind, onRoad = info.onRoad, junctionM = info.junctionM,
            inOrder = inOrder, lightLat = if (isLight) info.lightLat else null, lightLon = if (isLight) info.lightLon else null,
            surface = info.surface, longWait = longWait,
        )
        s.stops.add(rec)
        return listOf(DayEvent("stop", rec.endMs, mapOf("stop" to rec.toJson())))
    }
}
