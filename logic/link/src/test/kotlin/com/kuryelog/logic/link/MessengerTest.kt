package com.kuryelog.logic.link

import com.kuryelog.logic.link.LinkStatus.State.LINKED
import com.kuryelog.logic.link.LinkStatus.State.SEARCHING
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

/**
 * LINK.md 3.6–3.9 tek başına: `Messenger` sahte bir kanala yazar (gönderim sonucu elle), zaman elle ilerler.
 * Uçtan uca senaryolar (iki telefon, Wi-Fi ↔ Bluetooth, yeniden başlatma) `MessengerLinkTest`'te.
 */
class MessengerTest {
    private val peer = PeerId.of("22222222-2222-4222-8222-222222222222")!!
    private var now = 0L
    private val wallBase = 1_760_000_000_000L
    private val wall get() = wallBase + now
    private val store = MemoryMessengerStore()
    private var next = 0
    private val ids: () -> UUID = { UUID(0x1234, (++next).toLong()) }

    /** (zaman, zarf, kanal taşıması) */
    private val sent = ArrayList<Triple<Long, Envelope, Transport>>()
    private var transport = Transport.WIFI
    private var linkUp = true
    private val out = ArrayList<LinkCommand>()

    private fun messenger(cfg: MessengerConfig = MessengerConfig()): Messenger = Messenger(store, cfg, ids).also { m ->
        m.port = { b ->
            when {
                !linkUp -> SendResult.NO_LINK
                transport == Transport.BT && b.size + 16 > 4096 -> SendResult.TOO_BIG_FOR_BT
                else -> {
                    sent += Triple(now, Envelope.decode(b)!!, transport)
                    SendResult.SENT
                }
            }
        }
    }

    private fun Messenger.status(channel: Int?, t: Transport = Transport.WIFI) {
        transport = t
        linkUp = channel != null
        val s = if (channel == null) LinkStatus(SEARCHING, sinceMs = now) else LinkStatus(LINKED, t, peer, sinceMs = now, channel = channel)
        onStatus(s, now, wall)
        pump(now, wall)
        out += drain()
    }

    private fun Messenger.at(t: Long) {
        now = t
        pump(now, wall)
        out += drain()
    }

    /** Uyanma zamanlarını izleyerek [until]'e kadar ilerler. */
    private fun Messenger.runUntil(until: Long) {
        while (true) {
            val w = nextWakeMs()
            if (w == null || w > until) break
            at(maxOf(now, w))
        }
        at(until)
    }

    private fun Messenger.receive(e: Envelope, t: Transport = Transport.WIFI) {
        onData(e.encode(), t, now, wall)
        pump(now, wall)
        out += drain()
    }

    private fun data() = sent.filter { !it.second.type.startsWith("link.") }
    private fun acksSent() = sent.filter { it.second.kind == EnvelopeKind.ACK }
    private fun ackIds(e: Envelope): List<UUID> {
        val b = ByteBuffer.wrap(e.payload)
        val n = b.short.toInt() and 0xffff
        return List(n) { UUID(b.long, b.long) }
    }

    private fun ack(vararg ids: UUID): Envelope {
        val b = ByteBuffer.allocate(2 + 16 * ids.size).putShort(ids.size.toShort())
        ids.forEach { b.putLong(it.mostSignificantBits).putLong(it.leastSignificantBits) }
        return Envelope(EnvelopeKind.ACK, Messenger.ACK, UUID.randomUUID(), 0, wall, 0, b.array())
    }

    private fun messages() = out.filterIsInstance<LinkCommand.Message>().map { it.message }

    @Test
    fun reliableRetransmitsWithExponentialBackoffUntilAcked() {
        val m = messenger()
        m.status(channel = 1)
        val id = m.publishReliable("EnergyRequest", "{}".toByteArray(), nowMs = now, wallMs = wall)
        m.at(0)
        m.runUntil(215_000)
        val times = data().map { it.first }
        assertEquals(listOf(0L, 3_000L, 9_000L, 21_000L, 45_000L, 93_000L, 153_000L, 213_000L), times)
        assertTrue(data().all { it.second.id == id && it.second.seq == data()[0].second.seq })
        m.receive(ack(id))
        assertEquals(listOf(LinkCommand.Acked("EnergyRequest", id)), out.filterIsInstance<LinkCommand.Acked>())
        assertTrue(store.outbox().isEmpty())
        m.runUntil(600_000)
        assertEquals(8, data().size)
    }

