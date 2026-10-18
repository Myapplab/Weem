package com.kuryelog.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kuryelog.app.core.Store
import com.kuryelog.app.economics.Economics
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.ShiftMachine
import com.kuryelog.logic.economics.DayReportCard
import com.kuryelog.logic.orders.OrdersScreen
import org.json.JSONObject
import com.kuryelog.logic.app.Text as Str

/**
 * Vardiya sonu (A4.2, N4/N8): günün raporu bildirim olarak çıkar (net, brüt, bahşiş, komisyon, enerji, hedefe kalan); aynı kart Siparişler
 * sekmesinde ("Günün özeti" / gün ayrıntısında "Gün sonu raporu"). Siparişler ve scooter kancalarından sonra çalışır (kapanan sipariş ve
 * son enerji adımı hesapta); kendi iş parçacığında, hata vardiyaya ulaşmaz (`day_report_failed` olayı).
 */
class DayReportHook(ctx: Context) : ShiftMachine.Hooks {
    private val app = ctx.applicationContext

    override fun onEnd(startMs: Long, nowMs: Long, reason: String) {
        Thread({
            runCatching { post(app) }.onFailure {
                Store.event(app, EVENT_FAILED, JSONObject().put("error", it.javaClass.simpleName))
            }
        }, "weem-day-report").start()
    }

    companion object {
        const val EVENT_FAILED = "day_report_failed"
        private const val CHANNEL = "day_report"

        /** Bugünün raporu bildirimi; bugün sipariş de enerji de yoksa (boş vardiya) bildirim çıkmaz. */
        fun post(ctx: Context) {
            val lang = Store.lang(ctx)
            val src = OrdersAccess.source(ctx)
            val day = OrdersScreen(src, lang).days().firstOrNull { it.date == src.today() } ?: return
            if (day.delivered.isEmpty()) return
            val (title, body) = DayReportCard.notification(Economics.daySummaries(ctx).report(day)) { tr, az -> if (lang == Lang.AZ) az else tr }
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, Str.OSET_REPORT_CHANNEL.get(lang), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(ctx, NotificationIds.DAY_REPORT, ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent().setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE)
            nm.notify(NotificationIds.DAY_REPORT, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title)
                .setContentText(body.lineSequence().first()).setStyle(Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
