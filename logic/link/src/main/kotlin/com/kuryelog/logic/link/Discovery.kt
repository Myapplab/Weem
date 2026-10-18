package com.kuryelog.logic.link

import java.nio.ByteBuffer

/**
 * UDP işareti (LINK.md 1.2), 24 bayt: `WEEM` · sürüm 1 · tür 1 · kimlik 16 bayt · TCP portu (u16).
 * Yalnız bağlı değilken yayın adresine gider; alan taraf göndericinin adresine TCP ile bağlanır.
 */
object Beacon {
    const val SIZE = 24
    const val VERSION = 1
    private const val KIND_BEACON = 1

    class Parsed(val peer: PeerId, val tcpPort: Int)

    fun encode(self: PeerId, tcpPort: Int): ByteArray = ByteBuffer.allocate(SIZE)
        .put(Frames.MAGIC).put(VERSION.toByte()).put(KIND_BEACON.toByte()).put(self.bytes()).putShort(tcpPort.toShort())
        .array()

    /** Yabancı paket, başka sürüm ya da tür → `null`. */
    fun decode(b: ByteArray, length: Int = b.size): Parsed? {
        if (length != SIZE || b.size < SIZE) return null
        for (i in Frames.MAGIC.indices) if (b[i] != Frames.MAGIC[i]) return null
        if (b[4].toInt() != VERSION || b[5].toInt() != KIND_BEACON) return null
        val port = ByteBuffer.wrap(b, 22, 2).short.toInt() and 0xffff
        if (port == 0) return null
        return Parsed(PeerId.fromBytes(b, 6), port)
    }
}

/** Telefondaki bir ağ arayüzü (Android `NetworkInterface`): ad, IPv4 adresi, önek uzunluğu. */
data class NetIf(val name: String, val address: String, val prefix: Int, val up: Boolean = true, val loopback: Boolean = false)

/**
 * İşaretin gideceği yayın adresleri (LINK.md 1.2): hotspot sahibinde `ap0`/`swlan0`/`wlan1` …, istemcide `wlan0`.
 * Özel IPv4 aralığı; mobil veri, tünel ve sanal arayüzler hariç.
 */
object LanTargets {
    private val excluded = listOf(
        "rmnet", "ccmni", "v4-", "tun", "ppp", "wwan", "pdp", "seth", "clat", "dummy", "ifb", "ip6", "sit", "lo", "p2p",
        "ipsec", "epdg", "radio",
    )

    fun usable(i: NetIf): Boolean {
        if (!i.up || i.loopback) return false
        val n = i.name.lowercase()
        if (excluded.any { n.startsWith(it) }) return false
        if (i.prefix !in 8..30) return false
        val ip = parse(i.address) ?: return false
        return isPrivate(ip)
    }

    /** Arayüzün yerel yayın adresi (`192.168.43.1/24` → `192.168.43.255`); geçersizse `null`. */
    fun broadcast(address: String, prefix: Int): String? {
        val ip = parse(address) ?: return null
        if (prefix !in 0..32) return null
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        return format(ip or mask.inv())
    }

    /** İşaretin gönderileceği adresler (tekrarsız, sıralı). Yerel ağ var mı sorusunun yanıtı da budur: boşsa yok. */
    fun beaconTargets(ifs: List<NetIf>): List<String> =
        ifs.filter(::usable).mapNotNull { broadcast(it.address, it.prefix) }.distinct().sorted()

    fun isPrivate(ip: Int): Boolean {
        val a = (ip ushr 24) and 0xff
        val b = (ip ushr 16) and 0xff
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    fun parse(text: String): Int? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var v = 0
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255 || p.length > 3) return null
            v = (v shl 8) or n
        }
        return v
    }

    fun format(ip: Int) = "${(ip ushr 24) and 0xff}.${(ip ushr 16) and 0xff}.${(ip ushr 8) and 0xff}.${ip and 0xff}"
}

/** Android'de eşleşmiş Bluetooth cihazı: adres ve sınıfı telefon mu. */
data class BondedDevice(val address: String, val isPhone: Boolean)

/** Bluetooth hedefleri (LINK.md 1.3): öğrenilen eş adresi eşleşmişse yalnız o; yoksa eşleşmiş telefonlar, adres sırasıyla. */
object BtTargets {
    fun choose(bonded: List<BondedDevice>, learned: String?): List<String> {
        val norm = bonded.map { it.copy(address = it.address.uppercase()) }
        val l = learned?.uppercase()
        if (l != null && norm.any { it.address == l }) return listOf(l)
        return norm.filter { it.isPhone }.map { it.address }.distinct().sorted()
    }
}
