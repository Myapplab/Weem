package com.kuryelog.logic.capture

import java.text.Normalizer
import java.util.Locale

/** Window-local facts. A missing/hidden window is NEVER an idle observation. */
enum class ScreenStage { OFFER, PICKUP, DROPOFF, IDLE, OFFLINE, UNKNOWN }
data class DeliveryDetails(
    var name: String? = null, var address: String? = null, var note: String? = null,
    var instructions: String? = null, var building: String? = null, var entrance: String? = null,
    var floor: String? = null, var apartment: String? = null, var doorCode: String? = null,
    var checkId: Boolean = false, var addressDetails: String? = null,
    /** Card and courier.db only (K25). Never in logs or backups: see RecordJson.details / trip(withPhone). */
    var phone: String? = null
) {
    fun merge(d: DeliveryDetails) {
        // OCR may drop the surname ("Gulnar") or move it to the address line; keep the fuller value.
        d.name?.let { n -> if (!(name != null && NameMatch.sameName(name, n) && n.trim().split(Regex("\\s+")).size < name!!.trim().split(Regex("\\s+")).size)) name = n }
        d.address?.let { a -> if (!(address != null && NameMatch.looksLikeAddress(address!!) && !NameMatch.looksLikeAddress(a))) address = a }
        // A shortened note ("…") must not replace the complete text read earlier.
        d.note?.let { if (!(NoteFields.truncated(it) && note?.startsWith(it.trimEnd('.', '…', ' ')) == true)) note = it }
        d.instructions?.let { instructions = it }; d.building?.let { building = it }
        d.entrance?.let { entrance = it }; d.floor?.let { floor = it }; d.apartment?.let { apartment = it }
        d.doorCode?.let { doorCode = it }; checkId = checkId || d.checkId
        d.addressDetails?.let { addressDetails = it }; d.phone?.let { phone = it }
    }
    /** Structured fields left empty by the customer are taken from their free text. */
    fun fillFromText(): DeliveryDetails {
        // F7.1: a structured field holding other fields' words ("Menzil41" as Entrance, a whole direction) is free text:
        // its own value is read out of it, the rest fills the empty fields.
        val moved = listOfNotNull(entrance, floor, apartment).filter(NoteFields::holdsOtherFields)
        if (moved.isNotEmpty()) {
            val g = NoteFields.extract(*moved.toTypedArray())
            if (NoteFields.holdsOtherFields(entrance)) entrance = NoteFields.extract(entrance).entrance
            if (NoteFields.holdsOtherFields(floor)) floor = NoteFields.extract(floor).floor
            if (NoteFields.holdsOtherFields(apartment)) apartment = NoteFields.extract(apartment).apartment
            if (entrance == null) entrance = g.entrance
            if (floor == null) floor = g.floor
            if (apartment == null) apartment = g.apartment ?: g.room?.let { "Oda $it" }
            if (doorCode == null) doorCode = g.doorCode
        }
        val f = NoteFields.extract(addressDetails, note, instructions)
        if (building == null) building = f.building
        if (entrance == null) entrance = f.entrance
        if (floor == null) floor = f.floor
        if (apartment == null) apartment = f.apartment ?: f.room?.let { "Oda $it" }
        if (doorCode == null) doorCode = f.doorCode
        if (phone == null) phone = f.phones.firstOrNull()
        return this
    }
    val noteTruncated get() = NoteFields.truncated(note) || NoteFields.truncated(instructions)
    /** Short facts first (big on the card), then the customer's own words unchanged. */
    fun facts(az: Boolean = false): List<String> = listOfNotNull(
        doorCode?.let { (if (az) "Qapı kodu: " else "Kapı kodu: ") + it },
        building?.let { "Bina: $it" }, entrance?.let { (if (az) "Giriş: " else "Giriş: ") + it },
        floor?.let { (if (az) "Mərtəbə: " else "Kat: ") + it },
        apartment?.let { (if (az) "Mənzil: " else "Daire: ") + it },
        (if (az) "Şəxsiyyət yoxlaması lazımdır" else "Kimlik kontrolü gerekli").takeIf { checkId }
    )
    fun display(az: Boolean = false): String = (facts(az) + listOfNotNull(
        phone?.let { (if (az) "Telefon: " else "Telefon: ") + it },
        addressDetails?.let { (if (az) "Ünvan detalları: " else "Adres detayı: ") + it },
        note?.let { (if (az) "Qeyd: " else "Not: ") + it },
        instructions?.takeIf { it != note }?.let { (if (az) "Təlimat: " else "Talimat: ") + it },
        address,
        (if (az) "Qeyd qısaldılıb: tam mətn üçün kuryer tətbiqində Notes bölməsini aç." else "Not kısaltılmış: tamamı için kurye uygulamasında Notes'u aç.").takeIf { noteTruncated }
    )).joinToString("\n")
}
data class CourierObservation(
    val pkg: String, val at: Long, val stage: ScreenStage, val complete: Boolean,
    val source: String, val raw: List<String>, val actions: List<String>,
    val gross: Double? = null, val km: Double? = null, val minLo: Int? = null, val minHi: Int? = null,
    val stops: Int? = null, val quantity: Int? = null, val restaurant: String? = null,
    val restaurantAddress: String? = null, val customerAddress: String? = null,
    val orderIds: List<String> = emptyList(), val customer: DeliveryDetails = DeliveryDetails(),
    val readyIn: Int? = null, val pickupClock: Int? = null, val dropoffClock: Int? = null,
    val travelEta: Int? = null, val arrived: Boolean = false, val ready: Boolean = false,
    val delivered: Boolean = false, val demand: String? = null, val bonusPct: Int? = null,
    val bonusFrom: String? = null, val multipliers: List<Double> = emptyList(), val offlineUntil: String? = null,
    /** The "Order delivered!" / "Delivered · Slide to confirm" control is on screen. */
    val deliverControl: Boolean = false,
    /** "online" / "offline" only from positive evidence (slider label, waiting text, Wolt Feedback page). */
    val availability: String? = null, val availabilityEvidence: String? = null,
    /** Wolt "Decline offer?" confirmation sheet. */
    val declineDialog: Boolean = false,
    /** "N deliveries declined" banner: Wolt's count of consecutive declines. */
    val declinedCount: Int? = null,
    val preparing: Boolean = false, val restaurantNote: String? = null,
    val pickupCode: String? = null, val items: List<String> = emptyList(),
    /** Bolt offer card: "~8 min" to the restaurant, "~14 min" to the customer (the button's "22 min" is their sum). */
    val approachMin: Int? = null, val deliveryMin: Int? = null,
    /** Any boost on screen, active or not ("Hər sifariş üçün əlavə 20%"); [bonusPct] stays the active one only. */
    val boostPct: Int? = null, val boostWindow: String? = null, val boostStartsInMin: Int? = null,
    /** Performance pages (Wolt "Your stats", Bolt "Performance"/"Acceptance rate"); rates are fractions 0..1. Stage stays UNKNOWN. */
    val statsEarningsAzn: Double? = null, val statsTasksDone: Int? = null, val statsCompletionRate: Double? = null,
    val statsAcceptanceRate: Double? = null, val statsRating: Double? = null, val statsPeriodLabel: String? = null,
    /** K33: a progress indicator (accessibility class `ProgressBar`) on Wolt's delivery slider in this frame (see [SliderSpinner]).
     *  Set by the accessibility reader, never by the text parser; extra evidence only, never a delivery by itself. */
    val deliverSpinner: Boolean = false,
    /** K31: Wolt's delivery steps screen (see CashPayment in CashTips.kt). Set by OrderTracker after parsing, never by the text
     *  parser: a cash step was seen, the checkbox amount (`I have collected the cash (AZN 20.40)`), and the steps header. */
    val cashOrder: Boolean = false, val cashAmount: Double? = null, val cashStepsSeen: Boolean = false,
    /** D25 (Wolt 2.202.6): the offer card says "Cash order"; [changeNeeded] is the change the courier must carry
     *  ("AZN 45.75 | Total change needed"), 0.0 for "No change needed". Never the fee. */
    val offerCash: Boolean = false, val changeNeeded: Double? = null,
    /** D24: every boost slot on screen (Earn extra list, waiting sheet), in screen order. */
    val boostSlots: List<BoostSlot> = emptyList(),
    /** F8.1 Wolt "Your stats": the date header as a period ([CourierParser.statsPeriod]), "Distance on- and off-task",
     *  "Earnings without tips" and "Tips". */
    val statsPeriod: String? = null, val statsDistanceKm: Double? = null, val statsEarningsNoTipsAzn: Double? = null, val statsTipsAzn: Double? = null,
    /** M16 (16 Oct): Wolt's cash steps screen, set by OrderTracker like [cashOrder]: the checkbox seen ticked (true) / not (false)
     *  in the accessibility tree, null when unknown (OCR cannot see the tick); the change ("No change needed" = 0.0, else amount). */
    val cashChecked: Boolean? = null, val cashChangeAzn: Double? = null
)

