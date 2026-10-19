package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.RoadClass

/**
 * Yol kalitesi = akıcılık (0 kötü – 1 mükemmel) — `lib/routing/quality.dart` (MAP.md 9.12).
 * [q]: öğrenilen skor, [c]: güven (0–1), [r]: elle girilen % (0 = yok).
 */
data class LearnedQuality(val q: Double, val c: Double, val r: Int)

/** Elle girilen kalite (%): 1–100. 0 = girilmedi. */
object QualityRating {
    const val GOOD = 90
    const val NONE = 0
    const val BAD = 25

    /** Eski sürümün değerleri (1 / -1) → yüzde. */
    fun migrate(v: Int): Int = TODO("A6.4")
}

object QualityBand {
    const val GOOD = 0.75
    const val FAIR = 0.5
}

object Quality {
    /** OSM'den ön tahmin (sınıf + bayraklar). */
    fun priorQuality(cls: RoadClass, flags: Int): Double = TODO("A6.4")

    /** Sürüşten öğrenilen skor. [stops]: dur-kalk olayı; [vib]: ortalama titreşim (m/s²), yoksa null. */
    fun learnedScore(meters: Double, stops: Double, vib: Double? = null): Double = TODO("A6.4")

    /** Ne kadar çok sürülmüşse o kadar güvenilir: ~800 m'de tam güven. */
    fun learnedConfidence(meters: Double): Double = TODO("A6.4")

    /** Ön tahmin + öğrenilen → son kalite (0–1). Sürücünün girdiği % her şeyin önünde. */
    fun blendQuality(prior: Double, l: LearnedQuality?): Double = TODO("A6.4")

    /** E11 (C4): sokak puanı (1–10) → rota kalitesi 0–1. */
    fun streetScoreQuality(score: Double): Double = TODO("A6.4")

    /** Ters yön sorun oranı (ham): sorun / geçiş. */
    fun wrongWayRatio(passes: Int, problems: Double): Double = TODO("A6.4")

    /** Rota için yumuşatılmış oran. */
    fun wrongWaySmoothed(passes: Int, problems: Double): Double = TODO("A6.4")
}
