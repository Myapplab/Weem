package com.kuryelog.app.orders

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.kuryelog.app.capture.CaptureHost
import com.kuryelog.app.core.OwnOverlays
import com.kuryelog.app.core.Shift
import com.kuryelog.app.core.Store
import com.kuryelog.logic.orders.OverlayText

/**
 * Erişilebilirlik servisindeki sipariş işleri (CAPTURE.md 7.1): sipariş tiki, R7 rozeti (ORDERS.md 6; RS `WatchService.kt:313-401`
 * @ 6388c04, satır satır) ve R6 hatırlatıcı pencereleri ([DeliveryReminder]). `TYPE_ACCESSIBILITY_OVERLAY` pencereleri servisin
 * bağlamıyla açılır. WEEM: rozetin dikdörtgeni [OwnOverlays]'e kayıtlı (tüm ekran OCR'ında boyanır, CAPTURE.md 3.5).
 */
// Pencereler ve hatırlatıcı servisin bağlamıyla açılır, servis kapanınca bırakılır (onDisconnected): sızıntı yok.
@SuppressLint("StaticFieldLeak")
object OrdersHost : CaptureHost {
    private var badge: TextView? = null
    private var badgeDark: Boolean? = null
    private var reminders: DeliveryReminder? = null
    /** Every 500 ms; a TextView re-lays out its overlay window on each setText, so an unchanged badge is left as it is. */
    private var badgeLp: WindowManager.LayoutParams? = null
    private var badgeDodging = false

    /** RS `maskOverlays`: the badge's place on screen while it is attached. */
    private val badgeRect: () -> Rect? = {
        badge?.takeIf { it.isAttachedToWindow && it.width > 0 }?.let { v -> val at = IntArray(2); v.getLocationOnScreen(at); Rect(at[0], at[1], at[0] + v.width, at[1] + v.height) }
    }

    override fun onConnected(service: AccessibilityService) {
        Orders.flow(service).init()
        reminders = runCatching { DeliveryReminder(service) }.getOrNull()
        OwnOverlays.remove(badgeRect); OwnOverlays.add(badgeRect)
    }

    override fun onTick(service: AccessibilityService, now: Long, shiftOn: Boolean) {
        val flow = Orders.flow(service)
        if (shiftOn) {
            flow.tick(now)
            // A3.11 (S16 (a)): gölge modda rozet ve balon/kart pencereleri çizilmez; akış ve kayıtlar sürer.
            if (flow.shadowMode()) { removeBadge(service); reminders?.update(null) }
            else if (Store.prefs(service).getBoolean(OVERLAY_PREF, true)) { if (badge == null) addBadge(service); updateBadge(service) }
            else removeBadge(service)
            if (!flow.shadowMode()) reminders?.update(flow.reminder)
        } else { removeBadge(service); reminders?.update(null) }
    }

    override fun onDisconnected(service: AccessibilityService) {
        removeBadge(service); reminders?.close(); reminders = null
        OwnOverlays.remove(badgeRect)
    }

    private fun addBadge(s: AccessibilityService) {
        val wm = s.getSystemService(WindowManager::class.java)
        // K35: the badge wraps its text (no names, so no long line that wraps and leaves empty width); no extra font padding.
        val density = s.resources.displayMetrics.density
        val tv = TextView(s).apply { textSize = 12f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); includeFontPadding = false
            setPadding((10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt()); elevation = 4 * density }
        val p = Store.prefs(s)
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.END; lp.x = p.getInt(BX, 12); lp.y = p.getInt(BY, 95)
        var x = 0f; var y = 0f; var ox = 0; var oy = 0
        tv.setOnTouchListener { _, e ->
            when (e.action) { MotionEvent.ACTION_DOWN -> { x = e.rawX; y = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> { lp.x = ox - (e.rawX - x).toInt(); lp.y = oy + (e.rawY - y).toInt(); wm.updateViewLayout(tv, lp) }
                MotionEvent.ACTION_UP -> p.edit().putInt(BX, lp.x).putInt(BY, lp.y).apply() }; true
        }
        badgeLp = lp; badgeDodging = false
        runCatching { wm.addView(tv, lp); badge = tv; styleBadge(s) }
            .onFailure { Store.log(s).lastError = Orders.tl(s, "Rozet açılamadı: ", "Nişan açılmadı: ") + it.message }
    }
    private fun styleBadge(s: AccessibilityService) {
        val b = badge ?: return
        val dark = OwnOverlays.dark(s)
        if (badgeDark == dark) return
        val p = OwnOverlays.palette(s)
        b.setTextColor(p.text)
        b.background = GradientDrawable().apply { cornerRadius = 16 * s.resources.displayMetrics.density; setColor(p.surface); setStroke((s.resources.displayMetrics.density + .5f).toInt(), p.line) }
        badgeDark = dark
    }
    /**
     * D21 (13 Oct field photos): on an offer card the badge covered Wolt's Decline (top right) or the card's "Expected earnings"
     * line (bottom). While a card is on screen it moves into the map band (15–50 % of the height) and shrinks; the courier's own
     * position (prefs bx/by) comes back when the card is gone. A position the courier chose inside that band is kept.
     */
    private fun dodgeOffer(s: AccessibilityService, b: TextView) {
        val lp = badgeLp ?: return
        val dodge = Orders.flow(s).offerVisible(System.currentTimeMillis())
        if (dodge == badgeDodging) return
        val h = s.resources.displayMetrics.heightPixels
        val saved = Store.prefs(s).getInt(BY, 95)
        lp.y = if (!dodge || saved in (h * 0.15).toInt()..(h * 0.5).toInt()) saved else (h * 0.30).toInt()
        b.textSize = if (dodge) 10f else 12f
        runCatching { s.getSystemService(WindowManager::class.java).updateViewLayout(b, lp) }
        badgeDodging = dodge
    }
    private fun updateBadge(s: AccessibilityService) {
        val b = badge ?: return
        styleBadge(s)
        dodgeOffer(s, b)
        val text = OverlayText.distance(Shift.track.distanceM / 1000) + Orders.flow(s).badgeLines(names = false)
        if (b.text.toString() != text) b.text = text
    }
    private fun removeBadge(s: AccessibilityService) {
        badge?.let { runCatching { s.getSystemService(WindowManager::class.java).removeView(it) } }; badge = null; badgeDark = null; badgeLp = null; badgeDodging = false
    }

    /** Tercihler (ORDERS.md 6.2): rozet açık (varsayılan true) ve yeri piksel. */
    const val OVERLAY_PREF = "overlay"
    const val BX = "bx"
    const val BY = "by"
}
