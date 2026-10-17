package com.kuryelog.logic.capture

// İskelet (A3.2): Rawstats `CustomerMatch.distance/sameName/looksLikeAddress` (DeliveryCore.kt:900-918 @ 6388c04); CAPTURE.md 2.10.
// Kayıt düzeyindeki `CustomerMatch.find/distinct` (CustomerRecord) `:orders`'ta kalır. Gövdeler A3.4'te.
object NameMatch {
    internal fun distance(a: String, b: String): Int = TODO("A3.4")
    fun sameName(a: String?, b: String?): Boolean = TODO("A3.4")
    /** A street line has a digit; a surname moved to the address line by OCR has none. */
    fun looksLikeAddress(s: String): Boolean = TODO("A3.4")
}
