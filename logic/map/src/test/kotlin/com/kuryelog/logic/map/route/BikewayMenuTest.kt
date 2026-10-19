package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.TestGraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `bikeway_e13_test.dart` "uzun basma menüsü: sokağı işaretler, toast Geri al ile geri alır; OSM'de zaten varsa işaret gerekmez" (1 test); `MAP.md` 5.2. */
class BikewayMenuTest {
    private class RecordingToasts : MapToasts {
        var last: MapToast? = null
        override fun show(toast: MapToast) { last = toast }
    }

    @Test
    fun `uzun basma menüsü - sokağı işaretler, toast Geri al ile geri alır - OSM de zaten varsa işaret gerekmez`() {
        val g = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "lane"))
        val marks = BikewayMarksStore(MemoryMapPrefs())
        val toasts = RecordingToasts()
        val west = TestGraphs.edge(g, 302)
        assertEquals(BikewayMenuAction.MARK, bikewayMenuAction(g, west, marks.marks))
        assertEquals(BikewayMenuAction.ALREADY_OSM, bikewayMenuAction(g, TestGraphs.edge(g, 402), marks.marks))
        toggleBikewayWithUndo(marks, toasts, g, west, "Batı küçəsi")
        assertEquals(setOf(302L), marks.marks.ways)
        assertEquals("Bisiklet yolu işaretlendi: Batı küçəsi", toasts.last?.text)
        assertEquals(BikewayMenuAction.UNMARK, bikewayMenuAction(g, west, marks.marks))
        toasts.last!!.onAction!!() // Geri al
        assertTrue(marks.marks.ways.isEmpty())
        toggleBikewayWithUndo(marks, toasts, g, west, "Batı küçəsi")
        toggleBikewayWithUndo(marks, toasts, g, west, "Batı küçəsi") // menüden kaldır
        assertTrue(marks.marks.ways.isEmpty())
        assertEquals("Bisiklet yolu işareti kaldırıldı: Batı küçəsi", toasts.last?.text)
        assertNotNull(toasts.last!!.onAction)
        toasts.last!!.onAction!!() // kaldırmayı geri al
        assertEquals(setOf(302L), marks.marks.ways)
    }
}
