package com.kuryelog.logic.scooter

/**
 * One pairing window across reconnections (SCOOTER.md 4.1, R-S4; NB `BleCapture.java:46-47, 54`): the first protocol start of a
 * pairing link opens [NinebotSession.PAIR_WINDOW_MS], a reconnect gets what is left, an expired window starts no session (the
 * link stays connected: "Eşleştirme süresi doldu; yeniden denemek için kaydı durdur"), and a confirmed key ends pairing so later
 * reconnects authenticate with it.
 */
class PairWindow(pair: Boolean) {
    /** Pairing asked for and not confirmed yet (also decides whether an unreadable local key may be replaced). */
    var allowPair = pair
        private set
    private var pairEnd = 0L

    /** What a session started at [now] gets. */
    class Start(val pair: Boolean, val window: Long) {
        /** Pairing was asked for but its window is over: no session. */
        val expired: Boolean get() = window < 0
    }

    fun begin(now: Long): Start {
        if (allowPair && pairEnd == 0L) pairEnd = now + NinebotSession.PAIR_WINDOW_MS
        val pair = allowPair && now < pairEnd
        if (allowPair && !pair) return Start(false, -1)
        return Start(pair, if (pair) pairEnd - now else 0)
    }

    /** The scooter accepted the new key (AUTH with it succeeded). */
    fun confirmed() {
        allowPair = false
    }
}
