package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import kotlin.math.max

/**
 * Canlı ışık ölçer (Dart `LightTracker`, MAP.md 11.9): bilinen ışıkta kısa (≥3 sn), bilinmeyen kavşakta ≥10 sn bekleme; GPS boşluğu ve sipariş duruşu
 * ([orderStop]) eklenmez; 2 dk üstü `light?` + `longWait`. [sinceMs] süren beklemenin başı.
 */
class LightTracker {
    private var start: DayFix? = null
    private var last: DayFix? = null
    private var firstMoving: DayFix? = null
    private var info = RoadInfo()
    private var checkedMs: Long? = null
    private var moving = 0

    /** Işıkta ≥3 sn beklendiyse bekleme başı; adaylar sayaç göstermez. */
    val sinceMs: Long?
        get() {
            val st = start ?: return null
            val l = last ?: return null
            return if (info.atLight && (l.atMs - st.atMs) / 1000 >= 3) st.atMs else null
        }

    /** Süren ışık beklemesinin ışık yeri (haritada nabızlı işaret). */
    val lightAt: GeoPoint?
        get() = if (sinceMs == null) null else GeoPoint(info.lightLat ?: start!!.lat, info.lightLon ?: start!!.lon)

    fun reset() {
        start = null; last = null; firstMoving = null
        checkedMs = null
        moving = 0
        info = RoadInfo()
    }

    /** Gün sonu: yalnız son geçerli fix zamanına kadar ölçer; son fix'ten ≥25 sn sonra boş. */
    fun finish(atMs: Long, inOrder: Boolean = false): StopRec? {
        val l = last
        val rec = if (l == null || (atMs - l.atMs) / 1000 > 12) null else record(firstMoving?.atMs ?: l.atMs, inOrder)
        reset()
        return rec
    }

    private fun record(endMs: Long, inOrder: Boolean): StopRec? {
        val st = start ?: return null
        val seconds = ((endMs - st.atMs) / 1000).toInt()
        val known = info.atLight
        val candidate = info.known == null && info.allowsTrafficLight && (info.junctionM ?: Double.POSITIVE_INFINITY) <= 45
        // Madde 14: bilinen ışıkta 2 dk'dan uzun duruş ışık değil; kaydedilir, gün sonunda "?" ile sorulur.
        val long = known && seconds > ShiftEngine.LIGHT_LONG_S
        return if (seconds <= 360 && ((known && seconds >= 3) || (candidate && seconds >= 10))) {
            StopRec(
                startMs = st.atMs, endMs = endMs, lat = st.lat, lon = st.lon,
                kind = if (known && !long) "light" else "light?", onRoad = info.onRoad,
                junctionM = info.junctionM, inOrder = inOrder,
                lightLat = info.lightLat, lightLon = info.lightLon, measured = true, surface = info.surface, longWait = long,
            )
        } else {
            null
        }
    }

    /** Bekleme biterse (ilk hareket fix'inden sonraki ikinci fix) kaydı döner. */
    fun feed(fix: DayFix, road: (Double, Double) -> RoadInfo, orderStop: Boolean = false, inOrder: Boolean = false): StopRec? {
        if (orderStop) { reset(); return null }
        val l0 = last
        if (l0 != null && fix.atMs <= l0.atMs) return null
        if (fix.accuracy > 35 || fix.accuracy < 0 || !fix.accuracy.isFinite()) {
            if (l0 != null && (fix.atMs - l0.atMs) / 1000 > 12) reset()
            return null
        }
        if (l0 != null && (fix.atMs - l0.atMs) / 1000 > 12) reset()
        last = fix
        val st0 = start
        if (st0 == null) {
            if (fix.speedMps >= 0 && fix.speedMps <= 1.1) {
                start = fix
                info = road(fix.lat, fix.lon)
                checkedMs = fix.atMs
            }
            return null
        }
        val ck = checkedMs
        if (ck == null || (fix.atMs - ck) / 1000 >= 5) {
            info = road(st0.lat, st0.lon)
            checkedMs = fix.atMs
        }
        val movedAway = Geo.haversine(st0.lat, st0.lon, fix.lat, fix.lon) > max(35.0, fix.accuracy * 2)
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
}

/** Gün sonu ışık incelemesinin öğrettiği (`lightReviewTeaches`): uzun duruşta Reddet ışığı silmez (null); aday iki yönde öğretir. */
object LightReview {
    fun teaches(stop: StopRec, isLight: Boolean): Boolean? = if (stop.longWait && !isLight) null else isLight
}
