package com.kuryelog.logic.map.weather

import com.kuryelog.logic.core.Bilingual

/**
 * Hava simgesi türü ve açıklaması (`weather_widget.dart:13–33`, MAP.md 10.9); sırayla: rüzgâr ≥ 30 ya da hamle ≥ 45 km/s → [WIND]; kod yok → yağış > 0 [RAIN] değilse [CLOUD];
 * ≥ 95 [THUNDER]; 71–77, 85, 86 [SNOW]; ≥ 51 [RAIN]; 45, 48 [FOG]; ≥ 2 [CLOUD]; gece [NIGHT]; diğer [SUN]. [top]/[bottom] degrade (ARGB).
 */
enum class WeatherKind(override val tr: String, override val az: String, val top: Long, val bottom: Long) : Bilingual {
    WIND("Rüzgarlı", "Küləkli", 0xFFDDF0EA, 0xFFA9D3C6),
    THUNDER("Gök gürültülü", "Göy gurultulu", 0xFF5A6478, 0xFF353C4C),
    SNOW("Karlı", "Qarlı", 0xFFEFF6FF, 0xFFC9DDF2),
    RAIN("Yağmurlu", "Yağışlı", 0xFFB9D3EE, 0xFF7FA6D1),
    FOG("Sisli", "Dumanlı", 0xFFE3EAF2, 0xFFB9C7D6),
    CLOUD("Bulutlu", "Buludlu", 0xFFE3EAF2, 0xFFB9C7D6),
    NIGHT("Açık gece", "Açıq gecə", 0xFF2B3A67, 0xFF16203F),
    SUN("Güneşli", "Günəşli", 0xFFFFE7A3, 0xFFFFC56B),
    ;

    /** Yazı rengi: degradenin son renginin parlaklığı > 0,35 ise koyu `#17283A`, değilse beyaz. */
    val onColor: Long get() = if (luminance(bottom) > .35) 0xFF17283A else 0xFFFFFFFF

    companion object {
        /** Flutter `Color.computeLuminance` (sRGB → doğrusal). */
        internal fun luminance(argb: Long): Double {
            fun lin(c: Int): Double {
                val s = c / 255.0
                return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * lin(((argb shr 16) and 0xFF).toInt()) + 0.7152 * lin(((argb shr 8) and 0xFF).toInt()) + 0.0722 * lin((argb and 0xFF).toInt())
        }
    }
}

object WeatherIcons {
    fun kind(h: WeatherHour): WeatherKind {
        if ((h.wind ?: 0.0) >= 30 || (h.gust ?: 0.0) >= 45) return WeatherKind.WIND
        val code = h.code ?: return if ((h.rainMm ?: 0.0) > 0) WeatherKind.RAIN else WeatherKind.CLOUD
        return when {
            code >= 95 -> WeatherKind.THUNDER
            code in 71..77 || code == 85 || code == 86 -> WeatherKind.SNOW
            code >= 51 -> WeatherKind.RAIN
            code == 45 || code == 48 -> WeatherKind.FOG
            code >= 2 -> WeatherKind.CLOUD
            h.isDay == false -> WeatherKind.NIGHT
            else -> WeatherKind.SUN
        }
    }

    /** S42 simge: [kind] ile aynı karar sırası; bulutlu WMO 2 parçalı (gündüz/gece), yağmurda 51–57 çisenti. */
    fun glyph(h: WeatherHour): WeatherGlyph = when (kind(h)) {
        WeatherKind.WIND -> WeatherGlyph.WIND
        WeatherKind.THUNDER -> WeatherGlyph.THUNDER
        WeatherKind.SNOW -> WeatherGlyph.SNOW
        WeatherKind.RAIN -> if (h.code in 51..57) WeatherGlyph.DRIZZLE else WeatherGlyph.RAIN
        WeatherKind.FOG -> WeatherGlyph.FOG
        WeatherKind.CLOUD -> when {
            h.code != 2 -> WeatherGlyph.CLOUD
            h.isDay == false -> WeatherGlyph.PARTLY_NIGHT
            else -> WeatherGlyph.PARTLY_DAY
        }
        WeatherKind.NIGHT -> WeatherGlyph.MOON
        WeatherKind.SUN -> WeatherGlyph.SUN
    }

    /** Yuvarlağın 3. satırı: yağış ihtimali ≥ %10 ise damla + `%`, değilse rüzgâr; ikisi de yoksa null. */
    fun compactExtra(h: WeatherHour): CompactExtra? {
        val rain = h.rainChance
        if (rain != null && rain >= 10) return CompactExtra(true, "${Math.round(rain)}%")
        val wind = h.wind ?: return null
        return CompactExtra(false, "${Math.round(wind)}")
    }
}

/** [rain] true: damla simgesi, false: rüzgâr simgesi. */
data class CompactExtra(val rain: Boolean, val text: String)
