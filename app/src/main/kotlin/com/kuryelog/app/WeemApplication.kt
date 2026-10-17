package com.kuryelog.app

import android.app.Application
import android.content.Context
import com.kuryelog.app.core.CrashLog

/** Sürecin girişi: çökme kaydı ([CrashLog]) servislerden ve Activity'den önce kurulur (CORE.md 4.1, RS `RawstatsApp.kt`). */
class WeemApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(this)
    }
}
