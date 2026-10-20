package com.kuryelog.logic.map.reminders

import org.junit.Assert.assertEquals
import org.junit.Test

/** YolGo `reminder_card_test.dart` "kart 30 sn gösterilir (1.7.0)" (1; kart testleri 2 `:app` Robolectric `ReminderCardTest`; A6.2b, MAP.md 13.16, 13.18). */
class ReminderTimingTest {
    @Test
    fun `kart 30 sn gösterilir`() {
        assertEquals(30_000L, ReminderTiming.SHOW_FOR_MS)
    }
}
