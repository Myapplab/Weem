package com.kuryelog.logic.link

/**
 * Saat farkı (LINK.md 3.9): 4 zaman damgası (t1 istek, t2 eşte alış, t3 eşte yanıt, t4 alış; duvar saati ms).
 * `fark = ((t2 − t1) + (t3 − t4)) / 2` (eşin saati − benimki), `gecikme = (t4 − t1) − (t3 − t2)`. Gecikmesi 0–[maxDelayMs] olan örnek
 * alınır; tahmin = son [keep] örnekten gecikmesi en küçük olanın farkı (eşitlikte yenisi), belirsizlik ± gecikme / 2.
 */
class ClockSync(private val keep: Int = 8, private val maxDelayMs: Long = 10_000) {
    private class Sample(val offset: Long, val delay: Long)

    private val samples = ArrayDeque<Sample>()

    /** Örnek alındıysa `true`. */
    fun sample(t1: Long, t2: Long, t3: Long, t4: Long): Boolean {
        if (t4 < t1 || t3 < t2) return false
        val delay = (t4 - t1) - (t3 - t2)
        if (delay < 0 || delay > maxDelayMs) return false
        samples.addLast(Sample(Math.floorDiv((t2 - t1) + (t3 - t4), 2L), delay))
        while (samples.size > keep) samples.removeFirst()
        return true
    }

    private fun best(): Sample? {
        var b: Sample? = null
        for (s in samples) if (b == null || s.delay <= b.delay) b = s
        return b
    }

    val offsetMs: Long? get() = best()?.offset
    val uncertaintyMs: Long? get() = best()?.delay?.div(2)

    fun reset() = samples.clear()
}
