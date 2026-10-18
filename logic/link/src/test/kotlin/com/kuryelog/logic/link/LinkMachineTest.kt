package com.kuryelog.logic.link

import com.kuryelog.logic.link.LinkStatus.State.LINKED
import com.kuryelog.logic.link.LinkStatus.State.OFF
import com.kuryelog.logic.link.LinkStatus.State.SEARCHING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LINK.md 1.8: iki makine sahte ağda. A'nın kimliği B'ninkinden küçük. */
class LinkMachineTest {
    private val net = FakeNet()
    private val a = net.node("A", "11111111-1111-4111-8111-111111111111", "AA:00:00:00:00:01")
    private val b = net.node("B", "22222222-2222-4222-8222-222222222222", "BB:00:00:00:00:02")

    private fun assertLinked(t: Transport) {
        for ((n, peer) in listOf(a to b, b to a)) {
            assertEquals("${n.name} durum", LINKED, n.status.state)
            assertEquals("${n.name} taşıma", t, n.status.transport)
            assertEquals("${n.name} eş", peer.id, n.status.peer)
            assertEquals("${n.name} tek kanal", 1, n.links(t).size)
        }
    }

    @Test
    fun hotspotOnA_clientConnectsToGateway() {
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        assertLinked(Transport.WIFI)
        assertTrue(b.connects(Transport.WIFI).any { (it.second as LinkCommand.Connect).host == "192.168.43.1" })
        assertEquals(listOf(b.id), a.peersSeen)
        assertEquals(listOf(a.id), b.peersSeen)
    }

    @Test
    fun hotspotOnB_sameResult() {
        net.hotspot(owner = b, client = a)
        a.enable()
        b.enable()
        net.run(1_000)
        assertLinked(Transport.WIFI)
    }

    @Test
    fun ownerFindsClientByBeaconWhenClientStartsLater() {
        net.hotspot(owner = a, client = b)
        a.enable()
        net.run(30_000)
        assertEquals(SEARCHING, a.status.state)
        // İstemci geç açıldı ve ağ geçidi bilgisi yok (ör. eski Android): sahip onun işaretiyle bulur.
        b.handle(LinkEvent.Lan(true, null))
        b.enable()
        net.run(3_000)
        assertLinked(Transport.WIFI)
    }

    @Test
    fun homeWifiRouterRefusesButBeaconsLink() {
        a.join("home", "10.0.0.5", gateway = "10.0.0.1")
        b.join("home", "10.0.0.6", gateway = "10.0.0.1")
        a.enable()
        b.enable()
        net.run(3_000)
        assertLinked(Transport.WIFI)
    }

    @Test
    fun crossConnectEndsWithOneChannelOpenedBySmallerId() {
        a.join("lan", "10.0.0.5", gateway = null)
        b.join("lan", "10.0.0.6", gateway = null)
        a.enable()
        b.enable()
        net.run(5_000)
        assertLinked(Transport.WIFI)
        // Kalan kanalı A açtı: A'da giden (> 0), B'de gelen (< 0).
        assertTrue(a.links(Transport.WIFI).keys.single() > 0)
        assertTrue(b.links(Transport.WIFI).keys.single() < 0)
        net.run(60_000)
        assertLinked(Transport.WIFI)
    }

    @Test
    fun dataBothWaysByteByByte() {
        net.chunk = 1
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        assertEquals(SendResult.SENT, a.send("merhaba"))
        assertEquals(SendResult.SENT, b.send("salam"))
        net.run(1_000)
        assertEquals(listOf("merhaba" to Transport.WIFI), b.delivered)
        assertEquals(listOf("salam" to Transport.WIFI), a.delivered)
    }

    @Test
    fun sendResults() {
        assertEquals(SendResult.NO_LINK, a.send("x"))
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        assertEquals(SendResult.NO_LINK, a.send("x"))
        net.run(1_000)
        assertEquals(SendResult.TOO_BIG, a.sendBytes(ByteArray(net.cfg.wifiMaxPayload + 1)))
        assertEquals(SendResult.SENT, a.sendBytes(ByteArray(net.cfg.wifiMaxPayload)))
    }

    @Test
    fun idleLinkStaysUpWithPingAndMeasuresRtt() {
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(60_000)
        assertLinked(Transport.WIFI)
        assertEquals(2 * net.latencyMs, a.status.rttMs)
        // Bağlantı 60 sn boyunca hiç kopmadı: yeni bağlanma denemesi yok.
        val connects = a.connects(Transport.WIFI).size + b.connects(Transport.WIFI).size
        net.run(60_000)
        assertEquals(connects, a.connects(Transport.WIFI).size + b.connects(Transport.WIFI).size)
    }

