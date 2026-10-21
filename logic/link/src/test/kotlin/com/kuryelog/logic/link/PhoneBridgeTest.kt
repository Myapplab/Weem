package com.kuryelog.logic.link

import com.kuryelog.logic.core.CourierPlatform
import com.kuryelog.logic.core.DaySummary
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
import com.kuryelog.logic.core.TripStages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** LINK.md 4.4–4.8: iki köprü bellekte bağlı (sahte bağlantı); zaman elle. */
class PhoneBridgeTest {
    private val t0 = 1_760_000_000_000L
    private var now = t0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bir yönün kuyruğu: [flush] karşıya verir, teslim zorunlular gönderene onaylanır. [auto] = hemen teslim. */
    private inner class Wire(var linked: Boolean = true, var auto: Boolean = false) : LinkPort {
        lateinit var to: PhoneBridge
        lateinit var back: () -> PhoneBridge
        val sent = ArrayList<Triple<LinkTopic, ByteArray, UUID>>()
        private val queue = ArrayList<Triple<LinkTopic, ByteArray, UUID>>()

        override fun linked() = linked
        override fun publishLatest(type: String, payload: ByteArray, ttlMs: Long) = put(type, payload)
        override fun publishReliable(type: String, payload: ByteArray, ttlMs: Long) = put(type, payload)

        private fun put(type: String, payload: ByteArray): UUID {
            val t = LinkTopic.of(type)!!
            val id = UUID.randomUUID()
            sent += Triple(t, payload, id)
            synchronized(queue) {
                if (!t.reliable) queue.removeAll { it.first == t }
                queue += Triple(t, payload, id)
            }
            if (auto && linked) flush()
            return id
        }

        fun flush() {
            if (!linked) return
            val q = synchronized(queue) { queue.toList().also { queue.clear() } }
            for ((t, b, id) in q) {
                to.incoming(t.type, b, now)
                if (t.reliable) back().acked(t.type, id)
            }
        }

        fun count(t: LinkTopic) = sent.count { it.first == t }
    }

    private class Nav : NavPort {
        val opened = ArrayList<NavTarget>()
        override val route: StateFlow<RouteStatus?> = MutableStateFlow(null)
        override fun open(target: NavTarget) { opened += target }
    }

    private class Shift(active: Boolean = false, startedAtMs: Long? = null, endedAtMs: Long? = null) : ShiftPort {
        val calls = ArrayList<String>()
        override val state = MutableStateFlow(ShiftState(active, startedAtMs, endedAtMs))
        override fun start(reason: String) { calls += "start:$reason" }
        override fun stop(reason: String) { calls += "stop:$reason" }
    }

    private class Scooter : ScooterBridge {
        val calls = ArrayList<String>()
        val windows = ArrayList<Pair<Long, Long>>()
        var block: EnergyBlock? = EnergyBlock(JSONObject().put("source", "surus_paneli").put("wh", 9.5))
        override fun orderAccepted(tripId: String, atWallMs: Long) { calls += "accepted:$tripId@$atWallMs" }
        override fun orderDone(tripId: String, atWallMs: Long) { calls += "done:$tripId@$atWallMs" }
        override suspend fun energy(fromWallMs: Long, toWallMs: Long): EnergyBlock? { windows += fromWallMs to toWallMs; return block }
        override fun batteryPct(): Double? = null
        override val live: StateFlow<ScooterLive?> = MutableStateFlow(null)
        override val steps: SharedFlow<EnergyStep> = MutableSharedFlow()
    }

    private val pNav = Nav()
    private val rNav = Nav()
    private val rShift = Shift()
    private val rScooter = Scooter()
    private val rOpened = ArrayList<NavTarget>()
    private val rTheft = ArrayList<TheftCommand>()
    private val pOut = Wire()
    private val rOut = Wire()
    private var provider = newProvider()
    private val receiver = PhoneBridge(LinkRole.RECEIVER, rOut, { now }, scope,
        PhoneBridge.Local(nav = rNav, scooter = rScooter, shift = rShift, navOpened = { rOpened += it }, theftCommand = { rTheft += it }),
        energyWaitMs = 2_000)

