package com.kuryelog.logic.app

import com.kuryelog.logic.core.Bilingual
/**
 * Kabuk metinleri, iki dil yan yana (G3). Her giriş iki dili birlikte taşır; eksik çeviri derlenmez.
 * Ekrana giden metin burada olmalı; `:app` kaynağında düz Türkçe/Azerice metin testle yakalanır.
 */
enum class Text(override val tr: String, override val az: String) : Bilingual {
    TAB_MAP("Harita", "Xəritə"),
    TAB_ORDERS("Siparişler", "Sifarişlər"),
    TAB_SCOOTER("Scooter", "Scooter"),
    TAB_REMOTE("Uzaktan", "Uzaqdan"),
    TAB_MORE("Daha fazla", "Daha çox"),

    PLACEHOLDER_COMING("Bu ekran sonraki aşamada gelecek", "Bu ekran növbəti mərhələdə gələcək"),
    PLACEHOLDER_COUNTER("Sekme durumu denemesi: %d", "Tab vəziyyəti yoxlaması: %d"),

    SETTINGS_TITLE("Ayarlar", "Ayarlar"),
    SETTINGS_LANGUAGE("Dil", "Dil"),
    SETTINGS_THEME("Tema", "Tema"),
    LANG_TR("Türkçe", "Türkçe"),
    LANG_AZ("Azərbaycanca", "Azərbaycanca"),
    LANG_SYSTEM("Telefon dili", "Telefonun dili"),
    THEME_SYSTEM("Telefonu izle", "Telefona uyğun"),
    THEME_LIGHT("Gündüz", "Gündüz"),
    THEME_DARK("Gece", "Gecə"),
    SETTINGS_ORIENTATION("Ekran yönü", "Ekranın istiqaməti"),
    ORIENTATION_FREE("Serbest", "Sərbəst"),
    ORIENTATION_PORTRAIT("Dikey kilit", "Şaquli kilid"),
    ORIENTATION_LANDSCAPE("Yatay kilit", "Üfüqi kilid"),
    VERSION("Sürüm %s", "Versiya %s"),

    // R20 çökme kartı ve çizilemeyen sekme (CORE.md 4.1; Rawstats metinleri, ad "WEEM").
    CRASH_TITLE("WEEM kapandı · %s", "WEEM bağlandı · %s"),
    CRASH_HINT("Hata metnini kopyalayıp gönderirsen nedeni bulunur.", "Xəta mətnini kopyalayıb göndərsən, səbəbi tapılar."),
    CRASH_COPY("Hata metnini kopyala", "Xəta mətnini kopyala"),
    CRASH_SHARE("Hata metnini paylaş", "Xəta mətnini paylaş"),
    CRASH_HIDE("Gizle", "Gizlət"),
    PAGE_FAILED("Bu sayfa açılamadı", "Bu səhifə açılmadı"),
    ERROR("Hata: ", "Xəta: "),

