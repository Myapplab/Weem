package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S45: G3 navigasyon çerçeveleri 28 Kasım HCI kaydındaki resmî Segway baytlarıyla aynı. */
class G3DisplayTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun framesMatchOfficialCapture() {
        assertEquals("01", hex(G3Display.start().data))
        assertEquals("a61b00008c06000002001f00000000000000", hex(G3Display.guide(7078, 1676, 2, 31).data))
        assertEquals(3, G3Display.guide(0, 0, 0, 0).index)
        assertEquals("756e6e616d656420726f616400", hex(G3Display.street(0x10, "unnamed road").data))
        assertEquals("a61b0000", hex(G3Display.remaining(7078).data))
        assertEquals(1, G3Display.remaining(0).index)
    }

    @Test fun streetIsBoundedWithoutSplittingUtf8() {
        val f = G3Display.street(0x24, "ş".repeat(40))
        assertEquals(0, f.data.last().toInt())
        assertEquals(true, f.data.size - 1 <= G3Display.STREET_MAX)
        assertEquals("ş".repeat((f.data.size - 1) / 2), String(f.data, 0, f.data.size - 1, Charsets.UTF_8))
    }

    @Test fun probeStartsThenWalksCodes() {
        assertEquals(1, G3Display.probe(0)!!.size)
        val s = G3Display.probe(6)!!
        assertEquals(listOf(3, 0x10, 0x24, 1), s.map { it.index })
        assertEquals(5, s[0].data[8].toInt())
        assertNull(G3Display.probe(G3Display.PROBE_LAST + 2))
    }
}
