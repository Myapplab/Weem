package com.kuryelog.logic.remote

import com.kuryelog.logic.link.DhKeyPair
import com.kuryelog.logic.link.P256
import java.security.SecureRandom
import java.util.UUID

/** Paylaşan tarafın görünümü (REMOTE.md 1.6). */
data class HostView(val state: State, val watched: Boolean = false, val kbps: Int = 0) {
    enum class State { OFF, CONSENT, SHARING }

    companion object {
        val OFF = HostView(State.OFF)
    }
}

/**
 * Paylaşan taraf (REMOTE.md 1.2–1.5), saf: olay alır, komut verir; zaman parametre (monoton ms). Android katmanı (`RemoteHub`)
 * komutları uygular: link iletisi, izin bildirimi, MediaProjection/kodlayıcı, TCP soketleri.
 *
 * Paylaşım oturumu (bir onay) izleyen oturumlarından uzun yaşar: izleyen yokken [RemoteConfig.idleStopMs] sonra kapanır.
 */
class HostMachine(private val cfg: RemoteConfig = RemoteConfig(), private val random: SecureRandom = SecureRandom()) {
    sealed interface Event {
        /** Gelen `remote.ask`; [ageMs] = gönderilmesinden bu yana (saat farkı düzeltilmiş). */
        class Ask(val ask: RemoteAsk, val ageMs: Long) : Event
        class End(val end: RemoteEnd) : Event
        /** Kullanıcı onay verdi; paylaşım (MediaProjection) açıldı; [width]×[height] ekran boyutu. */
        class ConsentGranted(val width: Int, val height: Int) : Event
        object ConsentDenied : Event
        /** Kullanıcı bu telefonda paylaşımı açmak istedi (izleyen beklemeden). */
        object ShareRequested : Event
        /** Kullanıcı "Paylaşımı bitir" dedi. */
        object StopLocal : Event
        /** Sistem paylaşımı durdurdu (MediaProjection `onStop`). */
        object ProjectionStopped : Event
        /** Eşleşme kalktı ya da 2 telefon modu kapandı. */
        object LinkGone : Event
        /** Ekran boyutu değişti (döndürme). */
        class ScreenSize(val width: Int, val height: Int) : Event
        class Accepted(val conn: Int) : Event
        class Bytes(val conn: Int, val bytes: ByteArray, val len: Int = bytes.size) : Event
        class Closed(val conn: Int) : Event
        class EncoderConfig(val width: Int, val height: Int, val csd: ByteArray) : Event
        class EncoderFrame(val data: ByteArray, val ptsUs: Long, val key: Boolean) : Event
        object EncoderFailed : Event
        object Tick : Event
    }

    sealed interface Command {
        /** Link iletisi (teslim zorunlu, TTL [RemoteControl.TTL_MS]). */
        class Send(val type: String, val payload: ByteArray) : Command
        /** İzin bildirimini göster / kaldır. */
        class ConsentNotice(val show: Boolean) : Command
        /** Kullanıcı bu telefonda istedi: onay penceresini doğrudan aç. */
        object OpenConsent : Command
        class Listen(val on: Boolean, val port: Int) : Command
        class StartEncoder(val geometry: Geometry) : Command
        object StopEncoder : Command
        class SetBitrate(val bps: Int) : Command
        object RequestKey : Command
        class Write(val conn: Int, val bytes: ByteArray) : Command
        class Close(val conn: Int) : Command
        object StopProjection : Command
        class State(val view: HostView) : Command
    }

    private enum class Projection { NONE, PENDING, ACTIVE }

    private class Session(val id: UUID, val shared: ByteArray, val pub: ByteArray, val maxSide: Int)

    private inner class Conn(val id: Int, val openedMs: Long) {
        var lastRxMs = openedMs
        var lastTxMs = openedMs
        var lastAckMs = openedMs
        val endpoint = StreamEndpoint.host(random, { s -> session?.takeIf { it.id == s }?.shared }) { b ->
            lastTxMs = nowMs
            out += Command.Write(id, b)
        }
    }

    private val out = ArrayList<Command>()
    private var nowMs = 0L
    private var projection = Projection.NONE
    private var pendingSinceMs = 0L
    private var session: Session? = null
    private var listening = false
    private val conns = LinkedHashMap<Int, Conn>()
    private var active: Conn? = null
    private var idleSinceMs = 0L
    private var screenW = 0
    private var screenH = 0
    private var attempt = 0
    private var encoding: Geometry? = null
    private var config: StreamMsg.Config? = null
    private var flow: FlowControl? = null
    private var seq = 0L
    private var lastKeyMs = Long.MIN_VALUE / 2
    private var bytesThisSecond = 0L
    private var secondStartMs = 0L
    private var lastView = HostView.OFF

    fun drain(): List<Command> = ArrayList(out).also { out.clear() }

    val view: HostView get() = lastView

