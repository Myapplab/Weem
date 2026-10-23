package com.kuryelog.logic.map.ui

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.CompassSmoother
import kotlin.math.abs
import kotlin.math.exp

/**
 * Konum işaretinin yönü (S38, MAP.md 6.4, 7.6): hareketteyken çapa GPS yönüdür ([anchor]); iki okuma arasında pusulanın o okumadan beri dönüşü eklenir
 * (gidon dönünce ok hemen döner; en çok ±[MAX_TURN_MOVING_DEG]). Durunca çapa [ANCHOR_KEEP_MS] boyunca kalır (ışıkta beklerken yön sıçramaz, scooter'ın
 * pusulayı saptırması yön değiştirmez), sonra yalnız pusula; pusula yoksa filtreli GPS yönü. Hiçbiri yoksa yön yok (yalnız nokta).
 */
class PuckHeading {
    private var anchorCourse: Double? = null
    private var anchorCompass: Double? = null
    private var anchorAtMs = 0L

    /** GPS okuması: [course] hareket yönü (dururken null), [compass] o anki pusula. */
    fun onFix(course: Double?, compass: Double?, nowMs: Long) {
        if (course == null) return
        anchorCourse = course
        anchorCompass = compass
        anchorAtMs = nowMs
    }

    /** Şimdiki yön; [moving] son okuma hareketli mi, [compass] anlık pusula, [fallback] filtreli GPS yönü. */
    fun current(moving: Boolean, compass: Double?, fallback: Double?, nowMs: Long): Double? {
        val c = anchorCourse
        if (c != null && (moving || nowMs - anchorAtMs <= ANCHOR_KEEP_MS)) {
            val a = anchorCompass
            if (compass == null || a == null) return c
            val turn = Geo.angleDelta(compass, a)
            return Geo.norm360(c + if (moving) turn.coerceIn(-MAX_TURN_MOVING_DEG, MAX_TURN_MOVING_DEG) else turn)
        }
        return compass ?: fallback
    }

    companion object {
        /** Durduktan sonra GPS çapası bu kadar korunur (ms). */
        const val ANCHOR_KEEP_MS = 60_000L
        /** Hareketteyken iki okuma arasında pusulanın ekleyebileceği en büyük dönüş. */
        const val MAX_TURN_MOVING_DEG = 45.0
    }
}

/**
 * Konum işaretinin ekrandaki hâli (S38; YolGo `map_view.dart` `_onFix` kayması): her GPS adımı ([PuckStep]) [PuckStep.slideMs] boyunca doğrusal kayar, yön
 * gerçek zamanlıdır ([PuckHeading], pusula ~30 Hz). Çağıran yalnız değişiklik varken kare ister ([busy]); [frame] değişiklik yoksa null döner (yüzey yeniden
 * çizilmez, pil). Dururken (yön yok) [STILL_HOLD_M]'den küçük GPS titremesi işareti oynatmaz.
 */
class PuckAnimator {
    /** [heading] çizim ve kamera yönü; [moving] ok gösterilebilir (son okuma hareketli). */
    data class Frame(val point: GeoPoint, val heading: Double?, val moving: Boolean, val turning: Boolean, val accuracyM: Double?, val raw: GeoPoint)

    private val compass = CompassSmoother()
    private val fusion = PuckHeading()
    private var from: GeoPoint? = null
    private var to: GeoPoint? = null
    private var raw: GeoPoint? = null
    private var slideStartMs = 0L
    private var slideMs = 0L
    private var shownPoint: GeoPoint? = null
    private var shownHeading: Double? = null
    private var moving = false
    private var turning = false
    private var accuracyM: Double? = null
    private var gpsHeading: Double? = null
    private var lastFrameMs: Long? = null
    private var last: Frame? = null

    /** İşaretin şu anki çizim yeri (kayma ortasında olabilir): `PuckMotion.onFix`'in `displayed` girdisi. */
    val displayed: GeoPoint? get() = shownPoint

    /** Pusulanın yumuşatılmış değeri (yoksa null). */
    val compassDeg: Double? get() = compass.value

