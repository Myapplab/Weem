package com.kuryelog.logic.link

import com.kuryelog.logic.core.CourierPlatform
import com.kuryelog.logic.core.DaySummary
import com.kuryelog.logic.core.OfferSummary
import com.kuryelog.logic.core.ScooterLive
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.core.TripStages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

// Rawstats FeedChangesE8Test.kt @ 6388c04 (8 test), aynı girdi → aynı karar. WEEM (LINK.md 4.2): YolGo sağlayıcılarının `notifyChange`
// temposu 2 telefon iletilerine dönüştü. Eşlemeler: `offersKey` → teklif listesi (kare saati `lastSeenAtMs` 5 sn dilimiyle), `courierState`
// → `TripStages` (hız mesajda yok), `changedAtMs` → `ShiftState.generatedAtMs`, anahtar sırası → tipli eşitlik, `FeedChanges(200)` →
// teklif aralığı, `scooterKey` → `BatterySummary` (hız mesajda yok: S6/plan 4.4), `NotifyThrottle(5 s)` → gün özeti (plan 4.4: dakikada bir).

/**
 * E8: iletiler yalnız içerik değişince gider; kare saati, kare yaşı ve üretim saati değişim sayılmaz; görünen teklif 5 sn'lik dilimle
 * yeniden duyurulur; aralıkta gelen son değişiklik mutlaka çıkar.
 */
class FeedChangesE8Test {
    private val t0 = 1_791_295_200_000L
    private var now = t0

    private class Count : LinkPort {
        val sent = ArrayList<Pair<String, ByteArray>>()
        override fun linked() = true
        override fun publishLatest(type: String, payload: ByteArray, ttlMs: Long): UUID { sent += type to payload; return UUID.randomUUID() }
        override fun publishReliable(type: String, payload: ByteArray, ttlMs: Long): UUID { sent += type to payload; return UUID.randomUUID() }
        fun n(t: LinkTopic) = sent.count { it.first == t.type }
        fun last(t: LinkTopic) = sent.last { it.first == t.type }.second
    }

    private val out = Count()
    private val p = PhoneBridge(LinkRole.PROVIDER, out, { now }, CoroutineScope(Dispatchers.Unconfined))
    private val r = PhoneBridge(LinkRole.RECEIVER, out, { now }, CoroutineScope(Dispatchers.Unconfined))

    private fun offer(fee: Double?, frameAt: Long) = OfferSummary("offer-$t0-1", CourierPlatform.WOLT, t0, frameAt, true,
        displayedFeeAzn = fee, restaurant = "Borani Sahil", shown = OfferSummary.Shown(km = 2.0))

    @Test fun newOfferAndItsFeeAreChangesTheFrameClockIsNot() {
        p.providerOffers(emptyList(), now)
        now = t0 + 250
        p.providerOffers(listOf(offer(null, t0)), now)
        assertEquals("kartın kendisi", 2, out.n(LinkTopic.OFFERS))
        // Aynı kart 0,4 sn ve 1,2 sn sonra (OCR ve erişilebilirlik okuması): Receiver'a yeni bir şey yok.
        now = t0 + 450; p.providerOffers(listOf(offer(null, t0 + 400)), now)
        now = t0 + 1_250; p.providerOffers(listOf(offer(null, t0 + 1_200)), now)
        assertEquals(2, out.n(LinkTopic.OFFERS))
        // Wolt ücreti karttan 3 sn sonra yükledi (D6): değişim.
        now = t0 + 3_050; p.providerOffers(listOf(offer(2.61, t0 + 3_000)), now)
        assertEquals(3, out.n(LinkTopic.OFFERS))
    }

    @Test fun aVisibleOfferIsReannouncedEveryFiveSecondsNotEveryFrame() {
        // 14 sn boyunca 350 ms'de bir kare: ilk duyuru + lastSeenAtMs sonraki 5 sn dilimine girince (2 kez).
        for (i in 0 until 40) {
            now = t0 + i * 350L + 20
            p.providerOffers(listOf(offer(2.61, t0 + i * 350L)), now)
            p.tick(now)
        }
        assertEquals(3, out.n(LinkTopic.OFFERS))
    }

    @Test fun courierStageIsAChangeLiveSpeedIsNot() {
        fun st(stage: TripStages.Stage) = TripStages(listOf(TripStages.Trip("trip-1", CourierPlatform.WOLT.pkg, true, stage, null, null, emptyList())))
        // Hız aşama iletisinde yok: aynı aşama yeniden gelse de gitmez; aşama değişince gider.
        p.providerStages(st(TripStages.Stage.TO_PICKUP))
        p.providerStages(st(TripStages.Stage.TO_PICKUP))
        p.providerStages(st(TripStages.Stage.TO_PICKUP))
        assertEquals(1, out.n(LinkTopic.STAGES))
        p.providerStages(st(TripStages.Stage.AT_PICKUP))
        assertEquals(2, out.n(LinkTopic.STAGES))
    }

