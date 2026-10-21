package com.kuryelog.app.remote

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.Surface
import com.kuryelog.app.core.Store
import com.kuryelog.app.link.LinkHub
import com.kuryelog.logic.link.Incoming
import com.kuryelog.logic.link.LinkRole
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.Transport
import com.kuryelog.logic.remote.HostMachine
import com.kuryelog.logic.remote.HostView
import com.kuryelog.logic.remote.RemoteConfig
import com.kuryelog.logic.remote.RemoteControl
import com.kuryelog.logic.remote.ViewerMachine
import com.kuryelog.logic.remote.ViewerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Uzaktan kullanımın süreçteki merkezi (REMOTE.md 1.7): saf [HostMachine] (paylaşan) ve [ViewerMachine] (izleyen) tek iş
 * parçacığında sürülür; komutları uygular: link iletisi ([LinkHub]), izin bildirimi ve onay penceresi, [CaptureService]
 * (MediaProjection + kodlayıcı), [StreamNet] soketleri, [Player] çözücü. `:app` açılışta [init] der. Cihazda doğrulanmadı.
 */
// Yalnız uygulama bağlamı tutulur.
@SuppressLint("StaticFieldLeak")
object RemoteHub {
    private val cfg = RemoteConfig()
    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "weem-remote").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var app: Context? = null
    private val host = HostMachine(cfg)
    private val viewer = ViewerMachine(cfg)
    private var ticker: ScheduledFuture<*>? = null
    /** Kodlayıcı nesli: durdurulan kodlayıcının geç gelen çıktısı atılır. */
    private var encoderGen = 0

    private val _hostView = MutableStateFlow(HostView.OFF)
    val hostView: StateFlow<HostView> = _hostView.asStateFlow()
    private val _viewerView = MutableStateFlow(ViewerView.IDLE)
    val viewerView: StateFlow<ViewerView> = _viewerView.asStateFlow()

    private val player = Player { post { viewer.handle(ViewerMachine.Event.DecoderError, now()); apply() } }

    private val hostNet = StreamNet("host", object : StreamNet.Listener {
        override fun accepted(conn: Int) = host(HostMachine.Event.Accepted(conn))
        override fun bytes(conn: Int, bytes: ByteArray) = host(HostMachine.Event.Bytes(conn, bytes))
        override fun closed(conn: Int) = host(HostMachine.Event.Closed(conn))
        override fun allowed(remote: String) = LinkHub.onLan(remote)
    })

    private val viewerNet = StreamNet("viewer", object : StreamNet.Listener {
        override fun opened(conn: Int) = viewer(ViewerMachine.Event.Opened(conn))
        override fun bytes(conn: Int, bytes: ByteArray) = viewer(ViewerMachine.Event.Bytes(conn, bytes))
        override fun closed(conn: Int) = viewer(ViewerMachine.Event.Closed(conn))
        override fun bind(s: Socket, host: String) = LinkHub.bindToLan(s, host)
    })

    /** `:app` açılışta, [LinkHub.init]'ten sonra. */
    fun init(ctx: Context) {
        app = ctx.applicationContext
        LinkHub.onRemote = ::incoming
        scope.launch {
            LinkHub.status.collect { s ->
                val wifiHost = s.peerHost.takeIf { s.state == LinkStatus.State.LINKED && s.transport == Transport.WIFI }
                viewer(ViewerMachine.Event.Link(wifiHost))
            }
        }
        // 2 telefon modu kapandı ya da eşleşme kalktı: paylaşım biter.
        scope.launch {
            combine(LinkHub.role, LinkHub.peer) { r, p -> r == LinkRole.OFF || p == null }.collect { gone ->
                if (gone) host(HostMachine.Event.LinkGone)
            }
        }
    }

    // --- ekrandan ---

    /** Öteki telefonun ekranını iste; [maxSide] bu ekranın en uzun kenarı (piksel). */
    fun startViewing(maxSide: Int) = viewer(ViewerMachine.Event.Start(maxSide))
    fun stopViewing() = viewer(ViewerMachine.Event.Stop)
    /** Bu telefonun ekranını paylaş (onay penceresi açılır). */
    fun requestShare() = host(HostMachine.Event.ShareRequested)
    fun stopShare() = host(HostMachine.Event.StopLocal)
    fun setSurface(s: Surface?) {
        player.setSurface(s)
    }

    // --- CaptureService / ConsentActivity'den ---

    internal fun consentGranted(w: Int, h: Int) = host(HostMachine.Event.ConsentGranted(w, h))
    internal fun consentDenied() = host(HostMachine.Event.ConsentDenied)
    internal fun projectionStopped() = host(HostMachine.Event.ProjectionStopped)
    internal fun screenSize(w: Int, h: Int) = host(HostMachine.Event.ScreenSize(w, h))
    internal fun encoderFailed() = host(HostMachine.Event.EncoderFailed)

    internal fun encoderConfig(gen: Int, w: Int, h: Int, csd: ByteArray) = post {
        if (gen == encoderGen) { host.handle(HostMachine.Event.EncoderConfig(w, h, csd), now()); apply() }
    }

    internal fun encoderFrame(gen: Int, data: ByteArray, ptsUs: Long, key: Boolean) = post {
        if (gen == encoderGen) { host.handle(HostMachine.Event.EncoderFrame(data, ptsUs, key), now()); apply() }
    }

    // --- iç ---

    private fun incoming(m: Incoming) {
        val age = System.currentTimeMillis() - m.localSentAtMs
        when (m.type) {
            RemoteControl.ASK -> RemoteControl.parseAsk(m.payload)?.let { host(HostMachine.Event.Ask(it, age)) }
            RemoteControl.OFFER -> RemoteControl.parseOffer(m.payload)?.let { viewer(ViewerMachine.Event.Offer(it)) }
            RemoteControl.END -> RemoteControl.parseEnd(m.payload)?.let { e ->
                post {
                    host.handle(HostMachine.Event.End(e), now())
                    viewer.handle(ViewerMachine.Event.End(e), now())
                    apply()
                }
            }
        }
    }

    private fun host(e: HostMachine.Event) = post { host.handle(e, now()); apply() }
    private fun viewer(e: ViewerMachine.Event) = post { viewer.handle(e, now()); apply() }

    private fun post(block: () -> Unit) {
        exec.execute {
            val a = app
            if (a == null) runCatching(block) else Store.guarded(a, "remote") { block() }
        }
    }

    private fun now() = android.os.SystemClock.elapsedRealtime()

    /** İki makinenin komutlarını uygular (makine iş parçacığında). */
    private fun apply() {
        val a = app ?: return
        // Bir komut makineye yeni olay verebilir (ör. kodlayıcı yok → EncoderFailed): kuyruk boşalana dek.
        while (true) {
            val hc = host.drain()
            val vc = viewer.drain()
            if (hc.isEmpty() && vc.isEmpty()) break
            for (c in hc) hostCommand(a, c)
            for (c in vc) viewerCommand(c)
        }
        val active = host.view.state != HostView.State.OFF ||
            viewer.view.state != ViewerView.State.IDLE && viewer.view.state != ViewerView.State.ENDED
        if (active && ticker == null) {
            ticker = exec.scheduleWithFixedDelay({ tick() }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
        } else if (!active) {
            ticker?.cancel(false)
            ticker = null
        }
    }

    private fun tick() {
        val a = app ?: return
        Store.guarded(a, "remote_tick") {
            host.handle(HostMachine.Event.Tick, now())
            viewer.handle(ViewerMachine.Event.Tick, now())
            apply()
        }
    }

    private fun hostCommand(a: Context, c: HostMachine.Command) {
        when (c) {
            is HostMachine.Command.Send -> LinkHub.publishReliable(c.type, c.payload, RemoteControl.TTL_MS)
            is HostMachine.Command.ConsentNotice -> if (c.show) RemoteNotices.showAsk(a) else RemoteNotices.cancelAsk(a)
            HostMachine.Command.OpenConsent -> runCatching {
                a.startActivity(Intent(a, ConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { host.handle(HostMachine.Event.ConsentDenied, now()) }
            is HostMachine.Command.Listen -> hostNet.listen(c.on, c.port)
            is HostMachine.Command.StartEncoder -> {
                encoderGen++
                val svc = CaptureService.current
                if (svc == null) host.handle(HostMachine.Event.EncoderFailed, now()) else svc.startEncoder(c.geometry, encoderGen)
            }
            HostMachine.Command.StopEncoder -> {
                encoderGen++
                CaptureService.current?.stopEncoder()
            }
            is HostMachine.Command.SetBitrate -> CaptureService.current?.setBitrate(c.bps)
            HostMachine.Command.RequestKey -> CaptureService.current?.requestKey()
            is HostMachine.Command.Write -> hostNet.write(c.conn, c.bytes)
            is HostMachine.Command.Close -> hostNet.close(c.conn)
            HostMachine.Command.StopProjection -> CaptureService.stop(a)
            is HostMachine.Command.State -> {
                val was = _hostView.value.watched
                _hostView.value = c.view
                if (was != c.view.watched) CaptureService.refresh(a, c.view.watched)
            }
        }
    }

    private fun viewerCommand(c: ViewerMachine.Command) {
        when (c) {
            is ViewerMachine.Command.Send -> LinkHub.publishReliable(c.type, c.payload, RemoteControl.TTL_MS)
            is ViewerMachine.Command.Connect -> viewerNet.connect(c.conn, c.host, c.port)
            is ViewerMachine.Command.Write -> viewerNet.write(c.conn, c.bytes)
            is ViewerMachine.Command.Close -> viewerNet.close(c.conn)
            is ViewerMachine.Command.Configure -> player.configure(c.width, c.height, c.sps, c.pps)
            is ViewerMachine.Command.Decode -> player.decode(c.data, c.ptsUs, c.key)
            ViewerMachine.Command.Release -> player.release()
            is ViewerMachine.Command.State -> _viewerView.value = c.view
        }
    }

    private const val TICK_MS = 250L
}
