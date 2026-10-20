package com.kuryelog.logic.map.ui

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang

/** İki dilli metni biçimler (`%s`, `%d`). */
fun Bilingual.fmt(lang: Lang, vararg args: Any?): String = String.format(get(lang), *args)

/** İstatistik ekranı (Y17; MAP.md 12.7, 12.9) metinleri; gün silme onayı M-12-3. */
enum class StatsText(override val tr: String, override val az: String) : Bilingual {
    TITLE("İstatistik", "Statistika"),
    REFRESH("Yenile", "Yenilə"),
    DELETE_DAY("Bu günü sil", "Bu günü sil"),
    TOTAL_TIME("toplam süre", "ümumi vaxt"),
    KM("km", "km"),
    MOVING("hareket", "hərəkət"),
    DELIVERED_OF("teslim / sipariş", "çatdırılıb / sifariş"),
    REST_WAIT("restoranda bekleme", "restoranda gözləmə"),
    DOOR_WAIT("kapıda bekleme", "qapıda gözləmə"),
    HOME_KM("eve dönüş km", "evə qayıdış km"),
    HOME_TIME("eve dönüş süresi", "evə qayıdış vaxtı"),
    LIGHTS_HEADER("TRAFİK IŞIKLARI", "İŞIQFORLAR"),
    LIGHT_STOPS("ışıkta durma", "işıqforda dayanma"),
    LIGHT_TOTAL("toplam bekleme", "cəmi gözləmə"),
    LIGHT_AVG_MAX("ortalama / en uzun", "orta / ən uzun"),
    NO_LIGHTS("Bu gün trafik ışığında bekleme kaydedilmedi.", "Bu gün işıqforda gözləmə qeyd olunmayıb."),
    CAND_HEADER("EKSİK IŞIK KONTROLÜ", "ÇATIŞMAYAN İŞIQFORLAR"),
    CAND_INFO(
        "Bu duruşlar ışık olabilir. Onayladığın noktalar sonraki sürüşlerde hatırlanır. \"?\" işaretliler ışıkta 2 dk'dan uzun beklemeler: Onayla → ışık sayılır, Reddet → sayılmaz.",
        "Bu dayanmalar işıqfor ola bilər. Təsdiqlədiyin nöqtələr növbəti sürüşdə yadda qalır. \"?\" olanlar işıqforda 2 dəq-dən uzun gözləmələrdir: Təsdiqlə → sayılır, Rədd et → sayılmır.",
    ),
    CAND_LONG(" · ışıkta 2 dk'dan uzun", " · işıqforda 2 dəq-dən uzun"),
    CONFIRM_LONG("Onayla", "Təsdiqlə"),
    CONFIRM_LIGHT("Işık vardı", "İşıqfor var idi"),
    REJECT_LONG("Reddet", "Rədd et"),
    REJECT_LIGHT("Işık değildi", "İşıqfor deyildi"),
    ORDERS_HEADER("SİPARİŞLER", "SİFARİŞLƏR"),
    ORDER_REST("restoran", "restoran"),
    ORDER_DOOR("kapı", "qapı"),
    SHARE_DAY("Günü paylaş", "Günü paylaş"),
    SHARE_TEXT("WEEM gün özeti %s", "WEEM günün xülasəsi %s"),
    DELETE_TITLE("%s · günü sil", "%s · günü sil"),
    DELETE_BODY(
        "Bu tarihin harita kayıtları (sürüşler, gün özeti, olaylar, otomatik yol ölçümleri) silinir. Siparişler ve kazanç kayıtları, diğer günler ve elle kaydedilen yollar korunur.",
        "Bu tarixin xəritə qeydləri (sürüşlər, günün xülasəsi, hadisələr, avtomatik yol ölçmələri) silinir. Sifarişlər və qazanc qeydləri, digər günlər və əl ilə qeyd olunan yollar saxlanır.",
    ),
    DELETE_ACTIVE(
        "Haritanın aktif günü durdurulacak; vardiya sürer.",
        "Xəritənin aktiv günü dayandırılacaq; növbə davam edir.",
    ),
    CANCEL("Vazgeç", "Ləğv et"),
}

