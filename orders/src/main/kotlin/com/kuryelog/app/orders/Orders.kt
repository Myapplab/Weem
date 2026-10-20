package com.kuryelog.app.orders

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import com.kuryelog.app.capture.CaptureState
import com.kuryelog.app.core.CourierDb
import com.kuryelog.app.core.Shift
import com.kuryelog.app.core.Store
import com.kuryelog.logic.capture.Capture
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.core.WeatherPort
import com.kuryelog.logic.core.ShiftMachine
import com.kuryelog.logic.orders.CourierRecords
import com.kuryelog.logic.orders.OfferAnnotator
import com.kuryelog.logic.orders.OrderFlow
import com.kuryelog.logic.orders.OrderObserver
import com.kuryelog.logic.orders.OrderSettings
import com.kuryelog.logic.orders.TripRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Android `:orders` (ORDERS.md 4.14): süreçte tek [OrderFlow] (RS `OrderTracker` nesnesi) ve onun Android'e ait [OrderFlow.Env]'i
 * (`courier.db`, tercihler, gün dosyaları, saat, pil, `Geocoder`, bildirim ve pencere istekleri). `:app` açılışta `Capture.sink`,
 * `CaptureState.host` ve `ShiftListeners`'a bağlar (ORDERS.md 4.2 sırası).
 */
// Yalnız uygulama bağlamı tutulur (Activity değil): sızıntı yok.
@SuppressLint("StaticFieldLeak")
object Orders {
    @Volatile private var app: Context? = null
    @Volatile private var instance: OrderFlow? = null
    @Volatile private var env: AndroidEnv? = null

    /** Süreçteki tek sipariş akışı (ilk çağrıda kurulur). */
    fun flow(ctx: Context): OrderFlow = instance ?: synchronized(this) {
        instance ?: AndroidEnv(ctx.applicationContext.also { app = it }).also { env = it }.let(::OrderFlow).also { instance = it }
    }

    /** Akışın `courier.db` bağlantısı: Siparişler sekmesinin okuyucuları (A3.8) aynı bağlantıyı kullanır. */
    fun records(ctx: Context): CourierRecords { flow(ctx); return env!!.db }

    /** Scooter dikişi (SCOOTER.md 12.2): `:app` bağlayana dek `NONE` (teklif anı pili ve sipariş enerjisi yok). `:orders` `:scooter`'ı tanımaz. */
    @Volatile var scooter: ScooterBridge = ScooterBridge.NONE
        private set
    /** Hava dikişi (R15, MAP.md 10.10): `:app` bağlayana dek `NONE` (teklif satırında hava yok). `:orders` `:map`'i tanımaz. */
    @Volatile var weather: WeatherPort = WeatherPort.NONE
        private set
    fun bindWeather(port: WeatherPort) { weather = port }
    private var stepJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * `:app` açılışta: teklif anında scooter pili ve sipariş başına enerji ([OrderFlow.Env.scooter]). Bağlantı okurken her saniyelik adım
     * ekrandaki siparişe gider ([OrderFlow.scooterEnergy]; vardiya kapalıysa yok sayar). Akış kurallarına dokunmaz; hata kaydedilir.
     */
    fun bindScooter(ctx: Context, bridge: ScooterBridge) {
        scooter = bridge
        stepJob?.cancel()
        val flow = flow(ctx)
        val app = ctx.applicationContext
        stepJob = scope.launch {
            bridge.steps.collect { step -> Store.guarded(app, "scooter_energy") { flow.scooterEnergy(step.usedWh, step.recoveredWh, step.ms, step.complete) } }
        }
    }

    /**
     * Ekonomi dikişi (ECONOMICS.md 3): `:app` bağlayana dek `NONE` (ekonomi satırı yazılmaz, teklif özetinde puan yok). `:orders`
     * `:economics`'i tanımaz. Akışın ilk girişinden önce bağlanmalı (`restart`/`restored` kancaları ilk girişte bir kez çalışır):
     * `WeemApplication.onCreate` başında, [bind]'dan önce.
     */
    @Volatile var observer: OrderObserver = OrderObserver.NONE
        private set
    @Volatile var annotator: OfferAnnotator = OfferAnnotator.NONE
        private set
    fun bindEconomics(observer: OrderObserver, annotator: OfferAnnotator) {
        this.observer = observer
        this.annotator = annotator
    }

