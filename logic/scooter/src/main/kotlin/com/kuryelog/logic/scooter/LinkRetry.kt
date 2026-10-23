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

    companion object {
        /**
         * WEEM S18 (b), A10.4f (SCOOTER.md 5.1, 14 R-S3): delay before a link idle after `fail` is reopened, or null when it stays
         * idle. A failed session ([NinebotSession.Stage.FAILED]: no G3 answer, AUTH failed, bad name/serial) and a GATT without a
         * notification channel ([noChannel]) are retried; a missing key ([NinebotSession.Stage.NEEDS_KEY]) or an expired pairing
         * window needs the user (pairing), so a retry could not help.
         */
        fun idleRetryMs(stage: NinebotSession.Stage?, noChannel: Boolean = false): Long? =
            if (noChannel || stage == NinebotSession.Stage.FAILED) Timing.IDLE_RETRY_MS else null
    }
}
