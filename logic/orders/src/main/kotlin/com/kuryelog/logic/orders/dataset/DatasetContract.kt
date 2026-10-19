package com.kuryelog.logic.orders.dataset

import org.json.JSONObject
import java.time.ZoneId

/**
 * Teklif veri seti satır sözleşmesi (`DatasetContract.kt`, MAP.md 13.3; yoklama sabitleri hariç): yalnız güvenli alanlar, kimlik korunur (müşteri adı, telefon, adres, not
 * yok; alış 3 ondalık, bırakış null), ölçüler `unavailable`. Gün sınırı telefon saat diliminde ([ZoneId], MAP.md 13.5; Bakü'de sonuç aynı). İskelet (A6.2b): gövdeler A6.9'da.
 */
object DatasetContract {
    /** Aynı tekliften iki satır arasında en az bu kadar süre (çekirdek iz değişmedikçe). */
    const val ROW_GAP_MS = 5_000L

    /**
     * Rawstats teklif nesnesi → satır; güvensiz kimlik (`offer-[0-9]+-[0-9]+` değil), bilinmeyen platform, eksik/ters zaman → null. [courier] kurye noktası (gözlenmiş)
     * ya da null; [region] ("baku" …) satırdaki bölge etiketi.
     */
    fun offer(input: JSONObject, wallMs: Long, monoNs: Long, courier: JSONObject?, region: String?): JSONObject? = TODO("A6.9")

    /** Geçerli koordinat (3 ondalığa yuvarlanmaz; satıra yazılırken yuvarlanır); geçersizse null. */
    fun point(lat: Double, lon: Double): JSONObject? = TODO("A6.9")

    /** Metindeki koordinat ya da Plus Code; Plus Code yalnız gözlenmiş [reference] ile çözülür, varsayılan yok. */
    fun coordinate(text: String, reference: JSONObject?): JSONObject? = TODO("A6.9")

    /** Satır − `wall_ms`, `mono_ns`, `recordId`, ölçü alanları. */
    fun changedFingerprint(row: JSONObject): String = TODO("A6.9")

    /** `changedFingerprint` − `lastSeenAtMs`, `courier`. */
    fun coreFingerprint(row: JSONObject): String = TODO("A6.9")

    /** İç içe null alanlara `field_sources` bayrağı (`unavailable`); değer uydurulmaz. */
    fun flagUnknowns(row: JSONObject): JSONObject = TODO("A6.9")

    /** Okuma ölçüsü: `receivedAtMs`, `rawstatsChangedAtMs`, `feedDelayMs`, `readTrigger`; eski Rawstats'ta gecikme null + `unavailable_old_rawstats`. */
    fun measure(row: JSONObject, receivedAtMs: Long, changedAtMs: Long?, trigger: String): JSONObject = TODO("A6.9")

    fun changedAt(feed: JSONObject): Long? = TODO("A6.9")

    /** Takvim günü `YYYY-MM-DD`, [zone] saat diliminde. */
    fun day(wallMs: Long, zone: ZoneId): String = TODO("A6.9")

    /** `2026-10-10_Cumartesi` (gün adı her zaman Türkçe). */
    fun folder(day: String): String = TODO("A6.9")
}
