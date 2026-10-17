package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/**
 * E7 (Faz 4): with screen reading (accessibility) off the shift still starts, and a persistent notification and a red badge
 * say "Ekran okuma kapalı — kayıt yapılmıyor"; a tap opens the setting; both go when screen reading is on again; the same
 * when it goes off during the shift. Leaving home starts the shift without screen reading too (the home watcher also runs
 * from the notification listener).
 */
class ScreenReadingE7Test {
    private val pkg = "com.kuryelog.app"

    @Test fun onMeansSwitchedOnAndConnected() {
        val setting = "com.other.app/com.other.app.Svc:com.kuryelog.app/com.kuryelog.app.WatchService"
        assertTrue(ScreenReading.enabled(setting, pkg))
        assertFalse(ScreenReading.enabled("com.other.app/com.other.app.Svc", pkg))
        assertFalse(ScreenReading.enabled(null, pkg))
        assertFalse(ScreenReading.enabled("com.kuryelog.app.extra/x.Svc", pkg))
        assertTrue(ScreenReading.on(setting, pkg, connected = true))
        assertFalse(ScreenReading.on(setting, pkg, connected = false)) // switched on but not running (crashed): nothing recorded
    }

    @Test fun warningAfterGraceDuringAShiftOnly() {
        val g = ScreenReadingGuard()
        var t = 0L
        assertFalse(g.check(on = false, shift = true, now = t)); assertFalse(g.warning)        // shift just started, service binding
        t += 5_000; assertFalse(g.check(false, true, t)); assertFalse(g.warning)
        t += 5_000; assertTrue(g.check(false, true, t)); assertTrue(g.warning)                  // off for 10 s: warn
        t += 5_000; assertFalse(g.check(false, true, t)); assertTrue(g.warning)                 // no repeated change
        t += 5_000; assertTrue(g.check(true, true, t)); assertFalse(g.warning)                  // turned on: gone at once
        // Off again during the shift: the same warning after the grace.
        t += 5_000; g.check(false, true, t); assertFalse(g.warning)
        t += 10_000; g.check(false, true, t); assertTrue(g.warning)
        // Shift ended: no warning.
        t += 5_000; assertTrue(g.check(false, false, t)); assertFalse(g.warning)
    }

    @Test fun aShortGapIsNoWarning() {
        val g = ScreenReadingGuard()
        g.check(false, true, 0); g.check(true, true, 5_000); g.check(false, true, 8_000)
        assertFalse(g.check(false, true, 15_000)); assertFalse(g.warning)   // off since 8 s: only 7 s
        assertTrue(g.check(false, true, 18_000)); assertTrue(g.warning)
    }

    // ------------------------------------------------------------------ wiring (source checks; notification and badge are telefonda denenmedi)
    // WEEM (CAPTURE.md 5.2, 8): the warning listener lives in Android :capture (A3.7), the texts in the CaptureText table
    // (logic/capture), the card in :app. Red until A3.4 (table) and A3.7 (Android) are written.
    private fun androidCapture() = RepoSource.mainKotlin().filter { val p = it.invariantSeparatorsPath; "/capture/src/main/" in p && "/logic/" !in p }
        .joinToString("\n") { it.readText() }
    private fun appSources() = RepoSource.mainKotlin().filter { val p = it.invariantSeparatorsPath; "/app/src/main/" in p && "/logic/" !in p }
        .joinToString("\n") { it.readText() }
    private fun capture(name: String) = RepoSource.mainKotlin().single { it.name == name && "/capture/src/main/" in it.invariantSeparatorsPath && "/logic/" !in it.invariantSeparatorsPath }.readText()

    @Test fun shiftServiceWarnsAndTheHomeShowsIt() {
        val cap = androidCapture()
        assertTrue(cap.contains("ScreenReadingGuard("))
        val table = RepoSource.text("logic/capture/src/main/kotlin/com/kuryelog/logic/capture/CaptureText.kt")
        assertTrue(table.contains("Ekran okuma kapalı — kayıt yapılmıyor") && table.contains("Ekran oxuma bağlıdır — qeyd aparılmır"))
        assertTrue(cap.contains("Settings.ACTION_ACCESSIBILITY_SETTINGS"))
        assertTrue(cap.contains("setOngoing(true)"))
        assertTrue(cap.contains("TYPE_APPLICATION_OVERLAY"))       // the red badge (needs "display over other apps")
        // The manual start never asks for screen reading.
        assertFalse(appSources().contains("ScreenReading.on("))
        assertFalse(RepoSource.text("logic/core/src/main/kotlin/com/kuryelog/logic/core/ShiftMachine.kt").contains("ScreenReading"))
        // The shift card on the map tab shows the warning.
        assertTrue(RepoSource.text("app/src/main/kotlin/com/kuryelog/app/ShiftCard.kt").contains("screenReadingOffTitle"))
        // WatchService says whether it is connected; the notification listener hosts the home watcher too.
        assertTrue(capture("WatchService.kt").contains("running = true"))
        assertTrue(capture("NotifService.kt").contains("HomeWatch.refresh("))
        assertTrue(capture("WatchService.kt").contains("HomeWatch.refresh("))
    }
}
