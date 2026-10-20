package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.Lang

/**
 * G3 (A7.2): bağlantı ve kayıt durum metinlerinin Azərbaycanca gösterimi. Bu metinler (`NinebotSession`, `BleLink`, `ScooterService`,
 * `ScooterTelemetry`) Sürüş Paneli'nden Türkçe gelir ve olay kayıtlarına da Türkçe yazılır (`ble_state`; kayıt içi dizgiler değişmez,
 * SCOOTER.md 15); bu yüzden kaynakta değil, **ekrana giderken** çevrilir (YolGo `trMsg` yöntemi): servis bildirimi, Scooter sekmesinin
 * uyarı satırı ve Scooter ayarları → Durum. Parçalar uzundan kısaya uygulanır; tabloda olmayan metin olduğu gibi kalır.
 */
object ScooterStatusText {
    fun localize(text: String, lang: Lang): String {
        if (lang != Lang.AZ || text.isEmpty()) return text
        var out = text
        for ((tr, az) in ordered) if (out.contains(tr)) out = out.replace(tr, az)
        return out
    }

    /** `ScooterTelemetry.measurements` satır adları (satır başında `\n<ad>:`; harici batarya satırında `] • <ad>:`). */
    val LABELS: Map<String, String> = linkedMapOf(
        "Pil" to "Batareya",
        "Menzil" to "Məsafə",
        "Toplam km" to "Ümumi km",
        "Araç sıcaklığı" to "Skuterin temperaturu",
        "Hız (aday)" to "Sürət (namizəd)",
        "Sürüş km (aday)" to "Sürüş km (namizəd)",
        "Sürüş sn (aday)" to "Sürüş san (namizəd)",
        "Voltaj" to "Gərginlik",
        "Akım (+ şarj)" to "Cərəyan (+ şarj)",
        "Reg 0x8a (kapasite?)" to "Reg 0x8a (tutum?)",
        "Yüzde" to "Faiz",
    )

