package com.kuryelog.logic.capture

// İskelet (A3.2): imzalar ve sabitler Rawstats'tan (OfferCapture.kt @ 6388c04; CAPTURE.md 3.1–3.2); D6, K37. Gövdeler A3.4'te.
object OfferCapture {
    const val REREAD_MS = 100L
    const val MAX_REREADS = 10
    const val INVALID_WINDOW = 5
    const val DISPLAY_FALLBACK_MS = 60_000L
    const val OCR_GAP_MS = 1_500L
    const val SHOT_INTERVAL_MS = 350L
    const val HOT_AFTER_TRIGGER_MS = 10_000L
    const val HOT_OFFER_MS = 20_000L
    const val OCR_MAX_WIDTH = 960

    fun reread(o: CourierObservation, done: Int): Boolean = TODO("A3.4")
    /** True when OCR should take the whole display (and crop) instead of the window, given the last invalid-window failure. */
    fun useDisplay(sdk: Int, invalidWindowAt: Long?, now: Long): Boolean = TODO("A3.4")
    /** D6: scale of an OCR input [width] px wide (1 = as it is). */
    fun ocrScale(width: Int): Float = TODO("A3.4")
}

class OcrPace {
    fun trigger(pkg: String, at: Long): Unit = TODO("A3.4")
    fun hot(id: Int, pkg: String, now: Long): Boolean = TODO("A3.4")
    fun due(id: Int, pkg: String, now: Long): Boolean = TODO("A3.4")
    /** The window to read now: a due hot window first, else the due window read longest ago; null = none. */
    fun <W> next(windows: List<W>, id: (W) -> Int, pkg: (W) -> String, now: Long): W? = TODO("A3.4")
    fun started(id: Int, pkg: String, at: Long): Unit = TODO("A3.4")
    fun failed(id: Int, at: Long): Unit = TODO("A3.4")
    fun read(id: Int, o: CourierObservation): Unit = TODO("A3.4")
    fun hotDelay(now: Long): Long? = TODO("A3.4")
    fun same(id: Int, hash: Long): List<String>? = TODO("A3.4")
    fun remember(id: Int, hash: Long, lines: List<String>): Unit = TODO("A3.4")
    fun retain(ids: Set<Int>): Unit = TODO("A3.4")

    companion object {
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        /** Ekran görüntüsünün her pikselini sayan karma (D6). */
        fun fnv(h: Long, px: IntArray, n: Int): Long = TODO("A3.4")
    }
}
