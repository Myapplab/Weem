package com.kuryelog.logic.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dokunuş kaydı, tel biçimi ve paylaşanda oynatma (REMOTE.md 2). */
class TouchTest {
    private val n = TouchLimits.NORM

    // --- TouchRecorder ---

    @Test fun tapIsOnePartThatBeginsAndEnds() {
        val r = TouchRecorder()
        assertTrue(r.down(0, 1000, 2000, 5_000, 576, 1280).isEmpty())
        val parts = r.up(0, 1000, 2000, 5_060)
        val p = parts.single()
        assertEquals(TouchPart(1, 0, 576, 1280, 60, listOf(TouchStroke(0, true, true, listOf(TouchPoint(1000, 2000, 0), TouchPoint(1000, 2000, 60))))), p)
        assertFalse(r.inGesture)
    }

    @Test fun longDragIsSplitIntoContinuingParts() {
        val r = TouchRecorder()
        val parts = ArrayList<TouchPart>()
        parts += r.down(0, 0, 0, 0, 576, 1280)
        for (t in 10..250 step 10) parts += r.move(0, t * 100, t * 100, t.toLong())
        parts += r.up(0, 26_000, 26_000, 260)
        assertEquals(listOf(0, 1, 2), parts.map { it.index })
        assertEquals(listOf(100, 100, 60), parts.map { it.durationMs })
        val s = parts.map { it.strokes.single() }
        assertEquals(listOf(true, false, false), s.map { it.begins })
        assertEquals(listOf(false, false, true), s.map { it.ends })
        // Sürdürülen parça bir öncekinin bittiği yerden, t = 0'dan başlar.
        assertEquals(TouchPoint(10_000, 10_000, 0), s[1].points.first())
        assertEquals(s[0].points.last().copy(t = 0), s[1].points.first())
        assertEquals(TouchPoint(26_000, 26_000, 60), s[2].points.last())
    }

    @Test fun holdingStillStillSendsPartsOnTick() {
        val r = TouchRecorder()
        r.down(0, 500, 500, 0, 576, 1280)
        assertTrue(r.tick(50).isEmpty())
        val p = r.tick(250).single()
        assertEquals(250, p.durationMs)
        // Parmak parça sonuna dek yerinde.
        assertEquals(listOf(TouchPoint(500, 500, 0), TouchPoint(500, 500, 250)), p.strokes.single().points)
        assertFalse(p.strokes.single().ends)
        val q = r.tick(500).single()
        assertEquals(1, q.index)
        assertEquals(listOf(TouchPoint(500, 500, 0), TouchPoint(500, 500, 250)), q.strokes.single().points)
    }

    @Test fun fastMovesAreThinnedButTheLastPositionIsKept() {
        val r = TouchRecorder()
        r.down(0, 0, 0, 0, 576, 1280)
        for (t in 1..20) r.move(0, t * 10, 0, t.toLong())
        val p = r.up(0, 300, 0, 21).single()
        val pts = p.strokes.single().points
        assertTrue(pts.zipWithNext().dropLast(1).all { (a, b) -> b.t - a.t >= TouchLimits.MIN_SAMPLE_MS })
        assertEquals(TouchPoint(300, 0, 21), pts.last())
    }

    @Test fun pointsPerPartAreCapped() {
        val r = TouchRecorder(flushMs = 1_000)
        r.down(0, 0, 0, 0, 576, 1280)
        for (t in 1..99) r.move(0, t, t, t * 9L)
        val p = r.up(0, 5, 5, 900).single()
        assertEquals(TouchLimits.MAX_POINTS, p.strokes.single().points.size)
        assertEquals(TouchPoint(5, 5, 900), p.strokes.single().points.last())
    }

    @Test fun twoFingersAtMostAndAThirdIsIgnored() {
        val r = TouchRecorder()
        r.down(0, 100, 100, 0, 576, 1280)
        r.down(1, 200, 200, 10, 576, 1280)
        r.down(2, 300, 300, 20, 576, 1280)
        r.move(2, 400, 400, 30)
        r.up(2, 400, 400, 40)
        r.up(1, 250, 250, 50)
        val p = r.up(0, 150, 150, 60).single()
        assertEquals(listOf(0, 1), p.strokes.map { it.id })
        assertEquals(10, p.strokes[1].points.first().t)
    }

