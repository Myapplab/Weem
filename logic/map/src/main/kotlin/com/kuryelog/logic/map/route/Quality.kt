package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.RoadClass

/**
 * Sokak başına öğrenilen kalite girdisi (YolGo `LearnedQuality`, `MAP.md` 9.3): `q` öğrenilen skor, `c` güven (0–1), `r` sürücünün girdiği
 * yüzde (0 = girilmedi).
 */
data class LearnedQuality(val q: Double, val c: Double, val r: Int)

/** Elle girilen kalite (%): 1–100. 0 = girilmedi. 👍 = %90, 👎 = %25. */
object QualityRating {
    const val GOOD = 90
    const val NONE = 0
    const val BAD = 25

    /** Eski sürümün değerleri (1 / -1) → yüzde. */
    fun migrate(v: Int): Int = TODO("A6.4")
}

/** Kaliteye göre renk sınıfı için eşikler. */
object QualityBand {
    const val GOOD = 0.75
    const val FAIR = 0.5
}

/** OSM'den ön tahmin (`quality.dart`, `MAP.md` 9.3). İskelet (A6.2a): gövdeler A6.4'te. */
fun priorQuality(cls: RoadClass, flags: Int): Double = TODO("A6.4")

fun learnedScore(meters: Double, stops: Double, vib: Double? = null): Double = TODO("A6.4")
fun learnedConfidence(meters: Double): Double = TODO("A6.4")
fun blendQuality(prior: Double, learned: LearnedQuality?): Double = TODO("A6.4")
fun streetScoreQuality(score: Double): Double = TODO("A6.4")
fun wrongWayRatio(passes: Int, problems: Double): Double = TODO("A6.4")
fun wrongWaySmoothed(passes: Int, problems: Double): Double = TODO("A6.4")
