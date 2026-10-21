package com.kuryelog.logic.map.surface

import org.junit.Assert.*
import org.junit.Test

/** Y13 hareket kapısı (YolGo `test/surface_gate_test.dart` @ 870ab6e, aynı girdi → aynı çıktı). */
class SurfaceGateTest {
    private fun evidence(at: Long, speed: Double = 5.0, displacement: Double = 5.0, angle: Double = 0.0, steps: Int = 0, walking: Int = 0,
                         activityAt: Long? = null, orientation: Boolean = true) =
        MotionEvidence(at = at, speed = speed, accuracy = 5.0, displacementSpeed = displacement, orientationReliable = orientation, mountAngle = angle,
            steps = steps, walkConfidence = walking, activityAt = activityAt, activityAvailable = true)

    private fun feed(gate: MotionGate, e: MotionEvidence, leg: String? = "order-1/restaurant", eligible: Boolean = true, day: Boolean = true) =
        gate.feed(e, dayActive = day, leg = leg, eligiblePhase = eligible)

    private fun ride(gate: MotionGate) { var at = 1000L; while (at <= 5000) { feed(gate, evidence(at)); at += 1000 } }

    @Test fun motionNeverOpensAnInactiveDayOrIneligibleOrderPhase() {
        val gate = MotionGate()
        assertEquals(MeasurementState.CLOSED, feed(gate, evidence(1000), day = false).state)
        assertFalse(feed(gate, evidence(2000), eligible = false).accepts)
        assertFalse(feed(gate, evidence(3000), leg = null).accepts)
    }

    @Test fun stopPausesAndTheSameLegResumesWithoutAManualStart() {
        val gate = MotionGate(); ride(gate); assertTrue(gate.decision.accepts)
        assertEquals(MeasurementState.STOPPED, feed(gate, evidence(6000, speed = 0.0, displacement = 0.0)).state)
        feed(gate, evidence(7000)); assertTrue(feed(gate, evidence(8000)).accepts)
    }

    @Test fun walkingAwayFromTheTargetAndPushingTheScooterSuspendData() {
        val gate = MotionGate(); ride(gate)
        var at = 6000L; while (at <= 9000) { feed(gate, evidence(at, speed = 1.2, displacement = 1.2, steps = 6)); at += 1000 }
        assertEquals(MeasurementState.WALKING, gate.decision.state); assertEquals(6000L, gate.decision.excludeSince)
    }

    @Test fun walkingAndOnFootAreOneFamilyAndUnknownIsNotWalking() {
        val gate = MotionGate(); ride(gate)
        assertTrue(feed(gate, evidence(6000, walking = 0, activityAt = 5900)).accepts)
        feed(gate, evidence(7000, speed = 1.0, displacement = 1.0, walking = 90, activityAt = 6500))
        assertEquals(MeasurementState.WALKING, feed(gate, evidence(9000, speed = 1.0, displacement = 1.0, walking = 90, activityAt = 6500)).state)
        assertEquals(6500L, gate.decision.excludeSince)
    }

    @Test fun singleMountRotationOrSlowRidingCannotBecomeWalkingAlone() {
        val gate = MotionGate(); ride(gate)
        assertNotEquals(MeasurementState.WALKING, feed(gate, evidence(6000, angle = 35.0)).state)
        assertNotEquals(MeasurementState.WALKING, feed(gate, evidence(7000, speed = 1.5, displacement = 1.5)).state)
    }

    @Test fun manualPauseWinsAndResumeCannotForceAClosedPhaseIntoRiding() {
        val gate = MotionGate(); ride(gate); gate.pause(true)
        assertEquals(MeasurementState.MANUAL_PAUSED, feed(gate, evidence(6000)).state)
        gate.pause(false); assertFalse(feed(gate, evidence(7000), eligible = false).accepts)
    }

    @Test fun changingActualLegClearsPreviouslyConfirmedMotion() {
        val gate = MotionGate(); ride(gate)
        assertFalse(feed(gate, evidence(6000), leg = "order-2/customer").accepts)
    }

    @Test fun missingOrientationIsUnknownNeverAnInventedScore() {
        val gate = MotionGate(); ride(gate)
        assertEquals(MeasurementState.UNCERTAIN, feed(gate, evidence(6000, orientation = false)).state)
    }

    @Test fun eightSecondDelayDiscardsWindowsOverlappingLateWalkingOrManualArrival() {
        val buffer = BufferedSurface<String>()
        assertTrue(buffer.add("a", 1000, 2500, "a")); assertFalse(buffer.add("a", 1000, 2500, "a"))
        buffer.add("b", 2500, 4000, "b"); buffer.add("c", 4000, 5500, "c")
        assertTrue(buffer.commitBefore(10000).isEmpty())
        assertEquals(listOf("b", "c"), buffer.excludeSince(3500)); assertEquals(listOf("a"), buffer.commitBefore(11000))
    }

    @Test fun fasterThanAScooterTaxiBusIsNeverMeasured() {
        val gate = MotionGate(); ride(gate); assertTrue(gate.decision.accepts)
        assertEquals(MeasurementState.UNCERTAIN, feed(gate, evidence(6000, speed = 15.0, displacement = 15.0)).state)
        assertEquals("faster_than_scooter", gate.decision.reason)
    }

    @Test fun phoneMovingWhileTheConnectedScooterWheelsStandStillIsNotRiding() {
        val gate = MotionGate()
        fun ev(at: Long, wheels: Double) = MotionEvidence(at = at, speed = 5.0, accuracy = 5.0, displacementSpeed = 5.0, orientationReliable = true, activityAvailable = true, scooterKmh = wheels)
        var at = 1000L; while (at <= 5000) { feed(gate, ev(at, 0.0)); at += 1000 }
        assertFalse(gate.decision.accepts); assertEquals("scooter_wheels_still", gate.decision.reason)
        at = 6000; while (at <= 10000) { feed(gate, ev(at, 18.0)); at += 1000 }
        assertTrue(gate.decision.accepts)
    }

    @Test fun aRepeatVisitNeedsRidingDistanceAndFifteenSecondsElsewhere() {
        // YolGo `SurfacePassTracker`: kısa duraklama/GPS titremesi yeni geçiş değil.
        val t = SurfacePassTracker()
        assertEquals("L:pass0", t.pass("L", "s1", 1000, 25.0, 50.0))
        assertEquals("L:pass0", t.pass("L", "s1", 9000, 25.0, 50.0))
        assertEquals("L:pass0", t.pass("L", "s2", 12000, 100.0, 50.0))
        assertEquals("L:pass1", t.pass("L", "s1", 30000, 25.0, 50.0))
        assertEquals("M:pass0", t.pass("M", "s1", 31000, 25.0, 50.0))
    }
}
