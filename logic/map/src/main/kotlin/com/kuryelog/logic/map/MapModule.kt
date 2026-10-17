package com.kuryelog.logic.map

import com.kuryelog.logic.core.CoreModule

/** `:map` — harita verisi, rota motoru, navigasyon, yol kalitesi. İskelet (A1.1); kod ilgili aşamada gelir. */
object MapModule {
    const val NAME = "map"

    /** Bu modülün gördüğü modüller (gradle/module-graph.properties ile aynı). */
    val dependsOn: List<String> = listOf(CoreModule.NAME)
}