    @Test
    fun reliableWaitsForLinkAndResendsImmediatelyOnChannelChange() {
        val m = messenger()
        m.status(channel = null)
        val id = m.publishReliable("TripStages", byteArrayOf(1), nowMs = now, wallMs = wall)
        m.at(10_000)
        assertTrue(data().isEmpty())
        m.status(channel = 3)
        assertEquals(listOf(10_000L), data().map { it.first })
        m.at(11_000)
        // Bluetooth kanalı açıldı (Wi-Fi koptu): onaysız ileti RTO'yu beklemeden yeniden.
        m.status(channel = 4, t = Transport.BT)
        assertEquals(listOf(10_000L, 11_000L), data().map { it.first })
        assertEquals(Transport.BT, data().last().third)
        m.receive(ack(id), Transport.BT)
        assertTrue(store.outbox().isEmpty())
    }

    @Test
    fun inFlightWindowIsSixteen() {
        val m = messenger()
        m.status(channel = 1)
        val sentIds = List(20) { m.publishReliable("TripStages", byteArrayOf(it.toByte()), nowMs = now, wallMs = wall) }
        m.at(0)
        assertEquals(16, data().size)
        assertEquals(sentIds.take(16), data().map { it.second.id })
        // seq sırası korunur
        assertEquals(data().map { it.second.seq }.sorted(), data().map { it.second.seq })
        m.receive(ack(*sentIds.take(4).toTypedArray()))
        assertEquals(20, data().size)
        assertEquals(sentIds, data().map { it.second.id })
    }

    @Test
    fun bigMessagesWaitForWifiWhileSmallOnesUseBluetooth() {
        val m = messenger()
        m.status(channel = 1, t = Transport.BT)
        val big = m.publishReliable("Backup", ByteArray(5000), nowMs = now, wallMs = wall)
        val small = m.publishReliable("TripStages", ByteArray(10), nowMs = now, wallMs = wall)
        m.publishLatest("BatterySummary", ByteArray(6000), ttlMs = 60_000, nowMs = now, wallMs = wall)
        m.at(0)
        m.runUntil(30_000)
        assertTrue(data().none { it.second.id == big })
        assertTrue(data().any { it.second.id == small && it.third == Transport.BT })
        assertTrue(data().none { it.second.type == "BatterySummary" })
        m.status(channel = 2, t = Transport.WIFI)
        assertTrue(data().any { it.second.id == big && it.third == Transport.WIFI })
        assertTrue(data().any { it.second.type == "BatterySummary" && it.third == Transport.WIFI })
    }

    @Test
    fun latestMergesKeepsOnlyTheNewestAndExpires() {
        val m = messenger()
        m.status(channel = null)
        m.publishLatest("ShiftState", "1".toByteArray(), ttlMs = 60_000, nowMs = now, wallMs = wall)
        m.publishLatest("ShiftState", "2".toByteArray(), ttlMs = 60_000, nowMs = now, wallMs = wall)
        m.publishLatest("ShiftState", "3".toByteArray(), ttlMs = 60_000, nowMs = now, wallMs = wall)
        m.publishLatest("OfferSummary", "o".toByteArray(), ttlMs = 1_000, nowMs = now, wallMs = wall)
        m.at(5_000)
        m.status(channel = 1)
        assertEquals(listOf("ShiftState" to "3"), data().map { it.second.type to String(it.second.payload) })
        // Son durum onay beklemez, yeniden gönderilmez (kanal değişmedikçe).
        m.runUntil(400_000)
        assertEquals(1, data().count { it.second.type == "ShiftState" })
        assertTrue(acksSent().isEmpty())
    }

    @Test
    fun latestIsResentOnEveryChannelChangeOnly() {
        val m = messenger()
        m.status(channel = 1)
        m.publishLatest("RouteStatus", "r".toByteArray(), ttlMs = 600_000, nowMs = now, wallMs = wall)
        m.at(0)
        m.at(1_000)
        m.onStatus(LinkStatus(LINKED, Transport.WIFI, peer, sinceMs = 0, rttMs = 12, channel = 1), now, wall)
        m.at(1_000)
        assertEquals(1, data().size)
        m.status(channel = -5)
        assertEquals(2, data().size)
        assertEquals(data()[0].second.id, data()[1].second.id)
        assertEquals(data()[0].second.seq, data()[1].second.seq)
    }

    @Test
    fun receiverAcksEveryReliableAndProcessesItOnce() {
        val m = messenger()
        m.status(channel = 1)
        val id = UUID.randomUUID()
        val e = Envelope(EnvelopeKind.RELIABLE, "EnergyResult", id, 5, wall, 86_400_000, "x".toByteArray())
        m.receive(e)
        m.receive(e, Transport.BT)
        assertEquals(1, messages().size)
        assertEquals("EnergyResult", messages()[0].type)
        assertArrayEquals("x".toByteArray(), messages()[0].payload)
        assertEquals(2, acksSent().size)
        assertTrue(acksSent().all { ackIds(it.second) == listOf(id) })
        assertEquals(1, m.stats.duplicates)
    }

