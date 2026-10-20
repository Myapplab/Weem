package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.MemoryRoadFiles
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.surface.MarkStore
import com.kuryelog.logic.map.ui.Toasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** WEEM eki `MarkStoreTest` (MAP.md 9.11): kötü nokta ekle/sil/geri al, kalıcılık, gün silmede kalır; rota girdileri (9.9). */
class BadSpotAndPlanTest {
    private val clock = FakeClock()
    private val files = MemoryRoadFiles()
    private var settings = MapSettings()
    private fun book() = RoadBook(files, StreetPrefsStore(MemoryMapPrefs()), Toasts(), { settings }, clock)

    @Test
    fun `kötü nokta ekle sil geri al, yeniden açılışta ve gün silmede kalır`() {
        val b = book()
        b.addBadSpot(100, lat(200.0), lon(0.0))
        clock.advance(1000)
        b.addBadSpot(100, lat(210.0), lon(0.0))
        assertEquals(2, b.state.marks.size)
        val removed = b.clearBadSpots(100, lat(205.0), lon(0.0))
        assertEquals(2, removed.size)
        assertTrue(b.state.marks.all { it.removed })
        b.setSpotRemoved(removed.first().id, false)
        b.deleteContributions(com.kuryelog.logic.core.LogTime.day(clock.nowMs, clock.zone()))
        val again = book()
        assertEquals(listOf(false, true), again.state.marks.map { it.removed })
        assertTrue(files.read(MarkStore.FILE)!!.contains("\"label\":\"rough\""))
    }

    @Test
    fun `rota girdileri elle puan, ww oranı ve kaçınma, hayalet ve ww kesimi kenarları`() {
        val g = TestGraphs.directAndDetour()
        val b = book()
        val edge = g.edgesOfOsmWay(100).first()
        assertTrue(b.forPlan(g).ghost.isEmpty())
        b.rate(100, 25)
        repeat(4) { b.addWwPass(101, problem = it < 2) }
        b.markGhost(g, edge)
        b.setWwSegment(g, edge, allow = true)
        assertTrue(b.wwSegAllowed(g, edge))
        b.addBadSpot(100, lat(200.0), lon(0.0))
        val r = b.forPlan(g)
        assertEquals(25, r.learned[100L]!!.r)
        assertEquals((2 + 0.1) / 5, r.wwRatio[101L]!!, 1e-9)
        assertEquals(setOf(101L), r.wwAvoid)
        assertEquals(setOf(edge), r.ghost)
        assertEquals(setOf(edge), r.wrongWayEdges)
        assertEquals(1, r.marks.size)
        settings = MapSettings(wwMaxProblemPct = 60.0)
        assertTrue(b.forPlan(g).wwAvoid.isEmpty())
        b.setWwSegment(g, edge, allow = false)
        assertFalse(b.wwSegAllowed(g, edge))
        b.clearGhosts()
        assertTrue(b.forPlan(g).ghost.isEmpty())
    }

    @Test
    fun `sokağın tamamı yok bütün kenarları kapatır, aynı yerdeki parça işareti değişir`() {
        val g = TestGraphs.directAndDetour()
        val b = book()
        val edges = g.edgesOfOsmWay(100)
        b.markGhost(g, edges.first())
        b.markGhost(g, edges.first())
        assertEquals(1, b.state.k.ghosts.size)
        val all = b.markGhost(g, edges.first(), all = true)
        assertEquals(1, b.state.k.ghosts.size)
        assertTrue(all.all)
        assertEquals(edges.toSet(), b.forPlan(g).ghost)
        b.removeGhost(all)
        assertTrue(b.forPlan(g).ghost.isEmpty())
    }

    @Test
    fun `sorun yaşadım süren geçişte ve 90 sn içinde biten geçişte`() {
        val b = book()
        b.startDay("yolgo-${clock.nowMs}")
        var n = 0.0
        fun go(s: Int, ww: Boolean, way: Long = 9) = repeat(s) {
            clock.advance(1000)
            n += 5
            b.onFix(LearnFix(clock.nowMs, lat(n), lon(0.0), 18.0, way = way, name = "X", wrongWay = ww, approved = way == 9L))
        }
        go(10, true)
        assertEquals(9L, b.state.live?.way)
        go(1, false, way = 10)
        assertTrue(b.canReportRecent())
        assertTrue(b.reportProblem())
        assertEquals(1.0, b.state.k.ww[9L]!!.problems, 0.0)
        assertFalse(b.canReportRecent())
        assertEquals("Ters yön sorunu kaydedildi", b.toasts.current?.text)
    }
}
