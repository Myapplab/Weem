package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.*
import com.kuryelog.logic.core.*
import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import com.kuryelog.logic.capture.SahaFrames.WOLT
import com.kuryelog.logic.capture.SahaFrames.frames
import com.kuryelog.logic.capture.SahaFrames.expected
import com.kuryelog.logic.capture.SahaFrames.parse
import com.kuryelog.logic.capture.SahaFrames.isIdleFrame
import com.kuryelog.logic.capture.SahaFrames.isSplitFrame
import com.kuryelog.logic.capture.SahaFrames.cardEarning
import com.kuryelog.logic.capture.SahaFrames.changeAmount

/**
 * Saha tekrar testi (13 Ekim 2026 planı, F0.6). 11–12 Ekim'in 6.481 gerçek ekran karesi (Wolt 2.202.6, Bolt) parser'dan
 * geçirilir. Kareler kişisel veriden temizlendi (docs/SAHA_ANALIZI_2026-10-13.md §0); doğru cevaplar
 * `saha/saha_beklenen_2026-10-11_12.json`. Eski Wolt testleri eski metinlerle ("₼3.94") yazıldığı için saha bozulurken
 * yeşil kaldı; bu test sahanın kendisidir.
 *
 * Hedef testler ilgili madde düzelene kadar @Ignore'dur; madde bitince @Ignore kaldırılır (plan: docs/DUZELTME_PLANI_2026-10-13.md).
 */
class SahaTekrarTest {
    /** Ölçüm raporu: her koşuda yazdırılır, hiçbir zaman düşmez. Faz ilerledikçe sayılar iyileşmeli. */
    @Test fun olcumRaporu() {
        val idle = frames.filter(::isIdleFrame)
        val idleOk = idle.count { parse(it).stage in setOf(ScreenStage.IDLE, ScreenStage.OFFLINE) }
        val split = frames.filter(::isSplitFrame)
        val splitOk = split.count { parse(it).stage == ScreenStage.IDLE }
        val cards = frames.filter { it.wolt && cardEarning(it) != null }
        val feeOk = cards.count { parse(it).gross == cardEarning(it) }
        val offerStage = frames.filter { it.wolt && it.joined.contains("Expected earn") }.count { parse(it).stage == ScreenStage.OFFER }
        println("SAHA RAPORU: bekleme $idleOk/${idle.size} · bölünmüş $splitOk/${split.size} · kart kazancı $feeOk/${cards.size}" +
            " · teklif aşaması $offerStage/${frames.count { it.wolt && it.joined.contains("Expected earn") }}")
        assertTrue(frames.isNotEmpty())
    }

    private val baku = java.util.TimeZone.getTimeZone("Asia/Baku")
    /**
     * Kareleri sırayla DeliveryCore'dan geçirir (gün başına yeni çekirdek), OrderTracker.onScreen gibi: kazanç listesi karesi
     * çekirdeğe girmez (K36; [list] o anda satırlarını alır), Wolt Finances "Cash collection" satırı nakit siparişin teslim saatini
     * verir (D3 d). [until]'a kadar; [check] her kareden sonra çağrılır. Tıklama olayları kareler dosyasında yok.
     */
    fun replay(day: String, until: Long = Long.MAX_VALUE, list: (DeliveryCore, SahaFrame, List<PlatformHistory.Row>) -> Unit = { _, _, _ -> },
               check: (DeliveryCore, SahaFrame) -> Unit = { _, _ -> }): Pair<DeliveryCore, List<CoreEvent>> {
        val core = DeliveryCore().also { it.start() }
        val events = ArrayList<CoreEvent>()
        for (f in frames.filter { it.day == day && it.at <= until }) {
            val rows = PlatformHistory.parse(f.pkg, f.texts, f.at, baku)
            if (rows != null) { list(core, f, rows); continue }
            for (r in PlatformHistory.cashRows(f.pkg, f.texts)) {
                val at = PlatformHistory.toMs(r.day, r.clock, baku)
                if (at <= f.at + 60_000) PlatformHistory.cashMatch(r, at, core.trips.values)?.let { events += core.cashCollected(it.id, at, r.amount, f.at) }
            }
            events += core.observe(parse(f))
            check(core, f)
        }
        return core to events
    }
    private fun hm(at: Long?) = at?.let { java.time.Instant.ofEpochMilli(it).atOffset(java.time.ZoneOffset.ofHours(4)).toLocalTime().withNano(0).toString() } ?: "-"

