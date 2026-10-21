package com.kuryelog.app.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.remote.RemoteText

/** Uzaktan kullanım bildirimleri (REMOTE.md 1.2): kanal `remote`; izin isteği 7405 (60 sn, dokununca [ConsentActivity]). */
object RemoteNotices {
    const val ASK_TIMEOUT_MS = 60_000L

    fun channel(ctx: Context, lang: Lang) {
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(NotificationIds.CHANNEL_REMOTE, RemoteText.CHANNEL.get(lang), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun showAsk(ctx: Context) {
        val lang = Store.lang(ctx)
        channel(ctx, lang)
        val open = PendingIntent.getActivity(
            ctx, NotificationIds.REMOTE_ASK,
            Intent(ctx, ConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, NotificationIds.CHANNEL_REMOTE)
            .setContentTitle(RemoteText.ASK_TITLE.get(lang))
            .setContentText(RemoteText.ASK_TEXT.get(lang))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(ASK_TIMEOUT_MS)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(NotificationIds.REMOTE_ASK, n) }
    }

    fun cancelAsk(ctx: Context) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(NotificationIds.REMOTE_ASK) }
    }
}
