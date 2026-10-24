package com.kuryelog.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kuryelog.app.capture.CaptureState
import com.kuryelog.app.core.HomeWatch
import com.kuryelog.app.core.OwnOverlays
import com.kuryelog.app.core.Store
import com.kuryelog.logic.capture.Capture
import com.kuryelog.logic.capture.CaptureRows
import com.kuryelog.logic.capture.CaptureText
import com.kuryelog.logic.capture.CaptureTiming
import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.capture.CourierParser
import com.kuryelog.logic.capture.Dialer
import com.kuryelog.logic.capture.OcrAcceptance
import com.kuryelog.logic.capture.OcrPace
import com.kuryelog.logic.capture.OfferCapture
import com.kuryelog.logic.capture.ScreenDeduper
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.capture.SliderSpinner
import com.kuryelog.logic.core.KuryelogPrefs

/**
 * Wolt/Bolt ekran okuma (R1, CAPTURE.md 1, 3; RS `WatchService.kt` @ 6388c04, satır satır). Ad değişmez: erişilebilirlik izni
 * `com.kuryelog.app.WatchService` adına bağlı (plan Bölüm 2). Yalnız okur, hiçbir şeye basmaz, ağ kullanmaz (CAPTURE.md 7.3–7.4).
 * Her pencerenin kendi sınırı, ayrıştırması ve sipariş durumu vardır; sipariş işleri `Capture.sink` (`:orders`) ve
 * [CaptureState.host] (rozet, balonlar) üstünden.
 *
 * WEEM farkları (CAPTURE.md 1.2, 1.6, 1.8, 6.3, 9): `ScooterLink.publish` yok; rozet ve hatırlatıcılar `CaptureHost`'ta; koruma
 * `Store.guarded` (`VirtualMachineError` dışındaki her `Throwable`); `click.text` `redact`'li ([CaptureRows.click]); maskeleme
 * [OwnOverlays] kaydından.
 */