    /** Çekirdek raporu: her siparişin kabul saati, restoranı, ücreti ve nasıl kapandığı. Hiç düşmez. */
    @Test fun cekirdekRaporu() {
        for (day in listOf("2026-10-11", "2026-10-12")) {
            val (core, ev) = replay(day)
            println("ÇEKİRDEK $day: teklif ${ev.count { it.type == "offer" }} ${ev.filter { it.type == "offer" }.map { hm(it.at) }}, sipariş ${core.trips.size}")
            for (t in core.trips.values) {
                val why = ev.lastOrNull { it.tripId == t.id && it.type in setOf("delivered", "delivered_inferred", "end_unconfirmed", "trip_completed") }
                println("  ${hm(t.acceptedAt)} ${t.pkg.substringAfter("com.").take(5)} ${(t.pickups.firstOrNull()?.restaurant ?: t.offer?.data?.restaurant ?: "?").take(22)}" +
                    " ücret=${t.offer?.data?.gross} faz=${t.phase} bitti=${hm(t.completedAt)} çıkarım=${hm(t.deliveredInferredAt)}/${t.deliveredInferredReason} son=${why?.type}/${why?.reason}")
            }
        }
    }

    /**
     * D3/D7 (saha 11–12 Ekim): Wolt kazanç listesi + nakit kayıtlarına göre gerçek görevler teslim (doğrulanmış ya da çıkarım)
     * olarak kapanır; 12 Ekim 12:27 Soloha (kurye iptal etti) ve 13:00 Harrbidoner (Wolt geri aldı) teslim sayılmaz.
     * Wolt'un bekleme ekranı açık görev varken hiç görünmüyor (9 siparişte 0 kare); bekleme ekranı açık görev bırakmaz.
     */
    @Test fun d3d7SiparislerDogruKapanir() {
        fun closedAsDelivered(t: TripRecord) = t.countsAsDelivered
        val (c11, _) = replay("2026-10-11")
        assertEquals(10, c11.trips.size)
        assertTrue(c11.trips.values.filterNot(::closedAsDelivered).map { hm(it.acceptedAt) }.toString(), c11.trips.values.all(::closedAsDelivered))
        val (c12, _) = replay("2026-10-12")
        val cancelled = c12.trips.values.filter { hm(it.acceptedAt).startsWith("12:27") || hm(it.acceptedAt).startsWith("13:00") }
        assertEquals(2, cancelled.size)
        assertTrue(cancelled.none(::closedAsDelivered))
        val real = c12.trips.values - cancelled.toSet()
        assertEquals(12, real.size)
        assertTrue(real.filterNot(::closedAsDelivered).map { hm(it.acceptedAt) }.toString(), real.all(::closedAsDelivered))
        assertEquals("Mix Point Cafe", real.first().pickups.first().restaurant)
        assertEquals(2, real.first().expectedOrders)
        for (core in listOf(c11, c12)) assertTrue(core.trips.values.none { it.active })
    }

