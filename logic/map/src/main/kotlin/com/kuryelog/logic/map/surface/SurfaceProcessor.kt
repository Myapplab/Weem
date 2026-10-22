package com.kuryelog.logic.map.surface

import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.graph.EdgeHit
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.WayFlag
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Ölçümün anlık görünümü: kapının kararı, tamponda bekleyen ve kaydedilen pencere sayısı. */
data class SurfaceView(val decision: MotionDecision = MotionDecision(MeasurementState.CLOSED, 0, "day_closed", 0.0), val pending: Int = 0, val accepted: Int = 0)

/**
 * Üç bağımsız durumu birleştirir: gerçek navigasyon etabı, sipariş aşaması ve hareket (YolGo `SurfaceController._process/_observation`). Bu sınıf
 * hiçbir siparişi ilerletmez. Satırlar etap ve önyükleme kimliğini taşır; geç gelen bir pencere yeni etaba/güne yazılmaz. Tek iş parçacığından
 * çağrılır ([process] eşzamanlı); depoya yazma bittikten sonra döner.
 *
 * Bilerek fark: YolGo satırları dayanıklı bir günlükten Flutter'a taşırdı (çökmede kalır, onaylanınca silinir); WEEM'de tek süreçte satırlar
 * doğrudan gelir, tampondaki (en çok 8 sn) kayıt çökmede kaybolur.
 */
