package com.kuryelog.logic.link

import com.kuryelog.logic.link.LinkStatus.State.LINKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LINK.md 3.7–3.9 uçtan uca (`FakeNet`, iki eşleşmiş telefon): son durum ve teslim zorunlu kuyruklar, onay, yeniden başlatma,
 * Wi-Fi ↔ Bluetooth geçişinde iki kanaldan gelen tekrarlar, Bluetooth'ta büyük ileti, saati farklı telefonlar.
 */
class MessengerLinkTest {
    private val net = FakeNet()
    private val a = net.node("A", "11111111-1111-4111-8111-111111111111", "AA:00:00:00:00:01", messaging = true)
    private val b = net.node("B", "22222222-2222-4222-8222-222222222222", "BB:00:00:00:00:02", messaging = true)

    init {
        net.pair(a, b)
    }

    private fun linkWifi() {
        net.hotspot(owner = a, client = b)
        a.enable()
        b.enable()
        net.run(1_000)
        assertEquals(LINKED, a.status.state)
        assertEquals(Transport.WIFI, a.status.transport)
    }

    private fun texts(n: FakeNet.Node, type: String) = n.messages.filter { it.type == type }.map { String(it.payload) }

    @Test
    fun latestAndReliableFlowBothWays() {
        linkWifi()
        a.publishLatest("ShiftState", "açık")
        val id = a.publishReliable("TripStages", "olay 1")
        b.publishReliable("EnergyResult", "12 Wh")
        net.run(1_000)
        assertEquals(listOf("açık"), texts(b, "ShiftState"))
        assertEquals(listOf("olay 1"), texts(b, "TripStages"))
        assertEquals(listOf("12 Wh"), texts(a, "EnergyResult"))
        assertEquals(EnvelopeKind.LATEST, b.messages.first { it.type == "ShiftState" }.kind)
        assertEquals(EnvelopeKind.RELIABLE, b.messages.first { it.type == "TripStages" }.kind)
        assertEquals(listOf(LinkCommand.Acked("TripStages", id)), a.acked)
        assertTrue(a.store.outbox().isEmpty() && b.store.outbox().isEmpty())
    }

    @Test
    fun receiverRestartGetsTheLatestStateAgain() {
        linkWifi()
        a.publishLatest("RouteStatus", "hedef 1", ttlMs = 600_000)
        net.run(500)
        b.restart()
        net.run(5_000)
        assertEquals(LINKED, b.status.state)
        assertEquals(listOf("hedef 1", "hedef 1"), texts(b, "RouteStatus"))
    }

    @Test
    fun reliableSurvivesSenderRestart() {
        a.enable()
        val id = a.publishReliable("EnergyRequest", "bacak 7")
        net.run(1_000)
        a.restart()
        net.hotspot(owner = a, client = b)
        b.enable()
        net.run(2_000)
        assertEquals(listOf("bacak 7"), texts(b, "EnergyRequest"))
        assertEquals(id, b.messages.single().id)
        assertTrue(a.acked.any { it.id == id })
    }

    @Test
    fun sameIdIsProcessedOnceAcrossAReceiverRestart() {
        linkWifi()
        var dropAcks = true
        net.tamper = { from, _, _, frame -> if (dropAcks && from === b && FakeNet.frameType(frame) == FrameType.DATA) emptyList() else listOf(frame) }
        val id = a.publishReliable("TripStages", "teslim")
        net.run(500)
        assertEquals(1, b.messages.size)
        assertTrue(a.acked.isEmpty())
        dropAcks = false
        b.restart()
        net.run(10_000)
        assertEquals(1, b.messages.count { it.type == "TripStages" })
        assertEquals(id, a.acked.single().id)
        assertTrue(a.store.outbox().isEmpty())
        assertTrue(b.stack.messenger!!.stats.duplicates >= 1)
    }

