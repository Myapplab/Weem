package com.kuryelog.app.orders

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kuryelog.app.core.Store
import com.kuryelog.app.core.WeemFonts
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.core.OverlayPalette
import com.kuryelog.logic.orders.CustomerBubble
import com.kuryelog.logic.orders.CustomerCard
import com.kuryelog.logic.orders.OrderFlow
import com.kuryelog.logic.orders.Readiness
import com.kuryelog.logic.orders.RestaurantBubble

/**
 * R6 (ORDERS.md 5.2–5.3; RS `DeliveryReminder.kt` @ 6388c04, satır satır). WEEM: `OrderTracker` → [Orders.flow] ([OrderFlow]),
 * Sürüş Paneli paleti → [OverlayPalette] (aynı renkler), bildirim numarası [NotificationIds.DELIVERY_NOTE], dokunuş uygulamanın açılış
 * Activity'si (ORDERS.md 16.3).
 *
 * Customer or restaurant card at the TOP of the screen, so the courier app's slider and buttons
 * at the bottom stay free. Customer (F7.3, [CustomerBubble]): a thin bubble, Building · Floor · Flat · Code on the left and the
 * platform's minutes in a circle on the right, the notes unchanged below it. Restaurant (E14): a one-line bubble, the restaurant's name on the left and its E15 ready
 * state in a circle on the right; tapping it opens the full restaurant card (ready / ready in N min, note, order, address).
 */
