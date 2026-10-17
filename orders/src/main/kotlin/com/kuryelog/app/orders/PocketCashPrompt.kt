package com.kuryelog.app.orders

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.orders.PocketCash

/**
 * R9/M15 (ORDERS.md 8.1; RS `PocketCashPrompt.kt` @ 6388c04, satır satır). WEEM: numara [NotificationIds.POCKET_CASH], `OrderTracker` →
 * [Orders.flow].
 *
 * Ekim planı madde 15: when a shift starts, "Şu an cebinde ne kadar nakit var?" as a message notification like the cash tip
 * question (F4.6): "Yanıtla" takes the amount inline, a tap opens [PocketCashActivity]. The answer goes to [PocketCash.FILE].
 */
object PocketCashPrompt {
    private const val CHANNEL = "pocket_cash"
    private const val ID = NotificationIds.POCKET_CASH
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel(CHANNEL, Orders.tl(ctx, "Vardiya başı nakit sorusu", "Növbə əvvəli nağd sualı"), NotificationManager.IMPORTANCE_HIGH))
    }
    private fun window(ctx: Context, shift: Long) = PendingIntent.getActivity(ctx, ID,
        Intent(ctx, PocketCashActivity::class.java).putExtra(PocketCash.EXTRA_SHIFT, shift).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun ask(ctx: Context, shift: Long, retry: Boolean = false) = runCatching {
        val reply = Intent(ctx, PocketCashReceiver::class.java).putExtra(PocketCash.EXTRA_SHIFT, shift)
        // RemoteInput needs a mutable PendingIntent; the intent is explicit (own receiver, not exported).
        val pi = PendingIntent.getBroadcast(ctx, ID, reply, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val input = RemoteInput.Builder(PocketCash.KEY_AMOUNT).setLabel(Orders.tl(ctx, "Cepteki nakit (₼)", "Cibdəki nağd (₼)")).build()
        val action = Notification.Action.Builder(null, Orders.tl(ctx, "Yanıtla", "Cavab ver"), pi).addRemoteInput(input).build()
        val text = (if (retry) Orders.tl(ctx, "Sayı okunamadı, örn. 30 veya 12.50. ", "Rəqəm oxunmadı, məs. 30 və ya 12.50. ") else "") +
            Orders.tl(ctx, "Vardiya başladı. Yaz ya da dokun.", "Növbə başladı. Yaz və ya toxun.")
        nm(ctx).notify(ID, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(Orders.tl(ctx, "Şu an cebinde ne kadar nakit var?", "İndi cibində nə qədər nağd pul var?")).setContentText(text)
            .setCategory(Notification.CATEGORY_MESSAGE).setContentIntent(window(ctx, shift)).addAction(action).setOnlyAlertOnce(retry)
            .setAutoCancel(true).build())
    }

    fun saved(ctx: Context, amount: Double) = runCatching {
        nm(ctx).notify(ID, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(Orders.tl(ctx, "Kaydedildi", "Qeyd edildi")).setContentText(PocketCash.text(amount) { tr, az -> Orders.tl(ctx, tr, az) })
            .setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(10_000L).build())
    }
}

/** M15 inline reply of [PocketCashPrompt]: a number is saved; anything else asks again. */
class PocketCashReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val shift = intent.getLongExtra(PocketCash.EXTRA_SHIFT, 0L).takeIf { it > 0 } ?: return
        val amount = PocketCash.parse(RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PocketCash.KEY_AMOUNT)?.toString())
        val row = amount?.let { runCatching { Orders.flow(ctx).recordPocketCash(shift, it, PocketCash.VIA_REPLY) }.getOrNull() }
        if (row != null && amount != null) PocketCashPrompt.saved(ctx, amount) else PocketCashPrompt.ask(ctx, shift, retry = true)
    }
}

/** M15: the small window a tap on the question opens (dialog theme, not exported). */
class PocketCashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        val shift = intent.getLongExtra(PocketCash.EXTRA_SHIFT, 0L).takeIf { it > 0 } ?: run { finish(); return }
        title = Orders.tl(this, "Şu an cebinde ne kadar nakit var?", "İndi cibində nə qədər nağd pul var?")
        val px = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), px(12)) }
        val input = EditText(this).apply {
            hint = Orders.tl(this@PocketCashActivity, "Cepteki nakit (₼)", "Cibdəki nağd (₼)")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val save = Button(this).apply { text = Orders.tl(this@PocketCashActivity, "Kaydet", "Yadda saxla") }
        val cancel = Button(this).apply { text = Orders.tl(this@PocketCashActivity, "Vazgeç", "İmtina") }
        save.setOnClickListener {
            val amount = PocketCash.parse(input.text?.toString()) ?: return@setOnClickListener
            if (runCatching { Orders.flow(this).recordPocketCash(shift, amount, PocketCash.VIA_WINDOW) }.getOrNull() != null) PocketCashPrompt.saved(this, amount)
            finish()
        }
        cancel.setOnClickListener { finish() }
        box.addView(TextView(this).apply { textSize = 14f; text = Orders.tl(this@PocketCashActivity, "Vardiya başı; Wolt'a yatırılacak nakitle karıştırma.", "Növbənin əvvəli; Wolt-a veriləcək nağdla qarışdırma.") })
        box.addView(input)
        box.addView(LinearLayout(this).apply { gravity = Gravity.END; addView(cancel); addView(save) })
        setContentView(box)
        input.requestFocus()
    }
}
