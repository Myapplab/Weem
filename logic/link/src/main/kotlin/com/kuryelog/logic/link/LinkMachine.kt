package com.kuryelog.logic.link

/** Bağlantı durumu (LINK.md 1.7); A8.3 gösterir. */
data class LinkStatus(
    val state: State,
    val transport: Transport? = null,
    val peer: PeerId? = null,
    val sinceMs: Long = 0,
    val rttMs: Long? = null,
    /** Yerel ağ (hotspot ya da Wi-Fi) var mı. */
    val lan: Boolean = false,
    /** Bluetooth açık ve izinli mi. */
    val btOn: Boolean = false,
    /** Eşleşmiş telefon (ya da öğrenilen eş) var mı; yoksa Bluetooth yedeği kurulamaz. */
    val btPhones: Boolean = false,
) {
    enum class State { OFF, SEARCHING, LINKED }

    companion object {
        val OFF = LinkStatus(State.OFF)
    }
}

/** [LinkMachine.send] sonucu. `SENT` = sokete verildi; teslim garantisi yok (A8.2 `ack`). */
enum class SendResult { SENT, NO_LINK, TOO_BIG_FOR_BT, TOO_BIG }

/** Android katmanından makineye. Kanal numarası: giden kanalı makine verir (> 0), gelen kanalı katman verir (< 0). */
sealed interface LinkEvent {
    /** 2 telefon modu açık. [expectedPeer] verilmezse ilk el sıkışan eş kabul edilir (kimlik doğrulama A8.2'de). */
    class Enable(
        val self: PeerId,
        val expectedPeer: PeerId? = null,
        val btPeerAddress: String? = null,
        val listenOnly: Boolean = false,
    ) : LinkEvent

    object Disable : LinkEvent

    /** Yerel ağ: [present] = uygun IPv4 arayüz var (`LanTargets`); [gateway] = Wi-Fi istemcisiysen ağ geçidi. */
    data class Lan(val present: Boolean, val gateway: String?) : LinkEvent

    /** Bluetooth: [on] = açık ve izinli; [bonded] = Android'de eşleşmiş cihazlar. */
    data class Bt(val on: Boolean, val bonded: List<BondedDevice>) : LinkEvent

    /** Giden bağlantı kuruldu. */
    data class Opened(val channel: Int) : LinkEvent

    /** Gelen bağlantı kabul edildi; [remote] = IP ya da Bluetooth adresi. */
    data class Accepted(val channel: Int, val transport: Transport, val remote: String) : LinkEvent

    /** Giden bağlantı kurulamadı. */
    data class Failed(val channel: Int, val reason: String) : LinkEvent

    /** Kanal kapandı (okuma bitti ya da hata). */
    data class Closed(val channel: Int, val reason: String) : LinkEvent

    class Bytes(val channel: Int, val data: ByteArray, val offset: Int = 0, val length: Int = data.size) : LinkEvent

    class BeaconIn(val from: String, val data: ByteArray, val length: Int = data.size) : LinkEvent

    /** [LinkCommand.Wake] zamanı geldi. */
    object Tick : LinkEvent
}

/** Makineden Android katmanına. Sırayla uygulanır: aynı kanala `Write` sonra `Close` gelirse önce yazılır, sonra kapanır. */
sealed interface LinkCommand {
    data class Listen(val transport: Transport, val on: Boolean) : LinkCommand

    /** Bluetooth'ta [host] = adres, [port] = 0. */
    data class Connect(val channel: Int, val transport: Transport, val host: String, val port: Int) : LinkCommand

    class Write(val channel: Int, val bytes: ByteArray) : LinkCommand {
        override fun toString() = "Write($channel, ${bytes.size} B)"
    }

    data class Close(val channel: Int) : LinkCommand

    class SendBeacon(val bytes: ByteArray, val port: Int) : LinkCommand {
        override fun toString() = "SendBeacon($port)"
    }

    /** Gelen `DATA` yükü (A8.2 zarfı). */
    class Deliver(val payload: ByteArray, val transport: Transport) : LinkCommand {
        override fun toString() = "Deliver(${payload.size} B, $transport)"
    }

    data class Status(val status: LinkStatus) : LinkCommand

    /** Yeni eş el sıkıştı (beklenen eş verilmemişken); saklamak üst katmanın işi. */
    data class PeerSeen(val peer: PeerId) : LinkCommand

