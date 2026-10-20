package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Lang
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rota paneli metinleri (YolGo `fmtMin`, `_ActiveStop`, `_Arrived`; MAP.md 7.11). */
class NavPanelTextTest {
    @Test
    fun `süre ve varış saati, 1 dk altı`() {
        val now = LocalDateTime.of(2026, 10, 20, 7, 32)
        assertEquals("13 dk · 07:45", NavPanelText.eta(13 * 60.0, now, Lang.TR))
        assertEquals("<1 dəq · 07:32", NavPanelText.eta(25.0, now, Lang.AZ))
        assertEquals("2", NavPanelText.minutes(90.0))
    }

    @Test
    fun `güzergah ilk 3 sokak, varış başlığı iki dilde`() {
        assertEquals("Güzergâh: A → B → C", NavPanelText.streets(listOf("A", "B", "C", "D"), Lang.TR))
        assertEquals("Marşrut: A", NavPanelText.streets(listOf("A"), Lang.AZ))
        assertNull(NavPanelText.streets(emptyList(), Lang.TR))
        assertEquals("Vardın: Nizami 12", NavPanelText.arrived("Nizami 12", Lang.TR))
        assertEquals("Çatdın: Nizami 12", NavPanelText.arrived("Nizami 12", Lang.AZ))
    }
}
