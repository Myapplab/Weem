package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.geo.BBox
import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.geo.Geo
import java.util.Locale

/** Y21 bölge bilgisi metinleri (MAP.md 5.4): harita bölgeleri alt sayfası. */
enum class RegionText(override val tr: String, override val az: String) : Bilingual {
    TITLE("Harita bölgeleri", "Xəritə əraziləri"),
    HELP("Bölgeler neyi kapsıyor?", "Ərazilər nəyi əhatə edir?"),
    CENTRAL_NAME("Merkez Bakü", "Mərkəzi Bakı"),
    CENTRAL_BODY(
        "Badamdar, Bayıl, Sabail, İçerişehir, Yasamal, Nesimi, Narimanov / Gençlik, Hatai (Ağ şehir), Montin, 8-ci km'nin batısı. Kuryelerin çoğu siparişi bu alanda. Gömülü yol haritası her iki seçimde de geniş alanı kapsar.",
        "Badamdar, Bayıl, Səbail, İçərişəhər, Yasamal, Nəsimi, Nərimanov / Gənclik, Xətai (Ağ şəhər), Montin, 8-ci km-in qərbi. Sifarişlərin çoxu bu ərazidədir. Tətbiqin yol xəritəsi hər iki seçimdə də geniş ərazini əhatə edir.",
    ),
    WIDE_NAME("Geniş Bakü", "Böyük Bakı"),
    WIDE_BODY(
        "Merkez + Binagadi, Hırdalan, Masazır, Novhanı, Maştağa, Ahmedli, Hazi Aslanov, Bakıhanov, Garaçuhur, Sulutepe, Bibiheybet. Uzak siparişlere gidiyorsan bunu seç.",
        "Mərkəz + Binəqədi, Xırdalan, Masazır, Novxanı, Maştağa, Əhmədli, Həzi Aslanov, Bakıxanov, Qaraçuxur, Sulutəpə, Bibiheybət. Uzaq sifarişlərə gedirsənsə bunu seç.",
    ),
    CENTRAL_BUTTON("Merkez", "Mərkəz"),
    WIDE_BUTTON("Geniş", "Böyük"),
    NOTE(
        "Her iki seçimde de geniş yol ağı kullanılır. Turuncu sınırın dışındaki hedefler için ek harita verisi gerekir.",
        "Hər iki seçimdə geniş yol şəbəkəsi işləyir. Narıncı sərhəddən kənardakı ünvanlar üçün əlavə xəritə lazımdır.",
    ),
}

object RegionInfo {
    /** `https://tile.openstreetmap.org/{z}/{x}/{y}.png`: bölge haritası ve Y2 raster stiliyle ortak OSM karoları. */
    const val OSM_TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    /** Güney kenarı boyunca doğu-batı (km). */
    fun widthKm(b: BBox): Double = Geo.haversine(b.south, b.west, b.south, b.east) / 1000

    /** Batı kenarı boyunca kuzey-güney (km). */
    fun heightKm(b: BBox): Double = Geo.haversine(b.south, b.west, b.north, b.west) / 1000

    /** `~W × H km` (yuvarlanmış). */
    fun sizeLabel(b: BBox): String = "~${Math.round(widthKm(b))} × ${Math.round(heightKm(b))} km"

    /** Başlık satırı: "Merkez Bakü · ~17 × 12 km". */
    fun central(lang: Lang): String = "${RegionText.CENTRAL_NAME.get(lang)} · ${sizeLabel(Baku.centralBox)}"
    fun wide(lang: Lang): String = "${RegionText.WIDE_NAME.get(lang)} · ${sizeLabel(Baku.wideBox)}"

    /** Tarayıcıda açılan OSM bağlantısı. */
    fun osmLink(b: BBox): String = String.format(Locale.ROOT, "https://www.openstreetmap.org/?minlon=%s&minlat=%s&maxlon=%s&maxlat=%s&box=yes", b.west, b.south, b.east, b.north)
}
