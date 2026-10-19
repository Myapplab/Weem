package com.kuryelog.logic.map.reminders

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `reminders_test.dart` "ayarlar kaydedilip okunur; eksik alanlar varsayılan" (1; A6.2b, MAP.md 13.15, 13.18). */
class ReminderSettingsTest {
    @Test
    fun `ayarlar kaydedilip okunur, eksik alanlar varsayılan`() {
        val s = ReminderSettings(ReminderSettings.defaults.items.map { if (it.kind == ReminderKind.CHECK) it.copy(enabled = true, text = "Çantanı kapat", intervalMin = 30) else it })
        val back = ReminderSettings.fromJson(s.toJson())
        val check = back.of(ReminderKind.CHECK)
        assertTrue(check.enabled)
        assertEquals("Çantanı kapat", check.message(Lang.TR))
        assertEquals(30, check.intervalMin)
        assertEquals(listOf(15 * 60 + 15), back.of(ReminderKind.FOOD).times)
        assertTrue(ReminderSettings.fromJson(null).of(ReminderKind.WATER).enabled)
        assertEquals("15:05", fmtMinuteOfDay(15 * 60 + 5))
    }
}
