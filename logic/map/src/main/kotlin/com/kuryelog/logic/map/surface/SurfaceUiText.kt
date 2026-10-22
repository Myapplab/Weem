package com.kuryelog.logic.map.surface

import com.kuryelog.logic.core.Bilingual

/**
 * Y13 ekranlarının sabit metinleri (YolGo `surface_screen.dart` @ 870ab6e, TR/AZ): Yol puanları bölümü, sokak parçaları, parça ayrıntısı, yüzey kaydı,
 * Ölçüm ve kalibrasyon. Birleşik/sayılı satırlar [SurfaceText]'te.
 */
enum class SurfaceUiText(override val tr: String, override val az: String) : Bilingual {
    UNNAMED_ROAD("isimsiz yol", "adsız yol"),
    ROAD_SCORES("Yol puanları", "Yol balları"),
    SETUP("Ölçüm ve kalibrasyon", "Ölçmə və kalibrasiya"),
    LAYER("Haritada yüzey renkleri", "Xəritədə səth rəngləri"),
    NO_MEASUREMENT("Henüz fiziksel yol ölçümü yok", "Hələ fiziki yol ölçməsi yoxdur"),
    STREET_SCORE("Sokak puanı", "Küçə balı"),
    IMPACT_EVENTS("Darbe olayları · türünü sen doğrula", "Zərbə hadisələri · növünü sən təsdiqlə"),
    SAVE_MARK("Yüzey türü veya kapanış kaydet", "Səth növü və ya bağlanma qeyd et"),
    TECHNICAL("Teknik ölçümler", "Texniki ölçmələr"),
    MARK_TITLE("Yüzey kaydı", "Səth qeydi"),
    MARK_SAVED("Yüzey kaydedildi", "Səth qeyd edildi"),
    SAVE("Kaydet", "Yadda saxla"),
    SETUP_TITLE("Yol ölçümü", "Yol ölçməsi"),
    SAVED_PROFILE("Kayıtlı montaj profili", "Qeyd edilmiş montaj profili"),
    RESUME("Ölçüme devam et", "Ölçməyə davam et"),
    PAUSE("Ölçümü duraklat", "Ölçməni dayandır"),
    RESUME_HELP(
        "Devam etme komutu yalnızca uygun sipariş etabında doğrulanan scooter sürüşünü kabul eder.",
        "Davam əmri yalnız uyğun sifariş mərhələsində təsdiqlənən skuter sürüşünü qəbul edir.",
    ),
    NEW_PROFILE("Scooter / telefon / montaj profili: yeni", "Skuter / telefon / montaj profili: yeni"),
    NEW_PROFILE_HELP("Düzen değiştiyse yeni profil oluştur; eski ölçümler korunur.", "Quruluş dəyişibsə yeni profil yarat; köhnə ölçmələr saxlanır."),
    MOUNT_NOT_READY("Sabit montaj ve güvenilir konum henüz doğrulanmadı", "Sabit montaj və etibarlı məkan hələ təsdiqlənmədi"),
    SAVE_MOUNT(
        "Montaj yönünü kaydet (telefon sabit gidonda, scooter duruyorken)",
        "Montaj istiqamətini qeyd et (telefon sükan üzərində sabit, skuter dayanarkən)",
    ),
    CONTROLLED("Kontrollü kalibrasyon", "Nəzarətli kalibrasiya"),
    CONTROLLED_HELP(
        "Her hız aralığında bilinen düzgün ve bozuk yüzeyden en az üç ayrı sipariş etabında geç. Etiketi geçişten önce seç, sonra kapat. Normal sürüşler referansı değiştirmez.",
        "Hər sürət aralığında məlum hamar və nahamar səthdən ən az üç ayrı sifariş mərhələsində keç. Etiketi keçiddən əvvəl seç, sonra bağla. Adi sürüşlər istinadı dəyişmir.",
    ),
    CALIBRATION_OFF("Kalibrasyon kapalı", "Kalibrasiya bağlıdır"),
    KNOWN_SMOOTH("Bilinen düzgün yüzey", "Məlum hamar səth"),
    KNOWN_ROUGH("Bilinen bozuk yüzey", "Məlum nahamar səth"),
    CALIBRATION_WAITING("Kalibrasyon bekliyor", "Kalibrasiya gözlənilir"),
    CALCULATE("Kalibrasyonu hesapla", "Kalibrasiyanı hesabla"),
    ROAD_CALIBRATION("Yol Kalitesi Kalibrasyonu (1–10)", "Yol Keyfiyyəti Kalibrasiyası (1–10)"),
    MOTION_PERMISSION(
        "Hareket izni: sınırlı çıkarım · sensör + GPS kullanılır (adım dedektörü için etkinlik izni verilmedi)",
        "Hərəkət icazəsi: məhdud nəticə · sensor + GPS işlənir",
    ),
    DIAGNOSTICS("Ölçüm tanılama", "Ölçmə diaqnostikası"),
    EXPORT_JSON("JSON dışa aktar", "JSON ixrac et"),
    ERROR("Hata", "Xəta"),
    NEW_PROFILE_TITLE("Yeni ölçüm profili", "Yeni ölçmə profili"),
    NEW_PROFILE_HINT("Telefon modeli · montaj yeri", "Telefon modeli · montaj yeri"),
    CREATE("Oluştur", "Yarat"),
}
