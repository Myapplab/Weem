package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/**
 * RS `CrashGuardTest` (16 Oct, "Rawstats çalışmıyor") ekran okuma payı (CORE.md 6.1 → A3): the accessibility service's 180 ms
 * re-read, its K37 re-read, its event callback and its tick, the OCR callbacks and the notification listener record a failure
 * (`errors.jsonl`) and carry on; one exception there killed the whole process and Android unbound the service. WEEM: every guard
 * is `Store.guarded` (`VirtualMachineError` aside, every `Throwable`; CAPTURE.md 1.8) instead of `runCatching`/`catch (Exception)`.
 */
class CaptureCrashGuardTest {
    private fun capture(name: String) = RepoSource.text("capture/src/main/kotlin/com/kuryelog/app/$name")

    @Test fun everyScreenReadGoesThroughTheGuard() {
        val w = capture("WatchService.kt")
        // The declaration and the one guarded call; the event retry, the dialer retry, the K37 re-read and the capture all call readScreen().
        assertEquals(2, Regex("""\bsnapshot\(\)""").findAll(w).count())
        assertTrue(w.contains("private fun readScreen() { Store.guarded(this, \"screen\") { snapshot() } }"))
        assertEquals(2, Regex("""h\.postDelayed\(\{ if \(Store\.canRecordScreen\(this\)\) readScreen\(\) \}, CaptureTiming\.SECOND_READ_MS\)""").findAll(w).count())
        assertTrue(w.contains("if (Store.canRecordScreen(this)) readScreen() }, OfferCapture.REREAD_MS)"))
        assertTrue(w.contains("private val capture = Runnable { scheduled = false; readScreen() }"))
    }

    @Test fun accessibilityEventsAreGuarded() {
        assertTrue(capture("WatchService.kt").contains("if (e != null) Store.guarded(this, \"event\") { onEvent(e) }"))
    }

    @Test fun tickRecordsAFailureAndAlwaysRunsAgain() {
        val w = capture("WatchService.kt")
        val tick = RepoSource.body(w, "private val tick = object : Runnable", "private var eventScope")
        assertTrue(tick.contains("Store.guarded(this@WatchService, \"tick\") {"))
        assertTrue(tick.contains("} finally {"))
        assertTrue(tick.indexOf("h.postDelayed(this, if (shift) CaptureTiming.TICK_SHIFT_MS else CaptureTiming.TICK_IDLE_MS)") > tick.indexOf("} finally {"))
        // The order work (tick, badge, reminders) runs inside the same guard.
        assertTrue(tick.indexOf("CaptureState.host.onTick(") in tick.indexOf("Store.guarded(") until tick.indexOf("} finally {"))
    }

    @Test fun notificationListenerNeverThrows() {
        val n = capture("NotifService.kt")
        assertTrue(n.contains("if (sbn != null) Store.guarded(this, \"notification\") { posted(sbn) }"))
        assertTrue(n.contains("if (sbn != null) Store.guarded(this, \"notification\") { removed(sbn) }"))
        assertEquals(1, Regex("""override fun onNotificationPosted""").findAll(n).count())
    }

    @Test fun screenServiceStartAndOcrCallbacksAreGuarded() {
        val w = capture("WatchService.kt")
        assertTrue(w.contains("Store.guarded(this, \"screen_start\") { Store.ensureManualMode(this); CaptureState.host.onConnected(this) }"))
        assertTrue(w.contains("if (Store.guarded(this@WatchService, \"ocr\") { shot(result) } == null) ocrBusy = false"))
        assertTrue(w.contains("else if (Store.guarded(this@WatchService, \"ocr\") { recognize(r, image, hash, at, shift) } == null)"))
        assertTrue(w.contains(".addOnSuccessListener { resultText -> Store.guarded(this@WatchService, \"ocr\") {"))
        // No bare catch left that would let an Error through or swallow it unrecorded.
        assertFalse(w.contains("catch (e: Exception)"))
    }

    @Test fun ordersShiftStepsAreGuarded() {
        // RS `gpsOrderStepsAreGuarded`: OrderTracker.location/tick ran unguarded in the GPS callback. WEEM: every listener step is
        // guarded by `ShiftListeners` (source `gps_orders/<name>`), the orders flow is one listener.
        val ports = RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/Ports.kt")
        assertTrue(ports.contains("override fun onFix(fix: GpsFix, speedMps: Double?, nowMs: Long) = each(\"gps_orders\") { it.onFix(fix, speedMps, nowMs) }"))
        assertTrue(ports.contains("for ((name, h) in list) guarded(\"\$source/\$name\", sink()) { block(h) }"))
        val app = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/WeemApplication.kt")
        assertTrue(app.contains("ShiftHub.listeners.add(\"orders\", Orders.shiftHooks(this))"))
    }
}
