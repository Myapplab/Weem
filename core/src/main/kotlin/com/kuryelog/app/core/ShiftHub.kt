package com.kuryelog.app.core

import android.annotation.SuppressLint
import android.content.Context
import com.kuryelog.logic.core.ShiftListeners
import com.kuryelog.logic.core.ShiftPort
import com.kuryelog.logic.core.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android'deki [ShiftPort] (plan 4.3, CORE.md 1.11): durum `kuryelog` tercihlerinden kurulur, vardiya servisi her değişimde yayınlar;
 * modüller vardiya işlerini [listeners]'a kendi aşamalarında takar (Aşama 2'de boş). `WeemApplication` açılışta [attach] eder.
 */
// Yalnız uygulama bağlamı tutulur (Activity değil): sızıntı yok.
@SuppressLint("StaticFieldLeak")
object ShiftHub : ShiftPort {
    @Volatile private var app: Context? = null
    private val _state = MutableStateFlow(ShiftState(active = false))
    override val state: StateFlow<ShiftState> = _state.asStateFlow()

    /** Modüllerin vardiya işleri; hataları `errors.jsonl`'e (kaynak `shift_start/<modül>` gibi). */
    val listeners = ShiftListeners { app?.let(Store::sink) ?: com.kuryelog.logic.core.FailureSink { _, _ -> } }

    fun attach(ctx: Context) {
        val a = ctx.applicationContext
        app = a
        Store.guarded(a, "shift_state") { _state.value = Shift.state(a) }
    }

    internal fun published(s: ShiftState) { _state.value = s }

    override fun start(reason: String) { app?.let(Shift::start) }
    override fun stop(reason: String) { app?.let(Shift::stop) }
}