    private fun newProvider() = PhoneBridge(LinkRole.PROVIDER, pOut, { now }, scope, PhoneBridge.Local(nav = pNav), energyWaitMs = 2_000)

    init {
        wire()
        provider.linkChanged(true, now)
        receiver.linkChanged(true, now)
        flush()
    }

    private fun wire() {
        pOut.to = receiver; pOut.back = { provider }
        rOut.to = provider; rOut.back = { receiver }
    }

    private fun flush() { pOut.flush(); rOut.flush() }

    private fun offer(key: String, last: Long = now, fee: Double = 4.0) =
        OfferSummary(key, CourierPlatform.WOLT, t0, last, true, displayedFeeAzn = fee, dropoffAddress = "Nizami 5")

    private fun target(id: String = "r1", at: Long = now) = NavTarget(id, 40.41, 49.86, NavKind.CUSTOMER, NavOrigin.INTENT, "com.wolt.courierapp", at, "Nizami 5")

    private fun trip(id: String, active: Boolean, at: Long, ended: Long? = null) = TripStages.Trip(id, CourierPlatform.WOLT.pkg, active,
        if (active) TripStages.Stage.TO_PICKUP else null, if (active) null else TripStages.Outcome.DELIVERED, ended,
        listOf(TripStages.Event(TripStages.Stage.TO_PICKUP, at)))

    @Test
    fun rolesAreExchangedAndConflictIsSeen() {
        assertEquals(LinkRole.RECEIVER, provider.peerRole.value)
        assertEquals(LinkRole.PROVIDER, receiver.peerRole.value)
        assertFalse(provider.conflict())
        // İki Provider: uyarı; hedef öteki telefona gitmez.
        val other = Wire()
        val p2 = PhoneBridge(LinkRole.PROVIDER, other, { now }, scope)
        other.to = provider; other.back = { p2 }
        p2.linkChanged(true, now)
        other.flush()
        assertTrue(provider.conflict())
        assertFalse(provider.forwardNav(target()))
        provider.linkChanged(false, now)
        assertNull(provider.peerRole.value)
    }

    @Test
    fun offersFlowWithRateLimitAndRefresh() {
        provider.providerOffers(listOf(offer("a")), now)
        flush()
        assertEquals(listOf("a"), receiver.orders.offers.value.map { it.offerKey })
        // Yalnız "son görülme" değişimi yeniden yollanmaz; 200 ms içindeki değişim bekler, tick'te gider.
        now += 100
        provider.providerOffers(listOf(offer("a", last = now)), now)
        assertEquals(1, pOut.count(LinkTopic.OFFERS))
        provider.providerOffers(listOf(offer("a", last = now), offer("b")), now)
        assertEquals(1, pOut.count(LinkTopic.OFFERS))
        now += 150
        provider.tick(now)
        assertEquals(2, pOut.count(LinkTopic.OFFERS))
        flush()
        assertEquals(listOf("a", "b"), receiver.orders.offers.value.map { it.offerKey })
        // Teklif varken 5 sn'de bir tazelenir.
        now += 5_000
        provider.providerOffers(listOf(offer("a", last = now), offer("b", last = now)), now)
        assertEquals(3, pOut.count(LinkTopic.OFFERS))
        // Teklif kaybolunca boş liste gider.
        now += 300
        provider.providerOffers(emptyList(), now)
        flush()
        assertTrue(receiver.orders.offers.value.isEmpty())
        assertEquals(4, pOut.count(LinkTopic.OFFERS))
        // Boşken tazeleme yok.
        now += 10_000
        provider.providerOffers(emptyList(), now)
        assertEquals(4, pOut.count(LinkTopic.OFFERS))
    }

    @Test
    fun anUnchangedOfferIsRefreshedFromTheTickSoTheReceiverKeepsIt() {
        // Teklif ekranda duruyor, kaynak akış aynı listeyi bir daha yaymıyor: yenileme saniyelik tikten gelir.
        provider.providerOffers(listOf(offer("a")), now)
        flush()
        repeat(20) {
            now += 1_000
            provider.tick(now)
            flush()
            receiver.tick(now)
        }
        assertEquals(1, receiver.orders.offers.value.size)
        assertEquals(5, pOut.count(LinkTopic.OFFERS))
    }