    /** Tam cümleler ve sabit parçalar (sayılar, durum kodları ve sınıf adları aralarda kalır). */
    val PHRASES: Map<String, String> = linkedMapOf(
        // NinebotSession
        "Bluetooth cihaz adı okunamadı; GPS kaydı sürüyor" to "Bluetooth cihaz adı oxunmadı; GPS qeydi davam edir",
        "G3 başlangıç yanıtı yok; yalnız Nordic profile 0 denendi. Bağlantı ZIP'ini gönder" to
            "G3 başlanğıc cavabı yoxdur; yalnız Nordic profile 0 yoxlanıldı. Bağlantı ZIP-ini göndər",
        "G3 şifreli bağlantısı hazırlanıyor" to "G3 şifrəli bağlantısı hazırlanır",
        "G3 kimlik doğrulanıyor • deneme " to "G3 kimliyi yoxlanılır • cəhd ",
        "Scooter eşleştirme süresi doldu (300 sn pencere); GPS kaydı sürüyor. Kaydı durdurup ZIP'i gönder" to
            "Skuter cütləşmə vaxtı bitdi (300 san pəncərə); GPS qeydi davam edir. Qeydi dayandırıb ZIP-i göndər",
        "AUTH kabul edilmedi • eşleştirme onayı bekleniyor (" to "AUTH qəbul edilmədi • cütləşmə təsdiqi gözlənilir (",
        "G3 doğrulaması başarısız; mevcut anahtar korunuyor. GPS kaydı sürüyor" to "G3 yoxlaması alınmadı; mövcud açar qorunur. GPS qeydi davam edir",
        "PAIR veri yanıtının anlamı bilinmiyor • onay bekleniyor (" to "PAIR məlumat cavabının mənası bilinmir • təsdiq gözlənilir (",
        "Scooter yanıt verdi • eşleştirme onayı bekleniyor (" to "Skuter cavab verdi • cütləşmə təsdiqi gözlənilir (",
        "). Güç düğmesine bir kez kısa bas" to "). Güc düyməsinə bir dəfə qısa bas",
        "G3 cihaz kimliği beklenen biçimde değil" to "G3 cihaz kimliyi gözlənilən formada deyil",
        "Scooter güç düğmesine kısa bas • eşleştirme onayı bekleniyor (" to "Skuterin güc düyməsinə qısa bas • cütləşmə təsdiqi gözlənilir (",
        "G3 yanıt verdi • ölçümler için G3 ile eşleştir düğmesini kullan" to "G3 cavab verdi • ölçmələr üçün G3 ilə cütləşdir düyməsindən istifadə et",
        "G3 bağlı • deneysel ölçüm okuma" to "G3 qoşulub • eksperimental ölçmə oxunur",
        "Ölçüm yanıtı kesildi; yeniden bağlanıyor" to "Ölçmə cavabı kəsildi; yenidən qoşulur",
        "Şifreli oturum yenileniyor" to "Şifrəli sessiya yenilənir",
        "G3 bağlı • harici batarya bulundu (kart " to "G3 qoşulub • xarici batareya tapıldı (kart ",
        "G3 bağlı • harici batarya bulunamadı" to "G3 qoşulub • xarici batareya tapılmadı",
        " sn)" to " san)",
        // ScooterTelemetry
        "Scooter ölçümü bekleniyor • GPS ayrı kaydedilir" to "Skuter ölçməsi gözlənilir • GPS ayrıca qeyd olunur",
        "G3 ölçümleri • sahada karşılaştırılmalı" to "G3 ölçmələri • sahədə müqayisə edilməlidir",
        "\nGüç: ölçülüyor…" to "\nGüc: ölçülür…",
        "\nGüç: " to "\nGüc: ",
        " W (V×A • doğrulanmamış)" to " W (V×A • yoxlanılmayıb)",
        "\nPil [" to "\nBatareya [",
        // BleLink
        "Bluetooth işlem zaman aşımı" to "Bluetooth əməliyyatının vaxtı bitdi",
        "G3 bağlantı başlangıcı: " to "G3 bağlantı başlanğıcı: ",
        "G3 protokol işlemi: " to "G3 protokol əməliyyatı: ",
        "Bluetooth kapalı; GPS kaydı sürüyor" to "Bluetooth bağlıdır; GPS qeydi davam edir",
        "Scooter'a bağlanıyor" to "Skuterə qoşulur",
        "Bluetooth bağlantısı başlatılamadı" to "Bluetooth bağlantısı başladılmadı",
        "BLE işlem hatası: " to "BLE əməliyyat xətası: ",
        "Bluetooth bildirim kanalı açılamadı; GPS sürüyor" to "Bluetooth bildiriş kanalı açılmadı; GPS davam edir",
        "Bildirim aboneliği başlatılamadı" to "Bildiriş abunəliyi başladılmadı",
        "Yerel anahtar açılamadı; Bluetooth durduruldu, GPS sürüyor. Kaydı durdurup G3 ile eşleştir veya Gelişmiş bağlantı anahtarı ile kurtar." to
            "Yerli açar açılmadı; Bluetooth dayandırıldı, GPS davam edir. Qeydi dayandırıb G3 ilə cütləşdir və ya Qabaqcıl bağlantı açarı ilə bərpa et.",
        "Eşleştirme süresi doldu; yeniden denemek için kaydı durdur" to "Cütləşmə vaxtı bitdi; yenidən cəhd etmək üçün qeydi dayandır",
        "Bağlantı koptu (" to "Bağlantı kəsildi (",
        "); GPS kaydı sürüyor" to "); GPS qeydi davam edir",
        "Servis keşfi başlatılamadı" to "Xidmət axtarışı başladılmadı",
        "Servis keşfi hatası " to "Xidmət axtarışı xətası ",
        "GATT kaydedildi; desteklenen kanal yok" to "GATT qeyd olundu; dəstəklənən kanal yoxdur",
        "Bildirim aboneliği hatası " to "Bildiriş abunəliyi xətası ",
        "BLE yazım hatası " to "BLE yazma xətası ",
        // ScooterService ve Scooter
        "Bluetooth bekleniyor" to "Bluetooth gözlənilir",
        "GPS kaydı • scooter seçilmedi" to "GPS qeydi • skuter seçilməyib",
        "Kayıt açık, ancak arka planda konum izni verilmedi; GPS uygulama açıkken kaydedilir" to
            "Qeyd açıqdır, amma arxa planda məkan icazəsi verilməyib; GPS tətbiq açıq olanda qeyd olunur",
        "Teşhis kuyruğu doldu: " to "Diaqnostika növbəsi doldu: ",
        " BLE ayrıntısı atlandı; GPS ve sürüş kaydı sürüyor" to " BLE detalı buraxıldı; GPS və sürüş qeydi davam edir",
        "GPS bekleniyor" to "GPS gözlənilir",
        " sn önce" to " san əvvəl",
        "KAYIT AÇIK • " to "QEYD AÇIQDIR • ",
        " dk\n" to " dəq\n",
        " ölçüm • BLE: " to " ölçmə • BLE: ",
        "WEEM vardiyası kaydediyor" to "WEEM növbəsi qeyd edir",
        "kayıt yok" to "qeyd yoxdur",
        "Scooter seçilmedi" to "Skuter seçilməyib",
        "Kayıt durdu: depolama veya servis hatası. Ayarlar bölümünü kontrol et." to "Qeyd dayandı: yaddaş və ya xidmət xətası. Ayarlar bölməsini yoxla.",
        "Kayıt durdu: " to "Qeyd dayandı: ",
        ". Kaydedilen veriler korunur." to ". Qeyd olunan məlumatlar qorunur.",
        "Canlı panel kapatıldı • kayıt yapılmadı" to "Canlı panel bağlandı • qeyd aparılmadı",
        "Kayıt tamamlandı • " to "Qeyd tamamlandı • ",
        "Kayıt açık" to "Qeyd açıqdır",
        "Kayıt kapalı" to "Qeyd bağlıdır",
    )

    private val ordered: List<Pair<String, String>> = (PHRASES.entries.map { it.key to it.value } +
        LABELS.entries.flatMap { (tr, az) -> listOf("\n$tr:" to "\n$az:", "] • $tr:" to "] • $az:") })
        .sortedByDescending { it.first.length }
}
