package com.kuryelog.app.link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.kuryelog.app.core.Shift
import com.kuryelog.logic.core.FailureSink
import com.kuryelog.logic.link.BondedDevice
import com.kuryelog.logic.link.FileMessengerStore
import com.kuryelog.logic.link.Fingerprint
import com.kuryelog.logic.link.Incoming
import com.kuryelog.logic.link.LanTargets
import com.kuryelog.logic.link.LinkCommand
import com.kuryelog.logic.link.LinkConfig
import com.kuryelog.logic.link.LinkEvent
import com.kuryelog.logic.link.LinkProtocol
import com.kuryelog.logic.link.LinkStack
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.LocalIdentity
import com.kuryelog.logic.link.MessengerConfig
import com.kuryelog.logic.link.PairingQr
import com.kuryelog.logic.link.PeerId
import com.kuryelog.logic.link.PeerInfo
import com.kuryelog.logic.link.PeerRecord
import com.kuryelog.logic.link.QrResult
import com.kuryelog.logic.link.Transport
import com.kuryelog.logic.link.Trust
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 2 telefon bağlantısının Android kabuğu (LINK.md 1.1, 3.13): [LinkStack]'i (makine + `Messenger`) tek kilit altında sürer,
 * komutlarını TCP, UDP işareti ve klasik Bluetooth RFCOMM soketlerine çevirir; kimlik ve eş kaydı [LinkVault]'ta (Keystore
 * `WEEM.Link.v1` + `weem-link`), teslim zorunlu kuyruk `files/link/`'te. Karar veren kod `logic/link`'te.
 *
 * Ön plan servisi değildir: barındıran servis ve ekranlar (QR, S24) A8.3'te; [start]/[startPairing]/[pairWith]/[stop] onlar çağırır.
 * İş parçacığı güvenlidir; dinleyici çağrıları kilit dışında, olayı üreten iş parçacığında yapılır (uzun iş yapılmamalı; teslim
 * zorunlu iletinin onayı dinleyici döndükten sonra gider). Anahtar, jeton, QR metni ve yük loga ya da [failures]'a yazılmaz.
 * Cihazda doğrulanmadı (A8.1, A8.2); Bluetooth'un scooter BLE'sine etkisi LINK.md Bölüm 2 planıyla ölçülür.
 */
