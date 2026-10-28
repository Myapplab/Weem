package com.kuryelog.app.scooter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.kuryelog.app.core.Notices
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.CoreText
import com.kuryelog.logic.core.GpsFix
import com.kuryelog.logic.core.LogTime
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.ShiftDistance
import com.kuryelog.logic.core.ShiftMachine
import com.kuryelog.logic.scooter.ScooterEnergy
import com.kuryelog.logic.scooter.ScooterLinkRules
import com.kuryelog.logic.scooter.ScooterReconnect
import com.kuryelog.logic.scooter.ScooterSamples
import com.kuryelog.logic.scooter.ScooterText

/**
 * Vardiya bağlantısı (SCOOTER.md 5.3, 8.4; RS `ScooterLink.kt` @ 6388c04, `ShiftListeners` dinleyicisi, ORDERS.md 4.2): S37'den beri vardiya
 * scooter'ın Sürüş Paneli kaydını açar (GPS + oturum; eşleştirmeden kalan canlı bağlantı kopmadan kayda çevrilir) ve kayıt vardiyayla biter;
 * yalnız kendi açtığını (ya da sahiplendiğini) kapatır; vardiyanın her 1 Hz
 * örneği gün klasörüne `economics-scooter.jsonl` satırı olur, siparişe enerji adımı verir ([Scooter.steps]) ve D8 odometresini besler;
 * M23 "Scooter verileri alınıyor/alınamıyor" bildirimi her GPS fix'inde sürülür. Kararlar saf [ScooterLinkRules]'ta. Her çağrı korumalı:
 * scooter hatası vardiyayı ve sipariş kaydını durdurmaz. 2 telefonda Provider rolünde bağlantı açılmaz ([providerRole], A8 takar).
 */
// Yalnız uygulama bağlamı tutulur (Activity değil): sızıntı yok.
@SuppressLint("StaticFieldLeak")
object ScooterShiftLink : ShiftMachine.Hooks {
    /** `kuryelog` tercihi (ayar "Vardiyada scooter'a otomatik bağlan", varsayılan açık). */
    const val PREF_AUTO = ScooterLinkRules.PREF_AUTO
    private const val CHANNEL = "scooter_link"

    /** `economics.scooterId` (yoksa `scooter-1`); `:economics` (A4.1) aynı anahtarı kullanır. */
    const val PREF_SCOOTER = "economics.scooterId"
    const val DEFAULT_SCOOTER = "scooter-1"

    /** 2 telefon modunda bu telefon Provider mi (S11)? A8 takar; tek telefonda hep false. */
    @Volatile var providerRole: () -> Boolean = { false }

    @Volatile private var app: Context? = null

    /** Önceki 1 Hz örnek (servisin yazıcı iş parçacığı): siparişe enerji adımı için. */
    @Volatile private var previous: ScooterEnergy.Sample? = null
    private val reconnect = ScooterReconnect()

    /** Idempotent; vardiya dinleyicisi olarak ve her giriş noktasından. */
    fun install(ctx: Context) {
        if (app != null) return
        val c = ctx.applicationContext
        app = c
        Scooter.sampleSink = { s -> sample(c, s) }
    }

    /** Dinleyiciyi `ShiftListeners`'a takarken bağlamı verir: `ShiftHub.listeners.add("scooter", ScooterShiftLink.hooks(ctx))`. */
    fun hooks(ctx: Context): ShiftMachine.Hooks { install(ctx); return this }

    // ---- ShiftMachine.Hooks (ORDERS.md 4.2: sipariş dinleyicisinden sonra) ----

    override fun onNewShift(nowMs: Long, reason: String) { app?.let(::resetShiftOdometer) }

    override fun onRunning() { app?.let(::onShiftStart) }

    override fun onFix(fix: GpsFix, speedMps: Double?, nowMs: Long) { app?.let { reconnectTick(it, speedMps?.let { s -> s * 3.6 }) } }

    override fun odometer(): ShiftDistance.Odo? = app?.let(::shiftOdometer)

    override fun onEnd(startMs: Long, nowMs: Long, reason: String) {
        val c = app ?: return
        onShiftEnd(c)
        resetShiftOdometer(c)
    }

    // ---- M23 ----

