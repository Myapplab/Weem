package com.kuryelog.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kuryelog.logic.core.CoreText
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.ShiftMachine

/**
 * `:core` bildirimleri (G4, CORE.md 3.3; RS `AutoStartNotice`, `GpsService.startFg`). Numaralar yalnız [NotificationIds]'ten;
 * kanal kimlikleri eski uygulamayla aynı.
 */
object Notices {
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
    private fun text(ctx: Context, t: CoreText) = t.get(Store.lang(ctx))

    /** Vardiya ön plan bildirimi (kanal `shift`, LOW, ongoing): dokununca uygulama, eylem "Vardiyayı bitir". */
    fun shift(ctx: Context): Notification {
        nm(ctx).createNotificationChannel(NotificationChannel(NotificationIds.CHANNEL_SHIFT, text(ctx, CoreText.SHIFT_CHANNEL), NotificationManager.IMPORTANCE_LOW))
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.let { PendingIntent.getActivity(ctx, NotificationIds.SHIFT_FGS, it, PendingIntent.FLAG_IMMUTABLE) }
        val stop = PendingIntent.getService(ctx, STOP_REQUEST, Intent(ctx, ShiftService::class.java).setAction(ShiftMachine.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(ctx, NotificationIds.CHANNEL_SHIFT)
            .setContentTitle(text(ctx, CoreText.SHIFT_NOTIF_TITLE))
            .setContentText(text(ctx, CoreText.SHIFT_NOTIF_TEXT))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, text(ctx, CoreText.SHIFT_STOP_ACTION), stop).build())
            .build()
    }

    /** 5101: evden çıkış görüldü ama Android vardiyayı arka plandan başlatmadı; dokunuş aynı `HOME_START`'ı gönderir. */
    fun homeStart(ctx: Context, revision: Long) {
        val start = PendingIntent.getForegroundService(ctx, NotificationIds.HOME_AUTO_START, Home.startIntent(ctx, revision),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        autoStart(ctx, NotificationIds.HOME_AUTO_START, text(ctx, CoreText.HOME_LEFT_TITLE), text(ctx, CoreText.HOME_LEFT_TEXT), start)
    }

    /** 5103 (H4): Android vardiya servisini yeniden başlatmayı reddetti; dokunuş aynı vardiyayı sürdürür. Zaman aşımı yok. */
    fun shiftInterrupted(ctx: Context) {
        val resume = PendingIntent.getForegroundService(ctx, NotificationIds.SHIFT_INTERRUPTED,
            Intent(ctx, ShiftService::class.java).setAction(ShiftMachine.ACTION_START), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        autoStart(ctx, NotificationIds.SHIFT_INTERRUPTED, text(ctx, CoreText.SHIFT_INTERRUPTED_TITLE), text(ctx, CoreText.SHIFT_INTERRUPTED_TEXT), resume, timeoutMs = 0L)
    }

    fun cancel(ctx: Context, id: Int) { runCatching { nm(ctx).cancel(id) } }

    /** Kanal `auto_start` (HIGH). [timeoutMs] 0: dokunulana ya da silinene dek kalır. */
    private fun autoStart(ctx: Context, id: Int, title: String, body: String, tap: PendingIntent, timeoutMs: Long = AUTO_START_TIMEOUT_MS) {
        runCatching {
            nm(ctx).createNotificationChannel(NotificationChannel(NotificationIds.CHANNEL_AUTO_START, text(ctx, CoreText.AUTO_START_CHANNEL), NotificationManager.IMPORTANCE_HIGH))
            val b = Notification.Builder(ctx, NotificationIds.CHANNEL_AUTO_START).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(title).setContentText(body).setContentIntent(tap).setAutoCancel(true)
            if (timeoutMs > 0) b.setTimeoutAfter(timeoutMs)
            nm(ctx).notify(id, b.build())
        }
    }

    private const val STOP_REQUEST = 11
    const val AUTO_START_TIMEOUT_MS = 15 * 60_000L
}
