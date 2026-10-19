package com.kuryelog.logic.map.ui

/** Harita HUD yerleşim oranları (MAP.md 6.15): sol gösterge sütununun genişliği ekranla orantılı (0,64/0,95/0,58 oranları, kenarlar). İskelet (A6.2b): gövde A6.5'te. */
object HudLayout {
    /** Sol gösterge sütununun genişliği (dp), ekran genişliği [screenDp] için. */
    fun metricsWidthDp(screenDp: Double): Double = TODO("A6.5")

    /** Hava yuvarlağının kapalı boyu (dp). */
    const val WEATHER_COMPACT_DP = 62.0

    /** Konum düğmesinin ve alt yer düğmesinin kenarı (dp). */
    const val LOCATE_BUTTON_DP = 64.0
}