    fun handle(e: Event, now: Long) {
        nowMs = now
        when (e) {
            is Event.Ask -> onAsk(e)
            is Event.End -> if (e.end.session == session?.id) endSession(sendEnd = null)
            is Event.ConsentGranted -> onGranted(e)
            Event.ConsentDenied -> onDenied()
            Event.ShareRequested -> if (projection == Projection.NONE) {
                projection = Projection.PENDING
                pendingSinceMs = now
                out += Command.OpenConsent
            }
            Event.StopLocal -> stopSharing(RemoteEnd.Reason.HOST, stop = true)
            Event.ProjectionStopped -> stopSharing(RemoteEnd.Reason.STOPPED, stop = false)
            Event.LinkGone -> stopSharing(RemoteEnd.Reason.HOST, stop = true, notify = false)
            is Event.ScreenSize -> onScreen(e.width, e.height)
            is Event.Accepted -> onAccepted(e.conn)
            is Event.Bytes -> onBytes(e)
            is Event.Closed -> onClosed(e.conn)
            is Event.EncoderConfig -> onConfig(e)
            is Event.EncoderFrame -> onFrame(e)
            Event.EncoderFailed -> onEncoderFailed()
            Event.Tick -> onTick()
        }
        emitView()
    }

    private fun onAsk(e: Event.Ask) {
        if (e.ageMs > cfg.askTtlMs) return
        val a = e.ask
        if (a.session == session?.id) {
            // Yeniden gönderilmiş aynı istek: son yanıt yinelenir.
            offerFor(session!!)
            return
        }
        endSession(sendEnd = null)
        val pair: DhKeyPair = P256.generate(random)
        val shared = try {
            P256.agree(pair, a.pub)
        } catch (ex: Exception) {
            out += send(RemoteControl.OFFER, RemoteControl.offer(RemoteOffer(a.session, RemoteOffer.State.UNAVAILABLE)))
            return
        }
        val s = Session(a.session, shared, pair.public, a.maxSide.coerceAtLeast(RemoteControl.MIN_SIDE))
        session = s
        attempt = 0
        if (projection == Projection.NONE) {
            projection = Projection.PENDING
            pendingSinceMs = nowMs
            out += Command.ConsentNotice(true)
        }
        offerFor(s)
    }

    private fun offerFor(s: Session) {
        val o = when (projection) {
            Projection.ACTIVE -> {
                listen(true)
                RemoteOffer(s.id, RemoteOffer.State.READY, s.pub, cfg.port)
            }
            else -> RemoteOffer(s.id, RemoteOffer.State.CONSENT)
        }
        out += send(RemoteControl.OFFER, RemoteControl.offer(o))
    }

    private fun onGranted(e: Event.ConsentGranted) {
        projection = Projection.ACTIVE
        screenW = e.width
        screenH = e.height
        idleSinceMs = nowMs
        out += Command.ConsentNotice(false)
        session?.let(::offerFor)
    }

    private fun onDenied() {
        if (projection != Projection.PENDING) return
        projection = Projection.NONE
        out += Command.ConsentNotice(false)
        session?.let { out += send(RemoteControl.OFFER, RemoteControl.offer(RemoteOffer(it.id, RemoteOffer.State.REFUSED))) }
        session = null
    }

    private fun stopSharing(reason: RemoteEnd.Reason, stop: Boolean, notify: Boolean = true) {
        val was = projection
        endSession(sendEnd = if (notify) reason else null)
        if (was == Projection.PENDING) out += Command.ConsentNotice(false)
        if (was == Projection.ACTIVE && stop) out += Command.StopProjection
        projection = Projection.NONE
    }

    /** İzleyen oturumunu kapatır (paylaşım açık kalır). */
    private fun endSession(sendEnd: RemoteEnd.Reason?) {
        val s = session ?: return
        if (sendEnd != null) out += send(RemoteControl.END, RemoteControl.end(RemoteEnd(s.id, sendEnd)))
        for (c in conns.values.toList()) closeConn(c, StreamMsg.BYE_ENDED)
        session = null
        listen(false)
        if (projection == Projection.PENDING) {
            projection = Projection.NONE
            out += Command.ConsentNotice(false)
        }
        idleSinceMs = nowMs
    }

    private fun listen(on: Boolean) {
        if (on == listening) return
        listening = on
        out += Command.Listen(on, cfg.port)
    }

    private fun onScreen(w: Int, h: Int) {
        if (w == screenW && h == screenH) return
        screenW = w
        screenH = h
        if (encoding != null) {
            stopEncoder()
            startEncoder()
        }
    }

    private fun onAccepted(id: Int) {
        if (session == null || projection != Projection.ACTIVE) {
            out += Command.Close(id)
            return
        }
        conns[id] = Conn(id, nowMs)
    }

    private fun onBytes(e: Event.Bytes) {
        val c = conns[e.conn] ?: return
        c.lastRxMs = nowMs
        val wasAuth = c.endpoint.authenticated
        val msgs = try {
            c.endpoint.receive(e.bytes, 0, e.len)
        } catch (ex: StreamException) {
            closeConn(c, null)
            return
        }
        if (!wasAuth && c.endpoint.authenticated) onAuthenticated(c)
        if (c !== active) return
        for (m in msgs) {
            when (m) {
                is StreamMsg.Ack -> {
                    c.lastAckMs = nowMs
                    flow?.ack(m.bytes)
                }
                StreamMsg.KeyRequest -> requestKey()
                is StreamMsg.Bye -> { closeConn(c, null); return }
                else -> Unit
            }
        }
    }

