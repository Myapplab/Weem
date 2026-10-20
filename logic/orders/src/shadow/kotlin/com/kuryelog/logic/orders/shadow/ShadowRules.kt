package com.kuryelog.logic.orders.shadow

import com.kuryelog.logic.capture.NoteFields
import com.kuryelog.logic.orders.CashTips
import com.kuryelog.logic.orders.TripRecord

/**
 * Bilerek farklar (WEEM ≠ Rawstats; gölge mod "beklenen fark" sayar): ORDERS.md 14.3 ve 16, CAPTURE.md 9, CORE.md 3.3. Her kural
 * Rawstats değerini WEEM'in yazacağı biçime çevirir; çeviriden sonra eşitse fark beklenendir, değilse yumuşak ya da sert kalır.
 */
object ShadowRules {
    /**
     * A4.1'e kadar yalnız Rawstats'ın yazdığı ekonomi dosyaları (`OrderObserver.NONE`) ve A5'e kadar scooter dosyaları. A4.1'den sonra
     * WEEM de ekonomi dosyalarını yazar; satırları iki uygulamanın kendi teklif/sipariş kimlikleriyle olduğundan rapor içeriği
     * karşılaştırmaz, yalnız sayar ("iki tarafta" / "yalnız …"). Satır içeriği eşitliği `logic/economics` `SahaEconomicsTest`'te (ECONOMICS.md 5).
     */
    val notYetWritten = setOf("economics-offers.jsonl", "economics-legs.jsonl", "economics-idle.jsonl", "economics-penalties.jsonl",
        "economics-waits.jsonl", "economics-exposure.jsonl", "economics-energy.jsonl", "economics-offer-scores.jsonl", "economics-offer-ratings.jsonl",
        "economics-scooter.jsonl", "scooter.jsonl", "scooter_events.jsonl", "scooter_averages.jsonl")
    /** Kullanıcının cevapları (nakit bahşiş K32, cep nakdi M15): gölge modda soruyu yalnız bir uygulama sorar (ORDERS.md 16, soru 2). */
    val answers = setOf("economics-cash-tips.jsonl", "economics-pocket-cash.jsonl")

    /**
     * A3.11 (S16 (a)): gölge modda soruyu yalnız Rawstats sorar, bahşiş cevabı yalnız onun kaydındadır; siparişin ve günün neti
     * (net = … + bahşiş) o kadar farklıdır. Bahşişi kullanıcı yazmış sayılan kaynaklar (`tipSource`): nakit cevabı K32, hızlı düğme F4.6,
     * bahşiş düzenleyicisi.
     */
    val tipAnswerSources = setOf(CashTips.SOURCE, CashTips.SOURCE_QUICK, "user_manual")
    /** Siparişin netindeki kullanıcı cevabı olan bahşiş (yoksa 0). */
    fun answeredTip(t: TripRecord): Double = if (t.tipSource in tipAnswerSources) t.tip else 0.0

    /** Uygulamanın kendi paketleri (bildirim anahtarlarında). */
    val ownPackages = setOf("com.kuryelog.app", "com.kuryelog.app.dev")
    /** Rawstats bildirim numarasının WEEM karşılığı: ekran okuma uyarısı 5103 → 5104, nakit bahşiş 4000–8095 → 10000–14095 (CORE.md 3.3). */
    fun notificationId(rawstats: Int): Int = when (rawstats) {
        5103 -> 5104
        in 4000..8095 -> rawstats + 6000
        else -> rawstats
    }

    /**
     * Bildirim anahtarı `kullanıcı|paket|numara|etiket|uid` (Android `StatusBarNotification.getKey`). Uygulamanın kendi
     * bildiriminde paket `com.kuryelog.app` sayılır, numara WEEM'inkine çevrilir, uid atılır (iki paketin uid'i farklı).
     * [fromRawstats] false ise yalnız paket ve uid düzeltilir.
     */
    fun notificationKey(key: String, fromRawstats: Boolean): String {
        val p = key.split('|')
        if (p.size < 5 || p[1] !in ownPackages) return key
        val id = p[2].toIntOrNull()?.let { if (fromRawstats) notificationId(it) else it }?.toString() ?: p[2]
        return listOf(p[0], "com.kuryelog.app", id, p[3]).joinToString("|")
    }

    /** Kullanıcıya görünen metinde uygulama adı WEEM ("Rawstats", "KuryePlus" yerine), "(YolGo)" → "(WEEM)", YolGo hava metni (ORDERS.md 16). */
    fun renamed(text: String): String = text
        .replace("YolGo'dan hava gelmedi", "hava bilgisi gelmedi").replace("YolGo-dan hava gəlmədi", "hava məlumatı gəlmədi")
        .replace("(YolGo)", "(WEEM)").replace("Rawstats", "WEEM").replace("KuryePlus", "WEEM")

    /** `click.text`: Rawstats `redact` etmeden yazar, WEEM eder (CAPTURE.md 6.3). */
    fun clickText(rawstats: String): String = NoteFields.redact(rawstats) ?: rawstats

    /** YolGo kökenli değer (WEEM'in kendi navigasyonu A6'ya kadar yok): `nav_yolgo`, `gps_dwell_yolgo_target`, `gps_near_yolgo_target`, `yolgo_venue`. */
    fun yolgo(value: String?): Boolean = value != null && value.contains("yolgo", ignoreCase = true) && value != WEATHER_SOURCE
    const val WEATHER_SOURCE = "yolgo_open_meteo"
    /** YolGo'dan gelen olay türleri. */
    val yolgoEvents = setOf("yolgo_venue")
}
