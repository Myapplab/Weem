package com.kuryelog.logic.app

import com.kuryelog.logic.core.Bilingual

/**
 * G5 ilk açılış kurulumu (A7.2): dil → izinler → harita verisi → pil rehberi. Bir kez gösterilir ([PREF_KEY]); Daha fazla → İzinler
 * aynı izin kartını ve pil rehberini her zaman gösterir. Kurulum hiçbir izni zorunlu tutmaz: "İleri" her adımda açık.
 */
enum class SetupStep {
    LANGUAGE, PERMISSIONS, MAP, BATTERY;

    val title: SetupText
        get() = when (this) {
            LANGUAGE -> SetupText.STEP_LANGUAGE
            PERMISSIONS -> SetupText.STEP_PERMISSIONS
            MAP -> SetupText.STEP_MAP
            BATTERY -> SetupText.STEP_BATTERY
        }

    val isLast: Boolean get() = this == entries.last()
    fun next(): SetupStep = entries.getOrElse(ordinal + 1) { this }
    fun prev(): SetupStep = entries.getOrElse(ordinal - 1) { this }

    companion object {
        /** `kuryelog` Bool (yeni, A7.2): kurulum bitti ya da atlandı. Şifreli yedeğe girmez (yeni telefonda kurulum yeniden gösterilir). */
        const val PREF_KEY = "weem.setupDone"

        fun fromOrdinal(i: Int): SetupStep = entries.getOrElse(i) { LANGUAGE }
    }
}

/** Android'den okunan izin durumu (her `onResume`'da yeniden). [bluetoothNeeded]: Android 12+ ve scooter seçili (RS kuralı). */
data class PermissionState(
    val accessibility: Boolean,
    val notificationListener: Boolean,
    val location: Boolean,
    val backgroundLocation: Boolean,
    val bluetoothNeeded: Boolean = false,
    val bluetooth: Boolean = true,
    val batteryUnrestricted: Boolean = false,
    val homeSet: Boolean = false,
)

enum class PermKind(val label: SetupText) {
    ACCESSIBILITY(SetupText.PERM_ACC),
    NOTIFICATION_LISTENER(SetupText.PERM_NOTIF),
    LOCATION(SetupText.PERM_LOC),
    BACKGROUND_LOCATION(SetupText.PERM_BGLOC),
    BLUETOOTH(SetupText.PERM_BT),
}

data class PermRow(val kind: PermKind, val ok: Boolean)

/** İzinler kartı (RS `MainActivity.kt:1188-1210`, `ORDERS.md` 13.7): sıra, sayaç ve açık gelme kuralı Rawstats'la aynı. */
object Permissions {
    fun rows(s: PermissionState): List<PermRow> = listOfNotNull(
        PermRow(PermKind.ACCESSIBILITY, s.accessibility),
        PermRow(PermKind.NOTIFICATION_LISTENER, s.notificationListener),
        PermRow(PermKind.LOCATION, s.location),
        PermRow(PermKind.BACKGROUND_LOCATION, s.backgroundLocation),
        if (s.bluetoothNeeded) PermRow(PermKind.BLUETOOTH, s.bluetooth) else null,
    )

    fun allOk(s: PermissionState): Boolean = rows(s).all { it.ok }

    /** Başlık rozeti: hepsi tamamsa "✓", değilse "! verilen/toplam" (kırmızı). Pil satırı sayılmaz (isteğe bağlı). */
    fun badge(s: PermissionState): String = rows(s).let { r -> if (r.all { it.ok }) "✓" else "! ${r.count { it.ok }}/${r.size}" }

    /** Eksik izin varken kart açık gelir; kullanıcı açıp kapadıysa onun seçimi ([userOpen]). */
    fun startsOpen(s: PermissionState, userOpen: Boolean?): Boolean = userOpen ?: !allOk(s)

    /** Ev ayarlı ama "her zaman" konumu yok: otomatik vardiya için açıklama satırı. */
    fun backgroundNote(s: PermissionState): Boolean = s.homeSet && !s.backgroundLocation

