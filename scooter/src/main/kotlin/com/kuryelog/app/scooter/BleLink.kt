package com.kuryelog.app.scooter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import com.kuryelog.logic.scooter.GattPlan
import com.kuryelog.logic.scooter.LinkRetry
import com.kuryelog.logic.scooter.NinebotDashboard
import com.kuryelog.logic.scooter.NinebotFrames
import com.kuryelog.logic.scooter.NinebotLink
import com.kuryelog.logic.scooter.NinebotMetrics
import com.kuryelog.logic.scooter.NinebotMode
import com.kuryelog.logic.scooter.NinebotSession
import com.kuryelog.logic.scooter.PairWindow
import com.kuryelog.logic.scooter.PairingVault
import com.kuryelog.logic.scooter.ScooterEnergy
import com.kuryelog.logic.scooter.ScooterTelemetry
import com.kuryelog.logic.scooter.SessionKey
import com.kuryelog.logic.scooter.Timing
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID

/**
 * S1 scooter bağlantısı ve S6 yeniden bağlanma (SCOOTER.md 2.1, 2.4, 4.1, 5.1; NB `BleCapture.java` satır satır): tek GATT
 * bağlantısı, sıralı kurulum ([NinebotLink]: keşif → ad → MTU → abonelik → 200 ms → protokol), Nordic UART aboneliği, tek yazma
 * kuyruğu, [NinebotSession] nabzı, [LinkRetry] ile yeniden bağlanma. Saf kurallar `logic/scooter`'da ([GattPlan], [PairWindow]).
 *
 * İş parçacığı: bütün çağrılar ve GATT geri çağrıları [handler]'ın (servisin "scooter-link" `HandlerThread`'i) üstünde; eski
 * bağlantının geri çağrısı atılır; çalışırken hata → `failed` (yeniden bağlan). Sahibi (A5.3 servisi) `start`/`close`/
 * `updatePriority`'yi `guarded` içinde çağırır: scooter hatası vardiyayı ve sipariş kaydını durdurmaz.
 * Kayıt satırı ([telemetry]), 1 Hz enerji örneği ([sample]) ve ayar metni ([measurements]) [latestValues]'tan (A5.3; kuralları saf
 * `ScooterTelemetry`). Cihazda ve scooter'da doğrulanmadı.
 */
@SuppressLint("MissingPermission")
class BleLink(private val context: Context, private val handler: Handler, private val sink: Sink, private val viewers: Viewers = Viewers.NONE) {
    interface Sink {
        fun event(kind: String, data: JSONObject)
        fun state(text: String)
        fun pairPrompt(visible: Boolean)
        fun dashboard(frame: NinebotDashboard.Snapshot?) {}
        fun warning(text: String) {}

        /** WEEM S18 (b), A10.4f: true = bağlantı boşta kaldı, [Timing.IDLE_RETRY_MS] sonra yeniden denenecek ("koptu" bildirimi); false = kalktı. */
        fun dropped(on: Boolean) {}

        /** Per-packet rows: the payload is built only if the log policy keeps the row (detailed mode); counters still move. */
        fun frequent(kind: String, data: () -> JSONObject) {
            event(kind, data())
        }
    }

    /** Values on screen (A5.3/A5.4 fill these in): the live panel or its picture-in-picture window, the floating ball. */
    interface Viewers {
        fun dashboardVisible(): Boolean
        fun ballVisible(): Boolean

        object NONE : Viewers {
            override fun dashboardVisible() = false
            override fun ballVisible() = false
        }
    }

    private class Write(val c: BluetoothGattCharacteristic, val bytes: ByteArray, val type: Int)

    private var gatt: BluetoothGatt? = null
    private var address = ""
    private var name: String? = null
    private var window = PairWindow(false)
    private val retry = LinkRetry()
    private var busy = false
    private var mtu = GattPlan.DEFAULT_MTU
    private var mode = NinebotMode.GEN2_FW
    private var sweep: NinebotMetrics.Sweep? = null
    private val dash = NinebotDashboard()

