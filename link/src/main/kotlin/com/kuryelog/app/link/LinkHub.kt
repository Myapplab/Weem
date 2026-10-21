package com.kuryelog.app.link

import android.annotation.SuppressLint
import android.content.Context
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.FailureSink
import com.kuryelog.logic.link.Fingerprint
import com.kuryelog.logic.link.IdentityVault
import com.kuryelog.logic.link.Incoming
import com.kuryelog.logic.link.LinkPort
import com.kuryelog.logic.link.LinkRole
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.PairingQr
import com.kuryelog.logic.link.PeerInfo
import com.kuryelog.logic.link.PeerRecord
import com.kuryelog.logic.link.PhoneBridge
import com.kuryelog.logic.link.QrResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 2 telefon modunun süreçteki tek merkezi (LINK.md 4.7): rol (`weem-link` → `role`), [LinkRuntime] (bağlantı, eşleştirme) ve role
 * göre bir [PhoneBridge]. `:app` açılışta [init] ile yerel modülleri ([localFor]) ve rol değişince yeniden bağlama kancasını
 * ([onBridge]) verir. Mod açık ve eşleşmişken (ya da eşleştirme penceresi açıkken) [LinkService] süreci ön planda tutar.
 * `:link` yalnız `:core`'u görür: modüller arası bağ `:app`'te. Cihazda doğrulanmadı.
 */
// Yalnız uygulama bağlamı tutulur (Activity değil): sızıntı yok.
@SuppressLint("StaticFieldLeak")
object LinkHub {
    sealed interface Notice {
        class Paired(val name: String) : Notice
        object PairingExpired : Notice
        object UnpairedByPeer : Notice
    }

    @Volatile private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runtime: LinkRuntime? = null
    private var tickJob: Job? = null

    /** Bu telefonun yerel modülleri, role göre (`:app` verir). */
    @Volatile var localFor: (LinkRole) -> PhoneBridge.Local = { PhoneBridge.Local() }
    /** Köprü değişti (rol değişimi ya da açılış): `:app` portları yeniden bağlar. Kapalıyken `null`. */
    @Volatile var onBridge: (PhoneBridge?) -> Unit = {}
    /** `remote.*` iletileri (A9.1, REMOTE.md 1.3): köprüye değil `:remote`'a gider. */
    @Volatile var onRemote: (Incoming) -> Unit = {}

