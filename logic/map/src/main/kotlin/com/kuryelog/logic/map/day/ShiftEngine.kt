package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.GeoPoint

/**
 * Gün motoru (Dart `ShiftEngine`, MAP.md 11.3–11.6): km, hareket süresi, duruşlar ve siparişin aşamaları; aşamayı yalnız ekran okuması ilerletir
 * ([stageFromFeed], [deliverFromFeed]), motor varış/alış/teslim kararı vermez ve soru sormaz. Zaman epoch ms. İskelet (A6.2b): gövdeler A6.8'de.
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
    }

    fun startDay(atMs: Long, reason: String? = null): List<DayEvent> = TODO("A6.8")
    fun endDay(atMs: Long, road: (Double, Double) -> RoadInfo, reason: String? = null): List<DayEvent> = TODO("A6.8")

    /** Gün açık değilse hiçbir şey yapmaz (sipariş günü kendiliğinden başlatmaz). */
    fun startOrder(atMs: Long): List<DayEvent> = TODO("A6.8")
    fun cancelOrder(atMs: Long): List<DayEvent> = TODO("A6.8")

    /** Navigasyonun hedef bağı: sipariş açmaz; açık siparişin restoran/müşteri hedefini yazar (aşamayı geri sarmaz). */
    fun setDestination(point: GeoPoint, atMs: Long, restaurant: Boolean, name: String? = null): List<DayEvent> = TODO("A6.8")

    /** Ekran okumasından aşama: saat [atMs] (son uygulanan ile şimdi arasına kırpılır). */
    fun stageFromFeed(phase: OrderPhase, atMs: Long): List<DayEvent> = TODO("A6.8")
    fun deliverFromFeed(atMs: Long): List<DayEvent> = TODO("A6.8")

    /** Bir konum okuması: km, hareket süresi, duruş. Eski ya da geriye giden zaman, 41° sıçraması, titreme km eklemez. */
    fun feed(fix: DayFix, road: (Double, Double) -> RoadInfo): List<DayEvent> = TODO("A6.8")
}
