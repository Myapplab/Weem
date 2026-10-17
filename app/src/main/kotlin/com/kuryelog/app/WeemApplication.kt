package com.kuryelog.app

import android.app.Application
import android.content.Context
import com.kuryelog.app.capture.ScreenReadingWarning
import com.kuryelog.app.core.CrashLog
import com.kuryelog.app.core.ShiftHub

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
        // E7 ekran okuma uyarısı (CAPTURE.md 5.2): vardiya dinleyicilerinin sonuncusu (RS: `GpsService.startShift` sonunda).
        ShiftHub.listeners.add("screen_reading", ScreenReadingWarning(this))
    }
}
