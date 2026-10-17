package com.kuryelog.logic.capture

// İskelet (A3.2): imzalar Rawstats'tan (NavDistance.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.4'te.
object NavDistance {
    val packages: Set<String> = setOf("com.google.android.apps.maps", "com.waze", "ru.yandex.yandexnavi", "ru.yandex.yandexmaps",
        "ru.dublgis.dgismobile", "com.here.app.maps")
    fun summary(lines: List<String>): String? = TODO("A3.4")
    fun metres(summary: String?): Double? = TODO("A3.4")
}
