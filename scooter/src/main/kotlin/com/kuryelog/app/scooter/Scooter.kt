package com.kuryelog.app.scooter

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.kuryelog.logic.core.EnergyStep
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.scooter.NinebotDashboard
import com.kuryelog.logic.scooter.ScooterBridgeImpl
import com.kuryelog.logic.scooter.ScooterEnergy
import com.kuryelog.logic.scooter.ScooterLinkRules
import com.kuryelog.logic.scooter.ScooterLiveMapper
import com.kuryelog.logic.scooter.TripMeter
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Süreçteki scooter durumu ve dış yüzü (NB `SurusPaneli.java` + `RecordingService`'in statik alanları; SCOOTER.md 12.1, 12.2):
 * `:scooter`'ın dışına yalnız [bridge] (`ScooterBridge`) ve vardiya dinleyicisi ([ScooterShiftLink]) çıkar. Servis ([ScooterService])
 * ve vardiya bağı bu nesneyi doldurur. Salt okuma: scooter'a yazma komutu yok.
 */
object Scooter {
    /** Servis çalışıyor ve scooter bağlantısı açık ya da kuruluyor (canlı panel, kayıtlı ya da kayıtsız). */
    @Volatile var active = false
        internal set

    /** Bir kayıt açık: SQLite oturumu ve GPS. Yalnız canlı panel kipinde false (depoya hiçbir şey yazılmaz). */
    @Volatile var recording = false
        internal set

    /** Eski kayıt silinirken kayıt başlamaz. */
    @Volatile var maintenance = false

    @Volatile var summary = "Kayıt kapalı"
        internal set

    @Volatile var warning = ""
        internal set

    /** Ayar durum paneli açıkken true: saniyelik tik özet metnini yalnız o zaman kurar. */
    @Volatile var summaryWatched = false

    /** Son panel karesi; scooter değeri gelmiyorsa null. */
    @Volatile var frame: NinebotDashboard.Snapshot? = null
        internal set

    /** Ana uygulama son örneği vardiya kaydına yazdı (SampleSink true döndü). */
    @Volatile var hostLogging = false
        internal set

    /** 1 Hz örnek alıcısı (vardiya kaydı); servisin yazıcı iş parçacığında çağrılır, hatası bağlantıyı durdurmaz. true = saklandı. */
    @Volatile var sampleSink: ((ScooterEnergy.Sample) -> Boolean)? = null

    /** Çalışan ve önceki kayıtların 1 Hz örnekleri (bu süreçte); enerji bloğu bunlardan kurulur. */
    val energy = ScooterEnergy()

    /** Bağlantı başından ya da sipariş kabulünden beri toplamlar; küçük panel ve bildirim bunu gösterir. */
    val trip = TripMeter()

    /** Ana iş parçacığında her taze kare için çağrılır (canlı panel, yüzen panel; A5.4); bağlantı düşünce null. */
    val frameListeners: MutableSet<(NinebotDashboard.Snapshot?) -> Unit> = CopyOnWriteArraySet()

    /** Ekranda görünenler ([BleLink] okuma temposunu ve bağlantı önceliğini bunlara göre seçer); A5.4 canlı panel ve top bunları açar. */
    object Viewers : BleLink.Viewers {
        @Volatile var dashboard = false
        @Volatile var ball = false
        override fun dashboardVisible() = dashboard
        override fun ballVisible() = ball
    }

    private val liveState = MutableStateFlow<ScooterLive?>(null)
    private val stepFlow = MutableSharedFlow<EnergyStep>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Canlı değerler ([ScooterBridge.live]); bağlantı yokken `linkActive=false`, hiç başlamadıysa null. */
    val live: StateFlow<ScooterLive?> = liveState.asStateFlow()

    /** Her saniyelik adım ([ScooterBridge.steps]); yalnız servis okurken. */
    val steps: SharedFlow<EnergyStep> = stepFlow.asSharedFlow()

    internal fun emit(step: EnergyStep) { stepFlow.tryEmit(step) }

    /** Kare ve sayaçtan [live] yeniden kurulur (saniyede bir ve bağlantı değişince). */
    internal fun publishLive() {
        val a = active
        if (!a && liveState.value == null) return
        liveState.value = ScooterLiveMapper.map(a, frame, if (a) trip.stats() else null, SystemClock.elapsedRealtime(), System.currentTimeMillis())
    }

    /** Taze (≤15 sn) son kare; yoksa null. */
    fun freshFrame(): NinebotDashboard.Snapshot? = frame?.takeIf { SystemClock.elapsedRealtime() - it.monoMs in 0..ScooterLinkRules.FRESH_MS }

    /** `:orders`, `:economics`, `:map` için tek dikiş (plan 4.3); 2 telefonda `:link` bunun uzak uygulamasını takar. */
    val bridge: ScooterBridge by lazy {
        ScooterBridgeImpl(
            object : ScooterBridgeImpl.Source {
                override fun energy(fromWallMs: Long, toWallMs: Long): Map<String, Any?>? = this@Scooter.energy.block(fromWallMs, toWallMs)
                override fun batteryPct(): Double? = freshFrame()?.batteryPct
                override fun orderAccepted(tripId: String, atWallMs: Long) = trip.reset(tripId, atWallMs)
                override fun orderDone(tripId: String, atWallMs: Long) { trip.end(tripId, atWallMs) }
            },
            live, steps,
        )
    }

    // ---- Sürüş Paneli dış yüzünün karşılıkları (NB `SurusPaneli`) ----

    /** Bir scooter tarama listesinden seçildi. */
    fun selected(ctx: Context): Boolean = !LoggerPreferences.get(ctx).getString("device", "").isNullOrEmpty()

    /** Seçili scooter'ın kayıtlı eşleşme anahtarı var; yoksa bağlantı yalnız "önce eşleştir"e varır ve hiçbir şey okumaz. */
    fun paired(ctx: Context): Boolean {
        val d = LoggerPreferences.get(ctx).getString("device", "") ?: ""
        return d.isNotEmpty() && KeyVault(ctx).has(d)
    }

    /** Canlı bağlantının çalışma zamanı izinleri verilmiş (Bluetooth connect; Android 12 öncesinde konum). */
    fun permitted(ctx: Context): Boolean = ScooterService.permissions(ctx, true)

    /** Önceki bir taramada bulunan harici batarya kartları ("06"); bulunana dek "". */
    fun externalBattery(ctx: Context): String = LoggerPreferences.get(ctx).getString("external_bms_boards", "") ?: ""

    /** Bir kerelik harici batarya taraması sonraki bağlantıda çalışacak. */
    fun externalProbePending(ctx: Context): Boolean = LoggerPreferences.get(ctx).getBoolean("battery_probe", false)

    /** Seçili scooter için kayıtsız canlı panel; çağıran Bluetooth izinlerine sahip. Seçili scooter yoksa ya da bağlantı zaten varsa false. */
    fun startLive(ctx: Context): Boolean {
        if (active || !selected(ctx) || !ScooterService.permissions(ctx, true)) return false
        ctx.startForegroundService(ScooterService.startIntent(ctx, pair = false, live = true))
        return true
    }

    /** Çalışan canlı paneli bağlantıyı düşürmeden kayda çevirir. */
    fun record(ctx: Context) {
        if (active && !recording) ctx.startService(Intent(ctx, ScooterService::class.java).setAction(ScooterService.RECORD))
    }

    fun stop(ctx: Context) {
        if (active) ctx.startService(Intent(ctx, ScooterService::class.java).setAction(ScooterService.STOP))
    }
}