    fun batteryText(s: PermissionState): SetupText = if (s.batteryUnrestricted) SetupText.BATTERY_FREE else SetupText.BATTERY_RESTRICTED
    fun batteryBadge(s: PermissionState): SetupText? = if (s.batteryUnrestricted) null else SetupText.OPTIONAL
}

/** Pil rehberinin telefon ailesi (`Build.MANUFACTURER` / `Build.BRAND`). Xiaomi, Redmi ve POCO aynı MIUI/HyperOS ayarlarını kullanır. */
enum class Vendor {
    XIAOMI, PIXEL, OTHER;

    companion object {
        private val xiaomi = setOf("xiaomi", "redmi", "poco")

        fun of(manufacturer: String?, brand: String?): Vendor {
            val m = manufacturer.orEmpty().trim().lowercase()
            val b = brand.orEmpty().trim().lowercase()
            return when {
                m in xiaomi || b in xiaomi -> XIAOMI
                m == "google" || b == "google" -> PIXEL
                else -> OTHER
            }
        }
    }
}

/** Pil rehberinin düğmeleri: Android ayar sayfaları (`:app` intent'e çevirir; açılamazsa uygulama bilgisi). */
enum class BatteryAction(val label: SetupText) {
    AUTOSTART(SetupText.OPEN_AUTOSTART),
    APP_INFO(SetupText.OPEN_APP_INFO),
    BATTERY_LIST(SetupText.OPEN_BATTERY_LIST),
}

/**
 * Pil rehberi (G5): vardiya ön plan servisiyle çalışır, Android çoğu telefonda onu açık tutar; rehber isteğe bağlıdır ve ayrı bildirim
 * atmaz (G4, RS 0.22.2 kararı). Xiaomi/HyperOS ön plan servisini de durdurabildiği için otomatik başlatma ve "Kısıtlama yok" ister.
 */
object BatteryGuide {
    fun title(v: Vendor): SetupText = when (v) {
        Vendor.XIAOMI -> SetupText.GUIDE_XIAOMI_TITLE
        Vendor.PIXEL -> SetupText.GUIDE_PIXEL_TITLE
        Vendor.OTHER -> SetupText.GUIDE_OTHER_TITLE
    }

    fun steps(v: Vendor): List<SetupText> = when (v) {
        Vendor.XIAOMI -> listOf(SetupText.GUIDE_XIAOMI_1, SetupText.GUIDE_XIAOMI_2, SetupText.GUIDE_XIAOMI_3, SetupText.GUIDE_XIAOMI_4)
        Vendor.PIXEL -> listOf(SetupText.GUIDE_PIXEL_1, SetupText.GUIDE_PIXEL_2)
        Vendor.OTHER -> listOf(SetupText.GUIDE_OTHER_1, SetupText.GUIDE_OTHER_2)
    }

    fun actions(v: Vendor): List<BatteryAction> = when (v) {
        Vendor.XIAOMI -> listOf(BatteryAction.AUTOSTART, BatteryAction.APP_INFO)
        Vendor.PIXEL -> listOf(BatteryAction.APP_INFO)
        Vendor.OTHER -> listOf(BatteryAction.APP_INFO, BatteryAction.BATTERY_LIST)
    }
}

