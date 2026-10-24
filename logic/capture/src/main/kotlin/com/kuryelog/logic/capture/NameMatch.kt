package com.kuryelog.logic.capture

// Rawstats `CustomerMatch.distance/sameName/looksLikeAddress` (DeliveryCore.kt:900-918 @ 6388c04); CAPTURE.md 2.10.
// Kayıt düzeyindeki `CustomerMatch.find/distinct` (CustomerRecord) `:orders`'ta kalır ve bunu çağırır.
/** Same customer seen again without an order ID: OCR misreads a letter or drops the surname. */
object NameMatch {
    private fun tokens(s: String) = CourierParser.norm(s).split(" ").filter { it.isNotEmpty() }
    /** Levenshtein uzaklığı (`:orders` `CustomerMatch`, `OfferMatch`, `PlatformHistory` de kullanır). */
    fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) { val cur = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = cur }
        }
        return d[b.length]
    }
    private fun tokenEq(a: String, b: String) = a == b || (minOf(a.length, b.length) >= 5 && distance(a, b) <= 1)
    fun sameName(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val x = tokens(a); val y = tokens(b)
        if (x.isEmpty() || y.isEmpty()) return false
        val (short, long) = if (x.size <= y.size) x to y else y to x
        // Every word of the shorter name appears in the longer one, the first word included.
        return tokenEq(short[0], long[0]) && short.all { s -> long.any { tokenEq(s, it) } }
    }
    /** A street line has a digit; a surname moved to the address line by OCR has none. */
    fun looksLikeAddress(s: String) = s.any(Char::isDigit)
}
