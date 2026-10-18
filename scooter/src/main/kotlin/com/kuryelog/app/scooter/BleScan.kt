package com.kuryelog.app.scooter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.kuryelog.logic.scooter.ScooterPanel

/**
 * Scooter araması (NB `DashboardActivity.scan`): filtresiz, `SCAN_MODE_LOW_LATENCY`, [ScooterPanel.SCAN_MS]; sonuçlar canlı
 * ([ScooterPanel.ScanList]). Salt okuma: yalnız yayın dinlenir, bağlanılmaz. Ana iş parçacığında kullanılır; geri çağrılar da orada.
 */
@SuppressLint("MissingPermission")
class BleScan(private val context: Context, private val listener: Listener) {
    interface Listener {
        /** Liste değişti (yeni cihaz, ad ya da RSSI); [ScanList] okunabilir. */
        fun changed()

        /** Süre doldu ya da hata: tarama durdu. `error` = Android tarama hata kodu, yoksa null. */
        fun finished(error: Int?)
    }

    enum class Start { STARTED, BLUETOOTH_OFF, NO_SCANNER, FAILED }

    val list = ScooterPanel.ScanList()
    var scanning = false
        private set
    var failure: String = ""
        private set

    private val ui = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private val done = Runnable { if (scanning) { stop(); listener.finished(null) } }

    private val callback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val address = result.device.address
            val name = result.scanRecord?.deviceName
            val rssi = result.rssi
            ui.post { if (scanning && list.seen(address, name, rssi)) listener.changed() }
        }

        override fun onScanFailed(error: Int) {
            ui.post { stop(); listener.finished(error) }
        }
    }

    fun start(): Start {
        if (scanning) return Start.STARTED
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return Start.BLUETOOTH_OFF
        stop()
        list.clear()
        val s = adapter.bluetoothLeScanner ?: return Start.NO_SCANNER
        scanner = s
        scanning = true
        return try {
            s.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            ui.postDelayed(done, ScooterPanel.SCAN_MS)
            Start.STARTED
        } catch (e: RuntimeException) {
            failure = e.javaClass.simpleName
            stop()
            Start.FAILED
        }
    }

    fun stop() {
        ui.removeCallbacks(done)
        val s = scanner
        if (s != null && scanning) try { s.stopScan(callback) } catch (ignored: RuntimeException) { }
        scanning = false
    }

    companion object {
        /** Arama ve eşleştirmenin istediği izinler (NB `permissions`: konum her sürümde, Android 12+ tarama ve bağlantı). */
        fun neededPermissions(): Array<String> {
            val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) { need += Manifest.permission.BLUETOOTH_SCAN; need += Manifest.permission.BLUETOOTH_CONNECT }
            return need.toTypedArray()
        }

        fun missingPermissions(c: Context): Array<String> =
            neededPermissions().filter { c.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

        fun bluetoothOn(c: Context): Boolean = c.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }
}