    private fun onAuthenticated(c: Conn) {
        active?.let { if (it !== c) closeConn(it, StreamMsg.BYE_REPLACED) }
        active = c
        flow = FlowControl(encoding?.bitrate ?: session?.let { s -> CaptureGeometry.of(screenW, screenH, s.maxSide, attempt)?.bitrate } ?: CaptureGeometry.MIN_BPS)
            .also { it.reset(nowMs) }
        if (encoding == null) {
            startEncoder()
        } else {
            config?.let { c.endpoint.send(it) }
            requestKey(force = true)
        }
    }

    private fun onClosed(id: Int) {
        val c = conns.remove(id) ?: return
        if (c === active) detach()
    }

    private fun closeConn(c: Conn, bye: Int?) {
        if (bye != null && c.endpoint.authenticated) runCatching { c.endpoint.send(StreamMsg.Bye(bye)) }
        conns.remove(c.id)
        out += Command.Close(c.id)
        if (c === active) detach()
    }

    private fun detach() {
        active = null
        flow = null
        stopEncoder()
        idleSinceMs = nowMs
    }

    private fun startEncoder() {
        val s = session ?: return
        val g = CaptureGeometry.of(screenW, screenH, s.maxSide, attempt) ?: run { onUnavailable(); return }
        encoding = g
        config = null
        seq = 0
        out += Command.StartEncoder(g.copy(bitrate = flow?.bitrate ?: g.bitrate))
        flow?.reset(nowMs)
    }

    private fun stopEncoder() {
        if (encoding == null) return
        encoding = null
        config = null
        out += Command.StopEncoder
    }

    private fun onEncoderFailed() {
        if (encoding == null) return
        encoding = null
        config = null
        attempt++
        if (active != null) startEncoder()
    }

    private fun onUnavailable() {
        val s = session ?: return
        out += send(RemoteControl.OFFER, RemoteControl.offer(RemoteOffer(s.id, RemoteOffer.State.UNAVAILABLE)))
        endSession(sendEnd = null)
    }

    private fun onConfig(e: Event.EncoderConfig) {
        if (encoding == null) return
        val c = StreamMsg.Config(e.width, e.height, e.csd)
        config = c
        active?.endpoint?.send(c)
    }

    private fun onFrame(e: Event.EncoderFrame) {
        val c = active ?: return
        val f = flow ?: return
        if (config == null) return
        val d = f.admit(e.key, e.data.size, nowMs)
        if (d.requestKey) requestKey(force = true)
        d.bitrate?.let { out += Command.SetBitrate(it) }
        if (!d.send) return
        c.endpoint.send(StreamMsg.Frame(seq, e.ptsUs, e.key, e.data))
        seq = (seq + 1) and 0xFFFFFFFFL
        bytesThisSecond += e.data.size
    }

    private fun requestKey(force: Boolean = false) {
        if (!force && nowMs - lastKeyMs < cfg.keyRequestGapMs) return
        lastKeyMs = nowMs
        out += Command.RequestKey
    }

    private fun onTick() {
        for (c in conns.values.toList()) {
            when {
                !c.endpoint.authenticated && nowMs - c.openedMs >= cfg.authTimeoutMs -> closeConn(c, null)
                c.endpoint.authenticated && nowMs - c.lastRxMs >= cfg.deadAfterMs -> closeConn(c, null)
                c === active && nowMs - c.lastTxMs >= cfg.pingAfterMs -> c.endpoint.send(StreamMsg.Ping)
            }
        }
        flow?.tick(nowMs)?.let { out += Command.SetBitrate(it) }
        if (projection == Projection.PENDING && nowMs - pendingSinceMs >= cfg.consentWaitMs) onDenied()
        if (projection == Projection.ACTIVE && active == null && conns.isEmpty() && nowMs - idleSinceMs >= cfg.idleStopMs) {
            stopSharing(RemoteEnd.Reason.IDLE, stop = true)
        }
        if (nowMs - secondStartMs >= 1_000) {
            kbps = (bytesThisSecond * 8 / maxOf(1, nowMs - secondStartMs)).toInt()
            bytesThisSecond = 0
            secondStartMs = nowMs
        }
    }

    private var kbps = 0

    private fun emitView() {
        val v = when (projection) {
            Projection.NONE -> HostView.OFF
            Projection.PENDING -> HostView(HostView.State.CONSENT)
            Projection.ACTIVE -> HostView(HostView.State.SHARING, watched = active != null, kbps = if (active != null) kbps else 0)
        }
        if (v != lastView) {
            lastView = v
            out += Command.State(v)
        }
    }

    private fun send(type: String, payload: ByteArray) = Command.Send(type, payload)
}