    @Test
    fun peerThatNeverSaysHelloIsDroppedWithBackoff() {
        b.mute = true
        b.listenMute(Transport.WIFI)
        net.hotspot(owner = b, client = a)
        a.enable()
        net.run(60_000)
        assertFalse(a.statuses.any { it.state == LINKED })
        val times = a.connects(Transport.WIFI).map { it.first }
        // El sıkışma 5 sn bekler, sonra geri çekilme 1, 2, 4, 8, 16 sn.
        assertEquals(listOf(0L, 6_005L, 13_010L, 22_015L, 35_020L, 56_025L), times)
    }

    @Test
    fun wrongPeerIsRejected() {
        val c = PeerId.of("33333333-3333-4333-8333-333333333333")!!
        net.hotspot(owner = a, client = b)
        a.enable(expected = c)
        b.enable()
        net.run(30_000)
        assertFalse(a.statuses.any { it.state == LINKED })
        // B beklenen eş tanımlamadığı için A'nın BYE'ına kadar (birkaç ms) bağlı görünebilir; denemeleri yine de seyrekleşir.
        assertEquals(SEARCHING, b.status.state)
        val times = b.connects(Transport.WIFI).map { it.first }
        assertTrue("geri çekilme: $times", times.size <= 6)
        assertTrue("aralıklar büyür: $times", times.zipWithNext { x, y -> y - x }.zipWithNext { p, q -> q >= p }.all { it })
    }

    @Test
    fun selfIsNeverAPeer() {
        net.hotspot(owner = a, client = b)
        val clone = net.node("A2", a.id.text, "AA:00:00:00:00:09")
        clone.handle(LinkEvent.Lan(true, "192.168.43.1"))
        clone.ip = "192.168.43.78"
        clone.lanName = "hs"
        a.enable()
        clone.enable()
        net.run(10_000)
        assertFalse(a.statuses.any { it.state == LINKED })
    }

    @Test
    fun silentPartitionDetectedThenRelinks() {
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(11_000)
        net.wifiCut = true
        net.run(21_000)
        assertEquals(SEARCHING, a.status.state)
        assertEquals(SEARCHING, b.status.state)
        // 10 sn'den uzun açık kalmış kanal koptu: istemci ağ geçidini hemen yeniden dener (geri çekilmesiz).
        val closedAt = b.log.last { it.second is LinkCommand.Close }.first
        assertEquals(closedAt, b.connects(Transport.WIFI).first { it.first >= closedAt }.first)
        net.wifiCut = false
        net.run(35_000)
        assertLinked(Transport.WIFI)
    }

    @Test
    fun hotspotOffFallsBackToBluetoothAndBack() {
        net.hotspot(owner = a, client = b)
        net.bond(a, b)
        a.enable()
        b.enable()
        net.run(1_000)
        assertLinked(Transport.WIFI)
        assertTrue(a.links(Transport.BT).isEmpty())

        // Hotspot kapandı: yerel ağ yok → yoklama 3 sn → Bluetooth hemen.
        a.leaveLan()
        b.leaveLan()
        net.run(6_000)
        assertLinked(Transport.BT)
        assertEquals(listOf(b.btAddress), a.btLearned)
        assertEquals(listOf(a.btAddress), b.btLearned)
        assertEquals(SendResult.SENT, a.send("bt"))
        assertEquals(SendResult.TOO_BIG_FOR_BT, a.sendBytes(ByteArray(net.cfg.btMaxPayload + 1)))
        assertEquals(SendResult.SENT, b.sendBytes(ByteArray(net.cfg.btMaxPayload)))
        net.run(100)
        assertEquals("bt" to Transport.BT, b.delivered.last())

        // Hotspot geri: TCP açılır, etkin kanal Wi-Fi olur, Bluetooth 3 sn sonra kapanır.
        net.hotspot(owner = a, client = b)
        net.run(1_000)
        assertEquals(Transport.WIFI, a.status.transport)
        assertEquals(Transport.WIFI, b.status.transport)
        net.run(5_000)
        assertLinked(Transport.WIFI)
        assertTrue(a.links(Transport.BT).isEmpty())
        assertTrue(b.links(Transport.BT).isEmpty())
        assertEquals(SendResult.SENT, b.send("wifi"))
        net.run(100)
        assertEquals("wifi" to Transport.WIFI, a.delivered.last())
        net.run(120_000)
        assertLinked(Transport.WIFI)
        assertTrue(a.links(Transport.BT).isEmpty())
    }

    @Test
    fun bluetoothWaitsGraceWhenLanPresentButPeerUnreachable() {
        net.wifiCut = true
        net.hotspot(owner = a, client = b)
        net.bond(a, b)
        a.enable()
        b.enable()
        net.run(9_900)
        assertTrue(a.connects(Transport.BT).isEmpty())
        assertTrue(b.connects(Transport.BT).isEmpty())
        net.run(3_000)
        assertLinked(Transport.BT)
    }

