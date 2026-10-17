package com.kuryelog.logic.capture

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * CAPTURE.md 7.5 (WEEM, 17 Oct): Android hands every pattern to ICU, whose `\b`, `\s` are Unicode; JDK 19+ (WEEM tests run
 * on JDK 21) reads `\b` as an ASCII edge and `\s` without the no-break space. Rawstats' JDK 17 tests never saw the difference.
 * Every converted pattern gets a Cyrillic or NBSP input here; the expected value is the phone's (ICU) result.
 */
class RegexPortabilityTest {
    private val nbsp = ' '

    @Test fun navigationSummaryReadsCyrillicUnits() {
        assertEquals("3 мин · 1,2 км", NavDistance.summary(listOf("3 мин · 1,2 км")))
        assertEquals("12 мин · 450 м", NavDistance.summary(listOf("Ул. Низами", "12 мин · 450 м")))
        assertEquals("8 min · 2,1 km", NavDistance.summary(listOf("8 min · 2,1 km")))
        // A unit glued to a word is still no duration; a distance alone is the next manoeuvre.
        assertNull(NavDistance.summary(listOf("5 minutos · 2 km")))
        assertNull(NavDistance.summary(listOf("Turn left in 300 m")))
    }

    @Test fun noteFieldWordsEndAtCyrillicEdges() {
        assertTrue(NoteFields.holdsOtherFields("кв 5"))
        assertTrue(NoteFields.holdsOtherFields("kv 5"))
        assertTrue(NoteFields.holdsOtherFields("5 kat"))
        assertFalse(NoteFields.holdsOtherFields("kvadrat"))
        assertFalse(NoteFields.holdsOtherFields("katar"))
    }

    @Test fun noteNumbersEndAtCyrillicLetters() {
        // "5й" is a word on the phone: no floor 5 (JDK 21's ASCII `\b` would end after the digit).
        assertNull(NoteFields.extract("этаж 5й").floor)
        assertEquals("5", NoteFields.extract("этаж 5").floor)
        // A number glued to a Cyrillic word is no floor either ("дом5 этаж").
        assertNull(NoteFields.extract("дом5 этаж").floor)
        assertEquals("5", NoteFields.extract("5 этаж").floor)
        assertEquals("12", NoteFields.extract("квартира 12").apartment)
        assertNull(NoteFields.extract("квартира 12б").apartment)
    }

    @Test fun noBreakSpacesAreSpaces() {
        assertEquals("2.21 azn · 22 min", CourierParser.norm("2.21 AZN ·${nbsp}22${nbsp}min"))
        assertTrue(CourierParser.isAccept("2.21 AZN · 22${nbsp}min"))
        assertTrue(OfferRules.isAccept("Accept${nbsp}order"))
        // A changing distance or countdown behind a no-break space is no new screen.
        val d = ScreenDeduper()
        assertTrue(d.shouldSave("p", listOf("Pickup", "5${nbsp}km", "12:30")))
        assertFalse(d.shouldSave("p", listOf("Pickup", "7${nbsp}km", "12:45")))
        assertFalse(d.shouldSave("p", listOf("Pickup", "7${nbsp}km", "12:45", "15${nbsp}s")))
    }

    @Test fun clockAndMetricEdgesAreUnicode() {
        // "к12:30" is one word on the phone: not a clock, so the screen changed.
        val d = ScreenDeduper()
        assertTrue(d.shouldSave("p", listOf("Готово к12:30")))
        assertTrue(d.shouldSave("p", listOf("Готово к12:45")))
        assertTrue(d.shouldSave("q", listOf("Готово к 12:30")))
        assertFalse(d.shouldSave("q", listOf("Готово к 12:45")))
    }

    /** No `\b` left in the capture parser's code: a new one would bring the JDK/ICU difference back. */
    @Test fun noAsciiWordEdgeInCaptureCode() {
        val dir = File(RepoSource.root, "logic/capture/src/main/kotlin")
        val bad = dir.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> val t = l.trimStart()
                !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) && Regex("""\\\\[bB]""").containsMatchIn(l) }
                .map { "${f.name}:${it.index + 1}" }
        }.toList()
        assertEquals(emptyList<String>(), bad)
    }
}
