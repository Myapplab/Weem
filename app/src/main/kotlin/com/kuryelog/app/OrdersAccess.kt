package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.core.Shift
import com.kuryelog.app.core.Store
import com.kuryelog.app.orders.Orders
import com.kuryelog.logic.orders.LogOrdersSource
import com.kuryelog.logic.orders.OrdersSource

/**
 * A3.8: Siparişler sekmesinin ve Kayıtlar gün sayfalarının veri kaynağı: süreçteki tek [com.kuryelog.logic.orders.OrderFlow]
 * ([Orders.flow], A3.7) ve onun `courier.db` bağlantısı. Canlı kayıt saklıyı ezer; düzeltme düğmeleri akışa gider.
 */
object OrdersAccess {
    fun source(ctx: Context): OrdersSource =
        LogOrdersSource(Store.log(ctx), Orders.records(ctx), Store.values(ctx), Shift.AndroidClock, Orders.flow(ctx))

    /** Kayıt silindi: `courier.db` günlüklerden yeniden kurulur (içe aktarmadan sonrası `LogImport.afterImport` ile). */
    fun reload(ctx: Context) = Orders.reloadImportedStats(ctx)
}
