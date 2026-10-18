package com.kuryelog.logic.economics

import org.junit.Assert.*
import org.junit.Test
import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*

// Rawstats EconomicsObservationTest.kt @ 6388c04, aynı girdi → aynı çıktı; farklar ECONOMICS.md "WEEM" satırlarında.

class EconomicsObservationTest {
    private fun o(at: Long, stage: ScreenStage = ScreenStage.IDLE, complete: Boolean = true) = CourierObservation("wolt", at, stage, complete, "accessibility", emptyList(), emptyList(), demand="HIGH")
    @Test fun continuousOfferIsEvent() {
        val w=WaitObservation(); w.observe(o(1000)); w.observe(o(21000))
        val e=w.observe(o(31000, ScreenStage.OFFER)).single()
        assertTrue(e.eventObserved); assertEquals(.5,e.durationMinutes,1e-9)
    }
    @Test fun gapEndsAtLastKnownIdle() {
        val w=WaitObservation(); w.observe(o(1000)); w.observe(o(21000))
        val e=w.observe(o(70000,ScreenStage.OFFER)).single()
        assertFalse(e.eventObserved); assertEquals(21000,e.endAt); assertEquals("observation_gap",e.endReason)
    }
    @Test fun unknownAndPartialNeverCreateWaiting() {
        val w=WaitObservation(); w.observe(o(1000,ScreenStage.UNKNOWN)); w.observe(o(2000,complete=false))
        assertTrue(w.observe(o(3000,ScreenStage.OFFER)).isEmpty())
    }
    @Test fun partialCensorsOpenInterval() {
        val w=WaitObservation(); w.observe(o(1000)); val e=w.observe(o(5000,complete=false)).single()
        assertEquals(0.0,e.durationMinutes,0.0); assertFalse(e.eventObserved)
    }
    @Test fun offlineEndsVisibility() {
        val w=WaitObservation();w.observe(o(1000));assertFalse(w.observe(o(2000,ScreenStage.OFFLINE)).single().eventObserved)
    }
    @Test fun oldFramesIgnored() {
        val w=WaitObservation();w.observe(o(5000));assertTrue(w.observe(o(4000,ScreenStage.OFFER)).isEmpty());assertEquals(5000,w.stop().single().endAt)
    }
    @Test fun delayedDeclineCauseAppliedToCurrentEpisode() {
        val w=WaitObservation();w.observe(o(1000));w.nextCause("wolt","after_decline_observed")
        assertEquals("after_decline_observed",w.observe(o(2000,ScreenStage.OFFER)).single().cause)
    }
    @Test fun manualCancelDoesNotIncludeEarlierWait() {
        val w=WaitObservation();w.observe(o(1000));assertFalse(w.breakWait("wolt","manual_annotation")!!.eventObserved)
        w.nextCause("wolt","after_cancellation_user_reported");w.observe(o(10000))
        assertEquals(10000,w.observe(o(20000,ScreenStage.OFFER)).single().startAt)
    }
    @Test fun contextChangesAreNotMixed() {
        val w=WaitObservation();w.observe(o(1000),"car","center",9)
        assertEquals("context_changed",w.observe(o(2000),"electric_scooter","center",9).single().endReason)
    }
    @Test fun stoppedFeedEmpty() {
        val offer=OfferState("x","wolt",1000,o(1000,ScreenStage.OFFER))
        // WEEM: feed v1 yok (S7); karşılığı `OrderEvents.offers` üreticisi `OfferSummaries.of` (ORDERS.md 13.2).
        assertEquals(0,OfferSummaries.of(false,listOf(offer),2000).size)
    }
    @Test fun foodCashDoesNotReplaceCourierFee() {
        val o=CourierParser.parse("wolt",listOf("Cash to collect 20 AZN","Estimated fee 4 AZN","Accept","Decline"),listOf("Accept 4 AZN · 15 min","Decline"),1000)
        assertEquals(4.0,o.gross!!,0.0);assertEquals(ScreenStage.OFFER,o.stage)
    }
    @Test fun ambiguousAmountsKeepOfferButFeeUnknown() {
        val o=CourierParser.parse("wolt",listOf("20 AZN","4 AZN","Accept","Decline"),listOf("Accept","Decline"),1000)
        assertNull(o.gross);assertEquals(ScreenStage.OFFER,o.stage)
    }
    @Test fun privateDetailsNotInFeed() {
        val raw=o(1000,ScreenStage.OFFER).copy(raw=listOf("SECRET_RAW"),customer=DeliveryDetails(name="SECRET_NAME",doorCode="SECRET_DOOR",note="SECRET_NOTE"),gross=3.0)
        // WEEM: `OfferSummary` (feed v1'in `platformDistanceScope` alanı yok); bölge bilinmiyorsa "unknown", uydurulmaz.
        val j=OfferSummaries.of(true,listOf(OfferState("x","wolt",1000,raw)),2000).single().toJson().toString()
        assertFalse(j.contains("SECRET"));assertTrue(j.contains("platformDistanceKm"));assertTrue(j.contains("unknown"))
    }
}
