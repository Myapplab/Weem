package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// A3.8 — Siparişler sekmesinin içeriği (ORDERS.md 10.3). Rawstats `MainActivity.kt` `renderHome` (Bugün kartı), `renderDayPages`,
// `dayOrders`, `renderOrderDay`, `cashNote`, `officialNote`, `missedNote`, `boostNote`, `renderTrip` metinleri satır satır; fark:
// ekran Compose, bu sınıf yalnız metni ve sırayı üretir (Compose [Block]'ları çizer, Android'siz test edilir). Farklar: ORDERS.md 16.3.

/** Platform rengi: Wolt, Bolt Food ya da nötr (Rawstats `platformColor`/`platformBg`). */
enum class Tone { NEUTRAL, WOLT, BOLT }

/** Satırın ağırlığı ve rengi (Rawstats `text(c, s, size, bold, color)`). */
enum class Style { BODY, MUTED, BOLD, ACCENT_BOLD, DANGER, DANGER_BOLD, TITLE }

/** Kart bölümleri. */
sealed interface Block {
    data class Line(val text: String, val style: Style = Style.BODY) : Block
    /** Büyük sayı (Net kazanç). */
    data class Big(val text: String) : Block
    /** "● Wolt · …" başlık satırı. */
    data class Heading(val text: String, val tone: Tone = Tone.NEUTRAL) : Block
    data class Sub(val text: String) : Block
    data class Tiles(val items: List<Tile>) : Block
    /** Sipariş kartındaki müşteri notu (ad + ayrıntı; ekranda görünür, dışa aktarmada gizlenir: ORDERS.md 14.2). */
    data class Person(val name: String, val details: String) : Block
    data object Divider : Block
}

/** Sayı karosu; [metric] doluysa dokununca o ortalamanın günlük listesi açılır (E1). */
data class Tile(val value: String, val label: String, val tone: Tone = Tone.NEUTRAL, val metric: OrderHistory.Metric? = null)

data class Card(val blocks: List<Block>, val tone: Tone = Tone.NEUTRAL, val hero: Boolean = false, val outlined: Boolean = false)

/** Bugün kartındaki açık sipariş için "müşteri kartını göster" düğmesi. */
data class ActiveTrip(val tripId: String, val platform: String, val label: String)

/** "ŞU AN" kartı ve Bugün (Rawstats ana sayfası: ŞU AN, Net kazanç, platform kartları). */
data class Today(val nowTitle: String, val now: String?, val active: List<ActiveTrip>, val hero: Card, val platforms: List<Card>, val report: Card? = null)

data class MetricList(val title: String, val summary: String, val rows: List<Pair<String, Boolean>>, val empty: String?)

/** Gün sayfasındaki bir sipariş satırı. */
data class OrderRow(val tripId: String, val tone: Tone, val showDot: Boolean, val title: String, val line: String, val status: String?, val amounts: String)

data class DayPage(val date: String, val rows: List<OrderRow>, val empty: String?)

/** Sayfa başlığı: tarih, toplamlar, km ve aktif süre, "N / M gün". */
data class DayHeader(val date: String, val title: String, val totals: String, val activity: String, val indicator: String)

/** Sipariş kartındaki düğmeler (yalnız canlı akış bağlıyken ve bugünün siparişinde). */
sealed interface TripAction {
    val label: String
    data class EditTip(override val label: String, val title: String, val initial: String) : TripAction
    data class CashAnswer(override val label: String, val title: String, val initial: String?, val badInput: String) : TripAction
    data class CustomerCard(override val label: String) : TripAction
    data class ConfirmDelivered(override val label: String, val title: String, val message: String, val confirm: String) : TripAction
}

data class TripCard(val tripId: String, val card: Card, val actions: List<TripAction>)

/** Gün ayrıntısı: başlık kartı, nakit, resmî değerler, kaçırılanlar, boost, sonra her sipariş (kabul sırasıyla). */
data class DayDetail(val date: String, val platform: String?, val header: Card, val cards: List<Card>, val trips: List<TripCard>, val empty: String?)

/** Sekmenin sabit metinleri (G3). */
class OrdersLabels(private val tl: (String, String) -> String) {
    val ordersTab = tl("Siparişler", "Sifarişlər")
    val offerScoreTab = tl("Teklif puanı", "Təklif balı")
    val allOrders = tl("Tüm siparişler", "Bütün sifarişlər")
    val older = tl("Önceki gün", "Əvvəlki gün")
    val newer = tl("Sonraki gün", "Sonrakı gün")
    val dayDetails = tl("Günün tüm ayrıntıları", "Günün bütün təfərrüatları")
    val today = tl("Bugün", "Bu gün")
    val back = tl("Geri", "Geri")
    val close = tl("Kapat", "Bağla")
    val save = tl("Kaydet", "Yadda saxla")
    val cancel = tl("İptal", "Ləğv et")
    val noRecords = tl("Henüz kayıt yok", "Hələ qeyd yoxdur")
    val noOrdersToday = tl("Bugün teslim edilmiş sipariş yok.", "Bu gün çatdırılmış sifariş yoxdur.")
    val error = tl("Hata: ", "Xəta: ")
    val showArchive = tl("Arşivi aç", "Arxivi aç")
    val showActive = tl("Günlük kayıtlara dön", "Gündəlik qeydlərə qayıt")
    val dailyRecords = tl("GÜNLÜK KAYITLAR", "GÜNLÜK QEYDLƏR")
    val recordFiles = tl("Kayıt dosyaları", "Qeyd faylları")
    val archive = tl("Arşivle", "Arxivlə")
    val unarchive = tl("Arşivden çıkar", "Arxivdən çıxar")
    val archived = tl("Arşivde", "Arxivdə")
    val delete = tl("Sil", "Sil")
    val deleteConfirm = tl("Bu kayıt kalıcı olarak silinecek. Önce paylaşarak yedekleyebilirsin.", "Bu qeyd həmişəlik silinəcək. Əvvəl paylaşaraq ehtiyat nüsxə saxlaya bilərsən.")
    val recordStop = tl("Bugünün kaydını silmek veya arşivlemek için vardiyayı durdur.", "Bu günün qeydini silmək/arxivləmək üçün növbəni dayandır.")
    val recordFilesCount = tl("kayıt dosyası", "qeyd faylı")
    fun platformLabel(platform: String?) = platform ?: allOrders
}

