package com.kuryelog.logic.remote

import com.kuryelog.logic.link.DhKeyPair
import com.kuryelog.logic.link.P256
import java.security.SecureRandom
import java.util.UUID

/** İzleyen tarafın görünümü (REMOTE.md 1.6). */
data class ViewerView(
    val state: State,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val kbps: Int = 0,
    val ended: EndReason? = null,
) {
    enum class State { IDLE, ASKING, CONSENT, CONNECTING, PLAYING, ENDED }
    enum class EndReason { REFUSED, UNAVAILABLE, NO_ANSWER, NO_WIFI, HOST, LOST }

    companion object {
        val IDLE = ViewerView(State.IDLE)
    }
}

/**
 * İzleyen taraf (REMOTE.md 1.3–1.5), saf: olay alır, komut verir; zaman parametre. Android katmanı komutları uygular: link iletisi,
 * TCP bağlantısı, `MediaCodec` çözücü.
 */
class ViewerMachine(private val cfg: RemoteConfig = RemoteConfig(), private val random: SecureRandom = SecureRandom()) {
    sealed interface Event {
        /** [maxSide]: bu ekranın en uzun kenarı (paylaşan daha büyüğünü yollamasın). */
        class Start(val maxSide: Int) : Event
        object Stop : Event
        /** Bağlantı Wi-Fi üstündeyse eşin adresi, değilse null. */
        class Link(val peerHost: String?) : Event
        class Offer(val offer: RemoteOffer) : Event
        class End(val end: RemoteEnd) : Event
        class Opened(val conn: Int) : Event
        class Bytes(val conn: Int, val bytes: ByteArray, val len: Int = bytes.size) : Event
        class Closed(val conn: Int) : Event
        object DecoderError : Event
        object Tick : Event
    }

    sealed interface Command {
        class Send(val type: String, val payload: ByteArray) : Command
        class Connect(val conn: Int, val host: String, val port: Int) : Command
        class Write(val conn: Int, val bytes: ByteArray) : Command
        class Close(val conn: Int) : Command
        /** Çözücüyü (yeniden) kur: `csd-0` [sps], `csd-1` [pps] (başlangıç koduyla). */
        class Configure(val width: Int, val height: Int, val sps: ByteArray, val pps: ByteArray) : Command
        class Decode(val data: ByteArray, val ptsUs: Long, val key: Boolean) : Command
        /** Çözücüyü bırak. */
        object Release : Command
        class State(val view: ViewerView) : Command
    }

    private val out = ArrayList<Command>()
    private var nowMs = 0L
    private var peerHost: String? = null
    private var maxSide = cfg.maxSide
    private var state = ViewerView.State.IDLE
    private var endReason: ViewerView.EndReason? = null
    private var stateSinceMs = 0L
    private var session: UUID? = null
    private var pair: DhKeyPair? = null
    private var shared: ByteArray? = null
    private var port = 0
    private var reasked = false
    private var waitingHost = false

    private var nextConn = 0
    private var conn: Int? = null
    private var endpoint: StreamEndpoint? = null
    private var lastRxMs = 0L
    private var failures = 0
    private var reconnectAtMs: Long? = null

    private var width = 0
    private var height = 0
    private var configured = false
    private var expectedSeq: Long? = null
    private var needKey = true
    private var lastSeq = 0L
    private var receivedBytes = 0L
    private var lastAckMs = 0L
    private var lastKeyMs = Long.MIN_VALUE / 2

    private var framesThisSecond = 0
    private var bytesThisSecond = 0L
    private var secondStartMs = 0L
    private var fps = 0
    private var kbps = 0
    private var lastView = ViewerView.IDLE

    val view: ViewerView get() = lastView

    fun drain(): List<Command> = ArrayList(out).also { out.clear() }

    fun handle(e: Event, now: Long) {
        nowMs = now
        when (e) {
            is Event.Start -> start(e.maxSide)
            Event.Stop -> stop()
            is Event.Link -> onLink(e.peerHost)
            is Event.Offer -> onOffer(e.offer)
            is Event.End -> if (e.end.session == session) finish(ViewerView.EndReason.HOST, sendEnd = false)
            is Event.Opened -> onOpened(e.conn)
            is Event.Bytes -> onBytes(e)
            is Event.Closed -> if (e.conn == conn) lost()
            Event.DecoderError -> if (configured) { needKey = true; requestKey(force = true) }
            Event.Tick -> onTick()
        }
        emitView()
    }

