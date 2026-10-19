package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadGraph
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A* performansı (MAP.md 3.13; plan Bölüm 5 "A* performansı için test"): sentetik ızgara şehir (100 m aralık, kenarların ~%40'ı
 * yaya yolu, ~%15'i tek yön, tepeler), 3–8 km kuş uçuşu bacaklar. Süre CI'da eşik değildir, yalnız rapor
 * (`build/reports/route-perf-<n>.txt`); çekiş sayısı A*'ın keşif kapsamına üst sınır koyar (sezgi bozulursa kırmızı).
 * Ölçek: `WEEM_PERF_GRID` (varsayılan 150 → 22.500 düğüm; 330 ≈ Bakü geniş harita, 204.633 kenar).
 */
class RouterPerfTest {
    private val n = System.getenv("WEEM_PERF_GRID")?.toIntOrNull() ?: 150

    @Test
    fun `sentetik ızgarada 3–8 km bacaklar süre raporu ve çekiş üst sınırı`() {
        val t0 = System.nanoTime()
        val g = TestGraphs.gridCity(n, seed = 7)
        val buildMs = (System.nanoTime() - t0) / 1e6
        val r = Random(11)
        val legs = ArrayList<Pair<GeoPoint, GeoPoint>>()
        while (legs.size < 20) {
            val a = r.nextInt(g.nodeCount)
            val b = r.nextInt(g.nodeCount)
            val d = GeoPoint(g.nodeLat[a], g.nodeLon[a]).distanceTo(GeoPoint(g.nodeLat[b], g.nodeLon[b]))
            if (d in 3000.0..8000.0) legs += GeoPoint(g.nodeLat[a], g.nodeLon[a]) to GeoPoint(g.nodeLat[b], g.nodeLon[b])
        }
        val report = StringBuilder()
        val foot = (0 until g.edgeCount).count { g.classOf(it).isFootClass }
        report.appendLine(
            "graf ${n}x$n: ${g.nodeCount} düğüm, ${g.edgeCount} kenar (yaya %${fmt(100.0 * foot / g.edgeCount)}), kurulum ${fmt(buildMs)} ms",
        )
        var maxPopShare = 0.0
        for (mode in RideMode.entries) {
            // Isınma (JIT): ilk model ve bacak raporlanmaz.
            Router.routeLeg(model(g, mode), legs[0].first, legs[0].second)
            val modelTimes = (1..5).map {
                val m0 = System.nanoTime()
                model(g, mode)
                (System.nanoTime() - m0) / 1e6
            }.sorted()
            val m = model(g, mode)
            val times = ArrayList<Double>()
            var failed = 0
            for ((from, to) in legs) {
                val probe = RouteProbe()
                val s = System.nanoTime()
                try {
                    Router.routeLeg(m, from, to, probe = probe)
                } catch (_: RoutingException) {
                    failed++
                }
                times += (System.nanoTime() - s) / 1e6
                maxPopShare = maxOf(maxPopShare, probe.pops.toDouble() / g.nodeCount)
            }
            times.sort()
            report.appendLine(
                "$mode: CostModel medyan ${fmt(modelTimes[2])} ms (en çok ${fmt(modelTimes.last())}); bacak ort ${fmt(times.average())} ms, " +
                    "medyan ${fmt(times[times.size / 2])} ms, " +
                    "en kötü ${fmt(times.last())} ms; bulunamayan $failed/${legs.size}",
            )
        }
        report.appendLine("en çok çekiş / düğüm: ${fmt(maxPopShare)}")
        // En kötü durumlar: kopuk adaya bacak (bağlı bileşen baştan sona taranır) ve dört duraktan biri adada plan.
        val island = TestGraphs.gridIsland(n)
        val far = legs[1].first
        val probe = RouteProbe()
        var s = System.nanoTime()
        val islandErr = runCatching { Router.routeLeg(model(g, RideMode.TIME), far, island, probe = probe) }.exceptionOrNull()
        report.appendLine(
            "kopuk ada bacağı: ${fmt((System.nanoTime() - s) / 1e6)} ms, çekiş ${probe.pops} (${islandErr?.message ?: "rota var"})",
        )
        val stops = listOf(legs[2].second, legs[3].second, island, legs[4].second)
        val planProbe = RouteProbe()
        s = System.nanoTime()
        val plan = Planner.computePlans(PlanRequest(g, far, stops, RideParams(), emptySet(), emptySet(), departure = Instant.EPOCH), planProbe)
        report.appendLine(
            "4 durak (biri adada) plan: ${fmt((System.nanoTime() - s) / 1e6)} ms, bacak denemesi ${planProbe.legs}, " +
                "çekiş ${planProbe.pops} (${plan.error?.tr ?: "plan var"})",
        )
        File("build/reports").mkdirs()
        File("build/reports/route-perf-$n.txt").writeText(report.toString())
        println(report)
        // Lazy silmeli yığın: bir düğüm birden çok kez çekilebilir; 3–8 km bacakta keşif bütün grafın katlarına çıkmamalı.
        assertTrue("çekiş / düğüm $maxPopShare", maxPopShare < 1.5)
    }

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.1f", v)

    private fun model(g: RoadGraph, mode: RideMode) = CostModel(
        g, mode = mode, params = RideParams(), approved = emptySet(), blocked = emptySet(), departure = Instant.EPOCH,
    )

}