    /** Bluetooth eşinin adresi öğrenildi; saklanırsa sonraki seferde yalnız o aranır. */
    data class BtPeerLearned(val address: String) : LinkCommand

    /** Bir sonraki `Tick` zamanı (monoton ms); `null` = zamanlayıcı yok. */
    data class Wake(val atMs: Long?) : LinkCommand
}

/**
 * 2 telefon taşıması (LINK.md 1): keşif, el sıkışma, tek kanal, canlılık, yeniden bağlanma, Wi-Fi ↔ Bluetooth geçişi.
 *
 * Saf ve tek iş parçacıklı: [handle]/[send] durumu değiştirir, komutları biriktirir; çağıran [drain] ile alır ve uygular.
 * Zaman her çağrıda verilir (monoton ms). Soket, Bluetooth ve zamanlayıcı Android katmanında (`:link` `LinkRuntime`).
 */
class LinkMachine(private val cfg: LinkConfig = LinkConfig(), random: () -> Double = Math::random) {
    private enum class St { CONNECTING, HANDSHAKE, OPEN }

    private class Ch(val id: Int, val transport: Transport, val outbound: Boolean, val remote: String, val key: String, maxPayload: Int) {
        var state = St.CONNECTING
        var peer: PeerId? = null
        var startedMs = 0L
        var openedMs = 0L
        var lastRxMs = 0L
        var lastTxMs = 0L
        var rttMs: Long? = null
        var probeDeadlineMs: Long? = null
        var drainAtMs: Long? = null
        val decoder = FrameDecoder(maxPayload)
    }

    private class Seen(val peer: PeerId, val port: Int, val atMs: Long)

    private val backoff = Backoff(cfg.backoff, random)
    private val out = ArrayList<LinkCommand>()
    private val channels = LinkedHashMap<Int, Ch>()
    private var nextId = 1

    private var enabled = false
    private var self: PeerId? = null
    private var expected: PeerId? = null
    private var current: PeerId? = null
    private var listenOnly = false
    private var btLearned: String? = null
    private var beaconBytes = ByteArray(0)

    private var lan = false
    private var gateway: String? = null
    private var btOn = false
    private var bonded: List<BondedDevice> = emptyList()
    private var btTargets: List<String> = emptyList()
    private var btNext = 0

    private val seen = HashMap<String, Seen>()
    private var lastBeaconMs: Long? = null
    private var noWifiSinceMs = 0L
    private var tcpWasOpen = false
    private var searchSinceMs = 0L
    private var linkedSinceMs: Long? = null

    private val listening = mutableMapOf(Transport.WIFI to false, Transport.BT to false)
    private var lastStatus: LinkStatus? = null
    private var lastWake: Long? = null
    private var wakeSent = false

    /** Biriken komutlar (sırayla uygulanır). */
    fun drain(): List<LinkCommand> {
        val r = ArrayList(out)
        out.clear()
        return r
    }

    val status: LinkStatus get() = computeStatus()

    fun handle(e: LinkEvent, nowMs: Long) {
        when (e) {
            is LinkEvent.Enable -> enable(e, nowMs)
            LinkEvent.Disable -> disable()
            is LinkEvent.Lan -> onLan(e, nowMs)
            is LinkEvent.Bt -> onBt(e)
            is LinkEvent.Opened -> onOpened(e.channel, nowMs)
            is LinkEvent.Accepted -> onAccepted(e, nowMs)
            is LinkEvent.Failed -> channels.remove(e.channel)?.let { settle(it, End.REJECT, nowMs) }
            is LinkEvent.Closed -> onClosed(e.channel, nowMs)
            is LinkEvent.Bytes -> onBytes(e, nowMs)
            is LinkEvent.BeaconIn -> onBeacon(e, nowMs)
            LinkEvent.Tick -> Unit
        }
        step(nowMs)
    }

    /** Opak yükü etkin kanaldan gönderir (LINK.md 1.3: Bluetooth'ta ≤ 4 KiB). */
    fun send(payload: ByteArray, nowMs: Long): SendResult {
        if (!enabled) return SendResult.NO_LINK
        val a = active() ?: return SendResult.NO_LINK
        if (payload.size > cfg.wifiMaxPayload) return SendResult.TOO_BIG
        if (a.transport == Transport.BT && payload.size > cfg.btMaxPayload) return SendResult.TOO_BIG_FOR_BT
        write(a, Frames.encode(FrameType.DATA, payload), nowMs)
        return SendResult.SENT
    }

