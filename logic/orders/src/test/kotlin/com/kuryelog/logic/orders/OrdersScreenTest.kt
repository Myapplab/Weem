package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.io.File
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A3.8 — Siparişler sekmesinin içeriği (ORDERS.md 10.3). Rawstats'ın dört kaynak taraması (`PlatformTotalsF85Test.dayPageHasTheCard`,
 * `BoostTempE3Test.cardTopShowsIt`, `RestaurantWaitE2Test.cardAndListShowIt`, `AverageListE1Test.eachAverageOpensItsList`) MainActivity
 * dizgilerine bakıyordu; WEEM'de ekran modeli ([OrdersScreen]) üretildiği için aynı denetimler modelin çıktısına yazıldı (sıra ve içerik).
 * Sentetik kayıtlar (uydurma restoran adları).
 */
class OrdersScreenTest {
    @get:Rule val tmp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.courier"
    private val min = 60_000L
    private val hour = 60 * min
    /** 2026-10-16 09:00 Bakü (05:00 UTC); gün 2026-10-16, bir önceki gün 2026-10-15. */
    private val t0 = java.time.ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, baku).toInstant().toEpochMilli()
    private val yesterday = t0 - 24 * hour

    private class FakeClock(var now: Long, val zoneId: ZoneId) : Clock {
        override fun wallMs() = now
        override fun monoMs() = now
        override fun zone() = zoneId
    }

    private class Rig(val log: LogStore, val db: CourierRecords.Memory, val prefs: KuryelogPrefs.Memory, val clock: FakeClock) {
        val source get() = LogOrdersSource(log, db, prefs, clock)
        fun add(t: TripRecord) = db.saveTrip(RecordJson.trip(t))
        fun write(day: String, name: String, vararg rows: JSONObject) {
            val dir = log.dayDir(day).apply { mkdirs() }
            File(dir, name).appendText(rows.joinToString("") { it.toString() + "\n" })
        }
    }

    private var rigs = 0
    private fun rig(now: Long = t0 + 5 * hour): Rig {
        val clock = FakeClock(now, baku)
        return Rig(LogStore(File(tmp.root, "logs${rigs++}"), now = { clock.now }, zone = { baku }), CourierRecords.Memory(), KuryelogPrefs.Memory(), clock)
    }

    private fun obs(pkg: String, at: Long, gross: Double?, restaurant: String = "Test Döner", minLo: Int? = 10, minHi: Int? = 14) =
        CourierObservation(pkg, at, ScreenStage.OFFER, true, "accessibility", emptyList(), emptyList(), gross = gross, km = 2.0, minLo = minLo, minHi = minHi, restaurant = restaurant)

    /** Teslim edilmiş sipariş: teklif [at] − 1 dk, kabul [at], alış +9 dk (restoranda 4 dk), teslim +20 dk. */
    private fun trip(id: String, pkg: String = wolt, at: Long = t0, gross: Double? = 3.0, restaurant: String = "Test Döner", done: Boolean = true) =
        TripRecord(id, pkg, OfferState("o-$id", pkg, at - min, obs(pkg, at - min, gross, restaurant)), at, if (done) TripPhase.FINISHED else TripPhase.TO_RESTAURANT).also { t ->
            t.pickups.add(PickupRecord(restaurant, "Test Cad. 1", arrivedAt = at + 5 * min, pickedAt = at + 9 * min, arrivalSource = "gps_dwell"))
            if (done) t.completedAt = at + 20 * min
        }

    private fun styleOf(card: Card, text: String) = card.blocks.filterIsInstance<Block.Line>().first { it.text.contains(text) }.style
    private fun lines(card: Card) = card.blocks.filterIsInstance<Block.Line>().map { it.text }
    private fun idx(card: Card, pred: (Block) -> Boolean) = card.blocks.indexOfFirst(pred)

    // ---- F85: gün sayfasında "Resmî değerler" kartı ----

    @Test fun dayPageHasTheCard() {
        val r = rig()
        r.add(trip("a", gross = 3.0)); r.add(trip("b", at = t0 + hour, gross = 4.0))
        // Wolt Finances günü 18.24 ₼ diyor, WEEM'in brütü 7.00 ₼: fark kırmızı, iki sayıyla ("WEEM:").
        r.write("2026-10-16", PlatformTotals.FILE, PlatformTotals.record(PlatformTotals.Total("wolt", "day:2026-10-16", PlatformTotals.WOLT_DAILY, earnings = 18.24), t0))
        r.write("2026-10-16", PlatformHistory.FILE, JSONObject().put("schemaVersion", 1).put("platform", "wolt").put("clock", "12:40").put("restaurant", "Test Restoran")
            .put("amount", 18.24).put("amount_text", "AZN 18.24").put("match", JSONObject().put("trip_id", "a").put("trip_gross", 3.0)).put("missed_by_kuryeplus", false))
        val screen = OrdersScreen(r.source, Lang.TR)
        val d = screen.dayDetail("2026-10-16")
        val card = d.cards.first { c -> lines(c).any { it.startsWith("Resmî değerler") } }
        assertEquals("Resmî değerler · 1 fark", lines(card)[0])
        assertEquals(Style.DANGER_BOLD, card.blocks.filterIsInstance<Block.Line>()[0].style)
        assertEquals("Gün toplamı (Finances) — Wolt: 18.24 ₼ · WEEM: 7.00 ₼", lines(card)[1])
        assertEquals(Style.DANGER_BOLD, card.blocks.filterIsInstance<Block.Line>()[1].style)
        assertTrue(lines(card).any { it.contains("WEEM") && !it.contains("Rawstats") })
        assertTrue(lines(card).none { it.contains("Rawstats") })
    }

    @Test fun matchingValueIsPlainAndEmptyDayShowsTheHint() {
        val r = rig()
        r.add(trip("a", gross = 3.0))
        r.write("2026-10-16", PlatformTotals.FILE, PlatformTotals.record(PlatformTotals.Total("wolt", "day:2026-10-16", PlatformTotals.WOLT_DAILY, earnings = 3.0), t0))
        val ok = OrdersScreen(r.source, Lang.TR).dayDetail("2026-10-16").cards.first { c -> lines(c).any { it.startsWith("Resmî değerler") } }
        assertEquals("Resmî değerler", lines(ok)[0])
        assertEquals(Style.BOLD, ok.blocks.filterIsInstance<Block.Line>()[0].style)
        assertEquals(Style.BODY, ok.blocks.filterIsInstance<Block.Line>()[1].style)

        val empty = rig().also { it.add(trip("a")) }
        val card = OrdersScreen(empty.source, Lang.AZ).dayDetail("2026-10-16").cards.first { c -> lines(c).any { it.startsWith("Rəsmi dəyərlər") } }
        assertEquals("Wolt Stats/Finances-ə (Bolt: All deliveries) girəndə dolur.", lines(card)[1])
    }

    // ---- E3: boost ve sıcaklık kartın üstünde, listenin ikinci satırında ----

    @Test fun cardTopShowsIt() {
        val r = rig()
        r.add(trip("a"))
        val screen = OrdersScreen(r.source, Lang.TR)
        val trip = screen.dayDetail("2026-10-16").trips.single()
        val boostLine = idx(trip.card) { it is Block.Line && it.text.startsWith("Boost ") }
        val timeline = idx(trip.card) { it is Block.Sub && it.text == "Zaman çizelgesi" }
        assertTrue(boostLine in 0 until timeline)
        assertEquals("Boost — (görülmedi) · Sıcaklık — (hava bilgisi gelmedi)", (trip.card.blocks[boostLine] as Block.Line).text)
        // Listenin ikinci satırı OrderHistory.listLine (E2): bekleme, boost, sıcaklık.
        val row = screen.page("2026-10-16").rows.single()
        assertEquals(OrderHistory.listLine(r.source.stored().single(), { a, _ -> a }), row.line)
        assertTrue(row.line.contains("Boost"))
    }

    // ---- E2: restoran beklemesi kartın üstünde (zaman çizelgesinden önce) ve listede ----

    @Test fun cardAndListShowIt() {
        val r = rig()
        r.add(trip("a"))
        val screen = OrdersScreen(r.source, Lang.TR)
        val card = screen.dayDetail("2026-10-16").trips.single().card
        val wait = idx(card) { it is Block.Line && it.text.startsWith("Restoranda bekleme") }
        assertTrue(wait in 0 until idx(card) { it is Block.Sub && it.text == "Zaman çizelgesi" })
        assertEquals("Restoranda bekleme: 4 dk (GPS)", (card.blocks[wait] as Block.Line).text)
        assertEquals(Style.ACCENT_BOLD, (card.blocks[wait] as Block.Line).style)
        // Ölçülmeyen bekleme için eski "Bekleme: 0 dk" satırı yok.
        assertTrue(lines(card).none { it.startsWith("Bekleme:") })
        assertTrue(screen.page("2026-10-16").rows.single().line.startsWith("Restoranda 4 dk"))
        // Ölçülmediyse "—" ve neden.
        val r2 = rig()
        r2.add(trip("n").also { it.pickups.clear(); it.pickups.add(PickupRecord("Test Kafe", null, pickedAt = it.acceptedAt + 9 * min)) })
        val c2 = OrdersScreen(r2.source, Lang.TR).dayDetail("2026-10-16").trips.single().card
        assertTrue(lines(c2).any { it == "Restoranda bekleme: — (restorana varış görülmedi)" })
    }

    // ---- E1: her ortalama kendi listesini açar ----

    @Test fun eachAverageOpensItsList() {
        val r = rig()
        r.add(trip("a", at = t0)); r.add(trip("b", pkg = bolt, at = t0 + hour, gross = 5.0, restaurant = "Test Kafe"))
        val screen = OrdersScreen(r.source, Lang.TR)
        val tiles = screen.today().hero.blocks.filterIsInstance<Block.Tiles>().flatMap { it.items }
        for (m in OrderHistory.Metric.values()) {
            assertEquals(m.name, 1, tiles.count { it.metric == m })
            val list = screen.metricList(m)
            assertEquals(2, list.rows.size)
            assertNull(list.empty)
            assertTrue(list.title.endsWith("· Bugün"))
        }
        val rest = screen.metricList(OrderHistory.Metric.RESTAURANT)
        assertEquals("Ortalama: 4.0 dk · 2/2 siparişte ölçüldü", rest.summary)
        assertEquals("08:59  Wolt · Test Döner · 4.0 dk", rest.rows[0].first)
        assertEquals("10:20  Bolt Food · Test Kafe · 4.0 dk", rest.rows[1].first)
        // Enerji ölçülmedi: değer yok, neden var, satır soluk.
        val energy = screen.metricList(OrderHistory.Metric.ENERGY)
        assertTrue(energy.rows[0].second)
        assertEquals("Ortalama: — · 0/2 siparişte ölçüldü", energy.summary)
        // Bugün sipariş yok: boş liste metni.
        assertEquals("Bugün teslim edilmiş sipariş yok.", OrdersScreen(rig().source, Lang.TR).metricList(OrderHistory.Metric.ORDER).empty)
    }

    // ---- Bugün kartı ----

    @Test fun todayCardHasTheHomeNumbers() {
        val r = rig()
        r.add(trip("a", gross = 3.0)); r.add(trip("b", pkg = bolt, at = t0 + hour, gross = 5.0, restaurant = "Test Kafe")); r.add(trip("old", at = yesterday))
        r.write("2026-10-16", "events.jsonl", JSONObject().put("type", "shift_end").put("min", 120.0).put("km", 12.5))
        r.db.saveOffer(JSONObject().put("id", "x").put("pkg", wolt).put("day", "2026-10-16"), "rejected")
        val today = OrdersScreen(r.source, Lang.TR).today()
        val tiles = today.hero.blocks.filterIsInstance<Block.Tiles>().map { it.items }
        assertEquals("8.00 ₼", (today.hero.blocks[1] as Block.Big).text)
        assertEquals(listOf("2", "4.0 ₼", "0 dk"), tiles[0].map { it.value })
        assertEquals(listOf("0", "2", "1"), tiles[1].map { it.value })
        assertEquals(listOf("8.00 ₼", "0.00 ₼"), tiles[2].map { it.value })
        assertEquals(2, today.platforms.size)
        assertEquals("Wolt", (today.platforms[0].blocks[0] as Block.Heading).text)
        assertEquals(Tone.WOLT, today.platforms[0].tone)
        assertEquals(Tone.BOLT, today.platforms[1].tone)
        assertNull(today.now)
    }

    @Test fun daySummaryShowsTheReportAndSubtractsEnergyCostFromTheHeroNet() {
        val r = rig()
        r.add(trip("a", gross = 3.0)); r.add(trip("b", pkg = bolt, at = t0 + hour, gross = 5.0, restaurant = "Test Kafe")); r.add(trip("old", at = yesterday))
        r.write("2026-10-16", "events.jsonl", JSONObject().put("type", "shift_end").put("min", 120.0).put("km", 12.5))
        // Bağlı değil: kart yok, Rawstats sayısı değişmez.
        val plain = OrdersScreen(r.source, Lang.TR)
        assertNull(plain.today().report); assertEquals("Net kazanç", (plain.today().hero.blocks[0] as Block.Line).text)
        assertTrue(plain.dayDetail("2026-10-16").cards.none { c -> lines(c).any { it.startsWith("Gün sonu") } })
        val seen = mutableListOf<Pair<String, Boolean>>()
        val src = object : OrdersSource by r.source {
            override fun daySummary(day: OrderHistory.Day, today: Boolean, tl: (String, String) -> String): DayNetSummary {
                seen += day.date to today
                return DayNetSummary(Card(listOf(Block.Line(tl("Gün sonu raporu", "Gün sonu hesabatı"), Style.TITLE)), outlined = true), 0.5)
            }
        }
        val screen = OrdersScreen(src, Lang.TR)
        val today = screen.today()
        assertEquals("Gün sonu raporu", (today.report!!.blocks[0] as Block.Line).text)
        assertEquals("Net kazanç (enerji maliyeti düşülmüş)", (today.hero.blocks[0] as Block.Line).text)
        assertEquals("7.50 ₼", (today.hero.blocks[1] as Block.Big).text)   // 8.00 − 0.50
        assertEquals("3.8 ₼", today.hero.blocks.filterIsInstance<Block.Tiles>()[0].items[1].value)   // 7.50 / 2 saat = 3.75
        assertEquals("2026-10-16" to true, seen.first())
        // Gün ayrıntısı: rapor ilk kart, yalnız "Tüm siparişler"de; dünün raporu "şimdiye dek" değil.
        val d = screen.dayDetail("2026-10-15")
        assertEquals("Gün sonu raporu", lines(d.cards[0])[0]); assertEquals("2026-10-15" to false, seen.last())
        val n = seen.size
        assertTrue(screen.dayDetail("2026-10-16", "Wolt").cards.none { c -> lines(c).any { it.startsWith("Gün sonu") } }); assertEquals(n, seen.size)
        assertEquals("Gün sonu hesabatı", lines(OrdersScreen(src, Lang.AZ).dayDetail("2026-10-15").cards[0])[0])
    }

    // ---- Gün sayfaları: sıra, başlık, platform süzgeci ----

    @Test fun pagesGoNewestFirstAndTheHeaderCarriesTheDay() {
        val r = rig()
        r.add(trip("a")); r.add(trip("old", at = yesterday, gross = 2.0))
        r.write("2026-10-16", "events.jsonl", JSONObject().put("type", "shift_end").put("min", 90.0).put("km", 12.5))
        val screen = OrdersScreen(r.source, Lang.TR)
        val dates = screen.dates()
        assertEquals(listOf("2026-10-16", "2026-10-15"), dates)
        assertEquals(0, screen.index(dates, null))
        assertEquals(1, screen.index(dates, "2026-10-15"))
        assertEquals(0, screen.index(dates, "2020-01-01"))
        assertEquals(-1, screen.index(emptyList(), null))
        val h = screen.header("2026-10-16", 0, 2)
        assertEquals("16 Ekim 2026 · Bugün · Cuma", h.title)
        assertEquals("1 sipariş · Brüt 3.00₼ · Net 3.00₼", h.totals)
        assertEquals("12.5 km · Aktif süre: 1 sa 30 dk", h.activity)
        assertEquals("1 / 2 gün", h.indicator)
        val az = OrdersScreen(r.source, Lang.AZ).header("2026-10-15", 1, 2)
        assertTrue(az.title, az.title.startsWith("15 oktyabr 2026 · "))
        assertEquals("16 Ekim 2026 · Bugün · Cuma", h.title)
        assertEquals("2 / 2 gün", az.indicator)
        assertEquals("1 sifariş · Ümumi 2.00₼ · Xalis 2.00₼", az.totals)
    }

    @Test fun listRowsFollowTheListClockAndFilterByPlatform() {
        val r = rig()
        // Wolt: kabul anı (teklif kartının son karesi); Bolt: teslim anı.
        r.add(trip("w", at = t0 + 2 * hour, restaurant = "Test Döner"))
        r.add(trip("b", pkg = bolt, at = t0, gross = 5.0, restaurant = "Test Kafe"))
        r.add(trip("open", at = t0 + 3 * hour, gross = null, done = false))
        val screen = OrdersScreen(r.source, Lang.TR)
        val all = screen.page("2026-10-16")
        assertEquals(listOf("b", "w", "open"), all.rows.map { it.tripId })
        assertTrue(all.rows.all { it.showDot })
        assertEquals("09:20  Test Kafe", all.rows[0].title)
        assertEquals("10:59  Test Döner", all.rows[1].title)
        assertEquals("Fiyat okunamadı", all.rows[2].amounts)
        assertNotNull(all.rows[2].status)
        assertNull(all.rows[0].status)
        assertEquals("Brüt 5.00₼\nNet 5.00₼", all.rows[0].amounts)
        val onlyWolt = screen.page("2026-10-16", "Wolt")
        assertEquals(listOf("w", "open"), onlyWolt.rows.map { it.tripId })
        assertTrue(onlyWolt.rows.none { it.showDot })
        assertEquals("Bu gün için sipariş kaydı yok.", screen.page("2026-10-01").empty)
    }

    @Test fun unverifiedDeliveryIsLabelledInTheList() {
        val r = rig()
        r.add(trip("a").also { it.completedAt = null; it.phase = TripPhase.END_UNCONFIRMED; it.deliveredInferredAt = it.acceptedAt + 25 * min })
        val screen = OrdersScreen(r.source, Lang.TR)
        assertTrue(screen.page("2026-10-16").rows.single().title.endsWith("· doğrulanmadı"))
        val head = screen.dayDetail("2026-10-16").header
        assertTrue(lines(head).any { it.startsWith("Doğrulanmamış teslim: 1") })
    }

    // ---- Eski günler ve arşiv ----

    @Test fun recordsPagesSplitActiveAndArchive() {
        val r = rig()
        r.add(trip("a")); r.add(trip("old", at = yesterday))
        r.write("2026-10-16", "events.jsonl", JSONObject().put("type", "shift_end").put("min", 1.0).put("km", 1.0))
        r.write("2026-10-10", "events.jsonl", JSONObject().put("type", "shift_end").put("min", 1.0).put("km", 1.0))
        r.write("2026-10-15", "events.jsonl", JSONObject().put("type", "shift_end").put("min", 1.0).put("km", 1.0))
        DailyRecords(r.log.logsRoot).archive("2026-10-15", enabled = true)
        val screen = OrdersScreen(r.source, Lang.TR)
        // Siparişler sekmesi arşivden bağımsız: yalnız sipariş günleri.
        assertEquals(listOf("2026-10-16", "2026-10-15"), screen.dates())
        // Kayıtlar: etkin gün + kayıt günü; arşivdeki gün gizli.
        assertEquals(listOf("2026-10-16", "2026-10-10"), screen.dates(records = true))
        // Arşiv: yalnız arşivlenmiş kayıt günleri.
        assertEquals(listOf("2026-10-15"), screen.dates(records = true, archive = true))
    }

    // ---- Nakit kartı (M15–M17) ----

    @Test fun cashCardShowsPocketCollectedAndTheReconcile() {
        val r = rig()
        val a = trip("a", gross = 6.0).also { it.paymentMethod = CashPayment.CASH; it.paymentSource = CashPayment.SOURCE_CASH_SCREEN; it.cashAmount = 20.40 }
        r.add(a)
        r.write("2026-10-16", PocketCash.FILE, PocketCash.row(t0 - hour, 50.0, t0 - hour + min, "notification"))
        r.write("2026-10-16", CashPending.FILE,
            CashPending.shiftRow(CashPending.SHIFT_START, t0 - hour, 100.0, t0 - hour, t0 - hour),
            CashPending.shiftRow(CashPending.SHIFT_END, t0 - hour, 130.0, t0 + 3 * hour, t0 + 3 * hour))
        val card = OrdersScreen(r.source, Lang.TR).dayDetail("2026-10-16").cards.first { c -> lines(c).contains("Nakit") }
        assertEquals("Nakit", lines(card)[0])
        assertTrue(lines(card).contains("Vardiya başında cepte: 50.00₼ (08:00)"))
        assertTrue(lines(card).any { it == "Toplanan nakit (WEEM): 20.40₼" })
        // 100 + 20.40 − 0 = 120.40 beklenir, 130 görüldü: fark kırmızı.
        val diff = card.blocks.filterIsInstance<Block.Line>().first { it.text.startsWith("Pending:") }
        assertEquals("Pending: 100.00 → 130.00₼ · beklenen 120.40₼ · fark 9.60₼", diff.text)
        assertEquals(Style.DANGER_BOLD, diff.style)
        assertTrue(lines(card).none { it.contains("Rawstats") })
        // Nakit olmayan, kaydı olmayan günde kart yok.
        val plain = rig().also { it.add(trip("a")) }
        assertTrue(OrdersScreen(plain.source, Lang.TR).dayDetail("2026-10-16").cards.none { c -> lines(c).contains("Nakit") })
    }

    // ---- Kaçırılan siparişler ve boost ----

    @Test fun missedOrdersAndBoostCards() {
        val r = rig()
        r.add(trip("a"))
        r.write("2026-10-16", PlatformHistory.FILE,
            JSONObject().put("schemaVersion", 1).put("platform", "wolt").put("clock", "14:05").put("restaurant", "Test Restoran").put("amount_text", "AZN 3.10").put("missed_by_kuryeplus", true),
            JSONObject().put("schemaVersion", 1).put("platform", "bolt").put("clock", "15:10").put("restaurant", "Test Kafe").put("amount_text", "4,00 ₼").put("missed_by_kuryeplus", true))
        val all = OrdersScreen(r.source, Lang.TR).dayDetail("2026-10-16").cards
        val missed = all.first { c -> lines(c).any { it.startsWith("WEEM'in kaçırdığı siparişler") } }
        assertEquals("WEEM'in kaçırdığı siparişler (platform listesi): 2", lines(missed)[0])
        assertEquals("Wolt · 14:05 · Test Restoran · AZN 3.10", lines(missed)[1])
        assertEquals("Bolt Food · 15:10 · Test Kafe · 4,00 ₼", lines(missed)[2])
        // Platform seçiliyken platform adı satırda yok ve yalnız o platformun satırı.
        val wolt = OrdersScreen(r.source, Lang.TR).dayDetail("2026-10-16", "Wolt").cards.first { c -> lines(c).any { it.startsWith("WEEM'in kaçırdığı") } }
        assertEquals(listOf("WEEM'in kaçırdığı siparişler (platform listesi): 1", "14:05 · Test Restoran · AZN 3.10"), lines(wolt))
        val az = OrdersScreen(r.source, Lang.AZ).dayDetail("2026-10-16").cards.first { c -> lines(c).any { it.startsWith("WEEM-in") } }
        assertTrue(lines(az)[0].startsWith("WEEM-in buraxdığı sifarişlər"))
    }

    // ---- Sipariş kartı: ödeme ve düzeltme düğmeleri ----

    @Test fun cashOrderCardShowsTheAnswerAndOffersTheInputOnlyToTheLiveFlow() {
        val r = rig()
        val a = trip("a", gross = 6.0).also { it.paymentMethod = CashPayment.CASH; it.paymentSource = CashPayment.SOURCE_CASH_SCREEN; it.cashAmount = 20.40; it.cashCheckedAt = t0 + 18 * min }
        r.add(a)
        r.write("2026-10-16", CashTips.FILE, CashTips.record(a, 25.0, t0 + 30 * min, CashTips.VIA_CARD, null, CashTips.SOURCE))
        val screen = OrdersScreen(r.source, Lang.TR)
        val card = screen.tripCard(a, actions = false)
        assertTrue(lines(card.card).any { it.startsWith("Ödeme: Nakit 20.40₼") })
        assertTrue(lines(card.card).any { it.startsWith("Alınan nakit: 25.00₼ · nakit bahşiş 4.60₼ (senin girişin)") })
        // Akış bağlı değilken düğme yok.
        assertTrue(screen.dayDetail("2026-10-16").trips.single().actions.isEmpty())
        // Akış bağlıyken (actions = true): bahşişi düzenle + nakdi düzelt; teslim edilmiş siparişte "Teslim ettim" yok.
        val live = screen.tripCard(a, actions = true)
        assertEquals(listOf("Bahşişi düzenle", "Alınan nakdi düzelt"), live.actions.map { it.label })
        assertEquals("25.00", (live.actions[1] as TripAction.CashAnswer).initial)
        val open = trip("o", gross = 3.0, done = false)
        assertEquals(listOf("Bahşişi düzenle", "Düzeltme: teslim edildi olarak kaydet"), screen.tripCard(open, actions = true).actions.map { it.label })
    }

    /** E4 (A4.1): "Teklif ayrıntıları"nın ilk satırı `:economics`'in puan satırı (RS MainActivity.kt:1092-1093); bağlı değilken yok. */
    @Test fun offerScoreLineOpensTheOfferDetails() {
        val r = rig(); val a = trip("a"); r.add(a)
        val plain = OrdersScreen(r.source, Lang.TR).tripCard(a).card.blocks
        assertFalse(plain.any { it is Block.Line && it.text.startsWith("Teklif puanı") })
        val scored = object : OrdersSource by r.source {
            override fun offerScoreLine(trip: TripRecord, tl: (String, String) -> String) = tl("Teklif puanı", "Təklif balı") + ": " + trip.offer!!.id
        }
        val blocks = OrdersScreen(scored, Lang.AZ).tripCard(a).card.blocks
        val sub = blocks.indexOf(Block.Sub("Təklif təfərrüatları"))
        assertEquals(Block.Line("Təklif balı: o-a", Style.BOLD), blocks[sub + 1])
        // Teklifsiz sipariş: satır yok.
        assertFalse(OrdersScreen(scored, Lang.TR).tripCard(a.copy(offer = null)).card.blocks.any { it is Block.Line && it.text.startsWith("Teklif puanı") })
    }

    @Test fun tripCardKeepsTheTimelineAndTheListRowsOfTheSameCustomerOnce() {
        val r = rig()
        val a = trip("a")
        a.customers["c1"] = CustomerRecord("c1", DeliveryDetails(name = "Test Müşteri", address = "Test Sk. 3"), deliveredAt = a.completedAt)
        a.customers["c2"] = CustomerRecord("c2", DeliveryDetails(name = "Test Müşteri", address = "Test Sk. 3"), deliveredAt = a.completedAt)
        a.currentCustomer = "c1"
        r.add(a)
        val card = OrdersScreen(r.source, Lang.TR).dayDetail("2026-10-16").trips.single().card
        assertEquals(1, card.blocks.count { it is Block.Person })
        assertTrue(lines(card).contains("Teklif: 08:59 · Kabul: 09:00"))
        assertTrue(lines(card).contains("Restoranda alındı: 09:09 · Teslim: 09:20"))
        assertTrue(lines(card).contains("Restorana varış: 09:05 (GPS)"))
        assertEquals(listOf("Zaman çizelgesi", "Süre ve bekleme", "Ödeme ve scooter", "Teklif ayrıntıları"), card.blocks.filterIsInstance<Block.Sub>().map { it.text })
        // WEEM'de teklif puanı satırı yok (E4 :economics, A4.1).
        assertTrue(lines(card).none { it.startsWith("Teklif puanı") })
        assertTrue(lines(card).any { it == "Hava (teklif anı): yok (son 90 dk'da hava bilgisi gelmedi)" })
    }

    // ---- Kaynak: saklı kayıtlar ve canlı akış yokken ----

    @Test fun storedSourceReadsStatsFromTheDatabaseAndTheDayFiles() {
        val r = rig(now = t0 + 5 * hour)
        r.add(trip("a", gross = 3.0)); r.add(trip("b", at = t0 + hour, gross = 4.0))
        r.write("2026-10-16", "events.jsonl", JSONObject().put("type", "shift_end").put("min", 120.0).put("km", 10.0))
        r.db.saveOffer(JSONObject().put("id", "x").put("pkg", wolt).put("day", "2026-10-16"), "rejected")
        val s = r.source
        assertFalse(s.canAct)
        assertEquals("", s.nowLines())
        val st = s.stats()
        assertEquals(2, st.delivered); assertEquals(7.0, st.net, 0.001); assertEquals(3.5, st.perHour, 0.001); assertEquals(2, st.accepted); assertEquals(1, st.rejected)
        assertEquals(10.0, s.shiftKm("2026-10-16"), 0.001)
        assertEquals(120.0, s.shiftMinutes("2026-10-16"), 0.001)
        // Açık vardiya: bugünün km'sine ve süresine eklenir, başka güne eklenmez.
        r.prefs.put(mapOf(KuryelogPrefs.SHIFT to true, KuryelogPrefs.MANUAL_SESSION to true, KuryelogPrefs.SHIFT_START to t0 + 4 * hour, KuryelogPrefs.DIST to 2500f))
        assertEquals(12.5, r.source.shiftKm("2026-10-16"), 0.001)
        assertEquals(180.0, r.source.shiftMinutes("2026-10-16"), 0.001)
        assertEquals(0.0, r.source.shiftKm("2026-10-15"), 0.001)
        assertFalse(s.setTip("a", 1.0)); assertNull(s.recordCashTip("a", 5.0)); assertFalse(s.confirmDelivered("a")); assertFalse(s.showCustomerCard("a"))
    }

    @Test fun damagedRowsAreSkippedAndAnEmptyDayIsNotAnError() {
        val r = rig()
        r.add(trip("a"))
        r.db.tripRows["zz"] = JSONObject().put("id", "zz").put("pkg", wolt).put("day", "2026-10-16").put("accepted_at", t0).put("phase", "NOPE")
        File(r.log.dayDir("2026-10-16").apply { mkdirs() }, PlatformHistory.FILE).writeText("not json\n")
        val screen = OrdersScreen(r.source, Lang.TR)
        assertEquals(listOf("a"), screen.page("2026-10-16").rows.map { it.tripId })
        assertEquals(emptyList<String>(), OrdersScreen(rig().source, Lang.TR).dates())
        assertNull(OrdersScreen(rig().source, Lang.TR).dayDetail("2026-10-16").trips.firstOrNull())
    }

    @Test fun everyLabelHasBothLanguages() {
        val tr = OrdersLabels { a, _ -> a }; val az = OrdersLabels { _, b -> b }
        for (p in OrdersLabels::class.java.declaredFields.filter { it.type == String::class.java }) {
            p.isAccessible = true
            val a = p.get(tr) as String; val b = p.get(az) as String
            assertTrue(p.name, a.isNotBlank() && b.isNotBlank())
        }
        assertEquals("Önceki gün", tr.older); assertEquals("Əvvəlki gün", az.older)
    }
}
