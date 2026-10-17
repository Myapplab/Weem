package com.kuryelog.logic.link

import com.kuryelog.logic.core.CoreModule

/** `:link` — 2 telefon modu: zarf, kuyruk, eşleştirme. İskelet (A1.1); kod ilgili aşamada gelir. */
object LinkModule {
    const val NAME = "link"

    /** Bu modülün gördüğü modüller (gradle/module-graph.properties ile aynı). */
    val dependsOn: List<String> = listOf(CoreModule.NAME)
}