    @Test
    fun receiverDropsOffersOnSilenceAndOnLinkLoss() {
        provider.providerOffers(listOf(offer("a")), now)
        flush()
        now += PhoneBridge.OFFERS_STALE_MS + 1
        receiver.tick(now)
        assertTrue(receiver.orders.offers.value.isEmpty())
        now += 1_000
        provider.providerOffers(listOf(offer("a", fee = 5.0)), now)
        flush()
        assertEquals(1, receiver.orders.offers.value.size)
        receiver.linkChanged(false, now)
        assertTrue(receiver.orders.offers.value.isEmpty())
    }

    @Test
    fun stagesAreReliableAndDriveTheTripMeter() {
        // İlk iletideki açık sipariş sürüyor sayılır (Receiver yeniden başladıysa sayaç sıfırlanmasın).
        provider.providerStages(TripStages(listOf(trip("t1", true, t0))))
        flush()
        assertTrue(rScooter.calls.isEmpty())
        assertTrue(receiver.orders.busy())
        provider.providerStages(TripStages(listOf(trip("t1", true, t0))))
        assertEquals(1, pOut.count(LinkTopic.STAGES))
        provider.providerStages(TripStages(listOf(trip("t1", false, t0, ended = t0 + 900), trip("t2", true, t0 + 1_000))))
        flush()
        assertEquals(listOf("accepted:t2@${t0 + 1_000}", "done:t1@${t0 + 900}"), rScooter.calls)
        assertEquals(listOf("t1", "t2"), receiver.orders.stages.value.trips.map { it.tripId })
    }

    @Test
    fun daySummaryAtMostOncePerMinute() {
        fun d(n: Int, day: String = "2026-10-20") = DaySummary(day, mapOf(CourierPlatform.WOLT to DaySummary.PlatformDay(n, n * 5.0)))
        provider.providerDay(d(1), now)
        provider.providerDay(d(1), now)
        now += 10_000
        provider.providerDay(d(2), now)
        assertEquals(1, pOut.count(LinkTopic.DAY))
        now += 50_000
        provider.tick(now)
        assertEquals(2, pOut.count(LinkTopic.DAY))
        // Gün değişince hemen.
        now += 1_000
        provider.providerDay(d(0, "2026-10-21"), now)
        flush()
        assertEquals(3, pOut.count(LinkTopic.DAY))
        assertEquals("2026-10-21", receiver.orders.day.value!!.day)
    }

    @Test
    fun receiverFollowsTheProviderShiftOnce() {
        provider.providerShift(ShiftState(true, startedAtMs = now, generatedAtMs = now))
        flush()
        assertEquals(listOf("start:link"), rShift.calls)
        assertEquals(now, receiver.remoteShift.value!!.startedAtMs)
        // Aynı vardiya yeniden gelirse (yeniden bağlanma) bir şey olmaz.
        provider.providerShift(ShiftState(true, startedAtMs = now, generatedAtMs = now + 5))
        flush()
        assertEquals(1, rShift.calls.size)
        rShift.state.value = ShiftState(true, startedAtMs = now)
        now += 60_000
        provider.providerShift(ShiftState(false, startedAtMs = t0, endedAtMs = now, endReason = "manual", generatedAtMs = now))
        flush()
        assertEquals(listOf("start:link", "stop:link"), rShift.calls)
    }

    @Test
    fun laterLocalDecisionIsNotOverridden() {
        // Receiver'da vardiya Provider'ın bitişinden sonra elle açıldı: eski bitiş onu durdurmaz.
        rShift.state.value = ShiftState(true, startedAtMs = now)
        provider.providerShift(ShiftState(false, startedAtMs = now - 7_200_000, endedAtMs = now - 3_600_000))
        flush()
        assertTrue(rShift.calls.isEmpty())
        // Receiver'da vardiya Provider'ın başlangıcından sonra elle bitirildi: yeniden açılmaz.
        rShift.state.value = ShiftState(false, startedAtMs = now - 600_000, endedAtMs = now)
        provider.providerShift(ShiftState(true, startedAtMs = now - 300_000))
        flush()
        assertTrue(rShift.calls.isEmpty())
    }

