package com.kuryelog.logic.map.calibration

/** Kalibrasyon aşaması (YolGo `phase` dizgeleri aynen; yüzen panel ve ekran bunu okur). */
enum class CalibrationPhase(val key: String) { IDLE("idle"), COUNTDOWN("countdown"), RECORDING("recording"), SAVING("saving"), DONE("done"), ERROR("error");
    val active get() = this == COUNTDOWN || this == RECORDING || this == SAVING
}

/**
 * Yüzen kalibrasyon panelinin ve ekranın gördüğü anlık durum (YolGo `RoadCalibrationService.state`). [visible] panel açık mı; [remaining] geri sayım ya da
 * kalan süre (sn); [waitingOffset] 3-2-1 yol RMS ofseti (GPS + ≥2 sn duruş) hazır olana kadar bekliyor (en çok 15 sn, E10c).
 */
data class CalibrationState(
    val phase: CalibrationPhase = CalibrationPhase.IDLE, val visible: Boolean = false, val quality: String? = null, val seconds: Int = 0,
    val qualityLevel: Int? = null, val mode: String = "fixed", val profile: String = "g3-default", val remaining: Int = 3,
    val waitingOffset: Boolean = false, val message: String = "",
) {
    val ready get() = qualityLevel != null && seconds > 0
}