    // Daha fazla → Kayıtlar (CORE.md 5.3; A2.6 eski Rawstats ZIP'i burada içe aktarılır).
    RECORDS_TITLE("Kayıtlar", "Qeydlər"),
    RECORDS_OPEN("Kayıtlar ve içe aktarma", "Qeydlər və yükləmə"),
    RECORDS_IMPORT("Eski kayıtları içe aktar (.zip)", "Köhnə qeydləri yüklə (.zip)"),
    RECORDS_IMPORT_STOP("İçe aktarmadan önce vardiyayı durdur.", "Yükləməzdən əvvəl növbəni dayandır."),
    RECORDS_PREPARING("Hazırlanıyor…", "Hazırlanır…"),
    RECORDS_IMPORT_DONE("Kayıtlar aktarıldı; aynı kayıtlar tekrar sayılmadı.", "Qeydlər yükləndi; eyni qeydlər təkrar sayılmadı."),
    RECORDS_DAILY("Günlük kayıtlar", "Günlük qeydlər"),
    RECORDS_EMPTY("Henüz kayıt yok.", "Hələ qeyd yoxdur."),
    RECORDS_DAY("%d dosya · %s", "%d fayl · %s"),
    RECORDS_ARCHIVED("Arşivde", "Arxivdə"),
    // R17/R18 yedek ve dışa aktarma (A4.3; RS `renderRecordTools`, `I18n` export_all/share/toast_saved/toast_no_logs). Klasör "WEEM".
    RECORDS_BACKUP("Yedek", "Ehtiyat"),
    RECORDS_EXPORT_ALL("Tüm kayıtları paylaş (.zip)", "Bütün qeydləri paylaş (.zip)"),
    RECORDS_EXPORT_ANON("Analiz için paylaş (anonim)", "Analiz üçün paylaş (anonim)"),
    RECORDS_EXPORT_ANON_INFO("Analiz için ayrı bir ZIP: müşteri adı, adresi, telefonu, notu ve kapı kodu çıkarılır, sipariş kimlikleri takma ada çevrilir, konumlar yaklaşık 100 m'ye yuvarlanır, WEEM panel satırları silinir. Restoran adı ve adresi, ücret, süre ve km kalır. Tam yedek ayrıca durur ve içe aktarma için o kullanılır.",
        "Analiz üçün ayrıca ZIP: müştərinin adı, ünvanı, telefonu, qeydi və qapı kodu çıxarılır, sifariş identifikatorları təxəllüsə çevrilir, yerlər təxminən 100 m-ə yuvarlaqlaşdırılır, WEEM panel sətirləri silinir. Restoranın adı və ünvanı, ödəniş, müddət və km qalır. Tam ehtiyat ayrıca qalır və idxal üçün o istifadə olunur."),
    RECORDS_DAILY_PACK("Vardiya bitince günlük veri paketi", "Növbə bitəndə gündəlik məlumat paketi"),
    RECORDS_DAILY_PACK_INFO("Günün anonim analiz ZIP'i İndirilenler/WEEM/gunluk klasörüne yazılır; aynı günün sonraki vardiyası dosyayı yeniler. Müşteri bilgisi içermez, telefondan çıkmaz.",
        "Günün anonim analiz ZIP-i Yükləmələr/WEEM/gunluk qovluğuna yazılır; eyni günün növbəti növbəsi faylı yeniləyir. Müştəri məlumatı yoxdur, telefondan çıxmır."),
    RECORDS_SHARE("Kaydı paylaş", "Qeydi paylaş"),
    RECORDS_SAVED("İndirilenler/WEEM klasörüne kaydedildi", "Yükləmələr/WEEM qovluğuna yazıldı"),
    RECORDS_NO_LOGS("Henüz kayıt yok", "Hələ qeyd yoxdur"),
    // N3 şifreli haftalık yedek (A4.3, kullanıcı kararı S22).
    BACKUP_TITLE("Şifreli yedek", "Şifrəli ehtiyat"),
    BACKUP_INFO("Her vardiya bitince o haftanın kayıtları, ayarlar (ev konumun ve harita anahtarların hariç), sipariş veritabanı ve haritanın küçük verisi (yol bilgisi, işaretler, gün raporları, rota kayıtları; sürüş günlükleri hariç) parolayla şifrelenip İndirilenler/WEEM/yedek klasörüne yazılır; bir dosya bir hafta. Parolayı unutursan yedek açılmaz.",
        "Hər növbə bitəndə həmin həftənin qeydləri, ayarlar (ev məkanın və xəritə açarların istisna), sifariş bazası və xəritənin kiçik məlumatı (yol məlumatı, işarələr, gün hesabatları, marşrut qeydləri; sürüş jurnalları istisna) parolla şifrələnib Yükləmələr/WEEM/yedek qovluğuna yazılır; bir fayl bir həftə. Parolu unutsan ehtiyat açılmaz."),
    BACKUP_NO_PASSWORD("Parola yok: otomatik şifreli yedek kapalı.", "Parol yoxdur: avtomatik şifrəli ehtiyat bağlıdır."),
    BACKUP_ON("Otomatik şifreli yedek açık.", "Avtomatik şifrəli ehtiyat açıqdır."),
    BACKUP_SET_PASSWORD("Parola belirle", "Parol təyin et"),
    BACKUP_CHANGE_PASSWORD("Parolayı değiştir", "Parolu dəyiş"),
    BACKUP_PASSWORD("Parola (en az 8 karakter)", "Parol (ən azı 8 simvol)"),
    BACKUP_PASSWORD_AGAIN("Parola (tekrar)", "Parol (təkrar)"),
    BACKUP_PASSWORD_MISMATCH("Parolalar aynı değil ya da 8 karakterden kısa.", "Parollar eyni deyil və ya 8 simvoldan qısadır."),
    BACKUP_NOW("Bu haftayı şimdi yedekle", "Bu həftəni indi ehtiyatla"),
    BACKUP_SAVED("Şifreli yedek İndirilenler/WEEM/yedek klasörüne yazıldı", "Şifrəli ehtiyat Yükləmələr/WEEM/yedek qovluğuna yazıldı"),
    BACKUP_NONE("Henüz şifreli yedek yok.", "Hələ şifrəli ehtiyat yoxdur."),
    BACKUP_EXPORT("Dışa aktar / paylaş", "İxrac et / paylaş"),
    BACKUP_DELETE_WEEK("Uygulamadan sil", "Tətbiqdən sil"),
    BACKUP_DELETE_CONFIRM("Bu haftanın kayıtları telefondan silinir (vardiya açıksa bugün hariç). Şifreli yedek dosyası kalır; gerekirse geri yüklenir.",
        "Bu həftənin qeydləri telefondan silinir (növbə açıqdırsa bu gün istisna). Şifrəli ehtiyat faylı qalır; lazım olsa geri yüklənir."),
    BACKUP_DELETE_AFTER_EXPORT("Önce dışa aktar; sonra uygulamadan silinebilir.", "Əvvəlcə ixrac et; sonra tətbiqdən silinə bilər."),
    BACKUP_RESTORE("Şifreli yedeği geri yükle", "Şifrəli ehtiyatı geri yüklə"),
    BACKUP_RESTORE_DONE("Şifreli yedek geri yüklendi; aynı kayıtlar tekrar sayılmadı, mevcut ayarlar değişmedi.", "Şifrəli ehtiyat geri yükləndi; eyni qeydlər təkrar sayılmadı, mövcud ayarlar dəyişmədi."),
    BACKUP_MAP_RESTART("Harita verisi WEEM yeniden açılınca yüklenir (yalnız telefonda olmayanlar).", "Xəritə məlumatı WEEM yenidən açılanda yüklənir (yalnız telefonda olmayanlar)."),
    BACKUP_RESTART_NOW("Şimdi yeniden başlat", "İndi yenidən başlat"),
    LATER("Sonra", "Sonra"),
    BACKUP_WRONG_PASSWORD("Parola yanlış ya da dosya bozuk.", "Parol yanlışdır və ya fayl zədəlidir."),
    BACK("Geri", "Geri"),
    SAVE("Kaydet", "Yadda saxla"),
    CANCEL("İptal", "Ləğv et"),
    /** Kapat simge düğmesinin ekran okuyucu adı (A10.4d). */
    CLOSE("Kapat", "Bağla"),
    /** Satır menüsü (⋮) simge düğmesinin ekran okuyucu adı (A10.4d). */
    MORE_ACTIONS("Seçenekler", "Seçimlər"),

