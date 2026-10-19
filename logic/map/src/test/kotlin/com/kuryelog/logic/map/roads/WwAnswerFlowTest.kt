package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.MemoryRoadFiles
import com.kuryelog.logic.map.geo.Geo
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.graph.WayFlag
import com.kuryelog.logic.map.route.StreetPrefsCodec
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.ui.Toasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `ww_no_problem_green_test.dart` (5 test, 6 çalışma; A6.2a, MAP.md 9.11): Y9 saha hatası — ters yönden gelince "Ters yönde
 * sorun yaşadın mı?" soruldu, "Hayır" denildi ama OSM'nin böldüğü aynı sokağın onaysız parçası onaylanmıyordu. Şimdi "hayır"
 * geçişin bütün parçalarını hemen ve kalıcı onaylar. Uyarlamalar: Dart'ın GPS → NavController → RoadController yolu, saf sahte
 * eşleştirme ([Rig.fix]: `nearestEdge` + tek yön bayrağı + yön farkı) ve `VolumeDispatch` olur; "yeşil çizgi sayısı" yerine onaylı
 * kenar sayısı (katman işlevi Bölüm 6); TR/AZ kart testi saf (`WrongWayCardText` + akış). Kartın Compose dokunuşu (`:app`
 * Robolectric `WrongWayCardTest`) A6.11'de ekranla birlikte eklenir (`TEST_ENVANTERI`). Uygulama: A6.11.
 */
class WwAnswerFlowTest {
    private class Rig(askAlways: Boolean = false, val lang: Lang = Lang.TR) {
        val g: RoadGraph = TestGraphs.answerGraph()
        val prefs = MemoryMapPrefs()
        val streets = StreetPrefsStore(prefs)
        val toasts = Toasts()
        val clock = FakeClock()
        val book = RoadBook(MemoryRoadFiles(), streets, toasts, { MapSettings(wwAskAlways = askAlways) }, clock, { lang })
        val volume = VolumeDispatch(book, clock)

        init {
            book.startDay("yolgo-${clock.nowMs}")
            streets.approve(100, "Test küçəsi")
        }

        fun edges(way: Long) = g.edgesOfOsmWay(way).size
        fun approvedEdges() = (0 until g.edgeCount).count { g.osmIdOf(it) in streets.current.approved }

        /** Ters yönde güneye sürüş (saniyede bir fix, 18 km/s): sahte eşleştirme → öğrenme. */
        fun fix(east: Double, north: Double, kmh: Double = 18.0, heading: Double = 180.0) {
            clock.advance(1000)
            val la = lat(north)
            val lo = lon(east)
            val hit = g.nearestEdge(la, lo, maxDist = 25.0)
            val way = hit?.let { g.osmIdOf(it.edge) }
            val oneway = hit != null && (g.flagsOf(hit.edge) and WayFlag.ONEWAY_FWD) != 0
            book.onFix(
                LearnFix(
                    tMs = clock.nowMs, lat = la, lon = lo, kmh = kmh, way = way, name = hit?.let { g.nameOf(it.edge) } ?: "",
                    wrongWay = oneway && Geo.angleDiff(heading, hit!!.segBearing) > 90,
                    approved = way != null && way in streets.current.approved,
                ),
            )
        }

        /** 100'de ters yön (isteğe bağlı durma = olay), 101'e girince soru. */
        fun rideIntoQuestion(stop: Boolean = true): WwQuestion {
            var n = 295.0
            while (n >= 225) { fix(0.0, n); n -= 5 }
            if (stop) repeat(4) { fix(0.0, 220.0, kmh = 0.0) }
            n = 215.0
            while (n >= 155) { fix(0.0, n); n -= 5 }
            assertNull(book.state.question)
            fix(0.0, 145.0) // 101'e girildi: 100'deki geçiş bitti
            val q = book.state.question
            assertNotNull("Ters yönde sorun yaşadın mı? sorusu açılmalı", q)
            for (m in listOf(140.0, 135.0, 130.0)) fix(0.0, m) // cevap verirken 101'de sürüyor
            return q!!
        }

        /** Sürüş 101'de sürer, batıya çıkar. */
        fun rideOut() {
            var n = 125.0
            while (n >= 5) { fix(0.0, n); n -= 5 }
            var e = -5.0
            while (e >= -40) { fix(e, 0.0, heading = 270.0); e -= 5 }
        }
    }

