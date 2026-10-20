package com.kuryelog.logic.map.surface

/** Y13 yüzey işareti: WEEM 1.0 alt kümesi (Ses− kötü nokta; MAP.md 9.12 `surface/`). */
enum class SurfaceLabel { UNKNOWN, SMOOTH, ROUGH, POTHOLE, PATCH, RAISED_ASPHALT, DIRT, GRAVEL, HUMP, OBSTACLE }

enum class Passability { UNKNOWN, PASSABLE, CLOSED }

data class SurfaceMark(
    val id: String,
    val way: Long,
    val lat: Double,
    val lon: Double,
    /** Epoch ms. */
    val at: Long,
    val label: SurfaceLabel = SurfaceLabel.UNKNOWN,
    val access: Passability = Passability.UNKNOWN,
    val source: String = "user",
    val removed: Boolean = false,
)