    // G1 vardiya kartı (CORE.md 1.2; RS `I18n` shift_start/shift_stop).
    SHIFT_START("▶  Vardiyayı BAŞLAT", "▶  Növbəni BAŞLAT"),
    SHIFT_STOP("■  Vardiyayı BİTİR", "■  Növbəni BİTİR"),
    SHIFT_ON("Vardiya açık · başlangıç %s", "Növbə açıqdır · başlanğıc %s"),
    SHIFT_OFF("Vardiya kapalı", "Növbə bağlıdır"),
    SHIFT_INTERRUPTED("GPS kaydı durdu; sürdürmek için başlat düğmesine dokun.", "GPS qeydi dayandı; davam etmək üçün başlat düyməsinə toxun."),
    SHIFT_NEEDS_LOCATION("Vardiya kaydı için konum izni gerekli.", "Növbə qeydi üçün məkan icazəsi lazımdır."),

    // G2 ev konumu (CORE.md 2.2; RS ev ekranı metinleri).
    HOME_TITLE("Ev konumu", "Ev məkanı"),
    HOME_OPEN("Ev konumu ve otomatik vardiya", "Ev məkanı və avtomatik növbə"),
    HOME_SAVED("Kayıtlı ev", "Saxlanılmış ev"),
    HOME_NONE("Ev konumu seçilmedi", "Ev məkanı seçilməyib"),
    HOME_UPDATED("Son güncelleme: %s", "Son yenilənmə: %s"),
    HOME_UNKNOWN("bilinmiyor", "bilinmir"),
    HOME_EDIT("Düzenle", "Düzəliş et"),
    HOME_DELETE("Sil", "Sil"),
    HOME_DELETE_ASK("Ev konumunu sil?", "Ev məkanını sil?"),
    HOME_PICK("Ev konumunu seç", "Ev məkanını seç"),
    HOME_USE_CURRENT("Şu anki konumumu kullan", "Hazırkı məkanımı istifadə et"),
    HOME_FROM_MAP("Haritadan seç", "Xəritədən seç"),
    HOME_MEASURING("%d saniye ölçülüyor…", "%d saniyə ölçülür…"),
    HOME_ACCURACY("Doğruluk: ±%s m", "Dəqiqlik: ±%s m"),
    HOME_DRAFT("Seçilen konum · taslak", "Seçilmiş məkan · qaralama"),
    HOME_WARN("Doğruluk %d m'den kötü; daha açık bir yerde yeniden ölçebilirsin.", "Dəqiqlik %d m-dən pisdir; daha açıq yerdə yenidən ölçə bilərsən."),
    HOME_NO_FIX("Güncel konum alınamadı; dışarıda tekrar dene.", "Cari məkan alınmadı; çöldə yenidən cəhd et."),
    HOME_NO_PROVIDER("Konumu açıp tekrar dene.", "Məkanı açıb yenidən cəhd et."),
    HOME_BACKGROUND("Otomatik vardiya için konum iznini \"Her zaman izin ver\" yap.", "Avtomatik növbə üçün məkan icazəsini \"Həmişə icazə ver\" et."),
    HOME_BACKGROUND_BUTTON("İzni aç", "İcazəni aç"),
    HOME_RULE(
        "Ev ayarlıysa: evden kuş uçuşu %d m uzaklaşıp GPS hızı en az %d km/s olunca (art arda iki ölçüm, doğruluk ≤%d m) vardiya kendiliğinden başlar; eve (%d m) dönüp %d saniye yerinde kalınca biter (açık sipariş varken bitmez). Elle başlat/bitir her zaman çalışır. Ev konumu yalnız bu telefonun ayarlarında saklanır; kayıtlara ve dışa aktarmaya yazılmaz.",
        "Ev təyin olunubsa: evdən düz xətt %d m uzaqlaşıb GPS sürəti ən azı %d km/s olanda (ardıcıl iki ölçmə, dəqiqlik ≤%d m) növbə özü başlayır; evə (%d m) qayıdıb %d saniyə yerində qalanda bitir (açıq sifariş varkən bitmir). Əl ilə başlat/bitir həmişə işləyir. Ev məkanı yalnız bu telefonun ayarlarında saxlanılır; qeydlərə və ixraca yazılmır.",
    ),

