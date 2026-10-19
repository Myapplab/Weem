package com.kuryelog.logic.map.day

/**
 * Canlı ışık ölçer (Dart `LightTracker`, MAP.md 11.9): bilinen ışıkta kısa (≥3 sn), bilinmeyen kavşakta ≥10 sn bekleme; GPS boşluğu ve sipariş duruşu
 * ([orderStop]) eklenmez; 2 dk üstü `light?` + `longWait`. [sinceMs] süren beklemenin başı. İskelet (A6.2b): gövdeler A6.8'de.
 */
class LightTracker {
    val sinceMs: Long? get() = TODO("A6.8")

    /** Bekleme biterse (ilk hareket fix'inden sonraki ikinci fix) kaydı döner. */
    fun feed(fix: DayFix, road: (Double, Double) -> RoadInfo, orderStop: Boolean = false): StopRec? = TODO("A6.8")

    /** Gün sonu: yalnız son geçerli fix zamanına kadar ölçer; son fix'ten ≥25 sn sonra boş. */
    fun finish(atMs: Long): StopRec? = TODO("A6.8")
}

/** Gün sonu ışık incelemesinin öğrettiği (`lightReviewTeaches`): uzun duruşta Reddet ışığı silmez (null); aday iki yönde öğretir. */
object LightReview {
    fun teaches(stop: StopRec, isLight: Boolean): Boolean? = TODO("A6.8")
}