    @Test fun cancelLiftsEveryFinger() {
        val r = TouchRecorder()
        r.down(0, 100, 100, 0, 576, 1280)
        r.move(0, 120, 100, 30)
        val p = r.cancel(40).single()
        assertTrue(p.strokes.single().ends)
        assertEquals(TouchPoint(120, 100, 40), p.strokes.single().points.last())
        assertFalse(r.inGesture)
    }

    @Test fun nextGestureHasANewId() {
        val r = TouchRecorder()
        r.down(0, 1, 1, 0, 576, 1280)
        val a = r.up(0, 1, 1, 30).single()
        r.down(0, 1, 1, 100, 576, 1280)
        val b = r.up(0, 1, 1, 130).single()
        assertEquals(a.gesture + 1, b.gesture)
        assertEquals(0, b.index)
    }

    @Test fun coordinatesAreClamped() {
        val r = TouchRecorder()
        r.down(0, -5, n + 9, 0, 576, 1280)
        val p = r.up(0, n + 1, -1, 30).single()
        assertEquals(listOf(TouchPoint(0, n, 0), TouchPoint(n, 0, 30)), p.strokes.single().points)
    }

    // --- tel biçimi ---

    @Test fun touchActionAndControlRoundTrip() {
        val part = TouchPart(65_535, 3, 576, 1280, 100, listOf(
            TouchStroke(0, false, false, listOf(TouchPoint(0, 0, 0), TouchPoint(n, n, 100))),
            TouchStroke(1, true, true, listOf(TouchPoint(7, 8, 20))),
        ))
        val enc = StreamMsg.encode(StreamMsg.Touch(part), fromHost = false)
        assertEquals(part, (StreamMsg.decode(enc, fromHost = false) as StreamMsg.Touch).part)
        assertEquals(4, (StreamMsg.decode(StreamMsg.encode(StreamMsg.Action(4), false), false) as StreamMsg.Action).code)
        assertTrue((StreamMsg.decode(StreamMsg.encode(StreamMsg.Control(true), true), true) as StreamMsg.Control).touch)
        assertFalse((StreamMsg.decode(StreamMsg.encode(StreamMsg.Control(false), true), true) as StreamMsg.Control).touch)
        // Yöne uymayan tür bozuk: paylaşan dokunuş yollamaz, izleyen Control yollamaz.
        assertNull(StreamMsg.decode(enc, fromHost = true))
        assertNull(StreamMsg.decode(StreamMsg.encode(StreamMsg.Control(true), true), fromHost = false))
    }

    @Test fun malformedTouchIsRejected() {
        val ok = StreamMsg.encode(StreamMsg.Touch(TouchPart(1, 0, 576, 1280, 30, listOf(TouchStroke(0, true, true, listOf(TouchPoint(1, 1, 0)))))), false)
        assertNull(StreamMsg.decode(ok.copyOf(ok.size - 1), false))
        assertNull(StreamMsg.decode(ok + byteArrayOf(0), false))
        // Çizgi sayısı 0 ve sınırın üstü.
        assertNull(StreamMsg.decode(ok.copyOf().also { it[11] = 0 }, false))
        assertNull(StreamMsg.decode(ok.copyOf().also { it[11] = (TouchLimits.MAX_POINTERS + 1).toByte() }, false))
        // Nokta sayısı 0.
        assertNull(StreamMsg.decode(ok.copyOf().also { it[14] = 0 }, false))
    }

    @Test fun olderPeerIgnoresTouchAsUnknown() {
        // A9.1 izleyeni Control'ü, A9.1 paylaşanı Touch'ı tanımasa da akış kapanmaz: bilinmeyen tür yok sayılır (1.4).
        assertTrue(StreamMsg.decode(byteArrayOf(30, 1, 2), fromHost = false) is StreamMsg.Unknown)
    }

    // --- TouchPlayer ---

    private fun tap(g: Int = 1, x: Int = 0, y: Int = 0) = TouchPart(g, 0, 576, 1280, 40, listOf(TouchStroke(0, true, true, listOf(TouchPoint(x, y, 0), TouchPoint(x, y, 40)))))

