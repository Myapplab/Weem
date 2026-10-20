package com.kuryelog.logic.map.day

import com.kuryelog.logic.map.graph.RoadClass

/** `stop_semantics.dart` (MAP.md 11.8): yüzey ve yol kenarı kararı; GPS belirsizse karar verilmez. İskelet (A6.2b): gövdeler A6.8'de. */
object StopSemantics {
    /** AVM noktasına bu kadar yakın duruş ışık sayılmaz. */
    const val MALL_RADIUS_M = 80.0

    /** Elle işaretli → kaldırım; doğruluk >12 m → bilinmiyor; yaya yolu ≤8 m ve yoldan ≥3 m yakın → kaldırım; yol ≤6 m, doğruluk ≤8 m ve yaya yolundan ≥3 m yakın → yol. */
    fun classifySurface(accuracy: Double, roadM: Double? = null, footM: Double? = null, manualSidewalk: Boolean = false): StopSurface {
        if (manualSidewalk) return StopSurface.SIDEWALK
        if (!accuracy.isFinite() || accuracy < 0 || accuracy > 12) return StopSurface.UNKNOWN
        if (footM != null && footM <= 8 && (roadM == null || footM + 3 < roadM)) return StopSurface.SIDEWALK
        if (roadM != null && roadM <= 6 && accuracy <= 8 && (footM == null || roadM + 3 < footM)) return StopSurface.ROAD
        return StopSurface.UNKNOWN
    }

    /** Yol ortasından şeridin dış kenarına yaklaşık uzaklık (m): trunk 9, primary 7, secondary 6, tertiary 5, diğer 4. */
    fun roadHalfWidthM(c: RoadClass): Double = when (c) {
        RoadClass.TRUNK -> 9.0
        RoadClass.PRIMARY -> 7.0
        RoadClass.SECONDARY -> 6.0
        RoadClass.TERTIARY -> 5.0
        else -> 4.0
    }

    /** Yol çizgisinden yarım genişlikten fazla uzak ve GPS iyi (≤8 m); GPS kötüyse false. */
    fun isRoadside(accuracy: Double, roadM: Double?, cls: RoadClass?): Boolean {
        if (roadM == null || cls == null || !accuracy.isFinite() || accuracy < 0 || accuracy > 8) return false
        return roadM > roadHalfWidthM(cls)
    }
}
