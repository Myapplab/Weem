package com.kuryelog.logic.link

/**
 * 2 telefon modunda bu telefonun rolü (plan 4.5, LINK.md 4.1). Her telefon her rolü alabilir; kapalıyken tek telefon her şeyi yapar.
 * Saklanan anahtar [key] (`weem-link` → [PREF_KEY]); tanınmayan değer [OFF].
 */
enum class LinkRole(val key: String) {
    OFF("off"),
    /** Wolt/Bolt telefonu: ekran okuma, siparişler, kazanç, yedek. WEEM scooter'a hiç bağlanmaz (S11). */
    PROVIDER("provider"),
    /** Scooter'a sabit telefon: harita ve navigasyon, Sürüş Paneli, scooter'a tek Bluetooth bağlantısı. Ekran okuma yok. */
    RECEIVER("receiver");

    /** S11: Provider rolünde scooter BLE hiç açılmaz. */
    val scooterAllowed: Boolean get() = this != PROVIDER

    /** Scooter'a bağlı telefon Bluetooth yedeğinde yalnız bekler (LINK.md 1.3 `listenOnly`). */
    val listenOnly: Boolean get() = this == RECEIVER

    /** Bu rolün yolladığı türler ([LinkTopic.from]). */
    fun sends(t: LinkTopic): Boolean = t.from == this || t.from == null && this != OFF

    companion object {
        const val PREF_KEY = "role"
        fun of(key: String?): LinkRole = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/**
 * Mesaj türleri (plan 4.4 tablosu; LINK.md 4.2): zarf adı, sınıf, TTL ve gönderen rol. Adlar plan adlarıyla aynı.
 * [WeatherNow] yok (S6: her telefon havayı kendi internetinden alır); saniyelik `ScooterLive` yok (Receiver'dan çıkmaz).
 */
enum class LinkTopic(val type: String, val reliable: Boolean, val ttlMs: Long, val from: LinkRole?) {
    SHIFT("ShiftState", false, 24 * HOUR, LinkRole.PROVIDER),
    /** Ekrandaki tekliflerin tamamı tek yükte (son durum tür başına tek ileti; boş liste = teklif yok). */
    OFFERS("OfferSummary", false, 2 * MINUTE, LinkRole.PROVIDER),
    STAGES("TripStages", true, HOUR, LinkRole.PROVIDER),
    DAY("DaySummary", false, 24 * HOUR, LinkRole.PROVIDER),
    /** Ulaşamazsa Provider [PhoneBridge.NAV_FALLBACK_MS] sonra rotayı kendisi açar; geç gelen hedef Receiver'da açılmaz. */
    NAV_TARGET("NavTarget", true, 2 * MINUTE, LinkRole.PROVIDER),
    ENERGY_REQUEST("EnergyRequest", true, 24 * HOUR, LinkRole.PROVIDER),
    ENERGY_RESULT("EnergyResult", true, 24 * HOUR, LinkRole.RECEIVER),
    BATTERY("BatterySummary", false, 5 * MINUTE, LinkRole.RECEIVER),
    ROUTE("RouteStatus", false, MINUTE, LinkRole.RECEIVER),
    /** İki yönde: öteki telefonun rolü (iki Provider ya da iki Receiver uyarısı). */
    ROLE("LinkRole", false, 24 * HOUR, null);

    companion object {
        fun of(type: String): LinkTopic? = entries.firstOrNull { it.type == type }
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
