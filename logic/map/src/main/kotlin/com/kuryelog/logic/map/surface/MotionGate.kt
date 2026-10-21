package com.kuryelog.logic.map.surface

enum class MeasurementState { CLOSED, RIDING, STOPPED, WALKING, UNCERTAIN, MANUAL_PAUSED }

/** Bir saniyelik hareket kanıtı; [at] önyüklemeden beri monoton ms. */
data class MotionEvidence(
    val at: Long, val speed: Double, val accuracy: Double, val displacementSpeed: Double,
    val speedAccuracy: Double? = null, val orientationReliable: Boolean = false,
    val mountAngle: Double = 0.0, val steps: Int = 0, val walkConfidence: Int = 0,
    val activityAt: Long? = null, val activityAvailable: Boolean = false, val sensorReliable: Boolean = true,
    /** 1.7.2: canlı scooter tekerlek hızı (km/s); bağlı değilse null. */
    val scooterKmh: Double? = null,
)

data class MotionDecision(val state: MeasurementState, val at: Long, val reason: String, val confidence: Double, val excludeSince: Long? = null) {
    val accepts get() = state == MeasurementState.RIDING
}

/**
 * Hareket çıkarımı yalnız yüzey verisini denetler; hiçbir siparişi ilerletmez (YolGo `surface/motion_gate.dart`). Yüksek titreşim/sarsıntı
 * yürüme ya da tutucunun çıkması kanıtı sayılmaz. 1.7.2 (kullanıcı): yalnız scooterla yolda giderken ölçülür; scooter hızının üstü
 * (taksi, otobüs) ve scooter bağlıyken tekerlekler dururken telefonun hareketi ölçülmez.
 */
class MotionGate {
    companion object {
        /** Scooter bu hızı geçmez (G3 en çok ~35 km/s); üstü başka araç sayılır. */
        const val MAX_SCOOTER_MPS = 40 / 3.6
        /** Scooter bağlıyken bunun altındaki tekerlek hızı: itiliyor, taşınıyor ya da başka araçtasın. */
        const val WHEELS_STILL_KMH = 4.0
    }

    var manualPaused = false
    var leg: String? = null
        private set
    private var lastAt = -1L
    private var movingSince = -1L
    private var walkingSince = -1L
    private var mountSince = -1L
    private var lastRiding = -1L
    var decision = MotionDecision(MeasurementState.CLOSED, 0, "day_closed", 0.0)
        private set

    fun pause(value: Boolean) { manualPaused = value; movingSince = -1 }
    fun reset() { lastAt = -1; movingSince = -1; walkingSince = -1; mountSince = -1; lastRiding = -1 }