/** Sürüş kayıtları ekranı (Y18; MAP.md 12.10) metinleri. */
enum class RideLogText(override val tr: String, override val az: String) : Bilingual {
    TITLE("Sürüş kayıtları", "Sürüş tarixçəsi"),
    SUBTITLE("Günlere göre · ayrı sürüşler · kaydet ve paylaş", "Günlər üzrə · ayrı sürüşlər · saxla və paylaş"),
    REFRESH("Yenile", "Yenilə"),
    SAVE_ALL_HINT("Tüm günlüğü kaydet", "Bütün tarixçəni saxla"),
    SAVE_ALL("Tüm günlüğü tek dosyada kaydet", "Bütün tarixçəni bir faylda saxla"),
    SHARE_ALL("Tüm günlüğü paylaş", "Bütün tarixçəni paylaş"),
    SWITCH_TITLE("Sürüş kaydı", "Sürüş qeydi"),
    SWITCH_SUB("GPS, yükseklik ve olaylar telefonda saklanır.", "GPS, hündürlük və hadisələr telefonda saxlanılır."),
    INFO(
        "Gün ve sipariş başlangıcı veya yeni rota ayrı sürüş açar; varışta kapanır. Günlük dosya tüm hareketleri içerir. Devam eden kaydı silmek için önce kaydı durdur.",
        "Günə və sifarişə başlayanda, yaxud yeni marşrut seçəndə ayrıca sürüş açılır; ünvana çatanda bitir. Gündəlik faylda bütün hərəkətlər saxlanılır. Aktiv qeydi silmək üçün əvvəlcə qeydi dayandır.",
    ),
    DAY_SUBTITLE("%d sürüş · %d kayıt", "%d sürüş · %d qeyd"),
    DAY_MENU_HINT("Günün tamamını kaydet", "Bütün günü saxla"),
    DAY_SAVE("Günün tamamını kaydet", "Bütün günü saxla"),
    DAY_SHARE("Günün tamamını paylaş", "Bütün günü paylaş"),
    DAY_DELETE("Günün tamamını sil", "Bütün günü sil"),
    KIND_ROUTE("Rota · plan, iz ve sapmalar", "Marşrut · plan, iz və kənara çıxmalar"),
    KIND_RIDE("Sürüş", "Sürüş"),
    KIND_REPORT("Gün özeti", "Günün xülasəsi"),
    KIND_DAY("Günlük hareket kaydı", "Gündəlik hərəkət qeydi"),
    ACTIVE_SUFFIX(" · kayıt sürüyor", " · qeyd davam edir"),
    SAVE("Telefona kaydet", "Telefonda saxla"),
    SHARE("Paylaş", "Paylaş"),
    DELETE_ONE("Bu kaydı sil", "Bu qeydi sil"),
    EMPTY("Henüz sürüş kaydı yok", "Hələ sürüş qeydi yoxdur"),
    READ_ERROR("Kayıtlar okunamadı", "Qeydlər açılmadı"),
    ACTION_ERROR("İşlem tamamlanamadı. Tekrar dene.", "Əməliyyat tamamlanmadı. Yenidən cəhd et."),
    SAVED("Dosya kaydedildi", "Fayl saxlanıldı"),
    SHARE_TEXT("WEEM sürüş kaydı", "WEEM sürüş qeydi"),
    DELETE_DAY_TITLE("%s · Günü sil?", "%s · Günü silmək istəyirsən?"),
    DELETE_DAY_ACTIVE("Bu gün aktif: kayıt ve bildirimler durdurulacak.\n\n", "Bu gün aktivdir: qeyd və bildirişlər dayandırılacaq.\n\n"),
    DELETE_DAY_BODY(
        "Bu tarihin harita kayıtları (sürüşler, gün özeti, olaylar, otomatik yol ölçümleri) silinir. Siparişler ve kazanç kayıtları, diğer günler ve elle kaydedilen yollar korunur.",
        "Bu tarixin xəritə qeydləri (sürüşlər, günün xülasəsi, hadisələr, avtomatik yol ölçmələri) silinir. Sifarişlər və qazanc qeydləri, digər günlər və əl ilə qeyd olunan yollar saxlanır.",
    ),
    DELETE_DAY_CONFIRM("Günü sil", "Günü sil"),
    DELETE_ONE_TITLE("Bu kaydı sil?", "Bu qeydi silmək istəyirsən?"),
    DELETE_ONE_BODY("Yalnız bu kayıt silinir. Diğer sürüşler ve gün özeti korunur.", "Yalnız bu qeyd silinəcək. Digər sürüşlər və günün xülasəsi saxlanılacaq."),
    DELETE_ONE_CONFIRM("Sil", "Sil"),
    CANCEL("Vazgeç", "Ləğv et"),
}

