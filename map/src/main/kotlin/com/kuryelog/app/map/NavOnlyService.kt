package com.kuryelog.app.map

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.map.nav.NavText

/**
 * Yalnız-navigasyon ön plan servisi (MAP.md 7.12, 7.17 SORU-N4): gün bittiğinde rota açıksa konumun arka planda sürmesi için (≤90 dk, [NavOnlyController]).
 * Konumu kendisi istemez: `NavLocation` aynı süreçte sürer, bu servis süreci ön planda tutar. Bildirim 7402, kanal `navigation` (LOW).
 * Android 12+ arka plandan ön plan servisi başlatmayı reddedebilir: o zaman navigasyon yalnız uygulama öndeyken sürer (telefonda denenecek).
 */
class NavOnlyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val ok = runCatching { startForeground(NotificationIds.NAV_ONLY_FGS, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) }.isSuccess
        if (!ok) stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        private const val ACTION_STOP = "com.kuryelog.app.map.NAV_ONLY_STOP"

        fun set(ctx: Context, on: Boolean) {
            val i = Intent(ctx, NavOnlyService::class.java)
            runCatching {
                if (on) ctx.startForegroundService(i) else ctx.startService(i.setAction(ACTION_STOP))
            }
        }

        private fun notification(ctx: Context): Notification {
            val lang = Lang.resolve(ctx.getSharedPreferences("kuryelog", Context.MODE_PRIVATE).getString(Lang.PREF_KEY, null), java.util.Locale.getDefault().language)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(NotificationIds.CHANNEL_NAVIGATION, NavText.NAV_CHANNEL.get(lang), NotificationManager.IMPORTANCE_LOW))
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?.let { PendingIntent.getActivity(ctx, NotificationIds.NAV_ONLY_FGS, it, PendingIntent.FLAG_IMMUTABLE) }
            return Notification.Builder(ctx, NotificationIds.CHANNEL_NAVIGATION)
                .setContentTitle(NavText.NAV_ONLY_TITLE.get(lang))
                .setContentText(NavText.NAV_ONLY_TEXT.get(lang))
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }
    }
}
