package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.RepoSource
import org.junit.Assert.*
import org.junit.Test

/**
 * A3.7 bağlantısı (ORDERS.md 4.2, 4.14; CAPTURE.md 7.1): `:app` açılışta ekran olaylarını [OrderFlow]'a, servis tikini `OrdersHost`'a
 * bağlar ve vardiya dinleyicilerini Rawstats `GpsService` sırasıyla kaydeder. Android: kaynakta denetlenir (telefonda denenmedi).
 */
class OrdersWiringTest {
    private val app = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/WeemApplication.kt")
    private val orders = RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/Orders.kt")

    @Test fun captureAndServiceTickGoToTheOrders() {
        assertTrue(app.contains("Orders.bind(this)"))
        assertTrue(orders.contains("Capture.sink = flow(ctx)"))
        assertTrue(orders.contains("CaptureState.host = OrdersHost"))
        // ZIP içe aktarmadan sonra istatistik yeniden kurulur (RS `reloadImportedStats`).
        assertTrue(app.contains("LogImport.afterImport = Orders::reloadImportedStats"))
    }

    @Test fun shiftListenersKeepRawstatsOrder() {
        val cash = app.indexOf("ShiftHub.listeners.add(\"cash_pending\", Orders.cashPendingHooks(this))")
        val flow = app.indexOf("ShiftHub.listeners.add(\"orders\", Orders.shiftHooks(this))")
        val screen = app.indexOf("ShiftHub.listeners.add(\"screen_reading\", ScreenReadingWarning(this))")
        assertTrue(cash > 0 && flow > cash && screen > flow)
        assertTrue(orders.contains("fun cashPendingHooks(ctx: Context): ShiftMachine.Hooks = flow(ctx).cashPendingHooks"))
    }

    @Test fun envAsksThroughTheAndroidPrompts() {
        assertTrue(orders.contains("override fun askCashTip(trip: TripRecord) { CashTipPrompt.ask(ctx, trip) }"))
        assertTrue(orders.contains("override fun askPocketCash(shiftStartMs: Long) { PocketCashPrompt.ask(ctx, shiftStartMs) }"))
        // Geocoder off the main thread, the answer back on it (RS `OrderTracker.resolve`).
        assertTrue(orders.contains("worker.execute {") && orders.contains("main.post {"))
        val pocket = RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/PocketCashPrompt.kt")
        assertTrue(pocket.contains("private const val ID = NotificationIds.POCKET_CASH"))
    }
}
