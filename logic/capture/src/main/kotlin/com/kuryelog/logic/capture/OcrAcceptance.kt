package com.kuryelog.logic.capture

/**
 * Whether an OCR frame is recorded (CAPTURE.md 3.4; RS `WatchService.kt:260-273`, `ocrLines`). Pure, so the rules are tested
 * on the JVM; Android `:capture` passes what it knows when the recognizer answers.
 */
object OcrAcceptance {
    private val ORDER = setOf(ScreenStage.PICKUP, ScreenStage.DROPOFF)
    private val QUIET = setOf(ScreenStage.OFFER, ScreenStage.IDLE, ScreenStage.OFFLINE)

    /** The frame is still current: service connected, recording allowed, same shift, window (same id and root package) still
     *  on screen, screenshot younger than [CaptureTiming.OCR_STALE_MS], and some lines read. */
    fun fresh(connected: Boolean, canRecord: Boolean, sameShift: Boolean, windowShown: Boolean, shotAt: Long, now: Long, lines: List<String>): Boolean =
        connected && canRecord && sameShift && windowShown && now - shotAt < CaptureTiming.OCR_STALE_MS && lines.isNotEmpty()

    /** OCR sees no buttons: the actions are the lines that read as accept, decline, collect or deliver, and "arrived" only
     *  next to a "Slide to confirm" slider. */
    fun actions(lines: List<String>): List<String> {
        val hasSlider = lines.any { CourierParser.norm(it).contains("slide to confirm") }
        return lines.filter { CourierParser.isAccept(it) || CourierParser.isDecline(it) || CourierParser.isCollect(it) || CourierParser.isDeliver(it) ||
            (hasSlider && CourierParser.isArrive(it)) }
    }

    /** The actions to record the OCR [lines] of window [pkg] with, or null when the frame is dropped: the same window's
     *  accessibility read ([nativeTexts], [nativeActions]) shows a pickup or drop-off while OCR reads an offer, waiting or
     *  offline screen (a frame taken during a transition must not overwrite a populated order). */
    fun accept(pkg: String, nativeTexts: List<String>, nativeActions: List<String>, lines: List<String>, at: Long): List<String>? {
        val actions = actions(lines)
        val native = CourierParser.parse(pkg, nativeTexts, nativeActions, at)
        val ocr = CourierParser.parse(pkg, lines, actions, at, "ocr")
        return if (native.stage in ORDER && ocr.stage in QUIET) null else actions
    }
}
