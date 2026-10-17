package com.kuryelog.logic.capture

// İskelet (A3.2): imzalar Rawstats'tan (RecordingPolicy.kt @ 6388c04; CAPTURE.md 2.9). MotionGate ve OfferIdentity WEEM'de yazılmaz. Gövdeler A3.4'te.
object OfferRules {
    fun isAccept(s: String): Boolean = TODO("A3.4")
    fun isHistory(texts: List<String>): Boolean = TODO("A3.4")
    fun isOffer(actions: List<String>, texts: List<String>, hasDetails: Boolean): Boolean = TODO("A3.4")
}

class ScreenDeduper {
    fun shouldSave(pkg: String, texts: List<String>): Boolean = TODO("A3.4")
}
