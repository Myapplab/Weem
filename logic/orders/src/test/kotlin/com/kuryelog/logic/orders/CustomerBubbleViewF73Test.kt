package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.RepoSource
import org.junit.Assert.*
import org.junit.Test

/**
 * F7.3/F7.4 (RS `CustomerBubbleViewF73Test` @ 6388c04): the customer reminder is the thin bubble ([CustomerBubble]) with the
 * deadline and the order count; swipes fold the notes or remove it. Android window: checked on the source (telefonda denenmedi).
 * WEEM: `OrderTracker.kt` → `logic/orders` `OrderFlow.kt`, `DeliveryReminder.kt` → Android `:orders`, `OrderTracker.dismissReminder()`
 * → `Orders.flow(service).dismissReminder()` (ORDERS.md 16.3).
 */
class CustomerBubbleViewF73Test {
    private fun flow() = RepoSource.text("logic/orders/src/main/kotlin/com/kuryelog/logic/orders/OrderFlow.kt")
    private fun reminder() = RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/DeliveryReminder.kt")

    @Test fun reminderCarriesDeadlineAndOrderCount() {
        val t = flow()
        assertTrue(t.contains("val deadlineAt: Long? = null, val orders: Int = 1"))
        assertTrue(t.contains("deadlineAt = c.deadlineAt, orders = 1 + t.sameDropoff.count { core.trips[it]?.active == true }"))
    }

    @Test fun customerIsTheBubble() {
        val r = reminder()
        assertTrue(r.contains("if (r.kind == \"customer\") { closeCard(); closeBubble(); showCustomer(r); return }"))
        assertTrue(r.contains("CustomerBubble.line(d, d.name, tl)"))
        assertTrue(r.contains("CustomerBubble.minutes(r.deadlineAt, System.currentTimeMillis(), tl)"))
        assertTrue(r.contains("CustomerBubble.orders(r.orders, tl)"))
        assertTrue(r.contains("CustomerBubble.notes(d, tl)"))
        assertTrue(r.contains("CustomerBubble.NOTES_MAX_SHARE"))
    }

    @Test fun swipes() {
        val r = reminder()
        assertTrue(r.contains("CustomerBubble.swipe("))
        assertTrue(r.contains("CustomerBubble.Swipe.DISMISS -> { Orders.flow(service).dismissReminder(); close() }"))
        assertTrue(r.contains("CustomerBubble.Swipe.TOGGLE -> { notesOpen = !notesOpen"))
    }
}
