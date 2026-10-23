package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.Bilingual
import com.kuryelog.logic.core.Lang
import org.json.JSONObject
import java.util.Locale

/** `:scooter`'un ekrana/bildirime giden metinleri, iki dil yan yana (G3; RS `ScooterLink.kt`, `AutoStartNotice.kt`). */
enum class ScooterText(override val tr: String, override val az: String) : Bilingual {
    // M23 (SCOOTER.md 5.2): kanal `scooter_link`.
    LINK_CHANNEL("Scooter bağlantısı", "Skuter bağlantısı"),
    RECEIVING_TITLE("Scooter verileri alınıyor", "Skuter məlumatları alınır"),
    RECEIVING_TEXT("Bağlantı yeniden kuruldu, değerler geliyor.", "Bağlantı bərpa olundu, dəyərlər gəlir."),
    NOT_RECEIVING_TITLE("Scooter verileri alınamıyor", "Skuter məlumatları alınmır"),
    NOT_RECEIVING_TEXT("Sürüyorsun ama scooter'dan değer gelmiyor; Sürüş Paneli'ne bak.", "Sürürsən, amma skuterdən dəyər gəlmir; Sürüş Panelinə bax."),
    // A10.4f (S18 b): bağlantı boşta kaldı (`fail`); 60 sn sonra kendiliğinden yeniden denenir.
    DROPPED_TITLE("Scooter bağlantısı koptu", "Skuter bağlantısı kəsildi"),
    DROPPED_TEXT("60 sn sonra yeniden denenecek.", "60 san sonra yenidən cəhd ediləcək."),
    RETRY_NOW("Şimdi dene", "İndi yoxla"),

    // 5102 (vardiya bağlantısı reddedildi; kanal `auto_start`).
    NOT_CONNECTED_TITLE("Scooter bağlanmadı", "Skuter qoşulmadı"),
    NOT_CONNECTED_TEXT("Bağlanmak için dokun.", "Qoşulmaq üçün toxun."),

    // Ana kart satırı (SCOOTER.md 5.3).
    NO_SCOOTER("Scooter seçilmedi · Sürüş Paneli'nde ara ve seç", "Skuter seçilməyib · Sürüş Panelində axtar və seç"),
    NOT_PAIRED("Eşleştirilmedi · Sürüş Paneli'nde G3 ile eşleştir", "Cütləşdirilməyib · Sürüş Panelində G3 ilə cütləşdir"),
    NEEDS_PERMISSION("Bluetooth izni gerekli", "Bluetooth icazəsi lazımdır"),
    AUTO_OFF("Bağlı değil · vardiyada otomatik bağlantı kapalı", "Qoşulmayıb · növbədə avtomatik qoşulma söndürülüb"),
    NOT_CONNECTED("Bağlı değil", "Qoşulmayıb"),
    CONNECTS_AT_SHIFT("Vardiya başlayınca bağlanır", "Növbə başlayanda qoşulur"),
    WAITING_FOR_DATA("Bağlanıyor · scooter verisi bekleniyor", "Qoşulur · skuter məlumatı gözlənilir"),
    EXTERNAL("harici %", "xarici %"),
    THIS_ORDER("bu sipariş ", "bu sifariş "),
    CONNECT_NOW("Scooter'a şimdi bağlan", "Skuterə indi qoşul"),
    GIVE_PERMISSION("Scooter için Bluetooth izni ver", "Skuter üçün Bluetooth icazəsi ver"),
    PROVIDER_ROLE("Scooter, Receiver telefonuna bağlı", "Skuter Receiver telefonuna qoşulub"),
}

/**
 * Vardiya bağlantısı kararları (SCOOTER.md 5.3, 11.3; RS `ScooterLink.kt:86-219`): bir vardiya başlayınca scooter kaydı açılır mı
 * (S37'den beri canlı bağlantı değil, GPS'li Sürüş Paneli kaydı; vardiyayla başlar, vardiyayla biter), biterken kapanır mı, ana kartta ne yazar. Saf; Android tarafı olguları ([Facts]) toplar ve sonucu uygular. Her çağrı korumalı
 * olmalı: scooter hatası vardiyayı ve sipariş kaydını durdurmaz.
 */