    @Test
    fun bigReliableWaitsForWifiWhileSmallOnesUseBluetooth() {
        net.bond(a, b)
        a.handle(LinkEvent.Lan(false, null))
        b.handle(LinkEvent.Lan(false, null))
        a.enable()
        b.enable()
        net.run(3_000)
        assertEquals(Transport.BT, a.status.transport)
        val big = a.publishReliableBytes("Backup", ByteArray(6_000) { 7 })
        a.publishReliable("TripStages", "küçük")
        net.run(10_000)
        assertEquals(listOf("küçük"), texts(b, "TripStages"))
        assertTrue(b.messages.none { it.type == "Backup" })
        net.hotspot(owner = a, client = b)
        net.run(5_000)
        val got = b.messages.single { it.type == "Backup" }
        assertEquals(big, got.id)
        assertEquals(6_000, got.payload.size)
        assertEquals(Transport.WIFI, got.transport)
    }

    @Test
    fun wifiBluetoothSwitchDeliversEachMessageOnce() {
        net.btLatencyMs = 300
        net.bond(a, b)
        a.handle(LinkEvent.Lan(false, null))
        b.handle(LinkEvent.Lan(false, null))
        a.enable()
        b.enable()
        net.run(5_000)
        assertEquals(Transport.BT, a.status.transport)
        // Bluetooth'tan yola çıktılar (300 ms), hemen ardından Wi-Fi açıldı: etkin kanal değişince ikisi de yeniden gider.
        a.publishReliable("TripStages", "r1")
        a.publishLatest("ShiftState", "s1")
        net.hotspot(owner = a, client = b)
        net.run(100)
        assertEquals(Transport.WIFI, a.status.transport)
        a.publishLatest("ShiftState", "s2")
        net.run(5_000)
        assertEquals(listOf("r1"), texts(b, "TripStages"))
        // Bluetooth'tan geç gelen s1 (eski seq) atıldı.
        assertEquals(listOf("s1", "s2"), texts(b, "ShiftState"))
        val stats = b.stack.messenger!!.stats
        assertTrue("tekrar: ${stats.duplicates}, eski: ${stats.stale}", stats.duplicates >= 1 && stats.stale >= 1)
    }

    @Test
    fun clockOffsetBetweenSkewedPhones() {
        b.wallSkewMs = 7_345
        linkWifi()
        assertEquals(7_345L, a.stack.messenger!!.clock.offsetMs)
        assertEquals(-7_345L, b.stack.messenger!!.clock.offsetMs)
        assertEquals(LinkCommand.Clock(7_345, net.latencyMs), a.clocks.last())
        // B'nin saatiyle yazılmış zaman A'da düzeltilir.
        b.publishLatest("BatterySummary", "%80")
        net.run(100)
        val got = a.messages.single { it.type == "BatterySummary" }
        assertEquals(got.sentAtMs - 7_345, got.localSentAtMs)
        val before = a.log.count { (it.second as? LinkCommand.Write)?.let { FakeNet.frameType(it.bytes) } == FrameType.DATA }
        net.run(20 * 60_000L)
        // 5 dk'da bir ölçüm (istek + yanıt) ve PING dışında trafik yok.
        val after = a.log.count { (it.second as? LinkCommand.Write)?.let { FakeNet.frameType(it.bytes) } == FrameType.DATA }
        assertEquals(8, after - before)
    }

    @Test
    fun latestWhoseTtlPassesInTransitIsDropped() {
        // Yavaş Bluetooth (1,5 sn tek yön): el sıkışma yine 10 sn'ye sığar, 1 sn TTL'li durum yolda bayatlar.
        net.btLatencyMs = 1_500
        net.bond(a, b)
        a.handle(LinkEvent.Lan(false, null))
        b.handle(LinkEvent.Lan(false, null))
        a.enable()
        b.enable()
        net.run(30_000)
        assertEquals(LINKED, b.status.state)
        assertEquals(0L, b.stack.messenger!!.clock.offsetMs)
        a.publishLatest("OfferSummary", "teklif", ttlMs = 1_000)
        net.run(10_000)
        assertTrue(texts(b, "OfferSummary").isEmpty())
        assertTrue(b.stack.messenger!!.stats.expired >= 1)
    }

    @Test
    fun reliableThatExpiresUnsentIsReportedToTheSender() {
        a.enable()
        val id = a.publishReliable("EnergyRequest", "eski", ttlMs = 10_000)
        net.run(20_000)
        assertEquals(listOf(LinkCommand.Expired("EnergyRequest", id)), a.expired)
        net.hotspot(owner = a, client = b)
        b.enable()
        net.run(2_000)
        assertTrue(b.messages.none { it.type == "EnergyRequest" })
    }
}
