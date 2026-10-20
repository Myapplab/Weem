package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint

/**
 * Canlı ışık ölçer (Dart `LightTracker`, MAP.md 11.9): bilinen ışıkta kısa (≥3 sn), bilinmeyen kavşakta ≥10 sn bekleme; GPS boşluğu ve sipariş duruşu
 * ([feed] `orderStop`) eklenmez; 2 dk üstü `light?` + `longWait`. [sinceMs] süren beklemenin başı. Hareketin ilk fix'i bitiş sayılır, ikinci fix yalnız hareketi doğrular.
 */
class LightTracker {
    private var start: DayFix? = null
    private var last: DayFix? = null
    private var firstMoving: DayFix? = null
    private var info = RoadInfo()
    private var checkedMs: Long? = null
    private var moving = 0

    /** Süren ışık beklemesinin başı (bilinen ışıkta ve ≥3 sn); adaylar sayaç göstermez. */
    val sinceMs: Long?
        get() {
            val s = start ?: return null
            val l = last ?: return null
            return if (info.atLight && l.atMs - s.atMs >= 3000) s.atMs else null
        }

    /** Süren beklemenin ışık yeri (haritada nabızlı işaret). */
    val lightAt: GeoPoint?
        get() {
            val s = start ?: return null
            if (sinceMs == null) return null
            return GeoPoint(info.lightLat ?: s.lat, info.lightLon ?: s.lon)
        }

    /** Bekleme biterse (ilk hareket fix'inden sonraki ikinci fix) kaydı döner. */
    fun feed(fix: DayFix, road: (Double, Double) -> RoadInfo, orderStop: Boolean = false, inOrder: Boolean = false): StopRec? {
        if (orderStop) {
            reset()
            return null
        }
        val l = last
        if (l != null && fix.atMs <= l.atMs) return null
        if (!fix.accuracy.isFinite() || fix.accuracy < 0 || fix.accuracy > 35) {
            if (l != null && fix.atMs - l.atMs > GAP_MS) reset()
            return null
        }
        if (l != null && fix.atMs - l.atMs > GAP_MS) reset()
        last = fix
        if (start == null) {
            if (fix.speedMps < 0 || fix.speedMps > 1.1) return null
            start = fix
            info = road(fix.lat, fix.lon)
            checkedMs = fix.atMs
            return null
        }
        val s = start!!
        if (checkedMs == null || fix.atMs - checkedMs!! >= 5000) {
            info = road(s.lat, s.lon)
            checkedMs = fix.atMs
        }
        val movedAway = Geo.haversine(s.lat, s.lon, fix.lat, fix.lon) > maxOf(35.0, 2 * fix.accuracy)
        if (fix.speedMps >= 1.6 || movedAway) {
            if (firstMoving == null) firstMoving = fix
            moving++
        } else {
            moving = 0
            firstMoving = null
        }
        if (moving < 2) return null
        val rec = record(firstMoving!!.atMs, inOrder)
        reset()
        return rec
    }

    /** Gün sonu: yalnız son geçerli fix zamanına kadar ölçer; son fix'ten >12 sn sonra boş. */
    fun finish(atMs: Long, inOrder: Boolean = false): StopRec? {
        val l = last
        if (l == null || atMs - l.atMs > GAP_MS) {
            reset()
            return null
        }
        val rec = record(firstMoving?.atMs ?: l.atMs, inOrder)
        reset()
        return rec
    }

    fun reset() {
        start = null; last = null; firstMoving = null; info = RoadInfo(); checkedMs = null; moving = 0
    }

    private fun record(endMs: Long, inOrder: Boolean): StopRec? {
        val s = start ?: return null
        val sec = ((endMs - s.atMs) / 1000).toInt()
        val known = info.atLight
        val candidate = info.known == null && info.allowsTrafficLight && (info.junctionM ?: Double.POSITIVE_INFINITY) <= 45
        val long = known && sec > ShiftEngine.LIGHT_LONG_S
        if (!(sec <= ShiftEngine.LIGHT_MAX_S * 2 && ((known && sec >= 3) || (candidate && sec >= 10)))) return null
        return StopRec(
            startMs = s.atMs, endMs = endMs, lat = s.lat, lon = s.lon, kind = if (known && !long) "light" else "light?",
            onRoad = info.onRoad, junctionM = info.junctionM, inOrder = inOrder, lightLat = info.lightLat, lightLon = info.lightLon,
            measured = true, surface = info.surface, longWait = long,
        )
    }

    private companion object {
        const val GAP_MS = 12_000L
    }
}

/** Gün sonu ışık incelemesinin öğrettiği (`lightReviewTeaches`): uzun duruşta Reddet ışığı silmez (null); aday iki yönde öğretir. */
object LightReview {
    fun teaches(stop: StopRec, isLight: Boolean): Boolean? = if (stop.longWait && !isLight) null else isLight
}
