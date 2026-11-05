package com.kuryelog.logic.map.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * S44 (kullanıcı: "geçişler iyi değil"): takipte kamera merkezinin işarete göre kayması ve kamera yönü hedefe yumuşakça gelir. Takip başlarken
 * (yeni rotanın sığdırmasından, elle kaydırmadan ya da konum düğmesinden sonra) kamera bulunduğu yerden işarete kayar; varış odağına girip
 * çıkarken merkez (ben ↔ ben ile hedefin ortası) sıçramaz. Önceden konuma dönüş ve odak merkezi tek karede sıçrıyordu.
 *
 * Kayma işarete göre tutulur (derece): işaret yürürken kamera onunla birlikte gider, yalnız aradaki fark azalır. Başlangıçta kamera [SNAP_M]'den
 * uzaksa (uzakta kaydırılmış harita) bir anda gelinir: uzun kayma yol boyunca karo yükletir; uzun rotanın sığdırmasından dönüş bunun altında kalır. Yön bir kez yakalanınca ([BEARING_CAUGHT_DEG])
 * doğrudan işaretin yönünü izler (işaret zaten yumuşatılmış; ek gecikme oku eğerdi). Saf; süreler kare arası ms.
 */
class FollowCamera private constructor(private var offLat: Double, private var offLon: Double, private var bearing: Double?) {
    private var bearingCaught = bearing == null

    /** Kayma ve yön hedefe vardı: yeni kare yalnız işaret değişince gerekir. */
    var settled = false
        private set

    /**
     * Bir kare: işaret ([puckLat], [puckLon]), istenen merkez ve yön; [dtMs] önceki kareden bu yana. Döner: merkez enlem, boylam, yön. Yön bilinmiyorsa
     * ([targetBearing] null: pusula ve GPS yönü yok) kamera yönü değişmez (null: yüzey şimdiki yönü korur).
     */
    fun step(puckLat: Double, puckLon: Double, targetLat: Double, targetLon: Double, targetBearing: Double?, dtMs: Long): Triple<Double, Double, Double?> {
        val wantLat = targetLat - puckLat
        val wantLon = targetLon - puckLon
        val f = CameraEase.factor(CENTER_EASE, STEP_MS, dtMs)
        offLat += (wantLat - offLat) * f
        offLon += (wantLon - offLon) * f
        val centerDone = offsetM(offLat - wantLat, offLon - wantLon, puckLat) < CENTER_DONE_M
        if (centerDone) { offLat = wantLat; offLon = wantLon }
        val out = targetBearing?.let { want ->
            val b = bearing
            val nb = if (bearingCaught || b == null) want else CameraEase.easeAngle(b, want, CameraEase.factor(BEARING_EASE, STEP_MS, dtMs))
            if (!bearingCaught && abs(CameraEase.angleDelta(nb, want)) < BEARING_CAUGHT_DEG) bearingCaught = true
            if (bearingCaught) want else nb
        }
        if (out != null) bearing = out
        settled = centerDone && (bearingCaught || targetBearing == null)
        return Triple(puckLat + offLat, puckLon + offLon, out)
    }

    companion object {
        /** Merkez farkı her [STEP_MS]'de bu oranda kapanır (~1 sn'de %95). */
        const val CENTER_EASE = 0.2
        const val BEARING_EASE = 0.3
        const val STEP_MS = 80L
        const val CENTER_DONE_M = 0.3
        const val BEARING_CAUGHT_DEG = 2.0
        const val SNAP_M = 5_000.0

        /** Takip başlangıcı: kameranın şimdiki merkezi ve yönü (bilinmiyorsa null: işarete ve hedef yöne bir anda). */
        fun start(cameraLat: Double?, cameraLon: Double?, cameraBearing: Double?, puckLat: Double, puckLon: Double): FollowCamera {
            if (cameraLat == null || cameraLon == null || !cameraLat.isFinite() || !cameraLon.isFinite()) return FollowCamera(0.0, 0.0, null)
            if (offsetM(cameraLat - puckLat, cameraLon - puckLon, puckLat) > SNAP_M) return FollowCamera(0.0, 0.0, null)
            return FollowCamera(cameraLat - puckLat, cameraLon - puckLon, cameraBearing?.takeIf { it.isFinite() })
        }

        private fun offsetM(dLat: Double, dLon: Double, lat: Double): Double = hypot(dLat * M_PER_DEG, dLon * M_PER_DEG * cos(lat * PI / 180))
        private const val M_PER_DEG = 111_195.08
    }
}
