package com.kuryelog.logic.economics

import org.junit.Assert.*
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats WaitObservationProcessDeathTest.kt @ 6388c04, aynı girdi → aynı çıktı; farklar ECONOMICS.md "WEEM" satırlarında.

/** Regresyon: süreç (process) Android tarafından öldürülünce açık bekleme
 *  penceresi sessizce kaybolmamalı; yeniden açılışta sansürlü kayıt olmalı. */
class WaitObservationProcessDeathTest {
    private fun o(at: Long, stage: ScreenStage = ScreenStage.IDLE) =
        CourierObservation("wolt", at, stage, true, "accessibility", emptyList(), emptyList(), demand = "HIGH")

    @Test fun openWindowSurvivesProcessDeathAsCensoredEpisode() {
        val before = WaitObservation()
        before.observe(o(1_000), "electric_scooter", "center", 9)
        before.observe(o(21_000), "electric_scooter", "center", 9)
        before.nextCause("wolt", "after_delivery_observed")
        val saved = WaitObservation.encode(before.snapshot())

        // Süreç öldü; yeni süreçte bellekteki pencere yok, yalnız kayıt var.
        val after = WaitObservation()
        val e = after.restoreCensored(WaitObservation.decode(saved)).single()
        assertEquals(1_000, e.startAt)
        assertEquals(21_000, e.endAt) // son görülen an; ölüm süresi bekleme sayılmaz
        assertFalse(e.eventObserved) // teklif görülmedi: sansürlü
        assertEquals("process_restart", e.endReason)
        assertEquals("after_delivery_observed", e.cause)
        assertEquals("HIGH", e.demand)
        assertEquals("electric_scooter", e.vehicleMode)
        assertEquals("center", e.zone)
        assertEquals(9, e.bakuHour)
        // Geri yükleme pencereyi yeniden açmaz: sonraki teklif eski bekleme ile birleşmez.
        assertTrue(after.snapshot().isEmpty())
        assertTrue(after.observe(o(30_000, ScreenStage.OFFER)).isEmpty())
    }

    @Test fun nullDemandRoundTrips() {
        val w = WaitObservation()
        w.observe(CourierObservation("bolt", 5_000, ScreenStage.IDLE, true, "accessibility", emptyList(), emptyList()))
        val back = WaitObservation.decode(WaitObservation.encode(w.snapshot())).single()
        assertNull(back.demand)
        assertEquals("bolt", back.pkg)
        assertEquals(-1, back.hour)
    }

    @Test fun damagedRowsNeverInventWaits() {
        assertTrue(WaitObservation.decode(null).isEmpty())
        assertTrue(WaitObservation.decode("").isEmpty())
        assertTrue(WaitObservation.decode("{bozuk").isEmpty())
        val raw = """[
            {"pkg":"wolt","start":1000,"last":9000,"cause":"idle_observed","demand":"LOW","vehicle":"car","zone":"z","hour":8},
            {"pkg":"wolt","start":9000,"last":1000,"cause":"idle_observed"},
            {"pkg":"wolt","start":0,"last":1000,"cause":"idle_observed"},
            {"start":1000,"last":2000,"cause":"idle_observed"},
            "metin"
        ]"""
        val rows = WaitObservation.decode(raw)
        assertEquals(1, rows.size)
        assertEquals(9_000, rows.single().last)
    }

    @Test fun normalStopLeavesNothingToRestore() {
        val w = WaitObservation()
        w.observe(o(1_000)); w.observe(o(2_000))
        assertEquals("manual_stop", w.stop().single().endReason)
        assertTrue(w.snapshot().isEmpty())
        assertTrue(WaitObservation.decode(WaitObservation.encode(w.snapshot())).isEmpty())
    }

    @Test fun closedByOfferIsNotPersistedAgain() {
        val w = WaitObservation()
        w.observe(o(1_000)); w.observe(o(11_000))
        assertTrue(w.observe(o(12_000, ScreenStage.OFFER)).single().eventObserved)
        assertTrue(w.snapshot().isEmpty()) // aynı bekleme iki kez yazılmaz
    }
}
