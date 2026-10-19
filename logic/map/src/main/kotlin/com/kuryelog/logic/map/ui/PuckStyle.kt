package com.kuryelog.logic.map.ui

/** Konum imleci ölçüleri (dp, MAP.md 6.15; `puck_style.dart`): iki harita yüzeyi aynı değerleri kullanır. İskelet (A6.2b): değerler A6.5'te. */
object PuckStyle {
    val RING_RADIUS: Double get() = TODO("A6.5")
    val DOT_RADIUS: Double get() = TODO("A6.5")
    val SHADOW_RADIUS: Double get() = TODO("A6.5")
    val BEAM_RADIUS: Double get() = TODO("A6.5")
    val CANVAS: Double get() = TODO("A6.5")
    val ARROW_SIZE: Double get() = TODO("A6.5")

    /** Ok yalnız rota açık ve hareket yönü belliyken ([course] null değil). */
    fun showsArrow(route: Boolean, course: Double?): Boolean = TODO("A6.5")

    /** Ucu kuzeyde (yukarı) ok: merkez ([cx], [cy]), [size] yükseklik; köşe noktaları (x, y), y aşağı büyür. */
    fun arrowPoints(cx: Double, cy: Double, size: Double): List<Pair<Double, Double>> = TODO("A6.5")
}
