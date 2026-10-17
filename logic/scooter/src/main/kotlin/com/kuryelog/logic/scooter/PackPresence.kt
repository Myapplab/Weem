package com.kuryelog.logic.scooter

/**
 * D12 (13 Ekim): the external pack's "bağlı / bağlantı yok" message only after the new state held for STABLE_MS.
 * The pack flickered in 3 s pieces (saha 11–12 Ekim); the old rule spoke after any 60 s gap. Pure: no Android.
 * B3 (10 Ekim, "scooter her bağlandığında"): a link down for STABLE_MS or longer (scooter off, the next shift) makes the next
 * frame a new connection, said again once the pack held 60 s; a short reconnect (Bluetooth, 3–5 s) is the same connection.
 * (SCOOTER.md 9.4; NB `PackPresence.java`.)
 */
class PackPresence {
    /** The last announced state: true connected, false lost, null nothing announced yet. */
    var announced: Boolean? = null
        private set
    private var current = false
    private var since = -1L
    private var downAt = -1L

    /** One frame: [present] = the external pack's percent is fresh. Returns CONNECTED or LOST once per settled change, else null.
     *  A pack never seen is never announced as lost. */
    fun observe(present: Boolean, now: Long): String? {
        if (downAt >= 0) {
            if (now - downAt >= STABLE_MS) { announced = null; since = -1 }
            downAt = -1
        }
        if (since < 0 || present != current) { current = present; since = now }
        if (now - since < STABLE_MS) return null
        if (announced != null && announced == current) return null
        val first = announced == null
        announced = current
        if (first && !current) return null
        return if (current) CONNECTED else LOST
    }

    /** The scooter link went down at [now] (no frames until it is back). */
    fun linkDown(now: Long) {
        if (downAt < 0) downAt = now
    }

    companion object {
        const val STABLE_MS = 60_000L
        const val CONNECTED = "connected"
        const val LOST = "lost"
    }
}
