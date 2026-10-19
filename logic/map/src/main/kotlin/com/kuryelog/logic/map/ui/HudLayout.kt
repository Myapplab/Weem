package com.kuryelog.logic.map.ui

/** Harita HUD yerleşim oranları (MAP.md 6.3, 6.15; `map_hud_layout.dart`, `widgets.dart:85`). */
object HudLayout {
    /** Sol gösterge sütununun genişliği (dp): ekranın %21'i, 76–96 dp arası. */
    fun metricsWidthDp(screenDp: Double): Double = (screenDp * .21).coerceIn(76.0, 96.0)

    /** Üst panelin en çok yüksekliği: kullanılabilir yüksekliğin klavye kapalıyken %64'ü, açıkken %95'i. */
    fun topMaxHeightDp(availableDp: Double, keyboardOpen: Boolean): Double = availableDp * (if (keyboardOpen) .95 else .64)

    /** Alt panelin en çok yüksekliği: ekranın %58'i (yalnız klavye kapalıyken çizilir). */
    fun bottomMaxHeightDp(screenDp: Double): Double = screenDp * .58

    /** Üst panelin kenarları (sol, üst, sağ, alt; dp). */
    val TOP_PADDING_DP = listOf(8.0, 6.0, 8.0, 0.0)

    /** Alt panelin sol/sağ/alt kenarı (dp). */
    const val BOTTOM_PADDING_DP = 8.0

    /** Hava yuvarlağının kapalı boyu (dp). */
    const val WEATHER_COMPACT_DP = 62.0

    /** Konum düğmesinin ve alt yer düğmesinin kenarı (dp). */
    const val LOCATE_BUTTON_DP = 64.0
}
