package com.kuryelog.logic.remote

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang

/** Uzaktan kullanım metinleri (REMOTE.md 1.6). AZ metinler onay bekler (M-R-1). */
enum class RemoteText(override val tr: String, override val az: String) : Bilingual {
    VIEW_TITLE("Öteki telefonun ekranı", "Digər telefonun ekranı"),
    VIEW_START("Göster", "Göstər"),
    VIEW_STOP("Kapat", "Bağla"),
    SHARE_TITLE("Bu telefonun ekranı", "Bu telefonun ekranı"),
    SHARE_START("Paylaşımı aç", "Paylaşımı aç"),
    SHARE_STOP("Paylaşımı bitir", "Paylaşımı bitir"),
    NEED_LINK("Önce 2 telefon modunu açıp öteki telefonla eşleştirin (Daha fazla → 2 telefon modu).", "Əvvəlcə 2 telefon rejimini açıb digər telefonla cütləşdirin (Daha çox → 2 telefon rejimi)."),
    NOT_LINKED("Öteki telefon bağlı değil", "Digər telefon qoşulmayıb"),
    ONLY_WIFI("Yalnız Wi-Fi: bağlantı şu an Bluetooth'ta", "Yalnız Wi-Fi: bağlantı hazırda Bluetooth-dadır"),
    V_ASKING("Öteki telefona soruluyor…", "Digər telefondan soruşulur…"),
    V_CONSENT("Öteki telefonda izin bekleniyor: bildirime dokunup \"Başlat\" deyin", "Digər telefonda icazə gözlənilir: bildirişə toxunub \"Başlat\" deyin"),
    V_CONNECTING("Bağlanıyor…", "Qoşulur…"),
    V_PLAYING("%d×%d · %d kare/sn · %d kb/sn", "%d×%d · %d kadr/san · %d kb/san"),
    END_REFUSED("Öteki telefonda izin verilmedi", "Digər telefonda icazə verilmədi"),
    END_UNAVAILABLE("Öteki telefon ekranı paylaşamıyor", "Digər telefon ekranı paylaşa bilmir"),
    END_NO_ANSWER("Öteki telefon yanıt vermiyor", "Digər telefon cavab vermir"),
    END_NO_WIFI("Yalnız Wi-Fi ile çalışır", "Yalnız Wi-Fi ilə işləyir"),
    END_HOST("Öteki telefon paylaşımı bitirdi", "Digər telefon paylaşımı bitirdi"),
    END_LOST("Bağlantı koptu", "Bağlantı kəsildi"),
    H_OFF("Paylaşım kapalı", "Paylaşım qapalıdır"),
    H_CONSENT("İzin bekleniyor", "İcazə gözlənilir"),
    H_SHARING("Ekran paylaşılıyor", "Ekran paylaşılır"),
    H_WATCHED("Ekran paylaşılıyor · öteki telefon izliyor · %d kb/sn", "Ekran paylaşılır · digər telefon izləyir · %d kb/san"),
    HINT("Görüntü yalnız eşleşmiş telefona, şifreli gider; kaydedilmez. Paylaşım izleyensiz 10 dk sonra kendiliğinden kapanır.", "Görüntü yalnız cütləşmiş telefona, şifrəli gedir; yazılmır. Paylaşım izləyən olmadan 10 dəq sonra özü bağlanır."),
    CHANNEL("Uzaktan kullanım", "Uzaqdan istifadə"),
    ASK_TITLE("Öteki telefon bu ekranı görmek istiyor", "Digər telefon bu ekranı görmək istəyir"),
    ASK_TEXT("Dokunun: izin verin", "Toxunun: icazə verin"),
    NOTIF_STOP("Bitir", "Bitir"),
    N_WATCHED("Ekran paylaşılıyor · öteki telefon izliyor", "Ekran paylaşılır · digər telefon izləyir"),
}

/** Görünüm → metin (saf; `:app` ekranı ve bildirimler kullanır). */
object RemoteLines {
    fun viewer(v: ViewerView, lang: Lang): String? = when (v.state) {
        ViewerView.State.IDLE -> null
        ViewerView.State.ASKING -> RemoteText.V_ASKING.get(lang)
        ViewerView.State.CONSENT -> RemoteText.V_CONSENT.get(lang)
        ViewerView.State.CONNECTING -> RemoteText.V_CONNECTING.get(lang)
        ViewerView.State.PLAYING -> RemoteText.V_PLAYING.get(lang).format(v.width, v.height, v.fps, v.kbps)
        ViewerView.State.ENDED -> when (v.ended) {
            ViewerView.EndReason.REFUSED -> RemoteText.END_REFUSED
            ViewerView.EndReason.UNAVAILABLE -> RemoteText.END_UNAVAILABLE
            ViewerView.EndReason.NO_ANSWER -> RemoteText.END_NO_ANSWER
            ViewerView.EndReason.NO_WIFI -> RemoteText.END_NO_WIFI
            ViewerView.EndReason.HOST -> RemoteText.END_HOST
            ViewerView.EndReason.LOST, null -> RemoteText.END_LOST
        }.get(lang)
    }

    fun host(v: HostView, lang: Lang): String = when (v.state) {
        HostView.State.OFF -> RemoteText.H_OFF.get(lang)
        HostView.State.CONSENT -> RemoteText.H_CONSENT.get(lang)
        HostView.State.SHARING -> if (v.watched) RemoteText.H_WATCHED.get(lang).format(v.kbps) else RemoteText.H_SHARING.get(lang)
    }

    /** İzleyen ekranındaki "Göster" düğmesi açık mı (oturum yokken). */
    fun canView(v: ViewerView) = v.state == ViewerView.State.IDLE || v.state == ViewerView.State.ENDED
}