class SurfaceProcessor(
    private val store: SurfaceStore,
    /** Yol ağı; yoksa (harita yüklenmedi) pencereler atılır. */
    private val graph: () -> RoadGraph?,
    /** Canlı scooter tekerlek hızı (km/s); bağlı değilse null. */
    private val scooterKmh: () -> Double? = { null },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    val gate = MotionGate()
    private val buffer = BufferedSurface<SurfaceObservation>()
    private val passes = SurfacePassTracker()
    private val pendingIds = HashSet<String>()
    private var boot: String? = null
    private var ridingSince = 0L
    private var accepted = 0

    var view = SurfaceView()
        private set

    @Synchronized fun pause(paused: Boolean) { gate.pause(paused) }

    /** Gün bitti/silindi: tamponda bekleyen o güne ait pencereler atılır. */
    @Synchronized fun dropDay(day: String) { buffer.removeWhere { it.day == day }.forEach { pendingIds.remove(it.id) } }

    @Synchronized fun process(rows: List<SurfaceRow>) {
        val committed = ArrayList<SurfaceObservation>()
        val decisions = ArrayList<JSONObject>()
        // Tekerlek hızı yalnız canlı satırlarda (birikmiş eski satırlara bugünkü hız uymaz).
        val wheels = scooterKmh()
        for (row in rows) {
            when (row) {
                is SurfaceRow.Boundary -> {
                    if (row.day in store.deleted) continue
                    val capturedLeg = capture(row.leg, row.boot)
                    // Unconfirmed trailing windows are conservatively discarded on a manual arrival/day boundary, never forced into a surface measurement.
                    if (capturedLeg != null) store.retract(row.day, capturedLeg, row.at, if (row.closed) null else row.at)
                    pendingIds.clear(); buffer.clear(); ridingSince = 0
                }
                is SurfaceRow.Evidence -> {
                    if (row.day in store.deleted) continue
                    val capturedLeg = capture(row.leg, row.boot)
                    if (boot != row.boot) { gate.reset(); boot = row.boot; ridingSince = 0 }
                    gate.manualPaused = row.paused
                    val previous = gate.decision
                    val previousLeg = gate.leg
                    val decision = gate.feed(
                        MotionEvidence(
                            at = row.at, speed = row.speed, accuracy = row.accuracy, displacementSpeed = row.displacementSpeed, speedAccuracy = row.speedAccuracy,
                            orientationReliable = row.orientationReliable, mountAngle = row.mountAngle, steps = row.steps, walkConfidence = row.walkConfidence,
                            activityAt = row.activityAt, activityAvailable = row.activityAvailable, sensorReliable = row.sensorReliable, scooterKmh = wheels,
                        ),
                        dayActive = true, leg = capturedLeg, eligiblePhase = row.eligible,
                    )
                    if (previous.state != decision.state || previous.reason != decision.reason || previousLeg != capturedLeg) {
                        decisions += JSONObject().put("id", "${row.boot}:evidence:${row.at}").put("kind", "decision").put("day", row.day).put("leg", row.leg).put("boot", row.boot)
                            .put("at", row.at).put("state", decision.state.name.lowercase().let { camel(it) }).put("reason", decision.reason).put("confidence", decision.confidence)
                            .put("excludeSince", decision.excludeSince ?: JSONObject.NULL).put("receivedUtc", nowMs())
                    }
                    if (decision.accepts && !previous.accepts) ridingSince = decision.at
                    if (!decision.accepts) {
                        val onset = decision.excludeSince ?: decision.at
                        for (removed in buffer.excludeSince(onset)) pendingIds.remove(removed.id)
                        if (capturedLeg != null && decision.excludeSince != null &&
                            (previous.accepts || previous.state != decision.state || previous.reason != decision.reason || previousLeg != capturedLeg)) {
                            store.retract(row.day, capturedLeg, onset, decision.at)
                        }
                    }
                    val ready = buffer.commitBefore(row.at)
                    committed += ready
                    ready.forEach { pendingIds.remove(it.id) }
                }
                is SurfaceRow.Window -> {
                    if (row.day in store.deleted || row.id in pendingIds) continue
                    val capturedLeg = capture(row.leg, row.boot)
                    val valid = row.valid && gate.decision.accepts && gate.leg == capturedLeg && row.start >= ridingSince
                    val observation = if (valid) observation(row, capturedLeg!!) else null
                    if (observation != null && buffer.add(row.id, observation.startMs, observation.endMs, observation)) pendingIds += row.id
                }
            }
        }
        // Persist first; only then do the buffered rows count as accepted.
        store.add(committed)
        store.recordDecisions(decisions)
        accepted += committed.size
        view = SurfaceView(gate.decision, buffer.length, accepted)
    }

    private fun capture(leg: String?, boot: String): String? = if (leg == null) null else "$leg/$boot"

    private fun camel(lower: String) = when (lower) { "manual_paused" -> "manualPaused"; else -> lower }

    private fun observation(row: SurfaceRow.Window, leg: String): SurfaceObservation? {
        val g = graph() ?: return null
        val accuracy = row.accuracy; val speed = row.speed; val meters = row.meters
        if (accuracy > 20 || accuracy < 0 || speed < .7 || meters <= 0 || meters > 40 || row.fromLat == null || row.fromLon == null) return null
        fun reliable(lat: Double, lon: Double): EdgeHit? {
            val hits = g.nearestEdges(lat, lon, maxDist = min(25.0, max(8.0, accuracy)), limit = 4)
            if (hits.isEmpty()) return null
            val first = hits.first()
            if (Geo.angleDiff(row.bearing, first.segBearing) > 45 && Geo.angleDiff(row.bearing, (first.segBearing + 180) % 360) > 45) return null
            if (hits.drop(1).any { g.osmIdOf(it.edge) != g.osmIdOf(first.edge) && it.dist - first.dist < max(3.0, accuracy * .6) }) return null
            return first
        }
        val end = reliable(row.lat, row.lon)
        val start = reliable(row.fromLat, row.fromLon)
        if (end == null || start == null || end.edge != start.edge) return null
        val flags = g.flagsOf(end.edge)
        if ((flags and (WayFlag.RESTRICTED or WayFlag.SIDEWALK)) != 0 || g.classOf(end.edge).isFootClass) return null
        val profile = row.profile
        val direction = if (Geo.angleDiff(row.bearing, end.segBearing) <= 90) 0 else 1
        // Segments are anchored to the physical edge start coordinates, not mutable graph indices. Windows crossing a segment are excluded, never duplicated.
        val edgeLen = g.edgeLen[end.edge].toDouble()
        val index = Math.floor(end.fraction * edgeLen / profile.segmentM).toInt()
        if (Math.floor(start.fraction * g.edgeLen[start.edge].toDouble() / profile.segmentM).toInt() != index) return null
        val from = g.edgeFrom[end.edge]
        val anchor = SurfaceKey.anchor(g.nodeLat[from], g.nodeLon[from])
        val way = g.osmIdOf(end.edge)
        val segment = SurfaceKey.of(way, anchor, direction, index)
        val rms = row.rms
        val impacts = row.impacts.map { JSONObject().put("id", it.id).put("at", it.at).put("peak", it.peak).put("lat", row.lat).put("lon", row.lon).put("source", "auto_neutral") }
        return SurfaceObservation(
            id = row.id, day = row.day, leg = leg, pass = passes.pass(leg, segment, row.end, meters, profile.segmentM), segment = segment, profile = profile.id, at = row.utc,
            startMs = row.start, endMs = row.end, way = way, direction = direction, segmentIndex = index, profileVersion = profile.version, meters = meters, rms = rms,
            roughM = if (rms >= profile.roughThreshold(speed)) meters else 0.0, speed = speed, accuracy = accuracy, lat = row.lat, lon = row.lon, sampleHz = row.hz,
            confidence = gate.decision.confidence * (1 - accuracy / 40).coerceIn(0.0, 1.0), impacts = impacts, jerk = row.jerk, gyro = row.gyro, bands = row.bands,
            calibrationLabel = row.calibrationLabel, segmentGridM = profile.segmentM, segmentLength = min(profile.segmentM, edgeLen - index * profile.segmentM),
        )
    }
}
