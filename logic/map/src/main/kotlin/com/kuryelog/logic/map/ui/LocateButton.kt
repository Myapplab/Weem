package com.kuryelog.logic.map.ui

/** Kamera kipi (MAP.md 6.5): serbest, takip + kuzey yukarı, takip + yön yukarı. */
enum class CameraMode { FREE, FOLLOW_NORTH, FOLLOW_HEADING }

/** Konum düğmesinin eylemi: serbestken konuma dön, takipte yön ↔ kuzey arasında geç. */
enum class LocateAction { RECENTER, HEADING_UP, NORTH_UP }

/** Konum düğmesi kuralları (`locate_button.dart`; MAP.md 6.5). Başlangıç kipi [CameraMode.FOLLOW_HEADING]. */
object LocateButtonRules {
    val INITIAL = CameraMode.FOLLOW_HEADING

    fun cameraModeOf(follow: Boolean, headingUp: Boolean): CameraMode = when {
        !follow -> CameraMode.FREE
        headingUp -> CameraMode.FOLLOW_HEADING
        else -> CameraMode.FOLLOW_NORTH
    }

    fun locateActionFor(mode: CameraMode): LocateAction = when (mode) {
        CameraMode.FREE -> LocateAction.RECENTER
        CameraMode.FOLLOW_NORTH -> LocateAction.HEADING_UP
        CameraMode.FOLLOW_HEADING -> LocateAction.NORTH_UP
    }
}