    fun feed(e: MotionEvidence, dayActive: Boolean, leg: String?, eligiblePhase: Boolean): MotionDecision {
        fun result(state: MeasurementState, reason: String, confidence: Double, excludeSince: Long? = null): MotionDecision {
            decision = MotionDecision(state, e.at, reason, confidence, excludeSince); return decision
        }
        if (!dayActive || !eligiblePhase || leg == null) {
            this.leg = leg; reset()
            return result(MeasurementState.CLOSED, if (!dayActive) "day_closed" else "order_phase_closed", 0.0, e.at)
        }
        if (this.leg != leg) { reset(); this.leg = leg }
        if (manualPaused) return result(MeasurementState.MANUAL_PAUSED, "manual_pause", 0.0, e.at)
        if (e.at <= lastAt) return decision
        if (lastAt >= 0 && e.at - lastAt > 5000) reset()
        lastAt = e.at
        if (!e.sensorReliable || !e.orientationReliable || !e.accuracy.isFinite() || e.accuracy < 0 || e.accuracy > 25 ||
            !e.speed.isFinite() || e.speed < 0 || (e.speedAccuracy != null && e.speedAccuracy > 1.5)) {
            movingSince = -1
            return result(MeasurementState.UNCERTAIN, "sensor_or_gps_uncertain", 0.0)
        }
        if (e.speed > MAX_SCOOTER_MPS) {
            movingSince = -1
            return result(MeasurementState.UNCERTAIN, "faster_than_scooter", 0.2)
        }
        val wheels = e.scooterKmh
        if (wheels != null && wheels.isFinite() && wheels < WHEELS_STILL_KMH && e.speed >= 2.2) {
            movingSince = -1
            return result(MeasurementState.UNCERTAIN, "scooter_wheels_still", 0.3)
        }
        val freshActivity = e.activityAt != null && e.at - e.activityAt >= 0 && e.at - e.activityAt <= 10000
        val footFamily = freshActivity && e.walkConfidence >= 75
        val repeatedSteps = e.steps >= 4
        mountSince = if (e.mountAngle > 25) (if (mountSince < 0) e.at else mountSince) else -1
        val persistentMount = mountSince >= 0 && e.at - mountSince >= 2500
        // Steps and ON_FOOT/WALKING are correlated. They form one walking family, backed by sustained mount change or slow, consistent displacement.
        val slowDisplacement = e.displacementSpeed >= 0.25 && e.displacementSpeed <= 2.0 && e.speed < 2.2
        val walkingEvidence = (footFamily || repeatedSteps) && (persistentMount || slowDisplacement)
        walkingSince = if (walkingEvidence) (if (walkingSince < 0) e.at else walkingSince) else -1
        if (walkingSince >= 0 && e.at - walkingSince >= 1500) {
            movingSince = -1
            val onset = if (freshActivity && footFamily) minOf(walkingSince, e.activityAt!!) else walkingSince
            return result(MeasurementState.WALKING, "walking_or_carried", 0.8, onset)
        }
        if (persistentMount && repeatedSteps) {
            movingSince = -1
            return result(MeasurementState.WALKING, "mount_changed_with_steps", 0.8, mountSince)
        }
        if (e.speed < 0.5 && e.displacementSpeed < 0.6) {
            movingSince = -1
            return result(MeasurementState.STOPPED, "trip_paused", 0.9)
        }
        val history = lastRiding >= 0 && e.at - lastRiding < 30000
        val consistent = (e.speed >= 2.2 && e.displacementSpeed >= 1.2) || (history && e.speed >= 0.7 && e.displacementSpeed >= 0.4)
        if (!consistent || footFamily || repeatedSteps || persistentMount) {
            movingSince = -1
            return result(MeasurementState.UNCERTAIN, "motion_unconfirmed", 0.2)
        }
        if (movingSince < 0) movingSince = e.at
        if (e.at - movingSince < (if (history) 1000 else 2500)) return result(MeasurementState.UNCERTAIN, "confirming_motion", 0.3)
        lastRiding = e.at
        return result(MeasurementState.RIDING, if (e.activityAvailable) "motion_confirmed" else "limited_motion_evidence", if (e.activityAvailable) 0.9 else 0.65)
    }
}

/** Sekiz saniye gecikmeli tampon: geç gelen yürüme/varış sınırı son pencereleri atar, onaylananlar [commitBefore] ile çıkar. */
class BufferedSurface<T>(val delayMs: Long = 8000) {
    private class Item<T>(val id: String, val start: Long, val end: Long, val value: T)

    private val pending = ArrayList<Item<T>>()
    private val ids = HashSet<String>()

    fun add(id: String, start: Long, end: Long, value: T): Boolean {
        if (end <= start || !ids.add(id)) return false
        pending += Item(id, start, end, value); return true
    }

    fun excludeSince(at: Long): List<T> {
        val removed = pending.filter { it.end >= at }.map { it.value }
        pending.removeAll { it.end >= at }; return removed
    }

    fun removeWhere(matches: (T) -> Boolean): List<T> {
        val removed = pending.filter { matches(it.value) }
        pending.removeAll { matches(it.value) }
        removed.forEach { ids.remove(it.id) }
        return removed.map { it.value }
    }

    fun commitBefore(now: Long): List<T> {
        val ready = pending.filter { it.end <= now - delayMs }
        pending.removeAll { it.end <= now - delayMs }
        // Dedup memory is bounded; committed observation IDs also persist in storage.
        if (ids.size > 1000) { ids.clear(); ids.addAll(pending.map { it.id }) }
        return ready.map { it.value }
    }

    fun clear() { pending.clear(); ids.clear() }
    val length get() = pending.size
}

/**
 * Kısa duraklama ya da GPS titremesi yeni geçiş değildir: tekrar ziyaret, kesim dışında kabul edilmiş sürüş mesafesi ve en az 15 sn ara ister
 * (YolGo `surface/pass_tracker.dart`).
 */
class SurfacePassTracker {
    private class Visit(val index: Int, val distance: Double, val at: Long)

    private var leg: String? = null
    private var distance = 0.0
    private val visits = HashMap<String, Visit>()

    fun pass(leg: String, segment: String, at: Long, meters: Double, gridM: Double): String {
        if (this.leg != leg) { this.leg = leg; distance = 0.0; visits.clear() }
        val old = visits[segment]
        var index = old?.index ?: 0
        if (old != null && at - old.at >= 15000 && distance - old.distance >= gridM * 2) index++
        distance += meters; visits[segment] = Visit(index, distance, at)
        return "$leg:pass$index"
    }
}
