package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

class RecordPagesTest {
    private val dates = listOf("2026-10-15", "2026-10-12", "2026-10-09")
    @Test fun onlyRecordedDaysNewestFirst() {
        assertEquals(dates, RecordPages.dates(listOf(dates[1], dates[0]), listOf(dates[2], dates[1])))
        assertTrue(RecordPages.dates(emptyList(), emptyList()).isEmpty())
    }
    @Test fun opensTodayOrNewestAndKeepsSelectedDay() {
        assertEquals(1, RecordPages.index(dates, null, dates[1]))
        assertEquals(0, RecordPages.index(dates, null, "2026-10-16"))
        assertEquals(2, RecordPages.index(dates, dates[2], dates[0]))
        assertEquals(0, RecordPages.index(dates, "deleted-day", dates[0]))
        assertEquals(-1, RecordPages.index(emptyList(), null, dates[0]))
    }
}
