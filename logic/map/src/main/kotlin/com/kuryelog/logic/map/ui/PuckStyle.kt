package com.kuryelog.logic.map.ui

/**
 * Konum imleci ölçüleri (dp, MAP.md 6.5; `puck_style.dart`): iki harita yüzeyi aynı değerleri kullanır. Ekim planı madde 5: Google Maps gibi küçük
 * (beyaz kenar çapı ~20 dp).
 */
object PuckStyle {
    const val RING_RADIUS = 10.0
    const val DOT_RADIUS = 7.5
    const val SHADOW_RADIUS = 11.0
    const val SHADOW_BLUR = 4.0
    const val BEAM_RADIUS = 28.0
    const val BEAM_HALF_ANGLE_DEG = 30.0
    /** Koninin merkezdeki saydamlığı (dışa doğru 0'a iner; iki yüzeyde aynı, MAP.md 6.16 (3)). */
    const val BEAM_ALPHA = .55
    const val CANVAS = 60.0
    const val ARROW_SIZE = 22.0
    const val ARROW_STROKE = 3.0
    const val ARROW_SHADOW_BLUR = 3.0

    const val DOT_COLOR = 0xFF1A73E8.toInt()
    const val BEAM_COLOR = 0xFF4285F4.toInt()
    const val SHADOW_COLOR = 0x40000000
    const val ACCURACY_FILL = 0x1F4285F4
    const val ACCURACY_STROKE = 0x334285F4

    /** Doğruluk dairesi yalnız 8 < doğruluk < 80 m iken. */
    fun showsAccuracy(accuracyM: Double?): Boolean = accuracyM != null && accuracyM > 8 && accuracyM < 80

    /** Ok yalnız rota açık ve hareket yönü belliyken ([course] null değil). */
    fun showsArrow(route: Boolean, course: Double?): Boolean = route && course != null

    /** Ucu kuzeyde (yukarı) ok: merkez ([cx], [cy]), [size] yükseklik; köşe noktaları (x, y), y aşağı büyür. */
    fun arrowPoints(cx: Double, cy: Double, size: Double): List<Pair<Double, Double>> = listOf(
        cx to cy - size / 2,
        cx + size * .4 to cy + size / 2,
        cx to cy + size * .22,
        cx - size * .4 to cy + size / 2,
    )
}
