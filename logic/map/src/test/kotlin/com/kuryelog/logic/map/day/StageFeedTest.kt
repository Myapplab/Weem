package com.kuryelog.logic.map.day

import com.kuryelog.logic.core.TripStages
import com.kuryelog.logic.core.TripStages.Outcome
import com.kuryelog.logic.core.TripStages.Stage
import com.kuryelog.logic.map.day.StageFixtures.BOLT
import com.kuryelog.logic.map.day.StageFixtures.TRIP_A
import com.kuryelog.logic.map.day.StageFixtures.TRIP_B
import com.kuryelog.logic.map.day.StageFixtures.WOLT
import com.kuryelog.logic.map.day.StageFixtures.ev
import com.kuryelog.logic.map.day.StageFixtures.line
import com.kuryelog.logic.map.day.StageFixtures.ms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * YolGo `courier_stage_follow_test.dart` ayrıştırma 1. ve 3. (2) + `courier_stages_e8_test.dart` ayrıştırma 1. (1) = 3 test (A6.2b, MAP.md 11.7, 11.16) — UYARLANIR:
 * feed v2 JSON'u yerine `TripStages` (`:core`, tipli) → [FollowerFeed] (açıklar sırası, aşama eşlemesi, aşaması bilinmeyen yolculuk; olay sırası ve saatleri,
 * sonuç ve bitiş saati eşlemesi). Çevrilmez: v1/paylaşım kapalı/bozuk/büyük yük ve eski Rawstats zarfı (S7; `TripStages.parse` A2.5 `MessagesTest`'te).
 */
class StageFeedTest {
    @Test
    fun `açık yolculukların aşamaları ve sırası, aşama eşlemesi, okuma yoksa null`() {
        val feed = StageFeed.from(
            TripStages(
                listOf(
                    line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4, pickup = 0)), stage = Stage.TO_PICKUP),
                    line(TRIP_B, listOf(ev(Stage.AT_PICKUP, 9)), stage = Stage.AT_DROPOFF, pkg = BOLT),
                    line("trip-ended", emptyList(), outcome = Outcome.CANCELLED, endedS = 200),
                ),
            ),
        )!!
        assertEquals(
            listOf(FollowerTrip(TRIP_A, WOLT, Stage.TO_PICKUP), FollowerTrip(TRIP_B, BOLT, Stage.AT_DROPOFF)),
            feed.trips,
        )
        assertEquals(
            listOf(OrderPhase.TO_RESTAURANT, OrderPhase.AT_RESTAURANT, OrderPhase.TO_CUSTOMER, OrderPhase.AT_CUSTOMER),
            listOf(Stage.TO_PICKUP, Stage.AT_PICKUP, Stage.TO_DROPOFF, Stage.AT_DROPOFF).map(StageFeed::phaseOf),
        )
        assertNull("OrderEvents bağlı değil: okuma yok", StageFeed.from(null))
    }

    @Test
    fun `aşaması bilinmeyen yolculuk listelenir, aşama izlenmez`() {
        val raw = TripStages.Trip(TRIP_A, WOLT, active = true, stage = null, outcome = null, endedAtMs = null, events = emptyList())
        val feed = StageFeed.from(TripStages(listOf(raw)))!!
        assertEquals(TRIP_A, feed.trips.single().id)
        assertNull(feed.trips.single().stage)
    }

    @Test
    fun `olaylar sırasıyla ve gerçek saatleriyle, biten siparişin sonucu ve saati`() {
        val feed = StageFeed.from(
            TripStages(
                listOf(
                    line(TRIP_B, listOf(ev(Stage.TO_PICKUP, 10, pickup = 0)), outcome = Outcome.CANCELLED, endedS = 200, pkg = BOLT),
                    line(
                        TRIP_A,
                        listOf(ev(Stage.TO_PICKUP, 4, pickup = 0), ev(Stage.AT_PICKUP, 300, pickup = 0, source = "gps"), ev(Stage.TO_DROPOFF, 600)),
                        stage = Stage.TO_DROPOFF,
                    ),
                ),
            ),
        )!!
        val a = feed.stages!![TRIP_A]!!
        assertEquals(listOf(WOLT, true, Stage.TO_DROPOFF, null, null), listOf(a.platformPackage, a.active, a.stage, a.outcome, a.endedAtMs))
        assertEquals(
            listOf(Stage.TO_PICKUP to ms(4), Stage.AT_PICKUP to ms(300), Stage.TO_DROPOFF to ms(600)),
            a.events.map { it.stage to it.atMs },
        )
        assertEquals(listOf(0, "gps", null), listOf(a.events[0].pickup, a.events[1].source, a.events[2].pickup))
        val b = feed.stages!![TRIP_B]!!
        assertEquals(listOf(BOLT, false, null, Outcome.CANCELLED, ms(200)), listOf(b.platformPackage, b.active, b.stage, b.outcome, b.endedAtMs))
        for (outcome in listOf(Outcome.DELIVERED, Outcome.DELIVERED_UNVERIFIED, Outcome.UNCONFIRMED)) {
            val x = StageFeed.from(TripStages(listOf(line(TRIP_A, listOf(ev(Stage.TO_PICKUP, 4)), outcome = outcome, endedS = 900))))!!
            assertEquals(outcome, x.stages!![TRIP_A]!!.outcome)
        }
    }
}
