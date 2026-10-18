package com.kuryelog.logic.link

import java.security.SecureRandom
import java.util.PriorityQueue

/**
 * Sahte ağ (LINK.md 1.8, 3.14): telefon yığınlarını ([LinkStack]) TCP, UDP işareti ve Bluetooth benzetimiyle bağlar; zaman elle ilerler.
 *
 * Wi-Fi: aynı [Node.lanName]'deki düğümler birbirine ulaşır; [wifiCut] = yerel ağda paketler sessizce kaybolur
 * (yarı açık bağlantı, istemci yalıtımı). Bluetooth: iki tarafta açık, karşılıklı eşleşmiş ve [btCut] değilse bağlanır.
 * A8.2: her düğümün kimlik anahtarı ve güveni var ([pair] ikisini birbirine eşler); [tamper] yazılan her çerçeveyi görür
 * (düşür, değiştir, çoğalt), [divert] bir IP'ye bağlananı başka düğüme götürür (araya giren).
 */
class FakeNet(val cfg: LinkConfig = LinkConfig()) {
    var now = 0L
        private set
    var latencyMs = 5L
    /** Bluetooth'ta yazma gecikmesi; `null` = [latencyMs]. */
    var btLatencyMs: Long? = null
    var btConnectMs = 1_000L
    /** Yazılan baytlar alıcıya bu boyda parçalar hâlinde gider (0 = bölmeden). */
    var chunk = 0
    var wifiCut = false
    var btCut = false
    /** Her çerçeve yazımı: dönen liste alıcıya sırayla gider (boş = düşür). */
    var tamper: ((from: Node, to: Node, t: Transport, frame: ByteArray) -> List<ByteArray>)? = null
    /** Wi-Fi'da bu adrese bağlanan, eşlenen düğüme varır (ARP zehirleme benzetimi). */
    val divert = HashMap<String, Node>()
    val random = SecureRandom()

    private class Ev(val at: Long, val seq: Long, val action: () -> Unit)

    private var seq = 0L
    private val queue = PriorityQueue(compareBy<Ev>({ it.at }, { it.seq }))
    val nodes = ArrayList<Node>()

    private fun at(t: Long, action: () -> Unit) {
        queue += Ev(t, seq++, action)
    }

    /** [messaging] = `Messenger`'lı yığın (A8.2 mesajları); değilse ham `DATA` (A8.1 testleri). */
    fun node(name: String, id: String, bt: String, messaging: Boolean = false): Node =
        Node(name, PeerId.of(id)!!, bt, messaging).also { nodes += it }

    /** İki düğümü birbirine eşlenmiş yapar (QR'sız; eşleştirme testleri [Node.issue]/[Node.scan] kullanır). */
    fun pair(a: Node, b: Node) {
        a.trust = Trust.Paired(b.record())
        b.trust = Trust.Paired(a.record())
    }

    /** Zamanı [ms] ilerletir: sıradaki olaylar ve uyanmalar zaman sırasıyla işlenir. */
    fun run(ms: Long) {
        val until = now + ms
        while (true) {
            val nextEv = queue.peek()?.at
            val nextWake = nodes.mapNotNull { it.wakeAt }.minOrNull()
            val next = listOfNotNull(nextEv, nextWake).minOrNull()
            if (next == null || next > until) {
                now = until
                return
            }
            now = maxOf(now, next)
            if (nextEv != null && nextEv <= now) {
                queue.poll().action()
                continue
            }
            for (n in nodes) {
                val w = n.wakeAt
                if (w != null && w <= now) {
                    n.wakeAt = null
                    n.handle(LinkEvent.Tick)
                }
            }
        }
    }

    /** [owner] hotspot açar, [client] ona bağlanır. */
    fun hotspot(owner: Node, client: Node) {
        owner.join("hs", "192.168.43.1", gateway = null)
        client.join("hs", "192.168.43.77", gateway = "192.168.43.1")
    }

    fun bond(a: Node, b: Node) {
        a.bonded = a.bonded + BondedDevice(b.btAddress, isPhone = true)
        b.bonded = b.bonded + BondedDevice(a.btAddress, isPhone = true)
        a.handle(LinkEvent.Bt(a.btOn, a.bonded))
        b.handle(LinkEvent.Bt(b.btOn, b.bonded))
    }

    class Link(val node: Node, val channel: Int, val transport: Transport)

