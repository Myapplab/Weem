package com.kuryelog.logic.map.day

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

    fun endDay(atMs: Long, road: (Double, Double) -> RoadInfo, reason: String? = null): List<DayEvent> = TODO("A6.8")

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
    fun feed(fix: DayFix, road: (Double, Double) -> RoadInfo): List<DayEvent> = TODO("A6.8")
}