    private fun start(side: Int) {
        if (session != null) return
        maxSide = side.coerceIn(RemoteControl.MIN_SIDE, cfg.maxSide)
        if (peerHost == null) {
            set(ViewerView.State.ENDED, ViewerView.EndReason.NO_WIFI)
            return
        }
        reasked = false
        ask()
    }

    private fun ask() {
        val p = P256.generate(random)
        val s = UUID(random.nextLong(), random.nextLong())
        pair = p
        session = s
        shared = null
        out += Command.Send(RemoteControl.ASK, RemoteControl.ask(RemoteAsk(s, p.public, maxSide)))
        set(ViewerView.State.ASKING)
    }

    private fun stop() {
        val s = session
        if (s != null) out += Command.Send(RemoteControl.END, RemoteControl.end(RemoteEnd(s, RemoteEnd.Reason.VIEWER)))
        clear()
        set(ViewerView.State.IDLE)
    }

    private fun onLink(host: String?) {
        peerHost = host
        if (host != null && waitingHost) {
            waitingHost = false
            connect()
        }
    }

    private fun onOffer(o: RemoteOffer) {
        if (o.session != session) return
        when (o.state) {
            RemoteOffer.State.CONSENT -> if (state == ViewerView.State.ASKING) set(ViewerView.State.CONSENT)
            RemoteOffer.State.READY -> {
                if (shared != null) return
                val p = pair ?: return
                shared = try {
                    P256.agree(p, o.pub ?: return)
                } catch (ex: Exception) {
                    finish(ViewerView.EndReason.UNAVAILABLE, sendEnd = true)
                    return
                }
                port = o.port ?: return
                failures = 0
                connect()
            }
            RemoteOffer.State.REFUSED -> finish(ViewerView.EndReason.REFUSED, sendEnd = false)
            RemoteOffer.State.UNAVAILABLE -> finish(ViewerView.EndReason.UNAVAILABLE, sendEnd = false)
        }
    }

    private fun connect() {
        reconnectAtMs = null
        val host = peerHost
        if (state != ViewerView.State.PLAYING) set(ViewerView.State.CONNECTING)
        if (host == null) {
            waitingHost = true
            return
        }
        val id = ++nextConn
        conn = id
        endpoint = null
        lastRxMs = nowMs
        out += Command.Connect(id, host, port)
    }

    private fun onOpened(id: Int) {
        if (id != conn) {
            out += Command.Close(id)
            return
        }
        val s = session ?: return
        val k = shared ?: return
        lastRxMs = nowMs
        endpoint = StreamEndpoint.viewer(random, s, k, StreamMsg.KeyRequest) { b -> out += Command.Write(id, b) }.also { it.start() }
        lastAckMs = nowMs
    }

    private fun onBytes(e: Event.Bytes) {
        if (e.conn != conn) return
        val ep = endpoint ?: return
        lastRxMs = nowMs
        val msgs = try {
            ep.receive(e.bytes, 0, e.len)
        } catch (ex: StreamException) {
            lost()
            return
        }
        for (m in msgs) {
            when (m) {
                is StreamMsg.Config -> onConfig(m)
                is StreamMsg.Frame -> onFrame(m)
                is StreamMsg.Bye -> {
                    if (m.reason == StreamMsg.BYE_ENDED) finish(ViewerView.EndReason.HOST, sendEnd = false) else lost()
                    return
                }
                else -> Unit
            }
        }
        if (ep.authenticated && nowMs - lastAckMs >= cfg.ackFastMs) ack()
    }

    private fun onConfig(c: StreamMsg.Config) {
        val ps = AnnexB.parameterSets(c.csd) ?: return
        width = c.width
        height = c.height
        configured = true
        expectedSeq = null
        needKey = true
        out += Command.Configure(c.width, c.height, ps.first, ps.second)
    }