    @Test fun tapIsMappedToScreenPixels() {
        val p = TouchPlayer()
        val g = p.part(tap(x = n, y = n / 2), 1080, 2400, 0).single().strokes.single()
        assertEquals(GestureStroke(0, listOf(Px(1079, 1199), Px(1079, 1199)), 0, 40, continues = false, willContinue = false), g)
        assertFalse(p.holding)
    }

    @Test fun veryShortTapLastsAtLeastTwentyMs() {
        val p = TouchPlayer()
        val one = TouchPart(1, 0, 576, 1280, 1, listOf(TouchStroke(0, true, true, listOf(TouchPoint(10, 10, 0)))))
        assertEquals(TouchLimits.MIN_TAP_MS, p.part(one, 1080, 2400, 0).single().strokes.single().durationMs)
    }

    @Test fun recordedDragReplaysAsContinuedStrokes() {
        val r = TouchRecorder()
        val parts = ArrayList<TouchPart>()
        parts += r.down(0, 0, 0, 0, 576, 1280)
        for (t in 10..150 step 10) parts += r.move(0, t * 400, t * 400, t.toLong())
        parts += r.up(0, 60_000, 60_000, 160)
        val p = TouchPlayer()
        val out = parts.flatMap { p.part(it, 1080, 2400, it.index * 100L) }
        assertEquals(2, out.size)
        val (a, b) = out.map { it.strokes.single() }
        assertTrue(!a.continues && a.willContinue)
        assertTrue(b.continues && !b.willContinue)
        // Süreklilik: ikinci çizgi birincinin bittiği pikselden başlar.
        assertEquals(a.points.last(), b.points.first())
        assertEquals(100, a.durationMs)
        assertEquals(60, b.durationMs)
    }

    @Test fun outOfOrderPartDropsTheGestureAndLiftsTheFinger() {
        val p = TouchPlayer()
        val first = TouchPart(5, 0, 576, 1280, 100, listOf(TouchStroke(0, true, false, listOf(TouchPoint(100, 100, 0), TouchPoint(200, 200, 100)))))
        p.part(first, 1080, 2400, 0)
        assertTrue(p.holding)
        val skipped = TouchPart(5, 2, 576, 1280, 100, listOf(TouchStroke(0, false, true, listOf(TouchPoint(200, 200, 0)))))
        val lift = p.part(skipped, 1080, 2400, 200).single().strokes.single()
        assertTrue(lift.continues && !lift.willContinue)
        assertEquals(1, lift.points.size)
        assertFalse(p.holding)
        // Aynı hareketin geri kalanı yok sayılır.
        assertTrue(p.part(skipped.copy(index = 3), 1080, 2400, 300).isEmpty())
        // Ortasından yakalanan hareket (0. parça yok) oynatılmaz.
        assertTrue(p.part(skipped.copy(gesture = 6, index = 4), 1080, 2400, 300).isEmpty())
        assertEquals(1, p.part(tap(g = 7), 1080, 2400, 400).size)
    }

    @Test fun stalledFingerIsLiftedAfterOneSecond() {
        val p = TouchPlayer()
        p.part(TouchPart(1, 0, 576, 1280, 100, listOf(TouchStroke(0, true, false, listOf(TouchPoint(1, 1, 0), TouchPoint(1, 1, 100))))), 1080, 2400, 0)
        assertTrue(p.tick(999).isEmpty())
        val lift = p.tick(1_000).single().strokes.single()
        assertFalse(lift.willContinue)
        assertFalse(p.holding)
    }

    @Test fun gestureLongerThanThirtySecondsIsLifted() {
        val p = TouchPlayer()
        var t = 0L
        var i = 0
        p.part(TouchPart(1, i++, 576, 1280, 250, listOf(TouchStroke(0, true, false, listOf(TouchPoint(1, 1, 0), TouchPoint(1, 1, 250))))), 1080, 2400, t)
        while (t < 30_000) {
            t += 250
            val out = p.part(TouchPart(1, i++, 576, 1280, 250, listOf(TouchStroke(0, false, false, listOf(TouchPoint(1, 1, 0), TouchPoint(1, 1, 250))))), 1080, 2400, t)
            assertTrue(out.single().strokes.single().willContinue)
        }
        val last = p.part(TouchPart(1, i, 576, 1280, 250, listOf(TouchStroke(0, false, false, listOf(TouchPoint(1, 1, 0), TouchPoint(1, 1, 250))))), 1080, 2400, t + 250)
        assertFalse(last.single().strokes.single().willContinue)
        assertFalse(p.holding)
    }

