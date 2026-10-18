package com.kuryelog.logic.economics

import com.kuryelog.logic.orders.OrderHistory
import com.kuryelog.logic.orders.TripRecord
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * E4 "Teklif puanı" sayfasının içeriği (Rawstats `MainActivity.renderOfferScores/scoreDayBody/pickRating` ve sipariş kartındaki satır,
 * MainActivity.kt:804-869, 1092-1093 @ 6388c04): metinler ve sıra aynen, çizim Compose'ta (Siparişler → "Teklif puanı"). Saf.
 */
object OfferScorePage {
    /** Bir sipariş satırı: [text] iki puan (ya da "teklif görülmedi"); [both] iki puan da var (vurgulu); [rateable] dokununca puan sorulur. */
    data class Row(val tripId: String, val label: String, val text: String, val both: Boolean, val rateable: Boolean, val rating: Int?)
    /** [summaryAccent] false: ortalama fark yok (soluk); [biggestTitle] null: büyük fark yok. */
    data class Page(val intro: String, val info: String, val summary: String, val summaryAccent: Boolean, val biggestTitle: String?,
                    val biggest: List<String>, val rows: List<Row>)

    fun tabs(tl: (String, String) -> String) = listOf(tl("Siparişler", "Sifarişlər"), tl("Teklif puanı", "Təklif balı"))

    /** RS `OrderTracker.platform`. */
    fun platform(pkg: String) = if (pkg.contains("wolt", true)) "Wolt" else if (pkg.contains("bolt", true) || pkg.contains("mtakso", true)) "Bolt Food" else pkg.substringAfterLast('.')

    /** [scores] siparişin teklifinin uygulama puanı ve benim puanım (RS `offerScoreOf`; teklifsiz sipariş: ikisi de null). */
    fun page(trips: List<TripRecord>, scores: (TripRecord) -> Pair<JSONObject?, JSONObject?>, zone: ZoneId, tl: (String, String) -> String): Page {
        val intro = tl("Uygulamanın puanı teklif anında ekonomi motoruyla (EconomicsV2 ${EconomicsV2.SPEC_VERSION}) verilir ve sonradan değişmez. Senin puanın ayrı saklanır: siparişe dokun, 1–10 ver; yeniden dokunup düzeltebilirsin.", "Tətbiqin balı təklif anında iqtisadi mühərriklə (EconomicsV2 ${EconomicsV2.SPEC_VERSION}) verilir və sonradan dəyişmir. Sənin balın ayrıca saxlanılır: sifarişə toxun, 1–10 ver; yenidən toxunub düzəldə bilərsən.")
        val info = tl("Eşleme: ücret motorun kötü sınırında (feePoorBelow) ya da altındaysa 1–3, iyi sınırına (feeGoodFrom) ulaştıysa 8–10 (motorun hesapladığı kazanç fazlasına göre, her 0,5 ₼ bir puan), arasındaysa 4–7 doğrusal. Puan hesaplanamadıysa nedeni yazılır. Puanlar kayıtlara ve dışa aktarmaya girer; müşteri bilgisi yazılmaz.", "Uyğunlaşdırma: məbləğ mühərrikin pis həddində (feePoorBelow) və ya aşağıdırsa 1–3, yaxşı həddinə (feeGoodFrom) çatıbsa 8–10 (mühərrikin hesabladığı qazanc artığına görə, hər 0,5 ₼ bir bal), arasındadırsa 4–7 xətti. Bal hesablanmayıbsa səbəbi yazılır. Ballar qeydlərə və ixraca düşür; müştəri məlumatı yazılmır.")
        val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zone)
        val sorted = trips.sortedBy { OrderHistory.offerAt(it) }
        val rows = sorted.associate { it.id to scores(it) }
        val app = HashMap<String, JSONObject>(); val mine = HashMap<String, JSONObject>()
        for (t in sorted) { val id = t.offer?.id ?: continue; rows[t.id]?.first?.let { app[id] = it }; rows[t.id]?.second?.let { mine[id] = it } }
        val sum = OfferScore.summary(sorted.mapNotNull { it.offer?.id }, app, mine)
        fun label(t: TripRecord) = hm.format(Instant.ofEpochMilli(OrderHistory.offerAt(t))) + " · " + platform(t.pkg) + " · " + (OrderHistory.restaurant(t) ?: tl("Sipariş", "Sifariş"))
        val diff = sum.meanDiff?.let { OfferScore.signed(it) }
        val summary = if (diff == null) tl("Ortalama fark: — (iki puanı da olan sipariş yok)", "Orta fərq: — (iki balı da olan sifariş yoxdur)")
            else tl("Ortalama fark (uygulama − sen): $diff · ${sum.compared} sipariş", "Orta fərq (tətbiq − sən): $diff · ${sum.compared} sifariş")
        val biggest = sum.biggest.mapNotNull { b ->
            val t = sorted.firstOrNull { it.offer?.id == b.offerId } ?: return@mapNotNull null
            label(t) + ": " + OfferScore.pairText(rows[t.id]?.first, rows[t.id]?.second, tl)
        }
        val list = sorted.map { trip ->
            val (a, m) = rows[trip.id] ?: (null to null)
            val rateable = trip.offer != null
            val both = OfferScore.scoreOf(a) != null && OfferScore.ratingOf(m) != null
            Row(trip.id, label(trip), if (!rateable) tl("Teklif görülmedi, puan yok", "Təklif görünmədi, bal yoxdur") else OfferScore.pairText(a, m, tl),
                both, rateable, OfferScore.ratingOf(m))
        }
        return Page(intro, info, summary, diff != null, if (sum.biggest.isNotEmpty()) tl("En büyük farklar", "Ən böyük fərqlər") else null, biggest, list)
    }

    /** Sipariş kartı, "Teklif ayrıntıları": uygulamanın puanı ve benimki (teklifsiz siparişte satır yok). */
    fun cardLine(app: JSONObject?, mine: JSONObject?, tl: (String, String) -> String): String =
        tl("Teklif puanı", "Təklif balı") + ": " + OfferScore.pairText(app, mine, tl)

    /** Puan sorusu (RS `pickRating`): başlık, 1–10 seçenekleri, hata metni. */
    fun ratingTitle(tl: (String, String) -> String) = tl("Bu teklife puanın (1–10)", "Bu təklifə balın (1–10)")
    fun ratingItems(tl: (String, String) -> String): List<String> =
        (1..10).map { v -> when (v) { 1 -> tl("1 · çok kötü", "1 · çox pis"); 10 -> tl("10 · çok iyi", "10 · çox yaxşı"); else -> v.toString() } }
    fun ratingFailed(tl: (String, String) -> String) = tl("Puan kaydedilemedi", "Bal qeyd olunmadı")
    /** Eşleme açıklamasını açan düğmenin adı (RS `MainActivity.info`). */
    fun infoToggle(tl: (String, String) -> String) = tl("Açıklamayı göster", "İzahı göstər")
}
