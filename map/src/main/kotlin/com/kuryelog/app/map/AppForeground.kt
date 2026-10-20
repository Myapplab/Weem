package com.kuryelog.app.map

/** WEEM ekranda mı (`MainActivity` başlayıp durdukça): ekrandayken hatırlatıcı ve görev yalnız uygulama içi kart olur (CORE.md 3.1, `NotifyPolicy.inAppOnly`). */
object AppForeground {
    @Volatile var on: Boolean = false
}
