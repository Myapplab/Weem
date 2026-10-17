package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayDeque

/** NT `EventQueueTest.java` (S3; 6 çağrı, 13 çalışma): sınırlı kuyruk, reddedilen gönderim. */
class EventQueueTest : NinebotSuite() {
    @Test fun eventQueueAssertions() {
        val writer = ArrayDeque<Runnable>(); val queue = NinebotEventQueue({ task -> writer.add(task); true }, 8); val events = ArrayList<Int>()
        for (i in 0 until 8) { val n = i; check(queue.post { events.add(n) }, "accept event $i") }
        check(!queue.post { events.add(99) } && queue.size == 8, "bounded queue rejects overload")
        val ended = booleanArrayOf(false); writer.add { check(events == listOf(0, 1, 2, 3, 4, 5, 6, 7), "stop finalizes after accepted events in order"); ended[0] = true }
        while (!writer.isEmpty()) writer.remove().run(); check(ended[0] && queue.size == 0, "drained stop releases queue slots")
        queue.post { throw IllegalStateException("disk error") }; try { writer.remove().run() } catch (expected: IllegalStateException) {}; check(queue.size == 0, "disk exception releases capacity")
        val closed = NinebotEventQueue({ false }, 2); check(!closed.post {} && closed.size == 0, "closed handler does not leak capacity")
        assertEquals("event queue assertions passed", 13, count)
    }
}
