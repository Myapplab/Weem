package com.kuryelog.logic.map.surface

/** Yüzey etiketi; Ses− kötü nokta etiketi `ROUGH` (`MAP.md` 9.12, SORU-Q1). */
enum class SurfaceLabel { UNKNOWN, SMOOTH, ROUGH, POTHOLE, RAISED_ASPHALT, DIRT, GRAVEL, OBSTACLE }

enum class Passability { UNKNOWN, PASSABLE, CLOSED }

/** Yüzey işareti: Ses− kötü nokta (`source = "user"`). `at` UTC epoch ms. */
data class SurfaceMark(
    val id: String,
    val way: Long,
    val lat: Double,
    val lon: Double,
    val at: Long,
    val label: SurfaceLabel = SurfaceLabel.UNKNOWN,
    val access: Passability = Passability.UNKNOWN,
    val source: String = "user",
    val removed: Boolean = false,
)
