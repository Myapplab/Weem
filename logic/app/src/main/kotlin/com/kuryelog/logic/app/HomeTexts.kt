package com.kuryelog.logic.app

import com.kuryelog.logic.core.HomeFixes
import com.kuryelog.logic.core.HomeShift
import com.kuryelog.logic.core.Lang
import java.util.Locale
import kotlin.math.roundToInt

/** Ev ekranının kurala bağlı metinleri: sayılar kuralın sabitlerinden (RS `HomeShiftE6Test.watcherAndSettingsFollowTheRule`). */
object HomeTexts {
    fun rule(lang: Lang): String = Text.HOME_RULE.get(lang).format(
        HomeShift.START_RADIUS_M.toInt(), (HomeShift.START_SPEED_MPS * 3.6).roundToInt(), HomeShift.START_MAX_ACCURACY_M.toInt(),
        HomeShift.RADIUS_M.toInt(), (HomeShift.STILL_MS / 1000).toInt(),
    )
    fun measuring(lang: Lang): String = Text.HOME_MEASURING.get(lang).format((HomeFixes.COLLECT_MS / 1000).toInt())
    fun accuracy(lang: Lang, m: Double): String = Text.HOME_ACCURACY.get(lang).format(String.format(Locale.US, "%.1f", m))
    fun warning(lang: Lang): String = Text.HOME_WARN.get(lang).format(HomeFixes.WARN_ACCURACY_M.toInt())
    /** Koordinat `%.6f, %.6f` (Locale.US), RS ev kartı. */
    fun coordinates(lat: Double, lon: Double): String = String.format(Locale.US, "%.6f, %.6f", lat, lon)
}