/** Kurulum ve izin metinleri (iki dil yan yana; `TextCoverageTest` bunları da denetler). İzin ve pil metinleri Rawstats'tan, ad "WEEM". */
enum class SetupText(override val tr: String, override val az: String) : Bilingual {
    WELCOME_TITLE("WEEM'e hoş geldin", "WEEM-ə xoş gəldin"),
    WELCOME_TEXT("Harita, siparişler ve scooter tek uygulamada. Birkaç adımda hazırlayalım; her adımı sonra Daha fazla bölümünden değiştirebilirsin.",
        "Xəritə, sifarişlər və skuter bir tətbiqdə. Bir neçə addımda hazırlayaq; hər addımı sonra Daha çox bölməsindən dəyişə bilərsən."),
    STEP_COUNT("Adım %d/%d", "Addım %d/%d"),
    STEP_LANGUAGE("Dil", "Dil"),
    STEP_PERMISSIONS("İzinler", "İcazələr"),
    STEP_MAP("Harita verisi", "Xəritə məlumatı"),
    STEP_BATTERY("Pil ayarları", "Batareya ayarları"),
    NEXT("İleri", "İrəli"),
    PREV("Geri", "Geri"),
    FINISH("WEEM'i aç", "WEEM-i aç"),
    SKIP("Kurulumu atla", "Quraşdırmanı keç"),
    PERMISSIONS_TEXT("Ekran okuma Wolt/Bolt siparişlerini kaydetmek, konum vardiya ve navigasyon için gerekir. Eksik izinle de devam edebilirsin; kart kırmızı kalır.",
        "Ekran oxuma Wolt/Bolt sifarişlərini qeyd etmək, məkan isə növbə və naviqasiya üçün lazımdır. Çatışmayan icazə ilə də davam edə bilərsən; kart qırmızı qalır."),
    MAP_TEXT("Navigasyon için Bakü yol haritası telefona indirilir. İndirme arka planda sürer; şimdi başlatıp ilerleyebilir ya da sonra Harita sekmesinden indirebilirsin.",
        "Naviqasiya üçün Bakı yol xəritəsi telefona yüklənir. Yükləmə arxa planda davam edir; indi başladıb irəliləyə və ya sonra Xəritə bölməsindən yükləyə bilərsən."),
    MAP_READY("✓ Harita hazır", "✓ Xəritə hazırdır"),
    BATTERY_TEXT("İsteğe bağlı. Vardiya ön plan servisiyle çalışır; Android otomatik başlatmayı reddederse tek dokunuşla açılan bir bildirim gelir.",
        "İstəyə bağlı. Növbə ön plan xidməti ilə işləyir; Android avtomatik başlatmanı rədd etsə, bir toxunuşla açılan bildiriş gəlir."),

    PERMS_TITLE("İzinler", "İcazələr"),
    PERM_ACC("Ekran okuma (Erişilebilirlik)", "Ekran oxuma (Əlçatanlıq)"),
    PERM_NOTIF("Bildirim okuma", "Bildiriş oxuma"),
    PERM_NOTIF_NOTE("Bildirim okuma izni, navigasyon bildirimindeki kalan rota mesafesini okumak için de kullanılır (kaydedilmez).",
        "Bildiriş oxuma icazəsi naviqasiya bildirişindəki qalan marşrut məsafəsini oxumaq üçün də istifadə olunur (yazılmır)."),
    PERM_LOC("Konum + bildirim", "Məkan + bildiriş"),
    PERM_BGLOC("Konum: her zaman (isteğe bağlı)", "Məkan: həmişə (istəyə bağlı)"),
    PERM_BT("Scooter Bluetooth izni", "Skuter Bluetooth icazəsi"),
    PERM_BGLOC_HOME("Ev konumu ayarlı: evden çıkınca vardiyanın kendiliğinden başlaması için \"her zaman\" konum izni gerekir. İzin verilmezse vardiya yalnız elle başlar.",
        "Ev məkanı təyin olunub: evdən çıxanda növbənin özü başlaması üçün \"həmişə\" məkan icazəsi lazımdır. İcazə verilməsə, növbə yalnız əl ilə başlayır."),
    BATTERY_FREE("Pil optimizasyonu: KAPALI · uygulama kısıtlanmıyor", "Batareya optimallaşdırması: BAĞLI · tətbiq məhdudlaşdırılmır"),
    BATTERY_RESTRICTED("Pil optimizasyonu: AÇIK · Android uygulamayı kısıtlayabilir", "Batareya optimallaşdırması: AÇIQ · Android tətbiqi məhdudlaşdıra bilər"),
    OPTIONAL("isteğe bağlı", "istəyə bağlı"),
    BATTERY_HELP("Bu durum Android'in WEEM için bildirdiği pil optimizasyonu durumudur. Telefonun ek uyku/arka plan ayarları ayrı olabilir. Uygulama bilgisi → Pil bölümünden kontrol et.",
        "Bu, Android-in WEEM üçün bildirdiyi batareya optimallaşdırmasıdır. Telefonun əlavə yuxu/arxa plan ayarları fərqli ola bilər. Tətbiq məlumatı → Batareya bölməsində yoxla."),
    RESTRICTED_TITLE("Kısıtlı ayarlar / Allow restricted settings", "Məhdud ayarlar / Allow restricted settings"),
    RESTRICTED_HELP("Aşağıdaki düğme WEEM'in uygulama bilgisi sayfasını açar. Sağ üstte ⋮ → Allow restricted settings / Kısıtlı ayarlara izin ver seçeneğini kendin aç. Ardından ekran ve bildirim okuma izinlerini ver. Seçenek her telefonda görünmeyebilir.",
        "Düymə WEEM-in tətbiq məlumatını açır. Sağ yuxarıda ⋮ → Allow restricted settings / Məhdud ayarlara icazə ver seçimini özün aç. Sonra ekran və bildiriş oxuma icazəsini ver. Seçim hər telefonda görünməyə bilər."),
    OPEN_APP_INFO("Uygulama bilgisi", "Tətbiq məlumatı"),
    OPEN_ALL_APPS("Tüm uygulamalar", "Bütün tətbiqlər"),
    OPEN_AUTOSTART("Otomatik başlatma ayarı", "Avtomatik başlatma ayarı"),
    OPEN_BATTERY_LIST("Pil optimizasyonu listesi", "Batareya optimallaşdırması siyahısı"),

