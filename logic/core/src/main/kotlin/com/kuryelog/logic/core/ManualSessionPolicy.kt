package com.kuryelog.logic.core

/** Vardiya servisi kararları (CORE.md 1.1, 1.2, 1.5; RS `RecordingPolicy.kt`). */
object ManualSessionPolicy {
    /** Vardiyayı yalnız açık istek (elle ya da geçerli evden çıkış) açar; yapışkan yeniden başlatma yalnız açık vardiyayı sürdürür. İçe aktarma sürerken hiçbiri. */
    fun mayStart(explicitManual: Boolean, manualSessionActive: Boolean, importing: Boolean): Boolean =
        !importing && (explicitManual || manualSessionActive)

    /** Ekran kaydı ancak vardiya açık ve içe aktarma yokken. */
    fun canCapture(shiftActive: Boolean, manualSession: Boolean, importing: Boolean): Boolean = shiftActive && manualSession && !importing

    /** H4: reddedilen ön plan başlatması ne demek. Yalnız çalışan vardiyanın sistem yeniden başlatması (boş intent) vardiyayı korur. */
    enum class StartFailure { ABORT, INTERRUPTED }

    fun onStartFailure(stickyRestart: Boolean, shiftOn: Boolean): StartFailure =
        if (stickyRestart && shiftOn) StartFailure.INTERRUPTED else StartFailure.ABORT

    /** H4: çalışan vardiya kesildi (`shiftInterruptedAt`) ve sonra sürmedi. */
    fun interrupted(shiftOn: Boolean, interruptedAt: Long, shiftStart: Long): Boolean =
        shiftOn && interruptedAt > 0 && interruptedAt >= shiftStart
}
