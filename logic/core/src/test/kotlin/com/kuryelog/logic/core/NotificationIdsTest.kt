package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** G4, CORE.md 3.3 (yeni test): tek tablo, ayrık numaralar; 5103 iki kez yok, nakit bahşiş aralığı başka numaraya denk gelmez. */
class NotificationIdsTest {
    @Test fun everyNumberAndRangeIsDisjoint() {
        val entries = NotificationIds.TABLE.entries.toList()
        for (i in entries.indices) for (j in i + 1 until entries.size) {
            val (a, ra) = entries[i]; val (b, rb) = entries[j]
            assertTrue("$a $ra ∩ $b $rb", ra.last < rb.first || rb.last < ra.first)
        }
        assertEquals(19, entries.size) // A8.3: LINK_FGS
    }

    @Test fun theTwoOldCollisionsAreGone() {
        // 5103 was both the H4 shift notice and the screen reading warning; 4000–8095 covered 4901…7435.
        assertEquals(5103, NotificationIds.SHIFT_INTERRUPTED)
        assertEquals(5104, NotificationIds.SCREEN_READING_OFF)
        for (id in listOf(4901, 4902, 5101, 5102, 5103, 5104, 7100, 7101, 7102, 7201, 7301, 7401, 7435)) assertFalse(id in NotificationIds.CASH_TIP)
    }

    @Test fun cashTipIdsStayInTheirRange() {
        val trips = listOf("", "a", "wolt-123", "bolt:9f8e", "x".repeat(500), "\u0000", "Aa", "BB") + (0 until 5000).map { "trip-$it" }
        for (t in trips) assertTrue(t, NotificationIds.cashTip(t) in NotificationIds.CASH_TIP)
        // Rawstats' expression, moved from 4000 to 10000.
        assertEquals(10_000 + ("wolt-123".hashCode() and 0xFFF), NotificationIds.cashTip("wolt-123"))
        assertEquals(NotificationIds.CASH_TIP.first + 0xFFF, NotificationIds.CASH_TIP.last)
    }

    @Test fun remindersAndTheShiftEndList() {
        assertEquals(listOf(7100, 7101, 7102), NotificationIds.ReminderKind.entries.map(NotificationIds::reminder))
        assertEquals(listOf(12, 7100, 7101, 7102, 7201, 7301), NotificationIds.SHIFT_END_CANCEL)
        val all = NotificationIds.TABLE.values
        for (id in NotificationIds.SHIFT_END_CANCEL) assertTrue("$id", all.any { id in it })
        assertFalse("7400 is not used (YolGo's day recording merged into the shift service)", all.any { 7400 in it })
    }

    @Test fun sourcesUseTheTableNotBareNumbers() {
        val bare = Regex("""\b(?:notify|cancel|startForeground)\(\s*\d""")
        val found = RepoSource.mainKotlin().filter { it.name != "NotificationIds.kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (bare.containsMatchIn(line)) "${f.name}:${i + 1}: ${line.trim()}" else null }
        }
        assertEquals(emptyList<String>(), found)
    }
}
