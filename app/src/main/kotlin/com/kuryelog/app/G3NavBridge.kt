package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.map.NavRuntime
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.logic.scooter.G3Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.kuryelog.logic.map.nav.RouteProgress

/** S45 (N10): WEEM rotasının ölçümü → G3 ekranı yönlendirmesi; `:map` ile `:scooter` birbirini tanımaz, dikiş burada. */
object G3NavBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(ctx: Context) {
        val nav = NavRuntime.get(ctx).controller
        scope.launch {
            // İyi GPS yokken (bina içi) canlı ölçüm null kalır; ekran o zaman planın kendisini gösterir.
            combine(nav.progress, nav.state) { m, s -> RouteProgress.guide(m, s.plan) }.collect { m ->
                Scooter.navGuide = m?.let { G3Display.guide(it.remainingM, it.remainingS, it.nextTurnM, it.nextTurn?.angle, it.nextTurn?.name ?: "") }
            }
        }
    }
}
