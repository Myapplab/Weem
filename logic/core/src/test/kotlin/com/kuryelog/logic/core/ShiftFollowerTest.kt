package com.kuryelog.logic.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * YolGo `rawstats_shift_end_test.dart` (11) → saf `ShiftFollower` (CORE.md 1.11, 6.2). Kendi "gün"ünü tutan modül (`:map` gün motoru)
 * vardiya durumunu izler: kapalı vardiya + işlenmemiş `endedAtMs` (gün başından önce değil) → günü o anla bitir; açık vardiya + kapalı
 * gün + işlenmemiş `startedAtMs` → o anla başlat; gün açıkken görülen başlangıç "işlendi" sayılır (elle bitirilen gün aynı vardiyada
 * yeniden açılmaz); her damga bir kez; bozuk/başka şema ya da okuma yok → hiçbir şey. Yedi saf karar + dört denetleyici senaryosu
 * (tercih ve Riverpod yerine `follow` damga saklar).
 */
class ShiftFollowerTest {
    private val t0 = 1_760_000_000_000L
    private val hour = 3_600_000L
    private val minute = 60_000L

    private fun payload(active: Boolean, start: Long? = null, end: Long? = null, reason: String? = null, interrupted: Boolean = false) =
        JSONObject().put("schemaVersion", 1).put("active", active).put("startedAtMs", start ?: JSONObject.NULL)
            .put("endedAtMs", end ?: JSONObject.NULL).put("endReason", reason ?: JSONObject.NULL)
            .put("interrupted", interrupted).put("generatedAtMs", t0).toString()

    private fun state(active: Boolean, start: Long? = null, end: Long? = null, reason: String? = null) =
        ShiftState(active, start, end, reason)

    private fun decide(s: ShiftState?, day: Boolean = false, dayStart: Long? = null, end: Long? = null, start: Long? = null) =
        ShiftFollower.decide(s, dayActive = day, dayStartedAtMs = dayStart, handledEndMs = end, handledStartMs = start)

    // ------------------------------------------------------------------ saf karar
    @Test fun payloadIsReadAndBrokenOrOtherSchemaIsNull() {
        val s = ShiftState.parse(payload(false, t0, t0 + 5 * hour, "manual"))!!
        assertEquals(listOf(false, t0, t0 + 5 * hour, "manual"), listOf(s.active, s.startedAtMs, s.endedAtMs, s.endReason))
        assertNull(ShiftState.parse(null))
        assertNull(ShiftState.parse("not json"))
        assertNull(ShiftState.parse(JSONObject().put("schemaVersion", 2).put("active", true).toString()))
        assertNull(ShiftState.parse(JSONObject().put("schemaVersion", 1).toString()))
    }

    @Test fun openDayAndAShiftEndedAfterTheDayStartedEndTheDayAtTheShiftEndTime() {
        val end = t0 + 6 * hour
        val r = decide(state(false, t0, end), day = true, dayStart = t0 + 5 * minute)
        assertEquals(ShiftFollower.Action.END_DAY, r.action)
        assertEquals(end, r.atMs)
    }

    @Test fun aShiftThatEndedBeforeTheDayStartedIsIgnored() {
        val r = decide(state(false, t0, t0 + hour), day = true, dayStart = t0 + 2 * hour)
        assertEquals(ShiftFollower.Action.NONE, r.action)
    }

    @Test fun theSameEndIsHandledOnce() {
        val end = t0 + 6 * hour
        val r = decide(state(false, t0, end), day = true, dayStart = t0, end = end)
        assertEquals(ShiftFollower.Action.NONE, r.action)
    }

    @Test fun noReadingMeansNothing() {
        assertEquals(ShiftFollower.Action.NONE, decide(null, day = true, dayStart = t0).action)
        assertEquals(ShiftFollower.Action.NONE, decide(null).action)
    }

    @Test fun closedDayAndOpenShiftStartTheDayAtTheShiftStartOnce() {
        val s = state(true, t0)
        val r = decide(s)
        assertEquals(ShiftFollower.Action.START_DAY, r.action)
        assertEquals(t0, r.atMs)
        // Elle bitirilen gün, aynı vardiya sürerken yeniden açılmaz (ters kanal yok).
        assertEquals(ShiftFollower.Action.NONE, decide(s, start = t0).action)
    }

    @Test fun aStartSeenWhileTheDayIsOpenCountsAsHandled() {
        val r = decide(state(true, t0), day = true, dayStart = t0 - 3 * minute)
        assertEquals(ShiftFollower.Action.NONE, r.action)
        assertEquals(t0, r.handledStartMs)
    }

    // ------------------------------------------------------------------ denetleyici senaryoları (damgalar saklanır)
    @Test fun whenTheShiftEndsTheDayEndsAtTheSameTimeWithTheShiftEndTimeAndTheStampIsStored() {
        val f = ShiftFollower()
        val dayStart = t0 + minute // gün elle başladı
        val end = t0 + 2 * minute
        val d = f.follow(state(false, t0 - hour, end, "manual"), dayActive = true, dayStartedAtMs = dayStart)
        assertEquals(ShiftFollower.Action.END_DAY, d.action)
        assertEquals(end, d.atMs)
        assertEquals(end, d.handledEndMs)
        // Aynı bitiş tekrar okunsa da gün yeniden açılmaz/kapanmaz.
        assertEquals(ShiftFollower.Action.NONE, f.follow(state(false, null, end), dayActive = false, dayStartedAtMs = null).action)
        assertEquals(ShiftFollower.Action.NONE, f.follow(state(false, null, end), dayActive = true, dayStartedAtMs = dayStart).action)
    }

    @Test fun onAColdStartAnOpenShiftOpensTheDayAtItsStart() {
        val start = t0 - 20 * minute
        val d = ShiftFollower().follow(state(true, start), dayActive = false, dayStartedAtMs = null)
        assertEquals(ShiftFollower.Action.START_DAY, d.action)
        assertEquals(start, d.atMs)
    }

    @Test fun aDayEndedByHandIsNotReopenedWhileTheSameShiftRuns() {
        val f = ShiftFollower()
        val start = t0 - 20 * minute
        assertEquals(ShiftFollower.Action.START_DAY, f.follow(state(true, start), dayActive = false, dayStartedAtMs = null).action)
        // Gün açıldı, kullanıcı elle bitirdi; aynı vardiya hâlâ açık.
        assertEquals(ShiftFollower.Action.NONE, f.follow(state(true, start), dayActive = false, dayStartedAtMs = null).action)
        // Yeni vardiya (başka başlangıç damgası) günü yine açar.
        assertEquals(ShiftFollower.Action.START_DAY, f.follow(state(true, t0), dayActive = false, dayStartedAtMs = null).action)
    }

    @Test fun withNoReadingTheDayStaysAsItIs() {
        val f = ShiftFollower()
        assertEquals(ShiftFollower.Action.NONE, f.follow(null, dayActive = true, dayStartedAtMs = t0).action)
        assertEquals(ShiftFollower.Action.NONE, f.follow(null, dayActive = false, dayStartedAtMs = null).action)
    }
}
