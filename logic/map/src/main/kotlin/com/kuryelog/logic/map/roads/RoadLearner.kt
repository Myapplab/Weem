package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.geo.Geo

/** Öğrenme girdisi: her GPS fix'i (yol eşleşmesiyle). [tMs] epoch ms. */
class LearnFix(
    val tMs: Long,
    val lat: Double,
    val lon: Double,
    val kmh: Double,
    val accuracy: Double = 5.0,
    /** Eşleşen sokak (yoksa null). */
    val way: Long? = null,
    val name: String = "",
    /** Tek yönün tersine gidiyor. */
    val wrongWay: Boolean = false,
    /** Sokak "iki yönlü onaylı". */
    val approved: Boolean = false,
    /** Son 1 sn titreşim RMS (m/s²). */
    val vib: Double = 0.0,
    /** Restoran / müşteri / hedef yakını: durmalar sayılmaz. */
    val excludeStops: Boolean = false,
    /** Ana cadde: ters yön onayı verilemez. */
    val mainRoad: Boolean = false,
)

/** Ters yönde kesintisiz bir sürüşün bir sokak parçası. */
class WwRunWay(var name: String, val main: Boolean) {
    var meters = 0.0
}

/** Ters yönde kesintisiz sürüş: içindeki bütün sokak parçaları, onaylı olsun olmasın (sürüş sırasıyla). */
class WwRun(val startMs: Long) {
    var lastMs = startMs
    var lastWay: Long? = null
    val ways = LinkedHashMap<Long, WwRunWay>()

    internal fun extend(way: Long, name: String, meters: Double, tMs: Long, main: Boolean) {
        val w = ways.getOrPut(way) { WwRunWay(name, main) }
        if (w.name.isEmpty() && name.isNotEmpty()) w.name = name
        w.meters += meters
        lastMs = tMs
        lastWay = way
    }
}

/** Onaylı sokakta biten bir ters yön geçişi. */
class WwPass(
    val way: Long,
    val name: String,
    val meters: Double,
    val incidents: Int,
    val endMs: Long,
    val reported: Boolean = false,
    /** Geçişin içinde olduğu kesintisiz ters yön sürüşü (yoksa null). */
    val run: WwRun? = null,
)

private class WwSession(val way: Long, val name: String, val startMs: Long) {
    var meters = 0.0
    var incidents = 0
    var lastIncidentMs: Long? = null
    var reported = false
}

/**
 * Öğrenme: her GPS fix'i (yol eşleşmesiyle) → sokak istatistiği, dur-kalk, kesintisiz ters yön sürüşü, onaylı sokakta ters yön geçişi
 * (`road_knowledge.dart:365-593` birebir; MAP.md 9.3.2). Süreler Dart `Duration.inSeconds` gibi tam saniyeye kesilir.
 */
class RoadLearner(val k: RoadKnowledge) {
    private var lastT: Long? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastKmh = 0.0
    private var way: Long? = null
    private var passM = 0.0

    private var moving = false
    private var slowSince: Long? = null
    private var slowed = false
    private var slowWay: Long? = null
    private var stopCounted = false
    private var stopWay: Long? = null

    private var session: WwSession? = null
    private var wwOff = 0
    private var lastRun: WwRun? = null

    /** Son kesintisiz ters yön sürüşü (bitmiş olabilir). */
    val run: WwRun? get() = lastRun

    /** Onaylı sokakta süren ters yön geçişi ("Sorun yaşadım" düğmesi). */
    val liveWw: LiveWw? get() = session?.let { LiveWw(it.way, it.name, it.meters) }

    /** Süren geçişe "sorun vardı" işaretle (geçiş bitince ayrıca sorulmaz). */
    fun markLiveReported(): Boolean {
        val s = session ?: return false
        s.reported = true
        return true
    }

    /** Her fix'te çağrılır; biten ters yön geçişlerini döner (sayaçlar işlenmiş olarak). */
    fun feed(f: LearnFix): List<WwPass> {
        if (f.accuracy > 25) return emptyList()
        val out = ArrayList<WwPass>()

        var d = 0.0
        var dt = 0.0
        val lt = lastT
        val la = lastLat
        val lo = lastLon
        if (lt != null && la != null && lo != null) {
            dt = (f.tMs - lt) / 1000.0
            if (dt > 0 && dt <= 5) {
                d = Geo.haversine(la, lo, f.lat, f.lon)
                if (d > 100) d = 0.0 // GPS sıçraması
            } else {
                dt = 0.0
            }
        }
        val prevKmh = lastKmh
        lastT = f.tMs
        lastLat = f.lat
        lastLon = f.lon
        lastKmh = f.kmh

        // 1) Sokak istatistiği
        val w = f.way
        if (w != way) {
            val old = way
            if (old != null && passM >= 20) k.stats(old).passes++
            way = w
            passM = 0.0
        }
        if (w != null) {
            if (f.name.isNotEmpty() && k.names[w] != f.name) k.names[w] = f.name
            if (d > 0) {
                val st = k.stats(w)
                st.m += d
                st.s += dt
                st.lastAt = f.tMs
                passM += d
                if (f.kmh >= 10 && f.vib > 0) {
                    st.vibSum += f.vib
                    st.vibN++
                }
            }
        }

        // 2) Dur-kalk
        stopAndGo(f)

        // 3) Ters yön (yalnız onaylı sokakta sayılır; kesintisiz sürüş hepsinde izlenir)
        trackRun(f, d)
        var s = session
        if (f.wrongWay && f.approved && w != null) {
            if (s != null && s.way != w) {
                endWw()?.let { out += it }
                s = null
            }
            if (s == null) {
                s = WwSession(w, f.name, f.tMs)
                session = s
            }
            wwOff = 0
            s.meters += d
            incident(s, f, prevKmh, dt)
        } else if (s != null) {
            if (w == s.way && f.kmh < SLOW_KMH) {
                // Ters yönde yavaşladı / durdu: eşleştirici hareketsizken yön veremez, geçiş sürer (durma da olaydır).
                wwOff = 0
                s.meters += d
                incident(s, f, prevKmh, dt)
            } else {
                wwOff++
                val leftWay = w != null && w != s.way
                if (wwOff >= 3 || leftWay) endWw()?.let { out += it }
            }
        }
        return out
    }

