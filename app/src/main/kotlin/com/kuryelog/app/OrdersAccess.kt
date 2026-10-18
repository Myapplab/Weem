package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.core.Shift
import com.kuryelog.app.core.Store
import com.kuryelog.app.economics.Economics
import com.kuryelog.logic.economics.DaySummaries
import com.kuryelog.logic.economics.OfferScoreBook
import com.kuryelog.app.orders.Orders
import com.kuryelog.logic.orders.DayNetSummary
import com.kuryelog.logic.orders.LogOrdersSource
import com.kuryelog.logic.orders.OrderHistory
import com.kuryelog.logic.orders.OrdersSource
import com.kuryelog.logic.orders.TripRecord

/**
 * A3.8: Siparişler sekmesinin ve Kayıtlar gün sayfalarının veri kaynağı: süreçteki tek [com.kuryelog.logic.orders.OrderFlow]
 * ([Orders.flow], A3.7) ve onun `courier.db` bağlantısı. Canlı kayıt saklıyı ezer; düzeltme düğmeleri akışa gider.
 */
object OrdersAccess {
    fun source(ctx: Context): OrdersSource = withDaySummaries(withOfferScores(
        LogOrdersSource(Store.log(ctx), Orders.records(ctx), Store.values(ctx), Shift.AndroidClock, Orders.flow(ctx)), Economics.scores(ctx)), Economics.daySummaries(ctx))

    /** E4 (A4.1): Siparişler → "Teklif puanı" sayfası ve puan sorusu. */
    fun offerScores(ctx: Context): OfferScoreBook = Economics.scores(ctx)

    /** Sipariş kartının "Teklif puanı" satırı `:economics`'ten (`:orders` onu tanımaz). */
    fun withOfferScores(src: OrdersSource, book: OfferScoreBook): OrdersSource = object : OrdersSource by src {
        override fun offerScoreLine(trip: TripRecord, tl: (String, String) -> String): String? = book.cardLine(trip, tl)
    }

    /** N4/N8 (A4.2): gün sonu raporu kartı ve enerji maliyeti `:economics`'ten (`:orders` onu tanımaz). */
    fun withDaySummaries(src: OrdersSource, sums: DaySummaries): OrdersSource = object : OrdersSource by src {
        override fun daySummary(day: OrderHistory.Day, today: Boolean, tl: (String, String) -> String): DayNetSummary = sums.summary(day, today, tl)
    }

    /** Kayıt silindi: `courier.db` günlüklerden yeniden kurulur (içe aktarmadan sonrası `LogImport.afterImport` ile). */
    fun reload(ctx: Context) = Orders.reloadImportedStats(ctx)
}
