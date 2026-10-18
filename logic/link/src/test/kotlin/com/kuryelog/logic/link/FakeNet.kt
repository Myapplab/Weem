package com.kuryelog.logic.link

import java.util.PriorityQueue

/**
 * Sahte ağ (LINK.md 1.8): telefon makinelerini TCP, UDP işareti ve Bluetooth benzetimiyle bağlar; zaman elle ilerler.
 *
 * Wi-Fi: aynı [Node.lanName]'deki düğümler birbirine ulaşır; [wifiCut] = yerel ağda paketler sessizce kaybolur
 * (yarı açık bağlantı, istemci yalıtımı). Bluetooth: iki tarafta açık, karşılıklı eşleşmiş ve [btCut] değilse bağlanır.
 */
class FakeNet(val cfg: LinkConfig = LinkConfig()) {
    var now = 0L
        private set
    var latencyMs = 5L
    var btConnectMs = 1_000L
    /** Yazılan baytlar alıcıya bu boyda parçalar hâlinde gider (0 = bölmeden). */
    var chunk = 0
    var wifiCut = false
    var btCut = false

    private class Ev(val at: Long, val seq: Long, val action: () -> Unit)

    private var seq = 0L
    private val queue = PriorityQueue(compareBy<Ev>({ it.at }, { it.seq }))
    val nodes = ArrayList<Node>()

    private fun at(t: Long, action: () -> Unit) {
        queue += Ev(t, seq++, action)
    }

    fun node(name: String, id: String, bt: String): Node = Node(name, PeerId.of(id)!!, bt).also { nodes += it }

    /** Zamanı [ms] ilerletir: sıradaki olaylar ve makine uyanmaları zaman sırasıyla işlenir. */
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

    inner class Node(val name: String, val id: PeerId, val btAddress: String) {
        val m = LinkMachine(cfg, random = { 0.5 })
        var ip: String? = null
        var lanName: String? = null
        var btOn = true
        var bonded = listOf<BondedDevice>()
        /** Makinesiz sağır eş: bağlantıyı kabul eder, hiç yazmaz. */
        var mute = false
        val listening = HashSet<Transport>()
        val delivered = ArrayList<Pair<String, Transport>>()
        val statuses = ArrayList<LinkStatus>()
        val peersSeen = ArrayList<PeerId>()
        val btLearned = ArrayList<String>()
        /** (zaman, komut) */
        val log = ArrayList<Pair<Long, LinkCommand>>()
        var wakeAt: Long? = null
        private var nextIn = -1
        private val links = HashMap<Int, Link>()

        val status: LinkStatus get() = m.status

        fun links(t: Transport? = null) = links.filter { t == null || it.value.transport == t }

        fun connects(t: Transport) = log.filter { (it.second as? LinkCommand.Connect)?.transport == t }

        fun handle(e: LinkEvent) {
            if (mute) return
            m.handle(e, now)
            apply(m.drain())
        }

        fun enable(expected: PeerId? = null, btPeer: String? = null, listenOnly: Boolean = false) =
            handle(LinkEvent.Enable(id, expected, btPeer, listenOnly))

        fun send(text: String): SendResult = sendBytes(text.toByteArray())

        fun sendBytes(b: ByteArray): SendResult {
            val r = m.send(b, now)
            apply(m.drain())
            return r
        }

        fun join(lan: String, address: String, gateway: String?) {
            lanName = lan
            ip = address
            handle(LinkEvent.Lan(true, gateway))
        }

        /** Yerel ağ kayboldu (hotspot kapandı): açık TCP bağlantıları sessizce ölür. */
        fun leaveLan() {
            lanName = null
            ip = null
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
                    is LinkCommand.PeerSeen -> peersSeen += c.peer
                    is LinkCommand.BtPeerLearned -> btLearned += c.address
                    is LinkCommand.Wake -> wakeAt = c.atMs
                }
            }
        }

        private fun connect(c: LinkCommand.Connect) {
            val me = this
            if (c.transport == Transport.WIFI) {
                val lan = lanName
                val target = nodes.firstOrNull { it !== me && it.ip == c.host && it.lanName == lan && lan != null }
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

        private fun cut(t: Transport) = if (t == Transport.WIFI) wifiCut || lanName == null else btCut || !btOn

        private fun write(ch: Int, bytes: ByteArray) {
            val l = links[ch] ?: return
            if (cut(l.transport) || l.node.mute || l.node.cut(l.transport)) return
            val parts = if (chunk <= 0) listOf(bytes) else bytes.toList().chunked(chunk).map { it.toByteArray() }
            for (p in parts) at(now + latencyMs) { l.node.handle(LinkEvent.Bytes(l.channel, p)) }
        }

        private fun close(ch: Int) {
            val l = links.remove(ch) ?: return
            l.node.links.remove(l.channel)
            if (cut(l.transport) || l.node.mute || l.node.cut(l.transport)) return
            at(now + latencyMs) { l.node.handle(LinkEvent.Closed(l.channel, "eof")) }
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
    }
}
