package com.kuryelog.logic.economics

import com.kuryelog.logic.core.RangeBattery
import com.kuryelog.logic.core.RangeLeg
import com.kuryelog.logic.core.RangePort
import com.kuryelog.logic.orders.OfferState
import kotlinx.coroutines.runBlocking

/**
 * WEEM N5 menzil girdileri (A10.4e; MAP.md 10.6, kullanıcı kararı T1-1 a, S35): bir teklifin ekonomi v2 girdisine giren batarya ve
 * eve dönüş bacakları. Yeni hesap yok: v2'nin `BATTERY_ESCAPE_NOT_MET` kuralı bu girdilerle çalışır.
 *
 * Bırakış noktası yalnız kart metnindeki Plus Code ya da DMS'den ([OfferGeo.fromCardText]; ağ yok, adres hiçbir yere gitmez); yoksa ya da
 * ev kayıtlı değilse girdi yok (bugünkü "unknown" sürer). Rota hesabı ([RangePort.rideTo], telefondaki harita verisi) teklif karesini
 * bekletmesin diye [background] ile arka planda, teklif başına bir kez; sonuç bir sonraki karede değerlendirmeye girer ([key] değişir). Batarya
 * her karede taze okunur. İş parçacığı güvenli.
 */
class OfferRange(private val port: () -> RangePort, private val background: (() -> Unit) -> Unit) {
    /** Bırakış → ev ve konum → ev bacakları; [toHome] null = rota bulunamadı. */
    class Legs(val toHome: RangeLeg?, val fromHere: RangeLeg?)

    private val legs = LinkedHashMap<String, Legs>()
    private val asked = LinkedHashSet<String>()

    /** [offer] için v2 girdileri; bacaklar hazır değilse yalnız batarya. [home] yoksa ya da kartta bırakış noktası yoksa null. */
    fun inputs(offer: OfferState, home: OfferGeo.LatLon?, here: OfferGeo.LatLon?): ShadowEvaluator.Range? {
        home ?: return null
        val text = offer.data.customerAddress ?: return null
        val drop = OfferGeo.fromCardText(text, offer.lat, offer.lon) ?: return null
        val l = synchronized(this) { legs[offer.id] } ?: request(offer.id, drop.lat, drop.lon, home, here).let { synchronized(this) { legs[offer.id] } }
        val b = runCatching { port().battery() }.getOrNull()
        return ShadowEvaluator.Range(
            blePct = b?.takeIf { it.source == RangeBattery.BLE_FRESH }?.pct, bleAtMs = b?.takeIf { it.source == RangeBattery.BLE_FRESH }?.atMs,
            estimatorWh = b?.takeIf { it.source == RangeBattery.ESTIMATOR }?.wh,
            destToHomeKm = l?.toHome?.km, destToHomeMin = l?.toHome?.minutes, hereToHomeKm = l?.fromHere?.km, hereToHomeMin = l?.fromHere?.minutes,
        )
    }

    /** Değerlendirmeyi yenileyen anahtar: bacaklar geldi mi ([ShadowMode.frame]). */
    fun key(offerId: String): Boolean = synchronized(this) { offerId in legs }

    private fun request(id: String, dLat: Double, dLon: Double, home: OfferGeo.LatLon, here: OfferGeo.LatLon?) {
        synchronized(this) {
            if (!asked.add(id)) return
            while (asked.size > MAX) asked.remove(asked.first())
        }
        background {
            val p = port()
            val toHome = runCatching { runBlocking { p.rideTo(dLat, dLon, home.lat, home.lon) } }.getOrNull()
            val fromHere = here?.let { h -> runCatching { runBlocking { p.rideTo(h.lat, h.lon, home.lat, home.lon) } }.getOrNull() }
            synchronized(this) {
                legs[id] = Legs(toHome, fromHere)
                while (legs.size > MAX) legs.remove(legs.keys.first())
            }
        }
    }

    companion object {
        /** Ekranda aynı anda en çok birkaç teklif olur; eskiler atılır ([ShadowMode.MAX_ENTRIES] gibi). */
        const val MAX = 16
    }
}
