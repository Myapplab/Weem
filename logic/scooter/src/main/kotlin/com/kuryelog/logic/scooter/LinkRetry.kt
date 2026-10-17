package com.kuryelog.logic.scooter

/**
 * S6 reconnect bookkeeping of the BLE link (SCOOTER.md 5.1; NB `BleCapture.java:39, 41, 57`): every failure while open waits
 * [Timing.retryMs] of the current attempt and counts it (1, 2, 4, 8 s, then 15 s up to [Timing.FAST_RETRIES], then 60 s); a link
 * that reads a measurement starts again at 1 s; a closed link (user or service stop) never reconnects.
 */
class LinkRetry {
    /** Failures since the last accepted measurement; the `ble_state` event logs it before [next] counts it. */
    var attempt = 0
        private set
    var closed = false
        private set

    /** Delay before the next connection after a failure; counts the attempt. */
    fun next(): Long = Timing.retryMs(attempt++)

    /** An accepted measurement: the link reads again. */
    fun reading() {
        attempt = 0
    }

    fun close() {
        closed = true
    }
}
