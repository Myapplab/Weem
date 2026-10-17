package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** 1.7.0: one notification per reminder, gone after 30 s; weather once per day. */
class NotifyPolicyTest {
    @Test fun onScreenReminderIsOnlyTheCard() {
        assertTrue(NotifyPolicy.inAppOnly(foreground = true, preview = false))
        assertFalse(NotifyPolicy.inAppOnly(foreground = false, preview = false))
        assertFalse(NotifyPolicy.inAppOnly(foreground = true, preview = true))
        assertEquals(30_000L, NotifyPolicy.TIMEOUT_MS)
    }

    @Test fun weatherOncePerDay() {
        assertTrue(NotifyPolicy.weatherDue(null, "d1", 0, 1_000))
        assertFalse(NotifyPolicy.weatherDue("d1", "d1", 1_000, 1_000 + 3 * 3_600_000L))
        assertTrue(NotifyPolicy.weatherDue("d1", "d2", 1_000, 2_000))
        assertTrue(NotifyPolicy.weatherDue("d1", "d1", 5_000, 1_000)) // clock went back: send again
    }
}
