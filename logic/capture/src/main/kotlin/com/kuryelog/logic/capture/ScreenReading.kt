package com.kuryelog.logic.capture

/**
 * E7 (Faz 4): a shift starts and runs without screen reading (the accessibility service, WatchService) too, but then nothing
 * of Wolt or Bolt is recorded, so the courier is told at once: a persistent notification and a red badge "Ekran okuma
 * kapalı — kayıt yapılmıyor", whose tap opens the accessibility settings; both go away when screen reading is back, and come
 * again if it goes off during the shift. Pure.
 */
object ScreenReading {
    /** The accessibility service is switched on in Android's settings ([setting] = `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`). */
    fun enabled(setting: String?, pkg: String): Boolean = setting.orEmpty().split(':').any { it.startsWith("$pkg/") }
    /** Screen reading works: switched on and the service connected (an enabled service that crashed records nothing either). */
    fun on(setting: String?, pkg: String, connected: Boolean): Boolean = connected && enabled(setting, pkg)
}

/**
 * When to show the warning: only during a shift, after screen reading stayed off for [graceMs] (the service binds a moment
 * after the process starts or the switch is turned on), and gone at once when it works again or the shift ends.
 */
class ScreenReadingGuard(private val graceMs: Long = GRACE_MS) {
    private var offSince: Long? = null
    var warning = false
        private set

    /** One check ([CHECK_MS] apart); true when [warning] changed. */
    fun check(on: Boolean, shift: Boolean, now: Long): Boolean {
        val since = if (on || !shift) null else offSince ?: now
        offSince = since
        val want = since != null && now - since >= graceMs
        if (want == warning) return false
        warning = want
        return true
    }

    companion object {
        const val GRACE_MS = 10_000L
        const val CHECK_MS = 5_000L
        /** WEEM: `NotificationIds.SCREEN_READING_OFF` (5104), Rawstats'ta 5103 (CAPTURE.md 5.1). */
        const val NOTIFICATION_ID = 5104
    }
}
