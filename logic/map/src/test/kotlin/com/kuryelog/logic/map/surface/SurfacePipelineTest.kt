package com.kuryelog.logic.map.surface

import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.nav.Stop
import com.kuryelog.logic.map.nav.StopPurpose
import com.kuryelog.logic.map.route.TestGraphs
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Y13 sensör hattı ve işlemci (A10.4b). YolGo'da `TelemetryService`/`SurfaceController` ekran/servis koduydu ve doğrudan testi yoktu
 * (`surface_single_flight_test` günlük onay zincirini sınıyordu, WEEM'de zincir yok): beklenen davranış `_process/_observation/_actualLeg`
 * kodundan satır satır yazıldı. Sentetik veri: kuzeye giden 400 m'lik yol (way 100).
 */
class SurfacePipelineTest {
    @get:Rule val tmp = TemporaryFolder()

    // ---- SurfaceLeg ----

    private fun day(configure: (DayState) -> Unit = {}) = DayState().also { it.active = true; it.startedAtMs = 5000L; configure(it) }
    private fun order(phase: OrderPhase, n: Int = 3) = OrderRec(n, 1000).also {
        it.restTarget = GeoPoint(40.0, 49.0); it.customerTarget = GeoPoint(40.01, 49.01); it.customerDepartedAtMs = 2000
    }

    @Test fun legIsNullWhenTheDayIsClosedAndFreeWithoutAMatchingOrderRoute() {
        assertNull(SurfaceLeg.actual(DayState(), null, false))
        assertEquals("yolgo-5000:free", SurfaceLeg.actual(day(), null, false))
        val d = day { it.current = order(OrderPhase.TO_RESTAURANT); it.phase = OrderPhase.TO_RESTAURANT }
        val stop = Stop(GeoPoint(40.0, 49.0), "R", restaurant = true, orderId = 3, purpose = StopPurpose.ORDER)
        assertEquals("yolgo-5000:3:toRestaurant:1000", SurfaceLeg.actual(d, stop, true))
        assertEquals("yolgo-5000:free", SurfaceLeg.actual(d, stop, false)) // rota yok
        assertEquals("yolgo-5000:free", SurfaceLeg.actual(d, stop.copy(orderId = 4), true))
        assertEquals("yolgo-5000:free", SurfaceLeg.actual(d, stop.copy(restaurant = false), true))
        assertEquals("yolgo-5000:free", SurfaceLeg.actual(d, stop.copy(point = GeoPoint(40.001, 49.0)), true)) // hedefe 10 m'den uzak
        assertEquals("yolgo-5000:free", SurfaceLeg.actual(d, stop.copy(purpose = StopPurpose.MANUAL), true))
    }

    @Test fun customerLegUsesTheDepartureTimeAndOtherPhasesAreFree() {
        val d = day { it.current = order(OrderPhase.TO_CUSTOMER); it.phase = OrderPhase.TO_CUSTOMER }
        val stop = Stop(GeoPoint(40.01, 49.01), "C", restaurant = false, orderId = 3, purpose = StopPurpose.ORDER)
        assertEquals("yolgo-5000:3:toCustomer:2000", SurfaceLeg.actual(d, stop, true))
        d.phase = OrderPhase.AT_RESTAURANT
        assertEquals("yolgo-5000:free", SurfaceLeg.actual(d, stop, true))
    }

    // ---- SurfaceFeed ----

    private class Rig {
        val rows = ArrayList<SurfaceRow>()
        var elapsed = 0L
        val feed = SurfaceFeed("boot-1", { rows += it }, { elapsed }, { 1_790_000_000_000L + elapsed / 1_000_000 }, { true })
            .also { it.linear = true; it.accelerationAvailable = true }
        val up = doubleArrayOf(0.0, 0.0, 1.0)
        private var north = 0.0

        /** [seconds] sn boyunca 100 Hz ivme + 2 Hz konum; [speed] m/s kuzeye. */
        fun run(seconds: Double, speed: Double, accuracy: Double = 5.0, vertical: (Double) -> Double = { 0.0 }) {
            val steps = (seconds * 100).toInt()
            repeat(steps) {
                elapsed += 10_000_000
                feed.onRotation(up, elapsed)
                if (it % 50 == 0) {
                    north += speed * 0.5
                    feed.onLocation(SurfaceFix(elapsed, 1_790_000_000_000L + elapsed / 1_000_000, lat(north), lon(0.0), 0.0, accuracy, speed, 0.0))
                }
                feed.onAcceleration(elapsed, doubleArrayOf(0.0, 0.0, vertical(elapsed / 1e9)))
            }
        }
    }

    private fun config(leg: String? = "yolgo-1:free", eligible: Boolean = true, paused: Boolean = false, day: String? = "yolgo-1") =
        SurfaceFeedConfig(day = day, leg = leg, eligible = eligible, paused = paused)

    @Test fun evidenceOncePerSecondOnlyWhileEligible() {
        val r = Rig(); r.feed.configure(config(eligible = false))
        r.run(3.0, 5.0)
        assertTrue(r.rows.none { it is SurfaceRow.Evidence })
        r.feed.configure(config())
        r.rows.clear(); r.run(4.0, 5.0)
        val ev = r.rows.filterIsInstance<SurfaceRow.Evidence>()
        assertTrue("saniyede en çok bir kanıt: ${ev.size}", ev.size in 3..5)
        assertTrue(ev.zipWithNext().all { (a, b) -> b.at - a.at >= 900 })
        assertEquals("boot-1", ev.first().boot); assertEquals(5.0, ev.first().speed, 0.0); assertTrue(ev.first().orientationReliable)
    }

    @Test fun legChangeEmitsABoundaryForTheOldLegAndClosingSaysClosed() {
        val r = Rig(); r.feed.configure(config("yolgo-1:free"))
        r.feed.configure(config("yolgo-1:3:toRestaurant:1000"))
        r.feed.configure(config("yolgo-1:3:toRestaurant:1000", paused = true)) // duraklatma: etap kapanmaz
        r.feed.configure(config(day = null, leg = null, eligible = false))
        val b = r.rows.filterIsInstance<SurfaceRow.Boundary>()
        assertEquals(listOf("yolgo-1:free", "yolgo-1:3:toRestaurant:1000", "yolgo-1:3:toRestaurant:1000"), b.map { it.leg })
        assertEquals(listOf(true, false, true), b.map { it.closed })
    }

    @Test fun windowsNeedAStationaryZeroThenFreshGpsAndRealDistance() {
        val r = Rig(); r.feed.configure(config())
        r.run(3.0, 0.0) // sıfır ofseti için ≥2 sn duruş
        assertTrue(r.feed.offsetReady)
        r.rows.clear(); r.run(4.0, 5.0) { .5 * Math.sin(2 * Math.PI * 8 * it) }
        val w = r.rows.filterIsInstance<SurfaceRow.Window>()
        assertTrue(w.isNotEmpty())
        val valid = w.filter { it.valid }
        assertTrue(valid.isNotEmpty())
        assertTrue(valid.all { it.meters > 0 && it.meters < 40 && it.rms > 0 && it.hz >= 40 })
        assertTrue(w.zipWithNext().all { (a, b) -> b.start == a.end }) // çakışmayan pencereler
        assertTrue(w.all { it.id.startsWith("boot-1:") && it.leg == "yolgo-1:free" })
    }

    @Test fun mountCandidateNeedsAStillPhoneWithReliableOrientationAndGps() {
        val r = Rig(); r.feed.configure(config())
        assertNull(r.feed.mountCandidate()) // konum ve yönelim yok
        r.run(1.0, 0.0)
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 1.0), r.feed.mountCandidate(), 0.0)
        r.run(1.0, 5.0)
        assertNull("scooter giderken kaydedilmez", r.feed.mountCandidate())
        val bad = Rig(); bad.feed.configure(config()); bad.run(1.0, 0.0, accuracy = 30.0)
        assertNull("GPS doğruluğu > 20 m", bad.feed.mountCandidate())
    }

    @Test fun pausedOrIneligibleFeedsProduceNoWindows() {
        for (cfg in listOf(config(paused = true), config(eligible = false), config(leg = null))) {
            val r = Rig(); r.feed.configure(cfg); r.run(6.0, 5.0)
            assertTrue(r.rows.none { it is SurfaceRow.Window })
        }
    }

    @Test fun slowSamplingOnlyAfterTwoStationarySecondsWithOffsetReady() {
        val r = Rig(); r.feed.configure(config())
        r.run(1.0, 0.0); assertFalse(r.feed.slowSampling)
        r.run(4.0, 0.0); assertTrue(r.feed.slowSampling)
        r.run(2.0, 5.0); assertFalse(r.feed.slowSampling)
    }

    // ---- SurfaceProcessor ----

    private val g = TestGraphs.directAndDetour()
    private fun store(name: String) = SurfaceStore(java.io.File(tmp.root, name))
    private fun processor(store: SurfaceStore, wheels: Double? = null, graph: com.kuryelog.logic.map.graph.RoadGraph? = g) =
        SurfaceProcessor(store, { graph }, { wheels }, { 1_790_000_000_000L })

    private fun evidence(at: Long, speed: Double = 5.0, displacement: Double = 5.0, leg: String? = "yolgo-1:free", boot: String = "b1", eligible: Boolean = true, day: String = "yolgo-1") =
        SurfaceRow.Evidence(day, leg, eligible, false, boot, at, 1_790_000_000_000L + at, lat(80.0), lon(0.0), 0.0, 5.0, speed, 0.0, null, displacement, 0, true, 0.0, null, 0, true, null, true)

    private fun window(start: Long, from: Double = 61.0, to: Double = 99.0, valid: Boolean = true, accuracy: Double = 5.0, boot: String = "b1", leg: String = "yolgo-1:free", day: String = "yolgo-1", id: String = "b1:$start") =
        SurfaceRow.Window(id, day, leg, boot, SurfaceProfile(), null, start, start + 1500, 1_790_000_000_000L + start + 1500, lat(to), lon(0.0), lat(from), lon(0.0), 0.0, accuracy, 5.0, to - from, 0.3,
            0.5, null, emptyList(), 100.0, valid, "", 0.1, emptyList())

    private fun ride(p: SurfaceProcessor, from: Long = 1000, until: Long = 5000) { var at = from; while (at <= until) { p.process(listOf(evidence(at))); at += 1000 } }

    @Test fun rideConfirmedWindowsCommitOnlyAfterTheEightSecondDelay() {
        val s = store("p1"); val p = processor(s); ride(p)
        assertTrue(p.gate.decision.accepts)
        p.process(listOf(window(4000))) // 4000–5500; hareket 3500'te onaylandı
        assertEquals(1, p.view.pending); assertTrue(s.summaries.isEmpty())
        ride(p, 6000, 13_000)
        assertEquals("5500 + 8 sn = 13500'e dek tamponda", 1, p.view.pending)
        ride(p, 14_000, 14_000)
        assertEquals(0, p.view.pending); assertEquals(1, p.view.accepted)
        val sum = s.summaries.single()
        assertEquals(38.0, sum.meters, 1e-6); assertEquals(100, sum.way.toInt()); assertEquals(1, sum.passes)
        assertEquals(SurfaceKey.of(100, SurfaceKey.anchor(g.nodeLat[g.edgeFrom[TestGraphs.edgeOf(g, 100)]], g.nodeLon[g.edgeFrom[TestGraphs.edgeOf(g, 100)]]), 0, 1), sum.key)
    }

    @Test fun windowsBeforeMotionIsConfirmedAndInvalidOrUnreliableOnesAreDropped() {
        val s = store("p2"); val p = processor(s)
        p.process(listOf(evidence(1000), window(1000)))
        assertEquals(0, p.view.pending) // hareket henüz onaylanmadı
        ride(p, 2000, 6000)
        p.process(listOf(window(6000, valid = false), window(7500, accuracy = 30.0), window(9000, from = 10.0, to = 99.0), window(10_500, id = "dup"), window(12_000, id = "dup")))
        // geçersiz, kötü doğruluk, 40 m'den uzun ve aynı kimlik: yalnız ilk "dup" tamponda
        assertEquals(1, p.view.pending)
    }

    @Test fun walkingOrLeavingAnEligibleLegDropsBufferedWindowsAndRetractsStoredOnes() {
        val s = store("p3"); val p = processor(s); ride(p)
        p.process(listOf(window(4000)))
        ride(p, 6000, 14_000) // ilk pencere kayıtlandı
        assertEquals(1, s.summaries.size)
        p.process(listOf(window(14_500)))
        assertEquals(1, p.view.pending)
        // Yürüme: 6 adım + yavaş yer değiştirme → walking, excludeSince = ilk yürüme kanıtı
        for (at in 15_000L..18_000L step 1000) p.process(listOf(evidence(at, speed = 1.2, displacement = 1.2).copy(steps = 6)))
        assertEquals(MeasurementState.WALKING, p.gate.decision.state)
        assertEquals(0, p.view.pending) // yürüme başlangıcından (15 000) sonra biten pencere atıldı
        assertEquals("önceki kayıt kalır", 1, s.summaries.size)
        // Yürüme başlangıcından sonraki pencere artık kabul edilmez.
        p.process(listOf(window(19_000)))
        assertEquals(0, p.view.pending)
    }

    @Test fun boundaryClosingALegRetractsEverythingSinceAndDiscardsTheBuffer() {
        val s = store("p4"); val p = processor(s); ride(p)
        p.process(listOf(window(4000)))
        ride(p, 6000, 14_000)
        p.process(listOf(window(14_500)))
        assertEquals(1, s.summaries.single().passes); assertEquals(1, p.view.pending)
        p.process(listOf(SurfaceRow.Boundary("yolgo-1", "yolgo-1:free", "b1", true, 3000, 0)))
        assertEquals(0, p.view.pending)
        assertTrue("sınırdan sonrası silindi", s.summaries.isEmpty())
    }

    @Test fun deletedDayRowsAreIgnoredAndWheelsStillStopsMeasurement() {
        val s = store("p5"); s.deleteDay("yolgo-1"); val p = processor(s); ride(p)
        assertEquals(MeasurementState.CLOSED, p.gate.decision.state)
        val s2 = store("p6"); val q = processor(s2, wheels = 0.0); ride(q)
        assertFalse(q.gate.decision.accepts); assertEquals("scooter_wheels_still", q.gate.decision.reason)
    }

    @Test fun noGraphMeansNoMeasurementAndDecisionsAreRecordedOncePerChange() {
        val s = store("p7"); val p = processor(s, graph = null); ride(p)
        p.process(listOf(window(5000)))
        assertEquals(0, p.view.pending)
        val states = s.decisions.map { it.getString("state") }
        assertEquals(states.distinct(), states.distinct().filterIndexed { i, x -> states.indexOf(x) == i }) // her durum değişimi bir kez
        assertTrue(states.contains("riding"))
        // Aynı kanıt kümesini yeniden yazmak karar kimliklerini çoğaltmaz.
        assertEquals(s.decisions.size, s.decisions.map { it.getString("id") }.distinct().size)
    }
}
