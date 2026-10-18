package com.kuryelog.app.economics

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.kuryelog.app.core.Shift
import com.kuryelog.app.core.Store
import com.kuryelog.app.orders.Orders
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.core.ScooterBridge
import com.kuryelog.logic.economics.EconomicsObserver
import com.kuryelog.logic.economics.OfferScoreBook
import com.kuryelog.logic.orders.TripRecord

/**
 * Android `:economics` (ECONOMICS.md 0, 3): süreçte tek [EconomicsObserver] (RS `OrderTracker`'ın ekonomi kısmı) ve onun ortamı (gün
 * dosyaları, `kuryelog` tercihleri, saat, scooter köprüsü); E4 okuyucusu [OfferScoreBook]. `:app` açılışta [bind] ile `:orders`
 * akışına bağlar (akışın ilk girişinden önce). Ağ yok.
 */
// Yalnız uygulama bağlamı tutulur (Activity değil): sızıntı yok.
@SuppressLint("StaticFieldLeak")
object Economics {
    @Volatile private var instance: EconomicsObserver? = null
    @Volatile private var book: OfferScoreBook? = null

    /** Süreçteki tek ekonomi gözlemcisi (ilk çağrıda kurulur). */
    fun observer(ctx: Context): EconomicsObserver = instance ?: synchronized(this) {
        instance ?: EconomicsObserver(AndroidEnv(ctx.applicationContext)).also { instance = it }
    }

    /** `:app` açılışta, `Orders.bind`'dan önce: kancalar ve teklif özeti eklentisi `:orders` akışına (ECONOMICS.md 3). */
    fun bind(ctx: Context) {
        val o = observer(ctx)
        Orders.bindEconomics(o, o)
    }

    /** E4: puanlar ve benim puanım (Siparişler → "Teklif puanı", sipariş kartı). */
    fun scores(ctx: Context): OfferScoreBook = book ?: synchronized(this) {
        book ?: OfferScoreBook(observer(ctx), Store.log(ctx)::day, Shift.AndroidClock::wallMs).also { book = it }
    }

    /** Sipariş kartının "Teklif puanı" satırı (`OrdersSource.offerScoreLine`); teklifsiz siparişte null. */
    fun cardLine(ctx: Context, trip: TripRecord, tl: (String, String) -> String): String? = scores(ctx).cardLine(trip, tl)

    private class AndroidEnv(private val ctx: Context) : EconomicsObserver.Env {
        override val log: LogStore get() = Store.log(ctx)
        override val prefs: KuryelogPrefs.Values = Store.values(ctx)
        override val clock: Clock = Shift.AndroidClock
        /** A5.3 köprüsü (`Orders.bindScooter`): bağlı değilse `NONE` → enerji `not_measured`. Hava A6'ya kadar `NONE`. */
        override val scooter: ScooterBridge get() = Orders.scooter
        override fun monoNs(): Long = SystemClock.elapsedRealtimeNanos()
    }
}
