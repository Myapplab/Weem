package com.kuryelog.logic.link

import com.kuryelog.logic.link.LinkStatus.State.LINKED
import com.kuryelog.logic.link.LinkStatus.State.OFF
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LINK.md 3.3–3.5, 3.10–3.11 uçtan uca (`FakeNet`): QR eşleştirmesi, araya giren, bozulmuş/tekrarlanmış şifreli metin, yanlış eş,
 * sürüm farkı, eşleşmeyi kaldırma. A'nın kimliği B'ninkinden küçük.
 */
class SecureLinkTest {
    private val net = FakeNet()
    private val a = net.node("A", "11111111-1111-4111-8111-111111111111", "AA:00:00:00:00:01", messaging = true)
    private val b = net.node("B", "22222222-2222-4222-8222-222222222222", "BB:00:00:00:00:02", messaging = true)

    private fun linkedTogether(t: Transport) {
        for ((n, peer) in listOf(a to b, b to a)) {
            assertEquals("${n.name} durum", LINKED, n.status.state)
            assertEquals("${n.name} taşıma", t, n.status.transport)
            assertEquals("${n.name} eş", peer.id, n.status.peer)
        }
    }

    private fun assertPairedTogether() {
        assertEquals(1, a.paired.size)
        assertEquals(1, b.paired.size)
        assertEquals(b.id, a.paired[0].id)
        assertArrayEquals(b.keys.public, a.paired[0].publicKey)
        assertEquals("B", a.paired[0].name)
        assertEquals(a.id, b.paired[0].id)
        assertArrayEquals(a.keys.public, b.paired[0].publicKey)
        assertEquals("A", b.paired[0].name)
    }

    private fun neverLinked(vararg nodes: FakeNet.Node) {
        for (n in nodes) assertFalse("${n.name} bağlanmamalıydı", n.statuses.any { it.state == LINKED })
    }

    private fun byesWritten(n: FakeNet.Node): List<ByeReason> = n.log.mapNotNull { (_, c) ->
        val w = c as? LinkCommand.Write ?: return@mapNotNull null
        if (FakeNet.frameType(w.bytes) == FrameType.BYE) Frames.byeReason(w.bytes.copyOfRange(5, w.bytes.size)) else null
    }

    @Test
    fun scannerConnectsAndPairsOverWifi() {
        net.hotspot(owner = a, client = b)
        val qr = a.issue()
        b.scan(PairingQr.parse(qr.toUri()).let { (it as QrResult.Ok).qr })
        net.run(2_000)
        assertPairedTogether()
        linkedTogether(Transport.WIFI)
        // Eşleştirme kanalı açık kalır ve olağan oturum olur: mesaj hemen gider.
        b.publishReliable("TripStages", "ilk")
        net.run(100)
        assertEquals(listOf("ilk"), a.messages.map { String(it.payload) })
        assertEquals(0, a.pairingExpired + b.pairingExpired)
    }

    @Test
    fun issuerConnectsAndPairsOverWifi() {
        // A (veren) Wi-Fi istemcisi: B önce dinler, bağlantıyı A açar; Noise'da başlatan A.
        net.hotspot(owner = b, client = a)
        val token = ByteArray(32).also(net.random::nextBytes)
        b.scan(PairingQr(a.id, a.keys.public, token, a.info.deviceName))
        a.issue(token = token)
        net.run(2_000)
        assertPairedTogether()
        linkedTogether(Transport.WIFI)
        assertTrue(a.links(Transport.WIFI).keys.single() > 0)
    }

    @Test
    fun pairsOverBluetooth() {
        net.bond(a, b)
        a.handle(LinkEvent.Lan(false, null))
        b.handle(LinkEvent.Lan(false, null))
        val qr = a.issue()
        b.scan(qr)
        net.run(5_000)
        assertPairedTogether()
        linkedTogether(Transport.BT)
    }

    @Test
    fun wrongTokenNeverPairs() {
        net.hotspot(owner = a, client = b)
        val qr = a.issue()
        val token = qr.token.also { it[0] = (it[0].toInt() xor 1).toByte() }
        b.scan(PairingQr(qr.id, qr.publicKey, token, qr.name))
        net.run(60_000)
        assertTrue(a.paired.isEmpty() && b.paired.isEmpty())
        neverLinked(a, b)
        assertTrue(ByeReason.AUTH in byesWritten(a) + byesWritten(b))
        assertEquals(LinkIssue.AUTH, b.status.issue)
        // Yanlış denemeler jetonu harcamaz: doğru QR'la hâlâ eşleşilir.
        b.scan(qr)
        net.run(40_000)
        assertPairedTogether()
    }