    /** The last accepted register values of this connection (empty while not reading); any thread may read it. */
    @Volatile var latestValues: Collection<NinebotMetrics.Value> = emptyList()
        private set
    private var priority = -1
    private var session: NinebotSession? = null
    private var link: NinebotLink? = null
    private val assemblers = HashMap<UUID, NinebotFrames>()
    private val notify = ArrayList<BluetoothGattCharacteristic>()
    private var subscription = 0
    private var subscribed = 0
    private val writes = ArrayDeque<Write>()
    private val chars = HashMap<String, BluetoothGattCharacteristic>()
    private val cccd = UUID.fromString(GattPlan.CCCD)
    private val reconnect = Runnable { connect() }
    /** S18 (b): boşta kalan bağlantının yeniden denemesi bekliyor; "koptu" bildirimi gösterildi (okuma gelince kalkar). */
    private var idlePending = false
    private var droppedShown = false
    private val idleRetry = Runnable {
        if (retry.closed || !idlePending) return@Runnable
        idlePending = false
        sink.event("ble_state", json("state", "idle_retry", "attempt", retry.attempt))
        closeGatt()
        connect()
    }
    private val timeout = Runnable { failed("Bluetooth işlem zaman aşımı") }
    private val startProtocol = Runnable {
        if (!retry.closed && gatt != null && link?.protocolReady() == true) {
            try { protocol() } catch (e: RuntimeException) { failed("G3 bağlantı başlangıcı: " + e.javaClass.simpleName) }
        }
    }
    private val pulse: Runnable = Runnable {
        val s = session
        if (retry.closed || gatt == null || s == null) return@Runnable
        try { s.tick(SystemClock.elapsedRealtime()) } catch (e: RuntimeException) { failed("G3 protokol işlemi: " + e.javaClass.simpleName) }
        schedulePulse()
    }

    private fun schedulePulse() {
        handler.removeCallbacks(pulse)
        val s = session ?: return
        if (!retry.closed && gatt != null && s.stage != NinebotSession.Stage.CLOSED && s.stage != NinebotSession.Stage.FAILED &&
            s.stage != NinebotSession.Stage.NEEDS_KEY
        ) handler.postDelayed(pulse, s.nextDelayMs(SystemClock.elapsedRealtime()))
        else if (s.stage == NinebotSession.Stage.FAILED) idle(noChannel = false)
    }

    /**
     * WEEM S18 (b), A10.4f (SCOOTER.md 5.1, 14 R-S3): `fail` bağlantıyı açık ve boşta bıraktı. "Scooter bağlantısı koptu" bildirimi
     * ([Sink.dropped]); kullanıcı durdurmaz ya da "Şimdi dene" demezse [Timing.IDLE_RETRY_MS] sonra yeni bağlantı. Anahtar yoksa beklenmez.
     */
    private fun idle(noChannel: Boolean) {
        val ms = LinkRetry.idleRetryMs(session?.stage, noChannel) ?: return
        if (retry.closed || idlePending) return
        idlePending = true
        droppedShown = true
        sink.event("ble_idle", json("stage", session?.stage?.name, "no_channel", noChannel, "retry_ms", ms))
        sink.dropped(true)
        handler.postDelayed(idleRetry, ms)
    }

    /** "Şimdi dene" (bildirim düğmesi): bekleyen boşta yeniden denemeyi hemen yapar; bekleyen yoksa bir şey yapmaz. [handler] üstünde. */
    fun retryNow() {
        if (retry.closed || !idlePending) return
        handler.removeCallbacks(idleRetry)
        idleRetry.run()
    }

    private fun cancelIdle() {
        idlePending = false
        handler.removeCallbacks(idleRetry)
    }

    private var power: PowerManager? = null

