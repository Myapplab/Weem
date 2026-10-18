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
import com.kuryelog.logic.core.FailureSink
import com.kuryelog.logic.link.BondedDevice
import com.kuryelog.logic.link.LinkCommand
import com.kuryelog.logic.link.LinkConfig
import com.kuryelog.logic.link.LinkEvent
import com.kuryelog.logic.link.LinkMachine
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.PeerId
import com.kuryelog.logic.link.SendResult
import com.kuryelog.logic.link.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
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
 * 2 telefon taşımasının Android kabuğu (LINK.md 1.1): [LinkMachine]'i tek kilit altında sürer, komutlarını TCP, UDP işareti ve
 * klasik Bluetooth RFCOMM soketlerine çevirir. Karar veren kod `logic/link`'te; burada yalnız soket, zamanlayıcı ve ağ okuması.
 *
 * Ön plan servisi değildir: barındıran servis (A8.3) [start]/[stop] çağırır, [shutdown] ile bırakır. İş parçacığı güvenlidir;
 * dinleyici çağrıları kilit dışında, olayı üreten iş parçacığında yapılır (uzun iş yapılmamalı).
 * Cihazda doğrulanmadı (A8.1); Bluetooth'un scooter BLE'sine etkisi LINK.md Bölüm 2 planıyla ölçülür.
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
        fun onData(payload: ByteArray, transport: Transport) {}
        fun onPeerSeen(peer: PeerId) {}
        fun onBtPeerLearned(address: String) {}
    }

    private val app = context.applicationContext
    private val cfg = LinkConfig()
    private val lock = Any()
    private val machine = LinkMachine(cfg)
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

    @Volatile private var running = false
    private val refreshLock = Any()
    private var lastLan: LinkEvent.Lan? = null
    private var lastBt: LinkEvent.Bt? = null

    private val _status = MutableStateFlow(LinkStatus.OFF)
    val status: StateFlow<LinkStatus> = _status

    /**
     * 2 telefon modunu açar ya da ayarlarını günceller. [listenOnly] = scooter'a bağlı telefon: Bluetooth'ta yalnız bekler.
     * [btPeerAddress] = daha önce öğrenilen eş adresi ([Listener.onBtPeerLearned]).
     */
    fun start(self: PeerId, expectedPeer: PeerId? = null, btPeerAddress: String? = null, listenOnly: Boolean = false) {
        synchronized(lock) {
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
        feed(LinkEvent.Enable(self, expectedPeer, btPeerAddress, listenOnly))
        bg { refresh() }
    }

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

    /** Opak yük (A8.2 zarfı) etkin kanaldan gider; Bluetooth'ta ≤ 4 KiB. */
    fun send(payload: ByteArray): SendResult {
        val result: SendResult
        var callbacks: List<() -> Unit> = emptyList()
        synchronized(lock) {
            result = try {
                machine.send(payload, now()).also { callbacks = dispatch(machine.drain()) }
            } catch (t: RuntimeException) {
                failures.failure("link", t)
                callbacks = emptyList()
                SendResult.NO_LINK
            }
        }
        callbacks.forEach { runCatching { it() } }
        return result
    }

    // --- makine ---

    private fun feed(e: LinkEvent) {
        var callbacks: List<() -> Unit> = emptyList()
        synchronized(lock) {
            try {
                machine.handle(e, now())
                callbacks = dispatch(machine.drain())
            } catch (t: RuntimeException) {
                failures.failure("link", t)
            }
        }
        callbacks.forEach { runCatching { it() } }
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

    /** Kilit altında; soket işi iş parçacıklarına gider, makineye geri bildirim her zaman `io` üstünden (yeniden giriş yok). */
    private fun dispatch(cmds: List<LinkCommand>): List<() -> Unit> {
        val callbacks = ArrayList<() -> Unit>()
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
                is LinkCommand.Deliver -> {
                    val payload = c.payload
                    val transport = c.transport
                    callbacks += { listener.onData(payload, transport) }
                }
                is LinkCommand.Status -> {
                    val s = c.status
                    _status.value = s
                    callbacks += { listener.onStatus(s) }
                }
                is LinkCommand.PeerSeen -> {
                    val p = c.peer
                    callbacks += { listener.onPeerSeen(p) }
                }
                is LinkCommand.BtPeerLearned -> {
                    val a = c.address
                    callbacks += { listener.onBtPeerLearned(a) }
                }
                is LinkCommand.Wake -> schedule(c.atMs)
            }
        }
        return callbacks
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
                retryListen(Transport.WIFI)
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
            if (udp != null) bg { receiveBeacons(udp) }
        }
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
            feed(LinkEvent.Accepted(id, Transport.WIFI, s.inetAddress?.hostAddress ?: "?"))
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
            feed(LinkEvent.BeaconIn(from, buf.copyOf(p.length), p.length))
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
        const val RETRY_LISTEN_MS = 5_000L
        const val READ_BUF = 16 * 1024
        const val BEACON_BUF = 64
        val CLOSE = ByteArray(0)
    }
}

private fun Closeable.closeQuietly() {
    runCatching { close() }
}
