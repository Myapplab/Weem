package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Lang

/** Uzun basış menüsünün eylemleri (MAP.md 6.7; satır numaraları tablodaki gibi). */
enum class LongPressAction {
    GO_TO, ADD_STOP, GO_TO_RESTAURANT, PICK_AS_RESTAURANT, ADD_TASK, CURB_WAIT, CURB_REMOVE,
    ROAD_HOURS, WRONG_WAY_SEGMENT, RATE_ROAD, BAD_RATIO, BLOCK_ROAD, BIKEWAY, NO_ROAD_HERE, NO_STREET,
    LIGHT_HERE, NO_LIGHT, POI_EDIT,
}

/** Basılan noktanın 40 m içindeki yol: [name] boşsa "isimsiz yol"; [mainRoad] ana cadde, [oneway] tek yön; [wrongWayAllowed] bu kesimde ters yön izni var mı. */
data class PressedRoad(
    val name: String,
    val mainRoad: Boolean,
    val oneway: Boolean,
    val wrongWayAllowed: Boolean,
    val blocked: Boolean,
    val bikeway: BikewayMenuAction,
    /** Elle puan satırının alt yazısı (Y12 ölçümü; A6.11 doldurur). */
    val rateSubtitle: String? = null,
    /** Kötü kesim oranı satırının başlığı (`streetRatioText`, 9.7). */
    val badRatioTitle: String? = null,
)

/** 30 m içindeki en yakın yer (türler `r`, `f`, `c`, `t`, `m`). */
data class PressedPlace(val kind: Char, val name: String)

/** Bir menü satırı: [enabled] false ise pasif (OSM'de zaten bisiklet yolu). [thumbs] satırın sağında 👍/👎 (elle puan). */
data class LongPressRow(val action: LongPressAction, val title: String, val subtitle: String? = null, val enabled: Boolean = true, val thumbs: Boolean = false)

/**
 * Uzun basış menüsünün satırları ve sırası (YolGo `nav_screen.dart:155-366`; MAP.md 6.7): `MapTab` yalnız çizer. Satır 7 (yüzey türü, Y13) WEEM 1.0'da yok.
 * Yol satırları (8–15) yalnız 40 m içinde yol bulunduğunda.
 */
object LongPressMenu {
    const val ROAD_RADIUS_M = 40.0
    const val PLACE_RADIUS_M = 30.0
    const val LIGHT_RADIUS_M = 25.0
    val PLACE_KINDS = listOf('r', 'f', 'c', 't', 'm')
    private val FOOD = setOf('r', 'f', 'c')

    fun unnamedRoad(lang: Lang) = tl(lang, "isimsiz yol", "adsız yol")

