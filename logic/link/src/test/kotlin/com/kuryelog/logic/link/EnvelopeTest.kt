package com.kuryelog.logic.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

/** LINK.md 3.6: zarf v1 ikili düzeni, sınırlar, bozuk girdi. */
class EnvelopeTest {
    private val id = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")

    @Test
    fun roundTripAndLayout() {
        val e = Envelope(EnvelopeKind.RELIABLE, "TripStages", id, 0x0102030405060708L, 1_760_000_000_123L, 86_400_000L, "{\"a\":1}".toByteArray())
        val b = e.encode()
        assertEquals(39 + "TripStages".length + 7, b.size)
        assertEquals(1, b[0].toInt())
        assertEquals(2, b[1].toInt())
        assertEquals(10, b[2].toInt())
        val buf = ByteBuffer.wrap(b, 3 + 10, 16 + 8 + 8 + 4)
        assertEquals(id, UUID(buf.long, buf.long))
        assertEquals(0x0102030405060708L, buf.long)
        assertEquals(1_760_000_000_123L, buf.long)
        assertEquals(86_400_000, buf.int)
        val back = Envelope.decode(b)!!
        assertEquals(EnvelopeKind.RELIABLE, back.kind)
        assertEquals("TripStages", back.type)
        assertEquals(id, back.id)
        assertEquals(0x0102030405060708L, back.seq)
        assertEquals(1_760_000_000_123L, back.sentAtMs)
        assertEquals(86_400_000L, back.ttlMs)
        assertArrayEquals("{\"a\":1}".toByteArray(), back.payload)
        assertEquals(Envelope.headerSize("TripStages"), b.size - 7)
    }

    @Test
    fun allKindsAndEmptyPayload() {
        for (k in EnvelopeKind.entries) {
            val e = Envelope(k, "link.ack", id, 0, 0, 0, ByteArray(0))
            assertEquals(k, Envelope.decode(e.encode())!!.kind)
        }
    }

    @Test
    fun invalidTypesCannotBeBuilt() {
        for (t in listOf("", "a b", "ş", "x".repeat(49), "a/b")) {
            try {
                Envelope(EnvelopeKind.LATEST, t, id, 1, 0, 1, ByteArray(0))
                throw AssertionError("'$t' kabul edilmemeli")
            } catch (e: IllegalArgumentException) {
                // beklenen
            }
        }
        Envelope(EnvelopeKind.LATEST, "A-z_0.9", id, 1, 0, 1, ByteArray(0))
        Envelope(EnvelopeKind.LATEST, "x".repeat(48), id, 1, 0, 1, ByteArray(0))
    }

    @Test
    fun corruptBytesAreRejected() {
        val good = Envelope(EnvelopeKind.LATEST, "ShiftState", id, 7, 1, 1000, byteArrayOf(1, 2)).encode()
        assertNull(Envelope.decode(ByteArray(0)))
        assertNull(Envelope.decode(good.copyOf(20)))
        assertNull(Envelope.decode(good.copyOf().also { it[0] = 2 }))
        assertNull(Envelope.decode(good.copyOf().also { it[1] = 0 }))
        assertNull(Envelope.decode(good.copyOf().also { it[1] = 5 }))
        assertNull(Envelope.decode(good.copyOf().also { it[2] = 0 }))
        assertNull(Envelope.decode(good.copyOf().also { it[2] = 60 }))
        assertNull(Envelope.decode(good.copyOf().also { it[3] = ' '.code.toByte() }))
        // ttlMs u32: en büyük değer okunur
        val big = Envelope(EnvelopeKind.LATEST, "x", id, 1, 0, 0xffffffffL, ByteArray(0))
        assertEquals(0xffffffffL, Envelope.decode(big.encode())!!.ttlMs)
    }
}
