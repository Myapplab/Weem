package com.kuryelog.app.orders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.orders.CashTips
import com.kuryelog.logic.orders.TripRecord

/**
 * R8 (ORDERS.md 7.2; RS `CashTipPrompt.kt` @ 6388c04, satır satır). WEEM: numara [NotificationIds.cashTip] (10000 + …; Rawstats
 * 4000 + …), `OrderTracker` → [Orders.flow].
 *
 * K32: the "how much cash did you receive?" notification with an inline number reply (F4.6: message style, quick buttons). Dismissing it loses nothing: the
 * order card keeps the same input (Siparişler sekmesi, A3.8). Only the platform and the cash amount are shown, no customer data.
 */
object CashTipPrompt {
    private const val CHANNEL = "cash_tips"
    internal const val EXTRA_PKG = "pkg"
    internal const val EXTRA_CASH = "cash_amount"

    private fun id(tripId: String) = NotificationIds.cashTip(tripId)
    private const val OPEN_REQUEST = 13
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel(CHANNEL, Orders.tl(ctx, "Nakit bahşiş sorusu", "Nağd çaypulu sualı"), NotificationManager.IMPORTANCE_HIGH))
    }
    /** F4.7: a tap opens the small window ([TipAmountActivity]), not the app's home screen. */
    private fun window(ctx: Context, tripId: String, pkg: String, cash: Double?) = PendingIntent.getActivity(ctx, id(tripId),
        Intent(ctx, TipAmountActivity::class.java).putExtra(CashTips.EXTRA_TRIP, tripId).putExtra(EXTRA_PKG, pkg)
            .also { i -> cash?.let { i.putExtra(EXTRA_CASH, it) } }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun open(ctx: Context) = PendingIntent.getActivity(ctx, OPEN_REQUEST, ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent().setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE)

    fun ask(ctx: Context, trip: TripRecord) = ask(ctx, trip.id, trip.pkg, trip.cashAmount, retry = false)

    /**
     * F4.6 (Ekim planı madde 11, "WhatsApp gibi"): a message notification. "Yanıtla" takes the received amount inline
     * (RemoteInput; Android shows it on the heads-up), the quick buttons add 1/2/3/5 ₼ when the cash amount is known, a tap
     * opens the small window. [retry]: the previous reply was not a number; the question stays with a hint.
     */
    fun ask(ctx: Context, tripId: String, pkg: String, cash: Double?, retry: Boolean) {
        val reply = Intent(ctx, CashTipReceiver::class.java).putExtra(CashTips.EXTRA_TRIP, tripId).putExtra(EXTRA_PKG, pkg)
            .also { i -> cash?.let { i.putExtra(EXTRA_CASH, it) } }
        // RemoteInput needs a mutable PendingIntent; the intent is explicit (own receiver, not exported).
        val pi = PendingIntent.getBroadcast(ctx, id(tripId), reply, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val input = RemoteInput.Builder(CashTips.KEY_RECEIVED).setLabel(Orders.tl(ctx, "Alınan nakit (₼)", "Alınan nağd pul (₼)")).build()
        val action = Notification.Action.Builder(null, Orders.tl(ctx, "Yanıtla", "Cavab ver"), pi).addRemoteInput(input).build()
        val amount = cash?.let { Orders.f2(it) + "₼" }
        val text = (if (retry) Orders.tl(ctx, "Sayı okunamadı, örn. 25 veya 25.50. ", "Rəqəm oxunmadı, məs. 25 və ya 25.50. ") else "") +
            Orders.flow(ctx).platform(pkg) + " · " + (amount?.let { Orders.tl(ctx, "Nakit: $it; bahşiş = aldığın − $it", "Nağd: $it; çaypulu = aldığın − $it") }
                ?: Orders.tl(ctx, "nakit sipariş; tutar okunamadı, dokun ve yaz", "nağd sifariş; məbləğ oxunmadı, toxun və yaz"))
        val b = Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(Orders.tl(ctx, "Ne kadar nakit aldın?", "Nə qədər nağd pul aldın?")).setContentText(text)
            .setCategory(Notification.CATEGORY_MESSAGE).setContentIntent(window(ctx, tripId, pkg, cash))
            .addAction(action).setOnlyAlertOnce(retry)
        if (CashTips.quickAllowed(cash)) {
            val rv = RemoteViews(ctx.packageName, R.layout.tip_quick)
            rv.setTextViewText(R.id.tip_text, text)
            val buttons = listOf(R.id.tip_1, R.id.tip_2, R.id.tip_3, R.id.tip_5)
            CashTips.QUICK_TIPS.forEachIndexed { i, tip ->
                rv.setTextViewText(buttons[i], "+${tip.toInt()} ₼")
                val quick = Intent(ctx, CashTipReceiver::class.java).setAction("quick:$tripId:$i").putExtra(CashTips.EXTRA_TRIP, tripId)
                    .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_CASH, cash!!).putExtra(CashTips.EXTRA_TIP, tip)
                rv.setOnClickPendingIntent(buttons[i], PendingIntent.getBroadcast(ctx, id(tripId) * 8 + i + 1, quick,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            rv.setTextViewText(R.id.tip_other, Orders.tl(ctx, "Diğer", "Digər"))
            rv.setOnClickPendingIntent(R.id.tip_other, window(ctx, tripId, pkg, cash))
            b.setStyle(Notification.DecoratedCustomViewStyle()).setCustomBigContentView(rv)
        } else b.setStyle(Notification.BigTextStyle().bigText(text))
        nm(ctx).notify(id(tripId), b.build())
    }

    fun cancel(ctx: Context, tripId: String) = runCatching { nm(ctx).cancel(id(tripId)) }

    /** Replaces the question with what was saved (it times out by itself). */
    fun saved(ctx: Context, tripId: String, row: org.json.JSONObject) {
        val got = CashTips.num(row, "received_azn")?.let { Orders.f2(it) } ?: "—"
        val tip = CashTips.num(row, "tip_azn")
        val text = Orders.tl(ctx, "Alınan $got₼", "Alınan $got₼") + " · " + when {
            tip == null -> Orders.tl(ctx, "nakit tutarı okunamadı, bahşiş hesaplanmadı", "nağd məbləğ oxunmadı, çaypulu hesablanmadı")
            tip < 0 -> Orders.tl(ctx, "fark ${Orders.f2(tip)}₼ (eksi, kartta kontrol et)", "fərq ${Orders.f2(tip)}₼ (mənfi, kartda yoxla)")
            else -> Orders.tl(ctx, "bahşiş ${Orders.f2(tip)}₼", "çaypulu ${Orders.f2(tip)}₼")
        }
        nm(ctx).notify(id(tripId), Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(Orders.tl(ctx, "Nakit kaydedildi", "Nağd pul qeyd edildi")).setContentText(text)
            .setContentIntent(open(ctx)).setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(15_000L).build())
    }

    internal fun retry(ctx: Context, intent: Intent, tripId: String) =
        ask(ctx, tripId, intent.getStringExtra(EXTRA_PKG) ?: "", if (intent.hasExtra(EXTRA_CASH)) intent.getDoubleExtra(EXTRA_CASH, 0.0) else null, retry = true)
}

/** K32 inline reply of [CashTipPrompt]: a number appends an answer (OrderFlow.recordCashTip); anything else asks again. */
class CashTipReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val tripId = intent.getStringExtra(CashTips.EXTRA_TRIP) ?: return
        if (intent.hasExtra(CashTips.EXTRA_TIP)) {
            // F4.6: a quick button: received = cash + tip.
            val cash = if (intent.hasExtra(CashTipPrompt.EXTRA_CASH)) intent.getDoubleExtra(CashTipPrompt.EXTRA_CASH, 0.0) else null
            val received = CashTips.receivedFor(intent.getDoubleExtra(CashTips.EXTRA_TIP, 0.0), cash) ?: return
            val row = runCatching { Orders.flow(ctx).recordCashTip(tripId, received, CashTips.VIA_QUICK, tipSource = CashTips.SOURCE_QUICK) }.getOrNull()
            if (row != null) CashTipPrompt.saved(ctx, tripId, row)
            return
        }
        val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CashTips.KEY_RECEIVED)?.toString()
        val received = CashTips.parseInput(typed)
        val row = received?.let { runCatching { Orders.flow(ctx).recordCashTip(tripId, it, CashTips.VIA_NOTIFICATION) }.getOrNull() }
        if (row != null) CashTipPrompt.saved(ctx, tripId, row) else CashTipPrompt.retry(ctx, intent, tripId)
    }
}