    private val _role = MutableStateFlow(LinkRole.OFF)
    val role: StateFlow<LinkRole> = _role.asStateFlow()
    private val _status = MutableStateFlow(LinkStatus.OFF)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()
    private val _bridge = MutableStateFlow<PhoneBridge?>(null)
    val bridge: StateFlow<PhoneBridge?> = _bridge.asStateFlow()
    private val _peerInfo = MutableStateFlow<PeerInfo?>(null)
    val peerInfo: StateFlow<PeerInfo?> = _peerInfo.asStateFlow()
    private val _peer = MutableStateFlow<PeerRecord?>(null)
    val peer: StateFlow<PeerRecord?> = _peer.asStateFlow()
    /** Açık eşleştirme penceresinin sonu (duvar saati); yoksa null. */
    private val _pairingUntil = MutableStateFlow<Long?>(null)
    val pairingUntil: StateFlow<Long?> = _pairingUntil.asStateFlow()
    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 8)
    val notices: SharedFlow<Notice> = _notices.asSharedFlow()

    private fun failures(): FailureSink = app?.let(Store::sink) ?: FailureSink { _, _ -> }

    /** `:app` açılışta (bir kez): rolü okur, köprüyü kurar, mod açık ve eşleşmişse bağlantıyı başlatır. */
    fun init(ctx: Context, localFor: (LinkRole) -> PhoneBridge.Local, onBridge: (PhoneBridge?) -> Unit) {
        val a = ctx.applicationContext
        app = a
        this.localFor = localFor
        this.onBridge = onBridge
        _role.value = LinkRole.of(prefs(a).getString(LinkRole.PREF_KEY, null))
        Store.guarded(a, "link_init") { _peer.value = runtime(a).peer }
        apply(a)
    }

    // `weem-link` yazımı eşzamanlı (LINK.md 3.12).
    @SuppressLint("ApplySharedPref")
    fun setRole(ctx: Context, role: LinkRole) {
        val a = ctx.applicationContext
        prefs(a).edit().putString(LinkRole.PREF_KEY, role.key).commit()
        _role.value = role
        apply(a)
    }

    /** Veren telefon: QR metni (yalnız ekranda; loga yazılmaz). Kimlik açılamazsa null. */
    fun startPairing(ctx: Context): String? {
        val a = ctx.applicationContext
        val text = try { runtime(a).startPairing(_role.value.listenOnly) } catch (e: Exception) { failures().failure("link_pairing", e); return null }
        _pairingUntil.value = System.currentTimeMillis() + PairingQr.WINDOW_MS
        LinkService.set(a, true)
        return text
    }

    /** Okuyan telefon: taranan QR. */
    fun pairWith(ctx: Context, text: String): QrResult {
        val a = ctx.applicationContext
        val r = runtime(a).pairWith(text, _role.value.listenOnly)
        if (r is QrResult.Ok) {
            _pairingUntil.value = System.currentTimeMillis() + PairingQr.WINDOW_MS
            LinkService.set(a, true)
        }
        return r
    }

    fun cancelPairing(ctx: Context) {
        val a = ctx.applicationContext
        _pairingUntil.value = null
        runtime(a).cancelPairing()
        apply(a)
    }

    fun unpair(ctx: Context) {
        val a = ctx.applicationContext
        runtime(a).unpair()
        _peer.value = null
        _peerInfo.value = null
        apply(a)
    }

    /** Uzaktan kullanım denetim iletisi (teslim zorunlu); bağlantı kapalıysa null. */
    fun publishReliable(type: String, payload: ByteArray, ttlMs: Long): UUID? =
        runtime?.takeIf { _role.value != LinkRole.OFF }?.let { rt -> runCatching { rt.publishReliable(type, payload, ttlMs) }.getOrNull() }

    /** Adres yerel ağ (hotspot/Wi-Fi) alt ağında mı (REMOTE.md 1.4: akış yalnız oradan kabul edilir). */
    fun onLan(host: String): Boolean = runtime?.onLan(host) ?: false

    /** Hedef Wi-Fi istemci ağındaysa soketi o ağa bağlar (mobil veri varsayılan ağ olsa da). */
    fun bindToLan(s: java.net.Socket, host: String) {
        runtime?.bindToLan(s, host)
    }

    fun fingerprint(ctx: Context): String? = runCatching { runtime(ctx.applicationContext).fingerprint() }.getOrNull()
    fun peerFingerprint(): String? = _peer.value?.let { Fingerprint.of(it.publicKey) }

    /** Rol ve eşleşmeye göre köprüyü kurar, bağlantıyı açar/kapatır, servisi açar/kapatır. */
    @Synchronized
    private fun apply(a: Context) {
        val role = _role.value
        val rt = runtime(a)
        val old = _bridge.value
        val b = if (role == LinkRole.OFF) null else if (old?.role == role) old else PhoneBridge(role, port(rt), System::currentTimeMillis, scope, localFor(role), failures())
        if (b !== old) {
            _bridge.value = b
            Store.guarded(a, "link_bridge") { onBridge(b) }
            b?.linkChanged(_status.value.state == LinkStatus.State.LINKED)
        }
        val pairing = _pairingUntil.value?.let { it > System.currentTimeMillis() } == true
        val on = when {
            pairing -> true
            role == LinkRole.OFF -> { rt.stop(); false }
            else -> Store.guarded(a, "link_start") { rt.start(role.listenOnly) } == true
        }
        LinkService.set(a, on)
        tickJob?.cancel()
        tickJob = if (b == null) null else scope.launch {
            while (true) {
                Store.guarded(a, "link_tick") { b.tick() }
                delay(1_000)
            }
        }
    }

    private fun port(rt: LinkRuntime) = object : LinkPort {
        override fun linked() = _status.value.state == LinkStatus.State.LINKED
        override fun publishLatest(type: String, payload: ByteArray, ttlMs: Long): UUID = rt.publishLatest(type, payload, ttlMs)
        override fun publishReliable(type: String, payload: ByteArray, ttlMs: Long): UUID = rt.publishReliable(type, payload, ttlMs)
    }

    @Synchronized
    private fun runtime(a: Context): LinkRuntime = runtime ?: LinkRuntime(a, Listener, failures()).also { runtime = it }

    private object Listener : LinkRuntime.Listener {
        override fun onStatus(status: LinkStatus) {
            val was = _status.value.state == LinkStatus.State.LINKED
            _status.value = status
            val now = status.state == LinkStatus.State.LINKED
            if (was != now) _bridge.value?.linkChanged(now)
            app?.let { LinkService.refresh(it) }
        }

        override fun onMessage(message: Incoming) {
            if (message.type.startsWith(REMOTE_PREFIX)) {
                app?.let { a -> Store.guarded(a, "link_remote") { onRemote(message) } }
                return
            }
            _bridge.value?.incoming(message.type, message.payload)
        }

        override fun onPaired(peer: PeerRecord) {
            _peer.value = peer
            _pairingUntil.value = null
            _notices.tryEmit(Notice.Paired(peer.name))
            app?.let { a -> scope.launch { apply(a) } }
        }

        override fun onPairingExpired() {
            _pairingUntil.value = null
            _notices.tryEmit(Notice.PairingExpired)
            app?.let { a -> scope.launch { apply(a) } }
        }

        override fun onUnpaired() {
            _peer.value = null
            _peerInfo.value = null
            _notices.tryEmit(Notice.UnpairedByPeer)
            app?.let { a -> scope.launch { apply(a) } }
        }

        override fun onPeerInfo(info: PeerInfo) { _peerInfo.value = info }
        override fun onAcked(type: String, id: UUID) { _bridge.value?.acked(type, id) }
        override fun onClock(offsetMs: Long, uncertaintyMs: Long) { _bridge.value?.clock(offsetMs) }
    }

    private const val REMOTE_PREFIX = "remote."

    private fun prefs(a: Context) = a.getSharedPreferences(IdentityVault.PREFS, Context.MODE_PRIVATE)
}