    @Test fun rotatedFrameIsNotReplayed() {
        val p = TouchPlayer()
        // İzleyen hâlâ dikey görüntüye dokundu, paylaşan yataya döndü.
        assertTrue(p.part(tap(), 2400, 1080, 0).isEmpty())
        assertEquals(1, p.part(tap(g = 2), 1080, 2400, 0).size)
    }

    @Test fun invalidPartsAreRejected() {
        val p = TouchPlayer()
        fun bad(part: TouchPart) = assertTrue(part.toString(), p.part(part, 1080, 2400, 0).isEmpty())
        val s = TouchStroke(0, true, true, listOf(TouchPoint(1, 1, 0), TouchPoint(1, 1, 40)))
        bad(TouchPart(10, 0, 576, 1280, 0, listOf(s)))
        bad(TouchPart(11, 0, 576, 1280, TouchLimits.MAX_PART_MS + 1, listOf(s)))
        bad(TouchPart(12, 0, 576, 1280, 40, emptyList()))
        bad(TouchPart(13, 0, 576, 1280, 40, listOf(s, s)))
        // Başlamamış parmağın sürmesi, zaman geri gitmesi, parça süresini aşan nokta.
        bad(TouchPart(14, 0, 576, 1280, 40, listOf(s.copy(begins = false))))
        bad(TouchPart(15, 0, 576, 1280, 40, listOf(s.copy(points = listOf(TouchPoint(1, 1, 30), TouchPoint(1, 1, 10))))))
        bad(TouchPart(16, 0, 576, 1280, 40, listOf(s.copy(points = listOf(TouchPoint(1, 1, 0), TouchPoint(1, 1, 41))))))
        bad(TouchPart(17, 0, 0, 1280, 40, listOf(s)))
        assertFalse(p.holding)
    }

    @Test fun heldFingerMissingFromNextPartLiftsIt() {
        val p = TouchPlayer()
        p.part(TouchPart(1, 0, 576, 1280, 100, listOf(TouchStroke(0, true, false, listOf(TouchPoint(1, 1, 0), TouchPoint(1, 1, 100))))), 1080, 2400, 0)
        val other = TouchPart(1, 1, 576, 1280, 100, listOf(TouchStroke(1, true, true, listOf(TouchPoint(5, 5, 0)))))
        val lift = p.part(other, 1080, 2400, 100).single().strokes.single()
        assertEquals(0, lift.id)
        assertFalse(lift.willContinue)
    }

    @Test fun pinchWithTwoFingers() {
        val r = TouchRecorder()
        val parts = ArrayList<TouchPart>()
        parts += r.down(0, 30_000, 30_000, 0, 576, 1280)
        parts += r.down(1, 35_000, 35_000, 5, 576, 1280)
        for (t in 1..15) {
            parts += r.move(0, 30_000 - t * 1000, 30_000 - t * 1000, 5L + t * 10)
            parts += r.move(1, 35_000 + t * 1000, 35_000 + t * 1000, 5L + t * 10)
        }
        parts += r.up(1, 50_000, 50_000, 160)
        parts += r.up(0, 15_000, 15_000, 161)
        val p = TouchPlayer()
        val out = parts.flatMap { p.part(it, 1080, 2400, 0) }
        assertEquals(listOf(2, 2), out.map { it.strokes.size })
        assertEquals(listOf(false, false), out.last().strokes.map { it.willContinue })
        assertFalse(p.holding)
    }

    @Test fun resetLiftsHeldFingers() {
        val p = TouchPlayer()
        assertTrue(p.reset().isEmpty())
        p.part(TouchPart(1, 0, 576, 1280, 100, listOf(TouchStroke(0, true, false, listOf(TouchPoint(1, 1, 0), TouchPoint(1, 1, 100))))), 1080, 2400, 0)
        assertEquals(1, p.reset().single().strokes.size)
        assertFalse(p.holding)
    }

    @Test fun globalActionCodes() {
        assertEquals(GlobalAction.entries, GlobalAction.entries.map { GlobalAction.of(it.code) })
        assertNull(GlobalAction.of(0))
        assertNull(GlobalAction.of(99))
    }
}