    /** GPS adımı. [gpsHeadingDeg] `PuckMotion.heading` (filtreli GPS yönü), [rawPoint] ham konum. */
    fun onStep(step: PuckStep, accuracy: Double?, gpsHeadingDeg: Double?, rawPoint: GeoPoint, nowMs: Long) {
        val shown = shownPoint
        when {
            shown == null || step.jump -> {
                from = step.to; to = step.to; slideMs = 0
                shownPoint = step.to
            }
            step.course == null && Geo.haversine(shown.lat, shown.lon, step.to.lat, step.to.lon) < STILL_HOLD_M -> {
                // Dururken titreme: işaret yerinde kalır (kayma yok, kare yok).
                from = shown; to = shown; slideMs = 0
            }
            else -> {
                from = shown; to = step.to; slideStartMs = nowMs; slideMs = step.slideMs
            }
        }
        moving = step.course != null
        turning = step.turning
        accuracyM = accuracy
        gpsHeading = gpsHeadingDeg
        raw = rawPoint
        fusion.onFix(step.course, compass.value, nowMs)
    }

    /** Ham pusula (derece, gerçek kuzey); sayı değilse yok sayılır. */
    fun onCompass(deg: Double) {
        compass.update(deg)
    }

    /** Pusula kapandı (sensör yok ya da durdu). */
    fun compassOff() {
        compass.reset()
    }

    private fun target(nowMs: Long): Double? = fusion.current(moving, compass.value, gpsHeading, nowMs)

    /** Kare gerekiyor mu: işaret kayıyor ya da yön hedefe varmadı. Konum yokken hiç (pusula dönse de çizilecek işaret yok). */
    fun busy(nowMs: Long): Boolean {
        if (from == null) return false
        if (slideMs > 0 && nowMs - slideStartMs < slideMs) return true
        val t = target(nowMs) ?: return false
        val h = shownHeading ?: return true
        return abs(Geo.angleDelta(t, h)) > HEADING_DEADBAND_DEG
    }

    /** [nowMs] anının çizimi; öncekiyle aynıysa null. */
    fun frame(nowMs: Long): Frame? {
        val a = from ?: return null
        val b = to ?: return null
        val k = if (slideMs <= 0) 1.0 else ((nowMs - slideStartMs).toDouble() / slideMs).coerceIn(0.0, 1.0)
        val p = if (k >= 1.0) b else GeoPoint(a.lat + (b.lat - a.lat) * k, a.lon + (b.lon - a.lon) * k)
        shownPoint = p
        if (k >= 1.0) slideMs = 0
        val dt = lastFrameMs?.let { (nowMs - it).coerceIn(0L, MAX_DT_MS) } ?: MAX_DT_MS
        lastFrameMs = nowMs
        val t = target(nowMs)
        val h = shownHeading
        shownHeading = when {
            t == null -> null
            h == null -> t
            else -> {
                val d = Geo.angleDelta(t, h)
                if (abs(d) <= HEADING_DEADBAND_DEG) t
                else Geo.norm360(h + d * (1 - exp(-dt / (if (turning) TAU_TURNING_MS else TAU_MS))))
            }
        }
        val f = Frame(p, shownHeading, moving, turning, accuracyM, raw ?: p)
        if (f == last) return null
        last = f
        return f
    }

    companion object {
        /** Dururken bu kadar metreden küçük GPS oynaması işareti kaydırmaz. */
        const val STILL_HOLD_M = 2.0
        /** Yön bu kadar dereceden yakınsa hedefe oturur (kare istenmez). */
        const val HEADING_DEADBAND_DEG = 0.4
        /** Yönün hedefe yaklaşma zaman sabiti (ms); dönüşte daha çabuk. */
        const val TAU_MS = 90.0
        const val TAU_TURNING_MS = 60.0
        /** Kare aralığı bundan uzunsa bu sayılır (uygulama arkadan dönünce yön bir karede oturur). */
        const val MAX_DT_MS = 200L
        /** Kare temposu (~30 fps): kayma ve dönüş bundan sık çizilmez (pil; S38). */
        const val FRAME_MS = 33L
    }
}

/**
 * Takipte kamera adımı (MAP.md 6.5; S38): yakınlık ve varış odağı hedefe zamana bağlı yaklaşır (kare temposundan bağımsız; YolGo 80 ms'de bir %15 ve %25).
 */
object CameraEase {
    /** [perStep] oranının [stepMs]'lik adımlarla [dtMs] sürede birikmiş karşılığı. */
    fun factor(perStep: Double, stepMs: Long, dtMs: Long): Double = 1 - Math.pow(1 - perStep, dtMs.coerceIn(0L, 1_000L).toDouble() / stepMs)
}
