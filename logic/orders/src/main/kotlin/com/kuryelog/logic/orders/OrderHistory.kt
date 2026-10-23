package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*

// Rawstats OrderHistory.kt @ 6388c04, satır satır; farklar ORDERS.md "WEEM" satırlarında.
/** Orders of every recorded day, per platform, for the Siparişler tab (K6). Pure: no Android. */
object OrderHistory {
    data class Day(val date: String, val trips: List<TripRecord>) {
        /** D3(c): observed and unverified (K34 inferred) deliveries alike; the unverified ones keep their label ([inferred]). */
        val delivered get() = trips.filter { it.countsAsDelivered }
        val orders get() = delivered.sumOf { it.expectedOrders }
        /** Offer fees plus the extra waiting fees of the platform list (D15). */
        val gross get() = delivered.sumOf { (it.gross ?: 0.0) + it.extraWaitAzn }
        val net get() = delivered.sumOf { it.net ?: 0.0 }
        val tips get() = delivered.sumOf { it.tip }
        val unpriced get() = delivered.count { it.gross == null }
        /** Ended without any delivery (shift stopped, offline, the waiting map before a pickup); unverified deliveries excluded. */
        val unconfirmed get() = trips.count { it.phase == TripPhase.END_UNCONFIRMED && !it.deliveredInferred }
        /** Unverified deliveries (closed when the next order started) that no platform list has confirmed yet; part of [delivered]. */
        val inferred get() = trips.count { it.deliveredInferred && it.verifiedBy == null }
        val waitMinutes get() = delivered.sumOf { it.waitMinutes }
        val lateMinutes get() = delivered.sumOf { it.lateMinutes }
    }

    val PLATFORMS = listOf("Wolt", "Bolt Food")

    /**
     * 0.18.0: per-order averages of delivered orders for the Bugün card. Each value averages only the orders where it was
     * measured (null when none): scooter energy needs energy samples, restaurant wait an arrival and a pickup, customer
     * wait an arrival and a delivery at a customer, the wait before an offer a known previous end or session start.
     */
    data class Averages(val energyWh: Double?, val restaurantMin: Double?, val customerMin: Double?, val orderMin: Double?, val beforeOfferMin: Double?)

    private fun List<Double>.avgOrNull() = if (isEmpty()) null else average()

    fun customerWaitMinutes(t: TripRecord): Double? = t.customers.values
        .mapNotNull { c -> val a = c.arrivedAt; val d = c.deliveredAt; if (a != null && d != null && d >= a) (d - a) / 60000.0 else null }
        .takeIf { it.isNotEmpty() }?.sum()

    fun restaurantWaitMinutes(t: TripRecord): Double? =
        t.pickups.filter { it.arrivedAt != null && it.pickedAt != null }.takeIf { it.isNotEmpty() }
            ?.sumOf { (it.pickedAt!! - it.arrivedAt!!).coerceAtLeast(0) / 60000.0 }

    /** "4 dk", "1 sa 5 dk" (whole minutes), like the cards' other durations. */
    fun durationText(min: Double, tl: (String, String) -> String): String {
        val m = Math.round(min)
        return if (m >= 60) tl("${m / 60} sa ${m % 60} dk", "${m / 60} saat ${m % 60} dəq") else tl("$m dk", "$m dəq")
    }

    /** E2: why [t] has no restaurant wait ([restaurantWaitMinutes] null), or null when it has one. */
    fun restaurantWaitMissing(t: TripRecord, tl: (String, String) -> String): String? {
        val ps = t.pickups
        if (ps.isEmpty()) return tl("restoran ekranı görülmedi", "restoran ekranı görünmədi")
        if (ps.any { it.arrivedAt != null && it.pickedAt != null }) return null
        if (ps.none { it.arrivedAt != null })
            return if (t.active && ps.all { it.pickedAt == null }) tl("restorana gidiliyor", "restorana gedilir") else tl("restorana varış görülmedi", "restorana çatma görünmədi")
        return if (t.active) tl("restoranda, henüz alınmadı", "restoranda, hələ götürülməyib") else tl("alış görülmedi", "götürmə görünmədi")
    }

    /** E2, order list: "Restoranda 4 dk" or "Restoranda —". */
    fun restaurantWaitShort(t: TripRecord, tl: (String, String) -> String): String =
        tl("Restoranda", "Restoranda") + " " + (restaurantWaitMinutes(t)?.let { durationText(it, tl) } ?: "—")