    @Test
    fun expiredTokenNeverPairs() {
        val qr = a.issue(windowMs = 60_000)
        b.scan(qr)
        net.run(61_000)
        assertEquals(1, a.pairingExpired)
        net.hotspot(owner = a, client = b)
        net.run(30_000)
        assertTrue(a.paired.isEmpty() && b.paired.isEmpty())
        neverLinked(a, b)
        assertEquals(LinkIssue.PAIRING, b.status.issue)
    }

    @Test
    fun usedTokenCannotPairAThirdPhone() {
        net.hotspot(owner = a, client = b)
        val qr = a.issue()
        b.scan(qr)
        net.run(2_000)
        assertPairedTogether()
        val c = net.node("C", "33333333-3333-4333-8333-333333333333", "CC:00:00:00:00:03", messaging = true)
        c.join("hs", "192.168.43.88", gateway = "192.168.43.1")
        c.scan(qr)
        net.run(30_000)
        assertTrue(c.paired.isEmpty())
        neverLinked(c)
        assertEquals(1, a.paired.size)
        linkedTogether(Transport.WIFI)
    }

    @Test
    fun manInTheMiddleCannotCompletePairing() {
        net.hotspot(owner = a, client = b)
        // M, A'nın kimliğini taşır ama A'nın özel anahtarını ve jetonunu bilmez; iki yöndeki bağlantıları da üstüne çeker.
        val m = net.node("M", a.id.text, "EE:00:00:00:00:66", messaging = true)
        m.join("hs", "192.168.43.66", gateway = null)
        net.divert["192.168.43.1"] = m
        net.divert["192.168.43.77"] = m
        val fromB = ArrayList<FrameType?>()
        net.tamper = { from, to, _, frame ->
            if (from === b && to === m) fromB += FakeNet.frameType(frame)
            listOf(frame)
        }
        m.issue()
        val qr = a.issue()
        b.scan(qr)
        net.run(60_000)
        assertTrue(a.paired.isEmpty() && b.paired.isEmpty() && m.paired.isEmpty())
        neverLinked(a, b, m)
        assertTrue(byesWritten(b).contains(ByeReason.AUTH))
        // B, A'yı doğrulayamadığı için msg3'ü (kimlik anahtarı + bilgi) hiç yollamadı; şifreli veri de yok.
        assertTrue(fromB.isNotEmpty())
        assertTrue(fromB.none { it == FrameType.DATA })
        val noise = fromB.count { it == FrameType.NOISE }
        val hellos = fromB.count { it == FrameType.HELLO }
        assertTrue("B her bağlantıda yalnız msg1: $noise / $hellos", noise <= hellos)
    }

    @Test
    fun impostorWithoutThePrivateKeyCannotJoinAPairedLink() {
        net.pair(a, b)
        // A kapalı; M onun adresinde ve kimliğiyle, A'nın açık anahtarını gösterir ama özel anahtarı başka.
        val m = net.node("M", a.id.text, "EE:00:00:00:00:66", messaging = true)
        m.keys = DhKeyPair(P256.generate(net.random).private, a.keys.public)
        m.trust = Trust.Paired(b.record())
        m.join("hs", "192.168.43.1", gateway = null)
        b.join("hs", "192.168.43.77", gateway = "192.168.43.1")
        m.enable()
        b.enable()
        net.run(30_000)
        neverLinked(b, m)
        assertEquals(LinkIssue.AUTH, b.status.issue)
        // Kendi anahtarıyla gelen sahte A da aynı.
        m.keys = P256.generate(net.random)
        m.restart()
        net.run(30_000)
        neverLinked(b, m)
    }

    @Test
    fun peerWithAChangedKeyIsRejected() {
        net.pair(a, b)
        a.keys = P256.generate(net.random) // A'nın verisi silindi, yeni kimlik; B eski anahtarı biliyor
        net.hotspot(owner = a, client = b)
        a.enable(Trust.Paired(b.record()))
        b.enable()
        net.run(20_000)
        neverLinked(a, b)
        assertEquals(LinkIssue.AUTH, b.status.issue)
        assertEquals(LinkIssue.AUTH, a.status.issue)
    }

