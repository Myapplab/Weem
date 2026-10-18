package com.kuryelog.logic.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** LINK.md 3.9: 4 zaman damgası, en küçük gecikmeli örnek, kötü örnek reddi. */
class ClockSyncTest {
    @Test
    fun offsetAndDelayFormula() {
        val c = ClockSync()
        assertNull(c.offsetMs)
        // Eş 7 345 ms ileride, gidiş 30 ms, dönüş 10 ms, eşte 5 ms bekleme.
        val t1 = 1_000_000L
        val t2 = t1 + 30 + 7_345
        val t3 = t2 + 5
        val t4 = t1 + 30 + 5 + 10
        assertTrue(c.sample(t1, t2, t3, t4))
        assertEquals(7_345L + 10, c.offsetMs) // asimetri belirsizliği: (30 − 10) / 2
        assertEquals(20L, c.uncertaintyMs)
    }

    @Test
    fun keepsTheSmallestDelayAmongTheLastEight() {
        val c = ClockSync()
        var t = 0L
        fun s(delay: Long, offset: Long) {
            t += 300_000
            c.sample(t, t + delay / 2 + offset, t + delay / 2 + offset, t + delay)
        }
        s(400, 100)
        s(20, 50)
        s(300, 900)
        assertEquals(50L, c.offsetMs)
        assertEquals(10L, c.uncertaintyMs)
        repeat(8) { s(200, -40) }
        // 20 ms'lik örnek son 8'in dışına düştü.
        assertEquals(-40L, c.offsetMs)
    }

    @Test
    fun rejectsNegativeOrHugeDelay() {
        val c = ClockSync()
        assertFalse("eşte bekleme gidiş-dönüşten uzun", c.sample(1000, 500, 700, 1100))
        assertFalse("eşin saati geri gitti", c.sample(1000, 500, 400, 1100))
        assertFalse("benim saatim geri gitti", c.sample(1000, 500, 500, 900))
        assertFalse(c.sample(0, 0, 0, 10_001))
        assertNull(c.offsetMs)
        assertTrue(c.sample(0, 0, 0, 10_000))
        c.reset()
        assertNull(c.offsetMs)
    }
}
