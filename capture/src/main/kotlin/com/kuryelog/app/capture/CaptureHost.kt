package com.kuryelog.app.capture

import android.accessibilityservice.AccessibilityService
import java.util.concurrent.ConcurrentHashMap

/**
 * `:capture`'ın erişilebilirlik servisinde sipariş tarafına bıraktığı işler (CAPTURE.md 7.1): `:orders` uygular (rozet, balonlar,
 * sipariş tiki), `:app` açılışta [CaptureState.host]'a yazar. `TYPE_ACCESSIBILITY_OVERLAY` pencereleri yalnız servis bağlamıyla açılır.
 */
interface CaptureHost {
    /** RS `OrderTracker.init` + `DeliveryReminder` kurulumu (`WatchService.onServiceConnected`). */
    fun onConnected(service: AccessibilityService)
    /** RS tik adım 3–4 (`WatchService.kt:313-319`): vardiyada sipariş tiki, rozet, hatırlatıcılar; dışında pencereler kalkar. */
    fun onTick(service: AccessibilityService, now: Long, shiftOn: Boolean)
    /** RS `WatchService.onDestroy`: rozet ve hatırlatıcı pencereleri kaldırılır. */
    fun onDisconnected(service: AccessibilityService)

    /** Bağlanmamış: servis okur ve satır yazar, sipariş işi yok. */
    object NONE : CaptureHost {
        override fun onConnected(service: AccessibilityService) = Unit
        override fun onTick(service: AccessibilityService, now: Long, shiftOn: Boolean) = Unit
        override fun onDisconnected(service: AccessibilityService) = Unit
    }
}

/** Süreç içi `:capture` durumu (RS `Store.seen/seenAccept`, `WatchService.running`). Diske yazılmaz (CAPTURE.md 6.4). */
object CaptureState {
    @Volatile var host: CaptureHost = CaptureHost.NONE
    /** E7: ekran okuma şu an bağlı (uyarı bunu okur, [ScreenReadingWarning]). */
    @Volatile var running = false
    /** Tanı kartı: her izlenen uygulamanın son okuma ve son "Accept" görülme anı (CAPTURE.md 1.9). */
    val seen = ConcurrentHashMap<String, Long>()
    val seenAccept = ConcurrentHashMap<String, Long>()
    /** Bu süreçte yazılan `screens.jsonl` satırı (RS `Store.screens`). */
    @Volatile var screens = 0
}
