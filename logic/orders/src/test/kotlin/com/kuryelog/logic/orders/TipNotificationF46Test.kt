package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.RepoSource
import org.junit.Assert.*
import org.junit.Test

/**
 * F4.6/F4.7 (RS `TipNotificationF46Test` @ 6388c04; Ekim planı madde 11, "WhatsApp gibi"): the cash tip question is a message-like
 * notification. "Yanıtla" takes the received amount inline (RemoteInput), quick buttons add 1/2/3/5 ₼ when the cash amount is
 * known, and a tap opens a small window ([CashTips], `TipAmountActivity`) instead of the home screen. Android classes: checked
 * on the source, compiled by CI (telefonda denenmedi). WEEM: Android `:orders` (`com.kuryelog.app.orders`), `I18n.tl` →
 * `Orders.tl`, `OrderTracker` → `Orders.flow(…)` (ORDERS.md 16.3).
 */
class TipNotificationF46Test {
    private val dir = "orders/src/main/"
    private fun src(name: String) = RepoSource.text(dir + "kotlin/com/kuryelog/app/orders/$name")

    @Test fun messageStyleWithReplyQuickButtonsAndWindow() {
        val p = src("CashTipPrompt.kt")
        assertTrue(p.contains("setCategory(Notification.CATEGORY_MESSAGE)"))
        assertTrue(p.contains("Orders.tl(ctx, \"Yanıtla\", \"Cavab ver\")"))
        assertTrue(p.contains("addRemoteInput(input)"))
        assertTrue(p.contains("Notification.DecoratedCustomViewStyle()"))
        assertTrue(p.contains("RemoteViews(ctx.packageName, R.layout.tip_quick)"))
        assertTrue(p.contains("CashTips.QUICK_TIPS"))
        assertTrue(p.contains("if (CashTips.quickAllowed(cash))"))
        // A tap opens the small window, never the home screen.
        assertTrue(p.contains("Intent(ctx, TipAmountActivity::class.java)"))
        assertFalse(p.contains("Intent(ctx, MainActivity::class.java)"))
        // WEEM: the number comes from the one table (NotificationIds.cashTip = 10000 + …).
        assertTrue(p.contains("private fun id(tripId: String) = NotificationIds.cashTip(tripId)"))
    }

    @Test fun quickButtonGoesToTheReceiver() {
        val p = src("CashTipPrompt.kt")
        assertTrue(p.contains("putExtra(CashTips.EXTRA_TIP, tip)"))
        val r = p.substring(p.indexOf("class CashTipReceiver"))
        assertTrue(r.contains("CashTips.receivedFor("))
        assertTrue(r.contains("CashTips.VIA_QUICK"))
        assertTrue(r.contains("tipSource = CashTips.SOURCE_QUICK"))
    }

    @Test fun layoutHasFourQuickButtonsAndOther() {
        val xml = RepoSource.text(dir + "res/layout/tip_quick.xml")
        for (id in listOf("tip_text", "tip_1", "tip_2", "tip_3", "tip_5", "tip_other")) assertTrue(id, xml.contains("@+id/$id"))
    }

    @Test fun windowIsASmallDialogNotTheHomeScreen() {
        val m = RepoSource.text(dir + "AndroidManifest.xml")
        val i = m.indexOf("android:name=\"com.kuryelog.app.orders.TipAmountActivity\"")
        assertTrue(i > 0)
        val decl = m.substring(m.lastIndexOf("<activity", i), m.indexOf(">", i))
        assertTrue(decl.contains("android:exported=\"false\""))
        assertTrue(decl.contains("@android:style/Theme.DeviceDefault.Dialog"))
        assertTrue(decl.contains("android:excludeFromRecents=\"true\""))
        assertTrue(decl.contains("android:taskAffinity=\"\""))
        val a = src("TipAmountActivity.kt")
        assertTrue(a.contains("setFinishOnTouchOutside(false)"))
        assertTrue(a.contains("CashTips.preview("))
        assertTrue(a.contains("Orders.flow(this).recordCashTip(tripId, received, CashTips.VIA_WINDOW, cashOverride = typedCash)"))
    }
}
