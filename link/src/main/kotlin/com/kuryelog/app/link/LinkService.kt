package com.kuryelog.app.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.link.LinkText
import com.kuryelog.logic.link.LinkView

/**
 * 2 telefon bağlantısının ön plan servisi (LINK.md 4.7): bağlantı [LinkHub]'da, aynı süreçte sürer; bu servis süreci ekran kapalıyken
 * de ayakta tutar. Mod açık ve eşleşmişken (ya da eşleştirme penceresinde) çalışır. Bildirim 7403, kanal `link` (LOW, ongoing),
 * metni bağlantı durumu. Tür `connectedDevice` (izin: `CHANGE_NETWORK_STATE`, olağan izin). Android 12+ arka plandan başlatmayı
 * reddedebilir: o zaman bağlantı yalnız uygulama açıkken sürer (cihazda doğrulanmadı).
 */
class LinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val ok = runCatching { startForeground(NotificationIds.LINK_FGS, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }.isSuccess
        // Reddedildi: bayrak düşer ki sonraki `set(true)` (ör. uygulama öne gelince) yeniden denesin.
        if (!ok) { on = false; stopSelf() }
        return START_STICKY
    }

    companion object {
        private const val ACTION_STOP = "com.kuryelog.app.link.LINK_STOP"
        @Volatile private var on = false

        fun set(ctx: Context, run: Boolean) {
            if (run == on) return
            on = run
            val i = Intent(ctx, LinkService::class.java)
            runCatching {
                if (run) ctx.startForegroundService(i) else ctx.startService(i.setAction(ACTION_STOP))
            }.onFailure { if (run) on = false }
        }

        /** Bildirim metnini bağlantı durumuyla yeniler (servis çalışıyorsa). */
        fun refresh(ctx: Context) {
            if (!on) return
            runCatching { ctx.getSystemService(NotificationManager::class.java).notify(NotificationIds.LINK_FGS, notification(ctx)) }
        }

        private fun notification(ctx: Context): Notification {
            val lang = Store.lang(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(NotificationIds.CHANNEL_LINK, LinkText.SERVICE_CHANNEL.get(lang), NotificationManager.IMPORTANCE_LOW))
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?.let { PendingIntent.getActivity(ctx, NotificationIds.LINK_FGS, it, PendingIntent.FLAG_IMMUTABLE) }
            val lines = LinkView.lines(LinkHub.status.value, LinkHub.peer.value != null, LinkHub.role.value, LinkHub.bridge.value?.peerRole?.value,
                LinkHub.peerInfo.value, LinkHub.peerInfo.value?.appName ?: "", lang)
            return Notification.Builder(ctx, NotificationIds.CHANNEL_LINK)
                .setContentTitle(LinkText.TITLE.get(lang) + " · " + LinkView.role(LinkHub.role.value, lang))
                .setContentText(lines.firstOrNull().orEmpty())
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }
}
