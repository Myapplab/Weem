package com.kuryelog.logic.orders

import com.kuryelog.logic.core.*
import org.json.JSONObject
import java.io.File

/**
 * A3.8: what the Siparişler tab reads and may do (ORDERS.md 10.3; RS `MainActivity` → `OrderTracker` okuyucuları ve düzeltmeleri).
 * Ekran modeli ([OrdersScreen]) yalnız bunu görür; Android'de [LogOrdersSource] kurulur, canlı akış ([OrderFlow], A3.7) varsa
 * bağlanır. Akış yokken gün sayfaları `courier.db` ve gün dosyalarından (saklı kayıtlar, içe aktarılan Rawstats günleri dahil) çizilir;
 * düzeltmeler ([canAct]) kapalı kalır.
 */
interface OrdersSource {
    /** Günün anahtarı (`LogTime.day`, cihazın yerel günü). */
    fun day(t: Long = nowMs()): String
    fun nowMs(): Long
    fun zone(): java.time.ZoneId
    /** Bugünün günü. */
    fun today(): String = day()

    /** Saklı bütün siparişler (`courier.db`); bozuk satır atlanır. */
    fun stored(): List<TripRecord>
    /** Bugünün canlı kayıtları, yeniden eskiye; izleme çalışmıyorsa boş. */
    fun live(): List<TripRecord>
    fun stats(): OrderFlow.Stats
    /** "ŞU AN" metni: açık teklif ve siparişler, platform durumları (bugün sayacı satırı hariç); boşsa "". */
    fun nowLines(): String
    fun onlineToday(): List<OnlineSessions.Closed>

    fun platformTotals(day: String): List<PlatformTotals.Total>
    fun platformHistoryRows(day: String): List<JSONObject>
    fun cashPendingRows(day: String): List<JSONObject>
    fun pocketCashRows(day: String): List<JSONObject>
    fun boostRows(day: String): List<JSONObject>
    fun cashTipRows(day: String): Map<String, JSONObject>

    /** O günün vardiya km'si ve aktif süresi (biten vardiyalar + açık vardiya, yalnız bugünse). */
    fun shiftKm(day: String): Double
    fun shiftMinutes(day: String): Double

    /** Kayıtlar: gün klasörleri (arşiv işaretiyle), yeniden eskiye. */
    fun recordDays(): List<DailyRecords.Day>

    /** Canlı akış bağlı mı: düzeltme düğmeleri ancak o zaman gösterilir. */
    val canAct: Boolean get() = false
    fun shiftOn(): Boolean
    fun setTip(id: String, tip: Double): Boolean = false
    /** K32: alınan nakit cevabı; null = kaydedilemedi (sipariş bilinmiyor ya da nakit değil). */
    fun recordCashTip(id: String, received: Double): JSONObject? = null
    fun confirmDelivered(id: String): Boolean = false
    fun showCustomerCard(id: String): Boolean = false
}

/**
 * Dosya ve veritabanı okuyan [OrdersSource]. [flow] null ise yalnız saklı kayıtlar; verilince canlı kopya saklıyı ezer (RS
 * `OrderHistory.days`), "ŞU AN", açık oturumlar ve düzeltmeler akışa gider.
 */
