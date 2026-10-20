package com.kuryelog.logic.link

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang

/** 2 telefon modunun metinleri (LINK.md 4.10). AZ metinler onay bekler (LINK.md 4.12). */
enum class LinkText(override val tr: String, override val az: String) : Bilingual {
    TITLE("2 telefon modu", "2 telefon rejimi"),
    OPEN("2 telefon modunu aç", "2 telefon rejimini aç"),
    ROLE_TITLE("Bu telefonun rolü", "Bu telefonun rolu"),
    ROLE_OFF("Kapalı (tek telefon)", "Qapalı (tək telefon)"),
    ROLE_PROVIDER("Provider — Wolt/Bolt telefonu", "Provider — Wolt/Bolt telefonu"),
    ROLE_RECEIVER("Receiver — scooter telefonu", "Receiver — skuter telefonu"),
    HINT_PROVIDER("Bu telefonda WEEM scooter'a bağlanmaz; scooter verisi ve rota Receiver'dadır.", "Bu telefonda WEEM skuterə qoşulmur; skuter məlumatı və marşrut Receiver-dədir."),
    HINT_RECEIVER("Siparişler ve kazanç Provider'dan gelir; bu telefonda ekran okunmaz.", "Sifarişlər və qazanc Provider-dən gəlir; bu telefonda ekran oxunmur."),
    HINT_HOTSPOT("Hotspot hangi telefondaysa orada \"Hotspot'u otomatik kapat\" kapalı olmalı.", "Hotspot hansı telefondadırsa orada \"Hotspot-u avtomatik söndür\" qapalı olmalıdır."),

    STATUS_OFF("Bağlantı kapalı", "Bağlantı qapalıdır"),
    STATUS_UNPAIRED("Eşleşme yok: QR ile eşleştirin", "Cütləşmə yoxdur: QR ilə cütləşdirin"),
    STATUS_SEARCHING("Öteki telefon aranıyor", "Digər telefon axtarılır"),
    STATUS_LINKED_WIFI("Bağlı · Wi-Fi", "Qoşulub · Wi-Fi"),
    STATUS_LINKED_BT("Bağlı · Bluetooth", "Qoşulub · Bluetooth"),
    STATUS_RTT("%d ms", "%d ms"),
    NO_LAN("Yerel ağ yok (hotspot kapalı)", "Yerli şəbəkə yoxdur (hotspot qapalıdır)"),
    NO_BT("Bluetooth yedeği yok", "Bluetooth ehtiyatı yoxdur"),
    NO_BT_PHONE("Bluetooth'ta eşleşmiş telefon yok", "Bluetooth-da cütləşmiş telefon yoxdur"),
    ISSUE_VERSION("Öteki telefondaki WEEM uyumsuz (%s); ikisini de güncelleyin", "Digər telefondakı WEEM uyğun deyil (%s); hər ikisini yeniləyin"),
    ISSUE_VERSION_UNKNOWN("Öteki telefondaki WEEM uyumsuz; ikisini de güncelleyin", "Digər telefondakı WEEM uyğun deyil; hər ikisini yeniləyin"),
    ISSUE_AUTH("Öteki telefon tanınmadı: yeniden eşleştirin", "Digər telefon tanınmadı: yenidən cütləşdirin"),
    ISSUE_PAIRING("Eşleştirme modu uyuşmuyor: iki telefonda da yeniden başlatın", "Cütləşdirmə rejimi uyğun gəlmir: hər iki telefonda yenidən başladın"),
    CONFLICT("İki telefon da %s rolünde: birini değiştirin", "Hər iki telefon %s rolundadır: birini dəyişin"),
    APP_VERSION("Öteki telefonda WEEM %s, bu telefonda %s", "Digər telefonda WEEM %s, bu telefonda %s"),

    THIS_PHONE("Bu telefon", "Bu telefon"),
    PEER("Eşleşmiş telefon", "Cütləşmiş telefon"),
    FINGERPRINT("Parmak izi: %s", "Barmaq izi: %s"),
    PAIR_SHOW("Eşleştirme QR'ı göster", "Cütləşdirmə QR-ını göstər"),
    PAIR_SCAN("Öteki telefonun QR'ını tara", "Digər telefonun QR-ını skan et"),
    PAIR_HINT("Öteki telefonda \"QR'ı tara\"ya basın. QR 5 dakika geçerli; iki ekrandaki parmak izi aynı olmalı.", "Digər telefonda \"QR-ı skan et\"ə basın. QR 5 dəqiqə etibarlıdır; iki ekrandakı barmaq izi eyni olmalıdır."),
    PAIR_WAIT("Eşleşme bekleniyor · %s", "Cütləşmə gözlənilir · %s"),
    PAIR_CANCEL("Vazgeç", "İmtina et"),
    PAIRED("Eşleşti: %s", "Cütləşdi: %s"),
    PAIR_EXPIRED("Eşleştirme süresi doldu", "Cütləşdirmə müddəti bitdi"),
    QR_INVALID("Bu bir WEEM eşleştirme QR'ı değil", "Bu WEEM cütləşdirmə QR-ı deyil"),
    QR_NEWER("QR daha yeni bir WEEM'den: bu telefonda WEEM'i güncelleyin", "QR daha yeni WEEM-dəndir: bu telefonda WEEM-i yeniləyin"),
    SCAN_FAILED("QR okunamadı", "QR oxunmadı"),
    UNPAIR("Eşleşmeyi kaldır", "Cütləşməni sil"),
    UNPAIRED_BY_PEER("Öteki telefon eşleşmeyi kaldırdı", "Digər telefon cütləşməni sildi"),
    IDENTITY_FAILED("Bu telefonun bağlantı anahtarı açılamadı; sonra yeniden deneyin", "Bu telefonun bağlantı açarı açılmadı; sonra yenidən cəhd edin"),