@SuppressLint("MissingPermission") // Bluetooth çağrıları btPermitted() denetiminden sonra; reddedilirse yedek kapalı sayılır.
class LinkRuntime(
    context: Context,
    private val listener: Listener = object : Listener {},
    /** Beklenmeyen hata (uygulama çökmesin; A8.3 çökme kaydına bağlar). */
    private val failures: FailureSink = FailureSink { _, _ -> },
) {
    interface Listener {
        fun onStatus(status: LinkStatus) {}

        /** Eşten gelen ileti (son durum ya da teslim zorunlu; tekrarlar atılmış). */
        fun onMessage(message: Incoming) {}

        /** Eşleştirme tamamlandı; kayıt saklandı. */
        fun onPaired(peer: PeerRecord) {}

        /** Eşleştirme penceresi eşleşmeden bitti (5 dk); eski eşleşme sürer. */
        fun onPairingExpired() {}

        /** Öteki telefon eşleşmeyi kaldırdı; bu telefonda da kayıt silindi, bağlantı durdu. */
        fun onUnpaired() {}

        /** Eşin doğrulanmış bilgisi: protokol ve uygulama sürümü, cihaz adı (LINK.md 3.10). */
        fun onPeerInfo(info: PeerInfo) {}

        fun onAcked(type: String, id: UUID) {}
        fun onExpired(type: String, id: UUID) {}

        /** Saat farkı (eşin saati − bu telefonunki) ve belirsizliği, ms. */
        fun onClock(offsetMs: Long, uncertaintyMs: Long) {}
    }

    private val app = context.applicationContext
    private val cfg = LinkConfig()
    private val lock = Any()
    private val linkVault = LinkVault(app)
    private val random = SecureRandom()
    private val stack = LinkStack(cfg, FileMessengerStore(File(app.filesDir, STORE_DIR)), secureRandom = random)
    private val io: ExecutorService = Executors.newCachedThreadPool { r -> Thread(r, "weem-link-io").apply { isDaemon = true } }
    private val timer: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "weem-link-timer").apply { isDaemon = true } }
    private val channels = ConcurrentHashMap<Int, Pipe>()
    private val inboundIds = AtomicInteger(0)
    private val net = LanWatch(app)
    private val service: UUID = UUID.fromString(LinkConfig.BT_SERVICE_UUID)

    private val wanted = mutableMapOf(Transport.WIFI to false, Transport.BT to false)
    private var tcpServer: ServerSocket? = null
    private var beaconSocket: DatagramSocket? = null
    private var btServer: BluetoothServerSocket? = null
    private var wake: ScheduledFuture<*>? = null
    private var poll: ScheduledFuture<*>? = null
    private var identity: LocalIdentity? = null
    private var listenOnly = false

    @Volatile private var running = false
    private val refreshLock = Any()
    private var lastLan: LinkEvent.Lan? = null
    private var lastBt: LinkEvent.Bt? = null

    private val _status = MutableStateFlow(LinkStatus.OFF)
    val status: StateFlow<LinkStatus> = _status

    /** Eşleşmiş telefon (yoksa `null`). */
    val peer: PeerRecord? get() = linkVault.vault.peer()

    /** Bu telefonun parmak izi (A8.3: iki ekranda karşılaştırılır); kimlik açılamazsa `null`. */
    fun fingerprint(): String? = synchronized(lock) { identityOrNull()?.let { Fingerprint.of(it.keys.public) } }

    /**
     * 2 telefon modunu eşleşmiş eşle açar ya da ayarlarını günceller; eşleşme yoksa `false`. [listenOnly] = scooter'a bağlı
     * telefon: Bluetooth'ta yalnız bekler.
     */
    fun start(listenOnly: Boolean = false): Boolean {
        val p = linkVault.vault.peer() ?: return false
        enable(Trust.Paired(p), listenOnly)
        return true
    }

    /** Veren telefon: 5 dk'lık eşleştirme penceresi açar (eski eş bu sürede bağlanamaz) ve QR metnini döner (yalnız ekranda). */
    fun startPairing(listenOnly: Boolean = false): String {
        val token = ByteArray(PairingQr.TOKEN_LENGTH).also(random::nextBytes)
        val me = synchronized(lock) { identityOrNull() } ?: throw IllegalStateException("link identity unavailable")
        enable(Trust.Issuer(token, now() + PairingQr.WINDOW_MS), listenOnly)
        val text = PairingQr(me.id, me.keys.public, token, me.info.deviceName).toUri()
        token.fill(0)
        return text
    }

    /** Okuyan telefon: taranan QR'la eşleştirmeyi başlatır (5 dk dener). Geçersiz ya da yeni sürüm QR'ında açılmaz. */
    fun pairWith(qrText: String, listenOnly: Boolean = false): QrResult {
        val r = PairingQr.parse(qrText)
        if (r is QrResult.Ok) enable(Trust.Scanner(r.qr, now() + PairingQr.WINDOW_MS), listenOnly)
        return r
    }

    /** Eşleştirmeden vazgeç: eşleşme varsa ona döner, yoksa durur. */
    fun cancelPairing() {
        if (!start(listenOnly)) stop()
    }

    /** Eşleşmeyi kaldırır (3.11): bağlıysa eşe söyler, kuyrukları ve kaydı siler, durur. Kimlik anahtarı kalır. */
    fun unpair() {
        locked { stack.unpair(now(), wall()) }
        linkVault.vault.removePeer()
        stop()
    }

    /** Son durum (tür başına en yenisi; LINK.md 3.7). Geçersiz tür, TTL ya da çok büyük yükte `IllegalArgumentException`. */
    fun publishLatest(type: String, payload: ByteArray, ttlMs: Long): UUID {
        var id: UUID? = null
        locked(rethrow = true) { id = stack.publishLatest(type, payload, ttlMs, now(), wall()) }
        return id!!
    }

    /** Teslim zorunlu: dönmeden `files/link/outbox/`'a yazılır (yazılamazsa istisna çağırana), onaya ya da TTL'ye kadar yeniden gider. */
    fun publishReliable(type: String, payload: ByteArray, ttlMs: Long = MessengerConfig.DAY_MS): UUID {
        var id: UUID? = null
        locked(rethrow = true) { id = stack.publishReliable(type, payload, ttlMs, now(), wall()) }
        return id!!
    }

    /** Adres yerel ağ arayüzlerinden birinin alt ağında mı (uzaktan kullanım akışı; REMOTE.md 1.4). */
    fun onLan(host: String): Boolean = LanTargets.onLan(host, net.interfaces())

    /** Uzaktan kullanım soketi: hedef Wi-Fi istemci ağındaysa o ağa bağlanır. */
    fun bindToLan(s: Socket, host: String) = net.bindIfWifi(s, host)

    fun stop() {
        feed(LinkEvent.Disable)
        synchronized(lock) {
            running = false
            poll?.cancel(false)
            poll = null
            wake?.cancel(false)
            wake = null
            net.stop()
        }
        synchronized(refreshLock) {
            lastLan = null
            lastBt = null
        }
    }

    /** [stop] ve iş parçacıklarını bırakır; nesne bundan sonra kullanılmaz. */
    fun shutdown() {
        stop()
        io.shutdownNow()
        timer.shutdownNow()
    }

    // --- yığın ---

    private fun enable(trust: Trust, listenOnly: Boolean) {
        synchronized(lock) {
            this.listenOnly = listenOnly
            if (!running) {
                running = true
                net.start { bg { refresh() } }
                poll = try {
                    timer.scheduleWithFixedDelay({ refresh() }, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS)
                } catch (e: RejectedExecutionException) {
                    null
                }
            }
        }
        // Soket açma işi `io`'da (dispatch → listen); burada yalnız makine güncellenir.
        locked {
            val me = identityOrNull() ?: return@locked
            stack.handle(LinkEvent.Enable(me, trust, linkVault.vault.btAddress(), listenOnly), now(), wall())
        }
        bg { refresh() }
    }

    /**
     * Kilit altında; kimlik ilk kullanımda Keystore'dan açılır ya da üretilir (açılamazsa yenisi: öteki telefon yeniden eşleşir).
     * Keystore hiç çalışmazsa `null` ve [failures]'a yalnız istisna türü gider (anahtar yok).
     */
    private fun identityOrNull(): LocalIdentity? {
        identity?.let { return it }
        return try {
            val id = PeerId.of(Shift.deviceId(app)) ?: throw IllegalStateException("device id")
            val keys = linkVault.vault.identity().keys
            LocalIdentity(id, keys, info(id)).also { identity = it }
        } catch (e: Exception) {
            failures.failure("link", e)
            null
        }
    }

    private fun info(id: PeerId): PeerInfo {
        val pi = try {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val code = (pi?.longVersionCode ?: 0L).coerceIn(0L, 0xffffffffL)
        return PeerInfo(id, LinkProtocol.MAJOR, LinkProtocol.MINOR, code, pi?.versionName.orEmpty(), Build.MODEL.orEmpty())
    }

    private fun feed(e: LinkEvent) = locked { stack.handle(e, now(), wall()) }

    /**
     * [block] ve komutların uygulanması kilit altında, dinleyiciler kilit dışında; gelen ileti verildiyse dinleyiciler döndükten
     * sonra `delivered` (teslim zorunlu onayı, LINK.md 3.8). [rethrow] = yayınlama: geçersiz girdi ya da disk hatası çağırana döner.
     */
    private fun locked(rethrow: Boolean = false, block: () -> Unit) {
        val callbacks = ArrayList<() -> Unit>()
        var messages = false
        synchronized(lock) {
            try {
                block()
            } catch (t: Exception) {
                if (rethrow) throw t
                failures.failure("link", t)
            }
            try {
                messages = dispatch(stack.drain(), callbacks)
            } catch (t: Exception) {
                failures.failure("link", t)
            }
        }
        callbacks.forEach { runCatching { it() } }
        if (messages) locked { stack.delivered(now(), wall()) }
    }

    /** Arka plan işi; [shutdown]'dan sonra gelen iş sessizce düşer (iş parçacığında yakalanmayan hata uygulamayı çökertir). */
    private fun bg(block: () -> Unit) {
        try {
            io.execute(Runnable { block() })
        } catch (e: RejectedExecutionException) {
            // Kapatıldı.
        }
    }

    private fun later(delayMs: Long, block: () -> Unit): ScheduledFuture<*>? = try {
        timer.schedule(Runnable { block() }, delayMs, TimeUnit.MILLISECONDS)
    } catch (e: RejectedExecutionException) {
        null
    }

    /**
     * Kilit altında; soket işi iş parçacıklarına gider, makineye geri bildirim her zaman `io` üstünden (yeniden giriş yok).
     * Kalıcı yan etkiler (eş kaydı, Bluetooth adresi) burada; dönüş = gelen ileti var mı.
     */
    private fun dispatch(cmds: List<LinkCommand>, callbacks: MutableList<() -> Unit>): Boolean {
        var messages = false
        for (c in cmds) {
            when (c) {
                is LinkCommand.Listen -> {
                    val t = c.transport
                    wanted[t] = c.on
                    if (c.on) {
                        bg { synchronized(lock) { if (running && wanted[t] == true) listen(t, true) } }
                    } else {
                        listen(t, false)
                    }
                }
                is LinkCommand.Connect -> if (c.transport == Transport.WIFI) connectTcp(c) else connectBt(c)
                is LinkCommand.Write -> channels[c.channel]?.write(c.bytes)
                is LinkCommand.Close -> channels.remove(c.channel)?.close()
                is LinkCommand.SendBeacon -> {
                    val bytes = c.bytes
                    val port = c.port
                    bg { net.broadcast(bytes, port) }
                }
                is LinkCommand.Deliver -> Unit // Messenger'lı yığında çıkmaz
                is LinkCommand.Status -> {
                    val s = c.status
                    _status.value = s
                    callbacks += { listener.onStatus(s) }
                }
                is LinkCommand.Paired -> {
                    val p = c.peer
                    persist { linkVault.vault.savePeer(p) }
                    callbacks += { listener.onPaired(p) }
                }
                is LinkCommand.Peer -> {
                    val i = c.info
                    callbacks += { listener.onPeerInfo(i) }
                }
                LinkCommand.PairingExpired -> callbacks += {
                    cancelPairing()
                    listener.onPairingExpired()
                }
                is LinkCommand.BtPeerLearned -> {
                    val a = c.address
                    persist { linkVault.vault.saveBtAddress(a) }
                }
                is LinkCommand.Wake -> schedule(c.atMs)
                is LinkCommand.Message -> {
                    messages = true
                    val m = c.message
                    callbacks += { listener.onMessage(m) }
                }
                is LinkCommand.Acked -> {
                    val a = c
                    callbacks += { listener.onAcked(a.type, a.id) }
                }
                is LinkCommand.Expired -> {
                    val x = c
                    callbacks += { listener.onExpired(x.type, x.id) }
                }
                is LinkCommand.Clock -> {
                    val k = c
                    callbacks += { listener.onClock(k.offsetMs, k.uncertaintyMs) }
                }
                LinkCommand.Unpaired -> {
                    persist { linkVault.vault.removePeer() }
                    callbacks += {
                        stop()
                        listener.onUnpaired()
                    }
                }
            }
        }
        return messages
    }

    /** Kayıt yazımı başarısız olsa da partinin kalan komutları (soket yazmaları) uygulanır. */
    private fun persist(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            failures.failure("link", e)
        }
    }

    private fun schedule(atMs: Long?) {
        wake?.cancel(false)
        wake = null
        if (atMs == null || !running) return
        val delay = (atMs - now()).coerceAtLeast(0)
        wake = later(delay) { feed(LinkEvent.Tick) }
    }

    /** Ağ ve Bluetooth özetini okur; değiştiyse makineye verir (5 sn'de bir ve Wi-Fi değişince). */
    private fun refresh() {
        if (!running) return
        val lan: LinkEvent.Lan
        val bt: LinkEvent.Bt
        try {
            lan = net.lan()
            bt = btSnapshot()
        } catch (t: RuntimeException) {
            failures.failure("link", t)
            return
        }
        val events = ArrayList<LinkEvent>(2)
        synchronized(refreshLock) {
            if (lan != lastLan) {
                lastLan = lan
                events += lan
            }
            if (bt != lastBt) {
                lastBt = bt
                events += bt
            }
        }
        events.forEach(::feed)
    }

    // --- dinleme ---

    private fun listen(t: Transport, on: Boolean) {
        if (t == Transport.WIFI) {
            if (on) startTcp() else stopTcp()
        } else {
            if (on) startBt() else stopBt()
        }
    }

    private fun retryListen(t: Transport) {
        later(RETRY_LISTEN_MS) { synchronized(lock) { if (running && wanted[t] == true) listen(t, true) } }
    }

    private fun startTcp() {
        // TCP ya da UDP yuvası açılamazsa ikisi için tek yeniden deneme (açık olan yeniden açılmaz).
        var retry = false
        if (tcpServer == null) {
            val server = try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(cfg.tcpPort))
                }
            } catch (e: IOException) {
                null
            }
            if (server == null) {
                retry = true
            } else {
                tcpServer = server
                bg { acceptTcp(server) }
            }
        }
        if (beaconSocket == null) {
            val udp = try {
                DatagramSocket(null as SocketAddress?).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(cfg.beaconPort))
                }
            } catch (e: IOException) {
                null
            }
            beaconSocket = udp
            if (udp != null) bg { receiveBeacons(udp) } else retry = true
        }
        if (retry) retryListen(Transport.WIFI)
    }

    private fun stopTcp() {
        tcpServer?.closeQuietly()
        tcpServer = null
        beaconSocket?.closeQuietly()
        beaconSocket = null
    }

    private fun acceptTcp(server: ServerSocket) {
        while (!server.isClosed) {
            val s = try {
                server.accept()
            } catch (e: IOException) {
                break
            }
            val remote = s.inetAddress?.hostAddress ?: ""
            if (!LanTargets.onLan(remote, net.interfaces())) {
                // Yalnız hotspot/Wi-Fi alt ağı; mobil veri tarafından gelen bağlantı kabul edilmez.
                s.closeQuietly()
                continue
            }
            runCatching { s.tcpNoDelay = true }
            val id = -inboundIds.incrementAndGet()
            val pipe = Pipe(id, s)
            try {
                pipe.attach(s.getInputStream(), s.getOutputStream())
            } catch (e: IOException) {
                s.closeQuietly()
                continue
            }
            channels[id] = pipe
            feed(LinkEvent.Accepted(id, Transport.WIFI, remote))
            pipe.startReading()
        }
        synchronized(lock) {
            if (tcpServer === server) {
                tcpServer = null
                if (running && wanted[Transport.WIFI] == true) retryListen(Transport.WIFI)
            }
        }
    }

    private fun receiveBeacons(sock: DatagramSocket) {
        val buf = ByteArray(BEACON_BUF)
        while (!sock.isClosed) {
            val p = DatagramPacket(buf, buf.size)
            try {
                sock.receive(p)
            } catch (e: IOException) {
                break
            }
            val from = p.address?.hostAddress ?: continue
            if (!LanTargets.onLan(from, net.interfaces())) continue
            feed(LinkEvent.BeaconIn(from, buf.copyOf(p.length), p.length))
        }
        // Okuma hatayla bittiyse ölü yuva bırakılmaz: sonraki dinleme yeniden açar (acceptTcp ile aynı).
        synchronized(lock) {
            if (beaconSocket === sock) {
                sock.closeQuietly()
                beaconSocket = null
                if (running && wanted[Transport.WIFI] == true) retryListen(Transport.WIFI)
            }
        }
    }

    private fun startBt() {
        if (btServer != null) return
        val adapter = btAdapter()
        val server = if (adapter == null || !btPermitted()) {
            null
        } else {
            try {
                adapter.listenUsingRfcommWithServiceRecord(LinkConfig.BT_SERVICE_NAME, service)
            } catch (e: Exception) {
                null
            }
        }
        if (server == null) {
            retryListen(Transport.BT)
            return
        }
        btServer = server
        bg { acceptBt(server) }
    }

    private fun stopBt() {
        btServer?.closeQuietly()
        btServer = null
    }

    private fun acceptBt(server: BluetoothServerSocket) {
        while (true) {
            val s = try {
                server.accept()
            } catch (e: IOException) {
                break
            }
            val id = -inboundIds.incrementAndGet()
            val pipe = Pipe(id, s)
            try {
                pipe.attach(s.inputStream, s.outputStream)
            } catch (e: IOException) {
                s.closeQuietly()
                continue
            }
            channels[id] = pipe
            feed(LinkEvent.Accepted(id, Transport.BT, s.remoteDevice.address))
            pipe.startReading()
        }
        synchronized(lock) {
            if (btServer === server) {
                btServer = null
                if (running && wanted[Transport.BT] == true) retryListen(Transport.BT)
            }
        }
    }

    // --- bağlanma ---

    private fun connectTcp(c: LinkCommand.Connect) {
        val id = c.channel
        val s = Socket()
        val pipe = Pipe(id, s)
        channels[id] = pipe
        bg {
            try {
                net.bindIfWifi(s, c.host)
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(c.host, c.port), cfg.tcpConnectTimeoutMs.toInt())
                opened(pipe, s.getInputStream(), s.getOutputStream())
            } catch (e: Exception) {
                failed(pipe, e)
            }
        }
    }

    private fun connectBt(c: LinkCommand.Connect) {
        val id = c.channel
        val adapter = btAdapter()
        if (adapter == null || !btPermitted()) {
            bg { feed(LinkEvent.Failed(id, "bt_unavailable")) }
            return
        }
        val socket = try {
            adapter.getRemoteDevice(c.host).createRfcommSocketToServiceRecord(service)
        } catch (e: Exception) {
            bg { feed(LinkEvent.Failed(id, e.javaClass.simpleName)) }
            return
        }
        val pipe = Pipe(id, socket)
        channels[id] = pipe
        bg {
            try {
                socket.connect()
                opened(pipe, socket.inputStream, socket.outputStream)
            } catch (e: Exception) {
                failed(pipe, e)
            }
        }
    }

    private fun opened(pipe: Pipe, input: InputStream, output: OutputStream) {
        if (pipe.isClosed) {
            pipe.closeNow()
            return
        }
        pipe.attach(input, output)
        feed(LinkEvent.Opened(pipe.id))
        pipe.startReading()
    }

    private fun failed(pipe: Pipe, e: Exception) {
        pipe.closeNow()
        channels.remove(pipe.id, pipe)
        feed(LinkEvent.Failed(pipe.id, e.javaClass.simpleName))
    }

    // --- Bluetooth durumu ---

    private fun btAdapter(): BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter

    private fun btPermitted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun btSnapshot(): LinkEvent.Bt {
        val adapter = btAdapter()
        if (adapter == null || !btPermitted()) return LinkEvent.Bt(false, emptyList())
        val on = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!on) return LinkEvent.Bt(false, emptyList())
        val bonded = runCatching {
            adapter.bondedDevices.orEmpty().map { d ->
                BondedDevice(d.address, d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE)
            }
        }.getOrDefault(emptyList())
        return LinkEvent.Bt(true, bonded.sortedBy { it.address })
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun wall() = System.currentTimeMillis()

    /** Bir kanalın soketi: yazma kuyruğu (sırayla, `BYE` sonra kapanış) ve okuma döngüsü. */
    private inner class Pipe(val id: Int, private val socket: Closeable) {
        private val queue = LinkedBlockingQueue<ByteArray>()

        @Volatile private var input: InputStream? = null

        @Volatile var isClosed = false
            private set

        fun attach(i: InputStream, o: OutputStream) {
            input = i
            bg { writeLoop(o) }
        }

        fun startReading() {
            val i = input ?: return
            bg { readLoop(i) }
        }

        fun write(bytes: ByteArray) {
            if (!isClosed) queue.offer(bytes)
        }

        /** Kuyruktakiler yazılır, sonra soket kapanır; henüz bağlanmadıysa hemen kapanır (bağlanma iptal). */
        fun close() {
            if (isClosed) return
            isClosed = true
            if (input == null) socket.closeQuietly() else queue.offer(CLOSE)
        }

        fun closeNow() {
            isClosed = true
            socket.closeQuietly()
        }

        private fun writeLoop(out: OutputStream) {
            try {
                while (true) {
                    val b = queue.take()
                    if (b === CLOSE) break
                    out.write(b)
                    if (queue.isEmpty()) out.flush()
                }
                out.flush()
            } catch (e: Exception) {
                // Okuma döngüsü kopuşu makineye bildirir.
            } finally {
                socket.closeQuietly()
            }
        }

        private fun readLoop(i: InputStream) {
            val buf = ByteArray(READ_BUF)
            var reason = "eof"
            try {
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    if (n > 0) feed(LinkEvent.Bytes(id, buf.copyOf(n)))
                }
            } catch (e: Exception) {
                reason = e.javaClass.simpleName
            }
            isClosed = true
            queue.offer(CLOSE)
            socket.closeQuietly()
            channels.remove(id, this)
            feed(LinkEvent.Closed(id, reason))
        }
    }

    private companion object {
        const val POLL_MS = 5_000L
        const val STORE_DIR = "link"
        const val RETRY_LISTEN_MS = 5_000L
        const val READ_BUF = 16 * 1024
        const val BEACON_BUF = 64
        val CLOSE = ByteArray(0)
    }
}

private fun Closeable.closeQuietly() {
    runCatching { close() }
}