    @Test
    fun `saha sert fren durmalı geçiş, Ses- (hayır) iki parça hemen onaylı, yeniden açılışta da`() {
        val r = Rig()
        val q = r.rideIntoQuestion()
        assertEquals(100L, q.pass.way)
        assertTrue(q.pass.incidents >= 1)
        assertEquals(setOf(100L), r.streets.current.approved)
        assertEquals("önce yalnız 100 onaylı", r.edges(100), r.approvedEdges())

        r.volume.onKey(up = false) // "Ses+ evet / Ses− hayır"

        assertNull(r.book.state.question)
        assertEquals(setOf(100L, 101L), r.streets.current.approved)
        assertTrue(r.prefs.getStringList("streets.approved")!!.containsAll(listOf("100", "101")))
        assertEquals("geçilen parçalar onaylı", r.edges(100) + r.edges(101), r.approvedEdges())
        assertEquals("Onaylandı: Test küçəsi (iki yön)", r.toasts.current?.text)
        assertEquals(0.0, r.book.state.k.ww[100L]!!.problems, 0.0)

        // Sürüş 101'de (artık onaylı) sürer, çıkışta aynı sürüş yeniden sorulmaz.
        r.rideOut()
        assertNull(r.book.state.question)
        assertEquals(1, r.book.state.k.ww[101L]!!.passes)
        assertEquals(0.0, r.book.state.k.ww[101L]!!.problems, 0.0)

        // Yeniden açılış: aynı kayıtlardan okunur.
        assertEquals(setOf(100L, 101L), StreetPrefsCodec.load(r.prefs).approved)
    }

    @Test
    fun `Her geçişten sonra sor açık, olaysız geçişte de sorulur, hayır onaylı, devamı yeniden sorulmaz`() {
        val r = Rig(askAlways = true)
        val q = r.rideIntoQuestion(stop = false)
        assertEquals(0, q.pass.incidents)
        assertTrue(r.book.answer(problem = false))
        assertEquals(setOf(100L, 101L), r.streets.current.approved)
        r.rideOut()
        assertEquals("101 geçişi sayıldı", 1, r.book.state.k.ww[101L]!!.passes)
        assertNull("aynı sürüş için ikinci soru yok", r.book.state.question)
    }

    @Test
    fun `Evet sorun (Ses+) bugünkü gibi, sorun sayılır, onaysız parça onaylanmaz`() {
        val r = Rig()
        r.rideIntoQuestion()
        r.volume.onKey(up = true)
        assertNull(r.book.state.question)
        assertEquals(1.0, r.book.state.k.ww[100L]!!.problems, 0.0)
        assertEquals(setOf(100L), r.streets.current.approved)
        assertEquals(listOf("100"), r.prefs.getStringList("streets.approved"))
        assertEquals(r.edges(100), r.approvedEdges())
        assertFalse(r.toasts.current?.text.orEmpty().startsWith("Onaylandı"))
    }

    @Test
    fun `Geri al yeni onayı kaldırır, önceden onaylı parça kalır`() {
        val r = Rig()
        r.rideIntoQuestion()
        val before = r.book.state.approvedRev
        r.book.answer(problem = false)
        assertEquals(before + 1, r.book.state.approvedRev)
        val toast = r.toasts.current!!
        assertEquals("Geri al", toast.actionLabel)
        toast.onAction!!()
        assertEquals(setOf(100L), r.streets.current.approved)
        assertEquals(listOf("100"), r.prefs.getStringList("streets.approved"))
    }

    @Test
    fun `kart TR ve AZ metinleri, Hayır Xeyr dokunuşu iki parçayı onaylar ve toast metni`() {
        for (lang in listOf(Lang.TR, Lang.AZ)) {
            // "Her geçişten sonra sor" açık: olaysız geçişte de kart açılır.
            val r = Rig(askAlways = true, lang = lang)
            // Öğrenmeye doğrudan fix: 101 (onaysız) → 100 (onaylı) ters yönde, sonra çıkış.
            var north = 0.0
            fun feed(way: Long, seconds: Int, wrongWay: Boolean = true, approved: Boolean = false) {
                repeat(seconds) {
                    r.clock.advance(1000)
                    north += 5
                    r.book.onFix(
                        LearnFix(
                            tMs = r.clock.nowMs, lat = lat(north), lon = TestGraphs.LON0, kmh = 18.0, way = way,
                            name = if (way == 102L) "Yan küçə" else "Test küçəsi", wrongWay = wrongWay, approved = approved,
                        ),
                    )
                }
            }
            feed(101, 20)
            feed(100, 20, approved = true)
            feed(102, 1, wrongWay = false)
            assertNotNull(r.book.state.question)
            assertEquals(if (lang == Lang.AZ) "Əks istiqamətdə problem oldu?" else "Ters yönde sorun yaşadın mı?", WrongWayCardText.title(lang))
            assertEquals(if (lang == Lang.AZ) "Xeyr" else "Hayır", WrongWayCardText.no(lang))
            assertTrue(r.book.answer(problem = false)) // "Hayır" / "Xeyr" dokunuşu
            assertNull(r.book.state.question)
            assertEquals(setOf(100L, 101L), r.streets.current.approved)
            assertTrue(r.prefs.getStringList("streets.approved")!!.containsAll(listOf("100", "101")))
            assertEquals(
                if (lang == Lang.AZ) "Təsdiqləndi: Test küçəsi (iki tərəfli)" else "Onaylandı: Test küçəsi (iki yön)",
                r.toasts.current?.text,
            )
        }
    }
}
