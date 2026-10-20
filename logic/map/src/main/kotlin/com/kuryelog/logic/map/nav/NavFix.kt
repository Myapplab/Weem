package com.kuryelog.logic.map.nav

/**
 * Navigasyonun gördüğü konum okuması (Dart `Position`; MAP.md 7.6): [atMs] fix zamanı (epoch ms), [accuracyM] doğruluk (m),
 * [speedMps] hız ve [headingDeg] yön (0–360, bilinmiyorsa null).
 */
data class NavFix(
    val atMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Double = 5.0,
    val speedMps: Double? = null,
    val headingDeg: Double? = null,
)