    /**
     * Ekim planı madde 23: vardiyadaki her GPS fix'inde ([speedKmh] hızı): canlı bağlantı karelerini kaybettikten sonra "Scooter verileri
     * alınıyor" (geri geldi), ya da sürerken değer gelmiyorsa "Scooter verileri alınamıyor" ([ScooterReconnect]).
     */
    fun reconnectTick(ctx: Context, speedKmh: Double?) {
        if (!Store.shiftOn(ctx) || !Scooter.selected(ctx)) return
        val notice = reconnect.tick(SystemClock.elapsedRealtime(), Scooter.active, Scooter.frame?.monoMs, speedKmh) ?: return
        event(ctx, ScooterLinkRules.reconnectAction(notice), null)
        val ok = notice == ScooterReconnect.Notice.RECEIVING
        val lang = Store.lang(ctx)
        val (title, text) = ScooterLinkRules.reconnectText(notice, lang)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ScooterText.LINK_CHANNEL.get(lang), NotificationManager.IMPORTANCE_DEFAULT))
        val b = Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOnlyAlertOnce(false).setAutoCancel(true)
            .setContentTitle(title).setContentText(text)
        if (ok) b.setTimeoutAfter(RECEIVING_TIMEOUT_MS)
        nm.notify(NotificationIds.SCOOTER_LINK, b.build())
        // Aynı kimlik (4902): "koptu" bildirimi artık yok; sonraki droppedNotice(false) bu M23 bildirimini silmesin.
        droppedShown = false
    }

    // ---- A10.4f (S18 b): bağlantı koptu, boşta ----

    @Volatile private var droppedShown = false

    /**
     * "Scooter bağlantısı koptu" (4902, kanal `scooter_link`): `fail` bağlantıyı boşta bıraktı, 60 sn sonra kendiliğinden yeniden denenir;
     * "Şimdi dene" hemen dener ([ScooterService.RETRY]). [on] false: okuma geri geldi ya da bağlantı durdu, bu bildirim gösterildiyse kalkar.
     * Scooter'a bağlı telefonda çıkar (2 telefonda Receiver; Provider BLE açmaz, S11).
     */
    fun droppedNotice(ctx: Context, on: Boolean) {
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (!on) { if (droppedShown) { droppedShown = false; nm.cancel(NotificationIds.SCOOTER_LINK) }; return }
            val lang = Store.lang(ctx)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ScooterText.LINK_CHANNEL.get(lang), NotificationManager.IMPORTANCE_DEFAULT))
            val retry = PendingIntent.getService(ctx, NotificationIds.SCOOTER_LINK,
                android.content.Intent(ctx, ScooterService::class.java).setAction(ScooterService.RETRY), PendingIntent.FLAG_IMMUTABLE)
            val b = Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setAutoCancel(true)
                .setContentTitle(ScooterText.DROPPED_TITLE.get(lang)).setContentText(ScooterText.DROPPED_TEXT.get(lang))
                .addAction(Notification.Action.Builder(null, ScooterText.RETRY_NOW.get(lang), retry).build())
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                b.setContentIntent(PendingIntent.getActivity(ctx, NotificationIds.SCOOTER_LINK, it, PendingIntent.FLAG_IMMUTABLE))
            }
            nm.notify(NotificationIds.SCOOTER_LINK, b.build())
            droppedShown = true
        }
    }

    // ---- Vardiya başı / sonu ----

    private fun facts(ctx: Context) =
        ScooterLinkRules.Facts(Scooter.selected(ctx), Scooter.paired(ctx), Scooter.permitted(ctx), Scooter.active, providerRole(), Scooter.recording)

    fun autoConnect(ctx: Context): Boolean = Store.prefs(ctx).getBoolean(PREF_AUTO, true)

    /** Android 12+ Bluetooth iznini çalışma zamanında ister; yalnız bir scooter seçildiyse anlamlı. */
    fun needsBluetoothPermission(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 31 && Scooter.selected(ctx) && !Scooter.permitted(ctx)
    @get:SuppressLint("InlinedApi")
    val bluetoothPermissions: Array<String> get() = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    /** Vardiya başı, yapışkan yeniden başlatma da: eşleşmiş scooter hazırsa kaydı aç (S37). */
    fun onShiftStart(ctx: Context) {
        install(ctx)
        val skip = runCatching { ScooterLinkRules.startSkip(autoConnect(ctx), facts(ctx)) }.getOrElse { ScooterLinkRules.Reason.CHECK_FAILED }
        if (skip != null) { event(ctx, ScooterLinkRules.Action.SKIP, skip); return }
        open(ctx)
    }

    /** Vardiya içinde sonradan bağlanılabilir (scooter eşleşti ya da Bluetooth izni verildi); vardiya sonu onu da kapatır. */
    fun canConnectNow(ctx: Context): Boolean = Store.shiftOn(ctx) && runCatching { ScooterLinkRules.canConnectNow(true, facts(ctx)) }.getOrDefault(false)

    fun connectNow(ctx: Context) {
        install(ctx)
        if (Store.shiftOn(ctx)) {
            val skip = runCatching { ScooterLinkRules.openSkip(facts(ctx)) }.getOrElse { ScooterLinkRules.Reason.CHECK_FAILED }
            if (skip != null) event(ctx, ScooterLinkRules.Action.SKIP, skip) else open(ctx)
        }
    }

    private fun open(ctx: Context) {
        // S37: yeni kayıt, canlı bağlantıyı (eşleştirme sonrası) kopmadan kayda çevir ya da süren kaydı sahiplen; kayıt vardiyayla biter.
        // Arka plandan başlatma reddi (ForegroundServiceStartNotAllowedException) kaydedilir; vardiya scooter'sız sürer.
        val how = ScooterLinkRules.openAction(facts(ctx))
        val started = runCatching {
            when (how) {
                ScooterLinkRules.Open.RECORD -> Scooter.startRecording(ctx)
                ScooterLinkRules.Open.UPGRADE -> { Scooter.record(ctx); true }
                ScooterLinkRules.Open.ADOPT -> true
            }
        }
        if (started.getOrDefault(false)) {
            Store.prefs(ctx).edit().putBoolean(ScooterLinkRules.PREF_OWNED, true).apply()
            event(ctx, how.action, null)
            Notices.cancel(ctx, NotificationIds.SCOOTER_AUTO_START)
        } else {
            event(ctx, ScooterLinkRules.Action.FAILED, started.exceptionOrNull()?.javaClass?.simpleName ?: ScooterLinkRules.Reason.REFUSED)
            notConnectedNotice(ctx)
        }
    }

    /** 5102: vardiya açık ama scooter bağlantısı reddedildi; dokununca uygulama açılır (ana kartta "Scooter'a şimdi bağlan"). */
    private fun notConnectedNotice(ctx: Context) {
        runCatching {
            val lang = Store.lang(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(NotificationIds.CHANNEL_AUTO_START, CoreText.AUTO_START_CHANNEL.get(lang), NotificationManager.IMPORTANCE_HIGH))
            val b = Notification.Builder(ctx, NotificationIds.CHANNEL_AUTO_START).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(ScooterText.NOT_CONNECTED_TITLE.get(lang)).setContentText(ScooterText.NOT_CONNECTED_TEXT.get(lang)).setAutoCancel(true)
                .setTimeoutAfter(Notices.AUTO_START_TIMEOUT_MS)
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                b.setContentIntent(PendingIntent.getActivity(ctx, NotificationIds.SCOOTER_AUTO_START, it, PendingIntent.FLAG_IMMUTABLE))
            }
            nm.notify(NotificationIds.SCOOTER_AUTO_START, b.build())
        }
    }

    /** Vardiya sonu: vardiyanın açtığı (ya da sahiplendiği) kaydı kapat (S37: kayıt vardiyayla aynı anda biter). */
    fun onShiftEnd(ctx: Context) {
        val p = Store.prefs(ctx)
        val owned = p.getBoolean(ScooterLinkRules.PREF_OWNED, false)
        if (!owned) return
        p.edit().remove(ScooterLinkRules.PREF_OWNED).apply()
        runCatching {
            val end = ScooterLinkRules.shiftEnd(owned, Scooter.active)
            if (end == ScooterLinkRules.End.STOP) Scooter.stop(ctx)
            ScooterLinkRules.endAction(end)?.let { event(ctx, it, null) }
        }.onFailure { event(ctx, ScooterLinkRules.Action.STOP_FAILED, it.javaClass.simpleName) }
    }

    private fun event(ctx: Context, action: String, reason: String?) {
        Store.event(ctx, ScooterLinkRules.EVENT_TYPE, ScooterLinkRules.event(action, reason))
    }

    // ---- 1 Hz örnek ----

    /**
     * Servisin yazıcı iş parçacığı, bağlantı değer okurken saniyede bir: yalnız vardiyada yazılır (true = saklandı); önceki örnekle arasındaki
     * enerji adımı ekrandaki siparişe gider ([Scooter.steps] → `OrderFlow.scooterEnergy`).
     */
    private fun sample(ctx: Context, s: ScooterEnergy.Sample): Boolean {
        val before = previous
        previous = s
        if (!Store.canRecordScreen(ctx)) return false
        ScooterSamples.stepOf(before, s)?.let { Scooter.emit(it) }
        runCatching { odometer(ctx, s.odometerKm, s.wallMs) }
        val set = Store.prefs(ctx).getString(PREF_SCOOTER, null)
        val row = ScooterSamples.row(ScooterSamples.Values.of(s), set ?: DEFAULT_SCOOTER, if (set != null) "settings" else "default_single_scooter")
        return Store.append(ctx, ScooterSamples.FILE, row.toString(), LogTime.day(s.wallMs))
    }

    // ---- D8 vardiya odometresi ----

    /** D8: vardiyanın odometre aralığı ([ShiftDistance]); tercihte de tutulur (en sık 30 sn'de), süreç ölse de kalır. */
    @Volatile private var odo: ShiftDistance.Odo? = null
    @Volatile private var odoLoaded = false
    private var odoSavedAt = 0L

    private fun saved(ctx: Context): ShiftDistance.Odo? {
        if (!odoLoaded) {
            val p = Store.prefs(ctx)
            odo = ShiftDistance.load(p.getLong(ShiftDistance.PREF_ODO_FIRST, -1), p.getLong(ShiftDistance.PREF_ODO_LAST, -1), p.getLong(ShiftDistance.PREF_ODO_AT, 0))
            odoLoaded = true
        }
        return odo
    }

    @Synchronized
    private fun odometer(ctx: Context, km: Double?, at: Long) {
        val before = saved(ctx)
        val next = ShiftDistance.observe(before, km, at) ?: return
        odo = next
        if (before != null && before.firstKm == next.firstKm && at - odoSavedAt < ODO_SAVE_MS) return
        odoSavedAt = at
        Store.prefs(ctx).edit().putLong(ShiftDistance.PREF_ODO_FIRST, ShiftDistance.meters(next.firstKm))
            .putLong(ShiftDistance.PREF_ODO_LAST, ShiftDistance.meters(next.lastKm)).putLong(ShiftDistance.PREF_ODO_AT, next.lastAt).apply()
    }

    /** Vardiyanın odometre aralığı (bellek, yoksa önceki sürecin kaydı); okuma yoksa null. */
    @Synchronized
    fun shiftOdometer(ctx: Context): ShiftDistance.Odo? = saved(ctx)

    /** Vardiya başı ve sonu: yeni vardiya okumasız başlar. */
    @Synchronized
    fun resetShiftOdometer(ctx: Context) {
        odo = null
        odoLoaded = true
        odoSavedAt = 0L
        Store.prefs(ctx).edit().remove(ShiftDistance.PREF_ODO_FIRST).remove(ShiftDistance.PREF_ODO_LAST).remove(ShiftDistance.PREF_ODO_AT).apply()
    }

    /** Ana kart satırı (TR/AZ): bağlantı durumu ya da canlı pil, hız, güç ve bu siparişin enerjisi. */
    fun status(ctx: Context): String {
        val live = Scooter.freshFrame()?.let { ScooterLinkRules.Live(it, Scooter.trip.stats()) }
        return ScooterLinkRules.status(facts(ctx), autoConnect(ctx), Store.shiftOn(ctx), live, Store.lang(ctx))
    }

    private const val RECEIVING_TIMEOUT_MS = 15_000L
    private const val ODO_SAVE_MS = 30_000L
}
