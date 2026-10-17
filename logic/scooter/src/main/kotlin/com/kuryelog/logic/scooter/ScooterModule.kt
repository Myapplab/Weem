package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.CoreModule

/** `:scooter` — Ninebot protokolü, canlı panel, kayıt, enerji. İskelet (A1.1); kod ilgili aşamada gelir. */
object ScooterModule {
    const val NAME = "scooter"

    /** Bu modülün gördüğü modüller (gradle/module-graph.properties ile aynı). */
    val dependsOn: List<String> = listOf(CoreModule.NAME)
}
