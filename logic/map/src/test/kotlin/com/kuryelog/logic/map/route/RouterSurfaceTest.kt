package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.graph.GraphBuilder
import com.kuryelog.logic.map.graph.OsmRaw
import com.kuryelog.logic.map.graph.OsmWay
import com.kuryelog.logic.map.graph.RoadGraph
import com.kuryelog.logic.map.route.TestGraphs.RESIDENTIAL
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.route.TestGraphs.p
import com.kuryelog.logic.map.surface.Passability
import com.kuryelog.logic.map.surface.SurfaceKey
import com.kuryelog.logic.map.surface.SurfaceLabel
import com.kuryelog.logic.map.surface.SurfaceMark
import com.kuryelog.logic.map.surface.SurfaceSummary
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Y13 ölçülmüş yüzey ve rota (A10.4b): YolGo `router_test.dart` @ 870ab6e — "physical bad segment…", "passable rough-only path…",
 * "1.7.2: daha düzgün yol en fazla 200 m…", "E11 (C4)…" (3 test) ve cadde oranı satırlarının ölçülmüş yüzeyli kısmı. Arka plan rota işçisi
 * sınaması yok (WEEM'de ayrı işçi yok).
 */
class RouterSurfaceTest {
    private val a = p(0.0, 0.0)
    private val b = p(0.0, 400.0)
    private val now = 1_790_000_000_000L

    private fun summary(g: RoadGraph, score: Double, confidence: Double = .9, direction: Int = 0, calibrated: Boolean = true, meters: Double = 200.0,
                        roughM: Double = 200.0, length: Double = 50.0, grid: Double = 50.0, passes: Int = 4): SurfaceSummary {
        val from = g.edgeFrom[TestGraphs.edgeOf(g, 100)]
        return SurfaceSummary(key = SurfaceKey.of(100, SurfaceKey.anchor(g.nodeLat[from], g.nodeLon[from]), direction, 0), way = 100, direction = direction, segmentIndex = 0,
            profile = "profile", score = score, confidence = confidence, meters = meters, roughM = roughM, passes = passes, lastAt = now, rms = 1.2, accuracy = 4.0, impacts = 0,
            hardest = 0.0, lat = lat(25.0), lon = lon(0.0), calibrated = calibrated, segmentLength = length, segmentGridM = grid)
    }

    private fun badSurface(g: RoadGraph, confidence: Double = .9, direction: Int = 0) = summary(g, 15.0, confidence, direction)

    private fun ride(r: PlanRequest) = Planner.computePlans(r).plans[RideMode.TIME]!!.rideM
    private fun request(g: RoadGraph, surface: List<SurfaceSummary>, quality: Map<String, Double> = emptyMap(), blocked: Set<Long> = emptySet(), marks: List<SurfaceMark> = emptyList()) =
        PlanRequest(g, a, listOf(b), RideParams(), emptySet(), blocked, marks = marks, surface = surface, surfaceQuality = quality)

    @Test fun physicalBadSegmentAvoidsAlternativeOnlyWithSufficientConfidenceAndMatchingDirection() {
        val g = TestGraphs.directAndDetour()
        assertEquals(800.0, ride(request(g, listOf(badSurface(g)))), 5.0)
        assertEquals(400.0, ride(request(g, listOf(badSurface(g, confidence = .1)))), 5.0)
        assertEquals(400.0, ride(request(g, listOf(badSurface(g, direction = 1)))), 5.0)
    }

    @Test fun uncalibratedMeasurementNeverExcludesASegment() {
        val g = TestGraphs.directAndDetour()
        // Kalibre olmayan kesim dışlanmaz (yalnız maliyete girer): en kısa yol kalır.
        assertEquals(400.0, ride(request(g, listOf(summary(g, 15.0, calibrated = false)))), 5.0)
    }

    @Test fun passableRoughOnlyPathFallsBackWithWarningWhileClosureNeverFallsBack() {
        val g = TestGraphs.directAndDetour()
        fun req(access: Passability? = null) = request(g, listOf(badSurface(g)), blocked = setOf(201L, 202L, 203L),
            marks = if (access == null) emptyList() else listOf(SurfaceMark("closed", 100, lat(25.0), lon(0.0), now, access = access)))
        val fallback = Planner.computePlans(req()).plans[RideMode.TIME]!!
        assertEquals(400.0, fallback.rideM, 5.0)
        assertTrue(fallback.legs.single().warnings.isNotEmpty())
        assertTrue(Planner.computePlans(req(Passability.CLOSED)).plans.isEmpty())
    }

    @Test fun removedClosedMarkNoLongerClosesTheStreet() {
        val g = TestGraphs.directAndDetour()
        val closed = SurfaceMark("closed", 100, lat(25.0), lon(0.0), now, access = Passability.CLOSED)
        assertEquals(800.0, ride(request(g, emptyList(), marks = listOf(closed))), 5.0)
        assertEquals(400.0, ride(request(g, emptyList(), marks = listOf(closed.copy(removed = true)))), 5.0)
        // Ses− kötü nokta ("kapalı" değil) yolu kapatmaz.
        assertEquals(400.0, ride(request(g, emptyList(), marks = listOf(closed.copy(access = Passability.UNKNOWN, label = SurfaceLabel.POTHOLE)))), 5.0)
    }

    private fun parallel(east: Double): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0)); raw.addNode(2, lat(200.0), lon(0.0)); raw.addNode(3, lat(400.0), lon(0.0))
        raw.addNode(4, lat(0.0), lon(east)); raw.addNode(5, lat(400.0), lon(east))
        raw.addWay(OsmWay(100, listOf(1, 2, 3), RESIDENTIAL))
        raw.addWay(OsmWay(201, listOf(1, 4), RESIDENTIAL)); raw.addWay(OsmWay(202, listOf(4, 5), RESIDENTIAL)); raw.addWay(OsmWay(203, listOf(5, 3), RESIDENTIAL))
        return GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }
    }

    @Test fun smootherRoadIsChosenIfItAddsAtMost200mButNotMore() {
        // A(0,0)→B(0,400) doğrudan (way 100, pürüzlü) ya da doğuda paralel sokak (A→E→F→B).
        fun ride(east: Double, measured: Boolean = true): Double {
            val g = parallel(east)
            return ride(request(g, if (measured) listOf(summary(g, 30.0, confidence = 1.0, meters = 1600.0, roughM = 0.0, length = 400.0, grid = 400.0)) else emptyList()))
        }
        assertEquals(400.0, ride(100.0, measured = false), 5.0) // ölçüm yok: en kısa
        assertEquals(600.0, ride(100.0), 5.0) // +200 m: düzgün paralel sokak
        assertEquals(400.0, ride(150.0), 5.0) // +300 m: en kısa kalır
    }

    // ---- E11 (C4): rota yeni sokak puanını kullanır, +200 m / +2 dönüş kuralı aynen ----

    /** A(0,0)→B(0,400): doğrudan way 100 (ölçülmüş tek 400 m parça) ya da doğuda bir dolambaç. [zigzag]: 600 m; [stubs] köşeleri kavşak yapar (4 dönüş). */
    private fun detour(east: Double, zigzag: Boolean = false, stubs: Boolean = false): RoadGraph {
        val raw = OsmRaw()
        raw.addNode(1, lat(0.0), lon(0.0)); raw.addNode(2, lat(200.0), lon(0.0)); raw.addNode(3, lat(400.0), lon(0.0))
        raw.addWay(OsmWay(100, listOf(1, 2, 3), RESIDENTIAL))
        if (!zigzag) {
            raw.addNode(4, lat(0.0), lon(east)); raw.addNode(5, lat(400.0), lon(east))
            raw.addWay(OsmWay(201, listOf(1, 4, 5, 3), RESIDENTIAL))
        } else {
            val corners = listOf(50.0 to 0.0, 50.0 to 200.0, 100.0 to 200.0, 100.0 to 400.0)
            corners.forEachIndexed { i, c -> raw.addNode(10L + i, lat(c.second), lon(c.first)) }
            raw.addWay(OsmWay(301, listOf(1, 10, 11, 12, 13, 3), RESIDENTIAL))
            if (stubs) {
                // Köşelerden düz devam eden çıkmaz kısa yollar: dönüş yalnız ≥3 kollu kavşakta sayılır.
                val ends = listOf(80.0 to 0.0, 50.0 to 230.0, 130.0 to 200.0, 100.0 to 430.0)
                ends.forEachIndexed { i, c ->
                    raw.addNode(20L + i, lat(c.second), lon(c.first))
                    raw.addWay(OsmWay(400L + i, listOf(10L + i, 20L + i), RESIDENTIAL))
                }
            }
        }
        return GraphBuilder.buildRoadGraph(raw) { _, _ -> 0.0 }
    }

    private fun measured(g: RoadGraph, oldScore: Double) = summary(g, oldScore, confidence = 1.0, calibrated = false, meters = 1600.0, roughM = 0.0, length = 400.0, grid = 400.0)
    private fun e11(g: RoadGraph, oldScore: Double = 90.0, street: Double? = null): PlanResult {
        val s = measured(g, oldScore)
        return Planner.computePlans(request(g, listOf(s), if (street == null) emptyMap() else mapOf(s.key to street)))
    }
    private fun e11Ride(g: RoadGraph, oldScore: Double = 90.0, street: Double? = null) = e11(g, oldScore, street).plans[RideMode.TIME]!!.rideM
    private val bad = 1.0 / 9 // Sokak puanı 2/10 → rota kalitesi (2-1)/9.

    @Test fun oldScoreGoodNewStreetScoreBadPlus200mChosenPlus300mNot() {
        assertEquals(400.0, e11Ride(detour(100.0)), 5.0) // yeni puan yok: eski puan (90) iyi, en kısa
        assertEquals(600.0, e11Ride(detour(100.0), street = bad), 5.0) // +200 m: düzgün paralel sokak
        assertEquals(400.0, e11Ride(detour(150.0), street = bad), 5.0) // +300 m: en kısa kalır
    }

    @Test fun oldScoreBadNewStreetScoreTenOfTenShortestStays() {
        assertEquals(600.0, e11Ride(detour(100.0), oldScore = 30.0), 5.0) // eski puanla dolambaç
        assertEquals(400.0, e11Ride(detour(100.0), oldScore = 30.0, street = 1.0), 5.0)
    }

    @Test fun plus200mButPlusFourTurnsNotChosen() {
        assertEquals(600.0, e11Ride(detour(0.0, zigzag = true), street = bad), 5.0) // kavşak yok → 0 dönüş: seçilir
        val g = detour(0.0, zigzag = true, stubs = true)
        val smooth = e11(g, street = bad).plans[RideMode.TIME]!!
        assertEquals(400.0, smooth.rideM, 5.0)
        assertTrue(smooth.legs.single().turns.isEmpty())
    }

    @Test fun streetRatioAddsMeasuredBadSegmentAndMergesSpots() {
        val g = TestGraphs.directAndDetour()
        fun spot(id: String, north: Double) = SurfaceMark(id, 100, lat(north), lon(0.0), OffsetDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(), label = SurfaceLabel.POTHOLE)
        // Ölçülmüş kötü 50 m (başta) + nokta (ortada) → 100 m; aynı yerde iki nokta birleşir.
        val both = StreetRatios.of(g, 100, listOf(spot("one", 200.0), spot("two", 210.0)), listOf(badSurface(g)))
        assertEquals(110.0, both.badM, 2.0)
        // Güveni düşük ölçüm sayılmaz.
        assertEquals(0.0, StreetRatios.of(g, 100, emptyList(), listOf(badSurface(g, confidence = .2))).badM, 0.0)
    }
}