    // A4.2 Siparişler ayarları (S20 (b); RS "Özel ayarlar") ve net kazanç / günlük hedef (N4, N8).
    MSET_TITLE("Harita ayarları", "Xəritə tənzimləmələri"),
    MSET_OPEN("Harita görünümü, harita verisi, Google Maps", "Xəritənin görünüşü, xəritə məlumatları, Google Maps"),
    OSET_TITLE("Siparişler ayarları", "Sifariş ayarları"),
    OSET_OPEN("Komisyon, ekran, net kazanç ve günlük hedef", "Komissiya, ekran, xalis qazanc və gündəlik hədəf"),
    OSET_COMMISSION("Filo komisyonları", "Filo komissiyaları"),
    OSET_COMMISSION_NOTE("Teklif fiyatı brüt sayılır; net = brüt − komisyon + bahşiş. Komisyon girilmezse 0 sayılır.", "Təklif qiyməti ümumi sayılır; xalis = ümumi − komissiya + çaypulu. Komissiya daxil edilməsə 0 sayılır."),
    OSET_COMMISSION_ASK("%s komisyonu (yüzde, 0–99,99)", "%s komissiyası (faiz, 0–99,99)"),
    OSET_NO_PLATFORM("İzlenen Wolt ya da Bolt uygulaması yok", "İzlənən Wolt və ya Bolt tətbiqi yoxdur"),
    OSET_SCREEN("Ekran ve kartlar", "Ekran və kartlar"),
    OSET_OVERLAY("Ekran üstü panel görünür", "Ekran üstündəki panel görünür"),
    OSET_RANGE("Menzil uyarısı", "Məsafə xəbərdarlığı"),
    OSET_RANGE_NOTE("Teklif sonrası batarya eve yetmeyebilirse panelde tek satır ve haritada \"eve dön?\" kartı. Bırakış noktası kartta Plus Code ya da koordinatla görünüyorsa ve ev kayıtlıysa çalışır. Kabul yine sende.",
        "Təklifdən sonra batareya evə çatmaya bilərsə paneldə bir sətir və xəritədə \"evə qayıdaq?\" kartı. Çatdırılma nöqtəsi kartda Plus Code və ya koordinatla görünürsə və ev qeyd olunubsa işləyir. Qəbul yenə səndədir."),
    OSET_OCR("Görünür ekran OCR yedeği", "Görünən ekran üçün OCR ehtiyatı"),
    OSET_CARD("Müşteri kartı (ekranın üstünde)", "Müştəri kartı (ekranın yuxarısında)"),
    OSET_SHADOW("Gölge mod (Rawstats ile yan yana)", "Kölgə rejimi (Rawstats ilə yanaşı)"),
    OSET_SHADOW_NOTE(
        "Açıkken WEEM nakit bahşiş ve cepteki nakit sorularını sormaz; rozet, balon, kart ve bildirim 12 çıkmaz. Ekran okuma ve kayıtlar sürer. Soruları yalnız Rawstats sorar; rapordaki bahşiş farkı \"beklenen\" sayılır. Yalnız .dev sürümünde görünür.",
        "Açıq olanda WEEM nağd çaypulu və cibdəki nağd suallarını vermir; nişan, balon, kart və bildiriş 12 çıxmır. Ekran oxuma və qeydlər davam edir. Sualları yalnız Rawstats verir; hesabatdakı çaypulu fərqi \"gözlənilən\" sayılır. Yalnız .dev versiyasında görünür.",
    ),
    OSET_BUBBLE("Restoran balonu genişliği", "Restoran balonunun eni"),
    OSET_BUBBLE_ASK("Restoran balonu (ekran genişliğinin yüzdesi)", "Restoran balonu (ekran eninin faizi)"),
    OSET_CARD_SIZE("Müşteri kartı boyutu", "Müştəri kartının ölçüsü"),
    OSET_CARD_SIZE_ASK("Müşteri kartı boyutu (eski kartın yüzdesi)", "Müştəri kartının ölçüsü (köhnə kartın faizi)"),
    OSET_APPS("İzlenen uygulamalar", "İzlənən tətbiqlər"),
    OSET_APPS_NONE("Seçili uygulama yok", "Seçilmiş tətbiq yoxdur"),
    OSET_APPS_PICK("Uygulamaları seç", "Tətbiqləri seç"),
    OSET_NET("Net kazanç ve günlük hedef", "Xalis qazanc və gündəlik hədəf"),
    OSET_NET_NOTE(
        "Net = brüt + bahşiş − filo komisyonu − enerji maliyeti. Enerji maliyeti = ölçülen Wh × kWh fiyatı ÷ şarj verimi. kWh fiyatı girilmezse ya da enerji ölçülmediyse maliyet hesaba katılmaz ve raporda yazılır.",
        "Xalis = ümumi + çaypulu − filo komissiyası − enerji xərci. Enerji xərci = ölçülən Wh × kWh qiyməti ÷ şarj səmərəsi. kWh qiyməti daxil edilməsə və ya enerji ölçülməsə xərc hesaba alınmır və hesabatda yazılır.",
    ),
    OSET_KWH("kWh fiyatı (₼)", "kWh qiyməti (₼)"),
    OSET_EFFICIENCY("Şarj verimi (yüzde)", "Şarj səmərəsi (faiz)"),
    OSET_GOAL("Günlük net hedef (₼)", "Gündəlik xalis hədəf (₼)"),
    OSET_REPORT_CHANNEL("Gün sonu raporu", "Gün sonu hesabatı"),
    OSET_UNSET("girilmedi", "daxil edilməyib"),
    OSET_DEFAULT("%s (varsayılan)", "%s (susmaya görə)"),
    OSET_EMPTY_CLEARS("Boş bırakıp kaydedersen değer silinir.", "Boş buraxıb yadda saxlasan dəyər silinir."),
    OSET_INVALID("Geçersiz değer: %s ile %s arasında bir sayı yaz.", "Yanlış dəyər: %s ilə %s arasında bir rəqəm yaz."),
}

val Tab.label: Text
    get() = when (this) {
        Tab.MAP -> Text.TAB_MAP
        Tab.ORDERS -> Text.TAB_ORDERS
        Tab.SCOOTER -> Text.TAB_SCOOTER
        Tab.REMOTE -> Text.TAB_REMOTE
        Tab.MORE -> Text.TAB_MORE
    }
