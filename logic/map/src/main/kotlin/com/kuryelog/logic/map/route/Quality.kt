package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.Geo.clampD
import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.graph.WayFlag

/**
 * Yol kalitesi = akıcılık (0 kötü – 1 mükemmel) — `lib/routing/quality.dart` (MAP.md 3.3, 9.9).
 * [q]: öğrenilen skor, [c]: güven (0–1), [r]: elle girilen % (0 = yok).
 */
data class LearnedQuality(val q: Double, val c: Double, val r: Int)

/** Elle girilen kalite (%): 1–100. 0 = girilmedi. 👍 = %90, 👎 = %25. */
object QualityRating {
    const val GOOD = 90
    const val NONE = 0
    const val BAD = 25

    /** Eski sürümün değerleri (1 / -1) → yüzde. */
    fun migrate(v: Int): Int = if (v == 1) GOOD else if (v == -1) BAD else v
}

/** Kaliteye göre renk sınıfı eşikleri. */
object QualityBand {
    const val GOOD = 0.75
    const val FAIR = 0.5
}

object Quality {
    /** OSM'den ön tahmin: ana caddelerde ışık ve trafik → daha çok dur-kalk; toprak / parke taşı → sarsıntı. */
    fun priorQuality(cls: RoadClass, flags: Int): Double {
        var q = when (cls) {
            RoadClass.TRUNK, RoadClass.PRIMARY -> 0.60
            RoadClass.SECONDARY -> 0.68
            RoadClass.TERTIARY -> 0.76
            RoadClass.UNCLASSIFIED, RoadClass.RESIDENTIAL, RoadClass.ROAD -> 0.82
            RoadClass.LIVING_STREET -> 0.70
            RoadClass.SERVICE -> 0.75
            RoadClass.TRACK -> 0.45
            RoadClass.CYCLEWAY -> 0.90
            RoadClass.PATH, RoadClass.FOOTWAY, RoadClass.PEDESTRIAN -> 0.60
            RoadClass.STEPS -> 0.0
        }
        if ((flags and WayFlag.UNPAVED) != 0 && q > 0.35) q = 0.35
        if ((flags and WayFlag.ROUGH) != 0 && q > 0.50) q = 0.50
        return q
    }

    /**
     * Sürüşten öğrenilen skor. [stops]: dur-kalk olayı (tam durma 1, durmadan sert yavaşlama 0,5); [vib]: ortalama titreşim
     * (m/s²), yoksa null. km başına 2 dur-kalk ≈ %78, 4 ≈ %56, 6 ≈ %34.
     */
    fun learnedScore(meters: Double, stops: Double, vib: Double? = null): Double {
        if (meters <= 0) return 1.0
        val perKm = stops / (meters / 1000)
        var q = 1 - 0.11 * perKm
        if (vib != null && vib > 2.5) q -= clampD((vib - 2.5) * 0.1, 0.0, 0.3)
        return clampD(q, 0.05, 1.0)
    }

    /** Ne kadar çok sürülmüşse o kadar güvenilir: ~800 m'de tam güven. */
    fun learnedConfidence(meters: Double): Double = clampD(meters / 800, 0.0, 1.0)

    /** Ön tahmin + öğrenilen → son kalite (0–1). Sürücünün girdiği % her şeyin önünde. */
    fun blendQuality(prior: Double, l: LearnedQuality?): Double {
        if (l == null) return prior
        if (l.r > 0) return clampD(l.r / 100.0, 0.0, 1.0)
        return clampD(prior + (l.q - prior) * clampD(l.c, 0.0, 1.0), 0.0, 1.0)
    }

    /** E11 (C4): sokak puanı (1–10) → rota kalitesi 0–1 (Y13 ile; 1.0'da kullanılmaz). */
    fun streetScoreQuality(score: Double): Double = clampD((score - 1) / 9, 0.0, 1.0)

    /** Ters yön sorun oranı (ham, ekranda ve `wwAvoid`'de): sorun / geçiş. */
    fun wrongWayRatio(passes: Int, problems: Double): Double = if (passes <= 0) 0.0 else clampD(problems / passes, 0.0, 1.0)

    /** Rota için yumuşatılmış oran: tek kötü geçiş sokağı hemen gözden düşürmesin. */
    fun wrongWaySmoothed(passes: Int, problems: Double): Double = clampD((problems + 0.1) / (passes + 1), 0.0, 1.0)
}
