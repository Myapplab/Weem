package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/**
 * YolGo `home_day_rules_test.dart` (19) → WEEM tek ev kuralı = Rawstats `HomeShift` (CORE.md 1.9, 6.2). `idle(f)` = `observe(f, false)`,
 * `day(f, busy)` = `observe(f, true, busy)`; elle başlatma `onManualStart()`, elle bitirme `onManualStop()`; YolGo'nun konum dönüşümü
 * (1/111320° enlem, doğu 1/85000°).
 *
 * "Aynen" 8 · fark 1 (sıçrayan fix, 1.9) · F4.5b ölçekli 8 (YolGo `legacy()` 200 m / 10 km/s / 2 dk ile parametreliydi; WEEM kuralı
 * parametresiz: 50 m / 5 km/s / 30 sn) · çevrilmez 1 ("ayar kapalıysa başlamaz": ayar yok) · uyarlanır 1 (kalıcı durum).
 */
class HomeDayRulesTest {
    private val homeLat = 40.40
    private val homeLon = 49.85

    /** [m] metre kuzeyde bir fix; [ms] monoton saat. */
    private fun at(m: Double, ms: Long, kmh: Double = 0.0, acc: Double = 8.0) =
        HomeShift.Fix(ms, homeLat + m / 111320, homeLon, acc, kmh / 3.6)

    private fun home() = HomeShift(homeLat, homeLon)
    private fun HomeShift.idle(f: HomeShift.Fix) = observe(f, false)
    private fun HomeShift.day(f: HomeShift.Fix, busy: Boolean = false) = observe(f, true, busy)

    private val none = HomeShift.Decision.NONE
    private val start = HomeShift.Decision.START
    private val end = HomeShift.Decision.END

    // ------------------------------------------------------------------ gün başlangıcı (home_departure) — aynen
    @Test fun twoConsistentFixesFiftyMetresOutAtTenKmhStartOnceWithoutBeingSeenAtHome() {
        val r = home()
        assertEquals(none, r.idle(at(49.0, 0, kmh = 20.0))) // başlangıç yarıçapı içinde
        assertEquals(none, r.idle(at(51.0, 1000, kmh = 10.0))) // ilk tutarlı fix
        assertEquals(start, r.idle(at(54.0, 2000, kmh = 10.0)))
        assertEquals(none, r.day(at(57.0, 3000, kmh = 10.0))) // vardiya sürerken tekrar başlamaz
    }

    @Test fun belowTenKmhNeverStartsAndInsideFiftyMetresIsNotADeparture() {
        val r = home()
        for (i in 0 until 6) assertEquals(none, r.idle(at(300.0 + i, i * 1000L, kmh = 9.9)))
        val b = home()
        for (i in 0 until 6) assertEquals(none, b.idle(at(49.9, i * 1000L, kmh = 20.0)))
    }

    @Test fun withoutGpsSpeedTheSpeedBetweenFixesDoesNotCountAtTheStart() {
        val r = home()
        val out = (0..6).map { i -> r.idle(at(250 + i * 2.5, i * 1000L)) }
        assertFalse(out.contains(start))
    }

    @Test fun gpsJitterWhileStandingOutsideDoesNotStart() {
        val r = home()
        for (i in 0 until 120) assertEquals(none, r.idle(at(400.0 + if (i % 2 == 0) 3 else -3, i * 500L, acc = 10.0)))
    }

    @Test fun poorAccuracyAndOutOfOrderTimeBreakThePair() {
        val r = home()
        assertEquals(none, r.idle(at(250.0, 0, kmh = 15.0, acc = 21.0)))
        assertEquals(none, r.idle(at(252.0, 1000, kmh = 15.0, acc = 21.0)))
        assertEquals(none, r.idle(at(254.0, 2000, kmh = 15.0)))
        assertEquals(none, r.idle(at(256.0, 1500, kmh = 15.0))) // zaman geriye gitti
        assertEquals(none, r.idle(at(258.0, 3000, kmh = 15.0)))
        assertEquals(start, r.idle(at(262.0, 4000, kmh = 15.0)))
    }

    @Test fun afterAManualStopAtHomeItStartsOnlyOnceLeftTheZone() {
        val r = home().also { it.onManualStop() }
        for (i in 0 until 4) assertEquals(none, r.idle(at(20.0, i * 1000L, kmh = 12.0)))
        assertEquals(none, r.idle(at(205.0, 20000, kmh = 12.0))) // çıkış: ilk fix
        assertEquals(start, r.idle(at(209.0, 21000, kmh = 12.0)))
    }