    /**
     * D3 (b, c, d) + D4 kabul ölçütleri (saha 11–12 Ekim):
     * - aynı anda en fazla bir sipariş müşteri aşamasında; hiçbir Wolt siparişi bir sonraki Wolt siparişinin restoran ekranından
     *   sonra açık kalmıyor (b);
     * - teslim sayısı doğrulanmamışlar dahil (c): 11 Ekim Wolt 9 + Bolt 1, 12 Ekim Wolt 12 görev = 13 teslimat (Mix Point 2);
     * - nakit siparişin teslim saati Finances "Cash collection" saati (d) ve sipariş doğrulanmış teslim olur.
     */
    @Test fun d3d4TeslimSayimiVeKapanis() {
        val want = mapOf("2026-10-11" to Triple(9, 9, 1), "2026-10-12" to Triple(12, 13, 0))
        val cash = mapOf("2026-10-11" to listOf("King Doner" to "16:46", "Bonbon" to "17:45", "Fryday Caspian" to "19:50"),
            "2026-10-12" to listOf("Shaurma" to "17:56", "Firuze" to "18:26", "Arishta" to "19:30"))
        for ((day, n) in want) {
            val (core, events) = replay(day) { c, f ->
                val atCustomer = c.trips.values.filter { it.atCustomer }
                assertTrue("$day ${hm(f.at)} müşteride ${atCustomer.map { hm(it.acceptedAt) }}", atCustomer.size <= 1)
                val wolt = c.trips.values.filter { it.pkg == WOLT }
                for (a in wolt) if (a.active) assertTrue("$day ${hm(f.at)}: ${hm(a.acceptedAt)} sonraki siparişin restoran ekranından sonra açık",
                    wolt.none { b -> b.acceptedAt > a.acceptedAt && b.pickups.isNotEmpty() })
            }
            val wolt = core.trips.values.filter { it.pkg == WOLT }; val bolt = core.trips.values.filter { it.pkg != WOLT }
            assertEquals(day, n.first, wolt.count { it.countsAsDelivered })
            assertEquals(day, n.second, OrderHistory.deliveredOrders(wolt))
            assertEquals(day, n.third, OrderHistory.deliveredOrders(bolt))
            assertEquals(day, n.second + n.third, OrderHistory.Day(day, core.trips.values.toList()).orders)
            for ((name, clock) in cash.getValue(day)) {
                val t = wolt.single { OrderHistory.restaurant(it)!!.startsWith(name) }
                assertEquals("$day $name", clock, hm(t.completedAt).take(5))
                assertEquals("$day $name", "wolt_cash_collection", t.verifiedBy); assertEquals(TripPhase.FINISHED, t.phase)
            }
            assertTrue(events.none { it.type == "delivery_time_corrected" && hm(it.at).take(5) !in cash.getValue(day).map { c -> c.second } })
        }
    }

    /**
     * D3(c) (Faz 2 kabul kontrolü, 14 Ekim): kazanç listesi kareleri OrderTracker.platformHistory gibi göründükleri anda işlenir;
     * eşleşen her satır siparişini doğrular. Doğrulanmamış (çıkarım) teslimlerin hepsi listeyle doğrulanır (11 Ekim BIR-IKI;
     * 12 Ekim McDonald's Sahil, Zemanın), gün sonunda "doğrulanmadı" etiketi kalmaz; her Wolt teslimi liste ya da nakit
     * tahsilatıyla doğrulanmış olur. İptal edilen 2 sipariş (12 Ekim) hiçbir satırla eşleşmez.
     */
    @Test fun d3cListeDogrulanmamisTeslimleriDogrular() {
        val want = mapOf("2026-10-11" to listOf("BIR-IKI"), "2026-10-12" to listOf("McDonald's Sahil", "Zeman"))
        for ((day, names) in want) {
            val keys = HashMap<String, MutableSet<String>>(); val taken = HashMap<String, MutableSet<String>>(); val written = HashMap<String, MutableList<JSONObject>>()
            val core = replay(day, list = { c, f, rows ->
                val out = PlatformHistory.records(rows, f.at, f.src, f.pkg, baku, { keys.getOrPut(it) { HashSet() } }, { taken.getOrPut(it) { HashSet() } },
                    { d -> c.trips.values.filter { PlatformHistory.dayOf(it.acceptedAt, baku) == d } }, { written.getOrPut(it) { ArrayList() } })
                for ((_, j) in out) j.optJSONObject("match")?.optString("trip_id")?.let { c.listVerified(it, "wolt_earnings_list", f.at) }
            }).first
            val inferred = core.trips.values.filter { it.deliveredInferred }
            assertEquals(day, names, inferred.map { t -> names.first { OrderHistory.restaurant(t)!!.startsWith(it) } })
            for (t in inferred) {
                assertEquals("$day ${hm(t.acceptedAt)}", "wolt_earnings_list", t.verifiedBy)
                assertEquals(OrderHistory.LIST_VERIFIED.first, OrderHistory.status(t) { a, _ -> a })
            }
            assertEquals(day, 0, OrderHistory.Day(day, core.trips.values.toList()).inferred)
            val wolt = core.trips.values.filter { it.pkg == WOLT }
            assertTrue(day + " " + wolt.filter { it.countsAsDelivered && it.verifiedBy == null }.map { hm(it.acceptedAt) },
                wolt.filter { it.countsAsDelivered }.all { it.verifiedBy in setOf("wolt_earnings_list", "wolt_cash_collection") })
            assertTrue(wolt.filterNot { it.countsAsDelivered }.none { it.verifiedBy != null })
        }
    }

