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
 * A10.4a (S20, WEEM 1.1) — Siparişler → Çevrimiçi oturumlar (ORDERS.md 9.3, 10.3). Rawstats `renderSessions` ekran koduydu ve eski testi
 * yoktu: beklenen metin ve sıra o koddan (satır satır) yazıldı. Sentetik kayıtlar.
 */
class OrdersSessionsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    private val wolt = "com.wolt.courierapp"
    private val bolt = "com.bolt.courier"
    private val min = 60_000L
    private val hour = 60 * min
    /** 2026-10-16 09:00 Bakü. */
    private val t0 = java.time.ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, baku).toInstant().toEpochMilli()

    private class FakeClock(var now: Long, val zoneId: ZoneId) : Clock {
        override fun wallMs() = now
        override fun monoMs() = now
        override fun zone() = zoneId
    }

    private class Rig(val log: LogStore, val db: CourierRecords.Memory, val prefs: KuryelogPrefs.Memory, val clock: FakeClock) {
        val source get() = LogOrdersSource(log, db, prefs, clock)
        fun add(t: TripRecord) = db.saveTrip(RecordJson.trip(t))
        fun session(c: OnlineSessions.Closed) {
            val dir = log.dayDir(log.day(c.endAt)).apply { mkdirs() }
            File(dir, OrderFlow.ONLINE_SESSIONS_FILE).appendText(OnlineSessions.json(c).toString() + "\n")
        }
    }

    private var rigs = 0
    private fun rig(now: Long = t0 + 5 * hour): Rig {
        val clock = FakeClock(now, baku)
        return Rig(LogStore(File(tmp.root, "logs${rigs++}"), now = { clock.now }, zone = { baku }), CourierRecords.Memory(), KuryelogPrefs.Memory(), clock)
    }

    private fun closed(pkg: String, start: Long, end: Long, startObserved: Boolean = true, endObserved: Boolean = true, reason: String = "offline_screen",
                       offers: Int = 4, accepted: Int = 2, rejected: Int = 1, delivered: Int = 2) =
        OnlineSessions.Closed(pkg, start, end, startObserved, endObserved, reason, offers, accepted, delivered, rejected = rejected)

    private fun obs(pkg: String, at: Long) =
        CourierObservation(pkg, at, ScreenStage.OFFER, true, "accessibility", emptyList(), emptyList(), gross = 3.0, km = 2.0, minLo = 10, minHi = 14, restaurant = "Test Döner")

    private fun trip(id: String, session: String?, at: Long, restaurant: String, pkg: String = wolt) =
        TripRecord(id, pkg, OfferState("o-$id", pkg, at - min, obs(pkg, at - min)), at, TripPhase.FINISHED).also { t ->
            t.pickups.add(PickupRecord(restaurant, "Test Cad. 1", arrivedAt = at + 5 * min, pickedAt = at + 9 * min, arrivalSource = "gps_dwell"))
            t.completedAt = at + 20 * min
            t.sessionId = session
        }

    @Test fun emptyDayExplainsWhereSessionsComeFrom() {
        val page = OrdersSessions(rig().source, Lang.TR).page()
        assertEquals("Bugün çevrimiçi oturum görülmedi. Oturumlar vardiya açıkken Wolt/Bolt ekranından kaydedilir.", page.empty)
        assertTrue(page.groups.isEmpty()); assertNull(page.note)
        assertEquals("Bu gün onlayn sessiya görünməyib. Sessiyalar növbə açıq olanda Wolt/Bolt ekranından qeyd olunur.", OrdersSessions(rig().source, Lang.AZ).page().empty)
    }

    @Test fun groupsPerPlatformWithTotalsRangesAndCounters() {
        val r = rig()
        r.session(closed(wolt, t0, t0 + hour)); r.session(closed(wolt, t0 + 90 * min, t0 + 3 * hour + 20 * min, offers = 6, accepted = 3, rejected = 2, delivered = 3))
        r.session(closed(bolt, t0 + 30 * min, t0 + 2 * hour))
        val page = OrdersSessions(r.source, Lang.TR).page()
        assertEquals(listOf("Wolt", "Bolt Food"), page.groups.map { it.platform })
        // Wolt: 60 + 110 dk = 2 sa 50 dk, iki kez; arada çevrimdışı 10:00 – 10:30 (30 dk).
        val w = page.groups[0]
        assertEquals(Tone.WOLT, w.tone)
        assertEquals("Wolt · bugün 2 kez · 2 sa 50 dk", w.heading)
        assertNull(w.entries[0].offline)
        assertEquals("09:00 – 10:00  ·  1 sa 0 dk", w.entries[0].range)
        assertEquals("4 teklif görüldü · 2 kabul · 1 ret · 2 teslim", w.entries[0].counters)
        assertEquals("Çevrimdışı 10:00 – 10:30 · 30 dk", w.entries[1].offline)
        assertEquals("10:30 – 12:20  ·  1 sa 50 dk", w.entries[1].range)
        assertEquals("6 teklif görüldü · 3 kabul · 2 ret · 3 teslim", w.entries[1].counters)
        assertEquals("Bolt Food · bugün 1 kez · 1 sa 30 dk", page.groups[1].heading)
        assertEquals(Tone.BOLT, page.groups[1].tone)
        assertEquals("≈ : başlangıç/bitiş ekranı görülmedi; ilk/son görülen çevrimiçi ekranın saati.", page.note)
    }

    @Test fun unobservedEdgesGetATildeAndAnOpenSessionSaysSo() {
        val r = rig()
        r.session(closed(wolt, t0, t0 + hour, startObserved = false, endObserved = false, reason = "restart_after_shift"))
        // Açık dönem (devam ediyor): bitiş "devam ediyor", ondan sonra çevrimdışı boşluk satırı yok.
        val src = object : OrdersSource by r.source {
            override fun onlineToday() = listOf(closed(wolt, t0, t0 + hour, startObserved = false, endObserved = false, reason = "restart_after_shift"),
                closed(wolt, t0 + 2 * hour, t0 + 4 * hour, reason = "open"), closed(wolt, t0 + 5 * hour, t0 + 6 * hour))
        }
        val e = OrdersSessions(src, Lang.TR).page().groups.single().entries
        assertEquals("≈09:00 – ≈10:00  ·  1 sa 0 dk", e[0].range)
        assertEquals("11:00 – devam ediyor  ·  2 sa 0 dk", e[1].range)
        assertEquals("Çevrimdışı 10:00 – 11:00 · 1 sa 0 dk", e[1].offline)
        assertNull("açık dönemden sonra bitiş bilinmez: boşluk yazılmaz", e[2].offline)
    }

    @Test fun tripsOfASessionListedByAcceptTimeWithRestaurantAndStatus() {
        val r = rig()
        val s = closed(wolt, t0, t0 + 3 * hour)
        r.session(s)
        r.add(trip("late", s.id, t0 + 2 * hour, "Test Kafe")); r.add(trip("early", s.id, t0 + 30 * min, "Test Döner"))
        r.add(trip("other", "wolt-elsewhere", t0 + hour, "Başka Restoran"))
        // Bolt oturumunun siparişi Wolt oturumunda görünmez; oturumu bilinmeyen sipariş hiçbir yerde görünmez.
        r.add(trip("none", null, t0 + 90 * min, "Kimsesiz", wolt))
        val lines = OrdersSessions(r.source, Lang.TR).page().groups.single().entries.single().trips
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("09:30 · Test Döner · "))
        assertTrue(lines[1], lines[1].startsWith("11:00 · Test Kafe · "))
        assertEquals(OrderHistory.status(trip("x", s.id, t0, "x"), { tr, _ -> tr }), lines[0].substringAfter("Test Döner · "))
    }

    @Test fun azipTextAndNoCustomerDataOnThePage() {
        val r = rig()
        val s = closed(wolt, t0, t0 + hour)
        r.session(s); r.add(trip("a", s.id, t0 + 10 * min, "Test Döner"))
        val page = OrdersSessions(r.source, Lang.AZ).page()
        assertEquals("Wolt · bu gün 1 dəfə · 1 saat 0 dəq", page.groups.single().heading)
        assertEquals("4 təklif görüldü · 2 qəbul · 1 imtina · 2 çatdırılma", page.groups.single().entries.single().counters)
        assertTrue(page.note!!.startsWith("≈ : başlanğıc/bitiş"))
        // Sayfa yalnız restoran adı ve durum gösterir: müşteri adı, telefon, adres yok.
        val all = page.groups.flatMap { g -> g.entries.flatMap { listOf(it.range, it.counters) + it.trips } }.joinToString("\n")
        assertFalse(all.contains("Test Cad."))
    }

    @Test fun todayTripsComeFromTheSourceNotFromOtherDays() {
        val r = rig()
        val s = closed(wolt, t0, t0 + hour)
        r.session(s)
        // Dünkü kayıt (aynı oturum kimliğiyle olsa bile) bugünün listesine girmez.
        r.add(trip("yesterday", s.id, t0 - 24 * hour, "Dünkü Restoran"))
        r.add(trip("today", s.id, t0 + 20 * min, "Test Döner"))
        val lines = OrdersSessions(r.source, Lang.TR).page().groups.single().entries.single().trips
        assertEquals(1, lines.size); assertTrue(lines[0].contains("Test Döner"))
    }

    @Test fun cardsHoldTheLinesInOrderAndEndWithTheNote() {
        val r = rig()
        val s = closed(wolt, t0, t0 + hour)
        r.session(s); r.session(closed(wolt, t0 + 90 * min, t0 + 2 * hour)); r.add(trip("a", s.id, t0 + 10 * min, "Test Döner"))
        val cards = OrdersSessions(r.source, Lang.TR).page().cards()
        assertEquals(2, cards.size)
        val b = cards[0].blocks
        assertEquals(Block.Heading("Wolt · bugün 2 kez · 1 sa 30 dk", Tone.WOLT), b[0])
        assertEquals(Block.Line("09:00 – 10:00  ·  1 sa 0 dk", Style.BOLD), b[1])
        assertEquals(Block.Line("4 teklif görüldü · 2 kabul · 1 ret · 2 teslim", Style.MUTED), b[2])
        assertTrue((b[3] as Block.Line).text.startsWith("09:10 · Test Döner · "))
        assertEquals(Block.Divider, b[4])
        assertEquals(Block.Line("Çevrimdışı 10:00 – 10:30 · 30 dk", Style.MUTED), b[5])
        assertNotEquals("son dönemden sonra ayırıcı yok", Block.Divider, b.last())
        assertEquals(Style.MUTED, (cards[1].blocks.single() as Block.Line).style)
        assertEquals(1, OrdersSessions(rig().source, Lang.TR).page().cards().size)
    }
}
