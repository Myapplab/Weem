package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.CaptureModule
import com.kuryelog.logic.core.CoreModule

/** `:orders` — teklif ve sipariş durum makinesi, para, gün sayfaları. İskelet (A1.1); kod ilgili aşamada gelir. */
object OrdersModule {
    const val NAME = "orders"

    /** Bu modülün gördüğü modüller (gradle/module-graph.properties ile aynı). */
    val dependsOn: List<String> = listOf(CoreModule.NAME, CaptureModule.NAME)
}