    /** D4: 11 Ekim 19:30'da Bugün sayısı telefonda 5'ti; o saate kadar en az 7 teslim vardı (saha listesi + nakit kayıtları). */
    @Test fun d4BugunSayisi1930() {
        val until = OffsetDateTime.parse("2026-10-11T19:30:00+04:00").toInstant().toEpochMilli()
        val (core, _) = replay("2026-10-11", until)
        val n = OrderHistory.deliveredOrders(core.trips.values)
        println("D4 11 Ekim 19:30 Bugün: $n")
        assertTrue("Bugün $n", n >= 7)
    }

    /**
     * D15/D23 (saha 11–12 Ekim): Wolt kazanç listesi kareleri OrderTracker gibi işlenir. Her teslim bir kez yazılır (OCR ad
     * varyantı "ZO0 28", "Beauty ls You" ve saat hatası 14:54/14:57 aynı teslim), hepsi bir siparişle eşleşir (kaçırılan yok;
     * Mix Point "2 deliveries" listede 12:46, kabul 12:41), "Extra waiting time" üstündeki siparişe gider (Bonbon 0,50, Borani 0,30).
     */
    @Test fun d15d23KazancListesi() {
        val want = mapOf("2026-10-11" to 8, "2026-10-12" to 12)
        val trips = HashMap<String, TripRecord>()
        for ((day, n) in want) {
            val core = replay(day).first
            val keys = HashMap<String, MutableSet<String>>(); val taken = HashMap<String, MutableSet<String>>(); val written = HashMap<String, MutableList<JSONObject>>()
            for (f in frames.filter { it.day == day && it.wolt }) {
                val rows = PlatformHistory.parse(f.pkg, f.texts, f.at, baku) ?: continue
                PlatformHistory.records(rows, f.at, f.src, f.pkg, baku, { keys.getOrPut(it) { HashSet() } }, { taken.getOrPut(it) { HashSet() } },
                    { d -> core.trips.values.filter { PlatformHistory.dayOf(it.acceptedAt, baku) == d } }, { written.getOrPut(it) { ArrayList() } })
                for (r in rows) { val d = r.day ?: day
                    PlatformHistory.owner(r, d, written.getOrPut(d) { ArrayList() })?.let { id -> core.trips[id]?.let { t -> r.extraWait?.let { t.extraWaitAzn = it }; r.tip?.let { t.platformTipAzn = it } } } }
            }
            val rows = written.getValue(day)
            assertEquals(day + " " + rows.map { it.getString("clock") + " " + it.optString("restaurant") }, n, rows.size)
            assertTrue(day + " kaçırılan " + rows.filter { it.opt("missed_by_kuryeplus") != false }.map { it.getString("clock") + " " + it.optString("restaurant") },
                rows.all { it.opt("missed_by_kuryeplus") == false })
            assertTrue(rows.all { it.getString("day_source") == "screen_header" })
            core.trips.values.forEach { trips[day + " " + hm(it.acceptedAt).take(5)] = it }
        }
        assertEquals(0.50, trips.getValue("2026-10-11 17:09").extraWaitAzn, 1e-9); assertEquals(0.30, trips.getValue("2026-10-11 18:01").extraWaitAzn, 1e-9)
        assertEquals(listOf("2026-10-11 17:09", "2026-10-11 18:01"), trips.filter { it.value.extraWaitAzn > 0 }.keys.sorted())
        assertEquals(1.15, trips.getValue("2026-10-12 14:54").platformTipAzn!!, 1e-9)
        assertEquals(4.83 + 0.50, trips.getValue("2026-10-11 17:09").earnings!!, 1e-9)
    }

