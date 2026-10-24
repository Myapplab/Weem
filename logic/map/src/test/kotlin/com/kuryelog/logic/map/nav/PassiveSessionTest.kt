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

    @Test
    fun `tercihlerle arka plan ve öne dönüş, anahtar her dönüşte silinir`() {
        val prefs = com.kuryelog.logic.map.MemoryMapPrefs()
        val t = 1_792_000_000_000L
        PassiveSession.onBackground(prefs, dayActive = true, nowMs = t)
        assertNull(prefs.getLong(PassiveSession.KEY)) // gün açıkken yazılmaz
        PassiveSession.onBackground(prefs, dayActive = false, nowMs = t)
        assertEquals(t, prefs.getLong(PassiveSession.KEY))
        assertFalse(PassiveSession.onForeground(prefs, dayActive = false, nowMs = t + 60_000)) // 1 dk: rota kalır
        assertNull(prefs.getLong(PassiveSession.KEY))
        PassiveSession.onBackground(prefs, dayActive = false, nowMs = t)
        assertTrue(PassiveSession.onForeground(prefs, dayActive = false, nowMs = t + 300_000))
        assertFalse(PassiveSession.onForeground(prefs, dayActive = false, nowMs = t + 900_000)) // anahtar silindi
    }
}