    /** Somebody can read the values: the live panel (or its picture-in-picture window), or the floating ball over a map while the
     *  screen is on. With the screen off nobody sees them, so the link falls back to the lighter cadence and balanced priority. */
    private fun watched(): Boolean {
        if (viewers.dashboardVisible()) return true
        if (!viewers.ballVisible()) return false
        if (power == null) power = context.getSystemService(PowerManager::class.java)
        return power?.isInteractive == true
    }

    /** Once a second from the service tick: the read cadence and the connection interval follow [watched]. */
    fun updatePriority() {
        val g = gatt
        val s = session
        if (retry.closed || g == null || s == null) return
        val watched = watched()
        s.panelVisible(watched)
        val desired = if (watched) BluetoothGatt.CONNECTION_PRIORITY_HIGH else BluetoothGatt.CONNECTION_PRIORITY_BALANCED
        if (desired == priority) return
        try {
            val accepted = g.requestConnectionPriority(desired)
            if (accepted) priority = desired
            sink.event("ble_priority", json("requested", desired, "accepted", accepted, "dashboard_visible", viewers.dashboardVisible(), "ball_visible", viewers.ballVisible()))
        } catch (e: RuntimeException) {
            sink.event("ble_priority", json("error", e.javaClass.simpleName))
        }
    }

    /** `sample` olayının `scooter_telemetry` alanı; değer yoksa null. */
    fun telemetry(): JSONObject? = ScooterTelemetry.telemetry(latestValues, SystemClock.elapsedRealtime())

    /** Bir 1 Hz enerji örneği (ilk bilinen harici paket kartı harici pakettir); değer yoksa null. */
    fun sample(): ScooterEnergy.Sample? =
        ScooterTelemetry.sample(latestValues, System.currentTimeMillis(), SystemClock.elapsedRealtime(), LoggerPreferences.get(context).getString("external_bms_boards", ""))

    /** Ayar ekranındaki ölçüm metni. */
    fun measurements(): String = ScooterTelemetry.measurements(latestValues, SystemClock.elapsedRealtime())

    fun start(address: String, name: String?, pair: Boolean, mode: NinebotMode, sweep: NinebotMetrics.Sweep?) {
        this.address = address
        this.name = name
        window = PairWindow(pair)
        this.mode = mode
        this.sweep = sweep
        connect()
    }

