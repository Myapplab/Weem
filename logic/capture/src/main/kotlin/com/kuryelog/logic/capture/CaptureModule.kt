package com.kuryelog.logic.capture

import com.kuryelog.logic.core.CoreModule

/** `:capture` — Wolt/Bolt ekran ve bildirim okuma, ayrıştırma (ağsız). İskelet (A1.1); kod ilgili aşamada gelir. */
object CaptureModule {
    const val NAME = "capture"

    /** Bu modülün gördüğü modüller (gradle/module-graph.properties ile aynı). */
    val dependsOn: List<String> = listOf(CoreModule.NAME)
}
