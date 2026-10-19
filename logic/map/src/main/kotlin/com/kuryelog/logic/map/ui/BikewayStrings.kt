package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Bilingual
import java.util.Locale

/** Yollarım → Bisiklet sekmesi ve uzun basış satırı metinleri (MAP.md 5.1; YolGo `bikeway_layer.dart`). `:app` kaynağında düz metin olmaz. */
enum class BikewayStrings(override val tr: String, override val az: String) : Bilingual {
    TITLE("Bisiklet yolları", "Velosiped yolları"),
    NOTE("Bakü'de OSM bisiklet yolu kaydı az; eksikleri elle işaretleyebilirsin.", "Bakıda OSM-də velosiped yolu qeydləri azdır; çatışmayanları əl ilə qeyd edə bilərsən."),
    HELP(
        "Haritada sokağa uzun bas → \"Bisiklet yolu olarak işaretle\". İşaret yalnız bu telefonda kalır, OSM bisiklet yolu gibi kullanılır.",
        "Xəritədə küçəyə uzun bas → \"Velosiped yolu kimi qeyd et\". Qeyd yalnız bu telefonda qalır, OSM velosiped yolu kimi istifadə olunur.",
    ),
    OSM_STREETS("sokak", "küçə"),
    LAYER_TITLE("Haritada bisiklet yolları", "Xəritədə velosiped yolları"),
    LAYER_SUB("Düz çizgi: OSM · kesikli: elle işaretli", "Düz xətt: OSM · qırıq xətt: əl ilə qeyd olunan"),
    PREF_TITLE("Bisiklet yolu tercihi", "Velosiped yoluna üstünlük"),
    PREF_SUB(
        "Rota maliyeti bu çarpanla (×1.00: tercih yok). Süre, tek yön ve ters yön kuralları değişmez.",
        "Marşrut xərci bu əmsalla (×1.00: üstünlük yoxdur). Vaxt, birtərəfli hərəkət və əks istiqamət qaydaları dəyişmir.",
    ),
    MARKS_TITLE("Elle işaretlenen bisiklet yolları", "Əl ilə qeyd olunan velosiped yolları"),
    NONE_YET("Henüz yok", "Hələ yoxdur"),
    UNNAMED("isimsiz yol", "adsız yol"),
    REMOVE("İşareti kaldır", "Qeydi sil"),
    MENU_MARK("Bisiklet yolu olarak işaretle", "Velosiped yolu kimi qeyd et"),
    MENU_UNMARK("Bisiklet yolu işaretini kaldır", "Velosiped yolu qeydini sil"),
    MENU_MARK_SUB("bütün sokak; rotada tercih edilir", "bütün küçə; marşrutda üstünlük verilir"),
    MENU_ALREADY_SUB("OSM'de zaten bisiklet yolu", "OSM-də artıq velosiped yoludur"),
    ;

    companion object {
        /** `fmtKm`: 1.000 m altı "N m", değilse "X.X km". */
        fun fmtKm(m: Double): String = if (m < 1000) "${Math.round(m)} m" else String.format(Locale.US, "%.1f km", m / 1000)

        /** Rota maliyet çarpanı `×0.85` (50–100'e kıstırılır). */
        fun factor(pct: Double): String = "×" + String.format(Locale.US, "%.2f", pct.coerceIn(50.0, 100.0) / 100)
    }
}