/** Teklif veri seti ekranı (Y19; MAP.md 13.8–13.9; M-13-2) metinleri. */
enum class DatasetText(override val tr: String, override val az: String) : Bilingual {
    TITLE("Teklif veri seti", "Təklif məlumat dəsti"),
    REFRESH("Yenile", "Yenilə"),
    DESCRIPTION(
        "Vardiya açıkken teklifler görüldükleri anda kaydedilir. Paketlerde rota, yükseklik, mevcut hava bilgisi ve WEEM gün kayıtları bir araya gelir.",
        "Növbə açıq olanda təkliflər göründüyü anda qeyd olunur. Paketlərdə marşrut, yüksəklik, mövcud hava məlumatı və WEEM gün qeydləri bir araya gəlir.",
    ),
    COLLECT_TODAY("Günü topla", "Günü topla"),
    OTHER_DAY("Başka gün", "Başqa gün"),
    NO_DAYS("Henüz gün yok. Vardiya kaydıyla veya Günü topla ile başlayabilirsin.", "Hələ gün yoxdur. Növbə qeydi ilə və ya Günü topla ilə başlaya bilərsən."),
    OFFERS("Teklifler: Wolt %d · Bolt %d", "Təkliflər: Wolt %d · Bolt %d"),
    MATCH("Eşleşme: %s · Eşleşen %d", "Uyğunluq: %s · Uyğun gələn %d"),
    ONLY("Yalnız gün kayıtları %d · Yalnız canlı kayıt %d", "Yalnız gün qeydləri %d · Yalnız canlı qeyd %d"),
    ROUTE_RATIO("Rota km hesaplanan: %s", "Marşrut km hesablanan: %s"),
    EMPTY_RATIO("Boş alan oranı: %s", "Boş sahə nisbəti: %s"),
    SCOOTER_COVERAGE("Scooter kaydı kapsaması: %s", "Scooter qeydi əhatəsi: %s"),
    UNKNOWN("Bilinmiyor", "Məlum deyil"),
    COLLECTED("Gün kayıtları toplandı ve doğrulandı", "Gün qeydləri toplandı və doğrulandı"),
    INCOMPLETE("Gün kayıtları eksik — Günü topla ile yeniden dene", "Gün qeydləri natamam — Günü topla ilə yenidən yoxla"),
    MANUAL_DONE("Elle eklenenler tamam", "Əllə əlavə edilənlər tamdır"),
    MANUAL_MISSING("Eksik elle eklenenler: %s", "Çatışmayan əllə əlavələr: %s"),
    COLLECT("Günü topla", "Günü topla"),
    MANUAL("Elle ekle", "Əllə əlavə et"),
    SHARE("Paylaş", "Paylaş"),
    ERROR_ACTION(
        "İşlem tamamlanamadı. Boş alanı kontrol et. Gün eksik olarak işaretlendi; tekrar toplayabilirsin.",
        "Əməliyyat tamamlanmadı. Boş yeri yoxla. Gün natamam kimi qeyd olundu; yenidən toplaya bilərsən.",
    ),
    ERROR_LIST("Gün listesi okunamadı. Tekrar dene.", "Gün siyahısı oxunmadı. Yenidən cəhd et."),
    SHARE_TEXT("WEEM teklif veri seti · %s", "WEEM təklif məlumat dəsti · %s"),
    WOLT_EARNINGS("Wolt kazanç ekranı", "Wolt qazanc ekranı"),
    BOLT_EARNINGS("Bolt kazanç ekranı", "Bolt qazanc ekranı"),
    WOLT_RATING("Wolt rating ekranı", "Wolt reytinq ekranı"),
    BOLT_RATING("Bolt rating ekranı", "Bolt reytinq ekranı"),
    WOLT_ACCEPTANCE("Wolt kabul oranı ekranı", "Wolt qəbul nisbəti ekranı"),
    BOLT_ACCEPTANCE("Bolt kabul oranı ekranı", "Bolt qəbul nisbəti ekranı"),
    WOLT_COMPLETION("Wolt tamamlama ekranı", "Wolt tamamlama ekranı"),
    BOLT_COMPLETION("Bolt tamamlama ekranı", "Bolt tamamlama ekranı"),
    WEEKLY_PAYOUT("Haftalık ödeme", "Həftəlik ödəniş"),
    SHIFT_NOTE("Vardiya notu", "Növbə qeydi"),
    ;

