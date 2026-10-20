package com.kuryelog.app.map

import android.app.Activity
import android.content.Intent
import com.kuryelog.logic.map.input.IncomingLink

/**
 * Gelen bağlantı (MAP.md 8.2 `LinkIntake`): `MainActivity` `onCreate`/`onNewIntent`'ten çağrılır. VIEW → `dataString`, SEND → `EXTRA_TEXT`; gönderen
 * `Activity.getReferrer()` (`android-app://<paket>`) yalnız o intent işlenirken bilinir. Harita hazır olmasa da hedef hemen çözülür; graf gelince rota çizilir.
 */
object LinkIntake {
    /** Bağlantıysa işler ve true döner. Geçmişten (son uygulamalar) yeniden açılan intent işlenmez. */
    fun handle(activity: Activity, intent: Intent?): Boolean {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return false
        val view = intent.action == Intent.ACTION_VIEW
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        val referrer = IncomingLink.referrerPackage(runCatching { activity.referrer?.toString() }.getOrNull())
        val link = IncomingLink.of(text, view, referrer, System.currentTimeMillis()) ?: return false
        NavRuntime.get(activity).onLink(link)
        return true
    }
}
