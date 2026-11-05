package com.kuryelog.app.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.link.LinkText

/**
 * S44 (kullanıcı: "numara öteki telefonda direkt kopyalansın"): Receiver'da Provider'ın arama uygulamasında açılan numara panoya kopyalanır ve
 * üstten görünen bildirim (7407, kanal `dial_share`, 2 dk) çıkar; dokununca bu telefonun arama uygulaması numarayla açılır (arama kuryenin
 * dokunuşuyla başlar, WEEM kendiliğinden aramaz). Kilit ekranında numara gizli. Numara hiçbir yere yazılmaz.
 */
object DialNotice {
    private val main = Handler(Looper.getMainLooper())

    fun show(ctx: Context, number: String) {
        val a = ctx.applicationContext
        main.post { Store.guarded(a, "dial_share") { post(a, number) } }
    }

    private fun post(a: Context, number: String) {
        val lang = Store.lang(a)
        a.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(LinkText.DIAL_COPIED.get(lang), number))
        val nm = a.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(NotificationIds.CHANNEL_DIAL_SHARE, LinkText.DIAL_CHANNEL.get(lang), NotificationManager.IMPORTANCE_HIGH)
            .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(a, NotificationIds.DIAL_SHARE, dial, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = Notification.Builder(a, NotificationIds.CHANNEL_DIAL_SHARE).setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(LinkText.DIAL_COPIED.get(lang)).build()
        val n = Notification.Builder(a, NotificationIds.CHANNEL_DIAL_SHARE)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(LinkText.DIAL_COPIED.get(lang))
            .setContentText(LinkText.DIAL_TAP.get(lang).format(number))
            .setContentIntent(tap)
            .addAction(Notification.Action.Builder(null, LinkText.DIAL_CALL.get(lang), tap).build())
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        nm.notify(NotificationIds.DIAL_SHARE, n)
    }

    private const val TIMEOUT_MS = 2 * 60_000L
}
