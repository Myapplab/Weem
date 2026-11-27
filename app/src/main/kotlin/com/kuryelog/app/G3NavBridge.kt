package com.kuryelog.app

import android.content.Context
import com.kuryelog.app.map.NavRuntime
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.logic.scooter.G3Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** S45 (N10): WEEM rotasının ölçümü → G3 ekranı yönlendirmesi; `:map` ile `:scooter` birbirini tanımaz, dikiş burada. */
object G3NavBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(ctx: Context) {
        val progress = NavRuntime.get(ctx).controller.progress
        scope.launch {
            progress.collect { m ->
                Scooter.navGuide = m?.let { G3Display.guide(it.remainingM, it.remainingS, it.nextTurnM, it.nextTurn?.angle, it.nextTurn?.name ?: "") }
            }
        }
    }
}
