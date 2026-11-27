package com.kuryelog.logic.scooter

/**
 * G3 ekranına ne zaman yazılacağı (S45; saniyede bir çağrılır): yeni yönlendirmede önce başlatma; sonra kod, sokak ya da
 * mesafe ([STEP_M]) değişince ya da [HEARTBEAT_MS]'de bir. Rota bitince ([update] null) susar; yeni rota yine başlatır.
 */
class G3Guide {
    private var started = false
    private var last: G3Display.Guide? = null
    private var atMs = 0L

    fun update(g: G3Display.Guide?, nowMs: Long): List<G3Display.Frame>? {
        if (g == null) { started = false; last = null; return null }
        val prev = last
        val changed = prev == null || prev.code != g.code || prev.street != g.street || bucket(prev.nextM) != bucket(g.nextM) ||
            bucket(prev.remainingM) != bucket(g.remainingM)
        if (started && !changed && nowMs - atMs < HEARTBEAT_MS) return null
        last = g
        atMs = nowMs
        val frames = G3Display.frames(g)
        if (started) return frames
        started = true
        return listOf(G3Display.start()) + frames
    }

    /** Yeni Bluetooth oturumu: ekran yeniden başlatılır. */
    fun reset() { started = false; last = null }

    private fun bucket(m: Int) = m / STEP_M

    companion object {
        const val STEP_M = 10
        const val HEARTBEAT_MS = 5000L
    }
}