    /** Ters yönde sert fren (≤2,5 sn'de ≥9 km/s kayıp) ya da durma → olay (5 sn'de bir). */
    private fun incident(s: WwSession, f: LearnFix, prevKmh: Double, dt: Double) {
        val last = s.lastIncidentMs
        val cool = last == null || secs(f.tMs - last) >= 5
        val braked = dt > 0 && dt <= 2.5 && prevKmh - f.kmh >= BRAKE_KMH
        val stoppedNow = f.kmh < STOP_KMH && prevKmh >= STOP_KMH
        if ((braked || stoppedNow) && cool) {
            s.incidents++
            s.lastIncidentMs = f.tMs
        }
    }

    /** Kesintisiz ters yön sürüşü: ters yöndeki her fix; ters yönde yavaşlayan/duran fix aynı parçada sürer. */
    private fun trackRun(f: LearnFix, d: Double) {
        val w = f.way ?: return
        var r = lastRun
        if (r != null && secs(f.tMs - r.lastMs) > RUN_GAP_S) r = null // önceki sürüş bitti
        if (f.wrongWay) {
            (r ?: WwRun(f.tMs).also { lastRun = it }).extend(w, f.name, d, f.tMs, f.mainRoad)
        } else if (r != null && w == r.lastWay && f.kmh < SLOW_KMH) {
            r.extend(w, f.name, d, f.tMs, f.mainRoad)
        }
    }

    /** Süren geçişi kapatır (uygulama kapanırken / gün biterken). */
    fun flush(): WwPass? = endWw()

    private fun endWw(): WwPass? {
        val s = session
        session = null
        wwOff = 0
        if (s == null || s.meters < WW_MIN_M) return null
        val end = lastT ?: s.startMs
        val st = k.wwStats(s.way)
        st.passes++
        st.lastAt = end
        if (s.reported) st.problems += 1
        val r = lastRun
        return WwPass(s.way, s.name, s.meters, s.incidents, end, s.reported, if (r != null && r.ways.containsKey(s.way)) r else null)
    }

    private fun stopAndGo(f: LearnFix) {
        val kmh = f.kmh
        if (kmh >= MOVING_KMH) {
            // Durmadan yavaşlayıp devam etti → yarım dur-kalk
            val sw = slowWay
            if (slowed && !stopCounted && sw != null && !f.excludeStops) k.stats(sw).stops += 0.5
            moving = true
            resetSlow()
            return
        }
        if (!moving || kmh >= SLOW_KMH) return

        val since = slowSince ?: f.tMs.also { slowSince = it }
        if (!slowed) {
            slowed = true
            slowWay = f.way ?: way
        }
        val dur = secs(f.tMs - since)
        if (kmh < STOP_KMH && dur >= STOP_MIN_S && !stopCounted) {
            stopCounted = true
            val w = f.way ?: slowWay
            if (w != null && !f.excludeStops) {
                k.stats(w).stops += 1
                stopWay = w
            }
        }
        if (dur > STOP_MAX_S) {
            // Uzun bekleme dur-kalk değil (park, teslimat) → sayılanı geri al
            stopWay?.let { sw ->
                val st = k.stats(sw)
                st.stops = maxOf(0.0, st.stops - 1)
            }
            moving = false
            resetSlow()
        }
    }

    private fun resetSlow() {
        slowSince = null
        slowed = false
        slowWay = null
        stopCounted = false
        stopWay = null
    }

    class LiveWw(val way: Long, val name: String, val meters: Double)

    companion object {
        const val MOVING_KMH = 12.0
        const val SLOW_KMH = 8.0
        const val STOP_KMH = 5.0
        const val STOP_MIN_S = 3
        const val STOP_MAX_S = 150
        const val WW_MIN_M = 25.0
        const val BRAKE_KMH = 9.0
        const val RUN_GAP_S = 10

        /** Dart `Duration.inSeconds`: sıfıra doğru kesme. */
        private fun secs(ms: Long): Long = ms / 1000

        /**
         * "Hayır" cevabında onaylanacak sokaklar: geçişin sokağı ve aynı kesintisiz sürüşte ters yönde sürülen parçalar (en az
         * [WW_MIN_M] ya da geçişle aynı adlı). Ana cadde onaylanmaz. way → ad.
         */
        fun wwCleanApprovals(p: WwPass): Map<Long, String> {
            val out = linkedMapOf(p.way to p.name)
            val r = p.run ?: return out
            for ((id, w) in r.ways) {
                if (w.main || id in out) continue
                if (w.meters >= WW_MIN_M || (w.name.isNotEmpty() && w.name == p.name)) out[id] = w.name
            }
            return out
        }
    }
}
