package com.kuryelog.app

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kuryelog.app.capture.CaptureState
import com.kuryelog.app.core.HomeWatch
import com.kuryelog.app.core.Store
import com.kuryelog.logic.capture.Capture
import com.kuryelog.logic.capture.CaptureRows
import com.kuryelog.logic.capture.CaptureTiming
import com.kuryelog.logic.capture.NotificationDedup

/**
 * Seçili uygulamaların bildirimlerini `notifs.jsonl`'e yazar (R2, CAPTURE.md 4.1; RS `NotifService.kt` @ 6388c04). Ad değişmez:
 * bildirim erişimi `com.kuryelog.app.NotifService` adına bağlı (plan Bölüm 2). Süzgeç ve satır biçimi saf ([NotificationDedup],
 * [CaptureRows]); navigasyon bildiriminden yalnız rota özeti (R16), bellekte.
 */
class NotifService : NotificationListenerService() {
    private val dedup = NotificationDedup()
    /** E7: the system keeps this listener bound, so it drives the home watcher too: leaving home starts the shift without screen reading. */
    private val h = Handler(Looper.getMainLooper())
    private val homeTick = object : Runnable {
        override fun run() { HomeWatch.refresh(this@NotifService); h.postDelayed(this, CaptureTiming.HOME_TICK_MS) }
    }
    override fun onListenerConnected() { super.onListenerConnected(); h.removeCallbacks(homeTick); h.post(homeTick) }
    override fun onListenerDisconnected() { h.removeCallbacks(homeTick); super.onListenerDisconnected() }
    override fun onDestroy() { h.removeCallbacks(homeTick); super.onDestroy() }

    /** 16 Oct: a failure is recorded ([Store.failure]), never thrown. This listener runs in the app's process for every
     *  notification (a navigation app updates its own every second), so one exception here closed the app as soon as it opened. */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) Store.guarded(this, "notification") { posted(sbn) }
    }
    private fun posted(sbn: StatusBarNotification) {
        val ex = sbn.notification.extras
        when (dedup.route(sbn.packageName, Store.targets(this))) {
            NotificationDedup.Route.NAVIGATION -> {
                // Route summary only (remaining distance); navigation text is not written to disk.
                val lines = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT)
                    .mapNotNull { ex.getCharSequence(it)?.toString() }
                Capture.sink.onNavigation(lines, System.currentTimeMillis())
                return
            }
            NotificationDedup.Route.IGNORE -> return
            NotificationDedup.Route.WATCHED -> Unit
        }
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        // Aynı bildirim güncellenip duruyorsa tekrar yazma; izin yokken süzgeç boşalır.
        // Receiver'da (2 telefon) bildirim okunmaz (CaptureState.receiverRole, A10.2).
        if (!dedup.posted(sbn.key, title, text, big, sub, Store.canRecordScreen(this) && !CaptureState.receiverRole())) return
        val o = CaptureRows.posted(Store.log(this), System.currentTimeMillis(), sbn.postTime, sbn.packageName, sbn.key, sbn.isOngoing,
            sbn.notification.channelId, title, text, big, sub)
        Store.append(this, "notifs.jsonl", o.toString())
        Capture.sink.onNotification(sbn.packageName, NotificationDedup.forOrders(title, text, big, sub), sbn.postTime)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) Store.guarded(this, "notification") { removed(sbn) }
    }
    private fun removed(sbn: StatusBarNotification) {
        if (sbn.packageName !in Store.targets(this)) return
        if (!dedup.removed(sbn.key, Store.canRecordScreen(this) && !CaptureState.receiverRole())) return
        Store.append(this, "notifs.jsonl", CaptureRows.removed(Store.log(this), System.currentTimeMillis(), sbn.packageName, sbn.key).toString())
    }
}