    @Test
    fun listenOnlyPhoneIsCalledByTheOther() {
        net.bond(a, b)
        a.handle(LinkEvent.Lan(false, null))
        b.handle(LinkEvent.Lan(false, null))
        a.enable(listenOnly = true)
        b.enable()
        net.run(3_000)
        assertLinked(Transport.BT)
        assertTrue(a.connects(Transport.BT).isEmpty())
        assertFalse(b.connects(Transport.BT).isEmpty())
    }

    @Test
    fun bothListenOnlyNeverLink() {
        net.bond(a, b)
        a.enable(listenOnly = true)
        b.enable(listenOnly = true)
        net.run(60_000)
        assertEquals(SEARCHING, a.status.state)
        assertTrue(a.status.btOn && a.status.btPhones)
        assertTrue(a.connects(Transport.BT).isEmpty() && b.connects(Transport.BT).isEmpty())
    }

    @Test
    fun noBondedPhoneMeansNoBluetoothAttempts() {
        a.handle(LinkEvent.Bt(true, listOf(BondedDevice("CC:00:00:00:00:03", isPhone = false))))
        a.enable()
        net.run(30_000)
        assertEquals(SEARCHING, a.status.state)
        assertFalse(a.status.btPhones)
        assertTrue(a.connects(Transport.BT).isEmpty())
    }

    @Test
    fun learnedAddressIsTheOnlyBluetoothTarget() {
        val ghost = BondedDevice("AB:00:00:00:00:00", isPhone = true)
        a.bonded = listOf(ghost)
        net.bond(a, b)
        a.enable(btPeer = b.btAddress)
        net.run(40_000)
        val hosts = a.connects(Transport.BT).map { (it.second as LinkCommand.Connect).host }.toSet()
        assertEquals(setOf(b.btAddress), hosts)
    }

    @Test
    fun bluetoothRotatesBondedPhonesUntilPeerAnswers() {
        val ghost = BondedDevice("AB:00:00:00:00:00", isPhone = true)
        a.bonded = listOf(ghost)
        net.bond(a, b)
        a.enable()
        b.enable(listenOnly = true)
        net.run(10_000)
        assertLinked(Transport.BT)
        val hosts = a.connects(Transport.BT).map { (it.second as LinkCommand.Connect).host }
        assertEquals(listOf("AB:00:00:00:00:00", b.btAddress), hosts)
    }

    @Test
    fun disableSaysByeAndPeerSearches() {
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        a.handle(LinkEvent.Disable)
        assertEquals(OFF, a.status.state)
        assertTrue(a.log.any { it.second == LinkCommand.Listen(Transport.WIFI, false) })
        assertEquals(LinkCommand.Wake(null), a.log.last { it.second is LinkCommand.Wake }.second)
        net.run(100)
        assertEquals(SEARCHING, b.status.state)
        net.run(60_000)
        assertEquals(SEARCHING, b.status.state)
        // Yeniden açılınca bağlanır.
        a.enable()
        net.run(35_000)
        assertLinked(Transport.WIFI)
    }

    @Test
    fun networkChangeRetriesImmediately() {
        a.join("hs", "192.168.43.77", gateway = "192.168.43.1")
        a.enable()
        net.run(60_000)
        val before = a.connects(Transport.WIFI).size
        assertTrue(before in 5..8)
        a.handle(LinkEvent.Lan(true, "192.168.50.1"))
        val last = a.log.last { it.second is LinkCommand.Connect }
        assertEquals(net.now, last.first)
        assertEquals("192.168.50.1", (last.second as LinkCommand.Connect).host)
    }

    @Test
    fun beaconsSlowDownAfterAMinute() {
        a.join("hs", "192.168.43.1", gateway = null)
        a.enable()
        net.run(120_000)
        val times = a.log.filter { it.second is LinkCommand.SendBeacon }.map { it.first }
        val gaps = times.zipWithNext { x, y -> y - x }
        assertTrue(gaps.take(29).all { it == 2_000L })
        assertTrue(gaps.drop(31).all { it == 5_000L })
    }

    @Test
    fun beaconsStopWhileLinked() {
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        val n = a.log.count { it.second is LinkCommand.SendBeacon }
        net.run(60_000)
        assertEquals(n, a.log.count { it.second is LinkCommand.SendBeacon })
    }

    @Test
    fun corruptBytesCloseTheChannel() {
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        val ch = a.links(Transport.WIFI).keys.single()
        a.handle(LinkEvent.Bytes(ch, byteArrayOf(0, 0, 0, 1, 99)))
        assertTrue(a.log.any { it.second == LinkCommand.Close(ch) })
        net.run(5_000)
        assertLinked(Transport.WIFI)
    }

    @Test
    fun unknownOpenedChannelIsClosed() {
        a.enable()
        a.handle(LinkEvent.Opened(42))
        assertTrue(a.log.any { it.second == LinkCommand.Close(42) })
        a.handle(LinkEvent.Disable)
        a.handle(LinkEvent.Accepted(-7, Transport.WIFI, "10.0.0.9"))
        assertTrue(a.log.any { it.second == LinkCommand.Close(-7) })
    }
}
