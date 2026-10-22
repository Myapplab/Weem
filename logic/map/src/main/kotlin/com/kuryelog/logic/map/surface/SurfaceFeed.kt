package com.kuryelog.logic.map.surface

import com.kuryelog.logic.map.geo.Geo
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/** Bir GPS konumu (Android `Location` alanları); [elapsedNs] önyüklemeden beri monoton. */
data class SurfaceFix(
    val elapsedNs: Long, val utcMs: Long, val lat: Double, val lon: Double, val altitude: Double, val accuracy: Double, val speed: Double,
    val bearing: Double, val speedAccuracy: Double? = null,
)

/** Hattın o anki ayarı (gün, etap, uygunluk, duraklatma, kalibrasyon etiketi, kurulum profili). */
data class SurfaceFeedConfig(
    val day: String? = null, val leg: String? = null, val eligible: Boolean = false, val paused: Boolean = false,
    val calibrationLabel: String? = null, val profile: SurfaceProfile = SurfaceProfile(),
)

/**
 * Sensör hattı: konum, ivmeölçer, yönelim, jiroskop, adım ve etkinlik tanıma → [SurfaceRow] (YolGo `TelemetryService`'in saf kısmı; Android
 * bağı `:map` `SurfaceSensors`). 100 Hz işleme sensör iş parçacığında yapılır; dışarı yalnız çakışmayan özetler çıkar. Hız bölmesi ve trafik
 * duruşlarının yol kalitesi sayılması yok.
 */
