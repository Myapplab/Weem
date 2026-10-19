package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.roads.RoadLearner.Companion.wwCleanApprovals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `ww_clean_run_test.dart` (5 test; A6.2a, MAP.md 9.11): ters yön geçişinin "kesintisiz sürüşü" onaysız parçaları da taşır;
 * "hayır" cevabında onaylanacak sokaklar `wwCleanApprovals` ile seçilir. Uygulama: A6.11.
 */
class WwCleanRunTest {
    /** Güneye düz bir yolda saniyede bir fix: ters yönde sokaktan sokağa geçiş. */
    private class Ride {
        val k = RoadKnowledge()
        val learner = RoadLearner(k)
        var t = 1_790_000_000_000L
        var north = 400.0
        val passes = ArrayList<WwPass>()
        var way = 7L
        var name = "Test küçəsi"
        var wrongWay = false
        var approved = false
        var main = false

        fun go(seconds: Int, kmh: Double): Ride {
            repeat(seconds) {
                t += 1000
                north -= kmh / 3.6
                passes.addAll(
                    learner.feed(
                        LearnFix(
                            tMs = t, lat = LAT0 + north / 111320, lon = LON0, kmh = kmh, way = way, name = name,
                            wrongWay = wrongWay, approved = approved, mainRoad = main,
                        ),
                    ),
                )
            }
            return this
        }
    }

    @Test
    fun `onaysız parçadan onaylı parçaya ters yön geçiş ikisini birden taşır`() {
        val r = Ride()
        r.way = 101 // onaysız parça (OSM aynı sokağı ikiye bölmüş)
        r.wrongWay = true
        r.go(20, 18.0)
        r.way = 100 // onaylı parça
        r.approved = true
        r.go(20, 18.0)
        r.way = 102 // çıkış: çift yönlü sokak
        r.name = "Yan küçə"
        r.wrongWay = false
        r.approved = false
        r.go(3, 18.0)
        val p = r.passes.single()
        assertEquals(100L, p.way)
        assertNull("onaysız parçada geçiş sayılmaz (eski kural aynen)", r.k.ww[101L])
        assertNotNull(p.run)
        assertEquals(listOf(101L, 100L), p.run!!.ways.keys.toList())
        assertTrue(p.run!!.ways[101L]!!.meters > RoadLearner.WW_MIN_M)
        assertEquals(mapOf(100L to "Test küçəsi", 101L to "Test küçəsi"), wwCleanApprovals(p))
    }

    @Test
    fun `soru sorulduktan sonra aynı sürüşte girilen parça da sürüşe eklenir`() {
        val r = Ride()
        r.way = 100
        r.wrongWay = true
        r.approved = true
        r.go(20, 18.0)
        r.way = 101 // geçiş burada biter, soru açılır; sürüş sürer
        r.approved = false
        r.go(1, 18.0)
        val p = r.passes.single()
        assertEquals(listOf(100L, 101L), p.run!!.ways.keys.toList())
        r.go(3, 18.0) // cevap gelene kadar 101'de ters yönde ~15 m
        assertSame(r.learner.run, p.run)
        assertTrue(p.run!!.ways[101L]!!.meters < RoadLearner.WW_MIN_M)
        // Kısa ama aynı adlı parça: aynı sokak → onaylanır.
        assertTrue(wwCleanApprovals(p).keys.containsAll(listOf(100L, 101L)))
    }

    @Test
    fun `ana cadde ve kısa başka adlı yol onaylanmaz, uzun isimsiz parça onaylanır`() {
        val r = Ride()
        r.way = 300 // ana cadde (ters yön kesim izniyle)
        r.name = "Böyük prospekt"
        r.main = true
        r.wrongWay = true
        r.go(15, 18.0)
        r.main = false
        r.way = 301 // kavşakta 1 fix başka adlı tek yön
        r.name = "Kəsişən küçə"
        r.go(1, 18.0)
        r.way = 302 // uzun isimsiz parça
        r.name = ""
        r.go(10, 18.0)
        r.way = 100
        r.name = "Test küçəsi"
        r.approved = true
        r.go(20, 18.0)
        r.wrongWay = false
        r.approved = false
        r.way = 102
        r.go(3, 18.0)
        val p = r.passes.single()
        assertEquals(listOf(300L, 301L, 302L, 100L), p.run!!.ways.keys.toList())
        assertTrue(p.run!!.ways[300L]!!.main)
        assertEquals(mapOf(100L to "Test küçəsi", 302L to ""), wwCleanApprovals(p))
    }

    @Test
    fun `ters yön arası uzun normal sürüş önceki sokak bu sürüşe girmez`() {
        val r = Ride()
        r.way = 50
        r.name = "Başqa küçə"
        r.wrongWay = true
        r.go(15, 18.0)
        r.wrongWay = false
        r.way = 51
        r.go(RoadLearner.RUN_GAP_S + 5, 18.0) // kuralına uygun sürüş
        r.way = 100
        r.name = "Test küçəsi"
        r.wrongWay = true
        r.approved = true
        r.go(20, 18.0)
        r.wrongWay = false
        r.approved = false
        r.way = 102
        r.go(3, 18.0)
        val p = r.passes.single()
        assertEquals(listOf(100L), p.run!!.ways.keys.toList())
        assertEquals(mapOf(100L to "Test küçəsi"), wwCleanApprovals(p))
    }

    @Test
    fun `ters yönde ışıkta durmak sürüşü bölmez`() {
        val r = Ride()
        r.way = 101
        r.wrongWay = true
        r.go(10, 18.0)
        r.wrongWay = false // dururken eşleştirici yön veremez
        r.go(RoadLearner.RUN_GAP_S + 8, 0.0)
        r.wrongWay = true
        r.go(1, 18.0)
        r.way = 100
        r.approved = true
        r.go(20, 18.0)
        r.wrongWay = false
        r.approved = false
        r.way = 102
        r.go(3, 18.0)
        assertEquals(listOf(101L, 100L), r.passes.single().run!!.ways.keys.toList())
    }

    companion object {
        const val LAT0 = 40.40
        const val LON0 = 49.85
    }
}
