package com.kuryelog.logic.remote

import com.kuryelog.logic.core.CoreModule
import com.kuryelog.logic.link.LinkModule

/** `:remote` — uzaktan kullanım oturumu. İskelet (A1.1); kod ilgili aşamada gelir. */
object RemoteModule {
    const val NAME = "remote"

    /** Bu modülün gördüğü modüller (gradle/module-graph.properties ile aynı). */
    val dependsOn: List<String> = listOf(CoreModule.NAME, LinkModule.NAME)
}
