package com.kuryelog.logic.link

/**
 * Hedef başına geri çekilme (LINK.md 1.6): 1, 2, 4, 8, 16, 30 sn (üst sınır), ±%20 sapma.
 * Kayıt yoksa hedef hemen denenebilir; kanal açılınca kayıt silinir ([succeeded]); ağ değişince hepsi ([resetAll]).
 *
 * @param random [0, 1) arası; testlerde sabit (0.5 = sapmasız).
 */
class Backoff(private val plan: Plan = Plan(), private val random: () -> Double) {
    data class Plan(val stepsMs: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000), val jitter: Double = 0.2)

    private class Entry(var failures: Int, var nextAtMs: Long)

    private val entries = HashMap<String, Entry>()

    fun ready(key: String, nowMs: Long): Boolean = (entries[key]?.nextAtMs ?: Long.MIN_VALUE) <= nowMs

    /** Hedefin bir sonraki deneme anı; kayıt yoksa `null` (hemen). */
    fun nextAtMs(key: String): Long? = entries[key]?.nextAtMs

    /** Başarısız deneme: bekleme süresini döndürür. */
    fun failed(key: String, nowMs: Long): Long {
        val e = entries.getOrPut(key) { Entry(0, nowMs) }
        e.failures++
        val base = plan.stepsMs[minOf(e.failures, plan.stepsMs.size) - 1]
        val factor = 1.0 + plan.jitter * (2 * random() - 1)
        val delay = (base * factor).toLong().coerceAtLeast(1)
        e.nextAtMs = nowMs + delay
        return delay
    }

    fun failures(key: String): Int = entries[key]?.failures ?: 0

    fun succeeded(key: String) {
        entries.remove(key)
    }

    fun resetAll() = entries.clear()
}