    /** E2, order card: the wait with where the arrival came from (D9 GPS stay or the platform's own status), else "—" and why. */
    fun restaurantWaitLine(t: TripRecord, tl: (String, String) -> String): String {
        val head = tl("Restoranda bekleme", "Restoranda gözləmə") + ": "
        val m = restaurantWaitMinutes(t) ?: return head + "— (" + restaurantWaitMissing(t, tl) + ")"
        val src = t.pickups.firstOrNull { it.arrivedAt != null && it.pickedAt != null }?.arrivalSource
        val from = when { src == null -> ""; src.startsWith("gps") -> " (GPS)"; else -> " (" + tl("platform", "platforma") + ")" }
        return head + durationText(m, tl) + from
    }

    /** E3: the order's boost at offer time (K27/D11): "+%30", "1.5x", "yok" (seen without a boost) or "—" (not observed). */
    fun boostText(t: TripRecord, tl: (String, String) -> String): String {
        val o = t.offer ?: return "—"
        val v = o.boostValue
        return when {
            o.boostSource == null || o.boostSource == BoostLog.NOT_OBSERVED -> "—"
            v == null -> tl("yok", "yoxdur")
            o.boostUnit == BoostLog.UNIT_X -> String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.') + "x"
            else -> "+%" + Math.round(v)
        }
    }

    /** E3: the temperature when the order was accepted: the offer's K28 weather (read seconds before the accept), or null. */
    fun temperatureText(t: TripRecord): String? = t.offer?.weather?.tempC?.takeIf { it.isFinite() }?.let { Math.round(it).toString() + "°" }

    /** E3, top of the order card: the boost with where it was read and the temperature at acceptance, "—" and why when missing. */
    fun boostTempLine(t: TripRecord, tl: (String, String) -> String): String {
        val o = t.offer
        val noCard = tl("teklif kartı görülmedi", "təklif kartı görünmədi")
        val b = boostText(t, tl)
        val boost = "Boost " + b + when {
            o == null -> " ($noCard)"
            b == "—" -> " (" + tl("görülmedi", "görünmədi") + ")"
            else -> when (o.boostSource) {
                BoostLog.SOURCE_PLAN -> " (" + tl("Wolt planı", "Wolt planı") + ")"
                BoostLog.SOURCE_PLAN_LIST -> " (" + tl("Wolt Earn extra listesi", "Wolt Earn extra siyahısı") + ")"
                BoostLog.SOURCE_OFFER -> " (" + tl("teklif ekranı", "təklif ekranı") + ")"
                BoostLog.SOURCE_HOME -> " (" + tl("ana ekran", "əsas ekran") + ")"
                else -> ""
            }
        }
        val temp = tl("Sıcaklık", "Temperatur") + " " + (temperatureText(t)?.let { it + " (" + tl("kabul anı", "qəbul anı") + ")" }
            ?: "— (" + (if (o == null) noCard else tl("hava bilgisi gelmedi", "hava məlumatı gəlmədi")) + ")") // WEEM: YolGo adı yok (ORDERS.md 16)
        return "$boost · $temp"
    }

    /** Second line of an order in the day lists: its restaurant wait (E2), boost and temperature at acceptance (E3). */
    fun listLine(t: TripRecord, tl: (String, String) -> String): String =
        restaurantWaitShort(t, tl) + " · Boost " + boostText(t, tl) + " · " + (temperatureText(t) ?: tl("sıcaklık —", "temperatur —"))

    /** [trips] of one day; the wait before an offer is measured within each platform ([platformOf]). D3(c): unverified
     *  deliveries count too, for the values measured on them (energy, restaurant wait); their end time is not known. */
    fun averages(trips: List<TripRecord>, platformOf: (String) -> String): Averages {
        val done = trips.filter { it.countsAsDelivered }
        val before = done.mapNotNull { t -> waitBeforeOffer(t, trips.filter { platformOf(it.pkg) == platformOf(t.pkg) }) }
        return Averages(
            energyWh = done.mapNotNull(::energyOf).avgOrNull(),
            restaurantMin = done.mapNotNull(::restaurantWaitMinutes).avgOrNull(),
            customerMin = done.mapNotNull(::customerWaitMinutes).avgOrNull(),
            orderMin = done.mapNotNull(::totalMinutes).avgOrNull(),
            beforeOfferMin = before.avgOrNull())
    }

