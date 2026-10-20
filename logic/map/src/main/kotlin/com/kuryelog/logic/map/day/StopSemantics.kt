package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.graph.RoadClass

/** `stop_semantics.dart` (MAP.md 11.8): yüzey ve yol kenarı kararı; GPS belirsizse karar verilmez. İskelet (A6.2b): gövdeler A6.8'de. */
object StopSemantics {
    /** AVM noktasına bu kadar yakın duruş ışık sayılmaz. */
    const val MALL_RADIUS_M = 80.0

    /** Elle işaretli → kaldırım; doğruluk >12 m → bilinmiyor; yaya yolu ≤8 m ve yoldan ≥3 m yakın → kaldırım; yol ≤6 m, doğruluk ≤8 m ve yaya yolundan ≥3 m yakın → yol. */
    fun classifySurface(accuracy: Double, roadM: Double? = null, footM: Double? = null, manualSidewalk: Boolean = false): StopSurface = TODO("A6.8")

    /** Yol ortasından şeridin dış kenarına yaklaşık uzaklık (m): trunk 9, primary 7, secondary 6, tertiary 5, diğer 4. */
    fun roadHalfWidthM(c: RoadClass): Double = TODO("A6.8")

    /** Yol çizgisinden yarım genişlikten fazla uzak ve GPS iyi (≤8 m); GPS kötüyse false. */
    fun isRoadside(accuracy: Double, roadM: Double?, cls: RoadClass?): Boolean = TODO("A6.8")
}
