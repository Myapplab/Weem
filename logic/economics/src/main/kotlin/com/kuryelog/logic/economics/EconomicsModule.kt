package com.kuryelog.logic.economics

import com.kuryelog.logic.core.CoreModule
import com.kuryelog.logic.orders.OrdersModule

/** `:economics` — ekonomi v2, teklif puanı, gün sonu, net kazanç, hedef. İskelet (A1.1); kod ilgili aşamada gelir. */
object EconomicsModule {
    const val NAME = "economics"

    /** Bu modülün gördüğü modüller (gradle/module-graph.properties ile aynı). */
    val dependsOn: List<String> = listOf(CoreModule.NAME, OrdersModule.NAME)
}
