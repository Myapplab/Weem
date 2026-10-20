package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.MemoryRoadFiles
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.ui.Toasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** WEEM eki (MAP.md 9.4.1, 9.11): başlangıç 80 ve yuvarlama, ±10 sınırları 10/100, 6 sn otomatik kayıt, vazgeç. */
class QualityPromptTest {
    private val clock = FakeClock()
    private val book = RoadBook(MemoryRoadFiles(), StreetPrefsStore(MemoryMapPrefs()), Toasts(), { MapSettings() }, clock)
    private val prompt = QualityPrompt(book, clock).also { p -> p.onSaved = { saved++ } }
    private var saved = 0

    @Test
    fun `başlangıç puansız 80, elle puan yuvarlanır`() {
        prompt.open(1, "A")
        assertEquals(80, prompt.current!!.pct)
        prompt.cancel()
        book.rate(1, 34)
        prompt.open(1, "A")
        assertEquals(30, prompt.current!!.pct)
        prompt.cancel()
        prompt.open(1, "A", pct = 5)
        assertEquals(10, prompt.current!!.pct)
    }

    @Test
    fun `artır azalt 10 ile 100 arasında kalır`() {
        prompt.open(1, "A", pct = 90)
        prompt.onVolume(up = true)
        prompt.onVolume(up = true)
        assertEquals(100, prompt.current!!.pct)
        prompt.set(20)
        prompt.onVolume(up = false)
        prompt.onVolume(up = false)
        assertEquals(10, prompt.current!!.pct)
    }

    @Test
    fun `6 sn dokunulmazsa kaydedilir, her dokunuş sayacı yeniden kurar`() {
        prompt.open(7, "Yol", pct = 60)
        clock.advance(5_000)
        prompt.onVolume(up = true)
        clock.advance(5_000)
        prompt.due()
        assertEquals(70, prompt.current!!.pct)
        clock.advance(1_000)
        prompt.due()
        assertNull(prompt.current)
        assertEquals(70, book.state.k.ratings[7L])
        assertEquals("Yol", book.state.k.names[7L])
        assertEquals(1, saved)
    }

    @Test
    fun `vazgeç kaydetmez`() {
        prompt.open(7, "Yol", pct = 60)
        prompt.cancel()
        clock.advance(10_000)
        prompt.due()
        assertNull(book.state.k.ratings[7L])
        assertEquals(0, saved)
    }
}