/** One boost slot as Wolt shows it: "Hər sifariş üçün əlavə 30%" + "17:00-18:00" (window) or "… 20% from 16:00" (start only). */
data class BoostSlot(val pct: Int, val window: String?, val from: String?, val active: Boolean,
    /** M21: the Earn extra row's date label "17/10" (day/month); null for "Today" and the waiting sheet (today). */
    val date: String? = null)

object CourierParser {
    /** WEEM (CAPTURE.md 7.5): Unicode word edges written out. Android's ICU reads `\b` as a Unicode edge, JDK 19+ as an ASCII
     *  one, so `\b` after "мин" matches on the phone but not in JVM tests; these give the phone's result on both. */
    internal const val WORD_START = "(?<![\\p{L}\\p{N}_])"
    internal const val WORD_END = "(?![\\p{L}\\p{N}_])"
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ROOT)
        .replace('ə', 'e').replace('ı', 'i').replace('ё', 'е'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[\\s\\p{Z}]+"), " ").trim()
    val WOLT = setOf("com.wolt.courierapp", "com.wolt.partner")
    private val GO_ONLINE = setOf("go online", "slide to go online", "cevrim ici ol")
    private val GO_OFFLINE = setOf("go offline", "slide to go offline")
    private val declinedBanner = Regex("^(\\d+) (?:deliveries|delivery|orders|order|tasks|task|offers|offer) declined$")
    private val rowLabels = mapOf("entrance" to "entrance", "giris" to "entrance", "floor" to "floor", "kat" to "floor", "mertebe" to "floor",
        "apartment" to "apartment", "flat" to "apartment", "daire" to "apartment", "menzil" to "apartment", "door code" to "door", "entrance code" to "door")
    // A number glued to more digits is not an amount: "4.499 AZN" is never read as 499 (thousands separator, R04).
    private val money = Regex("(?:₼[\\s\\p{Z}]*([0-9]+(?:[.,][0-9]{1,2})?)(?![.,]?[0-9]))|(?:(?<![0-9.,])([0-9]+(?:[.,][0-9]{1,2})?)[\\s\\p{Z}]*(?:₼|azn|manat))", RegexOption.IGNORE_CASE)
    /** Amounts that are not the courier's fee: cash to collect from the customer, balances, basket totals (R06). */
    /** Wolt 2.202.6 amount line: "AZN 2.61"; OCR may read the decimal point as ':' ("AZN 2:13"). */
    private val woltAzn = Regex("^(?:azn|₼)[\\s\\p{Z}]*([0-9]{1,3})[.,:]([0-9]{2})$", RegexOption.IGNORE_CASE)
    private fun woltAmount(s: String?): Double? = s?.let { woltAzn.find(it.trim()) }?.let { "${it.groupValues[1]}.${it.groupValues[2]}".toDouble() }
    /** "Delivery demand ▮ Low": OCR reads the bar icon as 0–4 junk characters ("il", "l", "all", "1l", "|"), often on the next line (D19). */
    private val demandLevel = Regex("^(?:delivery demand)?[\\s\\p{Z}]*\\S{0,4}[\\s\\p{Z}]*(high|medium|low)$")
    /** Wolt 2.202.6 map label "Pickup 6 min" = ready in 6 min (D22); OCR: "8 Pickup 4 min", "-kup 8 min". Not the status-bar "N min" chip. */
    private val woltPickupIn = Regex("(?:pickup|^-?kup)[\\s\\p{Z}]*([−-]?[0-9]{1,3})[\\s\\p{Z}]*min")
    private val pickupsFrom = Regex("^(\\d+) pickups? from$")
    /** R1 (16 Oct): Wolt's customer card for several orders of one customer, "2 dropoffs to" + name ("Orders #072, #387"). */
    private val boltBubbleLine = Regex("^(\\d{1,3}) ?min$")
    private val dropoffsTo = Regex("^(\\d+) drop ?offs to$")
    private val notFee = Regex("cash|balance|basket|nakit|balans|negd|nagd|наличн")
    /** Live multipliers are whole lines ("1.6x", "x1.4"); "1.1–1.65x" (forecast), "Cook Box 2 by…" and "2x burger" are not. */
    private val multiplierLine = Regex("^(?:[x×][\\s\\p{Z}]*([0-9]+(?:[.,][0-9]+)?)|([0-9]+(?:[.,][0-9]+)?)[\\s\\p{Z}]*[x×])$")
    private val boltEta = Regex("^[~≈∼][\\s\\p{Z}]*([0-9]{1,3})[\\s\\p{Z}]*(?:min|deq|dk|мин)$")
    private val addressLabels = setOf("address", "unvan", "adres", "адрес", "delivery address", "drop-off address")
    private val sections = addressLabels + setOf("instructions", "additional note", "note", "notes", "floor", "apartment", "flat", "door code", "entrance code", "building name", "building", "entrance", "block", "unit", "kat", "daire", "mertebe", "bina", "giris", "qapi kodu", "menzil", "telimatlar", "qeyd", "elave qeyd", "этаж", "квартира", "подъезд", "код двери", "инструкции", "комментарий", "call", "chat", "translate", "view", "delivery issues?", "get help", "arrived", "pick up", "collect", "complete delivery", "slide to confirm", "customer drop-off", "pickup from", "dropoff to", "orders to pick up:", "address details", "order details", "difficulties with the delivery?", "send a message", "info", "items")
    private fun number(s: String?): Double? = s?.replace(',', '.')?.toDoubleOrNull()
    fun amount(s: String): Double? = money.find(s)?.let { number(it.groups[1]?.value ?: it.groups[2]?.value) }
    fun isAccept(s: String): Boolean {
        val n = norm(s)
        return OfferRules.isAccept(s) || (amount(s) != null && Regex("$WORD_START\\d+\\s*(min|deq|dk)$WORD_END").containsMatchIn(n))
    }
    fun isDecline(s: String) = norm(s) in setOf("decline", "decline delivery", "reject", "reddet", "redd et", "imtina", "imtina et", "imtina edin", "отклонить")
    fun isCollect(s: String): Boolean {
        val n = norm(s).replace("slide to confirm", "").trim()
        return n in setOf("pick up", "pickup", "collect", "confirm pickup", "tehvilalmani tamamla", "teslim al", "siparisi al", "goturdum", "tehvil al", "забрать", "получить заказ")
    }
    fun isDeliver(s: String): Boolean = norm(s).replace("slide to confirm", "").trim().trimEnd('!', '.', '…') in setOf("delivered", "order delivered", "complete delivery", "finish delivery", "confirm delivery", "catdirildi", "teslim edildi", "teslimati tamamla", "tehvilvermeni tamamla", "доставлено", "завершить доставку")
    /** Call button of the customer screen. Support and restaurant calls are not the customer's number. */
    fun isCall(s: String): Boolean {
        val n = norm(s)
        if (listOf("support", "destek", "restaurant", "restoran", "venue", "поддерж", "ресторан").any { n.contains(it) }) return false
        return Regex("(^|\\s)(call|phone|zeng|zeng et|ara|позвонить|звонок)(\\s|$)").containsMatchIn(n)
    }
    fun isArrive(s: String) = norm(s).replace("slide to confirm", "").trim() in setOf("arrived", "i have arrived", "catdim", "vardim", "на месте")

    fun parse(pkg: String, texts: List<String>, actions: List<String>, at: Long, source: String = "accessibility", complete: Boolean = true): CourierObservation {
        // WEEM's own badge lines ("● 30.1 km", "Wolt · 2.21₼ · Teklif", "Bugün: 7 sipariş") are never screen facts,
        // whatever the source (an OCR crop or a window read can contain the overlay).
        val shown = texts.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() && !OwnLines.isOwn(it) }
        val taps = actions.filterNot(OwnLines::isOwn)
        val lines = shown.distinct()
        val ns = lines.map(::norm); val all = ns.joinToString("\n")
        val acts = taps.map(::norm)
        val actionPrices = taps.filter(::isAccept).mapNotNull(::amount).distinct()
        // D1/D21 (Wolt 2.202.6, field data 11–12 Oct): "Expected earnings for the full delivery" alone marks the offer card; the fee is
        // the "AZN 2.61" line right before it in OCR order. Anything after it (cash change "AZN 45.75 | Total change needed") is never
        // the fee, so a card frame without that line has no fee (the first frame often lacks it while the card slides in).
        val earnIdx = if (pkg in WOLT) ns.indexOfFirst { it.startsWith("expected earn") } else -1
        val woltCard = earnIdx >= 0
        val woltFee = if (earnIdx > 0) woltAmount(lines[earnIdx - 1]) else null
        val earningPrices = lines.filter { line ->
            val n=norm(line)
            Regex("earning|courier fee|estimated fee|kazanc|qazanc|доход").containsMatchIn(n) &&
                !notFee.containsMatchIn(n)
        }.mapNotNull(::amount).distinct()
        val allPrices = lines.filterNot { notFee.containsMatchIn(norm(it)) }
            .flatMap { money.findAll(it).map { m -> number(m.groups[1]?.value ?: m.groups[2]?.value) }.filterNotNull().toList() }.distinct()
        val price = when {
            woltCard -> woltFee
            actionPrices.size == 1 -> actionPrices.single()
            earningPrices.size == 1 -> earningPrices.single()
            allPrices.size == 1 -> allPrices.single()
            else -> null
        }
        val accept = taps.any(::isAccept) || (lines.any(::isAccept) && lines.any(::isDecline)) || woltCard
        val wolt = pkg in WOLT
        // Slider labels reach OCR as "> Go online" or "Go online!".
        val labels = ns.map { it.trim('>', '›', '<', '‹', '→', '!', '.', ' ') }
        val goOnline = labels.any { it in GO_ONLINE }
        val goOffline = labels.any { it in GO_OFFLINE }
        // Wolt shows this page every time right after "Go offline" was slid.
        val woltFeedback = wolt && ns.any { it == "submit feedback" } &&
            ns.any { it == "how was your collaboration with the venue?" || it == "was everything ok with wolt support?" }
        val offline = goOnline || ns.any { it.startsWith("unable to go online") || it.contains("set offline until") }
        // Wolt 2.202.6: "Are you sure you want to | decline this delivery? | … | Decline delivery | Start pickup" (12 Oct field data).
        val declineDialog = ns.any { it == "decline offer?" || it == "decline offer" || it.startsWith("decline this delivery") }
        val declinedCount = ns.firstNotNullOfOrNull { declinedBanner.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        // Wolt's slider is an action, even when OCR drops its final '!'.
        // Its visible label must never be treated as a completed-status message.
        val multiDrop = wolt && ns.any { dropoffsTo.matches(it) }
        val deliveryPanel = multiDrop || ns.any { it == "dropoff to" || it == "drop off to" || it == "delivery timeline" || it == "address details" }
        val slider = ns.any { it.contains("slide to confirm") }
        val deliveryControl = taps.any(::isDeliver) || (wolt && deliveryPanel && lines.any(::isDeliver)) ||
            (slider && lines.any(::isDeliver) && ns.any { it.startsWith("drop off") || it.startsWith("dropoff") || Regex("#[a-z0-9]{4,}").containsMatchIn(it) })
        val delivered = !deliveryControl && ns.any { it == "delivery completed" || it == "order delivered" }
        // D19: Wolt's waiting sheet ("Quieter than usual", "Delivery demand ▮ Low", "EXPECTED DEMAND"); the level may be missing or
        // on the next line behind OCR junk, and "Go offline" is not visible in split screen. 2.259/2.715 field frames were missed.
        val demandIdx = if (wolt) ns.indexOfFirst { it.startsWith("delivery demand") } else -1
        val demandMap = wolt && (demandIdx >= 0 || ns.any { it.contains("quieter than usual") || it == "expected demand" })
        val waitingText = ns.any { it.contains("expect a task soon") || it.contains("new tasks incoming") || it.contains("waiting for orders") || it.contains("looking for orders") || it == "you are online" || it == "stop new orders" }
        val idle = demandMap || goOffline || waitingText || ns.any { it.contains("orders coming in hot") || Regex("^\\d+ live hotspots?$").matches(it) || it == "online" }
        // The demand map is on Wolt's online AND offline screens, so it is no evidence either way.
        val availability = when {
            woltFeedback -> "offline"
            offline && !goOffline -> "offline"
            (goOffline || waitingText) && !offline -> "online"
            else -> null
        }
        val availabilityEvidence = when {
            availability == null -> null
            woltFeedback -> "wolt_feedback_after_go_offline"
            goOnline -> "slider_go_online"
            offline -> "offline_message"
            goOffline -> "slider_go_offline"
            else -> "waiting_text"
        }
        val drop = delivered || deliveryControl || multiDrop || ns.any { it.startsWith("drop off in") || it.startsWith("dropoff ") || it == "dropoff to" || it == "drop off to" } || ns.any { Regex("#[a-z0-9]{4,}").containsMatchIn(it) } && ns.any { it in addressLabels } || (ns.any { it == "check id" } && ns.any { it.startsWith("dropoff") })
        val pickup = ns.any { it == "pickup from" || pickupsFrom.matches(it) || it.startsWith("pickup ") || it.contains("order is being prepared") || it.contains("order is ready for pickup") || it.startsWith("orders to pick up") || it.startsWith("arrive in") } || taps.any(::isCollect)
        val controls = accept || deliveryControl || taps.any(::isCollect)
        val stage = when {
            OfferRules.isHistory(lines) -> ScreenStage.UNKNOWN
            // The offer is still undecided behind "Decline offer?"; never an order screen.
            declineDialog -> ScreenStage.UNKNOWN
            // Menus, support, statistics and settings never change an order's state.
            CourierScreens.isMenu(ns) || (!controls && !idle && !offline && CourierScreens.isPage(ns)) -> ScreenStage.UNKNOWN
            accept && !OfferRules.isHistory(lines) -> ScreenStage.OFFER
            drop -> ScreenStage.DROPOFF
            pickup -> ScreenStage.PICKUP
            offline -> ScreenStage.OFFLINE
            idle -> ScreenStage.IDLE
            else -> ScreenStage.UNKNOWN
        }
        fun index(vararg labels: String) = ns.indexOfFirst { it in labels }
        fun after(vararg labels: String): String? {
            val i = index(*labels); if (i < 0) return null
            val out = ArrayList<String>()
            for (j in i+1 until lines.size) {
                if (ns[j] in sections || ns[j].startsWith("items (") || ns[j].startsWith("#") || isAccept(lines[j]) || isDeliver(lines[j])) break
                out.add(lines[j])
            }
            return out.joinToString("\n").trim().takeIf { it.isNotEmpty() }
        }
        fun field(vararg labels: String): String? {
            after(*labels)?.let { return it }
            for (i in ns.indices) for (label in labels) {
                if (ns[i].startsWith("$label:")) return lines[i].substringAfter(':').trim().takeIf { it.isNotEmpty() }
            }
            return null
        }
        // Wolt's short order number ("#408") is kept after the long ids; DeliveryCore keys customers by 4+ characters only (K10).
        val ids = (Regex("#[A-Za-z0-9]{4,}").findAll(lines.joinToString(" ")).map { it.value } +
            (if (wolt) Regex("(?<![A-Za-z0-9])#[A-Za-z0-9]{3}(?![A-Za-z0-9])").findAll(lines.joinToString(" ")).map { it.value } else emptySequence()))
            .distinct().toList()
        val count = Regex("orders to pick up\\s*:?\\s*(\\d+)").find(all)?.groupValues?.get(1)?.toIntOrNull()
            ?: ns.indexOfFirst { it.startsWith("orders to pick up") }.takeIf { it >= 0 }?.let { lines.getOrNull(it+1)?.toIntOrNull() }
        val stops = Regex("(\\d+)\\s*stops").find(all)?.groupValues?.get(1)?.toIntOrNull()
        val km = (if (woltCard) number(ns.firstNotNullOfOrNull { Regex("^(\\d+(?:[.,]\\d+)?)\\s*km$").find(it)?.groupValues?.get(1) }) else null)
            ?: number(Regex("(\\d+(?:[.,]\\d+)?)\\s*km").find(all)?.groupValues?.get(1))
        val range = Regex("(\\d+)\\s*[–−-]\\s*(\\d+)\\s*min").find(all)
        val priced = lines.firstOrNull { amount(it) != null && norm(it).contains("min") }
        val duration = priced?.let { Regex("(\\d+)\\s*min").find(norm(it))?.groupValues?.get(1)?.toIntOrNull() }
        fun clock(rx: String): Int? = Regex(rx).find(all)?.groupValues?.get(1)?.replace('−','-')?.toIntOrNull()
        // D22 (Wolt 2.202.6): on the offer card and the way to the restaurant "Pickup 6 min" counts down to the order being ready,
        // then "now" (time is up, not "ready"), then "Ready". It is not a pickup deadline any more.
        val woltLabelStage = wolt && stage in setOf(ScreenStage.OFFER, ScreenStage.PICKUP)
        val woltReadyIn = if (woltLabelStage) ns.firstNotNullOfOrNull { n -> woltPickupIn.find(n)?.groupValues?.get(1)?.replace('−', '-')?.toIntOrNull() }
            ?: if (ns.any { it == "now" || it == "pickup now" }) 0 else null else null
        // R2 (16 Oct): Bolt's blue map bubble on the restaurant screen is the restaurant's minutes left ("4 min", or "4" + "min");
        // blue is always the restaurant there. "Arrive in 11 min" and "~8 min" are other minutes.
        val boltBubble = if (!wolt && stage == ScreenStage.PICKUP) ns.indices.firstNotNullOfOrNull { i ->
            boltBubbleLine.find(ns[i])?.groupValues?.get(1)?.toIntOrNull()
                ?: if (Regex("^\\d{1,3}$").matches(ns[i]) && ns.getOrNull(i + 1) == "min") ns[i].toIntOrNull() else null
        } else null
        val readyIn = clock("ready in\\s*([−-]?\\d+)\\s*min") ?: woltReadyIn ?: boltBubble
        val pickupClock = if (woltReadyIn != null) null else clock("(?:pickup|arrive in)\\s*([−-]?\\d+)\\s*min")
        val dropClock = clock("drop ?off(?: in)?\\s*([−-]?\\d+)\\s*min")

        var restaurant: String? = null; var restAddress: String? = null; var customerAddress: String? = null
        var cardCount: Int? = null
        // Wolt 2.202.6 batch: "2 pickups from" + restaurant (12 Oct, Mix Point Cafe).
        val pi = ns.indexOfFirst { it == "pickup from" || pickupsFrom.matches(it) }
        if (pi >= 0) pickupsFrom.find(ns[pi])?.groupValues?.get(1)?.toIntOrNull()?.let { cardCount = it }
        if (pi >= 0) { restaurant = lines.getOrNull(pi+1); restAddress = lines.drop(pi+2).takeWhile { norm(it) !in sections }.joinToString("\n").takeIf { it.isNotBlank() } }
        if (stage == ScreenStage.OFFER && woltCard) {
            // D2: "Delivery from" / "2 deliveries from" + restaurant; the last line (after "Accept") is the RESTAURANT's address.
            val fi = ns.indexOfFirst { it == "delivery from" || Regex("^\\d+ deliveries from$").matches(it) }
            if (fi >= 0) { restaurant = lines.getOrNull(fi + 1); cardCount = Regex("^(\\d+) deliveries").find(ns[fi])?.groupValues?.get(1)?.toIntOrNull() ?: 1 }
            val ai = ns.indexOfLast { isAccept(it) }
            restAddress = (if (ai >= 0 && ai < lines.lastIndex) lines.drop(ai + 1) else listOfNotNull(lines.lastOrNull { Regex("(?i)baku|az ?\\d{4}").containsMatchIn(it) }))
                .joinToString("\n").takeIf { it.isNotBlank() }
        } else if (stage == ScreenStage.OFFER) {
            val si = ns.indexOfFirst { it.contains("stops") }
            if (si >= 0) {
                restaurant = lines.getOrNull(si+1)
                val ci = index("customer drop-off", "customer drop off")
                if (ci > si) { restAddress = lines.subList(si+2, ci).joinToString("\n").takeIf { it.isNotBlank() }; customerAddress = lines.getOrNull(ci+1) }
            } else {
                // Bolt: name/address immediately before ~8 min, not a generic map POI.
                val ti = ns.indexOfFirst { it.startsWith("~") && it.contains("min") }
                if (ti >= 2) { restaurant = lines[ti-2]; restAddress = lines[ti-1] }
            }
        } else if (stage == ScreenStage.PICKUP && pi < 0) {
            val i = ns.indexOfLast { it.startsWith("arrive in") || it == "arrived" }
            if (i >= 0) { restaurant = lines.getOrNull(i+1)?.takeUnless { norm(it) in sections }; restAddress = lines.getOrNull(i+2)?.takeUnless { norm(it) in sections || it.startsWith("#") } }
        }
        // OCR reads Wolt's "Entrance | Floor | Apartment" row as three labels, then three values.
        val row = HashMap<String, String>()
        run {
            val i = ns.indices.firstOrNull { ns[it] in rowLabels && ns.getOrNull(it + 1) in rowLabels } ?: return@run
            var k = i; while (k < ns.size && ns[k] in rowLabels) k++
            val values = lines.subList(k, minOf(lines.size, k + (k - i)))
            if (values.size == k - i && values.none { norm(it) in sections || norm(it) in rowLabels })
                for (j in values.indices) row.putIfAbsent(rowLabels.getValue(ns[i + j]), values[j])
        }
        val di = index("dropoff to", "drop off to").takeIf { it >= 0 } ?: ns.indexOfFirst { dropoffsTo.matches(it) }
        val namedId = lines.firstOrNull { Regex("#[A-Za-z0-9]{4,}").containsMatchIn(it) && !it.startsWith("#") }
        val details = DeliveryDetails(
            name = if (di >= 0) lines.getOrNull(di+1) else namedId?.substringBefore('#')?.trim()?.takeIf { it.isNotBlank() },
            address = field("address", "unvan", "adres", "адрес", "delivery address", "drop-off address") ?: if (di >= 0) lines.getOrNull(di+2) else customerAddress,
            instructions = field("instructions", "telimatlar", "инструкции"),
            note = field("additional note", "note", "notes", "qeyd", "elave qeyd", "комментарий"),
            building = field("building name", "building", "block", "bina"), entrance = row["entrance"] ?: field("entrance", "giris", "подъезд"),
            floor = row["floor"] ?: field("floor", "kat", "mertebe", "этаж"), apartment = row["apartment"] ?: field("apartment", "flat", "unit", "daire", "menzil", "квартира"),
            doorCode = row["door"] ?: field("door code", "entrance code", "qapi kodu", "код двери"), checkId = all.contains("check id"),
            addressDetails = ns.indexOfLast { it == "address details" }.takeIf { it >= 0 }?.let { i ->
                lines.drop(i + 1).takeWhile { norm(it) !in sections && !norm(it).startsWith("#") }.joinToString("\n").trim().takeIf { it.isNotEmpty() } }
        ).fillFromText()
        customerAddress = details.address ?: customerAddress
        // A percentage is a boost only with boost wording on its line ("əlavə 30%"); "Acceptance rate 85%" is not (R03).
        // A boost that "Starts in 4 hours" is recorded as boostPct/boostWindow but is not the active bonusPct (FP10).
        val boost = readBoost(ns)
        val bonus = boost?.takeUnless { it.future }?.pct
        val bonusFrom = Regex("from\\s*(\\d{1,2}:\\d{2})").find(all)?.groupValues?.get(1)
        val demand = (if (demandIdx >= 0) (demandLevel.find(ns[demandIdx]) ?: ns.getOrNull(demandIdx + 1)?.let { demandLevel.find(it) })?.groupValues?.get(1) else null)
            ?.replaceFirstChar { it.uppercase() }
            ?: ns.firstOrNull { it.contains("delivery demand") }?.let { when { it.contains("high") -> "High"; it.contains("medium") -> "Medium"; it.contains("low") -> "Low"; else -> null } }
            ?: ns.firstOrNull { it in setOf("high", "medium", "low") }?.replaceFirstChar { it.uppercase() }
        // Live multipliers only on a home, offline or offer screen; never from a page such as Bolt's Earnings Schedule (VM3).
        val multipliers = if (stage !in setOf(ScreenStage.IDLE, ScreenStage.OFFLINE, ScreenStage.OFFER)) emptyList()
            else ns.mapNotNull { n -> multiplierLine.find(n)?.let { number(it.groups[1]?.value ?: it.groups[2]?.value) } }.filter { it in 1.0..10.0 }.distinct()
        // Bolt's card: "~8 min" (to the restaurant) then "~14 min" (to the customer). Read from the frame before
        // de-duplication so two equal legs survive; a single or a third value leaves both unknown.
        val etas = if (wolt || stage != ScreenStage.OFFER) emptyList() else shown.mapNotNull { boltEta.find(norm(it))?.groupValues?.get(1)?.toIntOrNull() }
        val legs = when { etas.size == 2 -> etas; etas.distinct().size == 2 -> etas.distinct(); else -> null }
        val stats = if (stage == ScreenStage.UNKNOWN && CourierScreens.isStats(ns)) readStats(lines, ns, at) else null
        val changeIdx = if (woltCard) ns.indexOfFirst { it == "total change needed" || it == "change needed" } else -1
        val changeNeeded = if (changeIdx >= 0) woltAmount(lines.getOrNull(changeIdx - 1)) ?: woltAmount(lines.getOrNull(changeIdx + 1))
            else if (woltCard && ns.any { it == "no change needed" }) 0.0 else null
        return CourierObservation(pkg, at, stage, complete, source, lines, taps, price, km, range?.groupValues?.get(1)?.toIntOrNull() ?: duration,
            range?.groupValues?.get(2)?.toIntOrNull() ?: duration, stops, count ?: cardCount, restaurant, restAddress, customerAddress,
            ids, details, readyIn, pickupClock, dropClock,
            // Platform deadline clocks can go negative; they are NOT a remaining travel ETA.
            travelEta = clock("(?:travel time|eta|away)\\s*:?\\s*(\\d+)\\s*min"),
            arrived = ns.any { it == "arrived" } && acts.none { isArrive(it) },
            ready = all.contains("order is ready for pickup") || ns.any { it == "ready for pickup" || it == "order is ready" || it == "order ready" } ||
                (woltLabelStage && ns.any { it == "ready" }), delivered = delivered,
            demand = demand, bonusPct = bonus, bonusFrom = bonusFrom, multipliers = multipliers,
            offlineUntil = Regex("offline until\\s*(\\d{1,2}:\\d{2})").find(all)?.groupValues?.get(1),
            deliverControl = stage == ScreenStage.DROPOFF && deliveryControl,
            availability = availability, availabilityEvidence = availabilityEvidence,
            declineDialog = declineDialog, declinedCount = declinedCount,
            preparing = ns.any { it.contains("being prepared") || it == "preparing" || it.contains("is preparing") },
            // On the pickup screen "Notes" describes the restaurant's location, not the customer.
            restaurantNote = if (stage == ScreenStage.PICKUP) details.note else null,
            pickupCode = if (stage == ScreenStage.PICKUP) lines.firstOrNull { Regex("^#[A-Za-z0-9]{3,}$").matches(it.trim()) }?.trim() else null,
            items = if (stage == ScreenStage.PICKUP) lines.filter { Regex("^\\d+\\s*[x×]\\s+\\S").containsMatchIn(it.trim()) }.take(6) else emptyList(),
            approachMin = legs?.get(0), deliveryMin = legs?.get(1),
            boostPct = boost?.pct, boostWindow = boost?.window, boostStartsInMin = boost?.startsInMin,
            statsEarningsAzn = stats?.earnings, statsTasksDone = stats?.tasks, statsCompletionRate = stats?.completion,
            statsAcceptanceRate = stats?.acceptance, statsRating = stats?.rating, statsPeriodLabel = stats?.period,
            offerCash = woltCard && ns.any { it == "cash order" }, changeNeeded = changeNeeded, boostSlots = readBoostSlots(shown.map(::norm)),
            statsPeriod = stats?.periodKey, statsDistanceKm = stats?.distanceKm, statsEarningsNoTipsAzn = stats?.noTips, statsTipsAzn = stats?.tips)
    }

    // ------------------------------------------------------------------ boosts
    private class Boost(val pct: Int, val window: String?, val startsInMin: Int?, val future: Boolean)
    private val boostWord = Regex("(?<![\\p{L}\\p{N}_])(?:extra|elave|ekstra|boost|bonus|дополнительно|бонус)(?![\\p{L}\\p{N}_])")
    private val percent = Regex("(?<![0-9.,])\\+?([0-9]{1,3})[\\s\\p{Z}]*%")
    private val timeRange = Regex("(?<![0-9])([0-2]?[0-9]):([0-5][0-9])[\\s\\p{Z}]*[-–—−][\\s\\p{Z}]*([0-2]?[0-9]):([0-5][0-9])(?![0-9])")
    private val startsInEn = Regex("starts in (?:(an?|[0-9]+) ?(?:hours?|hrs?|h)(?![\\p{L}]))?(?: ?(?:and )?([0-9]+) ?(?:minutes?|mins?|min|m)(?![\\p{L}]))?")
    private val startsInAz = Regex("(?<![\\p{L}\\p{N}_])(?:([0-9]+) ?saat)?(?: ?([0-9]+) ?(?:deqiqe|deq|dakika|dk))? ?sonra bas")
    private val startsInRu = Regex("через (?:([0-9]+) ?час[\\p{L}]*)?(?: ?([0-9]+) ?мин[\\p{L}]*)?")

    private fun startsInMin(n: String): Int? {
        for (rx in listOf(startsInEn, startsInAz, startsInRu)) {
            val m = rx.find(n) ?: continue
            val h = m.groups[1]?.value?.let { if (it == "a" || it == "an") 1 else it.toIntOrNull() }
            val min = m.groups[2]?.value?.toIntOrNull()
            if (h != null || min != null) return (h ?: 0) * 60 + (min ?: 0)
        }
        return null
    }

    /**
     * D24: every boost slot on screen. Wolt 2.202.6 shows them as "Hər sifariş üçün əlavə 30%" + "Today" + "17:00-18:00" (Earn extra,
     * "Coming up later"), as "… əlavə" + "Wol" + "20%" + "16:00-17:00" under "Now · Boost", or folded as "… əlavə 20% from 16:00".
     * The window is searched only forward, up to the next percentage, so neighbouring slots never borrow each other's hours.
     */
    /** [ns]: the frame's lines before de-duplication (M21: Earn extra repeats "17/10" and "… 10%" on several rows). */
    private fun readBoostSlots(ns: List<String>): List<BoostSlot> {
        val out = ArrayList<BoostSlot>()
        for (k in ns.indices) {
            val m = percent.find(ns[k]) ?: continue
            val worded = boostWord.find(ns[k])?.let { it.range.first < m.range.first } == true ||
                (m.range.first == 0 && (maxOf(0, k - 2) until k).any { boostWord.containsMatchIn(ns[it]) })
            if (!worded) continue
            val pct = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..300 } ?: continue
            var window: String? = null
            for (j in k..minOf(ns.lastIndex, k + 2)) {
                if (j > k && percent.containsMatchIn(ns[j])) break
                timeRange.find(ns[j])?.groupValues?.let { g -> window = "${g[1].padStart(2, '0')}:${g[2]}-${g[3].padStart(2, '0')}:${g[4]}" }
                if (window != null) break
            }
            val from = Regex("from\\s*(\\d{1,2}:\\d{2})").find(ns[k])?.groupValues?.get(1)?.padStart(5, '0')
            val active = (maxOf(0, k - 4)..k).any { Regex("^now\\W*boost$").matches(ns[it]) }
            // M21: Earn extra rows carry "Today" or "17/10" in the left column, read just before the row.
            val date = (k - 1 downTo maxOf(0, k - 2)).takeWhile { !percent.containsMatchIn(ns[it]) }
                .firstNotNullOfOrNull { Regex("^(\\d{1,2})/(\\d{1,2})$").find(ns[it])?.let { g -> "${g.groupValues[1].padStart(2, '0')}/${g.groupValues[2].padStart(2, '0')}" } }
            val slot = BoostSlot(pct, window, from, active, date)
            if (out.none { it.pct == slot.pct && it.window == slot.window && it.from == slot.from && it.date == slot.date }) out.add(slot)
        }
        return out
    }

    /** The boost on screen: its percentage line, the "18:00-20:00" window and "Starts in …" within three lines of it. */
    private fun readBoost(ns: List<String>): Boost? {
        val i = ns.indices.firstOrNull { k ->
            val m = percent.find(ns[k])
            m != null && (boostWord.find(ns[k])?.let { it.range.first < m.range.first } == true ||
                (m.range.first == 0 && ns.getOrNull(k - 1)?.let { boostWord.containsMatchIn(it) } == true))
        } ?: return null
        val pct = percent.find(ns[i])?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val near = (maxOf(0, i - 3)..minOf(ns.lastIndex, i + 3)).sortedBy { kotlin.math.abs(it - i) }
        val window = near.firstNotNullOfOrNull { timeRange.find(ns[it]) }?.groupValues
            ?.let { g -> "${g[1].padStart(2, '0')}:${g[2]}-${g[3].padStart(2, '0')}:${g[4]}" }
        val startsIn = near.firstNotNullOfOrNull { startsInMin(ns[it]) }
        val future = (startsIn != null && startsIn > 0) || near.any { ns[it].startsWith("starts ") || ns[it] == "upcoming" || ns[it].contains("coming up") }
        return Boost(pct, window, startsIn, future)
    }

    // ------------------------------------------------------------------ performance pages
    private class Performance(val earnings: Double?, val tasks: Int?, val completion: Double?, val acceptance: Double?, val rating: Double?, val period: String?,
        val periodKey: String? = null, val distanceKm: Double? = null, val noTips: Double? = null, val tips: Double? = null)
    private val statsMoney = Regex("^(?:(?:₼|azn|manat)[\\s\\p{Z}]*([0-9]+(?:[.,][0-9]{1,2})?)|([0-9]+(?:[.,][0-9]{1,2})?)[\\s\\p{Z}]*(?:₼|azn|manat))$")
    private val statsCount = Regex("^([0-9]{1,5})$")
    private val statsPercent = Regex("^([0-9]{1,3}(?:[.,][0-9]{1,2})?)[\\s\\p{Z}]*%$")
    private val statsStars = Regex("^([0-5](?:[.,][0-9]{1,2})?)(?:[\\s\\p{Z}]*[★⭐*])?$")
    private val earningsLabels = setOf("earnings (estimate)", "earnings", "gross earnings", "total earnings", "qazanc", "kazanc")
    private val tasksLabels = setOf("deliveries completed", "completed deliveries", "tasks completed", "completed tasks")
    private val completionLabels = setOf("task completion rate", "completion rate")
    private val acceptanceLabels = setOf("acceptance rate", "tasks accepted")
    private val statsKm = Regex("^([0-9]+(?:[.,][0-9]{1,2})?)[\\s\\p{Z}]*km$")
    private val distanceLabels = setOf("distance on- and off-task", "distance on and off task", "distance")
    private val noTipsLabels = setOf("earnings without tips")
    private val tipsLabels = setOf("tips")
    private val ratingLabels = setOf("rating", "your rating", "average rating", "customer rating", "reyting")
    /** Period names and the tab row's words; one of them alone on the page is its period, several (tabs) say nothing. */
    private val periods = setOf("today", "yesterday", "this week", "last week", "this month", "last month", "week", "month", "day", "custom",
        "bu gun", "dunen", "bu hefte", "bu ay", "bugun", "dun", "bu hafta")

    /** The value shown for [labels]: on the label's own line ("Acceptance rate 85%", "Rating: 4.9") or on the next line. */
    private fun stat(ns: List<String>, labels: Set<String>, rx: Regex, except: Set<String> = emptySet()): String? {
        for (i in ns.indices) {
            if (except.any { ns[i] == it || ns[i].startsWith("$it ") }) continue
            val label = labels.filter { ns[i] == it || ns[i].startsWith("$it ") || ns[i].startsWith("$it:") }.maxByOrNull { it.length } ?: continue
            val inline = ns[i].removePrefix(label).trim(':', ' ')
            val m = inline.takeIf { it.isNotEmpty() }?.let { rx.matchEntire(it) } ?: ns.getOrNull(i + 1)?.let { rx.matchEntire(it) } ?: continue
            return (m.groups[1]?.value ?: m.groups[2]?.value)?.replace(',', '.')
        }
        return null
    }

    private fun readStats(lines: List<String>, ns: List<String>, at: Long): Performance {
        fun rate(s: String?) = s?.let { java.math.BigDecimal(it).movePointLeft(2).toDouble() }?.takeIf { it in 0.0..1.0 }
        val period = lines.filterIndexed { i, _ -> ns[i] in periods }
        return Performance(earnings = stat(ns, earningsLabels, statsMoney, noTipsLabels)?.toDoubleOrNull(), tasks = stat(ns, tasksLabels, statsCount)?.toIntOrNull(),
            completion = rate(stat(ns, completionLabels, statsPercent)), acceptance = rate(stat(ns, acceptanceLabels, statsPercent)),
            rating = stat(ns, ratingLabels, statsStars)?.toDoubleOrNull()?.takeIf { it in 0.0..5.0 },
            period = period.singleOrNull()?.take(40), periodKey = statsPeriod(ns, at),
            distanceKm = stat(ns, distanceLabels, statsKm)?.toDoubleOrNull(), noTips = stat(ns, noTipsLabels, statsMoney)?.toDoubleOrNull(),
            tips = stat(ns, tipsLabels, statsMoney)?.toDoubleOrNull())
    }

    // F8.1: Wolt Stats date header. Month names in English (full or Wolt's 3 letters; OCR reads O as 0) and Azerbaijani.
    private val monthNames = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val monthNamesAz = listOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avqust", "sentyabr", "oktyabr", "noyabr", "dekabr")
    private fun month(w: String): Int? {
        val x = w.replace('0', 'o')
        if (x.length < 3) return null
        val i = monthNames.indexOfFirst { it == x || (x.length == 3 && it.startsWith(x)) }.takeIf { it >= 0 } ?: monthNamesAz.indexOf(x).takeIf { it >= 0 }
        return i?.plus(1)
    }
    private const val M = "([a-z0]{3,9})"
    private val dayHeader = Regex("^([0-9]{1,2}) $M(?: ([0-9]{4}))?$")
    private val weekHeader = Regex("^([0-9]{1,2}) $M(?: ([0-9]{4}))? ?[-–] ?([0-9]{1,2}) $M(?: ([0-9]{4}))?$")
    private val monthHeader = Regex("^([a-z]{3,9})(?: ([0-9]{4}))?$")

    /** F8.1: the Stats page's date header ("16 October", "11 Oct - 16 Oct", "October") as "day:2026-10-16",
     *  "week:2026-10-11..2026-10-16" or "month:2026-10"; null when there is none or more than one. Without a printed year the
     *  year is [at]'s, or the year before when the month is later than [at]'s. [ns]: normalised lines. */
    fun statsPeriod(ns: List<String>, at: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): String? {
        val now = java.util.Calendar.getInstance(tz).apply { timeInMillis = at }
        val nowY = now.get(java.util.Calendar.YEAR); val nowM = now.get(java.util.Calendar.MONTH) + 1
        fun year(m: Int, y: String?) = y?.toIntOrNull() ?: if (m > nowM) nowY - 1 else nowY
        fun date(d: Int, m: Int, y: Int): String? {
            val c = java.util.Calendar.getInstance(tz).apply { clear(); set(y, m - 1, 1) }
            if (d < 1 || d > c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)) return null
            return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
        }
        val found = ns.mapNotNull { n ->
            weekHeader.matchEntire(n)?.let { w ->
                val g = w.groupValues
                val m1 = month(g[2]) ?: return@let null; val m2 = month(g[5]) ?: return@let null
                val y2 = year(m2, g[6].ifEmpty { null }); val y1 = g[3].toIntOrNull() ?: if (m1 > m2) y2 - 1 else y2
                val a = date(g[1].toInt(), m1, y1) ?: return@let null; val b = date(g[4].toInt(), m2, y2) ?: return@let null
                "week:$a..$b"
            } ?: dayHeader.matchEntire(n)?.let { d ->
                val m = month(d.groupValues[2]) ?: return@let null
                date(d.groupValues[1].toInt(), m, year(m, d.groupValues[3].ifEmpty { null }))?.let { "day:$it" }
            } ?: monthHeader.matchEntire(n)?.let { h ->
                val m = (monthNames.indexOf(h.groupValues[1]).takeIf { it >= 0 } ?: monthNamesAz.indexOf(h.groupValues[1]).takeIf { it >= 0 })?.plus(1) ?: return@let null
                String.format(Locale.US, "month:%04d-%02d", year(m, h.groupValues[2].ifEmpty { null }), m)
            }
        }.distinct()
        return found.singleOrNull()
    }
}
