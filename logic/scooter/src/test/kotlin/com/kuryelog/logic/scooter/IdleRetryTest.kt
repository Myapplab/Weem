package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** WEEM S18 (b), A10.4f: `fail` sonrası boşta kalan bağlantı 60 sn sonra yeniden açılır; anahtar/eşleştirme gereken durumda açılmaz. */
class IdleRetryTest {
    @Test fun failedSessionAndNoChannelRetryAfterAMinute() {
        assertEquals(60_000L, Timing.IDLE_RETRY_MS)
        assertEquals(60_000L, LinkRetry.idleRetryMs(NinebotSession.Stage.FAILED))
        assertEquals(60_000L, LinkRetry.idleRetryMs(null, noChannel = true))
        for (s in listOf(NinebotSession.Stage.NEEDS_KEY, NinebotSession.Stage.READING, NinebotSession.Stage.AUTH, NinebotSession.Stage.CLOSED, null)) {
            assertNull(s?.name ?: "null", LinkRetry.idleRetryMs(s))
        }
    }
}