    inner class Node(val name: String, val id: PeerId, val btAddress: String, val messaging: Boolean) {
        var keys: DhKeyPair = P256.generate(random)
        var info = PeerInfo(id, LinkProtocol.MAJOR, LinkProtocol.MINOR, 34, "1.0.0", name)
        val identity: LocalIdentity get() = LocalIdentity(id, keys, info)
        /** [enable]'ın varsayılan güveni. */
        var trust: Trust? = null
        val store = MemoryMessengerStore()
        /** Duvar saati = ortak taban + ağ zamanı + bu kayma. */
        var wallSkewMs = 0L
        val wall: Long get() = WALL_BASE + now + wallSkewMs
        var stack = newStack()
        val m: LinkMachine get() = stack.machine

        var ip: String? = null
        var lanName: String? = null
        var btOn = true
        var bonded = listOf<BondedDevice>()
        /** Makinesiz sağır eş: bağlantıyı kabul eder, hiç yazmaz. */
        var mute = false
        val listening = HashSet<Transport>()
        val delivered = ArrayList<Pair<String, Transport>>()
        val messages = ArrayList<Incoming>()
        val statuses = ArrayList<LinkStatus>()
        val paired = ArrayList<PeerRecord>()
        val peerInfos = ArrayList<PeerInfo>()
        val acked = ArrayList<LinkCommand.Acked>()
        val expired = ArrayList<LinkCommand.Expired>()
        val clocks = ArrayList<LinkCommand.Clock>()
        var unpaired = 0
        var pairingExpired = 0
        val btLearned = ArrayList<String>()
        /** (zaman, komut) */
        val log = ArrayList<Pair<Long, LinkCommand>>()
        var wakeAt: Long? = null
        private var nextIn = -1
        private val links = HashMap<Int, Link>()

        private fun newStack() = LinkStack(cfg, if (messaging) store else null, random = { 0.5 }, secureRandom = random)

        val status: LinkStatus get() = stack.status

        fun record(): PeerRecord = PeerRecord(id, keys.public, info.deviceName, 0)

        fun links(t: Transport? = null) = links.filter { t == null || it.value.transport == t }

        fun connects(t: Transport) = log.filter { (it.second as? LinkCommand.Connect)?.transport == t }

        fun handle(e: LinkEvent) {
            if (mute) return
            stack.handle(e, now, wall)
            apply(stack.drain())
        }

        fun enable(trust: Trust? = this.trust, btPeer: String? = null, listenOnly: Boolean = false) =
            handle(LinkEvent.Enable(identity, trust ?: error("$name: güven yok (net.pair)"), btPeer, listenOnly))

        /** Veren telefon: QR üretir, pencere [windowMs]. */
        fun issue(windowMs: Long = PairingQr.WINDOW_MS, token: ByteArray = ByteArray(32).also(random::nextBytes)): PairingQr {
            enable(Trust.Issuer(token, now + windowMs))
            return PairingQr(id, keys.public, token, info.deviceName)
        }

        /** Okuyan telefon: QR'ı okudu. */
        fun scan(qr: PairingQr, windowMs: Long = PairingQr.WINDOW_MS) = enable(Trust.Scanner(qr, now + windowMs))

        fun send(text: String): SendResult = sendBytes(text.toByteArray())

        fun sendBytes(b: ByteArray): SendResult {
            val r = stack.send(b, now, wall)
            apply(stack.drain())
            return r
        }

        fun publishLatest(type: String, text: String, ttlMs: Long = 60_000) = stack.publishLatest(type, text.toByteArray(), ttlMs, now, wall).also { apply(stack.drain()) }

        fun publishReliable(type: String, text: String, ttlMs: Long = 86_400_000L) = stack.publishReliable(type, text.toByteArray(), ttlMs, now, wall).also { apply(stack.drain()) }

        fun publishReliableBytes(type: String, b: ByteArray) = stack.publishReliable(type, b, 86_400_000L, now, wall).also { apply(stack.drain()) }

        fun unpair() {
            stack.unpair(now, wall)
            apply(stack.drain())
        }

        /** Süreç yeniden başladı: soketler kapanır (eş görür), bellek gider, [store] kalır. */
        fun restart(enableAgain: Boolean = true) {
            for ((ch, l) in links.toList()) {
                links.remove(ch)
                l.node.links.remove(l.channel)
                if (!cut(l.transport) && !l.node.cut(l.transport)) at(now + latencyMs) { l.node.handle(LinkEvent.Closed(l.channel, "eof")) }
            }
            listening.clear()
            wakeAt = null
            stack = newStack()
            if (lanName != null) handle(LinkEvent.Lan(true, gatewayOf()))
            handle(LinkEvent.Bt(btOn, bonded))
            if (enableAgain) enable()
        }

        private var gateway: String? = null
        private fun gatewayOf() = gateway

        fun join(lan: String, address: String, gateway: String?) {
            lanName = lan
            ip = address
            this.gateway = gateway
            handle(LinkEvent.Lan(true, gateway))
        }

        /** Yerel ağ kayboldu (hotspot kapandı): açık TCP bağlantıları sessizce ölür. */
        fun leaveLan() {
            lanName = null
            ip = null
            gateway = null
            handle(LinkEvent.Lan(false, null))
        }

        private fun apply(cmds: List<LinkCommand>) {
            for (c in cmds) {
                log += now to c
                when (c) {
                    is LinkCommand.Listen -> if (c.on) listening += c.transport else listening -= c.transport
                    is LinkCommand.Connect -> connect(c)
                    is LinkCommand.Write -> write(c.channel, c.bytes)
                    is LinkCommand.Close -> close(c.channel)
                    is LinkCommand.SendBeacon -> beacon(c.bytes)
                    is LinkCommand.Deliver -> delivered += String(c.payload) to c.transport
                    is LinkCommand.Status -> statuses += c.status
                    is LinkCommand.Paired -> {
                        paired += c.peer
                        trust = Trust.Paired(c.peer)
                    }
                    is LinkCommand.Peer -> peerInfos += c.info
                    is LinkCommand.PairingExpired -> pairingExpired++
                    is LinkCommand.BtPeerLearned -> btLearned += c.address
                    is LinkCommand.Wake -> wakeAt = c.atMs
                    is LinkCommand.Message -> messages += c.message
                    is LinkCommand.Acked -> acked += c
                    is LinkCommand.Expired -> expired += c
                    is LinkCommand.Clock -> clocks += c
                    LinkCommand.Unpaired -> unpaired++
                }
            }
        }

        private fun connect(c: LinkCommand.Connect) {
            val me = this
            if (c.transport == Transport.WIFI) {
                val lan = lanName
                val target = divert[c.host]?.takeIf { it.lanName == lan && lan != null }
                    ?: nodes.firstOrNull { it !== me && it.ip == c.host && it.lanName == lan && lan != null }
                when {
                    lan == null -> at(now + latencyMs) { handle(LinkEvent.Failed(c.channel, "no_network")) }
                    wifiCut -> at(now + cfg.tcpConnectTimeoutMs) { handle(LinkEvent.Failed(c.channel, "timeout")) }
                    target == null || Transport.WIFI !in target.listening || c.port != cfg.tcpPort ->
                        at(now + latencyMs) { handle(LinkEvent.Failed(c.channel, "refused")) }
                    else -> pair(c.channel, target, Transport.WIFI, me.ip!!, latencyMs)
                }
            } else {
                val target = nodes.firstOrNull { it !== me && it.btAddress.equals(c.host, ignoreCase = true) }
                val reachable = target != null && btOn && target.btOn && !btCut &&
                    target.bonded.any { it.address.equals(btAddress, true) } && bonded.any { it.address.equals(c.host, true) }
                when {
                    !reachable -> at(now + 5_000) { handle(LinkEvent.Failed(c.channel, "page_timeout")) }
                    Transport.BT !in target!!.listening -> at(now + 200) { handle(LinkEvent.Failed(c.channel, "no_service")) }
                    else -> pair(c.channel, target, Transport.BT, btAddress, btConnectMs)
                }
            }
        }

        private fun pair(myCh: Int, target: Node, t: Transport, myAddress: String, delay: Long) {
            val theirCh = target.nextIn--
            links[myCh] = Link(target, theirCh, t)
            target.links[theirCh] = Link(this, myCh, t)
            at(now + delay) { handle(LinkEvent.Opened(myCh)) }
            at(now + delay) { target.handle(LinkEvent.Accepted(theirCh, t, myAddress)) }
        }

        fun cut(t: Transport) = if (t == Transport.WIFI) wifiCut || lanName == null else btCut || !btOn

        private fun write(ch: Int, bytes: ByteArray) {
            val l = links[ch] ?: return
            if (cut(l.transport) || l.node.mute || l.node.cut(l.transport)) return
            val frames = tamper?.invoke(this, l.node, l.transport, bytes) ?: listOf(bytes)
            val delay = if (l.transport == Transport.BT) btLatencyMs ?: latencyMs else latencyMs
            for (f in frames) {
                val parts = if (chunk <= 0) listOf(f) else f.toList().chunked(chunk).map { it.toByteArray() }
                for (p in parts) at(now + delay) { l.node.handle(LinkEvent.Bytes(l.channel, p)) }
            }
        }

        private fun close(ch: Int) {
            val l = links.remove(ch) ?: return
            l.node.links.remove(l.channel)
            if (cut(l.transport) || l.node.mute || l.node.cut(l.transport)) return
            val delay = if (l.transport == Transport.BT) btLatencyMs ?: latencyMs else latencyMs
            at(now + delay) { l.node.handle(LinkEvent.Closed(l.channel, "eof")) }
        }

        private fun beacon(bytes: ByteArray) {
            val lan = lanName ?: return
            if (wifiCut) return
            for (n in nodes) {
                if (n === this || n.mute || n.lanName != lan) continue
                val from = ip!!
                at(now + latencyMs) { n.handle(LinkEvent.BeaconIn(from, bytes)) }
            }
        }

        /** Sağır eş için: dinliyormuş gibi görünür. */
        fun listenMute(t: Transport) {
            listening += t
        }

        override fun toString() = name
    }

    companion object {
        const val WALL_BASE = 1_760_000_000_000L

        /** Çerçeve türü (LINK.md 1.4); bozma kancaları için. */
        fun frameType(frame: ByteArray): FrameType? = if (frame.size >= 5) FrameType.of(frame[4].toInt() and 0xff) else null
    }
}
