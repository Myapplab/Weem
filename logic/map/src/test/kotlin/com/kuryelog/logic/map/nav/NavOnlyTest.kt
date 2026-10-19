package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `nav_only_location_test.dart` (2; A6.2b, MAP.md 7.12, 7.15), Ekim planı madde 1 / H1 (F4.4): gün biterken açık rota kesilmez; konum "yalnız navigasyon"
 * kipinde arka planda sürer; varınca, rota silinince ya da 90 dk sonra kapanır. Sağlayıcı testi `ShiftPort.state` yerine saf [NavOnlyController] girdileriyle.
 */
class NavOnlyTest {
    private val t0 = 1_791_000_000_000L
    private val minute = 60_000L

    @Test
    fun `saf geçişler`() {
        var s = NavOnlyState()
        s = NavOnly.next(s, dayActive = true, hasRoute = true, nowMs = t0)
        assertFalse(s.on) // gün açık: normal gün kipi
        s = NavOnly.next(s, dayActive = false, hasRoute = true, nowMs = t0, dayJustEnded = true)
        assertEquals(NavOnlyState(true, t0), s)
        s = NavOnly.next(s, dayActive = false, hasRoute = true, nowMs = t0 + 89 * minute)
        assertTrue(s.on)
        assertFalse(NavOnly.next(s, dayActive = false, hasRoute = true, nowMs = t0 + NavOnly.MAX_MS).on)
        assertFalse(NavOnly.next(s, dayActive = false, hasRoute = false, nowMs = t0 + 5 * minute).on) // varış/silme
        assertFalse(NavOnly.next(s, dayActive = true, hasRoute = true, nowMs = t0 + 5 * minute).on) // yeni gün
        // Rota yokken biten gün ya da gün kapalıyken çizilen rota bu kipi açmaz.
        assertFalse(NavOnly.next(NavOnlyState(), dayActive = false, hasRoute = false, nowMs = t0, dayJustEnded = true).on)
        assertFalse(NavOnly.next(NavOnlyState(), dayActive = false, hasRoute = true, nowMs = t0).on)
    }

    @Test
    fun `sağlayıcı gün biterken rota açıksa konum sürer, rota bitince kapanır`() {
        val calls = ArrayList<Boolean>()
        val c = NavOnlyController(FakeClock(t0)) { calls += it }
        c.update(dayActive = true, hasRoute = true)
        assertFalse(c.on)
        c.update(dayActive = false, hasRoute = true) // gün bitti, rota açık
        assertTrue(c.on)
        assertTrue(calls.last())
        c.update(dayActive = false, hasRoute = false) // varış
        assertFalse(c.on)
        assertFalse(calls.last())
    }
}
