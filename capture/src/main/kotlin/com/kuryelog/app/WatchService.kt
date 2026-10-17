package com.kuryelog.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Wolt/Bolt ekran okuma. Ad değişmez: erişilebilirlik izni `com.kuryelog.app.WatchService` adına bağlı (plan Bölüm 2).
 * Yalnız okur, hiçbir şeye basmaz, ağ kullanmaz. Gövde Aşama 3'te (`com.kuryelog.logic.capture`).
 */
class WatchService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
