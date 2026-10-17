package com.kuryelog.logic.core

import org.json.JSONObject
import java.io.File
import java.time.ZoneId

/**
 * Sahte telefon: `ShiftMachine` + `HomeWatchCore` aynı tercihler, kayıt klasörü ve sahte saatle; Android çağrıları [calls]'a yazılır.
 * İzleyicinin `startShift`'i servisi (`HOME_START`) çağırır, servisin `recheckHomeWatch`'u izleyiciyi.
 */
class ShiftRig(root: File, val homeAt: Pair<Double, Double>? = 40.4093 to 49.8671) {
    var wall = 1_792_228_260_000L // 2026-10-17 13:11 Bakü
    var mono = 1_000_000L
    val clock = object : Clock {
        override fun wallMs() = wall
        override fun monoMs() = mono
        override fun zone(): ZoneId = ZoneId.of("Asia/Baku")
    }
    val prefs = KuryelogPrefs.Memory(mutableMapOf("manualPolicy" to 1))
    val log = LogStore(File(root, "logs"), { wall }, { ZoneId.of("Asia/Baku") })
    val home = HomeRepository(object : HomeRepository.Storage {
        override fun read() = HomeRepository.Saved(prefs.getString("home.lat", null), prefs.getString("home.lon", null),
            prefs.map["home.updatedAt"] as? Long, prefs.getBoolean("home.startBlocked", false), prefs.getBoolean("home.endArmed", false))
        override fun write(value: HomeRepository.Saved) {
            if (value.lat == null || value.lon == null) prefs.put(mapOf("home.lat" to null, "home.lon" to null, "home.updatedAt" to null, "home.startBlocked" to null, "home.endArmed" to null))
            else prefs.put(mapOf("home.lat" to value.lat, "home.lon" to value.lon, "home.updatedAt" to value.updatedAt,
                "home.startBlocked" to value.startBlocked, "home.endArmed" to value.endArmed))
        }
    }).also { r -> homeAt?.let { r.set(it.first, it.second, wall) } }

    val calls = mutableListOf<String>()
    var refuseForeground: Exception? = null
    var refuseGps: Exception? = null
    var refuseWatcherStart: Exception? = null
    var importing = false
    var busy = false
    var permissions = true to true
    val states = mutableListOf<ShiftState>()
    var hooks: ShiftMachine.Hooks = object : ShiftMachine.Hooks {
        override fun busy() = this@ShiftRig.busy
    }

    val machine: ShiftMachine by lazy {
        ShiftMachine(prefs, log, home, clock, object : ShiftMachine.Platform {
            override fun startForeground() { calls += "startForeground"; refuseForeground?.let { throw it } }
            override fun stopForeground() { calls += "stopForeground" }
            override fun stopSelf() { calls += "stopSelf" }
            override fun requestGps() { calls += "requestGps"; refuseGps?.let { throw it } }
            override fun removeGps() { calls += "removeGps" }
            override fun showShiftInterrupted() { calls += "notify:${NotificationIds.SHIFT_INTERRUPTED}" }
            override fun cancel(id: Int) { calls += "cancel:$id" }
            override fun recheckHomeWatch() { calls += "recheckHomeWatch"; watcher.recheck(mono) }
            override fun published(state: ShiftState) { states += state }
        }, hooks, { importing })
    }
    val watcher: HomeWatchCore by lazy {
        HomeWatchCore(home, log, object : HomeWatchCore.Platform {
            override fun request(mode: HomeWatchPlan.Mode) { calls += "watch:$mode" }
            override fun remove() { calls += "watch:remove" }
            override fun startShift(revision: Long) {
                calls += "watch:startShift"
                refuseWatcherStart?.let { throw it }
                machine.onStartCommand(ShiftMachine.ACTION_HOME_START, revision)
            }
            override fun showHomeStart(revision: Long) { calls += "notify:${NotificationIds.HOME_AUTO_START}" }
        }, machine::shiftOn, { importing }, { permissions })
    }

    /** [m] metre kuzeyde (haversine ile tam), [speedKmh] GPS hızı (null: yok), [acc] doğruluk. Saati [dtS] ilerletir. */
    fun gps(m: Double, speedKmh: Double? = null, acc: Float = 5f, dtS: Double = 1.0): GpsFix {
        mono += (dtS * 1000).toLong(); wall += (dtS * 1000).toLong()
        val (lat, lon) = homeAt ?: (40.4093 to 49.8671)
        return GpsFix(mono * 1_000_000, wall, lat + Math.toDegrees(m / 6_371_008.8), lon, accuracy = acc,
            speed = speedKmh?.let { (it / 3.6).toFloat() } ?: 0f, hasSpeed = speedKmh != null)
    }
    fun drive(m: Double, speedKmh: Double? = null, acc: Float = 5f, dtS: Double = 1.0) = machine.onLocation(gps(m, speedKmh, acc, dtS))
    fun watch(m: Double, speedKmh: Double? = null, acc: Float = 5f, dtS: Double = 1.0) = watcher.onFix(gps(m, speedKmh, acc, dtS).homeFix(), mono)

    fun events(): List<JSONObject> = File(log.logsRoot, log.day()).resolve("events.jsonl").takeIf { it.exists() }?.readLines()?.map(::JSONObject).orEmpty()
    fun types(): List<String> = events().map { it.getString("type") }
    fun errors(): List<JSONObject> = File(log.logsRoot, log.day()).resolve("errors.jsonl").takeIf { it.exists() }?.readLines()?.map(::JSONObject).orEmpty()
    fun gpsLines(): List<String> = File(log.logsRoot, log.day()).resolve("gps.csv").takeIf { it.exists() }?.readLines().orEmpty()
}
