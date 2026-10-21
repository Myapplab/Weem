package com.kuryelog.logic.remote

import java.security.SecureRandom

/**
 * İki telefonun uzaktan kullanım tarafları, sahte link ve sahte akışla (REMOTE.md 1.8). Link iletileri anında karşıya gider
 * ([linkUp] false ise düşer); akış bağlantıları bellek içi borudur. Kodlayıcı ve çözücü komutları kaydedilir; kareleri test verir.
 */
class RemoteRig(val cfg: RemoteConfig = RemoteConfig()) {
    private val random = SecureRandom()
    val host = HostMachine(cfg, random)
    val viewer = ViewerMachine(cfg, random)
    var now = 0L
    var linkUp = true
    /** Paylaşan akış bağlantılarını kabul ediyor mu (dinliyor). */
    var listening = false
        private set

    val hostCommands = mutableListOf<HostMachine.Command>()
    val viewerCommands = mutableListOf<ViewerMachine.Command>()
    val linkLog = mutableListOf<String>()

    /** viewer bağlantı no → paylaşan bağlantı no. */
    private val v2h = HashMap<Int, Int>()
    private val h2v = HashMap<Int, Int>()
    private var nextHostConn = 100
    /** Akış baytlarını tutar (true: teslimi bekletir). */
    var holdStream = false
    private val heldToViewer = ArrayList<Pair<Int, ByteArray>>()
    /** Sahte kodlayıcının yapılandırma tamponu (SPS + PPS). */
    val csd = byteArrayOf(0, 0, 0, 1, 0x67, 1, 2, 0, 0, 0, 1, 0x68, 3)
    var encoderOn: Geometry? = null
        private set
    var keyRequests = 0
        private set
    var autoConfig = true

    fun host(e: HostMachine.Event) {
        host.handle(e, now)
        pump()
    }

    fun viewer(e: ViewerMachine.Event) {
        viewer.handle(e, now)
        pump()
    }

    fun advance(ms: Long, step: Long = 250) {
        val end = now + ms
        while (now < end) {
            now = minOf(end, now + step)
            host.handle(HostMachine.Event.Tick, now)
            viewer.handle(ViewerMachine.Event.Tick, now)
            pump()
        }
    }

    fun frame(key: Boolean, size: Int = 1000, ptsUs: Long = now * 1000) = host(HostMachine.Event.EncoderFrame(ByteArray(size) { 0x41 }, ptsUs, key))

    /** Paylaşan → izleyen bekletilen baytları teslim eder. */
    fun release() {
        val held = heldToViewer.toList()
        heldToViewer.clear()
        for ((c, b) in held) viewer.handle(ViewerMachine.Event.Bytes(c, b), now)
        pump()
    }

    /** Akışı iki uçtan da koparır (Wi-Fi düştü). */
    fun breakStream() {
        for ((v, h) in v2h.toMap()) {
            v2h.remove(v)
            h2v.remove(h)
            host.handle(HostMachine.Event.Closed(h), now)
            viewer.handle(ViewerMachine.Event.Closed(v), now)
        }
        pump()
    }

    /** Paylaşan telefondaki süreç öldü: akış kopar, dinleme biter (link iletileri [linkUp]'a bağlı). */
    fun killHost() {
        breakStream()
        listening = false
    }

    val hostView get() = host.view
    val viewerView get() = viewer.view
    val decoded get() = viewerCommands.filterIsInstance<ViewerMachine.Command.Decode>()

    private fun pump() {
        var again = true
        while (again) {
            again = false
            val hc = host.drain()
            val vc = viewer.drain()
            if (hc.isNotEmpty() || vc.isNotEmpty()) again = true
            hostCommands += hc
            viewerCommands += vc
            for (c in hc) hostCmd(c)
            for (c in vc) viewerCmd(c)
        }
    }

    private fun hostCmd(c: HostMachine.Command) {
        when (c) {
            is HostMachine.Command.Send -> deliverToViewer(c.type, c.payload)
            is HostMachine.Command.Listen -> listening = c.on
            is HostMachine.Command.StartEncoder -> {
                encoderOn = c.geometry
                if (autoConfig) host.handle(HostMachine.Event.EncoderConfig(c.geometry.width, c.geometry.height, csd), now)
            }
            HostMachine.Command.StopEncoder -> encoderOn = null
            HostMachine.Command.RequestKey -> keyRequests++
            is HostMachine.Command.Write -> h2v[c.conn]?.let { v ->
                if (holdStream) heldToViewer += v to c.bytes else viewer.handle(ViewerMachine.Event.Bytes(v, c.bytes), now)
            }
            is HostMachine.Command.Close -> h2v.remove(c.conn)?.let { v ->
                v2h.remove(v)
                viewer.handle(ViewerMachine.Event.Closed(v), now)
            }
            else -> Unit
        }
    }

    private fun viewerCmd(c: ViewerMachine.Command) {
        when (c) {
            is ViewerMachine.Command.Send -> deliverToHost(c.type, c.payload)
            is ViewerMachine.Command.Connect -> {
                if (!listening || c.port != cfg.port) {
                    viewer.handle(ViewerMachine.Event.Closed(c.conn), now)
                } else {
                    val h = nextHostConn++
                    v2h[c.conn] = h
                    h2v[h] = c.conn
                    host.handle(HostMachine.Event.Accepted(h), now)
                    viewer.handle(ViewerMachine.Event.Opened(c.conn), now)
                }
            }
            is ViewerMachine.Command.Write -> v2h[c.conn]?.let { h -> host.handle(HostMachine.Event.Bytes(h, c.bytes), now) }
            is ViewerMachine.Command.Close -> v2h.remove(c.conn)?.let { h ->
                h2v.remove(h)
                host.handle(HostMachine.Event.Closed(h), now)
            }
            else -> Unit
        }
    }

    /** Dışarıdan (sahte saldırgan) paylaşana bağlantı: paylaşan bağlantı no. */
    fun rogueConnect(): Int {
        val h = nextHostConn++
        host.handle(HostMachine.Event.Accepted(h), now)
        pump()
        return h
    }

    private fun deliverToViewer(type: String, payload: ByteArray) {
        linkLog += "h>$type"
        if (!linkUp) return
        when (type) {
            RemoteControl.OFFER -> RemoteControl.parseOffer(payload)?.let { viewer.handle(ViewerMachine.Event.Offer(it), now) }
            RemoteControl.END -> RemoteControl.parseEnd(payload)?.let { viewer.handle(ViewerMachine.Event.End(it), now) }
        }
    }

    private fun deliverToHost(type: String, payload: ByteArray) {
        linkLog += "v>$type"
        if (!linkUp) return
        when (type) {
            RemoteControl.ASK -> RemoteControl.parseAsk(payload)?.let { host.handle(HostMachine.Event.Ask(it, 0), now) }
            RemoteControl.END -> RemoteControl.parseEnd(payload)?.let { host.handle(HostMachine.Event.End(it), now) }
        }
    }
}