class OrdersScreen(private val src: OrdersSource, lang: Lang) {
    private val az = lang == Lang.AZ
    private fun tl(tr: String, az: String) = if (this.az) az else tr
    val labels = OrdersLabels(::tl)

    private val clockFormat by lazy { DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(src.zone()) }
    private fun clock(t: Long?) = t?.let { clockFormat.format(Instant.ofEpochMilli(it)) } ?: "—"
    private fun f1(n: Double) = String.format(Locale.US, "%.1f", n)
    private fun f2(n: Double) = String.format(Locale.US, "%.2f", n)
    private fun platformOf(pkg: String) = Platform.name(pkg)
    private fun toneOf(platform: String?) = when (platform) { "Wolt" -> Tone.WOLT; "Bolt Food" -> Tone.BOLT; else -> Tone.NEUTRAL }
    private fun dk(m: Double) = OrderHistory.durationText(m, ::tl)
    /** Whole minutes, the sign kept ("-3 dk" = earlier than the platform's upper estimate). */
    private fun minutesText(m: Double) = if (m < -0.5) "-" + dk(-m) else dk(m.coerceAtLeast(0.0))
    private val todayText = tl("Bugün", "Bu gün")

    /** Saklı kayıtlar ve canlı kopya bir kez okunur (bir örnek = bir görüntü); canlı kayıt saklıyı ezer. */
    private val merged: List<TripRecord> by lazy {
        val byId = LinkedHashMap<String, TripRecord>()
        (src.stored() + src.live()).forEach { byId[it.id] = it }
        byId.values.toList()
    }
    private val dayCache = HashMap<String?, List<OrderHistory.Day>>()

    /** Gün listesi (yeniden eskiye); [platform] null = Tüm siparişler. */
    fun days(platform: String? = null): List<OrderHistory.Day> =
        dayCache.getOrPut(platform) { OrderHistory.days(merged, emptyList(), platform, ::platformOf) { src.day(it) } }

    /** Okumaları baştan yapar (arayüz çizmeden önce). */
    fun warm(): OrdersScreen { days(); OrderHistory.PLATFORMS.forEach { days(it) }; return this }

    // ---------- Bugün ----------

    /** Rawstats `renderHome`'un "ŞU AN" ve "Net kazanç" kartları ile `renderTodayPlatforms`. */
    fun today(): Today {
        val st = src.stats()
        val sessions = src.onlineToday()
        val trips = days().firstOrNull { it.date == src.today() }?.trips.orEmpty()
        val both = trips.filter { platformOf(it.pkg) in OrderHistory.PLATFORMS }
        val byPlatform = OrderHistory.PLATFORMS.map { name -> name to OrderHistory.Day(src.today(), trips.filter { platformOf(it.pkg) == name }) }
        val a = OrderHistory.averages(both, ::platformOf)
        fun m(v: Double?) = v?.let { f1(it) + " " + tl("dk", "dəq") } ?: "—"
        val wh = a.energyWh?.let { f1(it) + " Wh" } ?: "—"
        // A4.2 (S8): enerji maliyeti varsa net'ten düşer (rapor kartı `:economics`'ten); yoksa Rawstats'ın sayısı değişmez.
        val summary = src.daySummary(OrderHistory.Day(src.today(), both), true, ::tl)
        val cost = summary?.energyCostAzn ?: 0.0
        val hours = src.shiftMinutes(src.today()) / 60.0
        val net = st.net - cost
        val perHour = if (cost > 0.0) (if (hours > 0.01) net / hours else 0.0) else st.perHour
        val blocks = listOf(
            Block.Line(if (cost > 0.0) tl("Net kazanç (enerji maliyeti düşülmüş)", "Xalis qazanc (enerji xərci çıxılmış)") else tl("Net kazanç", "Xalis qazanc"), Style.MUTED),
            Block.Big(f2(net) + " ₼"),
            Block.Tiles(listOf(Tile(st.delivered.toString(), tl("teslim", "çatdırılma")), Tile(f1(perHour) + " ₼", tl("saat başına", "saat başına")),
                Tile(dk(sessions.sumOf { it.minutes }), tl("çevrimiçi", "onlayn")))),
            Block.Divider,
            Block.Tiles(listOf(Tile(sessions.sumOf { it.offers }.toString(), tl("görülen teklif", "görülən təklif")), Tile(st.accepted.toString(), tl("kabul", "qəbul")),
                Tile(st.rejected.toString(), tl("ret", "imtina")))),
            Block.Tiles(listOf(Tile(f2(byPlatform.sumOf { it.second.gross }) + " ₼", tl("brüt", "ümumi")), Tile(f2(byPlatform.sumOf { it.second.tips }) + " ₼", tl("bahşiş", "çaypulu")))),
            Block.Divider,
            Block.Line(tl("Sipariş başına ortalama", "Sifariş başına orta"), Style.MUTED),
            Block.Tiles(listOf(Tile(wh, tl("batarya", "batareya"), metric = OrderHistory.Metric.ENERGY), Tile(m(a.restaurantMin), tl("restoranda", "restoranda"), metric = OrderHistory.Metric.RESTAURANT),
                Tile(m(a.customerMin), tl("müşteride", "müştəridə"), metric = OrderHistory.Metric.CUSTOMER))),
            Block.Tiles(listOf(Tile(m(a.orderMin), tl("harcanan süre", "sərf olunan vaxt"), metric = OrderHistory.Metric.ORDER),
                Tile(m(a.beforeOfferMin), tl("sipariş öncesi", "sifarişdən əvvəl"), metric = OrderHistory.Metric.BEFORE_OFFER))),
        )
        val platforms = byPlatform.filter { it.second.trips.isNotEmpty() }.map { (name, d) ->
            val tone = toneOf(name)
            val b = mutableListOf<Block>(Block.Heading(name, tone),
                Block.Tiles(listOf(Tile(d.orders.toString(), tl("teslim", "çatdırılma"), tone), Tile(f2(d.gross) + "₼", tl("brüt", "ümumi")), Tile(f2(d.net) + "₼", tl("net", "xalis")))),
                Block.Tiles(listOf(Tile(f1(d.waitMinutes) + " " + tl("dk", "dəq"), tl("bekleme", "gözləmə")),
                    Tile(f1(d.lateMinutes) + " " + tl("dk", "dəq"), tl("hazır sonrası bekleme", "hazırdan sonra gözləmə")), Tile(f2(d.tips) + "₼", tl("bahşiş", "çaypulu")))))
            if (d.unpriced > 0) b += Block.Line(unpricedNote(d.unpriced), Style.MUTED)
            Card(b, tone, outlined = false)
        }
        val now = src.nowLines().takeIf { it.isNotEmpty() }
        val active = if (now != null && src.canAct && src.shiftOn()) src.live().filter { it.active }.map {
            val p = platformOf(it.pkg)
            ActiveTrip(it.id, p, tl("$p müşteri kartını göster", "$p müştəri kartını göstər"))
        } else emptyList()
        return Today(tl("ŞU AN", "İNDİ"), now, active, Card(blocks, hero = true), platforms, summary?.card)
    }