    @Test
    fun latestOlderSeqIsDroppedAndExpiredIsDropped() {
        val m = messenger()
        m.status(channel = 1)
        fun latest(seq: Long, sentAt: Long = wall, ttl: Long = 60_000) =
            Envelope(EnvelopeKind.LATEST, "OfferSummary", UUID.randomUUID(), seq, sentAt, ttl, seq.toString().toByteArray())
        m.receive(latest(10))
        m.receive(latest(9))
        m.receive(latest(10))
        m.receive(latest(11, sentAt = wall - 120_000))
        m.receive(latest(12, ttl = 30_000))
        assertEquals(listOf("10", "12"), messages().map { String(it.payload) })
        assertEquals(wall + 30_000, messages()[1].expiresAtMs)
        assertEquals(2, m.stats.stale)
        assertEquals(1, m.stats.expired)
        assertTrue(acksSent().isEmpty())
    }

    @Test
    fun malformedAndUnknownAreCountedNotFatal() {
        val m = messenger()
        m.status(channel = 1)
        m.onData(byteArrayOf(1, 2, 3), Transport.WIFI, now, wall)
        m.onData(byteArrayOf(9) + ByteArray(60), Transport.WIFI, now, wall)
        m.receive(Envelope(EnvelopeKind.CONTROL, "link.yeni", UUID.randomUUID(), 0, wall, 0, ByteArray(0)))
        m.receive(ack(UUID.randomUUID()))
        assertEquals(2, m.stats.malformed)
        assertTrue(messages().isEmpty())
    }

    @Test
    fun seenIdsSurviveRestartAndAreForgottenAfterTtlPlusMargin() {
        val m = messenger()
        m.status(channel = 1)
        val e = Envelope(EnvelopeKind.RELIABLE, "TripStages", UUID.randomUUID(), 1, wall, 60_000, ByteArray(1))
        m.receive(e)
        val again = messenger()
        again.status(channel = 2)
        again.receive(e)
        assertEquals(1, messages().size)
        assertEquals(1, again.stats.duplicates)
        // TTL + 1 sa sonra unutulur (gönderen artık yollamaz).
        now += 60_000 + 3_600_000 + 1
        val later = messenger()
        later.status(channel = 3)
        later.receive(e)
        assertEquals(2, messages().size)
    }

    @Test
    fun reliableSurvivesSenderRestartWithTheSameIdAndSeq() {
        val m = messenger()
        m.status(channel = null)
        val id = m.publishReliable("TripStages", byteArrayOf(7), nowMs = now, wallMs = wall)
        val restarted = messenger()
        restarted.status(channel = 1)
        assertEquals(listOf(id), data().map { it.second.id })
        assertEquals(1L, data()[0].second.seq)
    }

    @Test
    fun seqIsMonotonicAcrossRestarts() {
        val m = messenger()
        m.status(channel = 1)
        repeat(300) { m.publishLatest("T$it", ByteArray(0), ttlMs = 1_000, nowMs = now, wallMs = wall) }
        m.at(0)
        val last = data().maxOf { it.second.seq }
        assertEquals(300L, last)
        assertTrue(store.seqBlock() > 300)
        val restarted = messenger()
        restarted.status(channel = 2)
        restarted.publishLatest("X", ByteArray(0), ttlMs = 1_000, nowMs = now, wallMs = wall)
        restarted.at(now)
        assertTrue(data().last().second.seq > last)
    }

    @Test
    fun reliableExpiresAndOutboxIsCapped() {
        val m = messenger(MessengerConfig(maxOutbox = 3))
        m.status(channel = null)
        val first = m.publishReliable("A", ByteArray(0), ttlMs = 1_000, nowMs = now, wallMs = wall)
        val ids2 = List(3) { m.publishReliable("B", ByteArray(0), nowMs = now, wallMs = wall) }
        // 4. ileti sınırı aştı: en eskisi düştü.
        assertEquals(listOf(LinkCommand.Expired("A", first)), m.drain().filterIsInstance<LinkCommand.Expired>())
        val short = ids2[0]
        m.at(10_000)
        assertEquals(3, store.outbox().size)
        val m2 = messenger()
        m2.status(channel = null)
        m2.publishReliable("C", ByteArray(0), ttlMs = 1_000, nowMs = now, wallMs = wall)
        m2.at(now + 2_000)
        assertTrue(out.filterIsInstance<LinkCommand.Expired>().any { it.type == "C" })
        assertTrue(store.outbox().none { Envelope.decode(it.second)!!.type == "C" })
        assertTrue(store.outbox().any { Envelope.decode(it.second)!!.id == short })
    }

