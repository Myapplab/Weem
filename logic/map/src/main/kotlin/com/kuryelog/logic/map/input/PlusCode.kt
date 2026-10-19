package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.geo.GeoPoint

/** `lib/input/plus_code.dart` (MAP.md 8.5): açık konum kodu (Plus Code), saf ve ağsız. İskelet (A6.2b): gövdeler A6.6'da. */
object PlusCode {
    val pattern: Regex get() = TODO("A6.6")

    /** Tam kod → hücre merkezi; geçersizse null. */
    fun decode(code: String): GeoPoint? = TODO("A6.6")

    /** Kısa kodu [ref] konumuna en yakın tam koda tamamlar (`+` yoksa null). */
    fun recover(code: String, ref: GeoPoint): GeoPoint? = TODO("A6.6")

    /** "2345+67" gibi sayıları eler: en az bir harf `[CFGHJMPQRVWX]`. */
    fun looksReal(code: String): Boolean = TODO("A6.6")
}
