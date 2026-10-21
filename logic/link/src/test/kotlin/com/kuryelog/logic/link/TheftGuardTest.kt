package com.kuryelog.logic.link

import com.kuryelog.logic.core.TripStages
import com.kuryelog.logic.core.TripStages.Stage
import com.kuryelog.logic.link.TheftGuard.ArmedBy
import com.kuryelog.logic.link.TheftGuard.Fix
import com.kuryelog.logic.link.TheftGuard.Inputs
import com.kuryelog.logic.link.TheftGuard.State
import com.kuryelog.logic.link.TheftGuard.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** LINK.md 5.1: devreye alma, kendiliğinden kapanma, tetikler, uyarı → alarm, kapatma, PIN. */
class TheftGuardTest {
    private val t0 = 1_760_000_000_000L
    private var now = t0
    private val g = TheftGuard()

    private val linkedIdle = Inputs(enabled = true, linked = true)
    private fun atPickup(id: String = "w1") = linkedIdle.copy(atStop = mapOf(id to Stage.AT_PICKUP))

    /** [sec] saniye boyunca saniyede bir tik. */
    private fun run(sec: Int, i: Inputs): TheftGuard.Status {
        var s = g.status
        repeat(sec) { now += 1_000; s = g.tick(now, i) }
        return s
    }

    private fun armByStage(): TheftGuard.Status {
        run(6, atPickup())
        assertEquals(State.ARMING, g.status.state)
        return run(20, atPickup())
    }

    @Test fun settingsAreLockedOnlyWhileGuarding() {
        // Hırsız Poco'da korumayı ayardan kapatamasın: ARMED, WARNING, ALARM kilitli; kapalı ve geri sayım değil.
        assertFalse(g.status.guarding)
        run(6, atPickup())
        assertEquals(State.ARMING, g.status.state)
        assertFalse(g.status.guarding)
        assertTrue(run(20, atPickup()).guarding)
        for (st in listOf(State.WARNING, State.ALARM)) assertTrue(TheftGuard.Status(st).guarding)
    }

    @Test fun stageArmsAfterStillnessAndGrace() {
        assertEquals(State.OFF, run(4, atPickup()).state)
        val s = run(2, atPickup())
        assertEquals(State.ARMING, s.state)
        assertEquals(ArmedBy.STAGE, s.armedBy)
        assertEquals(State.ARMING, run(19, atPickup()).state)
        assertEquals(State.ARMED, run(1, atPickup()).state)
    }

    @Test fun noArmWhileScooterMoving() {
        assertEquals(State.OFF, run(30, atPickup().copy(scooterSpeedKmh = 12.0)).state)
    }

    @Test fun stageChangeDisarmsAndSameStopDoesNotRearm() {
        armByStage()
        val both = linkedIdle.copy(atStop = mapOf("w1" to Stage.AT_PICKUP, "b2" to Stage.AT_DROPOFF))
        assertEquals(State.ARMED, run(1, both).state)
        // w1 alındı (TO_DROPOFF): w1 durakta değil → kapanır; b2'nin eski durağı yeniden devreye almaz.
        val onlyB = linkedIdle.copy(atStop = mapOf("b2" to Stage.AT_DROPOFF))
        assertEquals(State.OFF, run(1, onlyB).state)
        assertEquals(State.OFF, run(60, onlyB).state)
        // Yeni durak (w3 restoranda) yeniden devreye alır.
        assertEquals(State.ARMING, run(6, onlyB.copy(atStop = onlyB.atStop + ("w3" to Stage.AT_PICKUP))).state)
    }

    @Test fun autoArmingCancelledByMovingThenRetries() {
        run(6, atPickup())
        assertEquals(State.ARMING, g.status.state)
        assertEquals(State.OFF, run(1, atPickup().copy(scooterSpeedKmh = 8.0)).state)
        assertEquals(State.ARMING, run(6, atPickup()).state)
    }

    @Test fun awayArmsAfterLinkLostAndLinkReturnDisarms() {
        val away = linkedIdle.copy(linked = false)
        assertEquals(State.OFF, run(60, away).state)
        val s = run(1, away)
        assertEquals(State.ARMING, s.state)
        assertEquals(ArmedBy.AWAY, s.armedBy)
        assertEquals(State.ARMED, run(20, away).state)
        assertEquals(State.OFF, run(1, linkedIdle).state)
    }

    @Test fun speedTriggerNeedsTwoSecondsThenWarningThenAlarm() {
        armByStage()
        val fast = atPickup().copy(scooterSpeedKmh = 6.0)
        assertEquals(State.ARMED, run(2, fast).state)
        assertEquals(State.ARMED, run(1, atPickup()).state)
        assertEquals(State.ARMED, run(2, fast).state)
        val w = run(1, fast)
        assertEquals(State.WARNING, w.state)
        assertEquals(Trigger.SPEED, w.trigger)
        assertEquals(State.WARNING, run(7, fast).state)
        assertEquals(State.ALARM, run(1, fast).state)
        g.disarm(now)
        assertEquals(State.OFF, g.status.state)
    }