    private fun watch() {
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, GattPlan.WATCHDOG_MS)
    }

    private fun connect() {
        if (retry.closed) return
        cancelIdle()
        try {
            val m = context.getSystemService(BluetoothManager::class.java)
            val a = m?.adapter
            if (a == null || !a.isEnabled) { failed("Bluetooth kapalı; GPS kaydı sürüyor"); return }
            link = NinebotLink()
            sink.state("Scooter'a bağlanıyor")
            gatt = a.getRemoteDevice(address).connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) { failed("Bluetooth bağlantısı başlatılamadı"); return }
            watch()
        } catch (e: RuntimeException) {
            failed("Bluetooth: " + e.javaClass.simpleName)
        }
    }

    private fun failed(reason: String) {
        if (retry.closed) return
        cancelIdle()
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(reconnect)
        closeGatt()
        sink.state(reason)
        sink.event("ble_state", json("state", reason, "attempt", retry.attempt))
        handler.postDelayed(reconnect, retry.next())
    }

    private fun closeGatt() {
        latestValues = emptyList()
        priority = -1
        sink.dashboard(null)
        handler.removeCallbacks(pulse)
        handler.removeCallbacks(startProtocol)
        session?.close()
        session = null
        link?.close()
        link = null
        chars.clear()
        notify.clear()
        subscription = 0
        subscribed = 0
        assemblers.clear()
        writes.clear()
        busy = false
        mtu = GattPlan.DEFAULT_MTU
        val old = gatt
        gatt = null
        if (old != null) {
            try { old.disconnect() } catch (ignored: RuntimeException) {}
            old.close()
        }
    }

    /** User or service stop: no reconnect after this. */
    fun close() {
        retry.close()
        cancelIdle()
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(reconnect)
        closeGatt()
        if (droppedShown) { droppedShown = false; sink.dropped(false) }
    }

    private fun dispatch(g: BluetoothGatt, r: () -> Unit) {
        handler.post {
            if (!retry.closed && g === gatt) try { r() } catch (e: RuntimeException) { failed("BLE işlem hatası: " + e.javaClass.simpleName) }
        }
    }

    private fun requestMtu() {
        val l = link
        if (l == null || l.phase != NinebotLink.Phase.MTU) return
        if (!gatt!!.requestMtu(GattPlan.MTU)) { if (l.mtuReady()) subscribe() } else watch()
    }

    private fun subscribe() {
        val l = link
        if (l == null || l.phase != NinebotLink.Phase.SUBSCRIBING) return
        handler.removeCallbacks(timeout)
        if (subscription >= notify.size) {
            if (subscribed == 0) { failed("Bluetooth bildirim kanalı açılamadı; GPS sürüyor"); return }
            if (l.subscriptionsReady()) handler.postDelayed(startProtocol, GattPlan.SETTLE_MS)
            return
        }
        val g = gatt!!
        val c = notify[subscription++]
        val d = c.getDescriptor(cccd)
        if (d == null || !g.setCharacteristicNotification(c, true)) { subscribe(); return }
        if (!l.expectDescriptor(c.uuid.toString())) return
        val value = if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        val ok = if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS else legacyWriteDescriptor(g, d, value)
        if (ok) watch() else failed("Bildirim aboneliği başlatılamadı")
    }

    @Suppress("DEPRECATION")
    private fun legacyWriteDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean {
        d.value = value
        return g.writeDescriptor(d)
    }

    private fun protocol() {
        if (session != null) return
        val now = SystemClock.elapsedRealtime()
        sink.event("ble_subscribed", json("channels", subscribed, "mtu", mtu, "ready_mono_ms", now, "mode", mode.setting, "profile", 0))
        val vault = KeyVault(context)
        var key: ByteArray?
        try {
            key = vault.get(address)
        } catch (e: PairingVault.KeyUnavailableException) {
            if (window.allowPair) {
                key = null
                sink.event("ble_key_error", json("reason", "local_key_unavailable", "explicit_repair", true))
            } else {
                val message = "Yerel anahtar açılamadı; Bluetooth durduruldu, GPS sürüyor. Kaydı durdurup G3 ile eşleştir veya Gelişmiş bağlantı anahtarı ile kurtar."
                close()
                sink.state(message)
                sink.warning(message)
                sink.event("ble_key_error", json("reason", "local_key_unavailable", "retry", false))
                return
            }
        }
        val start = window.begin(now)
        sink.event("local_key_state", json("present", key != null, "confirmed", vault.confirmed(address), "pending_candidate", vault.hasPending(address)))
        if (start.expired) {
            sink.state("Eşleştirme süresi doldu; yeniden denemek için kaydı durdur")
            key?.fill(0)
            return
        }
        val s = NinebotSession(port(vault), name, key, start.pair, now, mode, start.window, System::currentTimeMillis, sweep, SessionKey::random)
        session = s
        key?.fill(0)
        // External pack ("Battery 2"): boards found by an earlier probe are read every connection; the opt-in probe runs once.
        val prefs = LoggerPreferences.get(context)
        val knownBoards = prefs.getString("external_bms_boards", "") ?: ""
        val probe = prefs.getBoolean("battery_probe", false)
        sink.event("battery_config", json("probe", probe, "known_boards", knownBoards))
        s.batteryBoards(NinebotMetrics.parseBoards(knownBoards), if (probe) NinebotMetrics.EXTERNAL_BMS_CANDIDATES else IntArray(0))
        updatePriority()
        handler.post(pulse)
    }

    private fun port(vault: KeyVault) = object : NinebotSession.Port {
        override fun available(profile: Int): Boolean {
            val c = chars[GattPlan.WRITE]
            return profile == 0 && c != null && (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        }

        override fun send(profile: Int, wire: ByteArray) {
            if (profile != 0) throw IllegalArgumentException("only profile 0")
            GattPlan.checkWrite(wire.size, mtu, writes.size)
            writes.add(Write(chars[GattPlan.WRITE]!!, wire, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE))
            pump()
        }

        override fun state(state: String) = sink.state(state)
        override fun note(kind: String, detail: String) = sink.event(kind, json("detail", detail))
        override fun frequentNote(kind: String, detail: () -> String) = sink.frequent(kind) { json("detail", detail()) }
        override fun pairPrompt(visible: Boolean) = sink.pairPrompt(visible)
        override fun fault(reason: String) = failed(reason)

        override fun key(key: ByteArray, confirmed: Boolean) {
            try {
                vault.save(address, key, confirmed)
                if (confirmed) window.confirmed()
            } finally {
                key.fill(0)
            }
        }

        override fun batteryFound(board: Int) {
            val prefs = LoggerPreferences.get(context)
            prefs.edit().putString("external_bms_boards", NinebotMetrics.addBoard(prefs.getString("external_bms_boards", ""), board)).apply()
        }

        override fun batteryProbeDone(found: Boolean) {
            LoggerPreferences.get(context).edit().putBoolean("battery_probe", false).apply()
        }

        override fun reading(value: NinebotMetrics.Value, bytes: ByteArray) {
            retry.reading()
            if (droppedShown) { droppedShown = false; sink.dropped(false) }
            val s = session
            if (s != null) {
                latestValues = ArrayList(s.metrics.values())
                sink.dashboard(dash.snapshot(latestValues, SystemClock.elapsedRealtime()))
            }
            sink.frequent("ble_measurement") {
                json(
                    "key", value.source.key, "value", value.value, "unit", value.source.unit, "board", value.source.board, "register", value.source.index,
                    "raw_hex", Timing.hex(bytes), "decoded_mono_ms", value.monoMs, "mapping", "community_g3", "device_verified", false,
                )
            }
        }
    }

    /** The only writer of scooter frames (SCOOTER.md 2.6): one write in flight, the next after `onCharacteristicWrite`. */
    private fun pump() {
        val g = gatt
        if (busy || writes.isEmpty() || g == null) return
        val w = writes.remove()
        busy = true
        val ok = if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(w.c, w.bytes, w.type) == BluetoothStatusCodes.SUCCESS else legacyWrite(g, w)
        sink.frequent("ble_write") {
            json(
                "characteristic", w.c.uuid.toString(), "bytes", w.bytes.size,
                "counter", ((w.bytes[w.bytes.size - 2].toInt() and 255) shl 8) or (w.bytes[w.bytes.size - 1].toInt() and 255),
                "write_type", w.type, "accepted", ok, "wire_hex", Timing.hex(w.bytes), "mode", mode.setting, "profile", 0,
            )
        }
        if (!ok) throw IllegalStateException("GATT write rejected")
        watch()
    }

    @Suppress("DEPRECATION")
    private fun legacyWrite(g: BluetoothGatt, w: Write): Boolean {
        w.c.writeType = w.type
        w.c.value = w.bytes
        return g.writeCharacteristic(w.c)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            dispatch(g) {
                if (status != 0 || state == BluetoothProfile.STATE_DISCONNECTED) { failed("Bağlantı koptu ($status); GPS kaydı sürüyor"); return@dispatch }
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    sink.event("ble_connected", json("status", status))
                    if (!g.discoverServices()) failed("Servis keşfi başlatılamadı") else watch()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            dispatch(g) {
                val l = link
                if (l == null || l.phase != NinebotLink.Phase.DISCOVERING) return@dispatch
                if (status != 0) { failed("Servis keşfi hatası $status"); return@dispatch }
                val services = JSONArray()
                chars.clear()
                notify.clear()
                subscription = 0
                subscribed = 0
                var deviceName: BluetoothGattCharacteristic? = null
                for (s in g.services) {
                    val cs = JSONArray()
                    val sid = s.uuid.toString()
                    for (c in s.characteristics) {
                        cs.put(json("uuid", c.uuid.toString(), "properties", c.properties))
                        val id = c.uuid.toString()
                        if (id == GattPlan.DEVICE_NAME) deviceName = c
                        if (GattPlan.kept(sid)) {
                            chars[id] = c
                            val notifies = (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                            if (GattPlan.subscribed(sid, id, notifies)) {
                                notify.add(c)
                                assemblers[c.uuid] = NinebotFrames { raw, reason ->
                                    sink.event("ble_reassembly_rejected", json("reason", reason, "characteristic", id, "wire_hex", Timing.hex(raw)))
                                }
                            }
                        }
                    }
                    services.put(json("uuid", sid, "characteristics", cs))
                }
                sink.event("gatt_inventory", json("services", services))
                handler.removeCallbacks(timeout)
                if (notify.isEmpty()) { sink.state("GATT kaydedildi; desteklenen kanal yok"); idle(noChannel = true); return@dispatch }
                val nameChar = deviceName
                val readName = nameChar != null && (nameChar.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                if (!l.servicesReady(readName)) return@dispatch
                if (readName && g.readCharacteristic(nameChar)) {
                    watch()
                } else {
                    if (readName) l.nameReady()
                    requestMtu()
                }
            }
        }

        private fun nameRead(g: BluetoothGatt, status: Int, value: ByteArray?) {
            val copy = value?.clone() ?: ByteArray(0)
            dispatch(g) {
                val l = link
                if (l == null || !l.nameReady()) return@dispatch
                if (status == 0) {
                    val n = GattPlan.deviceName(copy)
                    if (n.isNotEmpty()) name = n
                }
                requestMtu()
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) = nameRead(g, status, value)

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) nameRead(g, status, c.value)
        }

        override fun onMtuChanged(g: BluetoothGatt, value: Int, status: Int) {
            dispatch(g) {
                if (status == 0) mtu = value
                val expected = link?.mtuReady() == true
                sink.event("ble_mtu", json("mtu", mtu, "status", status, "expected", expected))
                if (expected) subscribe()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            dispatch(g) {
                val l = link
                if (l == null || !l.descriptorReady(d.characteristic.uuid.toString())) return@dispatch
                if (status != 0) { failed("Bildirim aboneliği hatası $status"); return@dispatch }
                subscribed++
                subscribe()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            dispatch(g) {
                if (!busy || link?.phase != NinebotLink.Phase.PROTOCOL) return@dispatch
                handler.removeCallbacks(timeout)
                busy = false
                sink.frequent("ble_write_result") { json("characteristic", c.uuid.toString(), "status", status) }
                if (status != 0) { failed("BLE yazım hatası $status"); return@dispatch }
                pump()
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = received(g, c, value)

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) received(g, c, c.value)
        }
    }

    private fun received(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray?) {
        if (value == null) return
        val copy = value.clone()
        val wall = System.currentTimeMillis()
        val mono = SystemClock.elapsedRealtimeNanos()
        dispatch(g) {
            sink.frequent("ble_raw") {
                json("received_wall_ms", wall, "received_mono_ns", mono, "service", c.service.uuid.toString(), "characteristic", c.uuid.toString(), "hex", Timing.hex(copy))
            }
            val frames = assemblers[c.uuid]
            val s = session
            if (frames != null && s != null) for (raw in frames.feed(copy, SystemClock.elapsedRealtime())) s.receive(raw, SystemClock.elapsedRealtime())
            schedulePulse()
        }
    }
}
