package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Clock

/** Yalnız-navigasyon kipi durumu (MAP.md 7.12): [on] açık, [sinceMs] açıldığı an. */
data class NavOnlyState(val on: Boolean = false, val sinceMs: Long? = null)

/** `nav_only.dart`: gün biterken açık rota kesilmez; varışta, rota silinince, yeni günde ya da 90 dk sonra kapanır. İskelet (A6.2b). */
object NavOnly {
    const val MAX_MS = 90 * 60_000L

    fun next(s: NavOnlyState, dayActive: Boolean, hasRoute: Boolean, nowMs: Long, dayJustEnded: Boolean = false): NavOnlyState = TODO("A6.6")
}

/** Dart `navOnlyProvider`: gün ve rota değişimini izler, geçişi saf kuraldan geçirir ve konum kaynağına bildirir. */
class NavOnlyController(private val clock: Clock, private val setLocationNavOnly: (Boolean) -> Unit) {
    val on: Boolean get() = TODO("A6.6")

    fun update(dayActive: Boolean, hasRoute: Boolean): Unit = TODO("A6.6")
}
