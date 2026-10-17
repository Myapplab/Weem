package com.kuryelog.logic.scooter

/**
 * Ekim planı madde 23 (16 Oct): the courier walks away from the scooter and comes back; Bluetooth reconnects but nobody knows if
 * values arrive again. Rawstats' `ninebot-logger/` exposed no connection moment, only its frames, so the link state is read
 * from them (SCOOTER.md 5.2; RS `ScooterReconnect.kt`, unchanged in WEEM although `:scooter` now owns the link):
 * with the live link open, no frame for [LOST_MS] after frames had come = lost; a frame after that = "Scooter
 * verileri alınıyor" ([Notice.RECEIVING]); lost and riding (GPS ≥ [RIDE_KMH]) for [NO_DATA_MS] without a frame = "Scooter
 * verileri alınamıyor" ([Notice.NOT_RECEIVING], once per loss). The first frames of a link are no reconnection. Pure; one tick a
 * second or so on one clock (the frames' monotonic clock in the app).
 */
class ScooterReconnect {
    enum class Notice { RECEIVING, NOT_RECEIVING }

    private var seen = false
    private var lastFrame: Long? = null
    private var lost = false
    private var ridingSince: Long? = null
    private var warned = false

    fun tick(now: Long, linkActive: Boolean, lastFrameAt: Long?, speedKmh: Double?): Notice? {
        if (!linkActive) { seen = false; lastFrame = null; lost = false; ridingSince = null; warned = false; return null }
        val fresh = lastFrameAt != null && (lastFrame == null || lastFrameAt > lastFrame!!)
        if (fresh) lastFrame = lastFrameAt
        if (lost) {
            if (fresh) { lost = false; ridingSince = null; warned = false; return Notice.RECEIVING }
            if (warned) return null
            ridingSince = if (speedKmh != null && speedKmh >= RIDE_KMH) ridingSince ?: now else null
            if (ridingSince != null && now - ridingSince!! >= NO_DATA_MS) { warned = true; return Notice.NOT_RECEIVING }
            return null
        }
        if (fresh) { seen = true; return null }
        if (seen && lastFrame != null && now - lastFrame!! >= LOST_MS) lost = true
        return null
    }

    companion object {
        const val LOST_MS = 30_000L
        const val NO_DATA_MS = 30_000L
        const val RIDE_KMH = 10.0
    }
}