class SurfaceFeed(
    private val boot: String,
    private val emit: (SurfaceRow) -> Unit,
    /** Android `SystemClock.elapsedRealtimeNanos()`. */
    private val elapsedRealtimeNs: () -> Long,
    private val utcNow: () -> Long,
    /** Adım dedektörü ya da etkinlik tanıma kullanılabilir mi (izin + donanım). */
    private val activityAvailable: () -> Boolean = { false },
) {
    /** Ham tanı kaydı (isteğe bağlı, A10.4c): doluysa her ivme örneği ölçüme girmeden önce buraya da verilir; null = kapalı. */
    @Volatile var rawSink: ((RawSample) -> Unit)? = null

    private var config = SurfaceFeedConfig()
    private var signal = SurfaceSignal()
    private var signalSetup = ""
    private var gps: SurfaceFix? = null
    private var up: DoubleArray? = null
    private var orientationAt = 0L
    private var gyro: Double? = null
    private var pressure: Double? = null
    private var mountUp: DoubleArray? = null
    private val steps = ArrayDeque<Long>()
    private var lastWindowEnd = 0L
    private var lastWindowLocation: SurfaceFix? = null
    private var currentLeg: String? = null
    private var lastEvidence = 0L
    private var displacementSpeed = 0.0
    private var stoppedAt = 0L
    private var activityAt = 0L
    private var walkConfidence = 0

    /** Doğruysa Android ivmeölçeri 25 ms'ye (40 Hz) yavaşlatır, jiroskobu 80 ms'ye: sıfır ofseti hazır ve ≥2 sn duruş. */
    var slowSampling = false
        private set

    /** Ölçülen ivme "doğrusal" (yerçekimsiz) sensörden mi geliyor. */
    var linear = false

    /** İvmeölçer kullanılabilir mi (yoksa yavaş örnekleme anlamsız). */
    var accelerationAvailable = false

    val offsetReady get() = signal.offsetReady

    /** Etap/gün değişince eski etap için sınır satırı; kurulum (profil/filtre) değişirse sinyal sıfırlanır, aynı kurulumda ofset korunur. */
    @Synchronized fun configure(next: SurfaceFeedConfig) {
        val old = config
        config = next
        val oldDay = old.day
        if (oldDay != null && (oldDay != next.day || old.leg != next.leg || !next.eligible || next.paused)) {
            emit(SurfaceRow.Boundary(oldDay, old.leg, boot, closed = oldDay != next.day || old.leg != next.leg || !next.eligible, at = elapsedRealtimeNs() / 1_000_000, utc = utcNow()))
        }
        val p = next.profile
        val setup = "${p.id}/${p.highPassHz}/${p.lowPassHz}/${p.impactThreshold}"
        if (!next.eligible || next.paused) { signal.restartWindowPreservingOffset(); return }
        if (setup != signalSetup) { signal = SurfaceSignal(p.highPassHz, p.lowPassHz, p.impactThreshold); signalSetup = setup } else signal.restartWindowPreservingOffset()
        mountUp = p.mount.takeIf { it.size == 3 }?.toDoubleArray()
    }

    /**
     * "Montaj yönünü kaydet": telefon sabit gidonda ve scooter dururken son güvenilir dünya "yukarı" vektörü (YolGo `SurfaceSetupScreen`:
     * yönelim güvenilir, hız < 0,5 m/s, GPS doğruluğu ≤ 20 m); koşul sağlanmıyorsa null.
     */
    @Synchronized fun mountCandidate(): DoubleArray? {
        val u = up ?: return null
        val f = gps ?: return null
        if (elapsedRealtimeNs() - orientationAt >= 500_000_000) return null
        if (f.speed >= .5 || f.accuracy > 20) return null
        return u.copyOf()
    }

    @Synchronized fun setActivity(atMs: Long, confidence: Int) { activityAt = atMs; walkConfidence = confidence }

    @Synchronized fun reset() { signal.reset(); gps = null; lastWindowLocation = null; lastWindowEnd = 0; activityAt = 0; walkConfidence = 0; steps.clear() }

    @Synchronized fun onRotation(worldUp: DoubleArray, timestampNs: Long) { up = worldUp; orientationAt = timestampNs }

    /** Yerçekimi vektörü (yönelim sensörü yoksa): normu 8'den küçükse güvenilmez, atlanır. */
    @Synchronized fun onGravity(values: DoubleArray, timestampNs: Long) {
        val n = sqrt(values.take(3).sumOf { it * it })
        if (n > 8) { up = DoubleArray(3) { values[it] / n }; orientationAt = timestampNs }
    }

    @Synchronized fun onGyro(magnitude: Double) { gyro = magnitude }
    @Synchronized fun onPressure(hPa: Double) { pressure = hPa }
    @Synchronized fun onStep(timestampNs: Long) { steps.addLast(timestampNs / 1_000_000) }

    @Synchronized fun onLocation(fix: SurfaceFix) {
        val day = config.day ?: return
        val old = gps
        if (old != null && fix.elapsedNs <= old.elapsedNs) return
        gps = fix
        displacementSpeed = if (old != null) Geo.haversine(old.lat, old.lon, fix.lat, fix.lon) / max(.01, (fix.elapsedNs - old.elapsedNs) / 1e9) else 0.0
        evidence(day, fix)
    }

    private fun mountAngle(): Double {
        val a = up ?: return 0.0
        val b = mountUp ?: return 0.0
        return acos(a.indices.sumOf { a[it] * b[it] }.coerceIn(-1.0, 1.0)) * 180 / PI
    }

    private fun evidence(day: String, fix: SurfaceFix) {
        val at = fix.elapsedNs / 1_000_000
        if (at - lastEvidence < 900) return
        lastEvidence = at
        if (!accelerationAvailable) { stoppedAt = 0; slowSampling = false }
        else if (fix.speed < .5 && displacementSpeed < .6) { if (stoppedAt == 0L) stoppedAt = at } else stoppedAt = 0
        slowSampling = accelerationAvailable && signal.offsetReady && stoppedAt > 0 && at - stoppedAt >= 2000
        while (steps.isNotEmpty() && steps.first() < at - 5000) steps.removeFirst()
        if (!config.eligible) return // Etap dışında kanıt satırı yok; hareket kapısı kapalı.
        emit(SurfaceRow.Evidence(
            day = day, leg = config.leg, eligible = config.eligible, paused = config.paused, boot = boot, at = at, utc = fix.utcMs, lat = fix.lat, lon = fix.lon,
            altitude = fix.altitude, accuracy = fix.accuracy, speed = fix.speed, bearing = fix.bearing, speedAccuracy = fix.speedAccuracy,
            displacementSpeed = displacementSpeed, steps = steps.size, orientationReliable = up != null && at * 1_000_000 - orientationAt < 500_000_000,
            mountAngle = mountAngle(), activityAt = activityAt.takeIf { it > 0 }, walkConfidence = walkConfidence, activityAvailable = activityAvailable(),
            pressure = pressure, sensorReliable = signal.offsetReady,
        ))
    }

    /** Ivme örneği ([values] m/s², cihaz ekseni); pencere tamamlanınca satır çıkar. */
    @Synchronized fun onAcceleration(timestampNs: Long, values: DoubleArray) {
        val day = config.day ?: return
        val fix = gps ?: return
        val fresh = timestampNs - fix.elapsedNs in 0..3_000_000_000L
        val stationary = fresh && fix.speed < .5 && displacementSpeed < .6
        val orientation = if (timestampNs - orientationAt < 500_000_000) up else null
        rawSink?.takeIf { !config.paused }?.invoke(RawSample(
            day = day, boot = boot, leg = config.leg, atNs = timestampNs, receivedNs = elapsedRealtimeNs(),
            utc = utcNow() - (elapsedRealtimeNs() - timestampNs) / 1_000_000, receivedUtc = utcNow(), linear = linear,
            acceleration = values.copyOf(3), worldUp = orientation?.copyOf(), gyro = gyro, speed = fix.speed, accuracy = fix.accuracy,
            profile = config.profile.id, freshGps = fresh, stationary = stationary, offsetReady = signal.offsetReady,
        ))
        val w = signal.feed(timestampNs, values.copyOf(3), orientation, linear, stationary, fix.speed, gyro) ?: return
        val leg = config.leg
        if (currentLeg != leg) { lastWindowLocation = null; lastWindowEnd = 0; currentLeg = leg }
        val before = lastWindowLocation
        val meters = if (before != null && lastWindowEnd == w.startNs && fresh) Geo.haversine(before.lat, before.lon, fix.lat, fix.lon) else 0.0
        lastWindowEnd = w.endNs; lastWindowLocation = fix
        if (leg == null || !config.eligible || config.paused) return
        val utc = utcNow() - (elapsedRealtimeNs() - w.endNs) / 1_000_000
        emit(SurfaceRow.Window(
            id = "$boot:${w.startNs}", day = day, leg = leg, boot = boot, profile = config.profile, calibrationLabel = config.calibrationLabel,
            start = w.startNs / 1_000_000, end = w.endNs / 1_000_000, utc = utc, lat = fix.lat, lon = fix.lon, fromLat = before?.lat, fromLon = before?.lon,
            bearing = fix.bearing, accuracy = fix.accuracy, speed = fix.speed, meters = meters, rms = w.rms, jerk = w.jerk, gyro = w.gyro, bands = w.bands,
            hz = w.hz, valid = w.valid && fresh && meters > 0 && meters < 40, reason = w.reason, horizontalRms = w.horizontalRms,
            impacts = w.impacts.map { SurfaceRow.Impact("$boot:${it.atNs}", it.atNs / 1_000_000, it.peak) },
        ))
    }
}