    // --- olaylar ---

    private fun enable(e: LinkEvent.Enable, now: Long) {
        if (enabled && self != e.self) disable()
        val first = !enabled
        enabled = true
        self = e.self
        listenOnly = e.listenOnly
        if (e.btPeerAddress?.uppercase() != btLearned) btLearned = e.btPeerAddress?.uppercase()
        btTargets = BtTargets.choose(bonded, btLearned)
        beaconBytes = Beacon.encode(e.self, cfg.tcpPort)
        if (expected != e.expectedPeer) {
            expected = e.expectedPeer
            if (expected != null && current != expected) current = null
            channels.values.filter { it.peer != null && expected != null && it.peer != expected }
                .forEach { close(it, ByeReason.WRONG_PEER, now) }
        }
        if (first) {
            backoff.resetAll()
            noWifiSinceMs = now
            searchSinceMs = now
            tcpWasOpen = false
            linkedSinceMs = null
            lastBeaconMs = null
        }
    }

    private fun disable() {
        for (ch in channels.values.toList()) {
            if (ch.state != St.CONNECTING) out += LinkCommand.Write(ch.id, Frames.bye(ByeReason.SHUTDOWN))
            out += LinkCommand.Close(ch.id)
        }
        channels.clear()
        seen.clear()
        enabled = false
        current = null
        linkedSinceMs = null
        lastBeaconMs = null
    }

    private fun onLan(e: LinkEvent.Lan, now: Long) {
        if (e.present == lan && e.gateway == gateway) return
        lan = e.present
        gateway = if (e.present) e.gateway else null
        backoff.resetAll()
        if (!lan) seen.clear()
        channels.values.filter { it.transport == Transport.WIFI && it.state == St.OPEN }.forEach { probe(it, now) }
    }

    private fun onBt(e: LinkEvent.Bt) {
        val changed = e.on != btOn || e.bonded != bonded
        btOn = e.on
        bonded = e.bonded
        btTargets = BtTargets.choose(bonded, btLearned)
        if (!changed) return
        backoff.resetAll()
        if (!btOn) {
            channels.values.filter { it.transport == Transport.BT }.toList().forEach {
                out += LinkCommand.Close(it.id)
                channels.remove(it.id)
            }
        }
    }

    private fun onOpened(id: Int, now: Long) {
        val ch = channels[id]
        if (ch == null || !ch.outbound || ch.state != St.CONNECTING) {
            // Makine kanalı bırakmıştı (zaman aşımı, kapanış): katmandaki soket kapanmalı.
            if (ch == null) out += LinkCommand.Close(id)
            return
        }
        ch.state = St.HANDSHAKE
        ch.startedMs = now
        ch.lastRxMs = now
        write(ch, Frames.hello(self!!), now)
    }

    private fun onAccepted(e: LinkEvent.Accepted, now: Long) {
        val pending = channels.values.count { !it.outbound && it.state == St.HANDSHAKE }
        if (!enabled || channels.containsKey(e.channel) || pending >= MAX_PENDING_INBOUND) {
            out += LinkCommand.Close(e.channel)
            return
        }
        val ch = Ch(e.channel, e.transport, outbound = false, remote = e.remote, key = "in:${e.transport}:${e.remote}", cfg.maxPayload(e.transport))
        ch.state = St.HANDSHAKE
        ch.startedMs = now
        ch.lastRxMs = now
        channels[ch.id] = ch
        write(ch, Frames.hello(self!!), now)
    }

    private fun onClosed(id: Int, now: Long) {
        val ch = channels.remove(id) ?: return
        settle(ch, if (ch.state == St.OPEN) End.LOST else End.REJECT, now)
    }

    private fun onBytes(e: LinkEvent.Bytes, now: Long) {
        val ch = channels[e.channel] ?: return
        if (ch.state == St.CONNECTING) return
        val frames = ch.decoder.feed(e.data, e.offset, e.length)
        if (frames.isNotEmpty() || e.length > 0) {
            ch.lastRxMs = now
            ch.probeDeadlineMs = null
        }
        for (f in frames) {
            if (channels[ch.id] !== ch) return
            onFrame(ch, f, now)
        }
        if (ch.decoder.error != null && channels[ch.id] === ch) close(ch, ByeReason.PROTOCOL, now)
    }

