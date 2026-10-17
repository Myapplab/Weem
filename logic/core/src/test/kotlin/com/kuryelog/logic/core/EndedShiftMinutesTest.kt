package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Regresyon: rozet her 500 ms'de günün olay dosyasını baştan okuyordu. */
class EndedShiftMinutesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun unchangedFileIsNotReadAgain() {
        val f = temp.newFile("events.jsonl")
        f.writeText("""{"type":"shift_start"}""" + "\n" + """{"type":"shift_end","min":42.5}""" + "\n" + "bozuk satır\n")
        val c = EndedShiftMinutes()
        repeat(120) { assertEquals(42.5, c.of(f), 1e-9) } // 1 dakikalık rozet yenilemesi
        assertEquals(1, c.reads)
    }

    @Test fun appendedShiftIsCounted() {
        val f = temp.newFile("events.jsonl")
        f.writeText("""{"type":"shift_end","min":10}""" + "\n")
        val c = EndedShiftMinutes()
        assertEquals(10.0, c.of(f), 1e-9)
        f.appendText("""{"type":"shift_end","min":20.25}""" + "\n")
        assertEquals(30.25, c.of(f), 1e-9)
        assertEquals(2, c.reads)
    }

    @Test fun unfinishedLastLineCountsOnceComplete() {
        // events.jsonl grows while a shift runs; a line being written is summed only when its newline arrives, and only once.
        val f = temp.newFile("events.jsonl")
        f.writeText("""{"type":"shift_end","min":5}""" + "\n" + """{"type":"shift_end","mi""")
        val c = EndedShiftMinutes()
        assertEquals(5.0, c.of(f), 1e-9)
        assertEquals(5.0, c.of(f), 1e-9)
        assertEquals(1, c.reads) // the unfinished line is not read again while the file stays the same
        f.appendText("""n":7}""" + "\n" + """{"type":"click","text":"Accept"}""" + "\n")
        assertEquals(12.0, c.of(f), 1e-9)
        assertEquals(12.0, c.of(f), 1e-9)
        f.appendText("""{"type":"shift_end","min":0.5}""" + "\n")
        assertEquals(12.5, c.of(f), 1e-9)
    }

    @Test fun shorterOrRewrittenFileIsReadFromTheStart() {
        val f = temp.newFile("events.jsonl")
        f.writeText("""{"type":"shift_end","min":10}""" + "\n" + """{"type":"shift_end","min":20}""" + "\n")
        val c = EndedShiftMinutes()
        assertEquals(30.0, c.of(f), 1e-9)
        f.writeText("""{"type":"shift_end","min":4}""" + "\n") // the day deleted and written again (import, delete)
        assertEquals(4.0, c.of(f), 1e-9)
        val other = temp.newFile("other.jsonl"); other.writeText("""{"type":"shift_end","min":1}""" + "\n")
        assertEquals(1.0, c.of(other), 1e-9) // the next day's file
    }

    @Test fun missingFileAndClearAreSafe() {
        val c = EndedShiftMinutes()
        assertEquals(0.0, c.of(temp.root.resolve("yok.jsonl")), 0.0)
        val f = temp.newFile("events.jsonl"); f.writeText("""{"type":"shift_end","min":5}""" + "\n")
        assertEquals(5.0, c.of(f), 0.0)
        c.clear()
        assertEquals(5.0, c.of(f), 0.0)
        assertEquals(2, c.reads)
    }
}