    /** D10: an order's scooter energy for the average, or null when it was not measured or its external pack went unread for
     *  over a quarter of the measured time (the figure would be short). */
    fun energyOf(t: TripRecord): Double? = t.usedWh.takeIf { t.energyCoveredMs > 0 && t.energyPartialMs * 4 <= t.energyCoveredMs }

    /** E1: the five per-order averages of the Bugün card; each opens its list of the day's orders. */
    enum class Metric { ENERGY, RESTAURANT, CUSTOMER, ORDER, BEFORE_OFFER }

    /** E1: one order of an average's list: its time (as in the order lists), platform, restaurant and value, else why not. */
    data class MetricRow(val tripId: String, val at: Long, val platform: String, val restaurant: String?, val value: Double?, val why: String?)

    /** E1: exactly the orders [averages] takes [m] over (delivered ones, unverified included), in time order, each with its
     *  value, or with why it has none. The average of the values is the card's number. */
    fun metricRows(trips: List<TripRecord>, m: Metric, platformOf: (String) -> String, tl: (String, String) -> String): List<MetricRow> =
        trips.filter { it.countsAsDelivered }.map { t ->
            val p = platformOf(t.pkg)
            val unverifiedEnd = tl("teslim anı görülmedi (doğrulanmamış teslim)", "çatdırılma anı görünmədi (təsdiqlənməmiş çatdırılma)")
            val (v, why) = when (m) {
                Metric.ENERGY -> energyOf(t) to when {
                    t.energyCoveredMs <= 0 -> tl("scooter ölçümü yok", "skuter ölçməsi yoxdur")
                    else -> tl("harici batarya okunmayan süre", "xarici batareya oxunmayan müddət") + " %" + Math.round(t.energyPartialMs * 100.0 / t.energyCoveredMs) +
                        " (" + tl("sınır", "hədd") + " %25)"
                }
                Metric.RESTAURANT -> restaurantWaitMinutes(t) to restaurantWaitMissing(t, tl)
                Metric.CUSTOMER -> customerWaitMinutes(t) to when {
                    t.completedAt == null -> unverifiedEnd
                    t.customers.values.none { it.arrivedAt != null } -> tl("müşteriye varış görülmedi", "müştəriyə çatma görünmədi")
                    else -> tl("teslim anı görülmedi", "çatdırılma anı görünmədi")
                }
                Metric.ORDER -> totalMinutes(t) to unverifiedEnd
                Metric.BEFORE_OFFER -> trips.filter { platformOf(it.pkg) == p }.let { same -> waitBeforeOffer(t, same) to beforeOfferMissing(t, same, tl) }
            }
            MetricRow(t.id, listTime(t, p), p, restaurant(t), v, if (v == null) why else null)
        }.sortedBy { it.at }