    private fun onFrame(ch: Ch, f: Frame, now: Long) {
        if (f.type == FrameType.BYE) {
            val reason = Frames.byeReason(f.payload)
            out += LinkCommand.Close(ch.id)
            channels.remove(ch.id)
            settle(ch, endOf(reason, ch), now)
            return
        }
        if (ch.state == St.HANDSHAKE) {
            if (f.type != FrameType.HELLO) return close(ch, ByeReason.PROTOCOL, now)
            return onHello(ch, f, now)
        }
        when (f.type) {
            FrameType.HELLO -> close(ch, ByeReason.PROTOCOL, now)
            FrameType.DATA -> out += LinkCommand.Deliver(f.payload, ch.transport)
            FrameType.PING -> write(ch, Frames.pong(f.payload), now)
            FrameType.PONG -> Frames.stamp(f.payload)?.let { if (it in 0..now) ch.rttMs = now - it }
            FrameType.BYE -> Unit
        }
    }

    private fun onHello(ch: Ch, f: Frame, now: Long) {
        val h = Frames.parseHello(f.payload) ?: return close(ch, ByeReason.PROTOCOL, now)
        if (h.version != Frames.LINK_VERSION) return close(ch, ByeReason.VERSION, now)
        val exp = expected
        val cur = current
        val wrong = h.peer == self ||
            (exp != null && h.peer != exp) ||
            (exp == null && cur != null && h.peer != cur && channels.values.any { it.state == St.OPEN })
        if (wrong) return close(ch, ByeReason.WRONG_PEER, now)
        ch.peer = h.peer
        ch.state = St.OPEN
        ch.openedMs = now
        ch.lastRxMs = now
        if (current != h.peer) {
            current = h.peer
            out += LinkCommand.PeerSeen(h.peer)
        }
        if (ch.transport == Transport.BT) {
            val addr = ch.remote.uppercase()
            if (addr != btLearned) {
                btLearned = addr
                btTargets = BtTargets.choose(bonded, btLearned)
                out += LinkCommand.BtPeerLearned(addr)
            }
            // Eş Bluetooth'tan geldi: onun Wi-Fi kanalı kapanmış olabilir; bizdeki TCP yoklanır (yarı açık kalmasın).
            channels.values.filter { it.transport == Transport.WIFI && it.state == St.OPEN }.forEach { probe(it, now) }
        }
        resolveDuplicates(ch, now)
    }

    /** LINK.md 1.5: aynı taşımada aynı eşle iki kanal → açanın kimliği küçük olan kalır. */
    private fun resolveDuplicates(ch: Ch, now: Long) {
        val me = self!!
        for (other in channels.values.toList()) {
            if (other === ch || other.state != St.OPEN || other.transport != ch.transport || other.peer != ch.peer) continue
            val ic = if (ch.outbound) me else ch.peer!!
            val io = if (other.outbound) me else other.peer!!
            if (ic != io) {
                close(if (ic < io) other else ch, ByeReason.DUPLICATE, now)
            } else if (ic == me) {
                // İkisini de biz açtık: yenisi kapanır. Eş açtıysa kararı o verir.
                close(ch, ByeReason.DUPLICATE, now)
            }
            if (channels[ch.id] !== ch) return
        }
    }

    private fun onBeacon(e: LinkEvent.BeaconIn, now: Long) {
        if (!enabled) return
        val b = Beacon.decode(e.data, e.length) ?: return
        if (b.peer == self) return
        if (expected != null && b.peer != expected) return
        if (expected == null && current != null && b.peer != current && active() != null) return
        seen[e.from] = Seen(b.peer, b.tcpPort, now)
    }

    // --- adım ---

    private fun step(now: Long) {
        if (!enabled) {
            setListen(Transport.WIFI, false)
            setListen(Transport.BT, false)
            emitStatus()
            emitWake(null)
            return
        }
        seen.entries.removeAll { now - it.value.atMs > cfg.beaconForgetMs }
        for (ch in channels.values.toList()) timers(ch, now)
        bookkeep(now)
        scheduleBtDrain(now)
        setListen(Transport.WIFI, lan)
        setListen(Transport.BT, btOn)
        val tcpOpen = tcpOpen()
        if (!tcpOpen && lan) {
            val last = lastBeaconMs
            if (last == null || now - last >= beaconInterval(now)) {
                out += LinkCommand.SendBeacon(beaconBytes, cfg.beaconPort)
                lastBeaconMs = now
            }
            gateway?.let { tryTcp(it, cfg.tcpPort, now) }
            for ((ip, s) in seen.entries.sortedBy { it.key }) tryTcp(ip, s.port, now)
        }
        if (btAllowed() && (!lan || now - noWifiSinceMs >= cfg.btGraceMs)) tryBt(now)
        bookkeep(now)
        emitStatus()
        emitWake(nextWake(now))
    }

