package com.kuryelog.logic.map.surface

/**
 * Sensör hattının ürettiği satırlar (YolGo `TelemetryService` günlük satırları `evidence`/`window`/`boundary`; WEEM'de tek süreç olduğundan
 * günlük/onay zinciri yok, satırlar doğrudan [SurfaceProcessor]'a gider). Zamanlar: [at]/[start]/[end] önyüklemeden beri monoton ms,
 * [utc] epoch ms; ikisi karıştırılmaz.
 */
sealed interface SurfaceRow {
    /** Saniyede bir GPS + hareket kanıtı: hareket kapısını besler. */
    data class Evidence(
        val day: String, val leg: String?, val eligible: Boolean, val paused: Boolean, val boot: String, val at: Long, val utc: Long,
        val lat: Double, val lon: Double, val altitude: Double, val accuracy: Double, val speed: Double, val bearing: Double, val speedAccuracy: Double?,
        val displacementSpeed: Double, val steps: Int, val orientationReliable: Boolean, val mountAngle: Double, val activityAt: Long?, val walkConfidence: Int,
        val activityAvailable: Boolean, val pressure: Double?, val sensorReliable: Boolean,
    ) : SurfaceRow

    data class Impact(val id: String, val at: Long, val peak: Double)

    /** 1,5 sn'lik titreşim penceresi (çakışmayan). */
    data class Window(
        val id: String, val day: String, val leg: String, val boot: String, val profile: SurfaceProfile, val calibrationLabel: String?,
        val start: Long, val end: Long, val utc: Long, val lat: Double, val lon: Double, val fromLat: Double?, val fromLon: Double?, val bearing: Double,
        val accuracy: Double, val speed: Double, val meters: Double, val rms: Double, val jerk: Double, val gyro: Double?, val bands: List<Double>,
        val hz: Double, val valid: Boolean, val reason: String, val horizontalRms: Double, val impacts: List<Impact>,
    ) : SurfaceRow

    /** Etap/gün sınırı: eski etabın son pencereleri kesin değildir ([closed]: etap kapandı ya da gün bitti). */
    data class Boundary(val day: String, val leg: String?, val boot: String, val closed: Boolean, val at: Long, val utc: Long) : SurfaceRow
}
