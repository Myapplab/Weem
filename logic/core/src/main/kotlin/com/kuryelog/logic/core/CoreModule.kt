package com.kuryelog.logic.core

/** `:core` — ortak tipler, mesaj sözleşmeleri, depolama, vardiya/gün/ev kuralı, bildirim tablosu. İskelet (A1.1); kod ilgili aşamada gelir. */
object CoreModule {
    const val NAME = "core"

    /** Bu modülün gördüğü modüller (gradle/module-graph.properties ile aynı). */
    val dependsOn: List<String> = listOf()
}
