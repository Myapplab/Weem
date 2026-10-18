package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.core.Shift
import com.kuryelog.app.core.Store
import com.kuryelog.app.economics.Economics
import com.kuryelog.logic.economics.OfferScoreBook
import com.kuryelog.app.orders.Orders
import com.kuryelog.logic.orders.LogOrdersSource
import com.kuryelog.logic.orders.OrdersSource
import com.kuryelog.logic.orders.TripRecord

/**
 * A3.8: Siparişler sekmesinin ve Kayıtlar gün sayfalarının veri kaynağı: süreçteki tek [com.kuryelog.logic.orders.OrderFlow]
 * ([Orders.flow], A3.7) ve onun `courier.db` bağlantısı. Canlı kayıt saklıyı ezer; düzeltme düğmeleri akışa gider.
 */
object OrdersAccess {
    fun source(ctx: Context): OrdersSource = withOfferScores(
        LogOrdersSource(Store.log(ctx), Orders.records(ctx), Store.values(ctx), Shift.AndroidClock, Orders.flow(ctx)), Economics.scores(ctx))

    /** E4 (A4.1): Siparişler → "Teklif puanı" sayfası ve puan sorusu. */
    fun offerScores(ctx: Context): OfferScoreBook = Economics.scores(ctx)

    /** Sipariş kartının "Teklif puanı" satırı `:economics`'ten (`:orders` onu tanımaz). */
    fun withOfferScores(src: OrdersSource, book: OfferScoreBook): OrdersSource = object : OrdersSource by src {
        override fun offerScoreLine(trip: TripRecord, tl: (String, String) -> String): String? = book.cardLine(trip, tl)
    }

    /** Kayıt silindi: `courier.db` günlüklerden yeniden kurulur (içe aktarmadan sonrası `LogImport.afterImport` ile). */
    fun reload(ctx: Context) = Orders.reloadImportedStats(ctx)
}
