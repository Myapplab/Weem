package com.kuryelog.logic.link

/**
 * Taşıma sabitleri (LINK.md 1.2–1.6). Testler süreleri kısaltabilir; telefon varsayılanları kullanır.
 *
 * Süreler monoton ms. Bağlanma zaman aşımını soketi açan Android katmanı uygular; makine ayrıca
 * `connectTimeout + CONNECT_GUARD_MS` sonra kanalı kendisi bırakır (katman haber vermezse takılmasın).
 */
data class LinkConfig(
    val tcpPort: Int = TCP_PORT,
    val beaconPort: Int = BEACON_PORT,
    /** İşaret aralığı: arama başladıktan sonra [beaconFastForMs] boyunca hızlı, sonra yavaş. */
    val beaconFastMs: Long = 2_000,
    val beaconSlowMs: Long = 5_000,
    val beaconFastForMs: Long = 60_000,
    /** İşaretle öğrenilen adres bu süre yeni işaret gelmezse unutulur. */
    val beaconForgetMs: Long = 15_000,
    val helloTimeoutMs: Long = 5_000,
    val tcpConnectTimeoutMs: Long = 3_000,
    val btConnectTimeoutMs: Long = 8_000,
    val wifiPingMs: Long = 5_000,
    val wifiDeadMs: Long = 15_000,
    val btPingMs: Long = 10_000,
    val btDeadMs: Long = 30_000,
    /** Ağ değişince ya da öteki taşımada eş belirince açık kanal yoklanır: bu sürede yanıt yoksa ölü. */
    val probeMs: Long = 3_000,
    /** Bu süre açık kalan kanal koparsa hedef hemen yeniden denenir; daha kısa ömürlüyse geri çekilme sürer. */
    val stableMs: Long = 10_000,
    /** Yerel ağ var ama eş bulunamıyorsa Bluetooth'a geçmeden önce beklenen süre. */
    val btGraceMs: Long = 10_000,
    /** Wi-Fi kanalı canlıyken Bluetooth kanalının kapanmadan önce bekleme süresi. */
    val btDrainMs: Long = 3_000,
    val wifiMaxPayload: Int = 1 shl 20,
    val btMaxPayload: Int = 4 * 1024,
    val backoff: Backoff.Plan = Backoff.Plan(),
) {
    fun pingMs(t: Transport) = if (t == Transport.WIFI) wifiPingMs else btPingMs
    fun deadMs(t: Transport) = if (t == Transport.WIFI) wifiDeadMs else btDeadMs
    fun connectTimeoutMs(t: Transport) = if (t == Transport.WIFI) tcpConnectTimeoutMs else btConnectTimeoutMs
    fun maxPayload(t: Transport) = if (t == Transport.WIFI) wifiMaxPayload else btMaxPayload

    companion object {
        const val TCP_PORT = 47310
        const val BEACON_PORT = 47311
        /** RFCOMM hizmet kaydı (LINK.md 1.3); değişirse iki telefon birbirini bulamaz. */
        const val BT_SERVICE_UUID = "55a2849b-5970-4a15-a703-6e6dfd08f28f"
        const val BT_SERVICE_NAME = "WEEM link"
        const val CONNECT_GUARD_MS = 2_000L
    }
}

/** Kanalın taşıması. Wi-Fi = hotspot yerel ağı üstünden TCP; BT = klasik Bluetooth RFCOMM (S23). */
enum class Transport { WIFI, BT }