    private fun onFrame(f: StreamMsg.Frame) {
        receivedBytes += f.data.size
        lastSeq = f.seq
        bytesThisSecond += f.data.size
        if (!configured) return
        val gap = expectedSeq?.let { it != f.seq } ?: false
        expectedSeq = (f.seq + 1) and 0xFFFFFFFFL
        if (gap) needKey = true
        if (needKey && !f.key) {
            requestKey()
            return
        }
        if (f.key) needKey = false
        out += Command.Decode(f.data, f.ptsUs, f.key)
        framesThisSecond++
        failures = 0
        reasked = false
        if (state != ViewerView.State.PLAYING) set(ViewerView.State.PLAYING)
    }

    private fun requestKey(force: Boolean = false) {
        val ep = endpoint ?: return
        if (!ep.authenticated) return
        if (!force && nowMs - lastKeyMs < cfg.keyRequestGapMs) return
        lastKeyMs = nowMs
        ep.send(StreamMsg.KeyRequest)
    }

    private fun ack() {
        val ep = endpoint ?: return
        lastAckMs = nowMs
        ep.send(StreamMsg.Ack(lastSeq, receivedBytes))
    }

    /** Akış koptu: oturum sürüyorsa geri çekilmeyle yeniden bağlan; denemeler bitince bir kez yeniden iste. */
    private fun lost() {
        closeConn()
        if (session == null || shared == null) return
        if (failures < cfg.reconnectMs.size) {
            reconnectAtMs = nowMs + cfg.reconnectMs[failures]
            failures++
            set(ViewerView.State.CONNECTING)
            return
        }
        if (!reasked) {
            reasked = true
            failures = 0
            out += Command.Release
            configured = false
            ask()
            return
        }
        finish(ViewerView.EndReason.LOST, sendEnd = true)
    }

    private fun closeConn() {
        conn?.let { out += Command.Close(it) }
        conn = null
        endpoint = null
        waitingHost = false
        receivedBytes = 0
        expectedSeq = null
        needKey = true
    }

    private fun finish(reason: ViewerView.EndReason, sendEnd: Boolean) {
        val s = session
        if (sendEnd && s != null) out += Command.Send(RemoteControl.END, RemoteControl.end(RemoteEnd(s, RemoteEnd.Reason.VIEWER)))
        clear()
        set(ViewerView.State.ENDED, reason)
    }

    private fun clear() {
        closeConn()
        if (configured) out += Command.Release
        configured = false
        session = null
        pair = null
        shared = null
        reconnectAtMs = null
        failures = 0
    }

    private fun onTick() {
        when (state) {
            ViewerView.State.ASKING -> if (nowMs - stateSinceMs >= cfg.offerWaitMs) finish(ViewerView.EndReason.NO_ANSWER, sendEnd = true)
            ViewerView.State.CONSENT -> if (nowMs - stateSinceMs >= cfg.consentWaitMs + cfg.offerWaitMs) finish(ViewerView.EndReason.NO_ANSWER, sendEnd = true)
            else -> Unit
        }
        if (conn != null && nowMs - lastRxMs >= cfg.deadAfterMs) lost()
        reconnectAtMs?.let { if (nowMs >= it) connect() }
        if (endpoint?.authenticated == true && nowMs - lastAckMs >= cfg.ackEveryMs) ack()
        if (nowMs - secondStartMs >= 1_000) {
            val span = maxOf(1L, nowMs - secondStartMs)
            fps = (framesThisSecond * 1000L / span).toInt()
            kbps = (bytesThisSecond * 8 / span).toInt()
            framesThisSecond = 0
            bytesThisSecond = 0
            secondStartMs = nowMs
        }
    }

    private fun set(s: ViewerView.State, reason: ViewerView.EndReason? = null) {
        if (s != state) stateSinceMs = nowMs
        state = s
        endReason = reason
    }

    private fun emitView() {
        val playing = state == ViewerView.State.PLAYING
        val v = ViewerView(state, if (playing) width else 0, if (playing) height else 0, if (playing) fps else 0, if (playing) kbps else 0, endReason)
        if (v != lastView) {
            lastView = v
            out += Command.State(v)
        }
    }
}