class DeliveryReminder(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var facts: TextView? = null
    private var building: TextView? = null
    private var row: LinearLayout? = null
    private val cells = ArrayList<Pair<TextView, TextView>>()
    private var body: TextView? = null
    private var header: TextView? = null
    private var sub: TextView? = null
    private var call: Button? = null
    private var phone: String? = null
    private var key: String? = null
    private var expanded = false
    private var styledDark: Boolean? = null
    private val secondaryButtons = ArrayList<Button>()
    /** E14 bubble of the open restaurant reminder, and the reminder ("trip:pickup") the courier tapped open into the card. */
    private var bubble: LinearLayout? = null
    private var bubbleName: TextView? = null
    private var bubbleReady: TextView? = null
    private var bubbleKey: String? = null
    private var bubbleStyle: Pair<Boolean, Readiness.Kind?>? = null
    private var restaurantExpanded: String? = null
    /** F7.3 customer bubble ([CustomerBubble]) and the notes under it. */
    private var customer: LinearLayout? = null
    private var customerLine: TextView? = null
    private var customerOrders: TextView? = null
    private var customerMinutes: TextView? = null
    private var customerNotes: LinearLayout? = null
    private var customerBody: TextView? = null
    private var customerCall: Button? = null
    private var customerKey: String? = null
    private var customerStyle: Pair<Boolean, Boolean>? = null
    /** F7.4: a vertical swipe on the bubble folds/unfolds the notes (until this customer's bubble closes). */
    private var notesOpen = true
    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = (v * service.resources.displayMetrics.density).toInt()
    private fun tl(tr: String, az: String) = Orders.tl(service, tr, az)
    fun update(r: OrderFlow.Reminder?) {
        if (r == null || !Store.prefs(service).getBoolean("customerCard", true)) { close(); return }
        // E14: a restaurant reminder is the bubble until the courier taps it open.
        if (r.kind == "customer") { closeCard(); closeBubble(); showCustomer(r); return }
        closeCustomer()
        if (r.kind == "restaurant" && restaurantExpanded != "${r.trip}:${r.customer}") { closeCard(); showBubble(r); return }
        closeBubble()
        val next = "${r.kind}:${r.trip}:${r.customer}"
        if (key != next) { closeCard(); key = next; show() }
        style()
        val d = r.details
        val restaurant = r.kind == "restaurant"
        // Door code and CHECK ID stay large; building and the Entrance · Floor · Apartment row have their own place.
        val factText = if (restaurant) r.headline.orEmpty() else listOfNotNull(
            d.doorCode?.let { tl("Kapı kodu: ", "Qapı kodu: ") + it }, tl("Kimlik kontrolü gerekli", "Şəxsiyyət yoxlaması lazımdır").takeIf { d.checkId }).joinToString("   ·   ")
        val buildingText = if (restaurant) "" else d.building?.let { tl("Bina: ", "Bina: ") + it }.orEmpty()
        val values = listOf(d.entrance, d.floor, d.apartment)
        val showRow = !restaurant && values.any { it != null }
        building?.let { if (it.text.toString() != buildingText) it.text = buildingText; it.visibility = if (buildingText.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE }
        row?.visibility = if (showRow) android.view.View.VISIBLE else android.view.View.GONE
        if (showRow) cells.forEachIndexed { i, (_, value) -> val v = values[i] ?: "–"; if (value.text.toString() != v) value.text = v }
        val rest = if (restaurant) r.body.orEmpty() else (listOfNotNull(
            d.addressDetails?.let { tl("Adres detayı: ", "Ünvan detalları: ") + it },
            d.note?.let { tl("Not: ", "Qeyd: ") + it },
            d.instructions?.takeIf { it != d.note }?.let { tl("Talimat: ", "Təlimat: ") + it },
            d.address,
            tl("Not kısaltılmış: tamamı için kurye uygulamasında Notes'u aç.", "Qeyd qısaldılıb: tam mətn üçün kuryer tətbiqində Notes bölməsini aç.").takeIf { d.noteTruncated }
        )).joinToString("\n\n").ifBlank { tl("Müşteri detay paneli henüz okunmadı. Kurye uygulamasında paneli açınca bu kart güncellenir.", "Müştəri detalları paneli hələ oxunmayıb. Kuryer tətbiqində paneli açanda bu kart yenilənir.") }
        if (header?.text?.toString() != r.title) header?.text = r.title
        if (sub?.text?.toString() != r.estimate) sub?.text = r.estimate
        facts?.let { if (it.text.toString() != factText) it.text = factText; it.visibility = if (factText.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE }
        if (body?.text?.toString() != rest) body?.text = rest
        phone = d.phone
        call?.let { it.text = d.phone?.let { p -> tl("Ara ", "Zəng et ") + p } ?: ""; it.visibility = if (d.phone == null) android.view.View.GONE else android.view.View.VISIBLE }
    }
    /** Hap köşesi (dp): yüksekliğin yarısından büyük her değer tam yuvarlak. */
    private val PILL = 100
    private fun rounded(color: Int, radius: Int = 16) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    // A10.4d (`docs/TASARIM.md` 7): düğmeler hap biçiminde.
    private fun buttonBackground(fill: Int, ink: Int) = RippleDrawable(ColorStateList.valueOf((ink and 0x00ffffff) or 0x22000000), rounded(fill, PILL), rounded(Color.WHITE, PILL))
    private fun style() {
        val dark = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (styledDark == dark || root == null) return
        val p = OverlayPalette.forMode(dark)
        root?.background = rounded(p.surface, 24).apply { setStroke(dp(1), p.line) }
        header?.setTextColor(p.text); sub?.setTextColor(p.muted); facts?.setTextColor(p.accentText)
        building?.setTextColor(p.text); body?.setTextColor(p.text)
        row?.background = rounded(p.raised)
        cells.forEach { (name, value) -> name.setTextColor(p.muted); value.setTextColor(p.text) }
        call?.apply { setTextColor(p.accentInk); background = buttonBackground(p.accent, p.accentInk) }
        secondaryButtons.forEach { it.setTextColor(p.text); it.background = buttonBackground(p.raised, p.text) }
        styledDark = dark
    }
    /** E16: the card's size choice (Ayarlar → Ekran), 85 % of the old card by default; text and height follow it. */
    private fun sizePct() = CustomerCard.sizePct(Store.prefs(service).getInt(CustomerCard.SIZE_KEY, CustomerCard.DEFAULT_SIZE_PCT))
    private fun show() {
        val medium = WeemFonts.get(WeemFonts.SEMIBOLD); val regular = WeemFonts.get(WeemFonts.REGULAR)
        val k = CustomerCard.scale(sizePct())
        val column = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(10)); elevation = dp(8).toFloat() }
        header = TextView(service).apply { textSize = 17f * k; typeface = medium; isAccessibilityHeading = true }.also { column.addView(it) }
        sub = TextView(service).apply { textSize = 13f * k; typeface = regular; setPadding(0, dp(4), 0, 0) }.also { column.addView(it) }
        facts = TextView(service).apply { textSize = 24f * k; typeface = medium; setPadding(0, dp(8), 0, dp(4)) }.also { column.addView(it) }
        building = TextView(service).apply { textSize = 18f * k; typeface = medium; setPadding(0, dp(4), 0, dp(4)) }.also { column.addView(it) }
        cells.clear()
        row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; setPadding(dp(12), dp(8), dp(12), dp(8)) }.also { r ->
            for (label in listOf(tl("Giriş", "Giriş"), tl("Kat", "Mərtəbə"), tl("Daire", "Mənzil"))) {
                val cell = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
                val name = TextView(service).apply { textSize = 12f * k; typeface = regular; text = label }
                val value = TextView(service).apply { textSize = 22f * k; typeface = medium; setPadding(0, dp(2), dp(8), 0) }
                cell.addView(name); cell.addView(value); r.addView(cell, LinearLayout.LayoutParams(0, -2, 1f)); cells.add(name to value)
            }
            column.addView(r)
        }
        call = Button(service).apply { isAllCaps = false; textSize = 16f * k; typeface = medium; minHeight = dp(52); setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { phone?.let { p -> runCatching { service.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.filter { c -> c.isDigit() || c == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } } }
        column.addView(call, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(4) })
        val scroll = ScrollView(service).apply { isVerticalScrollBarEnabled = false }
        body = TextView(service).apply { textSize = 16f * k; typeface = regular; setLineSpacing(0f, 1.15f); setPadding(0, dp(10), 0, dp(10)) }
        scroll.addView(body); column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(service)
        fun small(label: String) = Button(service).apply { isAllCaps = false; text = label; textSize = 14f * k; typeface = medium; minHeight = dp(48); setPadding(dp(10), dp(10), dp(10), dp(10)) }.also { secondaryButtons.add(it) }
        val expand = small(tl("Büyüt", "Böyüt")); val hide = small(tl("Kapat", "Bağla"))
        buttons.addView(expand, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }); buttons.addView(hide, LinearLayout.LayoutParams(0, -2, 1f))
        column.addView(buttons)
        val lp = WindowManager.LayoutParams(-1, height(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP; lp.y = dp(28); lp.horizontalMargin = 0.02f
        expand.setOnClickListener { expanded = !expanded; lp.height = height(); wm.updateViewLayout(column, lp); expand.text = if (expanded) tl("Küçült", "Kiçilt") else tl("Büyüt", "Böyüt") }
        hide.setOnClickListener { Orders.flow(service).dismissReminder(); close() }
        runCatching { wm.addView(column, lp); root = column; style() }.onFailure { Store.log(service).lastError = tl("Müşteri kartı açılamadı: ", "Müştəri kartı açılmadı: ") + it.message }
    }
    private fun height(): Int {
        val h = if (android.os.Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height() else service.resources.displayMetrics.heightPixels
        return (h * CustomerCard.heightShare(sizePct(), expanded)).toInt()
    }
    fun close() { closeCard(); closeBubble(); closeCustomer(); restaurantExpanded = null }
    private fun closeCard() { root?.let { runCatching { wm.removeView(it) } }; root = null; body = null; header = null; sub = null; facts = null; building = null; row = null; cells.clear(); call = null; phone = null; key = null; expanded = false; styledDark = null; secondaryButtons.clear() }

    private fun screenWidth(): Int = if (android.os.Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width() else service.resources.displayMetrics.widthPixels

    /** E14: the restaurant bubble: [RestaurantBubble.DEFAULT_WIDTH_PCT] of the screen width (Ayarlar → Ekran), name left, ready circle right. */
    private fun showBubble(r: OrderFlow.Reminder) {
        val k = "${r.trip}:${r.customer}"
        if (bubbleKey != k) { closeBubble(); addBubble(k) }
        val name = r.place ?: r.title
        bubbleName?.let { if (it.text.toString() != name) it.text = name }
        val tl = { a: String, b: String -> tl(a, b) }
        val ready = r.ready?.let { Readiness.short(it, tl) } ?: "?"
        bubbleReady?.let { if (it.text.toString() != ready) it.text = ready; it.contentDescription = r.ready?.let { s -> Readiness.headline(s, tl) } ?: ready }
        styleBubble(r.ready?.kind)
    }
    private fun addBubble(k: String) {
        val pct = RestaurantBubble.widthPct(Store.prefs(service).getInt(RestaurantBubble.WIDTH_KEY, RestaurantBubble.DEFAULT_WIDTH_PCT))
        val scale = RestaurantBubble.scale(pct)
        val medium = WeemFonts.get(WeemFonts.SEMIBOLD)
        val pill = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dpf(18 * scale), dpf(8 * scale), dpf(8 * scale), dpf(8 * scale)); elevation = dp(8).toFloat(); isClickable = true }
        val name = TextView(service).apply { textSize = 17f * scale; typeface = medium; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        pill.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        val size = dpf(56 * scale)
        val ready = TextView(service).apply { textSize = 13f * scale; typeface = medium; gravity = Gravity.CENTER; maxLines = 2; includeFontPadding = false }
        pill.addView(ready, LinearLayout.LayoutParams(size, size).apply { marginStart = dp(10) })
        val lp = WindowManager.LayoutParams(RestaurantBubble.widthPx(screenWidth(), pct), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; lp.y = dp(28)
        // Tap: the full restaurant card (note, order, address); its Kapat closes both for this pickup.
        pill.setOnClickListener { restaurantExpanded = k; update(Orders.flow(service).reminder) }
        runCatching { wm.addView(pill, lp); bubble = pill; bubbleName = name; bubbleReady = ready; bubbleKey = k }
            .onFailure { Store.log(service).lastError = tl("Restoran balonu açılamadı: ", "Restoran balonu açılmadı: ") + it.message }
    }
    /** Pill in the card colours; the circle green when ready, amber when the time is up, plain while counting. */
    private fun styleBubble(kind: Readiness.Kind?) {
        val dark = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (bubble == null || bubbleStyle == dark to kind) return
        val p = OverlayPalette.forMode(dark)
        bubble?.background = rounded(p.surface, 32).apply { setStroke(dp(1), p.line) }
        bubbleName?.setTextColor(p.text)
        val (fill, ink) = when (kind) { Readiness.Kind.READY -> p.positiveBg to p.positive; Readiness.Kind.NOW -> p.warnBg to p.warn
            Readiness.Kind.COUNTDOWN -> p.raised to p.text; else -> p.raised to p.muted }
        bubbleReady?.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
        bubbleReady?.setTextColor(ink)
        bubbleStyle = dark to kind
    }
    /** F7.3: the customer bubble; the width is the restaurant bubble's setting, the text size the card's (Ayarlar → Ekran). */
    private fun showCustomer(r: OrderFlow.Reminder) {
        val k = "${r.trip}:${r.customer}"
        if (customerKey != k) { closeCustomer(); addCustomer(k) }
        val d = r.details
        val tl = { a: String, b: String -> tl(a, b) }
        val line = CustomerBubble.line(d, d.name, tl)
        customerLine?.let { if (it.text.toString() != line) it.text = line }
        val minutes = CustomerBubble.minutes(r.deadlineAt, System.currentTimeMillis(), tl).orEmpty()
        customerMinutes?.let { if (it.text.toString() != minutes) it.text = minutes }
        val orders = CustomerBubble.orders(r.orders, tl)
        customerOrders?.let { if (it.text.toString() != orders.orEmpty()) it.text = orders.orEmpty(); it.visibility = if (orders == null) android.view.View.GONE else android.view.View.VISIBLE }
        val notes = (CustomerBubble.notes(d, tl) + listOfNotNull(
            tl("Not kısaltılmış: tamamı için kurye uygulamasında Notes'u aç.", "Qeyd qısaldılıb: tam mətn üçün kuryer tətbiqində Notes bölməsini aç.").takeIf { d.noteTruncated }))
            .joinToString("\n\n").ifBlank { tl("Müşteri detay paneli henüz okunmadı. Kurye uygulamasında paneli açınca bu kart güncellenir.", "Müştəri detalları paneli hələ oxunmayıb. Kuryer tətbiqində paneli açanda bu kart yenilənir.") }
        customerBody?.let { if (it.text.toString() != notes) it.text = notes }
        phone = d.phone
        customerCall?.let { it.text = d.phone?.let { p -> tl("Ara ", "Zəng et ") + p } ?: ""; it.visibility = if (d.phone == null) android.view.View.GONE else android.view.View.VISIBLE }
        styleCustomer(r.deadlineAt?.let { it < System.currentTimeMillis() } == true)
    }
    private fun addCustomer(k: String) {
        val pct = RestaurantBubble.widthPct(Store.prefs(service).getInt(CustomerBubble.WIDTH_KEY, RestaurantBubble.DEFAULT_WIDTH_PCT))
        val s = CustomerCard.scale(sizePct())
        val medium = WeemFonts.get(WeemFonts.SEMIBOLD); val regular = WeemFonts.get(WeemFonts.REGULAR)
        val column = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL; elevation = dp(8).toFloat() }
        val pill = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dpf(18 * s), dpf(8 * s), dpf(8 * s), dpf(8 * s)) }
        val left = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        val line = TextView(service).apply { textSize = 17f * s; typeface = medium; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val orders = TextView(service).apply { textSize = 12f * s; typeface = regular; visibility = android.view.View.GONE }
        left.addView(line); left.addView(orders); pill.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val size = dpf(CustomerBubble.BUBBLE_DP * s)
        val minutes = TextView(service).apply { textSize = 13f * s; typeface = medium; gravity = Gravity.CENTER; maxLines = 1; includeFontPadding = false }
        pill.addView(minutes, LinearLayout.LayoutParams(size, size).apply { marginStart = dp(10) })
        column.addView(pill)
        // The notes: at most a quarter of the screen, scrolled when longer; the small call button under them.
        val maxNotes = (screenHeight() * CustomerBubble.NOTES_MAX_SHARE).toInt()
        val scroll = object : ScrollView(service) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, android.view.View.MeasureSpec.makeMeasureSpec(maxNotes, android.view.View.MeasureSpec.AT_MOST))
        }.apply { isVerticalScrollBarEnabled = false }
        val body = TextView(service).apply { textSize = 15f * s; typeface = regular; setLineSpacing(0f, 1.15f) }
        scroll.addView(body)
        val notes = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL; setPadding(dpf(18 * s), 0, dpf(18 * s), dp(10)) }
        notes.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        val call = Button(service).apply { isAllCaps = false; textSize = 13f * s; typeface = medium; minHeight = dp(40); minimumHeight = dp(40); setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { phone?.let { p -> runCatching { service.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.filter { c -> c.isDigit() || c == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } } }
        notes.addView(call, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = dp(6) })
        column.addView(notes)
        // F7.4: up/down folds the notes, left/right removes the bubble; it does not open again for this customer
        // ([CustomerRecord.reminderAt] stays set). The notes scroll on their own.
        var downX = 0f; var downY = 0f
        pill.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY }
                MotionEvent.ACTION_UP -> when (CustomerBubble.swipe(e.rawX - downX, e.rawY - downY, v.width, service.resources.displayMetrics.density)) {
                    CustomerBubble.Swipe.DISMISS -> { Orders.flow(service).dismissReminder(); close() }
                    CustomerBubble.Swipe.TOGGLE -> { notesOpen = !notesOpen; customerNotes?.visibility = if (notesOpen) android.view.View.VISIBLE else android.view.View.GONE }
                    CustomerBubble.Swipe.NONE -> v.performClick()
                }
            }
            true
        }
        val lp = WindowManager.LayoutParams(RestaurantBubble.widthPx(screenWidth(), pct), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; lp.y = dp(28)
        runCatching { wm.addView(column, lp); customer = column; customerLine = line; customerOrders = orders; customerMinutes = minutes
            customerNotes = notes; customerBody = body; customerCall = call; customerKey = k }
            .onFailure { Store.log(service).lastError = tl("Müşteri kartı açılamadı: ", "Müştəri kartı açılmadı: ") + it.message }
    }
    /** Card colours; the minutes circle amber once the platform's time is up. */
    private fun styleCustomer(late: Boolean) {
        val dark = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (customer == null || customerStyle == dark to late) return
        val p = OverlayPalette.forMode(dark)
        customer?.background = rounded(p.surface, 28).apply { setStroke(dp(1), p.line) }
        customerLine?.setTextColor(p.text); customerOrders?.setTextColor(p.accentText); customerBody?.setTextColor(p.text)
        customerMinutes?.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (late) p.warnBg else p.raised) }
        customerMinutes?.setTextColor(if (late) p.warn else p.text)
        customerCall?.apply { setTextColor(p.text); background = buttonBackground(p.raised, p.text) }
        customerStyle = dark to late
    }
    private fun closeCustomer() {
        customer?.let { runCatching { wm.removeView(it) } }
        customer = null; customerLine = null; customerOrders = null; customerMinutes = null; customerNotes = null; customerBody = null; customerCall = null
        customerKey = null; customerStyle = null; notesOpen = true
    }
    private fun screenHeight(): Int = if (android.os.Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height() else service.resources.displayMetrics.heightPixels
    private fun closeBubble() { bubble?.let { runCatching { wm.removeView(it) } }; bubble = null; bubbleName = null; bubbleReady = null; bubbleKey = null; bubbleStyle = null }
    companion object {
        /** [update]: the same card's notification posted again with new text (E15 counter): no sound or vibration again. */
        fun notify(ctx: Context, r: OrderFlow.Reminder, update: Boolean = false) {
            val az = Store.lang(ctx) == Lang.AZ
            val nm = ctx.getSystemService(NotificationManager::class.java)
            // A10.2: kilit ekranında müşteri adı, telefonu ve notu görünmez (kanal ve bildirim gizli; kilit ekranında yalnız [publicVersion]).
            nm.createNotificationChannel(NotificationChannel(CHANNEL, Orders.tl(ctx, "Müşteri teslimat notu", "Müştəri çatdırılma qeydi"), NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true); lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
            val open = PendingIntent.getActivity(ctx, NotificationIds.DELIVERY_NOTE, ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent().setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE)
            val restaurant = r.kind == "restaurant"
            val text = if (restaurant) r.headline ?: r.estimate else r.details.facts(az).joinToString(" · ").ifBlank { r.details.note ?: r.details.instructions ?: r.details.address ?: Orders.tl(ctx, "Teslimat bilgileri", "Çatdırılma məlumatları") }
            val big = if (restaurant) listOfNotNull(r.headline, r.estimate, r.body).joinToString("\n") else r.details.display(az)
            val public = Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(Orders.tl(ctx, "Müşteri teslimat notu", "Müştəri çatdırılma qeydi")).build()
            nm.notify(NotificationIds.DELIVERY_NOTE, Notification.Builder(ctx, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(r.title)
                .setContentText(text).setStyle(Notification.BigTextStyle().bigText(big)).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(update)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).build())
        }
        private const val CHANNEL = "delivery_notes"
    }
}
