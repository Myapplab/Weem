package com.kuryelog.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.kuryelog.logic.core.LogImporter
import java.io.InputStream

/**
 * ZIP yedeğini arka planda içe aktarır (CORE.md 5.3; RS `MainActivity.kt:1477-1490`). Vardiya açıkken başlamaz; sürerken
 * [Store.importing] (vardiya başlamaz, ekran kaydı durur). Sonuç ana iş parçacığında. A3: ardından `:orders` istatistiği yeniden yükler.
 */
object LogImport {
    sealed interface Outcome {
        data class Done(val files: Int) : Outcome
        data object ShiftOn : Outcome
        data object Busy : Outcome
        data class Failed(val message: String?) : Outcome
    }

    fun start(ctx: Context, open: () -> InputStream?, done: (Outcome) -> Unit) {
        val app = ctx.applicationContext
        if (Store.shiftOn(app)) { done(Outcome.ShiftOn); return }
        if (Store.importing) { done(Outcome.Busy); return }
        Store.importing = true
        val main = Handler(Looper.getMainLooper())
        Thread({
            val outcome = try {
                val names = open()?.use { LogImporter.restore(it, app.filesDir, Store.logsRoot(app), app.cacheDir) } ?: throw IllegalStateException("open")
                Outcome.Done(names.size)
            } catch (e: Throwable) {
                // RS 16 Eki: bellek hatası da (iki büyük gün dosyası bellekte birleşir); bu iş parçacığında yakalanmazsa uygulama kapanır.
                runCatching { Store.failure(app, "import", e) }
                Outcome.Failed(e.message)
            }
            main.post { Store.importing = false; done(outcome) }
        }, "weem-import").start()
    }
}
