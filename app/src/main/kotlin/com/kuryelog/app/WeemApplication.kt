package com.kuryelog.app

import android.app.Application
import android.content.Context
import com.kuryelog.app.capture.ScreenReadingWarning
import com.kuryelog.app.core.CrashLog
import com.kuryelog.app.core.LogImport
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.core.Store
import com.kuryelog.app.orders.Orders

/** Sürecin girişi: çökme kaydı ([CrashLog]) servislerden ve Activity'den önce kurulur (CORE.md 4.1, RS `RawstatsApp.kt`). */
class WeemApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // ShiftPort durumu tercihlerden (süreç vardiya sürerken yeniden başlamış olabilir).
        ShiftHub.attach(this)
        // Ekran/bildirim olayları ve servis tiki sipariş tarafına (CAPTURE.md 7.1). Bağlama hatası kaydedilir, süreç açılır kalır
        // (bağlanmamış hâlde ekran okuma satır yazar, sipariş işi yapmaz).
        Store.guarded(this, "start") { Orders.bind(this) }
        LogImport.afterImport = Orders::reloadImportedStats
        // Vardiya dinleyicileri, Rawstats `GpsService` sırasıyla (ORDERS.md 4.2): cep nakdi + CASH PENDING → (core.dailyPack, A4.3) →
        // siparişler → (scooter, A5). `onEnd`'de CASH PENDING sonu günlük paketten, o da siparişlerin kapanmasından önce yazılır.
        Store.guarded(this, "start") {
            ShiftHub.listeners.add("cash_pending", Orders.cashPendingHooks(this))
            ShiftHub.listeners.add("orders", Orders.shiftHooks(this))
        }
        // E7 ekran okuma uyarısı (CAPTURE.md 5.2): sonuncu (RS: `GpsService.startShift` sonunda).
        ShiftHub.listeners.add("screen_reading", ScreenReadingWarning(this))
    }
}