    @Test fun ourOwnChangeTimeNeverFeedsBack() {
        p.providerShift(ShiftState(true, startedAtMs = t0, generatedAtMs = t0))
        p.providerShift(ShiftState(true, startedAtMs = t0, generatedAtMs = t0 + 999))
        assertEquals(1, out.n(LinkTopic.SHIFT))
        p.providerShift(ShiftState(false, startedAtMs = t0, endedAtMs = t0 + 5_000, generatedAtMs = t0 + 5_000))
        assertEquals(2, out.n(LinkTopic.SHIFT))
    }

    @Test fun keyIsCanonicalWhateverTheKeyOrder() {
        // Tipli eşitlik: aynı teklif iki kez kurulsa da aynı içerik; JSON anahtar sırası okuyucuda önemsiz.
        p.providerOffers(listOf(offer(2.61, t0)), now)
        now += 300
        p.providerOffers(listOf(offer(2.61, t0).copy()), now)
        assertEquals(1, out.n(LinkTopic.OFFERS))
        val a = """{"schemaVersion":1,"offers":[]}""".toByteArray()
        val b = """{"offers":[],"schemaVersion":1}""".toByteArray()
        assertEquals(LinkPayloads.Decoder(0).offers(a), LinkPayloads.Decoder(0).offers(b))
    }

    @Test fun publishOncePerChangeWithAShortGapAndTheLastChangeAlwaysOut() {
        fun o(fee: Double) = listOf(offer(fee, t0))
        p.providerOffers(o(1.0), t0)
        assertEquals("ilk içerik duyurulur", 1, out.n(LinkTopic.OFFERS))
        p.providerOffers(o(1.0), t0 + 500)
        assertEquals("değişmedi", 1, out.n(LinkTopic.OFFERS))
        p.providerOffers(o(2.0), t0 + 520)
        assertEquals("sessiz kanaldaki değişim hemen", 2, out.n(LinkTopic.OFFERS))
        // C, B'den 40 ms sonra: aralık bitene dek bekler, sonra gider.
        p.providerOffers(o(3.0), t0 + 560); p.tick(t0 + 560)
        p.providerOffers(o(3.0), t0 + 700); p.tick(t0 + 700)
        assertEquals(2, out.n(LinkTopic.OFFERS))
        p.tick(t0 + 730)
        assertEquals(3, out.n(LinkTopic.OFFERS))
        assertEquals(listOf(3.0), LinkPayloads.Decoder(0).offers(out.last(LinkTopic.OFFERS))!!.map { it.displayedFeeAzn })
        // D aralıkta titrer ve içerik C'ye döner: söylenecek bir şey yok.
        p.providerOffers(o(4.0), t0 + 800)
        p.providerOffers(o(3.0), t0 + 850)
        p.tick(t0 + 1_000)
        assertEquals(3, out.n(LinkTopic.OFFERS))
        p.providerOffers(o(4.0), t0 + 1_100)
        assertEquals(4, out.n(LinkTopic.OFFERS))
        // Türler bağımsız.
        r.receiverScooter(null, t0 + 1_101)
        assertEquals(1, out.n(LinkTopic.BATTERY))
    }

    @Test fun scooterKeyIgnoresFrameAgeButNotStalenessOrLinkLoss() {
        fun live(age: Long?, link: Boolean = true, pct: Double = 78.0) =
            ScooterLive(t0 + (age ?: 0), link, age, ScooterLive.Values(internalPct = pct, speedKmh = 18.5, powerW = -310.0))
        r.receiverScooter(live(400), t0)
        r.receiverScooter(live(900), t0 + 500)
        assertEquals(1, out.n(LinkTopic.BATTERY))
        r.receiverScooter(live(20_000), t0 + 20_000)
        assertEquals("20 sn yeni kare yok: Provider canlı göstermemeli", 2, out.n(LinkTopic.BATTERY))
        r.receiverScooter(live(null, link = false), t0 + 21_000)
        assertEquals(3, out.n(LinkTopic.BATTERY))
        r.receiverScooter(live(400), t0 + 22_000)
        r.receiverScooter(live(400, pct = 77.0), t0 + 23_000)
        assertEquals(5, out.n(LinkTopic.BATTERY))
    }

    @Test fun dayChangesAreCoalescedAndTheLastOneIsAlwaysAnnounced() {
        fun d(n: Int, day: String = "2026-10-14") = DaySummary(day, mapOf(CourierPlatform.WOLT to DaySummary.PlatformDay(n)))
        p.providerDay(d(1), 1_000)
        p.providerDay(d(1), 1_200)
        assertEquals(1, out.n(LinkTopic.DAY))
        // Aralıkta yeni satır: düşmez, aralık bitince duyurulur (WEEM aralığı 60 sn).
        p.providerDay(d(2), 3_000)
        p.tick(4_000)
        assertEquals(1, out.n(LinkTopic.DAY))
        p.tick(61_000)
        assertEquals(2, out.n(LinkTopic.DAY))
        p.providerDay(d(0, "2026-10-13"), 62_000)
        assertEquals("başka günün kendi aralığı", 3, out.n(LinkTopic.DAY))
    }
}
