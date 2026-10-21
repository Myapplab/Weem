package com.kuryelog.logic.link

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.link.TheftGuard.ArmedBy
import com.kuryelog.logic.link.TheftGuard.State
import com.kuryelog.logic.link.TheftGuard.Trigger

/** Hırsızlık koruması metinleri (LINK.md 5.4). AZ metinler onay bekler (M-L-1 gibi). */
enum class TheftText(override val tr: String, override val az: String) : Bilingual {
    CHANNEL("Hırsızlık koruması", "Oğurluq qoruması"),
    TITLE("Hırsızlık koruması", "Oğurluq qoruması"),
    ENABLE("Hırsızlık korumasını aç", "Oğurluq qorumasını aç"),
    HINT("Scooter telefonunda (Receiver) çalışır. WEEM scooter'ı kilitleyemez: hareketi algılar, alarm çalar.", "Skuter telefonunda (Receiver) işləyir. WEEM skuteri kilidləyə bilmir: hərəkəti aşkar edir, siqnal çalır."),
    AUTO_STAGE("Restoranda ve müşteride kendiliğinden aç", "Restoranda və müştəridə avtomatik aç"),
    AUTO_AWAY("Pixel uzaklaşınca (bağlantı 60 sn yok) kendiliğinden aç", "Pixel uzaqlaşanda (bağlantı 60 san yoxdur) avtomatik aç"),
    ONLY_TWO_PHONE("Yalnız 2 telefon modunda çalışır", "Yalnız 2 telefon rejimində işləyir"),

    STATE_OFF("Kapalı", "Qapalı"),
    STATE_ARMING("Açılıyor · %s", "Açılır · %s"),
    STATE_ARMED("Açık", "Açıqdır"),
    STATE_WARNING("UYARI: scooter hareket ediyor", "XƏBƏRDARLIQ: skuter hərəkət edir"),
    STATE_ALARM("ALARM: scooter hareket etti", "SİQNAL: skuter hərəkət etdi"),
    BY_STAGE("restoran/müşteri", "restoran/müştəri"),
    BY_AWAY("Pixel uzak", "Pixel uzaqdır"),
    BY_MANUAL("Park", "Park"),
    TRIG_SPEED("tekerlek döndü", "təkər fırlandı"),
    TRIG_MOTION("telefon sarsıldı", "telefon silkələndi"),
    TRIG_GPS("yer değişti", "yer dəyişdi"),
    LAST_FIX("Son konum: %s önce (±%d m)", "Son yer: %s əvvəl (±%d m)"),
    OPEN_MAP("Haritada aç", "Xəritədə aç"),
    NO_STATE("Scooter telefonundan bilgi yok", "Skuter telefonundan məlumat yoxdur"),

    PARK("Park", "Park"),
    DISARM("Kapat", "Söndür"),
    CANCEL("İptal", "Ləğv et"),
    SILENCE("Sesi kapat", "Səsi söndür"),
    SENT("Gönderildi", "Göndərildi"),
    NOT_SENT("Gönderilemedi: bağlantı yok", "Göndərilmədi: bağlantı yoxdur"),

    PIN("Kapatma PIN'i (4 hane)", "Söndürmə PIN-i (4 rəqəm)"),
    PIN_SET("PIN ayarla", "PIN təyin et"),
    PIN_IS_SET("PIN ayarlı", "PIN təyin edilib"),
    PIN_NONE("PIN yok: alarm 3 sn basılı tutularak kapanır", "PIN yoxdur: siqnal 3 san basıb saxlamaqla söndürülür"),
    PIN_ENTER("PIN girin", "PIN daxil edin"),
    PIN_WRONG("PIN yanlış", "PIN səhvdir"),
    HOLD("3 sn basılı tutun", "3 san basıb saxlayın"),
    SMS_TITLE("Alarmda konum SMS'i", "Siqnalda yer SMS-i"),
    SMS_HINT("Bağlantı koparsa bu telefon (SIM gerekir) numaralara konum yollar. Numara yalnız bu telefonda saklanır.", "Bağlantı kəsilsə bu telefon (SIM lazımdır) nömrələrə yer göndərir. Nömrə yalnız bu telefonda saxlanır."),
    SMS_NUMBER("Numara %d", "Nömrə %d"),
    SMS_SAVE("Kaydet", "Yadda saxla"),
    SMS_PERMISSION("SMS izni ver", "SMS icazəsi ver"),
    SMS_NO_PERMISSION("SMS izni yok (Xiaomi'de \"Servis SMS'i\" iznini de açın)", "SMS icazəsi yoxdur (Xiaomi-də \"Xidmət SMS-i\" icazəsini də açın)"),
    SMS_FAILED("SMS gönderilemedi", "SMS göndərilmədi"),
    TEST_SIREN("Siren dene (3 sn)", "Sireni yoxla (3 san)"),
    OVERLAY_WATCHED("Bu scooter izleniyor", "Bu skuter izlənilir"),
    ;

    companion object {
        fun state(s: TheftGuard.Status, lang: Lang, nowMs: Long, armingMs: Long = TheftGuard.Config().armingMs): String {
            val base = when (s.state) {
                State.OFF -> STATE_OFF.get(lang)
                State.ARMING -> STATE_ARMING.get(lang).format(ago((armingMs - (nowMs - s.sinceMs)).coerceAtLeast(0) + 999, lang))
                State.ARMED -> STATE_ARMED.get(lang)
                State.WARNING -> STATE_WARNING.get(lang)
                State.ALARM -> STATE_ALARM.get(lang)
            }
            val extra = listOfNotNull(
                when (s.armedBy) { ArmedBy.STAGE -> BY_STAGE; ArmedBy.AWAY -> BY_AWAY; ArmedBy.MANUAL -> BY_MANUAL; null -> null }?.get(lang)
                    ?.takeIf { s.state != State.OFF },
                when (s.trigger) { Trigger.SPEED -> TRIG_SPEED; Trigger.MOTION -> TRIG_MOTION; Trigger.GPS -> TRIG_GPS; null -> null }?.get(lang)
                    ?.takeIf { s.alarming },
            )
            return if (extra.isEmpty()) base else base + " · " + extra.joinToString(" · ")
        }

        /** Süre: "12 sn", "5 dk", "2 sa" (AZ: san, dəq, saat). */
        fun ago(ms: Long, lang: Lang): String {
            val s = (ms / 1000).coerceAtLeast(0)
            val az = lang == Lang.AZ
            return when {
                s < 60 -> "$s " + if (az) "san" else "sn"
                s < 3600 -> "${s / 60} " + if (az) "dəq" else "dk"
                else -> "${s / 3600} " + if (az) "saat" else "sa"
            }
        }

        fun mapUrl(f: TheftGuard.Fix): String = String.format(java.util.Locale.US, "https://maps.google.com/?q=%.6f,%.6f", f.lat, f.lon)
    }
}