    GUIDE_TITLE("Pil rehberi", "Batareya bələdçisi"),
    GUIDE_XIAOMI_TITLE("Xiaomi · Redmi · POCO (MIUI / HyperOS)", "Xiaomi · Redmi · POCO (MIUI / HyperOS)"),
    GUIDE_XIAOMI_1("Uygulama bilgisi → Otomatik başlatma: AÇ.", "Tətbiq məlumatı → Avtomatik başlatma: AÇ."),
    GUIDE_XIAOMI_2("Uygulama bilgisi → Pil tasarrufu → Kısıtlama yok.", "Tətbiq məlumatı → Batareyaya qənaət → Məhdudiyyət yoxdur."),
    GUIDE_XIAOMI_3("Uygulama bilgisi → Diğer izinler → Arka planda çalışırken açılır pencere göster: izin ver (balonlar ve yüzen panel için).",
        "Tətbiq məlumatı → Digər icazələr → Arxa planda işləyərkən açılan pəncərələri göstər: icazə ver (balonlar və üzən panel üçün)."),
    GUIDE_XIAOMI_4("Son uygulamalar ekranında WEEM'i basılı tutup kilitle (🔒); \"Tümünü temizle\" onu kapatmaz.",
        "Son tətbiqlər ekranında WEEM-i basıb saxla və kilidlə (🔒); \"Hamısını təmizlə\" onu bağlamır."),
    GUIDE_PIXEL_TITLE("Google Pixel", "Google Pixel"),
    GUIDE_PIXEL_1("Uygulama bilgisi → Uygulama pil kullanımı → Kısıtlanmamış (Unrestricted).",
        "Tətbiq məlumatı → Tətbiqin batareya istifadəsi → Məhdudiyyətsiz (Unrestricted)."),
    GUIDE_PIXEL_2("Vardiyada Pil Tasarrufu (Battery Saver) kapalı olsun; açıkken Android arka plandaki işleri kısabilir.",
        "Növbədə Batareya Qənaəti (Battery Saver) bağlı olsun; açıq olanda Android arxa plandakı işləri məhdudlaşdıra bilər."),
    GUIDE_OTHER_TITLE("Diğer telefonlar", "Digər telefonlar"),
    GUIDE_OTHER_1("Uygulama bilgisi → Pil → Kısıtlama yok / Optimize etme.", "Tətbiq məlumatı → Batareya → Məhdudiyyət yoxdur / Optimallaşdırma."),
    GUIDE_OTHER_2("Telefonun ek uyku ya da otomatik başlatma ayarı varsa WEEM'i dışarıda bırak.",
        "Telefonun əlavə yuxu və ya avtomatik başlatma ayarı varsa, WEEM-i istisna et."),
    GUIDE_NAMES_VARY("Menü adları telefona ve sürüme göre biraz farklı olabilir.", "Menyu adları telefona və versiyaya görə bir az fərqli ola bilər."),
}
