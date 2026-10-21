package com.kuryelog.logic.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

class StreamWireTest {
    @Test fun framesRoundTripWholeAndByteByByte() {
        val a = StreamFrames.encode(StreamFrames.HELLO, byteArrayOf(1, 2, 3))
        val b = StreamFrames.encode(StreamFrames.SEALED, ByteArray(70_000) { it.toByte() })
        val all = a + b
        val whole = StreamFrames.Decoder().feed(all)
        assertEquals(listOf(1, 2), whole.map { it.type })
        assertArrayEquals(byteArrayOf(1, 2, 3), whole[0].body)
        assertEquals(70_000, whole[1].body.size)
        val d = StreamFrames.Decoder()
        val got = all.flatMap { d.feed(byteArrayOf(it)) }
        assertEquals(2, got.size)
        assertArrayEquals(whole[1].body, got[1].body)
    }

    @Test fun badLengthAndTypeClose() {
        for (bad in listOf(
            ByteBuffer.allocate(5).putInt(0).put(1).array(),
            ByteBuffer.allocate(5).putInt(StreamFrames.MAX_LEN + 1).put(1).array(),
            StreamFrames.encode(1, ByteArray(0)).also { it[4] = 9 },
        )) {
            try {
                StreamFrames.Decoder().feed(bad)
                fail()
            } catch (e: StreamException) {
                assertEquals(CloseReason.PROTOCOL, e.reason)
            }
        }
    }

    @Test fun helloRoundTripAndForeign() {
        val s = UUID.randomUUID()
        val h = StreamHello(s, ByteArray(16) { 7 })
        val b = h.encode()
        assertEquals(36, b.size)
        val back = StreamHello.decode(b)!!
        assertEquals(s, back.session)
        assertArrayEquals(h.nonce, back.nonce)
        assertNull(StreamHello.decode(b.copyOf(35)))
        assertNull(StreamHello.decode(b.copyOf().also { it[0] = 'X'.code.toByte() }))
    }

    @Test fun innerMessagesRoundTripInTheirDirection() {
        val fromHost = listOf(
            StreamMsg.Config(576, 1280, byteArrayOf(0, 0, 0, 1, 0x67)),
            StreamMsg.Frame(0xFFFFFFFFL, 123_456_789L, true, byteArrayOf(9, 9)),
            StreamMsg.Ping,
            StreamMsg.Bye(StreamMsg.BYE_ENDED),
        )
        for (m in fromHost) {
            val back = StreamMsg.decode(StreamMsg.encode(m, fromHost = true), fromHost = true)!!
            assertEquals(m.javaClass, back.javaClass)
            // Yöne uymayan tür bozuk sayılır.
            assertNull(StreamMsg.decode(StreamMsg.encode(m, fromHost = true), fromHost = false))
        }
        val f = StreamMsg.decode(StreamMsg.encode(fromHost[1], true), true) as StreamMsg.Frame
        assertEquals(0xFFFFFFFFL, f.seq)
        assertEquals(123_456_789L, f.ptsUs)
        assertTrue(f.key)
        val c = StreamMsg.decode(StreamMsg.encode(fromHost[0], true), true) as StreamMsg.Config
        assertEquals(576, c.width)
        assertEquals(1280, c.height)
        val ack = StreamMsg.decode(StreamMsg.encode(StreamMsg.Ack(5, 1L shl 40), false), false) as StreamMsg.Ack
        assertEquals(5L, ack.seq)
        assertEquals(1L shl 40, ack.bytes)
        assertEquals(StreamMsg.KeyRequest, StreamMsg.decode(StreamMsg.encode(StreamMsg.KeyRequest, false), false))
        assertEquals(StreamMsg.BYE_ERROR, (StreamMsg.decode(StreamMsg.encode(StreamMsg.Bye(3), false), false) as StreamMsg.Bye).reason)
    }

    @Test fun unknownInnerTypeIsKeptForForwardCompatibilityButBrokenKnownIsNot() {
        assertEquals(30, (StreamMsg.decode(byteArrayOf(30, 1, 2), fromHost = false) as StreamMsg.Unknown).type)
        assertNull(StreamMsg.decode(byteArrayOf(StreamMsg.ACK.toByte(), 1), fromHost = false))
        assertNull(StreamMsg.decode(byteArrayOf(StreamMsg.CONFIG.toByte(), 0, 0, 0, 0), fromHost = true))
        assertNull(StreamMsg.decode(ByteArray(0), fromHost = true))
    }
}
