package com.kuryelog.logic.map.ui

/** Kamera kipi (MAP.md 6.15): serbest, takip + kuzey yukarı, takip + yön yukarı. */
enum class CameraMode { FREE, FOLLOW_NORTH, FOLLOW_HEADING }

/** Konum düğmesinin eylemi: serbestken konuma dön, takipte yön ↔ kuzey arasında geç. */
enum class LocateAction { RECENTER, HEADING_UP, NORTH_UP }

/** Konum düğmesi kuralları (`locate_button.dart`; MAP.md 6.15). İskelet (A6.2b): gövdeler A6.5'te. */
object LocateButtonRules {
    fun cameraModeOf(follow: Boolean, headingUp: Boolean): CameraMode = TODO("A6.5")

    fun locateActionFor(mode: CameraMode): LocateAction = TODO("A6.5")
}
