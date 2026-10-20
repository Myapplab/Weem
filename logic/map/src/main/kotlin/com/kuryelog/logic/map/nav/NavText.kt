package com.kuryelog.logic.map.nav

import com.kuryelog.logic.core.Bilingual

/** Navigasyonun sabit iki dilli metinleri (MAP.md 7, 8.7; YolGo metinleri harfiyen, WEEM menü yolları WEEM adlarıyla). */
enum class NavText(override val tr: String, override val az: String) : Bilingual {
    NO_MAP("Harita verisi yok — Daha fazla → Harita ayarları → Harita verisi", "Xəritə məlumatı yoxdur — Daha çox → Xəritə tənzimləmələri → Xəritə məlumatları"),
    ROUTING("Rota hesaplanıyor…", "Marşrut hesablanır…"),
    RESOLVING("Adres çözülüyor…", "Ünvan axtarılır…"),
    SEARCHING("Adres aranıyor…", "Ünvan axtarılır…"),
    WORKING("Çalışıyor…", "Hesablanır…"),
    NO_LINK_LOCATION("Bu linkte konum bulunamadı", "Bu linkdə ünvan tapılmadı"),
    SEARCH_HINT("Adres, mekân adı, koordinat veya harita linki yaz", "Ünvan, obyekt adı, koordinat və ya xəritə linki yaz"),
    KEEP_OLD_ROUTE("Yeni rota bulunamadı — eski rota sürüyor", "Yeni marşrut tapılmadı — köhnə marşrut davam edir"),
    MAIN_ROAD_WRONG_WAY("Ana caddede ters yöndesin!", "Əsas küçədə əks istiqamətdəsən!"),
    UNNAMED_ROAD("isimsiz yol", "adsız yol"),
    MAP_POINT("Harita noktası", "Xəritə nöqtəsi"),
    HOME("Ev", "Ev"),
    NET_TIMEOUT("Ağ zaman aşımı — hotspot bağlı mı?", "Şəbəkə cavab vermir — hotspot qoşuludur?"),
    NO_INTERNET("İnternet yok", "İnternet yoxdur"),
    NO_PERMISSION("Konum izni yok veya GPS kapalı", "Məkan icazəsi yoxdur və ya GPS sönülüdür"),

    /** YolGo'da AZ'si yoktu; MAP.md 8.13 SORU-N2 varsayılanı. */
    STALE_FIX("GPS konumu eski veya belirsiz", "GPS mövqeyi köhnə və ya qeyri-dəqiqdir"),
    NO_SIGNAL("GPS sinyali alınamadı", "GPS siqnalı tutulmadı"),
    NEW_ADDRESS("Yeni adres geldi", "Yeni ünvan gəldi"),
    ADD_STOP("Durak ekle", "Ünvan əlavə et"),
    NEW_ROUTE("Yeni rota", "Yeni marşrut"),
    RETRY("Tekrar", "Yenidən"),
    NO_ROAD("Yol yok", "Yol yoxdur"),
    FINISH("Bitir", "Tamamla"),
    LOW_CONFIDENCE("⚠ Adres emin değil — pini kontrol et", "⚠ Ünvan dəqiq deyil — nöqtəni yoxla"),
    HOME_SUGGEST("Batarya düşük — eve dön?", "Batareya azdır — evə qayıdaq?"),
    HOME_GO("Eve git", "Evə get"),
    SEARCH_FIELD("Adres veya mekân ara", "Ünvan və ya obyekt axtar"),
    DRAW_ROUTE("Rota çiz", "Marşrut çək"),
    NO_RESULTS("Sonuç bulunamadı", "Nəticə tapılmadı"),

    /** Yalnız-navigasyon servisi (yeni metinler, MAP.md 7.17 SORU-N4). */
    NAV_CHANNEL("WEEM navigasyon", "WEEM naviqasiya"),
    NAV_ONLY_TITLE("Navigasyon sürüyor", "Naviqasiya davam edir"),
    NAV_ONLY_TEXT("Gün bitti; rota açık olduğu için konum en çok 90 dk sürer", "Gün bitdi; marşrut açıq olduğu üçün mövqe ən çox 90 dəq davam edir"),

    /** Uzun basış ve rota paneli (MAP.md 6.7, 7.11). */
    ROUTE_PIN("Rota ve pin", "Marşrut və nöqtə"),
    GOOGLE_ATTRIBUTION("Google Maps", "Google Maps"),
}
