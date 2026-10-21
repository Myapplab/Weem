package com.kuryelog.logic.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class StreamCryptoTest {
    private val random = SecureRandom()
    private val session = UUID.randomUUID()
    private val secret = ByteArray(32) { (it * 3).toByte() }

    /** Paylaşan ve izleyen uçları birbirine bağlar; her yönün baytları ayrı listede birikir. */
    private class Pair(val host: StreamEndpoint, val viewer: StreamEndpoint, val toViewer: MutableList<ByteArray>, val toHost: MutableList<ByteArray>) {
        fun pumpHost(): List<StreamMsg> = toHost.toList().also { toHost.clear() }.flatMap { host.receive(it) }
        fun pumpViewer(): List<StreamMsg> = toViewer.toList().also { toViewer.clear() }.flatMap { viewer.receive(it) }
    }

    private fun pair(hostSecret: ByteArray = secret, viewerSecret: ByteArray = secret, hostKnows: UUID = session): Pair {
        val toViewer = mutableListOf<ByteArray>()
        val toHost = mutableListOf<ByteArray>()
        val host = StreamEndpoint.host(random, { if (it == hostKnows) hostSecret else null }) { toViewer += it }
        val viewer = StreamEndpoint.viewer(random, session, viewerSecret, StreamMsg.KeyRequest) { toHost += it }
        viewer.start()
        return Pair(host, viewer, toViewer, toHost)
    }

    @Test fun handshakeAuthenticatesViewerAndCarriesBothDirections() {
        val p = pair()
        assertTrue(p.pumpHost().isEmpty())
        assertFalse(p.host.authenticated)
        assertEquals(1, p.toViewer.size) // paylaşanın HELLO'su
        assertTrue(p.pumpViewer().isEmpty())
        assertTrue(p.viewer.authenticated)
        // İzleyenin ilk şifreli iletisi (KEY) paylaşanı doğrular.
        assertEquals(listOf<StreamMsg>(StreamMsg.KeyRequest), p.pumpHost())
        assertTrue(p.host.authenticated)
        assertEquals(session, p.host.session)
        p.host.send(StreamMsg.Frame(1, 2, true, byteArrayOf(5, 6, 7)))
        val f = p.pumpViewer().single() as StreamMsg.Frame
        assertArrayEquals(byteArrayOf(5, 6, 7), f.data)
        p.viewer.send(StreamMsg.Ack(1, 3))
        assertEquals(3L, (p.pumpHost().single() as StreamMsg.Ack).bytes)
    }

    @Test fun hostCannotSendBeforeViewerProvesTheKey() {
        val p = pair()
        p.pumpHost()
        try {
            p.host.send(StreamMsg.Ping)
            fail()
        } catch (e: IllegalStateException) {
            // beklenen
        }
    }

    @Test fun unknownSessionAndWrongKeyAreRejected() {
        val p = pair(hostKnows = UUID.randomUUID())
        try {
            p.pumpHost()
            fail()
        } catch (e: StreamException) {
            assertEquals(CloseReason.SESSION, e.reason)
        }
        val q = pair(viewerSecret = ByteArray(32))
        q.pumpHost()
        q.pumpViewer()
        try {
            q.pumpHost()
            fail()
        } catch (e: StreamException) {
            assertEquals(CloseReason.AUTH, e.reason)
        }
        assertFalse(q.host.authenticated)
    }

    @Test fun tamperedReplayedAndReorderedMessagesClose() {
        fun ready(): Pair = pair().also { it.pumpHost(); it.pumpViewer(); it.pumpHost() }
        // Değişmiş.
        val a = ready()
        a.host.send(StreamMsg.Ping)
        a.toViewer[0][10] = (a.toViewer[0][10].toInt() xor 1).toByte()
        expectAuth { a.pumpViewer() }
        // Tekrarlanmış.
        val b = ready()
        b.host.send(StreamMsg.Ping)
        val copy = b.toViewer[0].copyOf()
        b.pumpViewer()
        expectAuth { b.viewer.receive(copy) }
        // Sırası bozulmuş.
        val c = ready()
        c.host.send(StreamMsg.Ping)
        c.host.send(StreamMsg.Bye(1))
        val (first, second) = c.toViewer.toList()
        expectAuth { c.viewer.receive(second); c.viewer.receive(first) }
    }

    @Test fun everyConnectionHasFreshKeys() {
        val k1 = StreamKeys.derive(secret, session, ByteArray(16) { 1 }, ByteArray(16) { 2 })
        val k2 = StreamKeys.derive(secret, session, ByteArray(16) { 1 }, ByteArray(16) { 3 })
        assertNotEquals(k1.hostToViewer.toList(), k2.hostToViewer.toList())
        assertNotEquals(k1.hostToViewer.toList(), k1.viewerToHost.toList())
        // Aynı oturumda iki bağlantı: aynı düz metin farklı şifreli metin.
        val p = pair().also { it.pumpHost(); it.pumpViewer(); it.pumpHost() }
        val q = pair().also { it.pumpHost(); it.pumpViewer(); it.pumpHost() }
        p.host.send(StreamMsg.Ping)
        q.host.send(StreamMsg.Ping)
        assertNotEquals(p.toViewer[0].toList(), q.toViewer[0].toList())
    }

    @Test fun sealedBeforeHelloIsProtocolError() {
        val toViewer = mutableListOf<ByteArray>()
        val host = StreamEndpoint.host(random, { secret }) { toViewer += it }
        try {
            host.receive(StreamFrames.encode(StreamFrames.SEALED, ByteArray(20)))
            fail()
        } catch (e: StreamException) {
            assertEquals(CloseReason.PROTOCOL, e.reason)
        }
    }

    private fun expectAuth(block: () -> Unit) {
        try {
            block()
            fail()
        } catch (e: StreamException) {
            assertEquals(CloseReason.AUTH, e.reason)
        }
    }
}
