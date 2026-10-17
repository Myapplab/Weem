package com.kuryelog.logic.capture

/**
 * K37: how fast an offer card is read. The first read of an accessibility event is never delayed; while a card is on
 * screen without its fee, short extra reads follow so the fee is caught on the next frame instead of at the next tick.
 * After a window screenshot fails with an invalid window (Android 14 `takeScreenshotOfWindow` code 5), OCR uses the
 * display screenshot cropped to the window for a while.
 *
 * D6 (saha 11–12 Oct 2026): Wolt's map, offer and task screens have no accessibility text, so every Wolt card frame was OCR,
 * 1.5 s apart (one OCR per window per [OCR_GAP_MS]). Wolt shows the card first without its fee and route row and loads them
 * 1–12 s later (median 3 s after the first card frame; fee in the first frame on 4 of 28 cards). [OcrPace] reads a window
 * at Android's shortest screenshot interval while it matters (see there); OCR input is at most [OCR_MAX_WIDTH] wide.
 */
object OfferCapture {
    /** Gap between extra reads of an offer card that has no fee yet. */
    const val REREAD_MS = 100L
    /** Extra reads per offer card at most (about one second). */
    const val MAX_REREADS = 10
    /** AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW (API 34). */
    const val INVALID_WINDOW = 5
    /** How long the display screenshot replaces the window screenshot after an invalid-window failure. */
    const val DISPLAY_FALLBACK_MS = 60_000L
    /** D6: OCR of one window at most this often, unless the window is hot. */
    const val OCR_GAP_MS = 1_500L
    /** D6: two screenshots at least this far apart: Android refuses a second one within 333 ms (ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT). */
    const val SHOT_INTERVAL_MS = 350L
    /** D6: a notification or a new window state of the app makes its windows hot this long (a new offer; saha analysis: the
     *  card was recognised 2.1–6.7 s after Wolt's notification). */
    const val HOT_AFTER_TRIGGER_MS = 10_000L
    /** D6: an offer card still without its fee keeps its window hot this long after its first such frame (saha: at most 12 s). */
    const val HOT_OFFER_MS = 20_000L
    /** D6: OCR input width cap. ML Kit wants ≥16 px characters; Wolt's 12–14 sp text on the 1280 px screen stays about 20–24 px. */
    const val OCR_MAX_WIDTH = 960

    /** True when this frame is an offer card whose fee is still unread and another quick read is allowed. */
    fun reread(o: CourierObservation, done: Int): Boolean = o.stage == ScreenStage.OFFER && o.gross == null && done < MAX_REREADS

    /** True when OCR should take the whole display (and crop) instead of the window, given the last invalid-window failure. */
    fun useDisplay(sdk: Int, invalidWindowAt: Long?, now: Long): Boolean =
        sdk < 34 || (invalidWindowAt != null && now - invalidWindowAt in 0 until DISPLAY_FALLBACK_MS)

    /** D6: scale of an OCR input [width] px wide (1 = as it is). */
    fun ocrScale(width: Int): Float = if (width <= OCR_MAX_WIDTH) 1f else OCR_MAX_WIDTH.toFloat() / width
}

/**
 * D6: which window OCR reads next and when (pure; WatchService owns one). A window is read at most every
 * [OfferCapture.OCR_GAP_MS], except while it is hot: for [OfferCapture.HOT_AFTER_TRIGGER_MS] after its app posted a notification
 * or changed its window state ([trigger]), and while its last OCR frame was an offer card without its fee ([read]), for at most
 * [OfferCapture.HOT_OFFER_MS]. A hot window is read again as soon as the previous screenshot is [OfferCapture.SHOT_INTERVAL_MS]
 * old, before any other window. A screenshot identical to the window's previous one is not OCR'd again ([same]).
 */
class OcrPace {
    private val lastAt = HashMap<Int, Long>()
    private val pkgOf = HashMap<Int, String>()
    private val triggerAt = HashMap<String, Long>()
    private val loadingSince = HashMap<Int, Long>()
    private val image = HashMap<Int, Pair<Long, List<String>>>()
    private val failedAt = HashMap<Int, Long>()
    private var shotAt = Long.MIN_VALUE / 2

    fun trigger(pkg: String, at: Long) { if (at > (triggerAt[pkg] ?: Long.MIN_VALUE)) triggerAt[pkg] = at }

    fun hot(id: Int, pkg: String, now: Long): Boolean =
        triggerAt[pkg]?.let { now - it in 0..OfferCapture.HOT_AFTER_TRIGGER_MS } == true ||
            loadingSince[id]?.let { now - it in 0..OfferCapture.HOT_OFFER_MS } == true

    private fun last(id: Int) = lastAt[id] ?: Long.MIN_VALUE / 2

    fun due(id: Int, pkg: String, now: Long): Boolean = now - shotAt >= OfferCapture.SHOT_INTERVAL_MS &&
        now - last(id) >= if (hot(id, pkg, now) && failedAt[id] != last(id)) OfferCapture.SHOT_INTERVAL_MS else OfferCapture.OCR_GAP_MS

    /** The window to read now: a due hot window first, else the due window read longest ago; null = none. */
    fun <W> next(windows: List<W>, id: (W) -> Int, pkg: (W) -> String, now: Long): W? {
        val due = windows.filter { due(id(it), pkg(it), now) }
        return due.filter { hot(id(it), pkg(it), now) }.minByOrNull { last(id(it)) } ?: due.minByOrNull { last(id(it)) }
    }

    fun started(id: Int, pkg: String, at: Long) { lastAt[id] = at; pkgOf[id] = pkg; shotAt = at }

    /** The screenshot of window [id] started at [at] failed: the window waits the normal gap, never the hot one. */
    fun failed(id: Int, at: Long) { failedAt[id] = at }

    /** An accepted OCR frame of window [id]: an offer card without its fee keeps the window hot; any other frame ends that. */
    fun read(id: Int, o: CourierObservation) {
        if (o.stage == ScreenStage.OFFER && o.gross == null) { if (!loadingSince.containsKey(id)) loadingSince[id] = o.at } else loadingSince.remove(id)
    }

    /** Milliseconds until a hot window read before may be read again, null when none is hot (the next tick or event reads). */
    fun hotDelay(now: Long): Long? =
        if (lastAt.keys.any { hot(it, pkgOf[it] ?: "", now) }) (shotAt + OfferCapture.SHOT_INTERVAL_MS - now).coerceAtLeast(0) else null

    /** Lines of the window's previous screenshot when this one ([hash]) is identical to it; null = OCR it. */
    fun same(id: Int, hash: Long): List<String>? = image[id]?.takeIf { it.first == hash }?.second

    fun remember(id: Int, hash: Long, lines: List<String>) { image[id] = hash to lines }

    /** Windows no longer on screen are forgotten. */
    fun retain(ids: Set<Int>) { for (m in listOf(lastAt, pkgOf, loadingSince, image, failedAt)) m.keys.retainAll(ids) }

    companion object {
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L
        /** FNV-1a over the first [n] pixels of [px], continuing [h]: every pixel counts, so a changed digit changes the hash. */
        fun fnv(h: Long, px: IntArray, n: Int): Long {
            var x = h
            for (i in 0 until n) { x = (x xor (px[i].toLong() and 0xffffffffL)) * FNV_PRIME }
            return x
        }
    }
}