    private fun timers(ch: Ch, now: Long) {
        when (ch.state) {
            St.CONNECTING -> if (now - ch.startedMs >= cfg.connectTimeoutMs(ch.transport) + LinkConfig.CONNECT_GUARD_MS) {
                close(ch, null, now)
            }
            St.HANDSHAKE -> if (now - ch.startedMs >= cfg.helloTimeoutMs) close(ch, ByeReason.TIMEOUT, now)
            St.OPEN -> {
                val probe = ch.probeDeadlineMs
                when {
                    now - ch.lastRxMs >= cfg.deadMs(ch.transport) || (probe != null && now >= probe) ->
                        close(ch, ByeReason.TIMEOUT, now)
                    ch.drainAtMs?.let { now >= it } == true -> close(ch, ByeReason.MOVED_TO_WIFI, now)
                    now - ch.lastTxMs >= cfg.pingMs(ch.transport) -> write(ch, Frames.ping(now), now)
                }
            }
        }
    }

    /** Wi-Fi canlıysa (Bluetooth açıldıktan sonra TCP'den çerçeve geldiyse) Bluetooth kanalı [LinkConfig.btDrainMs] sonra kapanır. */
    private fun scheduleBtDrain(now: Long) {
        val tcp = channels.values.filter { it.transport == Transport.WIFI && it.state == St.OPEN }
        for (bt in channels.values) {
            if (bt.transport != Transport.BT || bt.state != St.OPEN || bt.drainAtMs != null) continue
            if (tcp.any { it.peer == bt.peer && it.lastRxMs > bt.openedMs }) bt.drainAtMs = now + cfg.btDrainMs
        }
    }

    private fun bookkeep(now: Long) {
        val tcpOpen = tcpOpen()
        if (tcpWasOpen && !tcpOpen) {
            noWifiSinceMs = now
            lastBeaconMs = null
        }
        tcpWasOpen = tcpOpen
        val linked = active() != null
        if (linked && linkedSinceMs == null) linkedSinceMs = now
        if (!linked && linkedSinceMs != null) {
            linkedSinceMs = null
            searchSinceMs = now
        }
    }

    private fun tryTcp(host: String, port: Int, now: Long) {
        if (channels.values.any { it.transport == Transport.WIFI && it.remote == host }) return
        val key = "tcp:$host:$port"
        if (!backoff.ready(key, now)) return
        open(Transport.WIFI, host, port, key, now)
    }

    private fun btAllowed() = btOn && !listenOnly && btTargets.isNotEmpty() && !tcpOpen() &&
        channels.values.none { it.transport == Transport.BT }

    private fun tryBt(now: Long) {
        val n = btTargets.size
        for (i in 0 until n) {
            val addr = btTargets[(btNext + i) % n]
            val key = "bt:$addr"
            if (backoff.ready(key, now)) {
                btNext = (btNext + i + 1) % n
                open(Transport.BT, addr, 0, key, now)
                return
            }
        }
    }

    private fun open(t: Transport, host: String, port: Int, key: String, now: Long) {
        val ch = Ch(nextId++, t, outbound = true, remote = host, key = key, maxPayload = cfg.maxPayload(t))
        ch.startedMs = now
        channels[ch.id] = ch
        out += LinkCommand.Connect(ch.id, t, host, port)
    }

    private fun close(ch: Ch, reason: ByeReason?, now: Long) {
        if (channels[ch.id] !== ch) return
        if (reason != null && ch.state != St.CONNECTING) out += LinkCommand.Write(ch.id, Frames.bye(reason))
        out += LinkCommand.Close(ch.id)
        channels.remove(ch.id)
        settle(ch, endOf(reason, ch), now)
    }

    /** Kanal sonu geri çekilmeye nasıl yansır (LINK.md 1.6). */
    private enum class End { NEUTRAL, REJECT, LOST }

    private fun endOf(reason: ByeReason?, ch: Ch): End = when (reason) {
        ByeReason.DUPLICATE, ByeReason.MOVED_TO_WIFI -> End.NEUTRAL
        ByeReason.TIMEOUT -> if (ch.state == St.OPEN) End.LOST else End.REJECT
        else -> End.REJECT
    }