    @Test fun afterAManualStopOutsideRidingHomeOpensNoDayButLeavingHomeAgainDoes() {
        val r = home().also { it.onManualStop() }
        for (i in 0 until 10) assertEquals(none, r.idle(at(2000.0 - i * 4, i * 1000L, kmh = 15.0)))
        assertEquals(none, r.idle(at(30.0, 600000))) // evde görüldü
        assertEquals(none, r.idle(at(220.0, 700000, kmh = 15.0)))
        assertEquals(start, r.idle(at(224.0, 701000, kmh = 15.0)))
    }

    @Test fun afterAnAutomaticEndThereIsNoWaitAndLeavingHomeStartsAgain() {
        val r = home() // otomatik bitişte koruyucu kurulmaz (endArmed=false, startBlocked=false)
        assertEquals(none, r.idle(at(210.0, 0, kmh = 10.0)))
        assertEquals(start, r.idle(at(213.0, 1000, kmh = 10.0)))
    }

    // ------------------------------------------------------------------ fark (CORE.md 1.9)
    /** Rawstats kuralı: 750 m sıçraması atılır, taban 250 m kalır → hiç başlamaz; kesinti ≤120 sn çifti bozmaz. */
    @Test fun jumpingFixIsDroppedAndThePairGapIsOneHundredTwentySeconds() {
        val r = home()
        assertEquals(none, r.idle(at(250.0, 0, kmh = 15.0)))
        assertEquals(none, r.idle(at(750.0, 1000, kmh = 15.0))) // 500 m / 1 sn: sıçrama
        assertEquals(none, r.idle(at(752.0, 2000, kmh = 15.0)))
        assertEquals(none, r.idle(at(756.0, 3000, kmh = 15.0))) // 4 fix'in hepsi none
        val g = home()
        assertEquals(none, g.idle(at(250.0, 0, kmh = 15.0)))
        assertEquals(start, g.idle(at(260.0, 31000, kmh = 15.0))) // 31 sn arayla çift (YolGo: 30 sn sınırı)
    }

    // ------------------------------------------------------------------ gün bitişi (home_arrival) — F4.5b ölçekli
    /** Gün içinde dışarıda (kurulur), eve yavaş giriş, sonra 20 m'de dalgalı bekleme. İndeks 2 = giriş fix'i. */
    private fun ride(r: HomeShift, busy: Boolean = false, wobbleM: Double = 3.0, dwellS: Int = 130): List<HomeShift.Decision> = buildList {
        add(r.day(at(900.0, 0, kmh = 20.0), busy))
        add(r.day(at(600.0, 60000, kmh = 20.0), busy))
        add(r.day(at(20.0, 120000, kmh = 4.0), busy)) // bölgeye <5 km/s giriş
        for (s in 1..dwellS) add(r.day(at(20 + if (s % 2 == 0) wobbleM else 0.0, 120000 + s * 1000L), busy))
    }

    @Test fun afterRidingOutAndEnteringHomeThirtySecondsStillEndsExactlyAtTheThirtieth() {
        val r = home()
        val out = ride(r)
        assertEquals(1, out.count { it == end })
        assertEquals(2 + 30, out.indexOf(end)) // giriş fix'inden 30 sn sonra
        assertFalse(r.endArmed)
    }

    @Test fun startingTheShiftAtHomeAndNeverLeavingNeverEnds() {
        val r = home().also { it.onManualStart() }
        for (s in 0 until 400) assertEquals(none, r.day(at(40.0, s * 1000L)))
        assertFalse(r.endArmed)
    }

    /** Rawstats kurulumda doğruluk payı düşmez: doğruluk ≤30 olan fix evin dışındaysa kurulur (YolGo'da kurmazdı). */
    @Test fun aSingleStrayFixWithAcceptableAccuracyArmsAndTheEndFollowsThirtySecondsLater() {
        val r = home().also { it.onManualStart() }
        r.day(at(40.0, 0))
        r.day(at(70.0, 1000, acc = 30.0))
        assertTrue(r.endArmed)
        val ends = (2..40).filter { s -> r.day(at(40.0, s * 1000L)) == end }
        assertEquals(listOf(32), ends) // 40 m'deki pencere 2. sn'de başladı
        // Doğruluk 31–35 m: fix atılır, kurulmaz.
        for (acc in listOf(31.0, 33.0, 35.0)) {
            val q = home().also { it.onManualStart() }
            q.day(at(40.0, 0))
            q.day(at(70.0, 1000, acc = acc))
            assertFalse("$acc", q.endArmed)
            for (s in 2 until 200) assertEquals(none, q.day(at(40.0, s * 1000L)))
        }
    }

