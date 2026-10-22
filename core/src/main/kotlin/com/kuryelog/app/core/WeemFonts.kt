package com.kuryelog.app.core

import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

/**
 * Canvas çizimleri ve kurye uygulamalarının üstündeki pencereler için yazı tipi (A10.4d K2: Inter, `docs/TASARIM.md` 5). Yazı tipi
 * dosyaları `:app`'te; uygulama açılışta [install] eder. Kurulmamışsa (test, erken servis) sistem yazısı.
 */
object WeemFonts {
    const val REGULAR = 400
    const val MEDIUM = 500
    const val SEMIBOLD = 600
    const val BOLD = 700
    const val EXTRABOLD = 800

    @Volatile private var loader: ((Int) -> Typeface?)? = null
    private val cache = ConcurrentHashMap<Int, Typeface>()

    fun install(load: (weight: Int) -> Typeface?) {
        loader = load
        cache.clear()
    }

    /** [weight] 400–800; dosya okunamazsa sistem yazısı aynı kalınlıkta. */
    fun get(weight: Int): Typeface = cache.getOrPut(weight) {
        runCatching { loader?.invoke(weight) }.getOrNull() ?: Typeface.create(Typeface.DEFAULT, weight, false)
    }
}