    @Test
    fun ttlLimits() {
        val m = messenger()
        for (bad in listOf(0L, 86_400_001L)) {
            try {
                m.publishLatest("X", ByteArray(0), ttlMs = bad, nowMs = now, wallMs = wall)
                throw AssertionError("son durum TTL $bad")
            } catch (e: IllegalArgumentException) {
                // beklenen
            }
        }
        try {
            m.publishReliable("X", ByteArray(0), ttlMs = 7 * 86_400_000L + 1, nowMs = now, wallMs = wall)
            throw AssertionError("teslim zorunlu TTL üst sınırı")
        } catch (e: IllegalArgumentException) {
            // beklenen
        }
        try {
            m.publishReliable("X", ByteArray(Messenger.MAX_PLAINTEXT), nowMs = now, wallMs = wall)
            throw AssertionError("çok büyük yük")
        } catch (e: IllegalArgumentException) {
            // beklenen
        }
    }

    @Test
    fun clockSyncAtChannelChangeAndEveryFiveMinutes() {
        val m = messenger()
        m.status(channel = 1)
        val requests = { sent.filter { it.second.type == Messenger.TIME && it.second.payload[0].toInt() == 1 } }
        assertEquals(1, requests().size)
        m.runUntil(1_200_000)
        assertEquals(listOf(0L, 300_000L, 600_000L, 900_000L, 1_200_000L), requests().map { it.first })
        // Yanıt: eş 2 500 ms ileride, gidiş-dönüş 40 ms.
        val t1 = ByteBuffer.wrap(requests().last().second.payload, 1, 8).long
        val reply = ByteBuffer.allocate(25).put(2).putLong(t1).putLong(t1 + 20 + 2_500).putLong(t1 + 20 + 2_500).array()
        now += 40
        m.receive(Envelope(EnvelopeKind.CONTROL, Messenger.TIME, UUID.randomUUID(), 0, wall, 0, reply))
        assertEquals(2_500L, m.clock.offsetMs)
        assertEquals(LinkCommand.Clock(2_500, 20), out.filterIsInstance<LinkCommand.Clock>().last())
        m.status(channel = 2)
        assertEquals(6, requests().size)
    }

    @Test
    fun timeRequestIsAnswered() {
        val m = messenger()
        m.status(channel = 1)
        sent.clear()
        val req = ByteBuffer.allocate(9).put(1).putLong(123_456L).array()
        m.receive(Envelope(EnvelopeKind.CONTROL, Messenger.TIME, UUID.randomUUID(), 0, 123_456L, 0, req))
        val r = sent.single { it.second.type == Messenger.TIME }.second.payload
        val b = ByteBuffer.wrap(r)
        assertEquals(2, b.get().toInt())
        assertEquals(123_456L, b.long)
        assertEquals(wall, b.long)
        assertEquals(wall, b.long)
    }

    @Test
    fun receivedTimesAreCorrectedWithTheOffset() {
        val m = messenger()
        m.status(channel = 1)
        val t1 = ByteBuffer.wrap(sent.first { it.second.type == Messenger.TIME }.second.payload, 1, 8).long
        val skew = -90_000L
        m.receive(Envelope(EnvelopeKind.CONTROL, Messenger.TIME, UUID.randomUUID(), 0, wall, 0,
            ByteBuffer.allocate(25).put(2).putLong(t1).putLong(t1 + skew).putLong(t1 + skew).array()))
        assertEquals(skew, m.clock.offsetMs)
        // Eşin saati 90 sn geride: onun 60 sn TTL'li durumu bizim saatimizle hâlâ taze.
        m.receive(Envelope(EnvelopeKind.LATEST, "BatterySummary", UUID.randomUUID(), 1, wall + skew, 60_000, ByteArray(0)))
        val got = messages().single()
        assertEquals(wall, got.localSentAtMs)
        assertEquals(wall + 60_000, got.expiresAtMs)
    }

    @Test
    fun unpairIsSentAndReceived() {
        val m = messenger()
        m.status(channel = 1)
        m.unpair(now, wall)
        assertTrue(sent.any { it.second.type == Messenger.UNPAIR && it.second.kind == EnvelopeKind.CONTROL })
        m.receive(Envelope(EnvelopeKind.CONTROL, Messenger.UNPAIR, UUID.randomUUID(), 0, wall, 0, ByteArray(0)))
        assertTrue(out.contains(LinkCommand.Unpaired))
    }

    @Test
    fun resetClearsQueuesAndDedupe() {
        val m = messenger()
        m.status(channel = null)
        m.publishReliable("TripStages", ByteArray(1), nowMs = now, wallMs = wall)
        m.publishLatest("ShiftState", ByteArray(1), ttlMs = 60_000, nowMs = now, wallMs = wall)
        m.reset()
        assertTrue(store.outbox().isEmpty())
        assertNull(store.seen())
        m.status(channel = 1)
        assertTrue(data().isEmpty())
    }
}
