package com.kuryelog.logic.map.stats

import com.kuryelog.logic.map.day.DayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** YolGo `home_day_controller_test.dart` 5. test (1; A6.2b, MAP.md 12.8, 12.13): gün istatistiğinde otomatik başlangıç/bitiş nedeni; saf işlev, SORU-11-1'e bağlı değil. */
class DayReasonsTest {
    private fun day(start: String? = null, end: String? = null) = DayState().also { it.startReason = start; it.endReason = end }

    @Test
    fun `gün istatistiğinde otomatik başlangıç ve bitiş nedeni`() {
        assertEquals("Evden çıkınca başladı · Eve dönünce bitti", DayReasons.of(day("home_departure", "home_arrival")))
        assertEquals("Eve dönünce bitti", DayReasons.of(day("manual", "home_arrival")))
        assertNull(DayReasons.of(day("manual", "manual")))
        assertNull("1.5.0 kayıtları", DayReasons.of(day()))
    }
}