    /** `:app` açılışta: ekran ve bildirim olayları bu akışa, erişilebilirlik servisinin sipariş işleri [OrdersHost]'a (CAPTURE.md 7.1). */
    fun bind(ctx: Context) {
        Capture.sink = flow(ctx)
        CaptureState.host = OrdersHost
    }
    /** ORDERS.md 4.2: `ShiftListeners` sırası `:app`'te — önce bu (cep nakdi M15, CASH PENDING M17), `core.dailyPack`'ten sonra [shiftHooks]. */
    fun cashPendingHooks(ctx: Context): ShiftMachine.Hooks = flow(ctx).cashPendingHooks
    /** RS `OrderTracker.start/location/tick/distance/hasOpenOrder/stop` (`GpsService`). */
    fun shiftHooks(ctx: Context): ShiftMachine.Hooks = flow(ctx)
    /** ZIP içe aktarmadan sonra (RS `safely("import") { OrderTracker.reloadImportedStats(this) }`). */
    fun reloadImportedStats(ctx: Context) = flow(ctx).reloadImportedStats()

    /** RS `I18n.tl`. */
    fun tl(ctx: Context, tr: String, az: String): String = if (Store.lang(ctx) == Lang.AZ) az else tr
    /** RS `OrderTracker.f2`. */
    fun f2(n: Double): String = String.format(Locale.US, "%.2f", n)

    private class AndroidEnv(private val ctx: Context) : OrderFlow.Env {
        override val log: LogStore get() = Store.log(ctx)
        override val prefs: KuryelogPrefs.Values = Store.values(ctx)
        override val db: CourierRecords by lazy { AndroidCourierRecords(CourierDb(ctx)) }
        override val scooter: ScooterBridge get() = Orders.scooter
        override val weather: WeatherPort get() = Orders.weather
        override val observer: OrderObserver get() = Orders.observer
        override val annotator: OfferAnnotator get() = Orders.annotator
        override val clock: Clock = Shift.AndroidClock
        override fun importing(): Boolean = Store.importing
        override fun phoneBattery(): Int? = Store.phoneBattery(ctx)
        /** A3.11: anahtar yalnız hata ayıklanabilir (`.dev`) yapıda geçerli; sürüm yapısına ZIP geri yüklemesiyle geçse de soru ve pencereler kapanmaz. */
        override fun shadowMode(): Boolean =
            ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && OrderSettings.shadowMode(prefs)
        override fun tl(tr: String, az: String): String = Orders.tl(ctx, tr, az)
        /** RS `OrderTracker.resolve/resolveRestaurant`: arka planda `Geocoder`, sonuç ana iş parçacığında. */
        override fun geocode(address: String, done: (DoubleArray?) -> Unit) {
            worker.execute {
                val result = runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(ctx, Locale.getDefault()).getFromLocationName(address, 3)?.firstOrNull { it.hasLatitude() && it.hasLongitude() }
                }.getOrNull()
                main.post { Store.guarded(ctx, "geocode") { done(result?.let { doubleArrayOf(it.latitude, it.longitude) }) } }
            }
        }
        override fun askCashTip(trip: TripRecord) { CashTipPrompt.ask(ctx, trip) }
        override fun askPocketCash(shiftStartMs: Long) { PocketCashPrompt.ask(ctx, shiftStartMs) }
        override fun notifyReminder(reminder: OrderFlow.Reminder, update: Boolean) { DeliveryReminder.notify(ctx, reminder, update) }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
}

/** [CourierRecords] üstünde Android `courier.db` (`:core` [CourierDb]). */
class AndroidCourierRecords(private val db: CourierDb) : CourierRecords {
    override fun saveTrip(j: JSONObject) = db.saveTrip(j)
    override fun saveOffer(j: JSONObject, result: String?) = db.saveOffer(j, result)
    override fun trips(day: String?): List<JSONObject> = db.trips(day)
    override fun offer(id: String): JSONObject? = db.offer(id)
    override fun offerCount(day: String, result: String): Int = db.offerCount(day, result)
    override fun session(): JSONObject? = db.session()
    override fun session(j: JSONObject) = db.session(j)
    override fun <T> batch(block: () -> T): T = db.batch(block)
    override fun clear() = db.clear()
}
