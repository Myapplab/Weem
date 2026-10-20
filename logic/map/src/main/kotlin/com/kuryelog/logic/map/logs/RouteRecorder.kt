package com.kuryelog.logic.map.logs

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.route.RoutePoint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToLong

/** Rotadan sapma: [maxOffM] en büyük uzaklık, [lenM] sapmada sürülen yol; [endMs] null = sürüyor. */
class RouteDeviation(val startMs: Long, var endMs: Long? = null, var maxOffM: Double = 0.0) {
    var lenM = 0.0
    val points = ArrayList<DoubleArray>() // [lat, lon]

    fun toJson(): JSONObject = JSONObject().apply {
        put("start", startMs)
        endMs?.let { put("end", it) }
        put("m", lenM.roundToLong())
        put("maxOff", maxOffM.roundToLong())
        put("pts", JSONArray().also { a -> points.forEach { p -> a.put(JSONArray().put(p[0]).put(p[1])) } })
    }
}

/**
 * Rota kaydı (`duzyol-route-v1`, MAP.md 12.5): planlanan çizgi, sapmalar, ortalama hız; adres metni yazılmaz. Sapma iki fix sonra açılır, dönünce ya da yeniden
 * hesapta kapanır. Saf: dosya yazımı çağırana aittir.
 */
class RouteRecorder(
    val startedAtMs: Long,
    val mode: String,
    planned: List<RoutePoint>,
    val destination: GeoPoint,
    val plannedM: Double,
    val plannedS: Double,
) {
    private val planList = ArrayList<List<RoutePoint>>().also { it += planned }
    private val planJson = ArrayList<List<DoubleArray>>().also { it += line(planned) }

    /** İlk plan + her yeniden hesaplamanın çizgisi. */
    val plans: List<List<RoutePoint>> get() = planList
    val deviations = ArrayList<RouteDeviation>()
    private val reroutes = ArrayList<Pair<Long, String>>()
    private val track = ArrayList<DoubleArray>() // [t ms, lat, lon, doğruluk]

    var distM = 0.0
        private set
    var movingS = 0.0
        private set
    private var lastAt = 0L
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var hasLast = false
    private var offHits = 0
    private var open: RouteDeviation? = null

    /** Yeni GPS fix'i. [off]: o anki rota çizgisine uzaklık (m); bilinmiyorsa null. */
    fun addFix(atMs: Long, lat: Double, lon: Double, accuracy: Double = 5.0, speedMps: Double = 0.0, off: Double? = null) {
        if (!lat.isFinite() || !lon.isFinite() || !accuracy.isFinite() || accuracy > 35) return
        if (hasLast && atMs <= lastAt) return
        if (hasLast) {
            val d = Geo.haversine(lastLat, lastLon, lat, lon)
            val dt = (atMs - lastAt) / 1000.0
            // Sıçrama ve dururken titreme mesafeye eklenmez.
            if (dt > 0 && dt <= 30 && d / dt <= 16 && d >= max(2.0, accuracy * 0.3)) {
                distM += d
                open?.let { it.lenM += d }
            }
            if (dt > 0 && dt <= 15 && speedMps >= 1.0) movingS += dt
        }
        if (!hasLast || Geo.haversine(lastLat, lastLon, lat, lon) >= 2 || (atMs - lastAt) / 1000 >= 5) {
            track += doubleArrayOf(atMs.toDouble(), r6(lat), r6(lon), (accuracy * 10).roundToLong() / 10.0)
        }
        hasLast = true
        lastAt = atMs
        lastLat = lat
        lastLon = lon
        if (off == null || accuracy > 30) return
        val o = open
        if (o == null) {
            offHits = if (off > OFF_M) offHits + 1 else 0
            if (offHits >= 2) {
                val dev = RouteDeviation(atMs)
                dev.maxOffM = off
                dev.points += doubleArrayOf(r6(lat), r6(lon))
                deviations += dev
                open = dev
                offHits = 0
            }
        } else {
            o.points += doubleArrayOf(r6(lat), r6(lon))
            o.maxOffM = max(o.maxOffM, off)
            if (off <= BACK_M) closeDeviation(atMs)
        }
    }

    private fun closeDeviation(atMs: Long) {
        open?.endMs = atMs
        open = null
    }

    /** Yeni rota çizgisi (sapmadan sonra yeniden hesaplandı): sapma burada biter, çünkü artık yeni plana göre ölçülür. */
    fun replanned(atMs: Long, reason: String, line: List<RoutePoint>) {
        reroutes += atMs to reason
        planList += line
        planJson += line(line)
        closeDeviation(atMs)
        offHits = 0
    }

    /** Rota başından beri ortalama hız (km/s, duruşlar dahil); yeterli veri yoksa null. */
    fun avgKmh(nowMs: Long): Double? {
        val s = (nowMs - startedAtMs) / 1000.0
        return if (s < 20 || distM < 30) null else distM / s * 3.6
    }

    /** Yalnız hareket halindeki ortalama hız (km/s). */
    fun movingKmh(): Double? = if (movingS < 20 || distM < 30) null else distM / movingS * 3.6

    fun toJson(nowMs: Long, reason: String): JSONObject {
        closeDeviation(nowMs)
        val driven = JSONObject().put("m", distM.roundToLong()).put("s", (nowMs - startedAtMs) / 1000).put("movingS", movingS.roundToLong())
        avgKmh(nowMs)?.let { driven.put("avgKmh", (it * 10).roundToLong() / 10.0) }
        movingKmh()?.let { driven.put("movingKmh", (it * 10).roundToLong() / 10.0) }
        return JSONObject().apply {
            put("format", "duzyol-route-v1")
            put("start", startedAtMs)
            put("end", nowMs)
            put("reason", reason)
            put("mode", mode)
            put("to", JSONArray().put(r6(destination.lat)).put(r6(destination.lon)))
            put("planned", JSONObject().put("m", plannedM.roundToLong()).put("s", plannedS.roundToLong()))
            put("driven", driven)
            put("deviations", JSONArray().also { a -> deviations.forEach { a.put(it.toJson()) } })
            put("reroutes", JSONArray().also { a -> reroutes.forEach { (t, why) -> a.put(JSONObject().put("t", t).put("reason", why)) } })
            put("plans", JSONArray().also { a -> planJson.forEach { l -> a.put(JSONArray().also { b -> l.forEach { p -> b.put(JSONArray().put(p[0]).put(p[1])) } }) } })
            put("track", JSONArray().also { a -> track.forEach { t -> a.put(JSONArray().put(t[0].toLong()).put(t[1]).put(t[2]).put(t[3])) } })
        }
    }

    /** Kayıtta en az 2 iz noktası var mı (dosya yazma koşulu, 12.5). */
    val trackSize: Int get() = track.size

    companion object {
        /** Sapma eşiği: rota çizgisinden bu kadar uzak (m). */
        const val OFF_M = 45.0

        /** Sapma bitişi: tekrar bu kadar yakına gelince (titreşmesin diye eşikten düşük). */
        const val BACK_M = 30.0

        private fun r6(v: Double) = (v * 1e6).roundToLong() / 1e6
        private fun line(pts: List<RoutePoint>) = pts.map { doubleArrayOf(r6(it.lat), r6(it.lon)) }
    }
}
