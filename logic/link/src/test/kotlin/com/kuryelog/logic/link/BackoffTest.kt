package com.kuryelog.logic.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {
    @Test
    fun sequenceCapsAtThirtySeconds() {
        val b = Backoff(random = { 0.5 })
        assertTrue(b.ready("k", 0))
        val delays = (1..8).map { b.failed("k", 0) }
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000), delays)
        assertEquals(8, b.failures("k"))
    }

    @Test
    fun readyAfterDelayAndPerKey() {
        val b = Backoff(random = { 0.5 })
        b.failed("gw", 10_000)
        assertFalse(b.ready("gw", 10_999))
        assertTrue(b.ready("gw", 11_000))
        assertTrue(b.ready("other", 10_000))
        assertEquals(11_000L, b.nextAtMs("gw"))
        assertNull(b.nextAtMs("other"))
    }

    @Test
    fun jitterBounds() {
        assertEquals(800L, Backoff(random = { 0.0 }).failed("k", 0))
        assertEquals(1_199L, Backoff(random = { 0.9995 }).failed("k", 0))
        val noJitter = Backoff(Backoff.Plan(listOf(500), jitter = 0.0), random = { 0.0 })
        assertEquals(500L, noJitter.failed("k", 0))
        assertEquals(500L, noJitter.failed("k", 0))
    }

    @Test
    fun successAndResetClear() {
        val b = Backoff(random = { 0.5 })
        b.failed("a", 0)
        b.failed("a", 0)
        b.succeeded("a")
        assertEquals(0, b.failures("a"))
        assertEquals(1_000L, b.failed("a", 0))
        b.failed("b", 0)
        b.resetAll()
        assertTrue(b.ready("a", 0))
        assertTrue(b.ready("b", 0))
    }
}
