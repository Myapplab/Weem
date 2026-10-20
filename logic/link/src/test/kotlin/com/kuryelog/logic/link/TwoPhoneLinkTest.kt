package com.kuryelog.logic.link

import com.kuryelog.logic.core.CourierPlatform
import com.kuryelog.logic.core.EnergyBlock
import com.kuryelog.logic.core.EnergyStep
import com.kuryelog.logic.core.LateEnergy
import com.kuryelog.logic.core.NavKind
import com.kuryelog.logic.core.NavOrigin
import com.kuryelog.logic.core.NavPort
import com.kuryelog.logic.core.NavTarget
import com.kuryelog.logic.core.OfferSummary
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.core.ShiftPort
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.link.LinkStatus.State.LINKED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.abs

/**
 * LINK.md 4.9: köprüler gerçek `LinkStack` (Noise kanalı, `Messenger` kuyrukları) üstünde, `FakeNet`'te. Provider'ın saati
 * Receiver'ınkinden 7,3 sn ileride: enerji penceresi ve vardiya saatleri Receiver'ın saatine çevrilir.
 */
class TwoPhoneLinkTest {
    private val net = FakeNet()
    private val a = net.node("Pixel", "11111111-1111-4111-8111-111111111111", "AA:00:00:00:00:01", messaging = true)
    private val b = net.node("Poco", "22222222-2222-4222-8222-222222222222", "BB:00:00:00:00:02", messaging = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private class Port(val n: FakeNet.Node) : LinkPort {
        override fun linked() = n.status.state == LINKED
        override fun publishLatest(type: String, payload: ByteArray, ttlMs: Long): UUID = n.publishLatestBytes(type, payload, ttlMs)
        override fun publishReliable(type: String, payload: ByteArray, ttlMs: Long): UUID = n.publishReliableBytes(type, payload, ttlMs)
    }

    private val windows = ArrayList<Pair<Long, Long>>()
    private val scooter = object : ScooterBridge {
        override fun orderAccepted(tripId: String, atWallMs: Long) {}
        override fun orderDone(tripId: String, atWallMs: Long) {}
        override suspend fun energy(fromWallMs: Long, toWallMs: Long): EnergyBlock {
            windows += fromWallMs to toWallMs
            return EnergyBlock(JSONObject().put("source", "surus_paneli").put("wh", 14.0))
        }
        override fun batteryPct(): Double? = null
        override val live: StateFlow<ScooterLive?> = MutableStateFlow(null)
        override val steps: SharedFlow<EnergyStep> = MutableSharedFlow()
    }
    private val opened = ArrayList<NavTarget>()
    private val nav = object : NavPort {
        override val route: StateFlow<RouteStatus?> = MutableStateFlow(null)
        override fun open(target: NavTarget) { opened += target }
    }
    private val shiftCalls = ArrayList<String>()
    private val shift = object : ShiftPort {
        override val state = MutableStateFlow(ShiftState(false))
        override fun start(reason: String) { shiftCalls += "start" }
        override fun stop(reason: String) { shiftCalls += "stop" }
    }

    private val provider = PhoneBridge(LinkRole.PROVIDER, Port(a), { a.wall }, scope, energyWaitMs = 0)
    private val receiver = PhoneBridge(LinkRole.RECEIVER, Port(b), { b.wall }, scope, PhoneBridge.Local(nav = nav, scooter = scooter, shift = shift))
    private val seen = HashMap<FakeNet.Node, Int>()
    private val acks = HashMap<FakeNet.Node, Int>()

    init {
        a.wallSkewMs = 7_300
        net.pair(a, b)
        net.hotspot(owner = b, client = a)
        a.enable()
        b.enable(listenOnly = true)
        run(2_000)
        assertEquals(LINKED, a.status.state)
        provider.linkChanged(true, a.wall)
        receiver.linkChanged(true, b.wall)
        run(1_000)
    }

    /** Ağı ilerletir ve gelenleri köprülere verir (Android'de `LinkRuntime.Listener`). */
    private fun run(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 100)
            net.run(step)
            left -= step
            pump(a, provider)
            pump(b, receiver)
        }
    }

    private fun pump(n: FakeNet.Node, bridge: PhoneBridge) {
        n.clocks.lastOrNull()?.let { bridge.clock(it.offsetMs) }
        val from = seen[n] ?: 0
        val msgs = n.messages.drop(from)
        seen[n] = n.messages.size
        for (m in msgs) bridge.incoming(m.type, m.payload, n.wall)
        val ackFrom = acks[n] ?: 0
        n.acked.drop(ackFrom).forEach { bridge.acked(it.type, it.id) }
        acks[n] = n.acked.size
        bridge.tick(n.wall)
    }

    @Test
    fun aShiftFlowsBetweenTwoPhones() = runBlocking {
        assertEquals(LinkRole.RECEIVER, provider.peerRole.value)
        assertEquals(LinkRole.PROVIDER, receiver.peerRole.value)
        val offset = b.clocks.last().offsetMs
        assertTrue("saat farkı $offset", abs(offset - 7_300) <= 50)

        // Vardiya: Receiver izler; saat Receiver'ınkine çevrilir.
        provider.providerShift(ShiftState(true, startedAtMs = a.wall, generatedAtMs = a.wall))
        val startedOnB = b.wall
        run(500)
        assertEquals(listOf("start"), shiftCalls)
        assertTrue(abs(receiver.remoteShift.value!!.startedAtMs!! - startedOnB) <= 600)

        // Teklif Receiver'ın sekmesine; adres gider (S5).
        provider.providerOffers(listOf(OfferSummary("wolt:9", CourierPlatform.WOLT, a.wall, a.wall, true, displayedFeeAzn = 5.2,
            dropoffAddress = "Nizami 5")), a.wall)
        run(500)
        assertEquals("Nizami 5", receiver.orders.offers.value.single().dropoffAddress)

        // "Navigasyon" Provider'da yakalandı: rota Receiver'da açılır, Provider'da açılmaz.
        val t = NavTarget("req-1", 40.41, 49.86, NavKind.RESTAURANT, NavOrigin.INTENT, CourierPlatform.WOLT.pkg, a.wall, "Kafe")
        assertTrue(provider.forwardNav(t, a.wall))
        run(10_000)
        assertEquals(listOf("req-1"), opened.map { it.requestId })

        // Batarya Receiver → Provider.
        receiver.receiverScooter(ScooterLive(b.wall, true, 400, ScooterLive.Values(internalPct = 71.0)), b.wall)
        run(500)
        assertEquals(71.0, provider.scooter.batteryPct()!!, 0.0)

        // Enerji: istek Provider'ın saatiyle, Receiver kendi saatiyle ölçer; yanıt (geç) Provider'a pencereyle döner.
        val lates = ArrayList<LateEnergy>()
        scope.launch { provider.scooter.late.collect { lates += it } }
        val to = a.wall
        val from = to - 600_000
        assertEquals(null, provider.scooter.energy(from, to))
        run(1_000)
        val (rf, rt) = windows.single()
        assertTrue(abs(rf - (from - 7_300)) <= 50 && abs(rt - (to - 7_300)) <= 50)
        assertEquals(LateEnergy(from, to, EnergyBlock(JSONObject().put("source", "surus_paneli").put("wh", 14.0))), lates.single())
        assertTrue(a.store.outbox().isEmpty() && b.store.outbox().isEmpty())
    }

    @Test
    fun aTargetWaitsInTheQueueWhileTheLinkIsDownAndOpensLocally() {
        // Bağlantı kopuk: Provider rotayı kendisi açar (çağıran `false` görür).
        a.leaveLan()
        b.leaveLan()
        run(20_000)
        provider.linkChanged(a.status.state == LINKED, a.wall)
        assertTrue(a.status.state != LINKED)
        val t = NavTarget("req-2", 40.41, 49.86, NavKind.CUSTOMER, NavOrigin.INTENT, null, a.wall, "Nizami 5")
        assertEquals(false, provider.forwardNav(t, a.wall))
    }
}
