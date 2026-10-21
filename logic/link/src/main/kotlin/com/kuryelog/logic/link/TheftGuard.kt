package com.kuryelog.logic.link

import com.kuryelog.logic.core.TripStages
import java.security.MessageDigest
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Hırsızlık koruması (N6, S10; LINK.md 5.1). Receiver'da (scooter telefonu) çalışır; saf, saati çağıran verir.
 * Android katmanı saniyede bir [tick] çağırır, ivme örneklerini [motion], konumları [fix] ile verir; Pixel'den gelen
 * `TheftCommand` [arm]/[disarm] olur. Siren, bildirim ve SMS [status]'a bakar (bu sınıf ses çıkarmaz, mesaj yollamaz).
 *
 * WEEM scooter'a salt okur: koruma algılama + alarmdır, scooter kilitlenmez.
 */
class TheftGuard(private val cfg: Config = Config()) {
    enum class State(val key: String) {
        OFF("off"), ARMING("arming"), ARMED("armed"), WARNING("warning"), ALARM("alarm");
        companion object { fun of(k: String?): State? = entries.firstOrNull { it.key == k } }
    }
    enum class ArmedBy(val key: String) {
        STAGE("stage"), AWAY("away"), MANUAL("manual");
        companion object { fun of(k: String?): ArmedBy? = entries.firstOrNull { it.key == k } }
    }
    enum class Trigger(val key: String) {
        SPEED("speed"), MOTION("motion"), GPS("gps");
        companion object { fun of(k: String?): Trigger? = entries.firstOrNull { it.key == k } }
    }

    data class Fix(val lat: Double, val lon: Double, val accuracyM: Double, val atMs: Long)

    data class Status(
        val state: State = State.OFF,
        val armedBy: ArmedBy? = null,
        val sinceMs: Long = 0,
        val trigger: Trigger? = null,
        val fix: Fix? = null,
    ) {
        /** Siren/alarm sesi gereken durum. */
        val alarming: Boolean get() = state == State.WARNING || state == State.ALARM
        /**
         * Koruma devrede: Poco'da korumayı kapatan ayarlar (aç/kapa, rol, eşleşmeyi kaldırma, PIN, SMS numaraları) kilitli; kapatma
         * yalnız PIN ya da 3 sn basılı tutma (PIN yoksa) ve Pixel'den. Geri sayım (`ARMING`) kilitli değil: PIN'siz iptal edilir.
         */
        val guarding: Boolean get() = state == State.ARMED || alarming
    }

    /**
     * Bir tik girdisi. [atStop]: açık siparişlerden `AT_PICKUP`/`AT_DROPOFF` olanlar ([atStopOf]); [scooterSpeedKmh]: taze
     * scooter karesinin hızı, kare yoksa null (durmuş sayılır); [linked]: Pixel ile kanal açık.
     */
    data class Inputs(
        val enabled: Boolean,
        val linked: Boolean,
        val atStop: Map<String, TripStages.Stage> = emptyMap(),
        val scooterSpeedKmh: Double? = null,
        val autoStage: Boolean = true,
        val autoAway: Boolean = true,
    )

    /** LINK.md 5.1 sayıları (ajan varsayılanı). */
    data class Config(
        val stopKmh: Double = 2.0,
        val stillMs: Long = 5_000,
        val awayMs: Long = 60_000,
        val armingMs: Long = 20_000,
        val warningMs: Long = 8_000,
        val speedKmh: Double = 4.0,
        val speedHoldMs: Long = 2_000,
        val motionMs2: Double = 1.2,
        val motionCount: Int = 3,
        val motionWindowMs: Long = 5_000,
        val motionSpanMs: Long = 1_000,
        val gpsMaxAccuracyM: Double = 20.0,
        val gpsMoveM: Double = 25.0,
        val fixFreshMs: Long = 30_000,
    )

    private val lock = Any()
    private var st = Status()
    private var armingFrom = 0L
    private var armedTrips: Map<String, TripStages.Stage> = emptyMap()
    private val consumed = HashMap<String, TripStages.Stage>()
    private var awaySuppressed = false
    private var stoppedSince: Long? = null
    private var linkDownSince: Long? = null
    private var speedSince: Long? = null
    private val motions = ArrayDeque<Long>()
    private var armPoint: Fix? = null
    private var lastFix: Fix? = null
    private var last = Inputs(enabled = false, linked = true)

    val status: Status get() = synchronized(lock) { st }

    fun tick(now: Long, i: Inputs): Status = synchronized(lock) {
        last = i
        val moving = (i.scooterSpeedKmh ?: 0.0) > cfg.stopKmh
        stoppedSince = if (moving) null else stoppedSince ?: now
        if (i.linked) { linkDownSince = null; awaySuppressed = false } else if (linkDownSince == null) linkDownSince = now
        consumed.entries.removeAll { i.atStop[it.key] != it.value }
        if (!i.enabled) {
            if (st.state != State.OFF) off(now)
            return st
        }
        when (st.state) {
            State.OFF -> autoReason(now, i)?.let { (by, trips) -> startArming(by, trips, now) }
            State.ARMING -> when {
                autoDisarmDue(i) -> autoOff(now, i)
                moving && st.armedBy == ArmedBy.MANUAL -> armingFrom = now
                moving -> off(now)
                now - armingFrom >= cfg.armingMs -> {
                    armPoint = lastFix?.takeIf { good(it) && now - it.atMs <= cfg.fixFreshMs }
                    speedSince = null; motions.clear()
                    st = st.copy(state = State.ARMED, sinceMs = now)
                }
            }
            State.ARMED -> if (autoDisarmDue(i)) autoOff(now, i) else trigger(now, i)?.let {
                st = st.copy(state = State.WARNING, sinceMs = now, trigger = it, fix = lastFix)
            }
            State.WARNING -> if (now - st.sinceMs >= cfg.warningMs) st = st.copy(state = State.ALARM, sinceMs = now, fix = lastFix)
            State.ALARM -> st = st.copy(fix = lastFix)
        }
        st
    }

