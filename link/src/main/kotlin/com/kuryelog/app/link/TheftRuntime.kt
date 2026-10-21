package com.kuryelog.app.link

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.link.LinkStatus
import com.kuryelog.logic.link.PhoneBridge
import com.kuryelog.logic.link.TheftCommand
import com.kuryelog.logic.link.TheftGuard
import com.kuryelog.logic.link.TheftGuard.State
import com.kuryelog.logic.link.TheftPin
import com.kuryelog.logic.link.TheftSms
import com.kuryelog.logic.link.TheftState
import com.kuryelog.logic.link.TheftText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import kotlin.math.sqrt

/**
 * Hırsızlık koruması (A8.4, LINK.md 5). Saf karar [TheftGuard]/[TheftSms]'te; burada saat, ivme ve konum, siren, bildirim ve SMS.
 * - **Receiver** (scooter telefonu): saniyede bir tik; durum [PhoneBridge.receiverTheft] ile Pixel'e; Pixel'in komutu [command].
 * - **Provider** (Pixel): gelen `TheftState`'e göre bildirim ve alarm sesi; Park/Kapat [requestArm]/[requestDisarm].
 * Ayarlar yalnız bu telefonda (`weem-theft`): numara ve PIN özeti bağlantıya girmez. `:app` `TwoPhone` role göre bağlar. Cihazda
 * doğrulanmadı.
 */
// Yalnız uygulama bağlamı tutulur.
@SuppressLint("StaticFieldLeak")
object TheftRuntime {
    const val PREFS = "weem-theft"
    private const val K_ENABLED = "enabled"
    private const val K_AUTO_STAGE = "auto_stage"
    private const val K_AUTO_AWAY = "auto_away"
    private const val K_PIN_HASH = "pin_hash"
    private const val K_PIN_SALT = "pin_salt"
    private val K_SMS = listOf("sms_1", "sms_2")
    /** Alarm 10 dk sürekli, sonra dakikada 30 sn (LINK.md 5.1). */
    private const val SIREN_FULL_MS = 10 * 60_000L
    private const val SIREN_ON_MS = 30_000L
    private const val SIREN_TEST_MS = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var app: Context? = null
    @Volatile private var bridge: PhoneBridge? = null
    private var job: Job? = null
    // Rol değişince ([stop]) yenilenir: eski geri sayım/alarm ve SMS zamanlayıcısı yeni Receiver oturumuna taşınmaz.
    @Volatile private var guard = TheftGuard()
    @Volatile private var sms = TheftSms()
    /** "Siren dene" bitişi: o ana dek saniyelik tik (koruma kapalıyken) sesi kesmez. */
    @Volatile private var sirenTestUntil = 0L

    private val _status = MutableStateFlow(TheftGuard.Status())
    /** Receiver'da bu telefonun koruma durumu. */
    val status: StateFlow<TheftGuard.Status> = _status.asStateFlow()
    private val _smsIssue = MutableStateFlow(false)
    /** Son SMS gönderilemedi (izin, SIM, numara yok). */
    val smsIssue: StateFlow<Boolean> = _smsIssue.asStateFlow()
    private val _silenced = MutableStateFlow(false)
    /** Pixel'de alarm sesi bu alarm için kapatıldı (bildirim kalır). */
    val silenced: StateFlow<Boolean> = _silenced.asStateFlow()

    // ------------------------------------------------------------------ bağlama (`:app` TwoPhone)

    /** Receiver: [speed] = taze scooter karesinin hızı (km/s), kare yoksa null. */
    @Synchronized
    fun receiver(ctx: Context, b: PhoneBridge, speed: () -> Double?) {
        stop()
        val a = ctx.applicationContext
        app = a; bridge = b
        job = scope.launch {
            var shown: TheftGuard.Status? = null
            while (true) {
                Store.guarded(a, "theft_tick") { shown = receiverTick(a, b, speed, shown) }
                delay(1_000)
            }
        }
    }

