package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `bikeway_e13_test.dart` "elle işaret yerel ve kalıcı, geri alınabilir; diğer sokak listeleri değişmez" (A6.2a, MAP.md 5.2).
 * Tercih `streets.bikeway.v1`, öğe `"<wayId>|<ad>"` (ad boş olabilir). Uygulama: A6.8.
 */
class BikewayMarksTest {
    @Test
    fun `elle işaret yerel ve kalıcı, geri alınabilir, diğer sokak listeleri değişmez`() {
        val prefs = MemoryMapPrefs(mapOf("streets.approved" to listOf("5"), "streets.blocked.migrated.v2" to true))
        val first = BikewayMarksStore(prefs)
        assertTrue(first.marks.ways.isEmpty())
        first.mark(401, "Test küçəsi")
        first.mark(402, "")
        assertTrue(prefs.getStringList(BikewayMarksStore.KEY)!!.containsAll(listOf("401|Test küçəsi", "402|")))
        val again = BikewayMarksStore(prefs) // yeniden açılış
        assertEquals(setOf(401L, 402L), again.marks.ways)
        assertEquals("Test küçəsi", again.marks.names[401L])
        again.unmark(401)
        assertEquals(setOf(402L), again.marks.ways)
        assertEquals(listOf("402|"), prefs.getStringList(BikewayMarksStore.KEY))
        assertEquals(setOf(5L), StreetPrefsCodec.load(prefs).approved)
        assertEquals(listOf("5"), prefs.getStringList("streets.approved"))
    }
}