    /** Poco'nun doğrusal ivme büyüklüğü (m/s², yerçekimi çıkarılmış). Yalnız `ARMED`'da sayılır. */
    fun motion(atMs: Long, accelMs2: Double) = synchronized(lock) {
        if (st.state == State.ARMED && accelMs2 >= cfg.motionMs2) {
            motions.addLast(atMs)
            while (motions.size > 32) motions.removeFirst()
        }
    }

    fun fix(f: Fix) = synchronized(lock) {
        if (lastFix == null || f.atMs >= lastFix!!.atMs) lastFix = f
        if (st.state == State.ARMED && armPoint == null && good(f)) armPoint = f
        if (st.alarming) st = st.copy(fix = lastFix)
    }

    /** "Park" (Pixel `TheftCommand arm` ya da Poco). Koruma kapalıysa false. Zaten devredeyse yalnız elle kipine geçer. */
    fun arm(now: Long): Boolean = synchronized(lock) {
        if (!last.enabled) return false
        when (st.state) {
            State.OFF -> startArming(ArmedBy.MANUAL, emptyMap(), now)
            State.ARMING, State.ARMED -> { st = st.copy(armedBy = ArmedBy.MANUAL); armedTrips = emptyMap() }
            State.WARNING, State.ALARM -> {}
        }
        true
    }

    /**
     * Sahibin kapatması (Pixel ya da Poco PIN'i; denetim çağıranda). Şu anki duraklar ve kopuk bağlantı yeniden otomatik
     * devreye almaz: aşama değişene, bağlantı geri gelene dek.
     */
    fun disarm(now: Long) = synchronized(lock) {
        consumed.putAll(last.atStop)
        if (!last.linked) awaySuppressed = true
        off(now)
    }

    /** Poco'da `ARMING` geri sayımında iptal (PIN'siz; henüz koruma yok). Başka durumda false. */
    fun cancelArming(now: Long): Boolean = synchronized(lock) {
        if (st.state != State.ARMING) return false
        disarm(now)
        true
    }

    private fun autoReason(now: Long, i: Inputs): Pair<ArmedBy, Map<String, TripStages.Stage>>? {
        val still = stoppedSince?.let { now - it >= cfg.stillMs } ?: false
        if (!still) return null
        val stops = i.atStop.filter { consumed[it.key] != it.value }
        if (i.autoStage && stops.isNotEmpty()) return ArmedBy.STAGE to stops
        val down = linkDownSince
        if (i.autoAway && !awaySuppressed && down != null && now - down >= cfg.awayMs) return ArmedBy.AWAY to emptyMap()
        return null
    }

    private fun startArming(by: ArmedBy, trips: Map<String, TripStages.Stage>, now: Long) {
        armedTrips = trips
        armingFrom = now
        st = Status(State.ARMING, by, now)
    }

    private fun autoDisarmDue(i: Inputs): Boolean = when (st.armedBy) {
        ArmedBy.STAGE -> armedTrips.none { i.atStop[it.key] == it.value }
        ArmedBy.AWAY -> i.linked
        ArmedBy.MANUAL, null -> false
    }

    private fun autoOff(now: Long, i: Inputs) {
        if (st.armedBy == ArmedBy.STAGE) consumed.putAll(i.atStop)
        off(now)
    }

    private fun off(now: Long) {
        st = Status(State.OFF, null, now)
        armedTrips = emptyMap(); armPoint = null; speedSince = null; motions.clear()
    }

    private fun trigger(now: Long, i: Inputs): Trigger? {
        val v = i.scooterSpeedKmh
        speedSince = if (v != null && v >= cfg.speedKmh) speedSince ?: now else null
        if (speedSince?.let { now - it >= cfg.speedHoldMs } == true) return Trigger.SPEED
        while (motions.isNotEmpty() && now - motions.first() > cfg.motionWindowMs) motions.removeFirst()
        if (motions.size >= cfg.motionCount && motions.last() - motions.first() >= cfg.motionSpanMs) return Trigger.MOTION
        val a = armPoint
        val f = lastFix
        if (a != null && f != null && f.atMs > a.atMs && good(f) && metres(a, f) >= cfg.gpsMoveM) return Trigger.GPS
        return null
    }

    private fun good(f: Fix) = f.accuracyM <= cfg.gpsMaxAccuracyM

    companion object {
        /** Açık siparişlerden durakta olanlar (aşama = otomatik devreye alma girdisi). */
        fun atStopOf(s: TripStages): Map<String, TripStages.Stage> = s.trips
            .filter { it.active && (it.stage == TripStages.Stage.AT_PICKUP || it.stage == TripStages.Stage.AT_DROPOFF) }
            .associate { it.tripId to it.stage!! }

        fun metres(a: Fix, b: Fix): Double {
            val r = 6_371_000.0
            val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
            val dp = p2 - p1; val dl = Math.toRadians(b.lon - a.lon)
            val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
            return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}

/** Poco'daki kapatma PIN'i: yalnız tuzlu SHA-256 özeti saklanır (LINK.md 5.1). 4 hane. */
object TheftPin {
    fun valid(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: ByteArray): String {
        require(valid(pin)) { "pin" }
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt); md.update(pin.toByteArray(Charsets.US_ASCII))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, salt: ByteArray, stored: String): Boolean =
        valid(pin) && MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())
}
