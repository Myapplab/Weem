package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Bilingual

/**
 * Hava şeridi, ayrıntı ve ayar bölümü metinleri (MAP.md 10.9; YolGo `weather_widget.dart`, `weather_section.dart`).
 * M-S19: bildirim 1.7.0'dan beri günde bir, anahtar başlığı buna göre ("Günde bir hava bildirimi"; önerilen düzeltme, onaya sunulur).
 */
enum class WeatherUiText(override val tr: String, override val az: String) : Bilingual {
    ABOUT_TITLE("Hava bildirimi hakkında", "Hava bildirişi haqqında"),
    ABOUT_TEXT(
        "Son bilinen konuma göre; konum yoksa Bakı. Bildirim izni gerekir. Pil/ağ koşulları saati geciktirebilir. Kaynak: Open-Meteo.",
        "Son məlum məkana görə; məkan yoxdursa Bakı. Bildiriş icazəsi lazımdır. Batareya və şəbəkə şəraiti vaxtı gecikdirə bilər. Mənbə: Open-Meteo.",
    ),
    CLOSE("Kapat", "Bağla"),
    COLLAPSE("Küçült", "Kiçilt"),
    DETAIL_CLOUD("Bulut", "Bulud"),
    DETAIL_DEW("Çiy noktası", "Şeh nöqtəsi"),
    DETAIL_FEELS("Hissedilen", "Hiss edilən"),
    DETAIL_GUST("Hamle", "Külək şiddəti"),
    DETAIL_HUMIDITY("Nem", "Rütubət"),
    DETAIL_PRESSURE("Basınç", "Təzyiq"),
    DETAIL_RAIN("Yağış", "Yağıntı"),
    DETAIL_RAIN_CHANCE("Yağış ihtimali", "Yağıntı ehtimalı"),
    DETAIL_VISIBILITY("Görüş", "Görünüş"),
    DETAIL_WIND("Rüzgar", "Külək"),
    DETAIL_WIND_DIR("Rüzgar yönü", "Küləyin istiqaməti"),
    EIGHT_HOURS("8 saat", "8 saat"),
    ERROR_FETCH("Tahmin alınamadı. İnternet bağlantısını kontrol et.", "Proqnoz alınmadı. İnternet bağlantısını yoxla."),
    FAILED("Hava güncellenemedi", "Hava yenilənmədi"),
    FORECAST("Tahmin", "Proqnoz"),
    INFO("Bilgi", "Məlumat"),
    LOADING("Hava yükleniyor…", "Hava yüklənir…"),
    NONE("Hava verisi yok", "Hava məlumatı yoxdur"),
    NOTIFY_SUBTITLE("Sonraki 4 saat · yağış, rüzgâr, sıcaklık", "Növbəti 4 saat · yağıntı, külək, temperatur"),
    NOTIFY_TITLE("Günde bir hava bildirimi", "Gündə bir hava bildirişi"),
    NOW("Şimdi", "İndi"),
    PHOTO("Fotoğraf", "Foto"),
    REFRESH("Yenile", "Yenilə"),
    SECTION_TITLE("Hava durumu", "Hava proqnozu"),
    SHOW_EIGHT("8 saatlik tahmini göster", "8 saatlıq proqnozu göstər"),
    STALE("Eski tahmin · ", "Köhnə proqnoz · "),
    STRIP_SUBTITLE("Kapatınca üstteki 4 saatlik şerit tamamen gizlenir", "Bağlayanda yuxarıdakı 4 saatlıq zolaq tamamilə gizlənir"),
    STRIP_TITLE("Haritada hava şeridini göster", "Xəritədə hava zolağını göstər"),
    UPDATED("Güncellendi", "Yeniləndi"),
}
