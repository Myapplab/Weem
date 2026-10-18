package com.kuryelog.logic.economics

import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.orders.OfferState
import com.kuryelog.logic.orders.TripRecord
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

/**
 * E4 okuyucusu (RS `MainActivity.offerScoreOf/pickRating`): puanlar teklifin gününden okunur, 10 sn önbellek, puan verince temizlenir;
 * benim puanım yeni satır (düzeltme de), uygulamanın puanı değişmez; kart satırı teklifsiz siparişte yok.
 */
class OfferScoreBookTest {
    @get:Rule val tmp = TemporaryFolder()
    private val baku = ZoneId.of("Asia/Baku")
    private val wolt = "com.wolt.courierapp"
    /** 2026-10-06 18:00 Baku. */
    private val t0 = 1791295200000L
    private var now = t0 + 3_600_000
    private val tr = { a: String, _: String -> a }

    private fun book(): Pair<OfferScoreBook, LogStore> {
        val log = LogStore(File(tmp.root, "logs"), now = { now }, zone = { baku })
        val env = object : EconomicsObserver.Env {
            override val log = log
            override val prefs = KuryelogPrefs.Memory()
            override val clock = object : Clock { override fun wallMs() = now; override fun monoMs() = now; override fun zone() = baku }
        }
        return OfferScoreBook(EconomicsObserver(env), log::day, { now }) to log
    }

    private fun trip(id: String, offer: Boolean = true) = TripRecord(id, wolt,
        if (offer) OfferState("o-$id", wolt, t0, CourierObservation(wolt, t0, ScreenStage.OFFER, true, "ocr", emptyList(), emptyList(), gross = 3.0)) else null, t0 + 5_000)

    private fun score(id: String, v: Int) = JSONObject().put("schemaVersion", 1).put("kind", "offer_score").put("offer_id", id).put("score", v).toString()

    @Test fun readsTheOfferDaysScoresAndMyNewestRating() {
        val (book, log) = book()
        val day = log.day(t0)
        log.append(OfferScore.FILE, score("o-a", 7), day)
        assertEquals("Teklif puanı: Uygulama 7 · Sen —", book.cardLine(trip("a"), tr))
        assertNull(book.cardLine(trip("x", offer = false), tr))
        assertEquals(null to null, book.of(trip("x", offer = false)))

        assertTrue(book.rate(trip("a"), 4, OfferScore.VIA_DAY))
        assertTrue(book.rate(trip("a"), 9, OfferScore.VIA_DAY))
        assertEquals("Teklif puanı: Uygulama 7 · Sen 9 (fark -2)", book.cardLine(trip("a"), tr))
        val lines = File(log.dayDir(day), OfferScore.RATINGS).readLines().map(::JSONObject)
        assertEquals(listOf(4, 9), lines.map { it.getInt("rating") })
        assertEquals(listOf("day_screen", "day_screen"), lines.map { it.getString("via") })
        assertEquals(1, File(log.dayDir(day), OfferScore.FILE).readLines().size)
    }

    @Test fun rereadsADayAtMostEveryTenSeconds() {
        val (book, log) = book()
        val day = log.day(t0)
        assertEquals("Teklif puanı: Uygulama — (puan kaydı yok) · Sen —", book.cardLine(trip("a"), tr))
        log.append(OfferScore.FILE, score("o-a", 6), day)
        now += OfferScoreBook.CACHE_MS - 1
        assertEquals("Teklif puanı: Uygulama — (puan kaydı yok) · Sen —", book.cardLine(trip("a"), tr))
        now += 1
        assertEquals("Teklif puanı: Uygulama 6 · Sen —", book.cardLine(trip("a"), tr))
    }
}
