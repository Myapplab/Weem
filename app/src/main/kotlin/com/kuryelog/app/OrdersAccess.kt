package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.core.CourierDb
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.orders.CourierRecords
import com.kuryelog.logic.orders.LogOrdersSource
import com.kuryelog.logic.orders.OrderFlow
import com.kuryelog.logic.orders.OrdersSource
import org.json.JSONObject

/**
 * A3.8: Siparişler sekmesinin ve Kayıtlar gün sayfalarının veri kaynağı. Canlı akış ([flow]) A3.7'de (`WatchService` süreci kurar ve
 * buraya koyar); o zamana kadar gün sayfaları `courier.db` ve gün dosyalarından çizilir (içe aktarılan Rawstats günleri dahil),
 * düzeltme düğmeleri ve "ŞU AN" metni gelmez.
 */
object OrdersAccess {
    @Volatile var flow: OrderFlow? = null
    @Volatile private var db: CourierDb? = null

    private fun db(ctx: Context): CourierDb = db ?: synchronized(this) { db ?: CourierDb(ctx.applicationContext).also { db = it } }

    fun source(ctx: Context): OrdersSource =
        LogOrdersSource(Store.log(ctx), CourierDbRecords(db(ctx)), Store.values(ctx), Clock.Jvm, flow)

    /** Kayıt silindi ya da içe aktarıldı: akış varsa `courier.db` günlüklerden yeniden kurulur; yoksa yapılamaz (false). */
    fun reload(): Boolean = flow?.let { it.reloadImportedStats(); true } ?: false
}

/** [CourierDb] (SQLite, `:core`) → `logic/orders` okuma arayüzü. */
private class CourierDbRecords(private val db: CourierDb) : CourierRecords {
    override fun saveTrip(j: JSONObject) = db.saveTrip(j)
    override fun saveOffer(j: JSONObject, result: String?) = db.saveOffer(j, result)
    override fun trips(day: String?) = db.trips(day)
    override fun offer(id: String) = db.offer(id)
    override fun offerCount(day: String, result: String) = db.offerCount(day, result)
    override fun session() = db.session()
    override fun session(j: JSONObject) = db.session(j)
    override fun <T> batch(block: () -> T): T = db.batch(block)
    override fun clear() = db.clear()
}
