package com.kuryelog.logic.capture

// İskelet (A3.2): imzalar Rawstats'tan (ScreenReading.kt @ 6388c04); R19/E7. Gövdeler A3.4'te.
object ScreenReading {
    /** The accessibility service is switched on in Android's settings ([setting] = `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`). */
    fun enabled(setting: String?, pkg: String): Boolean = TODO("A3.4")
    /** Screen reading works: switched on and the service connected (an enabled service that crashed records nothing either). */
    fun on(setting: String?, pkg: String, connected: Boolean): Boolean = TODO("A3.4")
}

class ScreenReadingGuard(private val graceMs: Long = GRACE_MS) {
    var warning: Boolean = false
        private set
    /** One check ([CHECK_MS] apart); true when [warning] changed. */
    fun check(on: Boolean, shift: Boolean, now: Long): Boolean = TODO("A3.4")

    companion object {
        const val GRACE_MS = 10_000L
        const val CHECK_MS = 5_000L
        /** WEEM: `NotificationIds.SCREEN_READING_OFF` (5104), Rawstats'ta 5103 (CAPTURE.md 5.1). */
        const val NOTIFICATION_ID = 5104
    }
}
