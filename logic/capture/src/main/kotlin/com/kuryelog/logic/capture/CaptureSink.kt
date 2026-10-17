package com.kuryelog.logic.capture

/**
 * What `:capture` hands to the order side (CAPTURE.md 7.1): `:orders` implements it, `:app` binds it at start-up
 * ([Capture.sink]). Calls come in today's order on the main thread; `:capture` checked recording permission already.
 */
interface CaptureSink {
    /** `OrderTracker.onScreen`: every parsed frame, written to `screens.jsonl` or not. */
    fun onScreen(pkg: String, texts: List<String>, actions: List<String>, at: Long, source: String,
                 spinner: Boolean, checked: Boolean?, parsed: CourierObservation)
    /** `OrderTracker.onClick`: one distinct label of a tapped view. */
    fun onClick(pkg: String, label: String, at: Long)
    /** `OrderTracker.notification`: a courier app notification (accessibility event or listener). */
    fun onNotification(pkg: String, text: String, at: Long)
    /** `OrderTracker.navigation`: a navigation app's notification lines (R16); memory only. */
    fun onNavigation(lines: List<String>, at: Long)
    /** `OrderTracker.awaitingPhone`: the dialer is read only while this is true. */
    fun awaitingPhone(now: Long): Boolean
    /** `OrderTracker.dialerPhone`: the dialer's texts; nothing of them is written. */
    fun onDialer(texts: List<String>, at: Long)

    /** Not bound yet: screen reading records its rows and does no order work. */
    object NONE : CaptureSink {
        override fun onScreen(pkg: String, texts: List<String>, actions: List<String>, at: Long, source: String,
                              spinner: Boolean, checked: Boolean?, parsed: CourierObservation) = Unit
        override fun onClick(pkg: String, label: String, at: Long) = Unit
        override fun onNotification(pkg: String, text: String, at: Long) = Unit
        override fun onNavigation(lines: List<String>, at: Long) = Unit
        override fun awaitingPhone(now: Long) = false
        override fun onDialer(texts: List<String>, at: Long) = Unit
    }
}

/** The binding `:app` writes once at start-up (`WeemApplication`). */
object Capture {
    @Volatile var sink: CaptureSink = CaptureSink.NONE
}
