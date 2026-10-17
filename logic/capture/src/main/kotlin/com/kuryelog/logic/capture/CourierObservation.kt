package com.kuryelog.logic.capture

// İskelet (A3.2): imzalar ve veri tipleri Rawstats'tan (CourierParser.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.4'te.

/** Window-local facts. A missing/hidden window is NEVER an idle observation. */
enum class ScreenStage { OFFER, PICKUP, DROPOFF, IDLE, OFFLINE, UNKNOWN }

data class DeliveryDetails(
    var name: String? = null, var address: String? = null, var note: String? = null,
    var instructions: String? = null, var building: String? = null, var entrance: String? = null,
    var floor: String? = null, var apartment: String? = null, var doorCode: String? = null,
    var checkId: Boolean = false, var addressDetails: String? = null,
    /** Card and courier.db only (K25). Never in logs or backups. */
    var phone: String? = null
) {
    fun merge(d: DeliveryDetails): Unit = TODO("A3.4")
    /** Structured fields left empty by the customer are taken from their free text. */
    fun fillFromText(): DeliveryDetails = TODO("A3.4")
    val noteTruncated: Boolean get() = TODO("A3.4")
    fun facts(az: Boolean = false): List<String> = TODO("A3.4")
    fun display(az: Boolean = false): String = TODO("A3.4")
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