    private fun unpricedNote(n: Int) = tl("Fiyatı okunamayan $n sipariş brüt ve net toplamına eklenmedi.", "Qiyməti oxunmayan $n sifariş ümumi və xalis cəmə əlavə edilmədi.")

    /** E1: bir ortalamanın dayandığı bugünkü siparişlerin listesi ("HH:mm  Wolt · restoran · değer" ya da "—" ve neden). */
    fun metricList(metric: OrderHistory.Metric): MetricList {
        val trips = days().firstOrNull { it.date == src.today() }?.trips.orEmpty().filter { platformOf(it.pkg) in OrderHistory.PLATFORMS }
        val rows = OrderHistory.metricRows(trips, metric, ::platformOf, ::tl)
        val avg = OrderHistory.averages(trips, ::platformOf).let { av -> when (metric) {
            OrderHistory.Metric.ENERGY -> av.energyWh?.let { f1(it) + " Wh" }
            OrderHistory.Metric.RESTAURANT -> av.restaurantMin?.let { f1(it) + " " + tl("dk", "dəq") }
            OrderHistory.Metric.CUSTOMER -> av.customerMin?.let { f1(it) + " " + tl("dk", "dəq") }
            OrderHistory.Metric.ORDER -> av.orderMin?.let { f1(it) + " " + tl("dk", "dəq") }
            OrderHistory.Metric.BEFORE_OFFER -> av.beforeOfferMin?.let { f1(it) + " " + tl("dk", "dəq") }
        } }
        val title = when (metric) {
            OrderHistory.Metric.ENERGY -> tl("Batarya", "Batareya")
            OrderHistory.Metric.RESTAURANT -> tl("Restoranda", "Restoranda")
            OrderHistory.Metric.CUSTOMER -> tl("Müşteride", "Müştəridə")
            OrderHistory.Metric.ORDER -> tl("Harcanan süre", "Sərf olunan vaxt")
            OrderHistory.Metric.BEFORE_OFFER -> tl("Sipariş öncesi", "Sifarişdən əvvəl")
        }
        val measured = rows.count { it.value != null }
        val summary = tl("Ortalama", "Orta") + ": " + (avg ?: "—") + " · " + tl("$measured/${rows.size} siparişte ölçüldü", "$measured/${rows.size} sifarişdə ölçülüb")
        return MetricList("$title · $todayText", summary,
            rows.map { clock(it.at) + "  " + OrderHistory.metricRowText(it, metric, ::tl) to (it.value == null) },
            if (rows.isEmpty()) labels.noOrdersToday else null)
    }

    // ---------- Gün sayfaları ----------

    /**
     * Sayfa tarihleri, yeniden eskiye (sola kaydırma eski güne). Siparişler sekmesi: sipariş günleri. Kayıtlar ([records]): sipariş günleri +
     * kayıt günleri; [archive] açıkken yalnız arşivlenmiş kayıt günleri, açık değilken arşivlenmiş günler görünmez (Rawstats `renderDayPages`).
     */
    fun dates(records: Boolean = false, archive: Boolean = false): List<String> {
        val logs = if (records) src.recordDays() else emptyList()
        return RecordPages.dates(if (records && archive) emptyList() else days().map { it.date }, logs.filter { it.archived == archive }.map { it.date })
            .filter { date -> logs.none { it.date == date && it.archived != archive } }
    }

    /** Açılışta seçili sayfa: seçili gün, yoksa bugün, o da yoksa ilk; boşsa -1. */
    fun index(dates: List<String>, selected: String?): Int = RecordPages.index(dates, selected, src.today())

    fun dayTitle(date: String) = OrderHistory.dateLabel(date, az) + if (date == src.today()) " · $todayText" else ""