    @Test
    fun navTargetOpensOnTheReceiverOnce() {
        assertTrue(provider.forwardNav(target(), now))
        flush()
        assertEquals(listOf("r1"), rNav.opened.map { it.requestId })
        assertEquals(1, rOpened.size)
        // Onaylandı: Provider yerelde açmaz.
        now += PhoneBridge.NAV_FALLBACK_MS + 1
        provider.tick(now)
        assertTrue(pNav.opened.isEmpty())
        // Aynı istek ikinci kez (yeniden gönderim) açılmaz.
        receiver.incoming(LinkTopic.NAV_TARGET.type, LinkPayloads.navTarget(target(at = now)), now)
        assertEquals(1, rNav.opened.size)
    }

    @Test
    fun navFallsBackToTheProviderWhenTheReceiverIsUnreachable() {
        // Bağlı değil: port yerelde açar.
        pOut.linked = false
        provider.nav.open(target("r2"))
        assertEquals(listOf("r2"), pNav.opened.map { it.requestId })
        // Bağlı ama onay gelmedi: 8 sn sonra yerelde; geç ulaşan hedef Receiver'da açılmaz.
        pOut.linked = true
        provider.nav.open(target("r3"))
        assertEquals(1, pNav.opened.size)
        now += PhoneBridge.NAV_FALLBACK_MS
        provider.tick(now)
        assertEquals(listOf("r2", "r3"), pNav.opened.map { it.requestId })
        now += LinkTopic.NAV_TARGET.ttlMs
        flush()
        assertTrue(rNav.opened.isEmpty())
    }

    @Test
    fun navTargetArrivingAfterTheProviderFallbackIsNotOpenedOnTheReceiver() {
        // Wi-Fi kısa koptu: Provider 8 sn sonra yerelde açtı; hedef TTL (2 dk) içinde ama 20 sn sonra ulaştı. İki telefonda rota olmaz.
        val sent = now
        provider.nav.open(target("r4", at = sent))
        now += PhoneBridge.NAV_FALLBACK_MS
        provider.tick(now)
        assertEquals(listOf("r4"), pNav.opened.map { it.requestId })
        now = sent + 20_000
        receiver.incoming(LinkTopic.NAV_TARGET.type, LinkPayloads.navTarget(target("r4", at = sent)), now, sentAtMs = sent)
        assertTrue(rNav.opened.isEmpty())
        // Zamanında gelen (zarf saatiyle 5 sn) açılır.
        receiver.incoming(LinkTopic.NAV_TARGET.type, LinkPayloads.navTarget(target("r5", at = now - 9_000)), now, sentAtMs = now - 5_000)
        assertEquals(listOf("r5"), rNav.opened.map { it.requestId })
    }

    @Test
    fun energyIsAskedFromTheReceiver() = runBlocking {
        pOut.auto = true
        rOut.auto = true
        val block = provider.scooter.energy(t0, t0 + 600_000)
        assertEquals(9.5, block!!.json.getDouble("wh"), 0.0)
        assertEquals(listOf(t0 to t0 + 600_000), rScooter.windows)
        // Kaynak yoksa ölçülmedi bloğu (uydurulmaz).
        rScooter.block = null
        val none = provider.scooter.energy(t0 + 1, t0 + 2)
        assertFalse(none!!.measured)
        assertNull(provider.scooter.energy(t0, t0))
    }

    @Test
    fun lateEnergyArrivesAsACorrection() = runBlocking {
        val late = async { withTimeout(5_000) { provider.scooter.late.first() } }
        kotlinx.coroutines.delay(50)
        // Bağlı değil: hemen döner (satır "ölçülmedi"), istek kuyrukta kalır.
        pOut.linked = false
        assertNull(provider.scooter.energy(t0, t0 + 60_000))
        pOut.linked = true
        rOut.auto = true
        pOut.flush()
        assertEquals(LateEnergy(t0, t0 + 60_000, rScooter.block!!), late.await())
    }

