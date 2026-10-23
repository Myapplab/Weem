package com.kuryelog.logic.scooter

/** Fix ages, reconnect delays (S6) and lossless hex (SCOOTER.md 5.1; NB `Timing.java`, line by line). */
object Timing {
    fun ageMs(nowNanos: Long, fixNanos: Long?): Long? =
        if (fixNanos == null || fixNanos > nowNanos) null else (nowNanos - fixNanos) / 1_000_000L

    /** Reconnect delay after a lost link: 1, 2, 4, 8 s, then every 15 s for the first [FAST_RETRIES] attempts (~11 minutes with
     *  15 s connect timeouts: a stop at a restaurant or a door, the scooter is back within seconds of being switched on), then
     *  every 60 s (the scooter is probably off for longer). A link that reads again starts from 1 s. */
    fun retryMs(attempt: Int): Long {
        val a = maxOf(0, attempt)
        return if (a >= FAST_RETRIES) 60_000L else minOf(15_000L, 1_000L shl minOf(4, a))
    }

    const val FAST_RETRIES = 24

    /** WEEM S18 (b), A10.4f: a link left idle by `fail` (GATT open, nothing read) is reopened after this, unless the user acts first. */
    const val IDLE_RETRY_MS = 60_000L

    fun hex(value: ByteArray): String {
        val out = CharArray(value.size * 2)
        val digits = "0123456789abcdef"
        for (i in value.indices) {
            val n = value[i].toInt() and 255
            out[i * 2] = digits[n ushr 4]
            out[i * 2 + 1] = digits[n and 15]
        }
        return String(out)
    }

    /** `Character.digit` as in the original (not `digitToInt`): the same characters are accepted. */
    fun unhex(text: String?): ByteArray {
        if (text == null || text.length % 2 != 0) throw IllegalArgumentException("hex")
        val out = ByteArray(text.length / 2)
        for (i in out.indices) {
            val a = Character.digit(text[i * 2], 16)
            val b = Character.digit(text[i * 2 + 1], 16)
            if (a < 0 || b < 0) throw IllegalArgumentException("hex")
            out[i] = (a * 16 + b).toByte()
        }
        return out
    }
}