class LogOrdersSource(
    private val log: LogStore,
    private val db: CourierRecords,
    private val prefs: KuryelogPrefs.Values,
    private val clock: Clock,
    private val flow: OrderFlow? = null,
) : OrdersSource {
    override fun day(t: Long) = log.day(t)
    override fun nowMs() = clock.wallMs()
    override fun zone() = clock.zone()

    override fun stored(): List<TripRecord> = db.trips().mapNotNull { runCatching { RecordJson.trip(it) }.getOrNull() }
    override fun live(): List<TripRecord> = flow?.today().orEmpty()

    /** Bugünün siparişleri: canlı varsa canlı, yoksa saklı (courier.db her ~1,5 sn'de bir güncellenir). */
    private fun todayTrips(): List<TripRecord> {
        val day = today()
        val live = live()
        return if (flow != null) live else stored().filter { day(it.acceptedAt) == day }
    }

    override fun stats(): OrderFlow.Stats {
        val today = todayTrips()
        // D3(c)/D4: unverified (inferred) deliveries count too; only their label differs.
        val done = today.filter { it.countsAsDelivered }
        val net = done.sumOf { it.net ?: 0.0 }
        val hours = shiftMinutes(today()) / 60.0
        return OrderFlow.Stats(OrderHistory.deliveredOrders(done), net, if (hours > 0.01) net / hours else 0.0, today.sumOf { it.expectedOrders },
            db.offerCount(today(), "rejected"), today.count { it.phase == TripPhase.END_UNCONFIRMED && !it.deliveredInferred })
    }

    override fun nowLines(): String =
        flow?.badgeLines()?.trim()?.lines()?.dropLast(1)?.joinToString("\n")?.trim().orEmpty()

    override fun onlineToday(): List<OnlineSessions.Closed> = flow?.onlineToday()
        ?: rows(today(), OrderFlow.ONLINE_SESSIONS_FILE).mapNotNull { runCatching { OnlineSessions.closed(it) }.getOrNull() }.sortedBy { it.startAt }

    private fun file(day: String, name: String) = File(log.dayDir(day), name)
    private fun rows(day: String, name: String): List<JSONObject> = try {
        val f = file(day, name)
        if (!f.exists()) emptyList() else f.useLines { s -> s.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList() }
    } catch (e: Exception) { emptyList() }

    override fun platformTotals(day: String): List<PlatformTotals.Total> = try {
        val f = file(day, PlatformTotals.FILE)
        if (!f.exists()) emptyList() else f.useLines { PlatformTotals.latest(it.asIterable()) }
    } catch (e: Exception) { emptyList() }
    override fun platformHistoryRows(day: String) = rows(day, PlatformHistory.FILE)
    override fun cashPendingRows(day: String) = rows(day, CashPending.FILE)
    override fun pocketCashRows(day: String) = rows(day, PocketCash.FILE)
    override fun boostRows(day: String) = rows(day, BoostLog.FILE)
    override fun cashTipRows(day: String): Map<String, JSONObject> = try {
        val f = file(day, CashTips.FILE)
        if (!f.exists()) emptyMap() else f.useLines { CashTips.latest(it) }
    } catch (e: Exception) { emptyMap() }

    override fun shiftKm(day: String): Double {
        val ended = rows(day, EVENTS_FILE).filter { it.optString("type") == "shift_end" }.sumOf { j -> if (j.has("km")) j.optDouble("km", 0.0).takeIf { it.isFinite() } ?: 0.0 else 0.0 }
        val running = if (shiftOn() && day == today()) prefs.getFloat(KuryelogPrefs.DIST, 0f) / 1000.0 else 0.0
        return ended + running
    }

    override fun shiftMinutes(day: String): Double {
        val ended = EndedShiftMinutes().of(file(day, EVENTS_FILE))
        val running = if (shiftOn() && day == today()) (nowMs() - prefs.getLong(KuryelogPrefs.SHIFT_START, nowMs())).coerceAtLeast(0) / 60000.0 else 0.0
        return ended + running
    }

    override fun recordDays(): List<DailyRecords.Day> = DailyRecords(log.logsRoot).days()

    override val canAct get() = flow != null
    override fun shiftOn() = KuryelogPrefs.shiftOn(prefs)
    override fun setTip(id: String, tip: Double): Boolean = flow?.let { it.setTip(id, tip); true } ?: false
    override fun recordCashTip(id: String, received: Double): JSONObject? = flow?.recordCashTip(id, received, CashTips.VIA_CARD)
    override fun confirmDelivered(id: String): Boolean = flow?.let { it.confirmDelivered(id); true } ?: false
    override fun showCustomerCard(id: String): Boolean = flow?.let { it.showDetails(id); true } ?: false

    private companion object { const val EVENTS_FILE = "events.jsonl" }
}
