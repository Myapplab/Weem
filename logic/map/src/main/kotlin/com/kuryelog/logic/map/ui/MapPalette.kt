package com.kuryelog.logic.map.ui

/**
 * Harita çizim renkleri (YolGo `theme.dart:30-68`; MAP.md 6.8): gece / gündüz paletleri, ARGB. HUD panelleri ve metinler `:app` paletindedir (`logic/app` `Palette`).
 */
class MapPalette private constructor(
    val background: Int,
    val panel: Int,
    val surface: Int,
    val surfaceHigh: Int,
    val stroke: Int,
    /** Düz yol ve iniş. */
    val blue: Int,
    /** Hafif yokuş. */
    val orange: Int,
    /** Dik yokuş. */
    val red: Int,
    /** Onaylı / iyi. */
    val green: Int,
    /** Duraklar. */
    val pink: Int,
    /** Eğimli sokak. */
    val slope: Int,
    val food: Int,
    val walk: Int,
    val text: Int,
    val textLo: Int,
    val shadow: Int,
    val bikeway: Int,
) {
    /** Eğim rengi: > %8 kırmızı, > %3 turuncu, aksi mavi (iniş dahil). [grade] oran (0,05 = %5). */
    fun forGrade(grade: Double): Int = when {
        grade > SLOPE_STEEP -> red
        grade > SLOPE_MILD -> orange
        else -> blue
    }

    companion object {
        const val SLOPE_MILD = 0.03
        const val SLOPE_STEEP = 0.08
        /** Öteki modun planı: soluk gri 4 px. */
        const val OTHER_PLAN = 0x66A0A8B8
        const val OTHER_PLAN_WIDTH = 4.0

        val NIGHT = MapPalette(
            background = 0xFF0A101B.toInt(), panel = 0xF20F1725.toInt(), surface = 0xFF101827.toInt(), surfaceHigh = 0xFF1B2638.toInt(),
            stroke = 0xFF2B384C.toInt(), blue = 0xFF8BB5FF.toInt(), orange = 0xFFFF9F1C.toInt(), red = 0xFFFF2D55.toInt(), green = 0xFF8ACDB8.toInt(),
            pink = 0xFFFF7AC6.toInt(), slope = 0xFFB26BFF.toInt(), food = 0xFFFF8A3D.toInt(), walk = 0xFFE8F7FF.toInt(), text = 0xFFE8F7FF.toInt(),
            textLo = 0xFFA5B0C1.toInt(), shadow = 0x99000000.toInt(), bikeway = 0xFF3DDCE8.toInt(),
        )
        val DAY = MapPalette(
            background = 0xFFF3F5F8.toInt(), panel = 0xF2FFFFFF.toInt(), surface = 0xFFFFFFFF.toInt(), surfaceHigh = 0xFFE6EAF0.toInt(),
            stroke = 0xFFCBD2DB.toInt(), blue = 0xFF1765D1.toInt(), orange = 0xFFD45D00.toInt(), red = 0xFFD7002E.toInt(), green = 0xFF137D65.toInt(),
            pink = 0xFFC0008A.toInt(), slope = 0xFF7B2FD0.toInt(), food = 0xFFE2580B.toInt(), walk = 0xFF16222E.toInt(), text = 0xFF0B1218.toInt(),
            textLo = 0xFF55616D.toInt(), shadow = 0x33000000, bikeway = 0xFF00838F.toInt(),
        )

        fun of(dark: Boolean): MapPalette = if (dark) NIGHT else DAY
    }
}