    /** Provider: Receiver'ın durumuna göre bildirim ve ses. */
    @Synchronized
    fun provider(ctx: Context, b: PhoneBridge) {
        stop()
        val a = ctx.applicationContext
        app = a; bridge = b
        job = scope.launch {
            var lastState: State? = null
            var lastSince = -1L
            b.theft.collect { t ->
                Store.guarded(a, "theft_provider") {
                    val s = t?.status ?: TheftGuard.Status()
                    if (s.state != lastState || s.sinceMs != lastSince) {
                        if (s.state != State.ALARM) _silenced.value = false
                        lastState = s.state; lastSince = s.sinceMs
                        providerSound(a, s)
                    }
                    notifyState(a, s, receiverSide = false, t)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel(); job = null
        bridge = null
        app?.let { a ->
            Store.guarded(a, "theft_stop") {
                sensors(a, false)
                Sound.stop(a)
                a.getSystemService(NotificationManager::class.java).cancel(NotificationIds.THEFT)
            }
        }
        guard = TheftGuard()
        sms = TheftSms()
        sirenTestUntil = 0L
        _status.value = TheftGuard.Status()
    }

    // ------------------------------------------------------------------ Receiver

    private fun receiverTick(a: Context, b: PhoneBridge, speed: () -> Double?, shown: TheftGuard.Status?): TheftGuard.Status {
        val now = System.currentTimeMillis()
        val p = prefs(a)
        val linked = LinkHub.status.value.state == LinkStatus.State.LINKED
        val s = guard.tick(now, TheftGuard.Inputs(
            enabled = p.getBoolean(K_ENABLED, false),
            linked = linked,
            atStop = TheftGuard.atStopOf(b.orders.stages.value),
            scooterSpeedKmh = speed(),
            autoStage = p.getBoolean(K_AUTO_STAGE, true),
            autoAway = p.getBoolean(K_AUTO_AWAY, true),
        ))
        _status.value = s
        b.receiverTheft(s, now)
        sensors(a, s.state != State.OFF)
        receiverSound(a, s, now)
        if (s.copy(fix = null) != shown?.copy(fix = null)) notifyState(a, s, receiverSide = true, null)
        sms.due(now, s, linked)?.let { sendSms(a, it) }
        return s
    }

    /** Pixel'den gelen komut (`PhoneBridge.Local.theftCommand`). */
    fun command(c: TheftCommand) {
        val now = System.currentTimeMillis()
        when (c.action) {
            TheftCommand.Action.ARM -> guard.arm(now)
            TheftCommand.Action.DISARM -> guard.disarm(now)
        }
        _status.value = guard.status
    }

    /** Poco'da "Park". Koruma kapalıysa false. */
    fun parkLocal(): Boolean = guard.arm(System.currentTimeMillis()).also { _status.value = guard.status }

    /** Poco'da geri sayım iptali (PIN'siz; henüz koruma yok). */
    fun cancelArmingLocal(): Boolean = guard.cancelArming(System.currentTimeMillis()).also { _status.value = guard.status }

    /** Poco'da kapatma: PIN ayarlıysa doğru PIN gerekir. Ayarlı değilse çağıran 3 sn basılı tutmayı denetler ([hasPin] false). */
    fun disarmLocal(ctx: Context, pin: String?): Boolean {
        val p = prefs(ctx)
        val hash = p.getString(K_PIN_HASH, null)
        val salt = p.getString(K_PIN_SALT, null)?.let(::unhex)
        if (hash != null && salt != null && (pin == null || !TheftPin.matches(pin, salt, hash))) return false
        guard.disarm(System.currentTimeMillis())
        _status.value = guard.status
        return true
    }

    // ------------------------------------------------------------------ Provider

    fun requestArm(): Boolean = bridge?.sendTheft(TheftCommand.Action.ARM) == true
    fun requestDisarm(): Boolean = bridge?.sendTheft(TheftCommand.Action.DISARM) == true

    /** Pixel'de yalnız sesi kapatır (alarm Receiver'da sürer). */
    fun silenceLocal(ctx: Context) {
        _silenced.value = true
        Sound.stop(ctx.applicationContext)
    }

    // ------------------------------------------------------------------ ayarlar

    fun enabled(ctx: Context) = prefs(ctx).getBoolean(K_ENABLED, false)
    fun autoStage(ctx: Context) = prefs(ctx).getBoolean(K_AUTO_STAGE, true)
    fun autoAway(ctx: Context) = prefs(ctx).getBoolean(K_AUTO_AWAY, true)
    /** Koruma devredeyken ([TheftGuard.Status.guarding]) kapatılamaz: önce PIN'le ya da Pixel'den kapatılır. Kapatılamadıysa false. */
    fun setEnabled(ctx: Context, v: Boolean): Boolean {
        if (!v && guarding()) return false
        prefs(ctx).edit().putBoolean(K_ENABLED, v).apply()
        return true
    }

    /** Bu telefonda koruma devrede (Poco'da ayarlar ve rol kilitli). */
    fun guarding(): Boolean = _status.value.guarding
    fun setAutoStage(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(K_AUTO_STAGE, v).apply()
    fun setAutoAway(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(K_AUTO_AWAY, v).apply()
    fun hasPin(ctx: Context) = prefs(ctx).getString(K_PIN_HASH, null) != null

    /** PIN'i değiştirir (yalnız tuzlu özet saklanır). Geçersizse ya da koruma devredeyse false. */
    fun setPin(ctx: Context, pin: String): Boolean {
        if (!TheftPin.valid(pin) || guarding()) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(ctx).edit().putString(K_PIN_SALT, hex(salt)).putString(K_PIN_HASH, TheftPin.hash(pin, salt)).apply()
        return true
    }

    fun smsNumbers(ctx: Context): List<String> = K_SMS.map { prefs(ctx).getString(it, "").orEmpty() }

    /** Koruma devredeyken değişmez (false). */
    fun setSmsNumbers(ctx: Context, numbers: List<String>): Boolean {
        if (guarding()) return false
        val e = prefs(ctx).edit()
        K_SMS.forEachIndexed { i, k -> e.putString(k, numbers.getOrNull(i)?.filter { it.isDigit() || it == '+' }.orEmpty()) }
        e.apply()
        return true
    }

    fun smsAllowed(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** "Siren dene": 3 sn alarm sesi. */
    fun testSiren(ctx: Context) {
        val a = ctx.applicationContext
        sirenTestUntil = System.currentTimeMillis() + SIREN_TEST_MS
        Sound.siren(a)
        scope.launch { delay(SIREN_TEST_MS); if (!_status.value.alarming) Sound.stop(a) }
    }

    // ------------------------------------------------------------------ ses

    private fun receiverSound(a: Context, s: TheftGuard.Status, now: Long) {
        when (s.state) {
            State.WARNING -> Sound.beep(a)
            State.ALARM -> {
                val over = now - s.sinceMs - SIREN_FULL_MS
                if (over < 0 || over % 60_000L < SIREN_ON_MS) Sound.siren(a) else Sound.stop(a)
            }
            else -> if (now >= sirenTestUntil) Sound.stop(a)
        }
    }

    private fun providerSound(a: Context, s: TheftGuard.Status) {
        when (s.state) {
            State.WARNING -> { Sound.stop(a); Sound.vibrate(a, long = false) }
            State.ALARM -> if (!_silenced.value) { Sound.siren(a); Sound.vibrate(a, long = true) }
            else -> Sound.stop(a)
        }
    }

    /** Alarm sesi kanalında (sessiz kipi aşar); alarmdayken ses en yükseğe alınır, bitince eski düzeye döner. */
    private object Sound {
        private var ringtone: Ringtone? = null
        private var tone: ToneGenerator? = null
        private var savedVolume: Int? = null

        @Synchronized
        fun siren(a: Context) {
            if (ringtone?.isPlaying == true) return
            val am = a.getSystemService(AudioManager::class.java)
            if (savedVolume == null) {
                savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
            }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = (ringtone ?: RingtoneManager.getRingtone(a, uri))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                isLooping = true
                play()
            }
        }

        @Synchronized
        fun beep(a: Context) {
            if (tone == null) tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            vibrate(a, long = false)
        }

        fun vibrate(a: Context, long: Boolean) {
            val v = a.getSystemService(Vibrator::class.java) ?: return
            val pattern = if (long) longArrayOf(0, 800, 300, 800, 300, 800) else longArrayOf(0, 300, 200, 300)
            runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
        }

        @Synchronized
        fun stop(a: Context) {
            ringtone?.stop(); ringtone = null
            tone?.release(); tone = null
            savedVolume?.let { vol -> runCatching { a.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, vol, 0) } }
            savedVolume = null
        }
    }

    // ------------------------------------------------------------------ sensörler (Receiver, koruma açıkken)

    private var sensorsOn = false
    private val accel = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
            guard.motion(System.currentTimeMillis(), sqrt((x * x + y * y + z * z).toDouble()))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    // Bütün yöntemler açık: API 29'da diğerleri soyut (varsayılan gövde API 30+).
    private val location = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            guard.fix(TheftGuard.Fix(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy.toDouble() else 999.0, l.time))
        }
        @Deprecated("API 29 uyumu")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun sensors(a: Context, on: Boolean) {
        if (on == sensorsOn) return
        sensorsOn = on
        val sm = a.getSystemService(SensorManager::class.java)
        val lm = a.getSystemService(LocationManager::class.java)
        if (on) {
            sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sm.registerListener(accel, it, SensorManager.SENSOR_DELAY_NORMAL) }
            if (a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                runCatching { lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, location, Looper.getMainLooper()) }
                    .onFailure { Store.sink(a).failure("theft_location", it) }
            }
        } else {
            sm?.unregisterListener(accel)
            runCatching { lm?.removeUpdates(location) }
        }
    }

    // ------------------------------------------------------------------ SMS (Receiver)

    @SuppressLint("MissingPermission")
    private fun sendSms(a: Context, text: String) {
        val numbers = smsNumbers(a).filter { it.length >= 5 }
        if (numbers.isEmpty() || !smsAllowed(a)) { _smsIssue.value = true; return }
        val mgr: SmsManager? = if (Build.VERSION.SDK_INT >= 31) a.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        if (mgr == null) { _smsIssue.value = true; return }
        var ok = true
        for (n in numbers) {
            runCatching { mgr.sendMultipartTextMessage(n, null, mgr.divideMessage(text), null, null) }
                .onFailure { ok = false; Store.sink(a).failure("theft_sms", it) }
        }
        _smsIssue.value = !ok
    }

    // ------------------------------------------------------------------ bildirim (iki telefon)

    private fun notifyState(a: Context, s: TheftGuard.Status, receiverSide: Boolean, remote: TheftState?) {
        val nm = a.getSystemService(NotificationManager::class.java)
        if (s.state == State.OFF) { nm.cancel(NotificationIds.THEFT); return }
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val lang = Store.lang(a)
        nm.createNotificationChannel(NotificationChannel(NotificationIds.CHANNEL_THEFT, TheftText.CHANNEL.get(lang), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        })
        val launch = a.packageManager.getLaunchIntentForPackage(a.packageName)
        val open = launch?.let { PendingIntent.getActivity(a, NotificationIds.THEFT, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val now = System.currentTimeMillis()
        val text = buildString {
            append(TheftText.state(s, lang, now))
            s.fix?.let { f -> append("\n").append(TheftText.LAST_FIX.get(lang).format(TheftText.ago(now - f.atMs, lang), f.accuracyM.toInt())) }
            if (remote == null && !receiverSide) append("\n").append(TheftText.NO_STATE.get(lang))
        }
        val nb = Notification.Builder(a, NotificationIds.CHANNEL_THEFT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(TheftText.TITLE.get(lang))
            .setContentText(text.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(s.state != State.OFF)
            .setCategory(if (s.alarming) Notification.CATEGORY_ALARM else Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(!s.alarming)
        if (s.alarming && open != null) nb.setFullScreenIntent(open, true)
        if (!receiverSide) {
            nb.addAction(action(a, TheftReceiver.ACTION_DISARM, TheftText.DISARM.get(lang)))
            if (s.state == State.ALARM) nb.addAction(action(a, TheftReceiver.ACTION_SILENCE, TheftText.SILENCE.get(lang)))
            s.fix?.let { f -> nb.addAction(Notification.Action.Builder(null, TheftText.OPEN_MAP.get(lang),
                PendingIntent.getActivity(a, NotificationIds.THEFT + 1, Intent(Intent.ACTION_VIEW, android.net.Uri.parse(TheftText.mapUrl(f))), PendingIntent.FLAG_IMMUTABLE)).build()) }
        }
        runCatching { nm.notify(NotificationIds.THEFT, nb.build()) }
    }

    private fun action(a: Context, act: String, title: String): Notification.Action {
        val pi = PendingIntent.getBroadcast(a, act.hashCode(), Intent(a, TheftReceiver::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(null, title, pi).build()
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String): ByteArray? = runCatching { ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
}