    @Test fun driftBeyondTenMetresRestartsTheWindow() {
        val out = ride(home(), wobbleM = 10.5, dwellS = 200)
        assertFalse(out.contains(end))
    }

    @Test fun enteringTheZoneFastOpensNoWindowUntilTheSpeedDrops() {
        val r = home()
        r.day(at(900.0, 0, kmh = 20.0))
        assertEquals(none, r.day(at(190.0, 60000, kmh = 15.0)))
        assertEquals(none, r.day(at(20.0, 70000, kmh = 8.0))) // ≥5 km/s: pencere yok
        val out = (0..125).map { s -> r.day(at(20.0, 80000 + s * 1000L, kmh = if (s == 0) 4.0 else 0.0)) }
        assertEquals(30, out.indexOf(end)) // yavaş ilk fix'ten 30 sn sonra
    }

    @Test fun anOpenOrderNeverEnds() {
        assertFalse(ride(home(), busy = true, dwellS = 100).contains(end))
    }

    @Test fun leavingTheZoneCancelsTheWindowAndReturningCountsFromScratch() {
        val r = home()
        r.day(at(900.0, 0, kmh = 20.0))
        for (s in 0..20) assertEquals(none, r.day(at(20.0, 10000 + s * 1000L))) // 20 sn: pencere açık
        assertEquals(none, r.day(at(400.0, 90000, kmh = 20.0))) // gerçekçi çıkış (380 m / 60 sn)
        val out = (0..40).map { s -> r.day(at(20.0, 150000 + s * 1000L)) }
        assertEquals(30, out.indexOf(end)) // dönüşün ilk fix'inden 30 sn sonra
    }

    /** Fark (CORE.md 1.9): 2 sn'de 380 m'lik çıkış fix'i sıçramadır, atılır; pencere sürer ve dönüşteki ilk fix'te biter. */
    @Test fun aJumpingExitFixDoesNotCancelTheWindow() {
        val r = home()
        r.day(at(900.0, 0, kmh = 20.0))
        r.day(at(300.0, 60000, kmh = 20.0))
        for (s in 0..28) assertEquals(none, r.day(at(20.0, 120000 + s * 1000L))) // pencere 120 sn'den 148 sn'ye
        assertEquals(none, r.day(at(400.0, 150000, kmh = 20.0))) // 380 m / 2 sn: atılır
        assertEquals(end, r.day(at(20.0, 151000))) // pencere 31 sn doldu
    }

    @Test fun aNewShiftDoesNotCarryTheOldShiftsArming() {
        val r = home()
        r.day(at(900.0, 0, kmh = 20.0))
        assertTrue(r.endArmed)
        r.onManualStart()
        assertFalse(r.endArmed)
        for (s in 0 until 200) assertEquals(none, r.day(at(20.0, 10000 + s * 1000L)))
    }

    // ------------------------------------------------------------------ kalıcı durum (uyarlanır)
    private class Memory(var value: HomeRepository.Saved = HomeRepository.Saved()) : HomeRepository.Storage {
        override fun read() = value
        override fun write(value: HomeRepository.Saved) { this.value = value }
    }

    /** YolGo `day.home.v1` koordinatsızdı; WEEM'de kalıcı durum `home.startBlocked` / `home.endArmed` iki bool'dur ve ev değişince düşer. */
    @Test fun persistedStateIsTwoBooleansIsRestoredAndDroppedWhenTheHomeChanges() {
        val m = Memory()
        val repo = HomeRepository(m)
        repo.set(homeLat, homeLon, 10)
        repo.observe(at(900.0, 0, kmh = 20.0), true) // gün içinde dışarıda: kurulur
        repo.changeGuard { it.onManualStop() }       // elle bitirildi
        assertTrue(m.value.endArmed); assertTrue(m.value.startBlocked)
        // Yalnız iki bool; koordinatlar evin kendisi (lat/lon), koruyucu alanlarda koordinat yok.
        val back = HomeRepository(m).engine()!!
        assertTrue(back.startBlocked); assertTrue(back.endArmed)
        // Geri yüklenen "evde görüldü" mantığı: bölge içinde bir fix engeli kaldırır.
        assertEquals(none, HomeRepository(m).observe(at(20.0, 5000), false))
        assertFalse(m.value.startBlocked)
        // Ev değişince koruyucular düşer.
        val moved = HomeRepository(m)
        moved.set(homeLat + 0.001, homeLon, 20)
        assertFalse(m.value.startBlocked); assertFalse(m.value.endArmed)
    }
}
