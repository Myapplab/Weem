package com.kuryelog.app.orders

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.kuryelog.logic.orders.CashTips

/**
 * R8 (ORDERS.md 7.2; RS `TipAmountActivity.kt` @ 6388c04, satır satır; `OrderTracker` → [Orders.flow]).
 *
 * F4.7/F4.9 (Ekim planı madde 11, "WhatsApp gibi"): the small window a tap on the cash tip question opens instead of
 * the app's home screen. The cash amount (K31) on top, "Aldığın para" below with the tip computed while typing; when the cash
 * amount was not read it is typed here too. Saving appends the K32 row like the inline reply. Not exported.
 */
class TipAmountActivity : Activity() {
    private lateinit var tripId: String
    private var cash: Double? = null
    private var typedCash: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        tripId = intent.getStringExtra(CashTips.EXTRA_TRIP) ?: run { finish(); return }
        val pkg = intent.getStringExtra(CashTipPrompt.EXTRA_PKG) ?: ""
        cash = if (intent.hasExtra(CashTipPrompt.EXTRA_CASH)) intent.getDoubleExtra(CashTipPrompt.EXTRA_CASH, 0.0) else null
        title = Orders.tl(this, "Ne kadar nakit aldın?", "Nə qədər nağd pul aldın?")
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), px(12)) }
        val head = TextView(this).apply { textSize = 15f }
        val cashInput = EditText(this).apply {
            hint = Orders.tl(this@TipAmountActivity, "Nakit tutarı (₼), okunamadı", "Nağd məbləğ (₼), oxunmadı")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val received = EditText(this).apply {
            hint = Orders.tl(this@TipAmountActivity, "Aldığın para (₼)", "Aldığın pul (₼)")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val tipLine = TextView(this).apply { textSize = 18f; setPadding(0, px(8), 0, px(8)) }
        val save = Button(this).apply { text = Orders.tl(this@TipAmountActivity, "Kaydet", "Yadda saxla"); isEnabled = false }
        val cancel = Button(this).apply { text = Orders.tl(this@TipAmountActivity, "Vazgeç", "İmtina") }
        head.text = Orders.flow(this).platform(pkg) + " · " + (cash?.let { Orders.tl(this, "Nakit: ${Orders.f2(it)} ₼", "Nağd: ${Orders.f2(it)} ₼") }
            ?: Orders.tl(this, "Nakit tutarı okunamadı — aşağıya yaz", "Nağd məbləğ oxunmadı — aşağıda yaz"))
        fun refresh() {
            typedCash = if (cash == null) CashTips.parseInput(cashInput.text?.toString()) else null
            val tip = CashTips.preview(received.text?.toString(), cash ?: typedCash)
            tipLine.text = when {
                tip == null -> Orders.tl(this, "Bahşiş: —", "Çaypulu: —")
                tip < 0 -> Orders.tl(this, "Fark: ${Orders.f2(tip)} ₼ (eksi)", "Fərq: ${Orders.f2(tip)} ₼ (mənfi)")
                else -> Orders.tl(this, "Bahşiş: ${Orders.f2(tip)} ₼", "Çaypulu: ${Orders.f2(tip)} ₼")
            }
            save.isEnabled = CashTips.parseInput(received.text?.toString()) != null && (cash != null || typedCash != null)
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        }
        received.addTextChangedListener(watcher); cashInput.addTextChangedListener(watcher)
        save.setOnClickListener {
            val received = CashTips.parseInput(received.text?.toString()) ?: return@setOnClickListener
            val row = runCatching { Orders.flow(this).recordCashTip(tripId, received, CashTips.VIA_WINDOW, cashOverride = typedCash) }.getOrNull()
            if (row != null) CashTipPrompt.saved(this, tripId, row)
            finish()
        }
        cancel.setOnClickListener { finish() }
        box.addView(head)
        if (cash == null) box.addView(cashInput)
        box.addView(received); box.addView(tipLine)
        box.addView(LinearLayout(this).apply { gravity = Gravity.END; addView(cancel); addView(save) })
        setContentView(box)
        refresh()
        received.requestFocus()
    }
}