    /** D1/D2: kabul edilen her siparişin teklif ücreti Wolt kazanç listesindekiyle aynı (teklif kartı yakalandıysa). */
    @Test fun d1d2SiparisUcretleriListeyleAyni() {
        val want = mapOf("2026-10-11" to listOf(4.82, 1.63, 4.83, 2.27, 2.61, 2.31, 3.79, 1.84, 1.97, 2.99),
            "2026-10-12" to listOf(null, 4.65, 1.74, 3.03, 3.96, 4.75, 2.13, 3.85, 1.32, 3.83, 2.37, 2.77, 1.61, 4.49))
        for ((day, fees) in want) assertEquals(day, fees, replay(day).first.trips.values.map { it.offer?.data?.gross })
    }

    /** D11/D24: Wolt siparişine kabul anından önceki en son plan gözleminin yüzdesi (11 Ekim beklenen liste). */
    @Test fun d11d24SipariseBoostYazilir() {
        val boosts = BoostLog(); val core = DeliveryCore().also { it.start() }
        val got = ArrayList<Pair<String, Double?>>()
        for (f in frames.filter { it.day == "2026-10-11" }) {
            val o = parse(f)
            boosts.observe(o, f.day, null)
            for (e in core.observe(o)) if (e.type == "accepted" && e.pkg == WOLT) {
                val t = core.trips[e.tripId]!!
                val at = t.offer?.firstAt ?: t.acceptedAt
                got += hm(at).take(5) to boosts.atOffer(t.offer?.data ?: o, at).value
            }
        }
        assertEquals(listOf("16:30" to 20.0, "17:09" to 30.0, "17:45" to 30.0, "18:01" to 50.0, "18:37" to 50.0, "18:51" to 50.0,
            "19:07" to 40.0, "19:18" to 40.0, "19:28" to 40.0), got)
    }

    /** D25: teklif kartında "Cash order" olan siparişler nakit (Finances "Cash collection" kayıtlarıyla aynı siparişler). */
    @Test fun d25NakitSiparislerTaninir() {
        val want = mapOf("2026-10-11" to listOf("16:30", "17:09", "19:30"), "2026-10-12" to listOf("13:00", "17:40", "18:04", "18:57"))
        for ((day, times) in want) {
            val core = replay(day).first
            assertEquals(day, times, core.trips.values.filter { it.paymentMethod == "cash" }.map { hm(it.acceptedAt).take(5) })
            val bonbon = core.trips.values.firstOrNull { it.offer?.data?.restaurant?.startsWith("Bonbon") == true }
            if (day == "2026-10-11") { assertEquals(45.75, bonbon!!.offer!!.data.changeNeeded!!, 0.001); assertEquals(4.83, bonbon.offer!!.data.gross!!, 0.001) }
        }
    }

}