    @Test
    fun lateEnergyIsMatchedEvenAfterAProviderRestart() = runBlocking {
        pOut.linked = false
        assertNull(provider.scooter.energy(t0, t0 + 60_000))
        provider = newProvider()
        wire()
        val late = async { withTimeout(5_000) { provider.scooter.late.first() } }
        kotlinx.coroutines.delay(50)
        pOut.linked = true
        rOut.auto = true
        pOut.flush()
        assertEquals(t0 to t0 + 60_000, late.await().let { it.fromWallMs to it.toWallMs })
        assertNull(PhoneBridge.windowOf("başka"))
        assertNull(PhoneBridge.windowOf("e1:5:4:x"))
    }

    @Test
    fun repeatedRequestGetsTheSameAnswer() = runBlocking {
        val req = com.kuryelog.logic.core.EnergyRequest(PhoneBridge.energyId(t0, t0 + 10), "w", com.kuryelog.logic.core.EnergyRequest.Kind.IDLE, t0, t0 + 10)
        receiver.incoming(LinkTopic.ENERGY_REQUEST.type, LinkPayloads.energyRequest(req), now)
        withTimeout(5_000) { while (rOut.count(LinkTopic.ENERGY_RESULT) < 1) Thread.sleep(5) }
        receiver.incoming(LinkTopic.ENERGY_REQUEST.type, LinkPayloads.energyRequest(req), now)
        assertEquals(2, rOut.count(LinkTopic.ENERGY_RESULT))
        assertEquals(1, rScooter.windows.size)
    }

    @Test
    fun batterySummaryBecomesTheProvidersScooterLive() {
        val live = ScooterLive(now, true, 500, ScooterLive.Values(internalPct = 64.0, externalPct = 80.0, speedKmh = 21.0, rangeKm = 30.0, tempC = 17.0))
        receiver.receiverScooter(live, now)
        flush()
        val got = provider.scooter.live.value!!
        assertTrue(got.fresh)
        assertEquals(64.0, provider.scooter.batteryPct()!!, 0.0)
        // Hız (saniyelik veri) gitmez.
        assertNull(got.scooter.speedKmh)
        assertEquals(30.0, got.scooter.rangeKm!!, 0.0)
        // %1'den az değişim ve 60 sn'den kısa süre: gönderilmez.
        now += 10_000
        receiver.receiverScooter(live.copy(scooter = live.scooter.copy(internalPct = 63.5)), now)
        assertEquals(1, rOut.count(LinkTopic.BATTERY))
        receiver.receiverScooter(live.copy(scooter = live.scooter.copy(internalPct = 63.0)), now)
        assertEquals(2, rOut.count(LinkTopic.BATTERY))
        // Yeni özet gelmezse 75 sn sonra canlı sayılmaz.
        now += PhoneBridge.BATTERY_CURRENT_MS + 1
        provider.tick(now)
        assertFalse(provider.scooter.live.value!!.linkActive)
        assertNull(provider.scooter.batteryPct())
        // Scooter bağlantısı kopunca hemen bildirilir.
        receiver.receiverScooter(live.copy(linkActive = false, ageMs = null), now)
        flush()
        assertEquals(3, rOut.count(LinkTopic.BATTERY))
        assertFalse(provider.scooter.live.value!!.linkActive)
        // Hiç scooter yokken de "bağlantı yok" gider; 2 telefon bağlantısı kopunca Provider'da canlı değil.
        receiver.receiverScooter(null, now + 120_000)
        assertEquals(4, rOut.count(LinkTopic.BATTERY))
        provider.linkChanged(false, now)
        assertFalse(provider.scooter.live.value!!.linkActive)
    }

