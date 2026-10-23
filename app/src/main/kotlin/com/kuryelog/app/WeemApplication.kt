package com.kuryelog.app

import android.app.Application
import android.content.Context
import com.kuryelog.app.capture.ScreenReadingWarning
import com.kuryelog.app.core.CrashLog
import com.kuryelog.app.core.LogImport
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.app.core.Store
import com.kuryelog.app.core.WeemFonts
import com.kuryelog.app.economics.Economics
import com.kuryelog.app.map.BatteryRuntime
import com.kuryelog.app.map.DatasetRecording
import com.kuryelog.app.map.MapSettingsRepo
import com.kuryelog.app.map.NavRuntime
import com.kuryelog.app.map.WeatherRuntime
import com.kuryelog.app.orders.Orders
import com.kuryelog.app.scooter.ScooterShiftLink

/** Sürecin girişi: çökme kaydı ([CrashLog]) servislerden ve Activity'den önce kurulur (CORE.md 4.1, RS `RawstatsApp.kt`). */
class WeemApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // A10.4d: Sürüş Paneli çizimleri ve sistem üstü pencereler de Inter yazar (dosyalar yalnız :app'te).
        WeemFonts.install { w ->
            resources.getFont(when (w) {
                WeemFonts.REGULAR -> R.font.inter_regular
                WeemFonts.MEDIUM -> R.font.inter_medium
                WeemFonts.SEMIBOLD -> R.font.inter_semibold
                WeemFonts.BOLD -> R.font.inter_bold
                else -> R.font.inter_extrabold
            })
        }
        // A10.4h: geri yüklenen harita verisi, harita çalışma zamanları (NavRuntime, hava…) kurulmadan önce.
        Store.guarded(this, "encrypted_restore_map") { EncryptedBackups.applyPendingMap(this) }
        // S38: anahtarlı APK'da harita Google Maps (eski varsayılan vektör bir kez Google'a döner; sonra kuryenin seçimi).
        Store.guarded(this, "map_style") { MapSettingsRepo.preferGoogle(this, BuildConfig.MAPS_ENABLED) }
        // ShiftPort durumu tercihlerden (süreç vardiya sürerken yeniden başlamış olabilir).
        ShiftHub.attach(this)
        // Ekonomi dikişi (ECONOMICS.md 3): akışın ilk girişinden önce (kancalar `restart`/`restored` ilk girişte bir kez); `:orders`
        // `:economics`'i tanımaz, bağı burada kurulur. Hata kaydedilir: bağlanmazsa akış ekonomi satırı yazmadan çalışır.
        Store.guarded(this, "start") { Economics.bind(this) }
        // Ekran/bildirim olayları ve servis tiki sipariş tarafına (CAPTURE.md 7.1). Bağlama hatası kaydedilir, süreç açılır kalır
        // (bağlanmamış hâlde ekran okuma satır yazar, sipariş işi yapmaz).
        Store.guarded(this, "start") { Orders.bind(this) }
        // A6.7: hava (R15, teklif satırı); `:orders` `:map`'i tanımaz, bağ burada.
        Store.guarded(this, "start") { Orders.bindWeather(WeatherRuntime.get(this).port) }
        // A10.4e: N5 menzil girdileri (batarya + eve sürüş, telefondaki harita verisiyle); `:economics` `:map`'i tanımaz.
        Store.guarded(this, "start") { Orders.bindRange(BatteryRuntime.get(this).range) }
        // A6.9: gün motoru, sürüş günlüğü, gün raporu, görev ve hatırlatıcı çalışma zamanları süreçle birlikte kalkar (vardiya arka planda sürerken ekran kapalı olabilir).
        Store.guarded(this, "start") { NavRuntime.get(this) }
        // Scooter dikişi (teklif anı pili, sipariş enerjisi R14, batarya tahmininin çapası), sipariş olayları → harita aşama izleyicisi ve
        // A8.3 2 telefon modu: role göre yerel ya da uzak portlar (TwoPhone; Provider'da scooter BLE açılmaz, S11).
        Store.guarded(this, "start") { TwoPhone.start(this) }
        LogImport.afterImport = Orders::reloadImportedStats
        // Vardiya dinleyicileri, Rawstats `GpsService` sırasıyla (ORDERS.md 4.2): cep nakdi + CASH PENDING → günlük paket (R17) → şifreli
        // yedek (N3) → siparişler → scooter (A5.3). `onEnd`'de CASH PENDING sonu günlük paketten, o da siparişlerin kapanmasından önce yazılır.
        Store.guarded(this, "start") {
            ShiftHub.listeners.add("cash_pending", Orders.cashPendingHooks(this))
            ShiftHub.listeners.add("daily_pack", DailyPackHook(this))
            // N3 (S22): günlük paketten sonra, siparişler kapanmadan; kendi iş parçacığında.
            ShiftHub.listeners.add("encrypted_backup", EncryptedBackupHook(this))
            ShiftHub.listeners.add("orders", Orders.shiftHooks(this))
            // A6.9 teklif veri seti (Y19): siparişlerin gün klasörü tamamlandıktan sonra gün toplanır ve mühürlenir (MAP.md 13.11).
            ShiftHub.listeners.add("dataset", DatasetRecording.hooks(this))
        }
        Store.guarded(this, "start") { DatasetRecording.start(this) }
        // Scooter bağlantısı (SCOOTER.md 5.3): vardiya canlı bağlantıyı açar/kapatır, M23, D8 odometre; siparişlerden sonra (ORDERS.md 4.2).
        Store.guarded(this, "start") { ShiftHub.listeners.add("scooter", ScooterShiftLink.hooks(this)) }
        // A4.2 gün sonu raporu: siparişler ve scooter kapandıktan sonra (kapanan sipariş ve son enerji hesapta).
        Store.guarded(this, "start") { ShiftHub.listeners.add("day_report", DayReportHook(this)) }
        // E7 ekran okuma uyarısı (CAPTURE.md 5.2): sonuncu (RS: `GpsService.startShift` sonunda).
        ShiftHub.listeners.add("screen_reading", ScreenReadingWarning(this))
    }
}