    private fun dayTotals(d: OrderHistory.Day) =
        tl("${d.orders} sipariş · Brüt ${f2(d.gross)}₼ · Net ${f2(d.net)}₼", "${d.orders} sifariş · Ümumi ${f2(d.gross)}₼ · Xalis ${f2(d.net)}₼")

    /** Başlık: tarih + gün adı, toplamlar (her platform), o günün vardiya km'si ve aktif süresi (yeni süre tahmini yok). */
    fun header(date: String, index: Int, count: Int): DayHeader {
        val d = days().firstOrNull { it.date == date } ?: OrderHistory.Day(date, emptyList())
        val weekday = LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag(if (az) "az" else "tr"))
        return DayHeader(date, dayTitle(date) + " · " + weekday, dayTotals(d),
            "${f1(src.shiftKm(date))} km · " + tl("Aktif süre: ", "Aktiv müddət: ") + dk(src.shiftMinutes(date)),
            "${index + 1} / $count " + tl("gün", "gün"))
    }

    /** Bir günün sipariş satırları (liste saati sırasıyla; kompakt, ayrıntı kartı "Günün tüm ayrıntıları"nda). */
    fun page(date: String, platform: String? = null): DayPage {
        val all = days().firstOrNull { it.date == date } ?: OrderHistory.Day(date, emptyList())
        val d = OrderHistory.Day(date, all.trips.filter { platform == null || platformOf(it.pkg) == platform })
        val trips = d.trips.sortedBy { OrderHistory.listTime(it, platformOf(it.pkg)) }
        val rows = trips.map { trip ->
            val name = platformOf(trip.pkg)
            val unverified = if (trip.deliveredInferred && trip.verifiedBy == null) " · " + tl("doğrulanmadı", "təsdiqlənmədi") else ""
            OrderRow(trip.id, toneOf(name), platform == null,
                "${clock(OrderHistory.listTime(trip, name))}  ${OrderHistory.restaurant(trip) ?: tl("Sipariş", "Sifariş")}$unverified",
                // E2: the order's restaurant wait under its name ("—" when it was not measured); E3 boost and temperature follow.
                OrderHistory.listLine(trip, ::tl),
                if (!trip.countsAsDelivered) OrderHistory.status(trip, ::tl) else null,
                trip.earnings?.let { g -> tl("Brüt ${f2(g)}₼\nNet ${f2(trip.net ?: g)}₼", "Ümumi ${f2(g)}₼\nXalis ${f2(trip.net ?: g)}₼") } ?: tl("Fiyat okunamadı", "Qiymət oxunmadı"))
        }
        return DayPage(date, rows, if (rows.isEmpty()) tl("Bu gün için sipariş kaydı yok.", "Bu gün üçün sifariş qeydi yoxdur.") else null)
    }

    // ---------- Gün ayrıntısı ----------

    /** Bir gün: toplamlar, nakit, resmî değerler, kaçırılanlar, boost, sonra her sipariş tam kart (teklif zamanı sırasıyla). */
    fun dayDetail(date: String, platform: String? = null): DayDetail {
        val d = days(platform).firstOrNull { it.date == date } ?: OrderHistory.Day(date, emptyList())
        val tone = if (platform == null) Tone.NEUTRAL else toneOf(platform)
        val head = mutableListOf<Block>(Block.Heading(labels.platformLabel(platform), tone), Block.Line(dayTitle(date), Style.TITLE),
            Block.Tiles(listOf(Tile(d.orders.toString(), tl("teslim", "çatdırılma"), tone), Tile(f2(d.gross) + "₼", tl("brüt", "ümumi")), Tile(f2(d.net) + "₼", tl("net", "xalis")))),
            Block.Tiles(listOf(Tile(f1(d.waitMinutes) + " " + tl("dk", "dəq"), tl("bekleme", "gözləmə")),
                Tile(f1(d.lateMinutes) + " " + tl("dk", "dəq"), tl("hazır sonrası bekleme", "hazırdan sonra gözləmə")), Tile(f2(d.tips) + "₼", tl("bahşiş", "çaypulu")))))
        if (d.unpriced > 0) head += Block.Line(unpricedNote(d.unpriced), Style.MUTED)
        if (d.unconfirmed > 0) head += Block.Line(tl("Sonucu doğrulanmamış: ${d.unconfirmed}", "Nəticəsi təsdiqlənməyən: ${d.unconfirmed}"), Style.MUTED)
        if (d.inferred > 0) head += Block.Line(tl("Doğrulanmamış teslim: ${d.inferred} (sonraki sipariş başladı; teslim ve net toplamına dahil, platform listesiyle eşleşince doğrulanır)",
            "Təsdiqlənməmiş çatdırılma: ${d.inferred} (növbəti sifariş başladı; çatdırılma və xalis cəmə daxildir, platforma siyahısı ilə uyğunlaşanda təsdiqlənir)"), Style.MUTED)
        // A4.2: gün sonu raporu yalnız "Tüm siparişler" görünümünde (net platformlar toplamıdır; enerji platforma bölünmez).
        val report = if (platform == null) src.daySummary(d, date == src.today(), ::tl)?.card else null
        val cards = listOfNotNull(report, cashCard(date, d), officialCard(platform, date, d), missedCard(platform, date), boostCard(platform, date))
        val live = src.live().map { it.id }.toSet()
        val trips = d.trips.sortedBy { OrderHistory.offerAt(it) }
            .map { trip -> tripCard(trip, trip.id in live && src.canAct, d.trips.filter { platformOf(it.pkg) == platformOf(trip.pkg) }) }
        return DayDetail(date, platform, Card(head, tone = tone, hero = platform == null), cards, trips,
            if (d.trips.isEmpty()) tl("Bu gün için sipariş kaydı yok.", "Bu gün üçün sifariş qeydi yoxdur.") else null)
    }

    private fun platformKeys(platform: String?) = if (platform == null) listOf("wolt", "bolt") else listOfNotNull(PlatformHistory.platformKey(platform))

    /** M15/M16/M17: vardiya başı cep nakdi, günün toplanan nakdi, CASH PENDING mutabakatı (kırmızı fark). */
    fun cashCard(date: String, d: OrderHistory.Day): Card? {
        val pocket = PocketCash.forDay(src.pocketCashRows(date), date)
        val cash = d.trips.any { it.paymentMethod == CashPayment.CASH }
        val pendingRows = src.cashPendingRows(date)
        val checks = CashPending.shifts(pendingRows).mapNotNull { (start, end) ->
            val collected = CashPayment.dayTotal(d.trips.filter { it.acceptedAt >= start && (end == null || it.acceptedAt <= end) })
            CashPending.reconcile(pendingRows, start, collected)
        }
        if (pocket.isEmpty() && !cash && checks.isEmpty()) return null
        val b = mutableListOf<Block>(Block.Line(tl("Nakit", "Nağd"), Style.BOLD))
        pocket.forEach { (start, amount) -> b += Block.Line(PocketCash.text(amount, ::tl) + " (" + clock(start) + ")") }
        // WEEM: "Toplanan nakit (Rawstats)" → "(WEEM)" (ORDERS.md 10.3).
        if (cash) b += Block.Line(tl("Toplanan nakit (WEEM): ", "Yığılan nağd (WEEM): ") + f2(CashPayment.dayTotal(d.trips)) + "₼")
        checks.forEach { r ->
            b += Block.Line(CashPending.line(r, ::tl) + (if (r.offloaded > 0) tl(" · yatırılan ", " · verilən ") + f2(r.offloaded) + "₼" else ""), if (r.ok) Style.BODY else Style.DANGER_BOLD)
        }
        if (cash && checks.isEmpty()) b += Block.Line(tl("Cash pending: Wolt → Finances'e girince dolar.", "Cash pending: Wolt → Finances-ə girəndə dolur."), Style.MUTED)
        return Card(b)
    }

    /** F8.5: platformların kendi değerleri ve WEEM'inki; farklı olan kırmızı, iki sayıyla; sonra ücreti farklı siparişler. */
    fun officialCard(platform: String?, date: String, d: OrderHistory.Day): Card {
        val totals = src.platformTotals(date)
        val km = src.shiftKm(date).takeIf { it > 0 }
        val platforms = platformKeys(platform)
        val checks = platforms.flatMap { p ->
            PlatformTotals.compare(p, totals, PlatformTotals.ours(OrderHistory.Day(date, d.trips.filter { PlatformHistory.platformKey(platformOf(it.pkg)) == p })), km)
        }
        val red = checks.count { !it.ok }
        val b = mutableListOf<Block>(Block.Line(tl("Resmî değerler", "Rəsmi dəyərlər") + if (red > 0) tl(" · $red fark", " · $red fərq") else "", if (red > 0) Style.DANGER_BOLD else Style.BOLD))
        if (checks.isEmpty()) {
            b += Block.Line(tl("Wolt Stats/Finances'e (Bolt: All deliveries) girince dolar.", "Wolt Stats/Finances-ə (Bolt: All deliveries) girəndə dolur."), Style.MUTED)
            return Card(b)
        }
        checks.forEach { b += Block.Line(PlatformTotals.line(it, ::tl), if (it.ok) Style.BODY else Style.DANGER_BOLD) }
        if (checks.any { it.field == "distance" }) b += Block.Line(tl("Wolt mesafesi yalnız çevrimiçi süreyi sayar; %10'dan büyük fark kırmızı.", "Wolt məsafəsi yalnız onlayn vaxtı sayır; 10%-dən böyük fərq qırmızıdır."), Style.MUTED)
        val diffs = platforms.flatMap { PlatformTotals.feeDiffs(src.platformHistoryRows(date), it) }
        if (red > 0 && diffs.isNotEmpty()) {
            b += Block.Line(tl("Ücreti farklı siparişler:", "Haqqı fərqli sifarişlər:"), Style.BOLD)
            diffs.forEach { b += Block.Line(PlatformTotals.diffLine(it), Style.MUTED) }
        }
        return Card(b)
    }

    /** K36: platformun kendi kazanç listesinde olup WEEM siparişiyle eşleşmeyen satırlar. */
    fun missedCard(platform: String?, date: String): Card? {
        val missed = PlatformHistory.missed(src.platformHistoryRows(date), platform?.let { PlatformHistory.platformKey(it) })
        if (missed.isEmpty()) return null
        // WEEM: "Rawstats'ın kaçırdığı siparişler" → "WEEM'in kaçırdığı siparişler" (ORDERS.md 10.3).
        val b = mutableListOf<Block>(Block.Line(tl("WEEM'in kaçırdığı siparişler (platform listesi): ${missed.size}", "WEEM-in buraxdığı sifarişlər (platforma siyahısı): ${missed.size}"), Style.BOLD))
        for (r in missed) b += Block.Line((if (platform == null) (if (r.optString("platform") == "wolt") "Wolt · " else "Bolt Food · ") else "") + PlatformHistory.missedLine(r), Style.MUTED)
        return Card(b)
    }

    /** K27: günün boost değişimi ve en yükseği (yalnız o gün boost kaydı varsa). */
    fun boostCard(platform: String?, date: String): Card? {
        val rows = src.boostRows(date).takeIf { it.isNotEmpty() } ?: return null
        val lines = platformKeys(platform).mapNotNull { p ->
            val s = BoostLog.stats(rows, p)
            if (s.changes == 0 && s.slotsObserved == 0 && s.max == null) return@mapNotNull null
            val top = s.max?.let { if (s.maxUnit == BoostLog.UNIT_X) f2(it).trimEnd('0').trimEnd('.') + "x" else "+%" + it.toInt() } ?: "—"
            (if (p == "wolt") "Wolt" else "Bolt Food") + " · " + tl("değişim", "dəyişmə") + " ${s.changes} · " + tl("en yüksek", "ən yüksək") + " $top" +
                (if (s.slotsObserved + s.slotsMissing > 0) " · " + tl("30 dk dilim: ${s.slotsBoosted}/${s.slotsObserved} boostlu, ${s.slotsMissing} gözlem yok",
                    "30 dəq hissə: ${s.slotsBoosted}/${s.slotsObserved} boostlu, ${s.slotsMissing} müşahidə yoxdur") else "")
        }
        if (lines.isEmpty()) return null
        return Card(listOf<Block>(Block.Line(tl("Boost (gün içi)", "Boost (gün ərzində)"), Style.BOLD)) + lines.map { Block.Line(it, Style.MUTED) })
    }

    // ---------- Sipariş kartı ----------

    /** K29: teslim süresi (alışta ve teslimde kalan; "-3 dk" = geç) ve kaynağı. */
    private fun deadlineLine(trip: TripRecord): String? {
        val m = DeliveryDeadline.margins(trip) ?: return null
        fun left(v: Double?, done: Boolean) = when {
            v == null -> "—"
            v < -0.5 -> tl("${dk(-v)} geç", "${dk(-v)} gecikmə")
            done -> tl("${dk(v)} kalmıştı", "${dk(v)} qalmışdı")
            else -> tl("${dk(v)} vardı", "${dk(v)} var idi")
        }
        val src = if (m.deadline.source == DeliveryDeadline.PLATFORM) tl("platform süresi", "platforma müddəti") else tl("teklifin üst süresi", "təklifin yuxarı müddəti")
        return tl("Teslim süresi", "Çatdırılma müddəti") + " ($src): " + tl("alışta", "götürəndə") + " " + left(m.atPickupMin, false) + " · " + tl("teslimde", "çatdıranda") + " " + left(m.atDeliveryMin, true)
    }

    /** K28: teklif anının havası ya da neden olmadığı. WEEM: kaynak `WeatherPort` (ORDERS.md 12). */
    private fun weatherLine(trip: TripRecord): String? {
        val o = trip.offer ?: return null
        val w = o.weather
        val body = if (w == null) tl("yok (son 90 dk'da hava bilgisi gelmedi)", "yoxdur (son 90 dəq-də hava məlumatı gəlmədi)") else listOfNotNull(
            w.tempC?.let { "${it.toInt()}°" }, w.kind(::tl),
            w.rainPct?.let { tl("yağış %${it.toInt()}", "yağıntı %${it.toInt()}") }, w.rainMm?.takeIf { it > 0 }?.let { "${f1(it)} mm" },
            w.windKmh?.let { tl("rüzgâr ${it.toInt()} km/s", "külək ${it.toInt()} km/saat") }, w.gustKmh?.let { tl("hamle ${it.toInt()}", "ani ${it.toInt()}") },
        ).joinToString(" · ").ifEmpty { "—" }
        return tl("Hava (teklif anı)", "Hava (təklif anı)") + ": " + body
    }

    /** K31 ödeme; K32 teslim edilmiş nakit siparişte alınan nakit cevabı ve girişi (bildirimin yedeği). */
    private fun cashSection(trip: TripRecord, answer: org.json.JSONObject?, actions: Boolean): Pair<List<Block>, TripAction?> {
        val pay = when (trip.paymentMethod) {
            // M16: amount, change and the moment the cash checkbox was seen ticked.
            CashPayment.CASH -> tl("Ödeme: ", "Ödəniş: ") + CashPayment.cardLine(trip, ::clock, ::tl)
            CashPayment.CARD_INFERRED -> tl("Ödeme: kart (tahmin; teslim adımlarında nakit adımı yoktu)", "Ödəniş: kart (təxmin; çatdırılma addımlarında nağd addımı yox idi)")
            else -> tl("Ödeme: görülmedi", "Ödəniş: görünmədi")
        }
        val b = mutableListOf<Block>(Block.Line(pay, Style.MUTED))
        if (trip.paymentMethod != CashPayment.CASH) return b to null
        answer?.let { a ->
            val got = CashTips.num(a, "received_azn")?.let { f2(it) } ?: "—"
            val tip = CashTips.num(a, "tip_azn")
            val negative = tip != null && tip < 0
            b += Block.Line(tl("Alınan nakit", "Alınan nağd pul") + ": $got₼ · " + when {
                tip == null -> tl("bahşiş hesaplanamadı (nakit tutarı okunamadı)", "çaypulu hesablanmadı (nağd məbləğ oxunmadı)")
                tip < 0 -> tl("fark ${f2(tip)}₼ — eksi: sipariş tutarından az alındı, kontrol et", "fərq ${f2(tip)}₼ — mənfi: sifariş məbləğindən az alınıb, yoxla")
                else -> tl("nakit bahşiş ${f2(tip)}₼ (senin girişin)", "nağd çaypulu ${f2(tip)}₼ (sənin daxil etdiyin)")
            }, if (negative) Style.BOLD else Style.MUTED)
        }
        if (!actions || !CashTips.shouldAsk(trip)) return b to null
        val title = tl("Ne kadar nakit aldın? (₼)", "Nə qədər nağd pul aldın? (₼)") + (trip.cashAmount?.let { " · " + tl("sipariş", "sifariş") + " ${f2(it)}₼" } ?: "")
        val action = TripAction.CashAnswer(if (answer == null) tl("Ne kadar nakit aldın?", "Nə qədər nağd pul aldın?") else tl("Alınan nakdi düzelt", "Alınan nağd pulu düzəlt"),
            title, answer?.let { CashTips.num(it, "received_azn")?.let(::f2) }, tl("Sayı okunamadı, örn. 25 veya 25.50", "Rəqəm oxunmadı, məs. 25 və ya 25.50"))
        return b to action
    }

    /**
     * Bir siparişin tam kartı (RS `renderTrip`): E2 restoran beklemesi ve E3 boost/sıcaklık zaman çizelgesinden önce, saatler saniyesiz (K22).
     * [actions] true: düzeltme düğmeleri (bahşiş, nakit cevabı, müşteri kartı, "Teslim ettim"). Teklif puanı satırı :economics'in ([OrdersSource.offerScoreLine], A4.1).
     */
    fun tripCard(trip: TripRecord, actions: Boolean = false, sameDay: List<TripRecord> = emptyList()): TripCard {
        val name = platformOf(trip.pkg)
        val tone = toneOf(name)
        val b = mutableListOf<Block>()
        b += Block.Heading("$name · ${OrderHistory.status(trip, ::tl)}", tone)
        b += Block.Line(trip.pickups.joinToString("\n") { listOfNotNull(it.restaurant, it.address).joinToString("\n") }, Style.TITLE)
        b += Block.Line("${trip.expectedOrders} ${tl("sipariş", "sifariş")}", Style.MUTED)
        // E2: restaurant wait (D9 arrival → Collect) at the top of the card, or "—" and why.
        b += Block.Line(OrderHistory.restaurantWaitLine(trip, ::tl), Style.ACCENT_BOLD)
        // E3: boost at offer time and the temperature when it was accepted, at the top too.
        b += Block.Line(OrderHistory.boostTempLine(trip, ::tl), Style.ACCENT_BOLD)
        val g = trip.earnings
        if (g != null) b += Block.Tiles(listOf(Tile(f2(trip.net ?: g) + " ₼", tl("net", "xalis")), Tile(f2(g) + " ₼", tl("brüt", "ümumi"))))
        else b += Block.Line(tl("Fiyat okunamadı", "Qiymət oxunmadı"), Style.MUTED)
        // D15: the Wolt earnings list's extra waiting fee (in the gross and net above) and in-app tip (shown only).
        if (trip.extraWaitAzn > 0) {
            val fee = trip.gross?.let { f2(it) } ?: "—"; val extra = f2(trip.extraWaitAzn)
            b += Block.Line(tl("Teklif $fee₼ + ek bekleme ücreti $extra₼ (Wolt listesi)", "Təklif $fee₼ + əlavə gözləmə haqqı $extra₼ (Wolt siyahısı)"), Style.MUTED)
        }
        trip.platformTipAzn?.let { b += Block.Line(tl("Uygulama bahşişi (platform listesi): ${f2(it)}₼ · toplama eklenmedi", "Tətbiq çaypulu (platforma siyahısı): ${f2(it)}₼ · cəmə əlavə edilmədi"), Style.MUTED) }
        val day = src.day(trip.acceptedAt)
        PlatformHistory.byTrip(src.platformHistoryRows(day))[trip.id]?.let { b += Block.Line(PlatformHistory.cardLine(it, ::tl), Style.MUTED) }
        b += Block.Divider
        b += Block.Sub(tl("Zaman çizelgesi", "Zaman xətti"))
        b += Block.Line("${tl("Teklif", "Təklif")}: ${clock(trip.offer?.firstAt)} · ${tl("Kabul", "Qəbul")}: ${clock(trip.acceptedAt)}")
        // D9: a Wolt arrival comes from the courier's GPS stay at the restaurant.
        trip.pickups.firstOrNull { it.arrivedAt != null }?.let { p ->
            b += Block.Line("${tl("Restorana varış", "Restorana çatma")}: ${clock(p.arrivedAt)}" + (if (p.arrivalSource?.startsWith("gps") == true) " (GPS)" else ""))
        }
        b += Block.Line("${tl("Restoranda alındı", "Restoranda götürüldü")}: ${clock(OrderHistory.pickedAt(trip))} · ${tl("Teslim", "Çatdırılma")}: ${clock(trip.completedAt)}")
        // K34/D3: closed when the next order started; the handover itself was not seen, so no delivery time. Counted as delivered.
        if (trip.deliveredInferred) b += Block.Line(if (trip.verifiedBy != null)
            tl("Teslim sinyali görülmedi, sonraki sipariş başladı (${clock(trip.deliveredInferredAt)}); platform listesi teslimi doğruladı.", "Çatdırılma siqnalı görünmədi, növbəti sifariş başladı (${clock(trip.deliveredInferredAt)}); platforma siyahısı çatdırılmanı təsdiqlədi.")
        else tl("Doğrulanmamış teslim: teslim sinyali görülmedi; sonraki sipariş başladı (${clock(trip.deliveredInferredAt)}). Teslim ve net toplamına dahil.", "Təsdiqlənməmiş çatdırılma: çatdırılma siqnalı görünmədi; növbəti sifariş başladı (${clock(trip.deliveredInferredAt)}). Çatdırılma və xalis cəmə daxildir."), Style.MUTED)
        // D3(d): Wolt Finances "Cash collection" clock = handover time of the cash order.
        trip.cashCollectedAt?.let { b += Block.Line(tl("Wolt nakit tahsilatı: ${clock(it)}", "Wolt nağd yığımı: ${clock(it)}") + (trip.cashCollectedAzn?.let { a -> " · ${f2(a)}₼" } ?: ""), Style.MUTED) }
        b += Block.Sub(tl("Süre ve bekleme", "Müddət və gözləmə"))
        val waited = OrderHistory.waitBeforeOffer(trip, sameDay)
        b += Block.Line("${tl("Teklif öncesi sipariş bekleme", "Təklifdən əvvəl sifariş gözləmə")}: ${waited?.let { minutesText(it) } ?: "—"}")
        b += Block.Line("${tl("Toplam süre (teklif → teslim)", "Ümumi müddət (təklif → çatdırılma)")}: ${OrderHistory.totalMinutes(trip)?.let { minutesText(it) } ?: tl("devam ediyor", "davam edir")}")
        val eta = trip.offer?.data?.let { o -> o.minLo?.let { if (o.minHi != null && o.minHi != it) "$it–${o.minHi}" else it.toString() } }
        b += Block.Line("${tl("Teklif süresi", "Təklif müddəti")}: ${eta?.let { "$it " + tl("dk", "dəq") } ?: "—"} · ${tl("Gerçek", "Faktiki")}: ${trip.actualMinutes?.let { minutesText(it) } ?: tl("devam ediyor", "davam edir")}")
        trip.actualMinutes?.let { actual -> trip.offer?.data?.minHi?.let { hi -> b += Block.Line("${tl("Üst süreye fark", "Yuxarı müddətdən fərq")}: ${minutesText(actual - hi)}", Style.MUTED) } }
        // E2: the restaurant wait itself is at the top of the card ("—" when unmeasured, never "0 dk").
        b += Block.Line("${tl("Restoranda hazır zamanını aşan bekleme", "Restoranda hazır olma vaxtını aşan gözləmə")}: ${minutesText(trip.lateMinutes)}", Style.MUTED)
        b += Block.Sub(tl("Ödeme ve scooter", "Ödəniş və skuter"))
        b += Block.Line("${tl("Bahşiş", "Çaypulu")}: ${f2(trip.tip)}₼${if (trip.tipSource == CashTips.SOURCE) " (" + tl("nakit girişi", "nağd girişi") + ")" else ""} · ${tl("Komisyon", "Komissiya")}: %${f1(trip.commissionPct)} · ${tl("Telefon pili", "Telefon batareyası")}: ${trip.offer?.phoneBattery?.let { "$it%" } ?: "—"}", Style.MUTED)
        OrderHistory.energyLine(trip, ::tl)?.let { b += Block.Line(it, Style.MUTED) }
        val (cashBlocks, cashAction) = cashSection(trip, src.cashTipRows(day)[trip.id], actions)
        b += cashBlocks
        b += Block.Sub(tl("Teklif ayrıntıları", "Təklif təfərrüatları"))
        // E4: the app's score of the offer and mine (rated on Siparişler → Teklif puanı).
        trip.offer?.let { src.offerScoreLine(trip, ::tl) }?.let { b += Block.Line(it, Style.BOLD) }
        // E3: the boost moved to the top of the card (OrderHistory.boostTempLine); the full weather stays here.
        weatherLine(trip)?.let { b += Block.Line(it, Style.MUTED) }
        // D6: how fast the offer and its fee were read.
        trip.offer?.let { o -> OrderHistory.readLine(o, ::tl)?.let { b += Block.Line(it, Style.MUTED) } }
        deadlineLine(trip)?.let { b += Block.Line(it, Style.MUTED) }
        // OCR variants of one customer (older records) are shown once (K21).
        for (customer in CustomerMatch.distinct(trip.customers.values)) b += Block.Person(customer.details.name ?: customer.id, customer.details.display(az))
        val buttons = mutableListOf<TripAction>()
        if (actions) {
            buttons += TripAction.EditTip(tl("Bahşişi düzenle", "Çaypulunu düzəlt"), tl("Bahşiş (₼) · sipariş grubu", "Çaypulu (₼) · sifariş qrupu"), f2(trip.tip))
            cashAction?.let { buttons += it }
            if (trip.active && src.shiftOn()) buttons += TripAction.CustomerCard(tl("Müşteri kartını aç", "Müştəri kartını aç"))
            if (trip.completedAt == null) buttons += TripAction.ConfirmDelivered(tl("Düzeltme: teslim edildi olarak kaydet", "Düzəliş: çatdırıldı kimi qeyd et"),
                tl("Teslimat kaydını düzelt", "Çatdırılma qeydini düzəlt"),
                tl("Teslimatı gerçekten bitirdiysen kaydet. Düzeltme zamanı ve kaynağı kayıtta belirtilir.", "Çatdırılmanı həqiqətən bitirmisənsə qeyd et. Düzəlişin vaxtı və mənbəyi qeyddə göstərilir."),
                tl("Teslim ettim", "Çatdırdım"))
        }
        return TripCard(trip.id, Card(b, tone, outlined = trip.active), buttons)
    }

    /** Bir tek siparişin kartı (liste satırına dokununca açılan ayrıntı); aynı gün ve platformdaki siparişler "teklif öncesi bekleme" içindir. */
    fun tripDetail(tripId: String): TripCard? {
        val trip = days(null).flatMap { it.trips }.firstOrNull { it.id == tripId } ?: return null
        val sameDay = days(platformOf(trip.pkg)).firstOrNull { it.date == src.day(trip.acceptedAt) }?.trips.orEmpty()
        return tripCard(trip, actions = false, sameDay = sameDay)
    }
}

/** Platform adı (Rawstats `OrderTracker.platform`): "Wolt", "Bolt Food" ya da paketin son parçası. */
internal object Platform {
    fun name(pkg: String) = if (pkg.contains("wolt", true)) "Wolt" else if (pkg.contains("bolt", true) || pkg.contains("mtakso", true)) "Bolt Food" else pkg.substringAfterLast('.')
}