    @Test
    fun routeStatusAtMostOncePerSecond() {
        val r = RouteStatus(RouteStatus.Target(40.4, 49.8, NavKind.CUSTOMER, NavOrigin.INTENT, now, null), RouteStatus.Route(900.0, 3, now), now)
        receiver.receiverRoute(r, now)
        flush()
        assertEquals(900.0, provider.nav.route.value!!.route!!.remainingM, 0.0)
        now += 300
        receiver.receiverRoute(r.copy(route = RouteStatus.Route(850.0, 3, now), updatedAtMs = now), now)
        receiver.receiverRoute(r.copy(route = RouteStatus.Route(800.0, 3, now), updatedAtMs = now), now)
        assertEquals(1, rOut.count(LinkTopic.ROUTE))
        now += 700
        receiver.tick(now)
        flush()
        assertEquals(2, rOut.count(LinkTopic.ROUTE))
        assertEquals(800.0, provider.nav.route.value!!.route!!.remainingM, 0.0)
        // Yalnız `updatedAtMs` değişimi yollanmaz (ölçüm anı değişirse gider: Provider 30 sn tazelik ister); rota bitince "rota yok".
        val last = provider.nav.route.value!!
        now += 2_000
        receiver.receiverRoute(RouteStatus(r.target, RouteStatus.Route(800.0, 3, last.route!!.measuredAtMs), now), now)
        assertEquals(2, rOut.count(LinkTopic.ROUTE))
        receiver.receiverRoute(null, now)
        flush()
        assertNull(provider.nav.route.value)
    }

    @Test
    fun wrongDirectionAndOffRoleAreIgnored() {
        // Provider kendi türünü (öteki Provider'dan) almaz; Receiver Receiver türünü almaz.
        provider.incoming(LinkTopic.OFFERS.type, LinkPayloads.offers(listOf(offer("x"))), now)
        assertTrue(provider.orders.offers.value.isEmpty())
        receiver.incoming(LinkTopic.BATTERY.type, LinkPayloads.battery(com.kuryelog.logic.core.BatterySummary(now, true)), now)
        assertNull(receiver.scooter.live.value)
        // Rolü olmayan girdiler hiçbir şey yollamaz.
        val before = rOut.sent.size
        receiver.providerOffers(listOf(offer("y")), now)
        receiver.providerShift(ShiftState(true))
        assertFalse(receiver.forwardNav(target()))
        provider.receiverScooter(null, now)
        provider.receiverRoute(null, now)
        assertEquals(before, rOut.sent.size)
        assertEquals(1, pOut.sent.size) // yalnız açılıştaki rol
        val off = PhoneBridge(LinkRole.OFF, Wire(), { now }, scope)
        off.incoming(LinkTopic.SHIFT.type, LinkPayloads.shift(ShiftState(true)), now)
        assertNull(off.remoteShift.value)
        // Bilinmeyen tür ve bozuk yük atılır.
        receiver.incoming("YeniTür", byteArrayOf(1), now)
        receiver.incoming(LinkTopic.SHIFT.type, byteArrayOf(1, 2), now)
        assertNull(receiver.remoteShift.value)
    }

    @Test
    fun clockOffsetMovesIncomingTimes() {
        receiver.clock(5_000)
        provider.providerShift(ShiftState(true, startedAtMs = t0, generatedAtMs = t0))
        flush()
        assertEquals(t0 - 5_000, receiver.remoteShift.value!!.startedAtMs)
    }

    // ------------------------------------------------------------------ A8.4 hırsızlık koruması (LINK.md 5.2)

