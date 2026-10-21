package com.kuryelog.logic.map.calibration

import kotlin.math.ceil
import kotlin.math.max

/** E10c: Başlat'ta sensör/GPS açılır ve faz "countdown" olur, ama 3-2-1 deadline'ı
 * yol RMS ofseti (taze GPS + >=2 sn duruş) hazır olana kadar kurulmaz. 15 sn içinde
 * hazır olmazsa 3-2-1 yine başlar; yol RMS'in mevcut ret nedeni geçerli kalır. Saf JVM. */
const val CALIBRATION_COUNTDOWN_MS = 3_000L
const val CALIBRATION_OFFSET_WAIT_MS = 15_000L

/** null: ofset hâlâ bekleniyor (waitingOffset); değilse 3-2-1'in bittiği an (elapsedRealtime ms). */
fun calibrationCountdownDeadline(nowMs: Long, waitStartedMs: Long, offsetReady: Boolean): Long? =
    if (offsetReady || nowMs - waitStartedMs >= CALIBRATION_OFFSET_WAIT_MS) nowMs + CALIBRATION_COUNTDOWN_MS else null

/** Gösterilen saniye. Beklerken 3-2-1 başlamadığı için tam süre (3); 0 ya da eksi görünmez. */
fun calibrationCountdownRemaining(nowMs: Long, deadlineMs: Long?): Int =
    if (deadlineMs == null) (CALIBRATION_COUNTDOWN_MS / 1000).toInt()
    else ceil(max(0L, deadlineMs - nowMs) / 1000.0).toInt()