object ScooterLinkRules {
    /** A live frame older than this does not tell the scooter's current battery (RS `ScooterLink.FRESH_MS`). */
    const val FRESH_MS = 15_000L

    /** `kuryelog` tercihi: vardiya başında scooter kaydını aç (varsayılan açık). */
    const val PREF_AUTO = "scooter.autoConnect"

    /** `kuryelog` tercihi: bu vardiya canlı bağlantıyı kendisi açtı (bitişte yalnız o kapatılır). */
    const val PREF_OWNED = "scooter.shiftLink"

    /** `events.jsonl` `type`. */
    const val EVENT_TYPE = "scooter_link"

    /** `events.jsonl` `scooter_link` `action` değerleri. */
    object Action {
        const val SKIP = "skip"
        const val START = "start"
        const val FAILED = "failed"
        /** S37: canlı bağlantı (eşleştirme sonrası) bağlantı kopmadan kayda çevrildi. */
        const val UPGRADE = "upgrade"
        /** S37: süren kayıt (yapışkan yeniden başlatma) vardiyaya bağlandı. */
        const val ADOPT = "adopt"
        const val STOP = "stop"
        const val STOP_FAILED = "stop_failed"
        const val DATA_BACK = "scooter_data_back"
        const val DATA_MISSING = "scooter_data_missing"
    }

    /** `skip` nedenleri (`reason`). `role_provider` WEEM eki (S11). */
    object Reason {
        const val AUTO_OFF = "auto_off"
        const val ROLE_PROVIDER = "role_provider"
        const val NO_SCOOTER = "no_scooter"
        const val NOT_PAIRED = "not_paired"
        const val NO_BLUETOOTH_PERMISSION = "no_bluetooth_permission"
        const val CHECK_FAILED = "check_failed"
        const val REFUSED = "refused"
    }

    /** Android'in topladığı olgular. [providerRole]: 2 telefon modunda Provider (S11; tek telefonda hep false); [recording]: bağlantı kayıtta. */
    data class Facts(
        val selected: Boolean, val paired: Boolean, val permitted: Boolean, val active: Boolean, val providerRole: Boolean = false, val recording: Boolean = false,
    )

    /** `events.jsonl` `scooter_link` satırının gövdesi (`type` ve zamanı kayıt katmanı ekler). */
    fun event(action: String, reason: String?): JSONObject = JSONObject().put("action", action).put("reason", reason ?: JSONObject.NULL)

    /** Vardiya başı (yapışkan yeniden başlatma dahil): bağlantı açılmayacaksa nedeni, açılacaksa null. */
    fun startSkip(autoConnect: Boolean, f: Facts): String? = if (!autoConnect) Reason.AUTO_OFF else openSkip(f)

    /**
     * `open` (vardiya içinde "şimdi bağlan" dahil): sırayla `role_provider`, `no_scooter`, `not_paired`, `no_bluetooth_permission`. S37: açık bir
     * bağlantı engel değil ([openAction] onu kayda çevirir ya da vardiyaya bağlar); izin yalnız yeni bağlantıda gerekir.
     */
    fun openSkip(f: Facts): String? = when {
        f.providerRole -> Reason.ROLE_PROVIDER
        !f.selected -> Reason.NO_SCOOTER
        !f.paired -> Reason.NOT_PAIRED
        !f.active && !f.permitted -> Reason.NO_BLUETOOTH_PERMISSION
        else -> null
    }

    /** S37: vardiya kaydı nasıl açılır — yeni kayıt, canlı bağlantıyı (eşleştirme sonrası) kopmadan kayda çevirme ya da süren kaydı sahiplenme. */
    enum class Open(val action: String) { RECORD(Action.START), UPGRADE(Action.UPGRADE), ADOPT(Action.ADOPT) }