    companion object {
        /** `manual.jsonl` türünün etiketi; bilinmeyen tür olduğu gibi. */
        fun manualLabel(kind: String, lang: Lang): String = values().firstOrNull { it.name.lowercase() == kind }?.get(lang) ?: kind
    }
}

/** Günlük işler ve uyarı kartı (Y20; MAP.md 13.14) metinleri. */
enum class TaskText(override val tr: String, override val az: String) : Bilingual {
    TITLE("Günlük işler", "Gündəlik işlər"),
    INFO(
        "Seçtiğin saatten sonra, teslimat bittiğinde 1 km içindeki işler hatırlatılır. Kapatınca tamamlanmaz; sonraki teslimatta veya bölgeye yeniden girince tekrar gösterilir.",
        "Seçdiyin saatdan sonra, sifarişi çatdıranda 1 km yaxınlıqdakı işlər xatırladılır. Bağlamaq işi bitirmir; növbəti çatdırılmada və ya yenidən yaxınlaşanda göstərilir.",
    ),
    EMPTY("Henüz iş eklenmedi", "Hələ iş əlavə edilməyib"),
    PENDING("Bekliyor", "Gözləyir"),
    DONE("Tamamlandı · rota açıldı", "Tamamlandı · marşrut açıldı"),
    DELETE("Sil", "Sil"),
    DELETE_TITLE("Bu işi sil?", "Bu işi silmək istəyirsən?"),
    ADD("İş ekle", "İş əlavə et"),
    CANCEL("Vazgeç", "Ləğv et"),
    ALERT_TITLE("Yakınında yapılacak iş var", "Yaxınlıqda işin var"),
    ALERT_NEAR("%d m yakınında", "%d m yaxınlıqdadır"),
    ALERT_ROUTE("Rota Oluştur", "Marşrut qur"),
    ALERT_CLOSE("Tamam / Kapat", "Oldu / Bağla"),
    ALERT_NOTE("Kapatırsan iş beklemeye devam eder.", "Bağlasan, iş gözləməyə davam edəcək."),
    ROUTE_FAILED("Rota açılamadı; iş bekliyor.", "Marşrut açılmadı; iş gözləyir."),
    SAVE_FAILED("İş tamamlanmış olarak kaydedilemedi.", "İş tamamlanmış kimi saxlanılmadı."),
}

/** Hatırlatıcı kartı (Y20; MAP.md 13.16) metinleri. */
enum class ReminderText(override val tr: String, override val az: String) : Bilingual {
    DONE("Tamam", "Oldu"),
}
