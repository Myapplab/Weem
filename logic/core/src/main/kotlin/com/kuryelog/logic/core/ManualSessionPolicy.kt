package com.kuryelog.logic.core

/** Vardiya servisi kararları (CORE.md 1.5, H4). */
object ManualSessionPolicy {
    /** H4: reddedilen ön plan başlatması ne demek. Yalnız çalışan vardiyanın sistem yeniden başlatması (boş intent) vardiyayı korur. */
    enum class StartFailure { ABORT, INTERRUPTED }

    fun onStartFailure(stickyRestart: Boolean, shiftOn: Boolean): StartFailure =
        if (stickyRestart && shiftOn) StartFailure.INTERRUPTED else StartFailure.ABORT

    /** H4: çalışan vardiya kesildi (`shiftInterruptedAt`) ve sonra sürmedi. */
    fun interrupted(shiftOn: Boolean, interruptedAt: Long, shiftStart: Long): Boolean =
        shiftOn && interruptedAt > 0 && interruptedAt >= shiftStart
}