    @Test fun warningIsNotAutoDisarmedByStageChange() {
        armByStage()
        run(3, atPickup().copy(scooterSpeedKmh = 6.0))
        assertEquals(State.WARNING, g.status.state)
        assertEquals(State.ALARM, run(8, linkedIdle).state)
    }

    @Test fun motionNeedsThreeSamplesSpreadOverASecond() {
        armByStage()
        g.motion(now + 100, 2.0); g.motion(now + 200, 2.0); g.motion(now + 300, 2.0)
        assertEquals(State.ARMED, run(1, atPickup()).state)
        g.motion(now + 100, 0.5)
        g.motion(now + 1_500, 1.5)
        val s = run(2, atPickup())
        assertEquals(State.WARNING, s.state)
        assertEquals(Trigger.MOTION, s.trigger)
    }

    @Test fun motionIgnoredUnlessArmed() {
        run(6, atPickup())
        g.motion(now + 100, 3.0); g.motion(now + 600, 3.0); g.motion(now + 1_200, 3.0)
        run(20, atPickup())
        assertEquals(State.ARMED, run(1, atPickup()).state)
    }

    @Test fun gpsMoveFromArmPointTriggers() {
        g.fix(Fix(40.4093, 49.8671, 8.0, now))
        armByStage()
        g.fix(Fix(40.4094, 49.8671, 35.0, now + 1)) // kötü doğruluk sayılmaz
        assertEquals(State.ARMED, run(1, atPickup()).state)
        g.fix(Fix(40.4096, 49.8671, 8.0, now + 1)) // ~33 m
        val s = run(1, atPickup())
        assertEquals(Trigger.GPS, s.trigger)
        assertEquals(40.4096, s.fix!!.lat, 1e-9)
    }

    @Test fun manualArmIgnoresAutoDisarmAndMovingResetsGrace() {
        run(1, linkedIdle)
        assertTrue(g.arm(now))
        assertEquals(ArmedBy.MANUAL, g.status.armedBy)
        run(15, linkedIdle)
        assertEquals(State.ARMING, run(1, linkedIdle.copy(scooterSpeedKmh = 5.0)).state)
        assertEquals(State.ARMING, run(19, linkedIdle).state)
        assertEquals(State.ARMED, run(1, linkedIdle).state)
        assertEquals(State.ARMED, run(5, linkedIdle).state)
    }

    @Test fun parkWhileAutoArmedBecomesManual() {
        armByStage()
        g.arm(now)
        assertEquals(State.ARMED, run(1, linkedIdle).state)
    }

    @Test fun ownerDisarmSuppressesSameStopAndSameAwayPeriod() {
        armByStage()
        g.disarm(now)
        assertEquals(State.OFF, run(30, atPickup()).state)
        val away = linkedIdle.copy(linked = false)
        run(61, away)
        assertEquals(State.ARMING, g.status.state)
        g.disarm(now)
        assertEquals(State.OFF, run(120, away).state)
        run(1, linkedIdle)
        assertEquals(State.OFF, run(60, away).state)
        assertEquals(State.ARMING, run(1, away).state)
    }

    @Test fun cancelArmingOnlyDuringCountdown() {
        run(6, atPickup())
        assertTrue(g.cancelArming(now))
        assertEquals(State.OFF, run(30, atPickup()).state)
        assertFalse(g.cancelArming(now))
    }

    @Test fun disabledKeepsOffAndRefusesPark() {
        armByStage()
        assertEquals(State.OFF, run(1, atPickup().copy(enabled = false)).state)
        assertFalse(g.arm(now))
        assertEquals(State.OFF, run(30, atPickup().copy(enabled = false)).state)
    }

    @Test fun atStopOfTakesOnlyActiveStops() {
        val s = TripStages(
            listOf(
                TripStages.Trip("a", "com.wolt.courierapp", true, Stage.AT_PICKUP, null, null, emptyList()),
                TripStages.Trip("b", "com.wolt.courierapp", true, Stage.TO_DROPOFF, null, null, emptyList()),
                TripStages.Trip("c", "ee.mtakso.driver", false, null, TripStages.Outcome.DELIVERED, t0, emptyList()),
            ),
        )
        assertEquals(mapOf("a" to Stage.AT_PICKUP), TheftGuard.atStopOf(s))
        assertNull(TheftGuard.atStopOf(TripStages.EMPTY)["a"])
    }

    @Test fun pinStoredOnlyAsSaltedHash() {
        val salt = byteArrayOf(1, 2, 3, 4)
        val h = TheftPin.hash("2580", salt)
        assertFalse(h.contains("2580"))
        assertTrue(TheftPin.matches("2580", salt, h))
        assertFalse(TheftPin.matches("2581", salt, h))
        assertFalse(TheftPin.matches("2580", byteArrayOf(9), h))
        assertFalse(TheftPin.valid("12a4"))
        assertFalse(TheftPin.matches("258", salt, h))
    }
}