    fun rows(road: PressedRoad?, place: PressedPlace?, lang: Lang): List<LongPressRow> {
        fun t(tr: String, az: String) = tl(lang, tr, az)
        val out = mutableListOf(
            LongPressRow(LongPressAction.GO_TO, t("Buraya git", "Bura get")),
            LongPressRow(LongPressAction.ADD_STOP, t("Durak olarak ekle (çifte sipariş)", "Ünvan kimi əlavə et (ikiqat sifariş)")),
        )
        if (place != null && place.kind in FOOD) {
            out += LongPressRow(LongPressAction.GO_TO_RESTAURANT, "${t("Restorana git", "Restorana get")}: ${place.name}")
        } else {
            out += LongPressRow(LongPressAction.PICK_AS_RESTAURANT, t("Bu noktayı restoran olarak seç", "Bu nöqtəni restoran kimi seç"))
        }
        out += LongPressRow(LongPressAction.ADD_TASK, t("Bu konuma günlük iş ekle", "Bu ünvana gündəlik iş əlavə et"))
        out += LongPressRow(LongPressAction.CURB_WAIT, t("Burada kaldırımda bekliyorum", "Burada səkidə gözləyirəm"), t("Yakındaki ışıkla karıştırma", "Yaxındakı işıqforla qarışdırma"))
        out += LongPressRow(LongPressAction.CURB_REMOVE, t("Kaldırım işaretini kaldır", "Səki işarəsini sil"))
        if (road != null) {
            val n = road.name.ifEmpty { unnamedRoad(lang) }
            if (!road.mainRoad) out += LongPressRow(LongPressAction.ROAD_HOURS, t("Saatli çift yön izni", "Saatla iki istiqamət icazəsi"), n)
            if (road.mainRoad && road.oneway) {
                out += LongPressRow(
                    LongPressAction.WRONG_WAY_SEGMENT,
                    if (road.wrongWayAllowed) t("Bu kesimde ters yön iznini kaldır", "Bu hissədə əks istiqamət icazəsini sil")
                    else t("Bu kesimde ters yöne izin ver", "Bu hissədə əks istiqamətə icazə ver"),
                    t("$n · yalnız bu kavşak arası, ana cadde", "$n · yalnız bu kəsişmələr arası, əsas küçə"),
                )
            }
            out += LongPressRow(LongPressAction.RATE_ROAD, t("Elle yüzey puanı · $n", "Əl ilə səth balı · $n"), road.rateSubtitle, thumbs = true)
            road.badRatioTitle?.let {
                out += LongPressRow(LongPressAction.BAD_RATIO, it, t("Ölçülmüş kötü kesimler + kötü noktalar (±25 m)", "Ölçülmüş pis hissələr + pis nöqtələr (±25 m)"))
            }
            out += LongPressRow(
                LongPressAction.BLOCK_ROAD,
                if (road.blocked) t("Yol kapatmayı kaldır", "Yolun bağlanmasını ləğv et") else t("Bu yolu tamamen kapat", "Bu yolu tam bağla"),
                t("$n · Ses− artık yalnız ±25 m işaretler", "$n · Səs− indi yalnız ±25 m qeyd edir"),
            )
            out += LongPressRow(
                LongPressAction.BIKEWAY,
                if (road.bikeway == BikewayMenuAction.UNMARK) t("Bisiklet yolu işaretini kaldır", "Velosiped yolu qeydini sil")
                else t("Bisiklet yolu olarak işaretle", "Velosiped yolu kimi qeyd et"),
                if (road.bikeway == BikewayMenuAction.ALREADY_OSM) t("$n · OSM'de zaten bisiklet yolu", "$n · OSM-də artıq velosiped yoludur")
                else t("$n · bütün sokak; rotada tercih edilir", "$n · bütün küçə; marşrutda üstünlük verilir"),
                enabled = road.bikeway != BikewayMenuAction.ALREADY_OSM,
            )
            out += LongPressRow(LongPressAction.NO_ROAD_HERE, t("Burada yol yok", "Burada yol yoxdur"), t("$n · bu parça rotaya hiç girmez", "$n · bu hissə marşruta heç vaxt düşməyəcək"))
            out += LongPressRow(LongPressAction.NO_STREET, t("Bu sokağın tamamı yok", "Bu küçə ümumiyyətlə yoxdur"), n)
        }
        out += LongPressRow(LongPressAction.LIGHT_HERE, t("Burada trafik ışığı var", "Burada işıqfor var"))
        out += LongPressRow(LongPressAction.NO_LIGHT, t("Bu noktada ışık yok", "Bu nöqtədə işıqfor yoxdur"))
        out += if (place == null) LongPressRow(LongPressAction.POI_EDIT, t("Buraya işletme ekle", "Bura obyekt əlavə et"))
        else LongPressRow(LongPressAction.POI_EDIT, t("İşletme adını düzelt", "Obyektin adını düzəlt"), place.name)
        return out
    }

    /** Elle puan satırının 👍/👎 ipuçları. */
    fun thumbHints(lang: Lang) = tl(lang, "Kaliteli yol", "Keyfiyyətli yol") to tl(lang, "Kalitesiz yol", "Keyfiyyətsiz yol")

    private fun tl(lang: Lang, tr: String, az: String) = if (lang == Lang.AZ) az else tr
}
