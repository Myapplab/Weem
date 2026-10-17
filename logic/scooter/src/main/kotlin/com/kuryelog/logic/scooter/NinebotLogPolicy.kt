package com.kuryelog.logic.scooter

/**
 * Limits diagnostic storage, independently of the live panel and 1 Hz ride samples. Per-packet rows and connection/error rows
 * have separate budgets, so ten minutes of detailed packet logging can never use up the space for a later disconnect or AUTH
 * failure. (SCOOTER.md 7.1; NB `NinebotLogPolicy.java`.)
 */
class NinebotLogPolicy(private val detailed: Boolean, nowMs: Long) {
    private val startedMs = nowMs
    private var frequentAccepted = 0
    private var rareAccepted = 0
    var omitted = 0L
        private set

    fun keep(kind: String, nowMs: Long): Boolean {
        if (frequent(kind)) {
            if (!detailed || nowMs - startedMs >= DETAIL_MS || frequentAccepted >= MAX_BLE_EVENTS) { omitted++; return false }
            frequentAccepted++
            return true
        }
        if (rareAccepted >= MAX_BLE_EVENTS) { omitted++; return false }
        rareAccepted++
        return true
    }

    companion object {
        const val MAX_BLE_EVENTS = 10000
        const val DETAIL_MS = 10 * 60 * 1000L

        fun frequent(kind: String): Boolean =
            kind == "ble_raw" || kind == "ble_write" || kind == "ble_write_result" || kind == "protocol_tx" || kind == "ble_measurement"
    }
}