    /** E1 row without its clock: "Wolt · Test Döner · 4.0 dk", or "… · — (why)". */
    fun metricRowText(r: MetricRow, m: Metric, tl: (String, String) -> String): String {
        fun f1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)
        val value = r.value?.let { if (m == Metric.ENERGY) f1(it) + " Wh" else f1(it) + " " + tl("dk", "dəq") } ?: ("— (" + r.why + ")")
        return r.platform + " · " + (r.restaurant ?: tl("Sipariş", "Sifariş")) + " · " + value
    }

    /** Order card status: a K34 inferred delivery reads "delivered (unverified)" until a platform list confirms it, every
     *  other order its phase. */
    val INFERRED = "Teslim edildi (doğrulanmadı)" to "Çatdırıldı (təsdiqlənmədi)"
    val LIST_VERIFIED = "Teslim edildi (listede doğrulandı)" to "Çatdırıldı (siyahıda təsdiqləndi)"
    fun status(t: TripRecord, tl: (String, String) -> String): String = when {
        t.deliveredInferred && t.verifiedBy != null -> tl(LIST_VERIFIED.first, LIST_VERIFIED.second)
        t.deliveredInferred -> tl(INFERRED.first, INFERRED.second)
        else -> OverlayText.phase(t.phase, tl)
    }
    /** D3(c)/D4: deliveries of [trips] (each order of a bundle counted), unverified deliveries included: the Bugün number. */
    fun deliveredOrders(trips: Collection<TripRecord>): Int = trips.sumOf { if (it.countsAsDelivered) it.expectedOrders else 0 }

    /** Newest day first. A live trip (today's, still changing) replaces its stored copy.
     *  platform null = "Tüm siparişler": Wolt and Bolt Food together, other watched apps left out. */
    fun days(stored: List<TripRecord>, live: List<TripRecord>, platform: String?, platformOf: (String) -> String, dayOf: (Long) -> String): List<Day> {
        val byId = LinkedHashMap<String, TripRecord>()
        (stored + live).forEach { byId[it.id] = it }
        return byId.values.filter { if (platform == null) platformOf(it.pkg) in PLATFORMS else platformOf(it.pkg) == platform }.groupBy { dayOf(it.acceptedAt) }
            .map { (date, trips) -> Day(date, trips.sortedBy { it.acceptedAt }) }.sortedByDescending { it.date }
    }

    /** Same clock as the platform's own earnings list: Wolt shows the accept time,
     *  Bolt "All Deliveries" the delivery time. */
    fun listTime(t: TripRecord, platform: String): Long = if (platform == "Bolt Food") t.completedAt ?: t.deliveredInferredAt ?: t.acceptedAt else acceptTime(t)
    /**
     * D23: the moment the courier tapped Accept: the last frame of the offer card. [TripRecord.acceptedAt] is the first task
     * frame after it, minutes later for an order accepted while another one was still being handed over (Wolt keeps the
     * current drop-off on screen: SMOKING SHOP 11 Oct, accepted 19:18 per the earnings list, first pickup frame 19:20:46).
     */
    fun acceptTime(t: TripRecord): Long = t.offer?.lastSeenAt?.takeIf { it <= t.acceptedAt } ?: t.acceptedAt

    /**
     * Merged Sürüş Paneli: the order's scooter energy for its card, or null when the shift's scooter link measured nothing for it.
     * Headline: the energy taken out of the measured batteries ([TripRecord.usedWh]) and Wh/km over the order's GPS distance
     * (from 300 m). The Ninebot Max G3 has no regenerative braking, so energy flowing into a battery ([TripRecord.recoveredWh],
     * a name kept from the 0.7 records) is a charger or the external battery feeding the internal one while only the internal
     * battery is measured: shown apart under that name, never subtracted. Then the measured minutes of the order, and a note
     * when another order was open at the same time (the energy followed the screens, like the distance).
     */
    fun energyLine(t: TripRecord, tl: (String, String) -> String): String? {
        if (t.energyCoveredMs <= 0 && t.energyPartialMs <= 0) return null
        fun f1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)
        // D10: both packs' energy only, never negative; "—" when the external pack was never read during the order.
        val parts = mutableListOf(tl("Scooter enerjisi", "Skuter enerjisi") + ": " + (if (t.energyCoveredMs > 0) f1(t.usedWh.coerceAtLeast(0.0)) + " Wh" else "—"))
        if (t.energyCoveredMs > 0 && t.energyPartialMs <= 0 && t.distanceM >= 300) parts += f1(t.usedWh / (t.distanceM / 1000)) + " Wh/km"
        if (t.recoveredWh >= 0.05) parts += tl("bataryaya giren", "batareyaya daxil olan") + " " + f1(t.recoveredWh) + " Wh (" + tl("harici batarya/şarj", "xarici batareya/şarj") + ")"
        val measured = Math.round(t.energyCoveredMs / 60000.0)
        val window = (t.completedAt ?: t.deliveredInferredAt)?.let { Math.round((it - t.acceptedAt).coerceAtLeast(0) / 60000.0) }
        parts += tl("ölçülen", "ölçülən") + " " + (if (window != null) "$measured/$window" else "$measured") + " " + tl("dk", "dəq")
        if (t.energyPartialMs > 0) parts += tl("harici batarya okunmayan", "xarici batareya oxunmayan") + " " + Math.round(t.energyPartialMs / 60000.0) + " " + tl("dk", "dəq")
        // WEEM A10.4f (S18, S35): the minutes the scooter link read nothing are named, not hidden (one phone loses the link between orders).
        val missing = window?.let { it - Math.round((t.energyCoveredMs + t.energyPartialMs) / 60000.0) } ?: 0
        if (missing >= 1) parts += tl("veri eksik", "məlumat əskik") + " " + missing + " " + tl("dk", "dəq")
        if (t.energyShared) parts += tl("başka siparişle aynı anda", "başqa sifarişlə eyni vaxtda")
        return parts.joinToString(" · ")
    }

    /** D6: how fast the offer was read (the app's notification → the card's first frame → the frame with the fee → OCR), or null. */
    fun readLine(o: OfferState, tl: (String, String) -> String): String? {
        fun s(ms: Long) = String.format(java.util.Locale.US, "%.1f", ms / 1000.0) + " " + tl("sn", "san")
        val parts = listOfNotNull(o.notifiedAt?.let { tl("bildirim → kart ", "bildiriş → kart ") + s(o.firstAt - it) },
            o.feeAt?.let { tl("kart → tutar ", "kart → məbləğ ") + s((it - o.firstAt).coerceAtLeast(0)) },
            o.feeOcrMs?.let { "OCR " + s(it) })
        return if (parts.isEmpty()) null else tl("Okuma", "Oxuma") + ": " + parts.joinToString(" · ")
    }

    /** Offer first seen, else the accept time. */
    fun offerAt(t: TripRecord): Long = t.offer?.firstAt ?: t.acceptedAt
    /** Restaurant pickup: the last pickup of the trip. */
    fun pickedAt(t: TripRecord): Long? = t.pickups.mapNotNull { it.pickedAt }.maxOrNull()
    /** Offer → delivery, the whole time the order took. */
    fun totalMinutes(t: TripRecord): Double? = t.completedAt?.let { (it - offerAt(t)).coerceAtLeast(0) / 60000.0 }

    /** Minutes the courier waited for this offer: since the previous order of the same platform ended,
     *  or since going online (session start), whichever is later. 0 if another order was still running.
     *  null when neither is known. [sameDay] are the other orders of that platform on that day. */
    fun waitBeforeOffer(t: TripRecord, sameDay: List<TripRecord>): Double? = (before(t, sameDay) as? Before.Minutes)?.value

    /** E1: why [waitBeforeOffer] is null, or null when it has a value. */
    fun beforeOfferMissing(t: TripRecord, sameDay: List<TripRecord>, tl: (String, String) -> String): String? = when (before(t, sameDay)) {
        is Before.Minutes -> null
        Before.AfterUnverified -> tl("önceki sipariş doğrulanmamış teslimle kapandı, bitişi bilinmiyor", "əvvəlki sifariş təsdiqlənməmiş çatdırılma ilə bağlandı, bitişi bilinmir")
        Before.NoStart -> tl("çevrimiçi başlangıcı ve önceki sipariş bilinmiyor", "onlayn başlanğıc və əvvəlki sifariş bilinmir")
    }

    private sealed class Before { class Minutes(val value: Double) : Before(); object AfterUnverified : Before(); object NoStart : Before() }
    private fun before(t: TripRecord, sameDay: List<TripRecord>): Before {
        val at = offerAt(t)
        val earlier = sameDay.filter { it.id != t.id && it.acceptedAt < t.acceptedAt }
        if (earlier.any { it.completedAt == null && it.active || (it.completedAt ?: Long.MIN_VALUE) > at }) return Before.Minutes(0.0)
        val sessionStart = t.sessionId?.substringAfterLast('-')?.toLongOrNull()?.takeIf { it <= at }
        val previousEnd = earlier.mapNotNull { it.completedAt }.filter { it <= at }.maxOrNull()
        // K34: an earlier order closed as an inferred delivery has no observed end. When it is the last end before this
        // offer (or was closed after it), the wait is unknown, not measured from an older delivery.
        val inferredEnd = earlier.filter { it.deliveredInferred }.mapNotNull { it.deliveredInferredAt }.maxOrNull()
        if (inferredEnd != null && (inferredEnd > at || inferredEnd >= maxOf(previousEnd ?: Long.MIN_VALUE, sessionStart ?: Long.MIN_VALUE))) return Before.AfterUnverified
        val from = listOfNotNull(sessionStart, previousEnd).maxOrNull() ?: return Before.NoStart
        return Before.Minutes((at - from) / 60000.0)
    }

    fun restaurant(t: TripRecord): String? =
        t.pickups.mapNotNull { it.restaurant?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString(" + ").ifEmpty { null }
            ?: t.offer?.data?.restaurant?.trim()?.takeIf(String::isNotEmpty)

    private val trMonths = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    private val azMonths = listOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avqust", "sentyabr", "oktyabr", "noyabr", "dekabr")
    /** "2026-10-06" → "6 Ekim 2026" / "6 oktyabr 2026". */
    fun dateLabel(date: String, az: Boolean): String {
        val p = date.split('-')
        if (p.size != 3) return date
        val month = p.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return date
        return "${p[2].toIntOrNull() ?: p[2]} ${(if (az) azMonths else trMonths)[month - 1]} ${p[0]}"
    }
}
