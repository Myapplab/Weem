package com.kuryelog.logic.map.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Y1 karakterizasyonu (MAP.md 6.2): gün kapalıyken arka planda 5 dk → rota silinir; anahtar adı korunur. */
class PassiveSessionTest {
    @Test
    fun `gün kapalıyken 5 dakika sonra rota silinir`() {
        assertEquals("passive.pausedAt.v2", PassiveSession.KEY)
        val t = 1_792_000_000_000L
        assertEquals(t, PassiveSession.pausedAtOnBackground(dayActive = false, nowMs = t))
        assertNull(PassiveSession.pausedAtOnBackground(dayActive = true, nowMs = t))
        assertFalse(PassiveSession.shouldClearRoute(false, t, t + 299_999))
        assertTrue(PassiveSession.shouldClearRoute(false, t, t + 300_000))
        assertFalse(PassiveSession.shouldClearRoute(true, t, t + 900_000))
        assertFalse(PassiveSession.shouldClearRoute(false, null, t + 900_000))
    }
}
