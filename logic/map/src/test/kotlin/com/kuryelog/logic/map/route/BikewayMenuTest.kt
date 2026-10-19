package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.ui.BikewayMenu
import com.kuryelog.logic.map.ui.BikewayMenuAction
import com.kuryelog.logic.map.ui.Toasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `bikeway_e13_test.dart` "uzun basma menüsü: sokağı işaretler, toast Geri al ile geri alır; OSM'de zaten varsa işaret
 * gerekmez" (A6.2a, MAP.md 5.2). Saf kural: Riverpod/Toast yerine `BikewayMarksStore` + `Toasts`. Uygulama: A6.8.
 */
class BikewayMenuTest {
    @Test
    fun `uzun basma menüsü sokağı işaretler, bildirim Geri al ile geri alır, OSM'de zaten varsa işaret gerekmez`() {
        val g = TestGraphs.parallel(east = mapOf("highway" to "residential", "cycleway" to "lane"))
        val store = BikewayMarksStore(MemoryMapPrefs())
        val toasts = Toasts()
        val west = TestGraphs.edgeOf(g, 302)
        assertEquals(BikewayMenuAction.MARK, BikewayMenu.action(g, west, store.marks))
        assertEquals(BikewayMenuAction.ALREADY_OSM, BikewayMenu.action(g, TestGraphs.edgeOf(g, 402), store.marks))
        BikewayMenu.toggleWithUndo(store, toasts, g, west, "Batı küçəsi")
        assertEquals(setOf(302L), store.marks.ways)
        assertEquals("Bisiklet yolu işaretlendi: Batı küçəsi", toasts.current?.text)
        assertEquals(BikewayMenuAction.UNMARK, BikewayMenu.action(g, west, store.marks))
        toasts.current!!.onAction!!() // Geri al
        assertTrue(store.marks.ways.isEmpty())
        BikewayMenu.toggleWithUndo(store, toasts, g, west, "Batı küçəsi")
        BikewayMenu.toggleWithUndo(store, toasts, g, west, "Batı küçəsi") // menüden kaldır
        assertTrue(store.marks.ways.isEmpty())
        assertEquals("Bisiklet yolu işareti kaldırıldı: Batı küçəsi", toasts.current?.text)
        toasts.current!!.onAction!!() // kaldırmayı geri al
        assertEquals(setOf(302L), store.marks.ways)
    }
}
