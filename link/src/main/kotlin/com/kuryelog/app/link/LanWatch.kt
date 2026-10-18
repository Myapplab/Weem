package com.kuryelog.app.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.kuryelog.logic.link.LanTargets
import com.kuryelog.logic.link.LinkEvent
import com.kuryelog.logic.link.NetIf
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Yerel ağ (LINK.md 1.2): Wi-Fi istemci ağı (ağ geçidi = hotspot sahibi, soket bağlama) ve telefondaki IPv4 arayüzleri
 * (hotspot sahibinde `ap0`/`swlan0`/`wlan1`). Kararlar `logic/link` `LanTargets`'ta; burada yalnız Android okuması.
 */
internal class LanWatch(context: Context) {
    private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    @Volatile private var wifi: Network? = null

    @Volatile private var wifiProps: LinkProperties? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Wi-Fi ağı gelince, gidince ya da adresi değişince [onChange] çağrılır (ağ iş parçacığında). */
    fun start(onChange: () -> Unit) {
        val m = cm ?: return
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifi = network
                wifiProps = runCatching { m.getLinkProperties(network) }.getOrNull()
                onChange()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network == wifi) {
                    wifiProps = linkProperties
                    onChange()
                }
            }

            override fun onLost(network: Network) {
                if (network == wifi) {
                    wifi = null
                    wifiProps = null
                    onChange()
                }
            }
        }
        // Hotspot'un interneti olmayabilir: İNTERNET yeteneği aranmaz.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            m.registerNetworkCallback(request, cb)
            callback = cb
        } catch (e: RuntimeException) {
            callback = null
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        runCatching { cm?.unregisterNetworkCallback(cb) }
        wifi = null
        wifiProps = null
    }

    /** Makineye giden yerel ağ özeti: uygun arayüz var mı, Wi-Fi istemcisiysen ağ geçidi. */
    fun lan(): LinkEvent.Lan {
        val present = LanTargets.beaconTargets(interfaces()).isNotEmpty()
        val gateway = wifiProps?.routes.orEmpty()
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        return LinkEvent.Lan(present, if (present) gateway else null)
    }

    fun interfaces(): List<NetIf> = try {
        val all = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        all.flatMap { ni ->
            val up = runCatching { ni.isUp }.getOrDefault(false)
            val loopback = runCatching { ni.isLoopback }.getOrDefault(false)
            ni.interfaceAddresses.orEmpty().mapNotNull { ia ->
                val a = ia.address as? Inet4Address ?: return@mapNotNull null
                val text = a.hostAddress ?: return@mapNotNull null
                NetIf(ni.name, text, ia.networkPrefixLength.toInt(), up, loopback)
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** İşareti her uygun arayüzün yayın adresine yollar; bir hedefin hatası ötekileri durdurmaz. */
    fun broadcast(bytes: ByteArray, port: Int) {
        for (target in LanTargets.beaconTargets(interfaces())) {
            try {
                DatagramSocket().use { s ->
                    s.broadcast = true
                    if (inWifiSubnet(target)) wifi?.bindSocket(s)
                    s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(target), port))
                }
            } catch (e: Exception) {
                // Arayüz o an kapanmış olabilir; sonraki işaret yeniden dener.
            }
        }
    }

    /** Hedef Wi-Fi istemci ağındaysa soket o ağa bağlanır (varsayılan ağ mobil veri olsa da yerel ağa gitsin). */
    fun bindIfWifi(s: Socket, host: String) {
        val n = wifi ?: return
        if (inWifiSubnet(host)) n.bindSocket(s)
    }

    private fun inWifiSubnet(host: String): Boolean {
        val lp = wifiProps ?: return false
        return lp.linkAddresses.any { la ->
            val a = la.address as? Inet4Address ?: return@any false
            val text = a.hostAddress ?: return@any false
            LanTargets.sameSubnet(host, text, la.prefixLength)
        }
    }
}