class WatchService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var scheduled = false
    private var recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val deduper = ScreenDeduper()
    /** D6: which window OCR reads next and how soon (hot after a notification, a new window state or a card without its fee). */
    private val pace = OcrPace()
    private var ocrBusy = false
    /** K37: extra quick reads done for the offer card on screen without a fee; last window-screenshot invalid-window failure. */
    private var offerRereads = 0
    private var rereadScheduled = false
    private var invalidWindowAt: Long? = null
    private var connected = false
    private data class WindowRead(val id: Int, val pkg: String, val bounds: Rect, val texts: List<String>, val actions: List<String>, val nodes: List<CaptureRows.Node>,
        /** K33: a spinner on Wolt's delivery slider in this accessibility read (see [SliderSpinner]). */
        val spinner: Boolean = false,
        /** M16: a checkable node's tick in this accessibility read (Wolt's cash checkbox): true if any is ticked, false if some are
         *  and none is ticked, null without checkable nodes. */
        val checked: Boolean? = null)

    private fun now() = System.currentTimeMillis()
    private fun sink() = Capture.sink

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 16 Oct: a failing start is recorded, never thrown (it would close the app and Android would leave the service off).
        Store.guarded(this, "screen_start") { Store.ensureManualMode(this); CaptureState.host.onConnected(this) }
        connected = true; CaptureState.running = true
        h.post(tick)
    }
    /** A failure while handling an event is recorded ([Store.failure]), never thrown: an exception here kills the whole process
     *  (shift, GPS and screen reading with it) and Android unbinds the accessibility service. */
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e != null) Store.guarded(this, "event") { onEvent(e) }
    }
    private fun onEvent(e: AccessibilityEvent) {
        if (!Store.canRecordScreen(this) || CaptureState.receiverRole()) return
        val pkg = e.packageName?.toString() ?: ""
        // The dialer is read only right after a customer card, for the number the courier is calling.
        if (Dialer.isDialer(pkg) && sink().awaitingPhone(now())) {
            if (!scheduled) { scheduled = true; h.post(capture); h.postDelayed({ if (Store.canRecordScreen(this)) readScreen() }, CaptureTiming.SECOND_READ_MS) }
            return
        }
        if (pkg !in Store.targets(this)) return
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val n = e.source
            val labels = ArrayList<String>()
            fun readLabels(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || depth > CaptureTiming.CLICK_DEPTH || labels.size > CaptureTiming.CLICK_LABELS) return
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let(labels::add)
                node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(labels::add)
                for (i in 0 until node.childCount) readLabels(node.getChild(i), depth + 1)
            }
            readLabels(n, 0); labels.addAll(e.text.map { it.toString() }); e.contentDescription?.toString()?.let(labels::add)
            val click = CaptureRows.click(pkg, labels)
            if (click != null && pkg in Store.targets(this)) {
                Store.event(this, "click", click)
                labels.distinct().forEach { sink().onClick(pkg, it, now()) }
            }
        }
        if (e.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val at = now()
            if (pkg in Store.targets(this)) sink().onNotification(pkg, e.text.joinToString("\n"), at)
            // D6: the courier app announced something (a new offer): its window is read now and again right after.
            pace.trigger(pkg, at)
            if (!scheduled) { scheduled = true; h.post(capture) }
            return
        }
        // D6: a new window state (a dialog, another page) is read at once too.
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) pace.trigger(pkg, now())
        // S39 (battery, main thread): a storm of content/window-list changes (timers, scrolling lists) read the tree twice per event (now and
        // 180 ms later). Now at most once per CONTENT_READ_MS, trailing, so the last state is still read; window state, notification and click
        // events keep the immediate read and the retry below.
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || e.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (!contentQueued) {
                contentQueued = true
                h.postDelayed(contentRead, maxOf(0L, lastReadAt + CaptureTiming.CONTENT_READ_MS - SystemClock.uptimeMillis()))
            }
            return
        }
        // First read is queued immediately; a short retry captures content populated after the window event.
        if (!scheduled) { scheduled = true; h.post(capture); h.postDelayed({ if (Store.canRecordScreen(this)) readScreen() }, CaptureTiming.SECOND_READ_MS) }
    }
    private val capture = Runnable { scheduled = false; readScreen() }
    /** S39: uptime of the last tree read and the pending trailing read for content changes. */
    private var lastReadAt = 0L
    private var contentQueued = false
    private val contentRead = Runnable { contentQueued = false; if (Store.canRecordScreen(this)) readScreen() }
    /** Every screen read goes through here: the 180 ms retry and the K37 re-read used to call [snapshot] unguarded, so one
     *  failing frame crashed the app on the main thread. */
    private fun readScreen() { Store.guarded(this, "screen") { snapshot() } }
    private fun snapshot() {
        lastReadAt = SystemClock.uptimeMillis()
        if (!connected || !Store.canRecordScreen(this) || CaptureState.receiverRole()) return
        val allowed = Store.targets(this)
        val reads = ArrayList<WindowRead>()
        val wins = windows.orEmpty()
        for (w in wins) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = w.root ?: continue
            val pkg = root.packageName?.toString() ?: continue
            if (Dialer.isDialer(pkg)) {
                // Never logged: only a phone number is taken, and kept in memory for the card.
                if (sink().awaitingPhone(now())) {
                    val texts = ArrayList<String>(); walk(root, texts, ArrayList(), ArrayList(), 0, false, Rect().also { w.getBoundsInScreen(it) })
                    sink().onDialer(texts, now())
                }
                continue
            }
            if (pkg !in allowed) continue
            val bounds = Rect(); w.getBoundsInScreen(bounds)
            val texts = ArrayList<String>(); val actions = ArrayList<String>(); val nodes = ArrayList<CaptureRows.Node>()
            val spin = if (pkg in CourierParser.WOLT) ArrayList<SliderSpinner.Box>() else null
            val labels = ArrayList<SliderSpinner.Box>()
            val ticks = if (pkg in CourierParser.WOLT) ArrayList<Boolean>() else null
            walk(root, texts, actions, nodes, 0, false, bounds, spin, labels, ticks)
            reads.add(WindowRead(w.id, pkg, bounds, texts.distinct(), actions.distinct(), nodes, spin != null && SliderSpinner.onSlider(spin, labels),
                ticks?.takeIf { it.isNotEmpty() }?.any { it }))
        }
        val now = now()
        var feeMissing = false; var offerShown = false
        for (r in reads) {
            if (r.pkg !in allowed || r.texts.isEmpty()) continue
            val o = accept(r, r.texts, r.actions, now, "accessibility") ?: continue
            if (o.stage == ScreenStage.OFFER) offerShown = true
            if (OfferCapture.reread(o, offerRereads)) feeMissing = true
        }
        // K37: an offer card without its fee is read again shortly, not only at the next event or tick.
        if (!offerShown) offerRereads = 0
        if (feeMissing && !rereadScheduled) {
            offerRereads++; rereadScheduled = true
            h.postDelayed({ rereadScheduled = false; if (Store.canRecordScreen(this)) readScreen() }, OfferCapture.REREAD_MS)
        }
        if (Build.VERSION.SDK_INT >= 30 && !ocrBusy && Store.prefs(this).getBoolean(OCR_PREF, true)) {
            pace.retain(reads.map { it.id }.toSet())
            pace.next(reads, { it.id }, { it.pkg }, now)?.let { screenshot(it, now) }
        }
    }
    private fun accept(r: WindowRead, texts: List<String>, actions: List<String>, at: Long, source: String): CourierObservation? {
        if (!Store.canRecordScreen(this) || CaptureState.receiverRole()) return null
        CaptureState.seen[r.pkg] = at
        if (actions.any(CourierParser::isAccept)) CaptureState.seenAccept[r.pkg] = at
        val obs = CourierParser.parse(r.pkg, texts, actions, at, source)
        if (deduper.shouldSave(r.pkg + source, texts)) {
            val j = CaptureRows.screen(Store.log(this), at, r.pkg, r.id, r.bounds.flattenToString(), source, obs.stage, texts, actions, r.nodes,
                Store.phoneBattery(this), r.spinner)
            Store.append(this, "screens.jsonl", j.toString()); CaptureState.screens++
        }
        sink().onScreen(r.pkg, texts, actions, at, source, spinner = source == "accessibility" && r.spinner,
            checked = if (source == "accessibility") r.checked else null, parsed = obs)
        return obs
    }
    private fun walk(n: AccessibilityNodeInfo, texts: MutableList<String>, actions: MutableList<String>, nodes: MutableList<CaptureRows.Node>, depth: Int, inherited: Boolean, window: Rect,
                     spin: MutableList<SliderSpinner.Box>? = null, labels: MutableList<SliderSpinner.Box>? = null, ticks: MutableList<Boolean>? = null) {
        if (depth > CaptureTiming.WALK_DEPTH || nodes.size > CaptureTiming.WALK_NODES || !n.isVisibleToUser) return
        val b = Rect(); n.getBoundsInScreen(b); if (!Rect.intersects(window, b)) return
        val actionable = n.isEnabled && (n.isClickable || inherited)
        val values = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).filter { it.isNotBlank() }.distinct()
        values.forEach { texts.add(it); if (actionable) actions.add(it) }
        // K33: a progress indicator has no text, so it is kept apart (class and bounds only, never logged as a node).
        if (spin != null) {
            val box = SliderSpinner.Box(b.left, b.top, b.right, b.bottom)
            if (SliderSpinner.isSpinnerClass(n.className?.toString())) spin.add(box)
            if (values.any(CourierParser::isDeliver)) labels?.add(box)
        }
        if (ticks != null && n.isCheckable) ticks.add(n.isChecked)
        if (values.isNotEmpty()) nodes.add(CaptureRows.Node(n.text?.toString() ?: "", n.contentDescription?.toString() ?: "",
            n.viewIdResourceName ?: "", n.className?.toString() ?: "", b.flattenToString(), actionable))
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, texts, actions, nodes, depth + 1, actionable, window, spin, labels, ticks) }
    }
    // Callers check SDK_INT >= 30 (lint enforces it for @RequiresApi).
    @RequiresApi(30)
    private fun screenshot(r: WindowRead, at: Long) {
        ocrBusy = true; pace.started(r.id, r.pkg, at)
        val shift = Store.prefs(this).getLong(KuryelogPrefs.SHIFT_START, 0)
        val display = OfferCapture.useDisplay(Build.VERSION.SDK_INT, invalidWindowAt, at)
        val callback = object : TakeScreenshotCallback {
            // 16 Oct: the screenshot and OCR callbacks run on the main thread like the screen reads; a failure is recorded, never thrown.
            override fun onSuccess(result: ScreenshotResult) {
                if (Store.guarded(this@WatchService, "ocr") { shot(result) } == null) ocrBusy = false
            }
            private fun shot(result: ScreenshotResult) {
                val hardware = result.hardwareBuffer
                val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace)
                // A display screenshot is drawn on (masked), so it is copied mutable; a window screenshot holds no overlay.
                val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, display)
                wrapped?.recycle(); hardware.close()
                if (bitmap == null) { ocrBusy = false; return }
                if (display) maskOverlays(bitmap)
                // D6: crop, scale and fingerprint off the main thread (the badge and the floating panel draw there).
                runCatching { prep.execute {
                    val image = runCatching { ocrInput(bitmap, if (display) Rect(r.bounds) else null) }.getOrNull()
                    val hash = image?.let { runCatching { fingerprint(it) }.getOrNull() }
                    h.post { if (image == null || hash == null) { image?.recycle(); ocrBusy = false }
                        else if (Store.guarded(this@WatchService, "ocr") { recognize(r, image, hash, at, shift) } == null) { image.recycle(); ocrBusy = false } }
                } }.onFailure { bitmap.recycle(); ocrBusy = false }
            }
            override fun onFailure(errorCode: Int) {
                ocrBusy = false
                // D6: a failed screenshot is retried at the normal pace, never in the hot loop.
                pace.failed(r.id, at)
                // K37: an invalid window (code 5) switches OCR to the display screenshot cropped to the window for a while.
                if (errorCode == OfferCapture.INVALID_WINDOW && !display) invalidWindowAt = at
                Store.event(this@WatchService, "screenshot_unavailable", CaptureRows.screenshotUnavailable(r.pkg, errorCode, display))
                Store.log(this@WatchService).lastError = CaptureText.OCR_UNAVAILABLE.get(Store.lang(this@WatchService)).format(errorCode)
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !display) takeScreenshotOfWindow(r.id, mainExecutor, callback)
        else takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
    }
    /** D6: [bitmap] cropped to the window ([clip], display screenshots only) and at most OfferCapture.OCR_MAX_WIDTH wide (ML Kit's
     *  time grows with the pixels; text stays ≥16 px). Null for an empty crop. Intermediate bitmaps are recycled. */
    private fun ocrInput(bitmap: Bitmap, clip: Rect?): Bitmap? {
        val window = if (clip == null) bitmap else {
            // intersect() leaves a window outside the bitmap unchanged and returns false: that is an empty crop too.
            if (!clip.intersect(0, 0, bitmap.width, bitmap.height) || clip.isEmpty) { bitmap.recycle(); return null }
            Bitmap.createBitmap(bitmap, clip.left, clip.top, clip.width(), clip.height()).also { if (it !== bitmap) bitmap.recycle() }
        }
        val scale = OfferCapture.ocrScale(window.width)
        if (scale >= 1f) return window
        return Bitmap.createScaledBitmap(window, maxOf(1, Math.round(window.width * scale)), maxOf(1, Math.round(window.height * scale)), true)
            .also { if (it !== window) window.recycle() }
    }
    /** D6: a screenshot identical to the window's previous one is not OCR'd again; its lines are used. Main thread. */
    private fun recognize(r: WindowRead, image: Bitmap, hash: Long, at: Long, shift: Long) {
        if (!connected) { image.recycle(); ocrBusy = false; return }
        pace.same(r.id, hash)?.let { lines -> image.recycle(); ocrLines(r, lines, at, shift); ocrBusy = false; afterOcr(); return }
        recognizer.process(InputImage.fromBitmap(image, 0)).addOnSuccessListener { resultText -> Store.guarded(this@WatchService, "ocr") {
            val lines = resultText.textBlocks.flatMap { it.lines }.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })).map { it.text }
            pace.remember(r.id, hash, lines)
            ocrLines(r, lines, at, shift)
        } }.addOnFailureListener { Store.failure(this@WatchService, "ocr", it) }
            .addOnCompleteListener { image.recycle(); ocrBusy = false; afterOcr() }
    }
    /** OCR lines of window [r] captured at [at] (or reused for an identical screenshot): parsed and recorded unless stale. */
    private fun ocrLines(r: WindowRead, lines: List<String>, at: Long, shift: Long) {
        // Discard a stale OCR callback when the app/window or manual session changed.
        val stillVisible = windows.any { it.id == r.id && it.root?.packageName?.toString() == r.pkg }
        if (!OcrAcceptance.fresh(connected, Store.canRecordScreen(this), Store.prefs(this).getLong(KuryelogPrefs.SHIFT_START, 0) == shift, stillVisible, at, now(), lines)) return
        // Native populated order state wins over an OCR frame taken during a transition.
        val semanticActions = OcrAcceptance.accept(r.pkg, r.texts, r.actions, lines, at) ?: return
        accept(r, lines, semanticActions, at, "ocr")?.let { pace.read(r.id, it) }
    }
    /** D6: while a window is hot (offer coming or loading), the next read follows as soon as Android allows another screenshot. */
    private fun afterOcr() { if (connected) pace.hotDelay(now())?.let { h.postDelayed(capture, it) } }
    /** D6: OCR input preparation (crop, scale, fingerprint); its [pixels] buffer is used on that thread only. */
    private val prep = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var pixels = IntArray(0)
    /** D6: hash of every pixel of the OCR input (row blocks, one reused buffer), so an unchanged screen is not OCR'd again. */
    private fun fingerprint(b: Bitmap): Long {
        val w = b.width; val rows = maxOf(1, minOf(b.height, 65_536 / maxOf(1, w)))
        if (pixels.size < w * rows) pixels = IntArray(w * rows)
        var hash = OcrPace.FNV_OFFSET xor ((w.toLong() shl 32) or b.height.toLong())
        var y = 0
        while (y < b.height) { val n = minOf(rows, b.height - y); b.getPixels(pixels, 0, w, 0, y, w, n); hash = OcrPace.fnv(hash, pixels, w * n); y += n }
        return hash
    }
    /**
     * A display screenshot (K37 fallback) also shows our own overlays over the courier app: the K35 badge and the scooter's
     * floating panel, whose numbers (a "12.4" value over its "km" caption) could be read as the offer's km or a price. Blank them
     * ([OwnOverlays]: each module registers its windows).
     */
    private fun maskOverlays(b: Bitmap) {
        if (!b.isMutable) return
        val rects = OwnOverlays.rects()
        if (rects.isEmpty()) return
        val c = Canvas(b); val black = Paint().apply { color = Color.BLACK }
        for (r in rects) c.drawRect(r, black)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!connected) return
            var shift = false
            // A failing step is recorded, never thrown (it would kill the process and Android unbinds the service), and the
            // next tick is always scheduled: before, one exception here also stopped the badge, the cards and the home watcher.
            try {
                Store.guarded(this@WatchService, "tick") {
                    // K38/E7: the process-wide home watcher (the notification listener drives it too, screen reading or not).
                    HomeWatch.refresh(this@WatchService)
                    shift = Store.shiftOn(this@WatchService)
                    syncEventScope(Store.canRecordScreen(this@WatchService) && !CaptureState.receiverRole())
                    // Orders tick, badge and reminders (RS: OrderTracker.tick, badge, DeliveryReminder.update; off shift both go).
                    CaptureState.host.onTick(this@WatchService, now(), shift)
                    // During a shift the screen is read at least once per tick.
                    if (shift && !scheduled) { scheduled = true; h.post(capture) }
                }
            } finally {
                // 0.22.2: off shift nothing on screen needs half-second updates (home watching is rate-limited to 5 s anyway).
                h.postDelayed(this, if (shift) CaptureTiming.TICK_SHIFT_MS else CaptureTiming.TICK_IDLE_MS)
            }
        }
    }
    /**
     * 0.22.2 (battery): off shift Android delivers only this app's own events instead of every content change of every
     * app (they were dropped right away by [Store.canRecordScreen], but each one still woke the service). While recording,
     * A10.2: only the watched apps and the dialers ([Dialer.eventScope]), never every app (banking, messages). The tick checks
     * every 0.5–2 s, so a change of the watched apps applies within a tick.
     */
    private var eventScope: Set<String>? = null
    private val defaultDialer: String? by lazy { runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull() }
    private fun syncEventScope(recording: Boolean) {
        val scope = if (recording) Dialer.eventScope(Store.targets(this), defaultDialer, packageName) else setOf(packageName)
        if (eventScope == scope) return
        Store.guarded(this, "event_scope") {
            val info = serviceInfo ?: return
            info.packageNames = scope.toTypedArray()
            serviceInfo = info
            eventScope = scope
        }
    }
    override fun onInterrupt() {}
    override fun onDestroy() {
        connected = false; CaptureState.running = false; h.removeCallbacksAndMessages(null); prep.shutdownNow()
        Store.guarded(this, "screen_start") { CaptureState.host.onDisconnected(this) }
        recognizer.close(); super.onDestroy()
    }

    companion object {
        /** Tercih `ocr` (varsayılan açık; Daha fazla → "Görünür ekran OCR yedeği", CAPTURE.md 6.4). */
        const val OCR_PREF = "ocr"
    }
}