    fun openAction(f: Facts): Open = when {
        !f.active -> Open.RECORD
        !f.recording -> Open.UPGRADE
        else -> Open.ADOPT
    }

    /** Ana kartta "Scooter'a şimdi bağlan": vardiya açık, kayıt yok, seçili + eşleşmiş + izinli (Provider'da değil). */
    fun canConnectNow(shiftOn: Boolean, f: Facts): Boolean = shiftOn && !(f.active && f.recording) && openSkip(f) == null

    /** Vardiya sonunda ne yapılır. */
    enum class End { NOTHING, STOP }

    /** S37: vardiyanın açtığı (ya da sahiplendiği) kayıt vardiyayla biter; [owned] değilse bağlantıya dokunulmaz. */
    fun shiftEnd(owned: Boolean, active: Boolean): End = if (owned && active) End.STOP else End.NOTHING

    /** Vardiya sonu olayı (`stop`); yapılacak bir şey yoksa null. */
    fun endAction(end: End): String? = when (end) {
        End.NOTHING -> null
        End.STOP -> Action.STOP
    }

    /** M23 bildirimi: (başlık, metin); bağlantı geri geldiyse 15 sn sonra kalkar, alınamıyorsa kalkmaz. */
    fun reconnectText(notice: ScooterReconnect.Notice, lang: Lang): Pair<String, String> = when (notice) {
        ScooterReconnect.Notice.RECEIVING -> ScooterText.RECEIVING_TITLE.get(lang) to ScooterText.RECEIVING_TEXT.get(lang)
        ScooterReconnect.Notice.NOT_RECEIVING -> ScooterText.NOT_RECEIVING_TITLE.get(lang) to ScooterText.NOT_RECEIVING_TEXT.get(lang)
    }

    /** M23 olay eylemi. */
    fun reconnectAction(notice: ScooterReconnect.Notice): String =
        if (notice == ScooterReconnect.Notice.RECEIVING) Action.DATA_BACK else Action.DATA_MISSING

    /** Ana kart satırı için canlı değerler: taze kare ve sayaç. */
    class Live(val frame: NinebotDashboard.Snapshot, val trip: TripMeter.Stats?)

    /** Ana kart satırı (RS `ScooterLink.status`): bağlantı durumu ya da canlı pil, hız, güç ve bu siparişin enerjisi. */
    fun status(f: Facts, autoConnect: Boolean, shiftOn: Boolean, live: Live?, lang: Lang): String {
        fun t(x: ScooterText) = x.get(lang)
        if (f.providerRole) return t(ScooterText.PROVIDER_ROLE)
        if (!f.selected) return t(ScooterText.NO_SCOOTER)
        if (!f.paired) return t(ScooterText.NOT_PAIRED)
        if (!f.active) return when {
            !f.permitted -> t(ScooterText.NEEDS_PERMISSION)
            !autoConnect -> t(ScooterText.AUTO_OFF)
            shiftOn -> t(ScooterText.NOT_CONNECTED)
            else -> t(ScooterText.CONNECTS_AT_SHIFT)
        }
        val fr = live?.frame ?: return t(ScooterText.WAITING_FOR_DATA)
        val parts = mutableListOf("%" + NinebotDashboard.whole(fr.batteryPct), NinebotDashboard.speed(fr.speedKmh) + " km/h", NinebotDashboard.watts(fr.powerW) + " W")
        fr.extBatteryPct?.let { parts.add(t(ScooterText.EXTERNAL) + NinebotDashboard.whole(it)) }
        val tr = live.trip
        if (tr != null && tr.label.isNotEmpty() && tr.wh != null) parts.add(t(ScooterText.THIS_ORDER) + String.format(Locale.US, "%.1f Wh", tr.wh))
        return parts.joinToString(" · ")
    }
}