    @Test
    fun tamperedCiphertextClosesTheChannelAndTheMessageArrivesOnce() {
        net.pair(a, b)
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        linkedTogether(Transport.WIFI)
        var armed = true
        net.tamper = { from, _, _, frame ->
            if (armed && from === a && FakeNet.frameType(frame) == FrameType.DATA) {
                armed = false
                listOf(frame.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x40).toByte() })
            } else {
                listOf(frame)
            }
        }
        a.publishReliable("EnergyRequest", "x")
        net.run(10_000)
        assertFalse(armed)
        assertTrue(byesWritten(b).contains(ByeReason.AUTH))
        assertEquals(listOf("x"), b.messages.filter { it.type == "EnergyRequest" }.map { String(it.payload) })
        linkedTogether(Transport.WIFI)
        assertTrue(a.acked.any { it.type == "EnergyRequest" })
    }

    @Test
    fun replayedFrameClosesTheChannelAndIsNotDeliveredTwice() {
        net.pair(a, b)
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        var replay = false
        net.tamper = { from, _, _, frame ->
            if (replay && from === a && FakeNet.frameType(frame) == FrameType.DATA) {
                replay = false
                listOf(frame, frame)
            } else {
                listOf(frame)
            }
        }
        replay = true
        a.publishReliable("TripStages", "bir kez")
        net.run(10_000)
        assertTrue(byesWritten(b).contains(ByeReason.AUTH))
        assertEquals(1, b.messages.count { it.type == "TripStages" })
        linkedTogether(Transport.WIFI)
    }

    @Test
    fun majorVersionMismatchReportsTheVersionAndNeverLinks() {
        net.pair(a, b)
        b.info = PeerInfo(b.id, LinkProtocol.MAJOR + 1, 0, 99, "2.0.0", "B")
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(30_000)
        neverLinked(a, b)
        assertEquals(LinkIssue.VERSION, a.status.issue)
        assertEquals(LinkIssue.VERSION, b.status.issue)
        assertEquals("2.0.0", a.peerInfos.last().appName)
        assertEquals(LinkProtocol.MAJOR + 1, a.peerInfos.last().protoMajor)
        assertEquals(LinkProtocol.MAJOR, b.peerInfos.last().protoMajor)
        assertTrue(ByeReason.VERSION in byesWritten(a) + byesWritten(b))
        // Geri çekilme: 30 sn'de birkaç deneme.
        assertTrue(b.connects(Transport.WIFI).size <= 7)
    }

    @Test
    fun minorVersionDifferenceLinksAndIsReported() {
        net.pair(a, b)
        b.info = PeerInfo(b.id, LinkProtocol.MAJOR, LinkProtocol.MINOR + 7, 40, "1.4.0", "B")
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        linkedTogether(Transport.WIFI)
        assertEquals(LinkProtocol.MINOR + 7, a.peerInfos.last().protoMinor)
        assertEquals("1.4.0", a.peerInfos.last().appName)
        assertEquals(null, a.status.issue)
    }

    @Test
    fun unpairTellsThePeerAndBothStop() {
        net.pair(a, b)
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        a.unpair()
        net.run(1_000)
        assertEquals(1, b.unpaired)
        assertEquals(OFF, a.status.state)
        assertEquals(OFF, b.status.state)
    }

    @Test
    fun pairingWindowDropsTheOldPeerAndPairsTheNewOne() {
        net.pair(a, b)
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        linkedTogether(Transport.WIFI)
        val c = net.node("C", "33333333-3333-4333-8333-333333333333", "CC:00:00:00:00:03", messaging = true)
        c.join("hs", "192.168.43.88", gateway = "192.168.43.1")
        val qr = a.issue()
        net.run(5_000)
        // Pencere açıkken eski eş bağlanamaz.
        assertFalse(b.status.state == LINKED)
        assertEquals(LinkIssue.PAIRING, b.status.issue)
        c.scan(qr)
        net.run(5_000)
        assertEquals(listOf(c.id), a.paired.map { it.id })
        assertEquals(LINKED, c.status.state)
        assertEquals(c.id, a.status.peer)
        net.run(30_000)
        assertFalse(b.status.state == LINKED)
    }

    @Test
    fun handshakeThatStallsAfterHelloTimesOut() {
        net.pair(a, b)
        net.tamper = { from, _, _, frame -> if (from === b && FakeNet.frameType(frame) == FrameType.NOISE) emptyList() else listOf(frame) }
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(11_000)
        neverLinked(a, b)
        assertTrue(ByeReason.TIMEOUT in byesWritten(a))
    }
}
