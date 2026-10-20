package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.BBox
import java.util.Locale

/** Yer düzeltme penceresi (MAP.md 5.3 `poi_editor`) ve bölge bilgisi (MAP.md 5.4) metinleri. */
enum class PlaceText(override val tr: String, override val az: String) : Bilingual {
    ADD_TITLE("İşletme ekle", "Obyekt əlavə et"),
    EDIT_TITLE("İşletme adını düzelt", "Obyektin adını düzəlt"),
    NAME("Ad", "Ad"),
    KIND_RESTAURANT("Restoran", "Restoran"),
    KIND_FASTFOOD("Fast food", "Fast food"),
    KIND_CAFE("Kafe", "Kafe"),
    KIND_STORE("Mağaza", "Mağaza"),
    KIND_MALL("AVM", "Ticarət mərkəzi"),
    NOTE("Bu düzeltme WEEM’de saklanır; harita güncellense de korunur.", "Bu düzəliş WEEM-də qalır; xəritə yenilənsə də itmir."),
    SAVE("Kaydet", "Yadda saxla"),
    CANCEL("Vazgeç", "Ləğv et"),
    REGION_TITLE("Harita bölgeleri", "Xəritə əraziləri"),
    REGION_WHAT("Bölgeler neyi kapsıyor?", "Ərazilər nəyi əhatə edir?"),
    CENTRAL_TITLE("Merkez Bakü · %s", "Mərkəzi Bakı · %s"),
    CENTRAL_TEXT(
        "Badamdar, Bayıl, Sabail, İçerişehir, Yasamal, Nesimi, Narimanov / Gençlik, Hatai (Ağ şehir), Montin, 8-ci km'nin batısı. Kuryelerin çoğu siparişi bu alanda. Gömülü yol haritası her iki seçimde de geniş alanı kapsar.",
        "Badamdar, Bayıl, Səbail, İçərişəhər, Yasamal, Nəsimi, Nərimanov / Gənclik, Xətai (Ağ şəhər), Montin, 8-ci km-in qərbi. Sifarişlərin çoxu bu ərazidədir. Tətbiqin yol xəritəsi hər iki seçimdə də geniş ərazini əhatə edir.",
    ),
    WIDE_TITLE("Geniş Bakü · %s", "Böyük Bakı · %s"),
    WIDE_TEXT(
        "Merkez + Binagadi, Hırdalan, Masazır, Novhanı, Maştağa, Ahmedli, Hazi Aslanov, Bakıhanov, Garaçuhur, Sulutepe, Bibiheybet. Uzak siparişlere gidiyorsan bunu seç.",
        "Mərkəz + Binəqədi, Xırdalan, Masazır, Novxanı, Maştağa, Əhmədli, Həzi Aslanov, Bakıxanov, Qaraçuxur, Sulutəpə, Bibiheybət. Uzaq sifarişlərə gedirsənsə bunu seç.",
    ),
    BTN_CENTRAL("Merkez", "Mərkəz"),
    BTN_WIDE("Geniş", "Böyük"),
    REGION_NOTE(
        "Her iki seçimde de geniş yol ağı kullanılır. Turuncu sınırın dışındaki hedefler için ek harita verisi gerekir.",
        "Hər iki seçimdə geniş yol şəbəkəsi işləyir. Narıncı sərhəddən kənardakı ünvanlar üçün əlavə xəritə lazımdır.",
    ),
    ;

    fun format(lang: Lang, vararg args: Any?): String = String.format(get(lang), *args)
}

/** Yer düzeltme penceresi kuralları (MAP.md 5.3): ad en çok 100, kelime başı büyük, tür seçici. */
object PoiEditRules {
    const val MAX_NAME = 100

    /** Seçilebilir türler (`s`, `l` gelirse `r`). */
    val KINDS = listOf('r', 'f', 'c', 't', 'm')

    fun kindOrDefault(kind: Char): Char = if (kind in KINDS) kind else 'r'

    fun kindLabel(kind: Char): PlaceText = when (kind) {
        'f' -> PlaceText.KIND_FASTFOOD
        'c' -> PlaceText.KIND_CAFE
        't' -> PlaceText.KIND_STORE
        'm' -> PlaceText.KIND_MALL
        else -> PlaceText.KIND_RESTAURANT
    }

    /** Boşlukları teke indirir, 100 karaktere kısaltır, her kelimenin ilk harfini büyütür (Türkçe/Azerice `i` → `İ`). */
    fun normalizeName(raw: String, lang: Lang = Lang.TR): String {
        val locale = Locale.forLanguageTag(lang.key)
        return raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).split(' ').joinToString(" ") { w ->
            if (w.isEmpty()) w else w.substring(0, 1).uppercase(locale) + w.substring(1)
        }
    }
}

/** Bölge bilgisi sayfası: OSM'de sınırı gösteren bağlantı (MAP.md 5.4). */
object RegionInfo {
    fun osmBoxUrl(b: BBox): String = "https://www.openstreetmap.org/?minlon=${b.west}&minlat=${b.south}&maxlon=${b.east}&maxlat=${b.north}&box=yes"
}