    SERVICE_CHANNEL("2 telefon bağlantısı", "2 telefon bağlantısı"),

    REMOTE_FROM_PROVIDER("Provider telefonundan", "Provider telefonundan"),
    REMOTE_NOT_LINKED("Provider'a bağlı değil: son bilinen değerler", "Provider-ə qoşulmayıb: son məlum dəyərlər"),
    REMOTE_SHIFT_ON("Vardiya açık", "Növbə açıqdır"),
    REMOTE_SHIFT_OFF("Vardiya kapalı", "Növbə qapalıdır"),
    REMOTE_OFFER("Teklif: %s", "Təklif: %s"),
    REMOTE_TRIPS("Açık sipariş: %d", "Açıq sifariş: %d"),
    REMOTE_DAY("Bugün %d sipariş · %s ₼ net", "Bu gün %d sifariş · %s ₼ xalis"),
    REMOTE_NO_DATA("Henüz veri gelmedi", "Hələ məlumat gəlməyib"),
    REMOTE_SCOOTER("Scooter Receiver telefonunda", "Skuter Receiver telefonundadır"),
    REMOTE_BATTERY("Batarya %s", "Batareya %s"),
    REMOTE_EXTERNAL("harici %s", "xarici %s"),
    REMOTE_RANGE("menzil %s km", "məsafə %s km"),
    REMOTE_SCOOTER_OFF("Receiver'da scooter bağlı değil", "Receiver-də skuter qoşulmayıb"),
}

/** Durum satırları (saf; ekran ve bildirim aynısını gösterir). */
object LinkView {
    fun role(r: LinkRole, lang: Lang): String = when (r) {
        LinkRole.OFF -> LinkText.ROLE_OFF
        LinkRole.PROVIDER -> LinkText.ROLE_PROVIDER
        LinkRole.RECEIVER -> LinkText.ROLE_RECEIVER
    }.get(lang)

    /**
     * Ana durum satırı + ayrıntılar: bağlantı, ret nedeni (3.4, 3.10), rol çakışması, eksik taşıma, uygulama sürümü farkı.
     * [paired] false ise yalnız "eşleşme yok".
     */
    fun lines(s: LinkStatus, paired: Boolean, role: LinkRole, peerRole: LinkRole?, peer: PeerInfo?, myVersion: String, lang: Lang): List<String> {
        if (!paired) return listOf(LinkText.STATUS_UNPAIRED.get(lang))
        val out = ArrayList<String>()
        when (s.state) {
            LinkStatus.State.OFF -> out += LinkText.STATUS_OFF.get(lang)
            LinkStatus.State.SEARCHING -> {
                out += LinkText.STATUS_SEARCHING.get(lang)
                when (s.issue) {
                    LinkIssue.VERSION -> out += peer?.appName?.let { LinkText.ISSUE_VERSION.get(lang).format(it) } ?: LinkText.ISSUE_VERSION_UNKNOWN.get(lang)
                    LinkIssue.AUTH -> out += LinkText.ISSUE_AUTH.get(lang)
                    LinkIssue.PAIRING -> out += LinkText.ISSUE_PAIRING.get(lang)
                    null -> {}
                }
                if (!s.lan) out += LinkText.NO_LAN.get(lang)
                if (!s.btOn) out += LinkText.NO_BT.get(lang) else if (!s.btPhones) out += LinkText.NO_BT_PHONE.get(lang)
            }
            LinkStatus.State.LINKED -> {
                val head = (if (s.transport == Transport.BT) LinkText.STATUS_LINKED_BT else LinkText.STATUS_LINKED_WIFI).get(lang)
                out += s.rttMs?.let { "$head · " + LinkText.STATUS_RTT.get(lang).format(it) } ?: head
            }
        }
        if (role != LinkRole.OFF && peerRole == role) out += LinkText.CONFLICT.get(lang).format(role.key.replaceFirstChar { it.uppercase() })
        if (peer != null && peer.appName != myVersion) out += LinkText.APP_VERSION.get(lang).format(peer.appName, myVersion)
        return out
    }

    /** Eşleştirme penceresinin kalan süresi `m:ss`. */
    fun remaining(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
