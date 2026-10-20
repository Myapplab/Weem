package com.kuryelog.logic.map.roads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `road_learner_test.dart` (9 test; A6.2a, MAP.md 9.11): sokak başına metre/süre, dur-kalk, titreşim ve onaylı sokakta
 * ters yön geçişi. Uygulama: A6.11.
 */
class RoadLearnerTest {
    /** Kuzeye düz bir yolda saniyede bir fix üreten sürücü. */
    private class Ride {
        val k = RoadKnowledge()
        val learner = RoadLearner(k)
        var t = 1_790_000_000_000L
        var north = 0.0
        val passes = ArrayList<WwPass>()
        var way = 7L
        var wrongWay = false
        var approved = false
        var exclude = false

        fun go(seconds: Int, kmh: Double, vib: Double = 1.5): Ride {
            repeat(seconds) {
                t += 1000
                north += kmh / 3.6
                passes.addAll(
                    learner.feed(
                        LearnFix(
                            tMs = t, lat = LAT0 + north / 111320, lon = LON0, kmh = kmh, way = way, name = "Test küçəsi",
                            wrongWay = wrongWay, approved = approved, vib = vib, excludeStops = exclude,
                        ),
                    ),
                )
            }
            return this
        }

        val st get() = k.ways[7L]!!
    }

    @Test
    fun `mesafe, süre ve isim öğrenilir, sokak değişince geçiş sayılır`() {
        val r = Ride().go(30, 20.0)
        assertEquals(161.0, r.st.m, 5.0) // ilk fix'in öncesi yok: 29 sn × 5.56 m
        assertEquals(29.0, r.st.s, 0.5)
        assertEquals("Test küçəsi", r.k.nameOf(7))
        assertEquals(0, r.st.passes)
        r.way = 8
        r.go(5, 20.0)
        assertEquals(1, r.st.passes)
    }

    @Test
    fun `tam durma 1, durmadan yavaşlama 0,5 dur-kalk`() {
        val r = Ride().go(20, 20.0).go(6, 0.0) /* ışık */.go(20, 20.0).go(3, 6.0) /* yaya geçidi: yavaşladı, durmadı */.go(20, 20.0)
        assertEquals(1.5, r.st.stops, 1e-9)
        assertTrue(r.st.score < 1)
    }

    @Test
    fun `uzun bekleme (park teslimat) ve hedef yakını dur-kalk sayılmaz`() {
        val r = Ride().go(20, 20.0).go(200, 0.0) /* 3 dk+ bekleme */.go(20, 20.0)
        assertEquals(0.0, r.st.stops, 0.0)
        r.exclude = true
        r.go(6, 0.0).go(10, 20.0)
        assertEquals(0.0, r.st.stops, 0.0)
    }

    @Test
    fun `kısa durma (en çok 2 sn) dur-kalk değil, yarım sayılır`() {
        val r = Ride().go(20, 20.0).go(2, 0.0).go(20, 20.0)
        assertEquals(0.5, r.st.stops, 0.0)
    }

    @Test
    fun `titreşim sadece sürerken ölçülür`() {
        val r = Ride().go(30, 20.0, vib = 5.0).go(30, 0.0, vib = 9.0)
        assertEquals(5.0, r.st.vib!!, 1e-9)
    }

    @Test
    fun `onaylı sokakta ters yön geçiş ve sert fren olayı`() {
        val r = Ride().go(5, 20.0)
        r.way = 9
        r.wrongWay = true
        r.approved = true
        r.go(10, 20.0).go(1, 8.0) /* 20 → 8 km/s: sert fren */.go(10, 15.0)
        r.wrongWay = false
        r.go(3, 15.0)
        assertEquals(1, r.passes.size)
        val p = r.passes.single()
        assertEquals(9L, p.way)
        assertTrue(p.incidents >= 1)
        assertTrue(p.meters > 25)
        assertEquals(1, r.k.ww[9L]!!.passes)
        assertEquals(0.0, r.k.ww[9L]!!.problems, 0.0) // cevap gelene kadar sorun yazılmaz
    }

    @Test
    fun `ters yönde durup devam etmek tek geçiştir (durma = olay)`() {
        val r = Ride()
        r.way = 9
        r.approved = true
        r.wrongWay = true
        r.go(8, 18.0)
        r.wrongWay = false // eşleştirici dururken yön vermez
        r.go(6, 0.0)
        r.wrongWay = true
        r.go(8, 18.0)
        r.wrongWay = false
        r.go(3, 18.0)
        assertEquals(1, r.passes.size)
        assertTrue(r.passes.single().incidents >= 1)
        assertEquals(1, r.k.ww[9L]!!.passes)
    }

    @Test
    fun `ters yön onaysız ya da çok kısa geçiş sayılmaz, sorun yaşadım sayılır`() {
        val r = Ride()
        r.way = 9
        r.wrongWay = true
        r.approved = false
        r.go(20, 20.0)
        r.wrongWay = false
        r.go(3, 20.0)
        assertTrue(r.passes.isEmpty())

        r.wrongWay = true
        r.approved = true
        r.go(2, 20.0) // ~11 m
        r.wrongWay = false
        r.go(3, 20.0)
        assertTrue(r.passes.isEmpty())

        r.wrongWay = true
        r.go(10, 20.0)
        assertEquals(9L, r.learner.liveWw?.way)
        assertTrue(r.learner.markLiveReported())
        r.wrongWay = false
        r.go(3, 20.0)
        assertTrue(r.passes.single().reported)
        assertEquals(1.0, r.k.ww[9L]!!.problems, 0.0)
        assertEquals(1.0, r.k.ww[9L]!!.ratio, 0.0)
    }

    @Test
    fun `bilgi kaydedilip geri okunur`() {
        val r = Ride().go(30, 20.0).go(6, 0.0).go(10, 20.0)
        val k = r.k
        k.ratings[7L] = 35
        k.ghosts.add(GhostMark(way = 5, lat = 40.4, lon = 49.8, name = "Yox küçə", at = 1, all = true))
        k.wwStats(9).apply { passes = 4; problems = 1.5 }

        val ways = RoadKnowledge.decodeMap(RoadKnowledge.encodeMap(k.ways) { it.toJson() }, WayStats::fromJson)
        assertEquals(k.ways[7L]!!.stops, ways[7L]!!.stops, 0.0)
        assertEquals(k.ways[7L]!!.m, ways[7L]!!.m, 1.0)
        val ww = RoadKnowledge.decodeMap(RoadKnowledge.encodeMap(k.ww) { it.toJson() }, WwStats::fromJson)
        assertEquals(1.5, ww[9L]!!.problems, 0.0)
        val ghosts = RoadKnowledge.decodeGhosts(RoadKnowledge.encodeGhosts(k.ghosts))
        assertTrue(ghosts.single().all)
        assertEquals("Yox küçə", ghosts.single().name)
        val rates = RoadKnowledge.decodeMap(RoadKnowledge.encodeMap(k.ratings) { it }) { (it as Number).toInt() }
        assertEquals(35, rates[7L])

        val learned = k.learnedForRouting()
        assertEquals(35, learned[7L]!!.r)
        assertEquals(setOf(9L), k.wrongWayAvoid(0.3)) // 1.5/4 = %37.5 ≥ %30, 4 geçiş
        assertTrue(k.wrongWayAvoid(0.5).isEmpty())
    }


    companion object {
        const val LAT0 = 40.40
        const val LON0 = 49.85
    }
}