    @Test
    fun theftStateGoesToProviderOnChangeAndEveryTenSecondsWhileAlarming() {
        val armed = TheftGuard.Status(TheftGuard.State.ARMED, TheftGuard.ArmedBy.STAGE, t0)
        receiver.receiverTheft(armed, now)
        receiver.receiverTheft(armed, now + 1_000)
        receiver.receiverTheft(armed.copy(fix = TheftGuard.Fix(40.4, 49.8, 8.0, now)), now + 2_000)
        assertEquals(1, rOut.count(LinkTopic.THEFT_STATE))
        val fix = TheftGuard.Fix(40.41, 49.87, 6.0, now + 3_000)
        val alarm = TheftGuard.Status(TheftGuard.State.ALARM, TheftGuard.ArmedBy.STAGE, now + 3_000, TheftGuard.Trigger.SPEED, fix)
        receiver.receiverTheft(alarm, now + 3_000)
        receiver.receiverTheft(alarm, now + 12_000)
        receiver.receiverTheft(alarm, now + 13_000)
        assertEquals(3, rOut.count(LinkTopic.THEFT_STATE))
        flush()
        val got = provider.theft.value!!
        assertEquals(TheftGuard.State.ALARM, got.status.state)
        assertEquals(TheftGuard.Trigger.SPEED, got.status.trigger)
        assertEquals(40.41, got.status.fix!!.lat, 1e-9)
        // Bağlantı kopsa da son durum (son bilinen konum) kalır.
        provider.linkChanged(false, now)
        assertEquals(TheftGuard.State.ALARM, provider.theft.value!!.status.state)
    }

    @Test
    fun theftCommandReachesReceiverOnceAndOnlyFromProvider() {
        assertTrue(provider.sendTheft(TheftCommand.Action.ARM, now))
        assertFalse(receiver.sendTheft(TheftCommand.Action.DISARM, now))
        flush()
        assertEquals(listOf(TheftCommand.Action.ARM), rTheft.map { it.action })
        val again = pOut.sent.last { it.first == LinkTopic.THEFT_COMMAND }
        receiver.incoming(LinkTopic.THEFT_COMMAND.type, again.second, now)
        assertEquals(1, rTheft.size)
        // Yanlış yön: Receiver'ın yolladığı komut Provider'da da Receiver'da da işlenmez.
        receiver.incoming(LinkTopic.THEFT_STATE.type, LinkPayloads.theftState(TheftState(TheftGuard.Status(), now)), now)
        provider.incoming(LinkTopic.THEFT_COMMAND.type, again.second, now)
        assertEquals(1, rTheft.size)
        provider.receiverTheft(TheftGuard.Status(TheftGuard.State.ALARM), now)
        assertEquals(0, pOut.count(LinkTopic.THEFT_STATE))
    }

    @Test
    fun theftPayloadsRoundTripWithClockAndRejectBadInput() {
        val fix = TheftGuard.Fix(40.409, 49.867, 9.0, t0 + 2_000)
        val s = TheftState(TheftGuard.Status(TheftGuard.State.WARNING, TheftGuard.ArmedBy.AWAY, t0 + 1_000, TheftGuard.Trigger.MOTION, fix), t0 + 3_000)
        val back = LinkPayloads.Decoder(1_000).theftState(LinkPayloads.theftState(s))!!
        assertEquals(TheftGuard.State.WARNING, back.status.state)
        assertEquals(TheftGuard.ArmedBy.AWAY, back.status.armedBy)
        assertEquals(t0, back.status.sinceMs)
        assertEquals(t0 + 1_000, back.status.fix!!.atMs)
        assertEquals(t0 + 2_000, back.atMs)
        val off = LinkPayloads.Decoder(0).theftState(LinkPayloads.theftState(TheftState(TheftGuard.Status(), t0)))!!
        assertNull(off.status.armedBy); assertNull(off.status.fix)
        val c = TheftCommand("c1", TheftCommand.Action.DISARM, t0 + 5_000)
        assertEquals(c.copy(atMs = t0), LinkPayloads.Decoder(5_000).theftCommand(LinkPayloads.theftCommand(c)))
        val d = LinkPayloads.Decoder(0)
        assertNull(d.theftState("{}".toByteArray()))
        assertNull(d.theftState("""{"schemaVersion":1,"state":"stolen","sinceMs":1,"atMs":1}""".toByteArray()))
        assertNull(d.theftCommand("""{"schemaVersion":1,"commandId":"x","action":"open","atMs":1}""".toByteArray()))
        assertNull(d.theftCommand("""{"schemaVersion":2,"commandId":"x","action":"arm","atMs":1}""".toByteArray()))
        assertNull(d.theftCommand(byteArrayOf(-1, -2)))
    }
}
