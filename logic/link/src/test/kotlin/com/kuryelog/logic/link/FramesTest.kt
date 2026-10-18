package com.kuryelog.logic.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramesTest {
    private val a = PeerId.of("0f0e0d0c-0b0a-4908-8706-050403020100")!!

    @Test
    fun peerIdRoundTripAndOrder() {
        assertEquals(a, PeerId.fromBytes(a.bytes(), 0))
        assertEquals(a, PeerId.of(" 0F0E0D0C-0B0A-4908-8706-050403020100 "))
        assertNull(PeerId.of("x"))
        assertNull(PeerId.of(null))
        val b = PeerId.of("ff000000-0000-4000-8000-000000000000")!!
        assertTrue(a < b)
    }

    @Test
    fun encodeLayout() {
        val f = Frames.encode(FrameType.DATA, byteArrayOf(7, 8))
        assertArrayEquals(byteArrayOf(0, 0, 0, 3, 2, 7, 8), f)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1, 2), Frames.encode(FrameType.DATA))
    }

    @Test
    fun helloRoundTrip() {
        val frames = FrameDecoder(1024).feed(Frames.hello(a))
        assertEquals(1, frames.size)
        assertEquals(FrameType.HELLO, frames[0].type)
        val h = Frames.parseHello(frames[0].payload)!!
        assertEquals(Frames.LINK_VERSION, h.version)
        assertEquals(a, h.peer)
        assertEquals(2, Frames.parseHello(FrameDecoder(1024).feed(Frames.hello(a, version = 2))[0].payload)!!.version)
    }

    @Test
    fun helloRejectsForeignBytes() {
        assertNull(Frames.parseHello(ByteArray(21)))
        assertNull(Frames.parseHello(ByteArray(3)))
        val p = FrameDecoder(1024).feed(Frames.hello(a))[0].payload
        p[0] = 'X'.code.toByte()
        assertNull(Frames.parseHello(p))
    }

    @Test
    fun pingPongAndBye() {
        val d = FrameDecoder(1024)
        val got = d.feed(Frames.ping(123_456_789_012L) + Frames.pong(Frames.encode(FrameType.DATA).copyOfRange(0, 0)) + Frames.bye(ByeReason.WRONG_PEER))
        assertEquals(listOf(FrameType.PING, FrameType.PONG, FrameType.BYE), got.map { it.type })
        assertEquals(123_456_789_012L, Frames.stamp(got[0].payload))
        assertNull(Frames.stamp(got[1].payload))
        assertEquals(ByeReason.WRONG_PEER, Frames.byeReason(got[2].payload))
        assertNull(Frames.byeReason(ByteArray(0)))
        assertNull(Frames.byeReason(byteArrayOf(99)))
    }

    @Test
    fun decoderByteByByte() {
        val payload = ByteArray(1000) { (it * 7).toByte() }
        val stream = Frames.encode(FrameType.DATA, payload) + Frames.ping(5) + Frames.encode(FrameType.DATA, byteArrayOf(1))
        val d = FrameDecoder(4096)
        val out = ArrayList<Frame>()
        for (b in stream) out += d.feed(byteArrayOf(b))
        assertEquals(3, out.size)
        assertArrayEquals(payload, out[0].payload)
        assertEquals(5L, Frames.stamp(out[1].payload))
        assertArrayEquals(byteArrayOf(1), out[2].payload)
        assertNull(d.error)
    }

    @Test
    fun decoderOddChunksAndOffset() {
        val stream = ByteArray(3) + Frames.encode(FrameType.DATA, ByteArray(300) { 1 }) + Frames.encode(FrameType.DATA, ByteArray(10) { 2 })
        val d = FrameDecoder(4096)
        val first = d.feed(stream, 3, 200)
        assertTrue(first.isEmpty())
        val rest = d.feed(stream, 203, stream.size - 203)
        assertEquals(listOf(300, 10), rest.map { it.payload.size })
    }

    @Test
    fun decoderLimits() {
        val ok = FrameDecoder(4).feed(Frames.encode(FrameType.DATA, ByteArray(4)))
        assertEquals(1, ok.size)
        val big = FrameDecoder(4)
        assertTrue(big.feed(Frames.encode(FrameType.DATA, ByteArray(5))).isEmpty())
        assertNotNull(big.error)
        assertTrue(big.feed(Frames.ping(1)).isEmpty())

        val zero = FrameDecoder(16)
        zero.feed(byteArrayOf(0, 0, 0, 0))
        assertEquals("bad_length:0", zero.error)

        val badType = FrameDecoder(16)
        val good = badType.feed(Frames.ping(9) + byteArrayOf(0, 0, 0, 1, 42))
        assertEquals(1, good.size)
        assertEquals("bad_type:42", badType.error)
    }
}
