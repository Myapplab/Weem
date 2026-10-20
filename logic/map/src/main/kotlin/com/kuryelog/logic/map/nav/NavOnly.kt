package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Clock

/** Yalnız-navigasyon kipi durumu (MAP.md 7.12): [on] açık, [sinceMs] açıldığı an. */
data class NavOnlyState(val on: Boolean = false, val sinceMs: Long? = null)

/** `nav_only.dart`: gün biterken açık rota kesilmez; varışta, rota silinince, yeni günde ya da 90 dk sonra kapanır. */
object NavOnly {
    const val MAX_MS = 90 * 60_000L

    fun next(s: NavOnlyState, dayActive: Boolean, hasRoute: Boolean, nowMs: Long, dayJustEnded: Boolean = false): NavOnlyState {
        if (dayActive || !hasRoute) return NavOnlyState()
        if (s.on) return if (nowMs - (s.sinceMs ?: nowMs) >= MAX_MS) NavOnlyState() else s
        return if (dayJustEnded) NavOnlyState(true, nowMs) else NavOnlyState()
    }
}

/** Dart `navOnlyProvider`: gün ve rota değişimini izler, geçişi saf kuraldan geçirir ve konum kaynağına bildirir. */
class NavOnlyController(private val clock: Clock, private val setLocationNavOnly: (Boolean) -> Unit) {
    var state = NavOnlyState()
        private set
    val on: Boolean get() = state.on
    private var dayActive: Boolean? = null

    /** Gün ya da rota değişince ve zamanlayıcıyla (90 dk sınırı için dakikada bir yeterli) çağrılır. */
    fun update(dayActive: Boolean, hasRoute: Boolean) {
        val ended = this.dayActive == true && !dayActive
        this.dayActive = dayActive
        val next = NavOnly.next(state, dayActive, hasRoute, clock.wallMs(), ended)
        if (next == state) return
        val changed = next.on != state.on
        state = next
        if (changed) setLocationNavOnly(next.on)
    }
}
