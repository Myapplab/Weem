package com.kuryelog.app

import android.app.Application
import android.content.Context
import com.kuryelog.app.capture.ScreenReadingWarning
import com.kuryelog.app.core.CrashLog
import com.kuryelog.app.core.LogImport
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.core.Store
import com.kuryelog.app.economics.Economics
import com.kuryelog.app.map.RideRuntime
import com.kuryelog.app.map.WeatherRuntime
import com.kuryelog.app.orders.Orders
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.app.scooter.ScooterShiftLink

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
        // Ekonomi dikişi (ECONOMICS.md 3): akışın ilk girişinden önce (kancalar `restart`/`restored` ilk girişte bir kez); `:orders`
        // `:economics`'i tanımaz, bağı burada kurulur. Hata kaydedilir: bağlanmazsa akış ekonomi satırı yazmadan çalışır.
        Store.guarded(this, "start") { Economics.bind(this) }
        // Ekran/bildirim olayları ve servis tiki sipariş tarafına (CAPTURE.md 7.1). Bağlama hatası kaydedilir, süreç açılır kalır
        // (bağlanmamış hâlde ekran okuma satır yazar, sipariş işi yapmaz).
        Store.guarded(this, "start") { Orders.bind(this) }
        // Scooter dikişi: teklif anı pili ve sipariş başına enerji (R14); `:orders` `:scooter`'ı tanımaz, bağı burada kurulur.
        Store.guarded(this, "start") { Orders.bindScooter(this, Scooter.bridge) }
        // Harita çalışma zamanı (A6.7): hava (Y10), hız/batarya tahmini (Y9) ve menzil (N5). Hava ve menzil köprüleri `:orders` üzerinden `:economics`'e gider
        // (R15, N5); `:orders` ve `:economics` `:map`'i tanımaz, bağ burada kurulur. `Economics.bind`/`Orders.bind`'dan sonra: yalnız köprü alanları atanır.
        Store.guarded(this, "start") {
            WeatherRuntime.bind(this)
            RideRuntime.bind(this, Scooter.bridge)
            Orders.bindWeather(WeatherRuntime.port)
            Orders.bindRange(RideRuntime.range)
        }
        LogImport.afterImport = Orders::reloadImportedStats
        // Vardiya dinleyicileri, Rawstats `GpsService` sırasıyla (ORDERS.md 4.2): cep nakdi + CASH PENDING → günlük paket (R17) → şifreli
        // yedek (N3) → siparişler → scooter (A5.3). `onEnd`'de CASH PENDING sonu günlük paketten, o da siparişlerin kapanmasından önce yazılır.
        Store.guarded(this, "start") {
            ShiftHub.listeners.add("cash_pending", Orders.cashPendingHooks(this))
            ShiftHub.listeners.add("daily_pack", DailyPackHook(this))
            // N3 (S22): günlük paketten sonra, siparişler kapanmadan; kendi iş parçacığında.
            ShiftHub.listeners.add("encrypted_backup", EncryptedBackupHook(this))
            ShiftHub.listeners.add("orders", Orders.shiftHooks(this))
        }
        // Scooter bağlantısı (SCOOTER.md 5.3): vardiya canlı bağlantıyı açar/kapatır, M23, D8 odometre; siparişlerden sonra (ORDERS.md 4.2).
        Store.guarded(this, "start") { ShiftHub.listeners.add("scooter", ScooterShiftLink.hooks(this)) }
        // A4.2 gün sonu raporu: siparişler ve scooter kapandıktan sonra (kapanan sipariş ve son enerji hesapta).
        Store.guarded(this, "start") { ShiftHub.listeners.add("day_report", DayReportHook(this)) }
        // E7 ekran okuma uyarısı (CAPTURE.md 5.2): sonuncu (RS: `GpsService.startShift` sonunda).
        ShiftHub.listeners.add("screen_reading", ScreenReadingWarning(this))
    }
}
