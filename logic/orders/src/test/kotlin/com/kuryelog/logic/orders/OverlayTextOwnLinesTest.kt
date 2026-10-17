package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.OwnLines
import org.junit.Assert.*
import org.junit.Test

/**
 * WEEM (A3.5; ORDERS.md 6.3, CAPTURE.md 2.10): `:orders` `OverlayText` formats with `logic/capture` `OwnLines` vocabulary, so
 * every line the badge draws is one the parser drops (N11). Rawstats kept both in one object; the split must not let them drift.
 */
class OverlayTextOwnLinesTest {
    private val tr = { a: String, _: String -> a }
    private val az = { _: String, b: String -> b }

    @Test fun everyPhaseHasItsOwnLinesTextUnderTheEnumName() {
        assertEquals(TripPhase.values().map { it.name }.toSet(), OwnLines.PHASES.keys)
        for (p in TripPhase.values()) {
            assertEquals(OwnLines.PHASES.getValue(p.name).first, OverlayText.phase(p, tr))
            assertEquals(OwnLines.PHASES.getValue(p.name).second, OverlayText.phase(p, az))
        }
    }

    @Test fun everyFormattedLineIsRecognisedAsOwn() {
        for (tl in listOf(tr, az)) {
            val lines = listOf(OverlayText.distance(12.34), OverlayText.offer("Wolt", 2.21, null, tl = tl),
                OverlayText.offer("Bolt Food", 3.94, "Borani", net = OverlayText.net(3.94, 20.0), tl = tl),
                OverlayText.status("Wolt", true, tl), OverlayText.status("Bolt Food", false, tl), OverlayText.today(7, tl)) +
                TripPhase.values().flatMap { listOf(OverlayText.tripStatus("Wolt", it, tl), OverlayText.trip("Bolt Food", null, it, tl)) }
            for (l in lines) assertTrue(l, OwnLines.isOwn(l))
        }
        assertEquals(OwnLines.SEP, OverlayText.SEP)
    }
}