    /**
     * Giden kanalın hedefi: ret ve kurulamayan bağlantı geri çekilmeyi büyütür; en az [LinkConfig.stableMs] açık kalmış kanal
     * koparsa hedef hemen denenir; kısa ömürlü kanal (açılıp hemen kopan) da geri çekilmeye girer, kısır döngü olmaz.
     */
    private fun settle(ch: Ch, end: End, now: Long) {
        if (!ch.outbound) return
        when (end) {
            End.NEUTRAL -> Unit
            End.REJECT -> backoff.failed(ch.key, now)
            End.LOST -> if (now - ch.openedMs >= cfg.stableMs) backoff.succeeded(ch.key) else backoff.failed(ch.key, now)
        }
    }

    private fun write(ch: Ch, bytes: ByteArray, now: Long) {
        out += LinkCommand.Write(ch.id, bytes)
        ch.lastTxMs = now
    }

    private fun probe(ch: Ch, now: Long) {
        write(ch, Frames.ping(now), now)
        val d = now + cfg.probeMs
        ch.probeDeadlineMs = ch.probeDeadlineMs?.let { minOf(it, d) } ?: d
    }

    private fun setListen(t: Transport, on: Boolean) {
        if (listening[t] == on) return
        listening[t] = on
        out += LinkCommand.Listen(t, on)
    }

    private fun tcpOpen() = channels.values.any { it.transport == Transport.WIFI && it.state == St.OPEN }

    /** Etkin kanal: açık TCP varsa (en eski), yoksa açık Bluetooth. */
    private fun active(): Ch? = channels.values.filter { it.state == St.OPEN }
        .minWithOrNull(compareBy<Ch>({ if (it.transport == Transport.WIFI) 0 else 1 }, { it.openedMs }, { it.id }))

    private fun beaconInterval(now: Long) = if (now - noWifiSinceMs < cfg.beaconFastForMs) cfg.beaconFastMs else cfg.beaconSlowMs

    private fun computeStatus(): LinkStatus {
        if (!enabled) return LinkStatus.OFF
        val a = active()
        val phones = btTargets.isNotEmpty()
        return if (a != null) {
            LinkStatus(LinkStatus.State.LINKED, a.transport, a.peer, linkedSinceMs ?: a.openedMs, a.rttMs, lan, btOn, phones)
        } else {
            LinkStatus(LinkStatus.State.SEARCHING, null, null, searchSinceMs, null, lan, btOn, phones)
        }
    }

    private fun emitStatus() {
        val s = computeStatus()
        if (s != lastStatus) {
            lastStatus = s
            out += LinkCommand.Status(s)
        }
    }

    private fun emitWake(at: Long?) {
        if (wakeSent && at == lastWake) return
        wakeSent = true
        lastWake = at
        out += LinkCommand.Wake(at)
    }

    private fun nextWake(now: Long): Long? {
        val t = ArrayList<Long>()
        if (!tcpOpen() && lan) {
            lastBeaconMs?.let { t += it + beaconInterval(now) }
            gateway?.let { gw -> backoff.nextAtMs("tcp:$gw:${cfg.tcpPort}")?.let { t += it } }
            for ((ip, s) in seen) backoff.nextAtMs("tcp:$ip:${s.port}")?.let { t += it }
        }
        if (btAllowed()) {
            if (lan && now < noWifiSinceMs + cfg.btGraceMs) {
                t += noWifiSinceMs + cfg.btGraceMs
            } else {
                btTargets.mapNotNullTo(t) { backoff.nextAtMs("bt:$it") }
            }
        }
        for (ch in channels.values) {
            when (ch.state) {
                St.CONNECTING -> t += ch.startedMs + cfg.connectTimeoutMs(ch.transport) + LinkConfig.CONNECT_GUARD_MS
                St.HANDSHAKE -> t += ch.startedMs + cfg.helloTimeoutMs
                St.OPEN -> {
                    t += ch.lastTxMs + cfg.pingMs(ch.transport)
                    t += ch.lastRxMs + cfg.deadMs(ch.transport)
                    ch.probeDeadlineMs?.let { t += it }
                    ch.drainAtMs?.let { t += it }
                }
            }
        }
        return t.minOrNull()?.coerceAtLeast(now + 1)
    }

    private companion object {
        const val MAX_PENDING_INBOUND = 4
    }
}
