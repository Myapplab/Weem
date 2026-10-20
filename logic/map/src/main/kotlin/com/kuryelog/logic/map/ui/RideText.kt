package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Bilingual

/**
 * Sol göstergeler, hız ve scooter pencereleri (MAP.md 7.11, 10.2, 10.4, 10.5; YolGo `widgets.dart`, `scooter_sheet.dart`). `:app` kaynağında düz metin olmaz.
 * WEEM'e özgü: [SCOOTER_TITLE] ("Rawstats canlı" yok, S7) ve [SCOOTER_OFF] (Rawstats paylaşım anahtarı yok).
 */
enum class RideText(override val tr: String, override val az: String) : Bilingual {
    ARRIVAL("Varış", "Çatma"),
    ARRIVAL_LC("varış", "çatma"),
    AVG_SUB("ort. km/s", "orta km/s"),
    AVG_TITLE("Rota ortalama hızı", "Marşrutun orta sürəti"),
    BATTERY("Batarya", "Batareya"),
    BATTERY_ESTIMATE("tahmin", "təxmin"),
    KMH("km/s", "km/saat"),
    NO_ROUTE("Rota yok", "Marşrut yoxdur"),
    REMAINING("Durağa kalan", "Dayanacağa qalan"),
    REMAINING_SHORT("Kalan", "Qalan"),
    REMAINING_SUB("yürüme dahil", "piyada daxil"),
    RISK("risk", "risk"),
    ROAD_DETAILS("Yol ayrıntıları", "Yol detalları"),
    ROUTE_TIME("Rota süresi", "Marşrut vaxtı"),
    SCOOTER_AVG("Ort. hız (harekette)", "Orta sürət (hərəkətdə)"),
    SCOOTER_EXTERNAL("Harici batarya", "Xarici batareya"),
    SCOOTER_INTERNAL("İç batarya", "Daxili batareya"),
    SCOOTER_ODOMETER("Kilometre", "Kilometr"),
    SCOOTER_OFF("Scooter bağlı değil — Scooter sekmesinden bağlan", "Skuter qoşulmayıb — Scooter bölməsindən qoş"),
    SCOOTER_POWER("Güç", "Güc"),
    SCOOTER_RANGE("Menzil", "Məsafə"),
    SCOOTER_SPEED("Hız", "Sürət"),
    SCOOTER_TEMP("Sıcaklık", "Temperatur"),
    SCOOTER_TITLE("Scooter · canlı", "Skuter · canlı"),
    SCOOTER_TRIP("Bu sipariş", "Bu sifariş"),
    SCOOTER_VOLTAGE("Voltaj", "Gərginlik"),
    SPEED_POPUP("Farklı hızlarda süre", "Fərqli sürətlərdə vaxt"),
    SPEED_SHEET_HINT("Süre, varış saati ve batarya tahmini. Hızı − / + ile değiştir.", "Vaxt, çatma saatı və batareya təxmini. Sürəti − / + ilə dəyiş."),
    SPEED_SHEET_TITLE("Farklı hızlarda rota", "Fərqli sürətlərdə marşrut"),
    STOP("durak", "dayanacaq"),
    SURFACE_NONE("Yüzey: bu yön ve yol parçasında yeterli fiziksel ölçüm yok.", "Səth: bu istiqamət və yol hissəsində yetərli fiziki ölçmə yoxdur."),
    WALK("Yürüme", "Piyada"),
    WRONG_WAY("Ters yön", "Əks istiqamət"),
}
