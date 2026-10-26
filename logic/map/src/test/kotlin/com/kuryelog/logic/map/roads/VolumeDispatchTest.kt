package com.kuryelog.logic.map.roads

import com.kuryelog.logic.map.FakeClock
import com.kuryelog.logic.map.MemoryMapPrefs
import com.kuryelog.logic.map.MemoryRoadFiles
import com.kuryelog.logic.map.geo.GeoPoint
import com.kuryelog.logic.map.graph.RoadClass
import com.kuryelog.logic.map.nav.MatchInfo
import com.kuryelog.logic.map.nav.WrongWayEvent
import com.kuryelog.logic.map.route.QualityRating
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.route.TestGraphs.lat
import com.kuryelog.logic.map.route.TestGraphs.lon
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.ui.Toasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WEEM eki (MAP.md 9.11): 450 ms çift basış, öncelik sırası, uzun basış, soru/kart açıkken yönlendirme, ana cadde reddi. */
class VolumeDispatchTest {
    private class Rig {
        val clock = FakeClock()
        val prefs = MemoryMapPrefs()
        val streets = StreetPrefsStore(prefs)
        val toasts = Toasts()
        val book = RoadBook(MemoryRoadFiles(), streets, toasts, { MapSettings() }, clock)
        val prompt = QualityPrompt(book, clock)
        var match: MatchInfo? = MatchInfo(0, 100, "Test küçəsi", RoadClass.RESIDENTIAL, true, false, 3.0)
        var here: GeoPoint? = GeoPoint(lat(100.0), lon(0.0))
        var suggestion: WrongWayEvent? = null
        var replans = 0
        val events = ArrayList<String>()
        val env = object : VolumeEnv {
            override fun match() = match
            override fun here() = here
            override fun suggestion() = suggestion
            override fun clearSuggestion() { suggestion = null }
            override fun replanIfRoute() { replans++ }
        }
        val volume = VolumeDispatch(book, clock, prompt, env)

        init {
            book.log = { name, _ -> events += name }
        }

        fun press(up: Boolean, afterMs: Long = 0) {
            clock.advance(afterMs)
            volume.onKey(up)
        }

        fun settle() {
            clock.advance(VolumeDispatch.DOUBLE_MS)
            volume.due()
        }
    }

    @Test
    fun `Ses+ tek onay, 450 ms beklemeden olmaz, Geri al eski hâli döner`() {
        val r = Rig()
        r.press(up = true)
        r.clock.advance(400)
        r.volume.due()
        assertTrue(r.streets.current.approved.isEmpty())
        r.clock.advance(50)
        r.volume.due()
        assertEquals(setOf(100L), r.streets.current.approved)
        assertEquals("Onaylandı: Test küçəsi (iki yön)", r.toasts.current?.text)
        assertEquals(listOf("approve"), r.events)
        r.toasts.current!!.onAction!!()
        assertTrue(r.streets.current.approved.isEmpty())
        assertEquals(listOf("approve", "undo"), r.events)
    }

    @Test
    fun `Ses+ iki kez kalite kartı açar, onay yok`() {
        val r = Rig()
        r.press(up = true)
        r.press(up = true, afterMs = 300)
        r.settle()
        assertEquals(80, r.prompt.current?.pct)
        assertTrue(r.streets.current.approved.isEmpty())
        // Kart açıkken Ses± ±10 (sokak kararı değil).
        r.press(up = false)
        r.press(up = false)
        r.settle()
        assertEquals(60, r.prompt.current?.pct)
        assertTrue(r.streets.current.steep.isEmpty())
    }

    @Test
    fun `450 ms sonra ikinci basış iki ayrı tek basıştır`() {
        val r = Rig()
        r.press(up = true)
        r.press(up = true, afterMs = 500)
        assertEquals(setOf(100L), r.streets.current.approved)
        assertNull(r.prompt.current)
    }

    @Test
    fun `ters yön önerisi Ses+ ile onaylanır, ana caddede onay reddedilir`() {
        val r = Rig()
        r.suggestion = WrongWayEvent(77, "Öneri küçəsi", false)
        r.press(up = true)
        r.settle()
        assertEquals(setOf(77L), r.streets.current.approved)
        assertNull(r.suggestion)

        r.match = MatchInfo(0, 300, "Böyük prospekt", RoadClass.PRIMARY, true, false, 3.0)
        r.press(up = true)
        r.settle()
        assertFalse(300L in r.streets.current.approved)
        assertEquals("Ana caddede ters yön onayı kapalı", r.toasts.current?.text)
    }

    @Test
    fun `eşleşme yokken toast, Ses- iki kez aşırı eğimli`() {
        val r = Rig()
        val m = r.match
        r.match = null
        r.press(up = true)
        r.settle()
        assertEquals("Yol eşleşmedi — biraz daha sür", r.toasts.current?.text)
        r.match = m
        r.press(up = false)
        r.press(up = false, afterMs = 200)
        assertEquals(setOf(100L), r.streets.current.steep)
        assertTrue(r.toasts.current!!.text.startsWith("Aşırı eğimli: Test küçəsi"))
    }

    @Test
    fun `Ses- tek kötü nokta, uzun basış siler, nokta yoksa kötü puan kalkar`() {
        val r = Rig()
        r.press(up = false)
        r.settle()
        val spot = r.book.state.marks.single()
        assertEquals(100L, spot.way)
        assertEquals("Kötü nokta: Test küçəsi · ±25 m (uzun bas: sil)", r.toasts.current?.text)
        assertEquals(1, r.replans)

        r.volume.onDownHold()
        assertTrue(r.book.state.marks.single().removed)
        assertEquals("Kötü nokta silindi: Test küçəsi", r.toasts.current?.text)

        r.volume.onDownHold()
        assertEquals("Burada kötü nokta işareti yok", r.toasts.current?.text)

        r.book.rate(100, QualityRating.BAD)
        r.volume.onDownHold()
        assertNull(r.book.state.k.ratings[100L])
        assertEquals("Kötü yol işareti kaldırıldı: Test küçəsi", r.toasts.current?.text)
    }

    @Test
    fun `uzun basış bekleyen tek Ses- iptal eder, konum yoksa kötü nokta yok`() {
        val r = Rig()
        r.press(up = false)
        r.volume.onDownHold()
        r.settle()
        assertTrue(r.book.state.marks.isEmpty())
        r.here = null
        r.press(up = false)
        r.settle()
        assertTrue(r.book.state.marks.isEmpty())
        assertEquals("Konum yok — biraz daha sür", r.toasts.current?.text)
    }

    @Test
    fun `S42 yol öğrenme kapalıyken yalnız ters yön önerisi beklerken Ses+ yakalanır`() {
        val sugg = WrongWayEvent(200, "Tək yön küçəsi", false)
        assertTrue(VolumeDispatch.captures(volumeKeys = true, mapInFront = true, learning = false, up = true, suggestion = sugg))
        assertFalse("öneri yok: sistem sesi", VolumeDispatch.captures(true, true, false, true, null))
        assertFalse("Ses− öğrenme kapalıyken yakalanmaz", VolumeDispatch.captures(true, true, false, false, sugg))
        assertFalse("ana caddede onay yok", VolumeDispatch.captures(true, true, false, true, sugg.copy(mainRoad = true)))
        assertFalse("ses tuşu ayarı kapalı", VolumeDispatch.captures(false, true, false, true, sugg))
        assertFalse("Harita arkada", VolumeDispatch.captures(true, false, false, true, sugg))
        assertTrue("öğrenme açık: her ses tuşu", VolumeDispatch.captures(true, true, true, false, null))
    }

    @Test
    fun `S42 öneriyi onaylama hemen olur, kalite kartı açılmaz, öneri temizlenir`() {
        val r = Rig()
        r.suggestion = WrongWayEvent(200, "Tək yön küçəsi", false)
        assertTrue(r.volume.approveSuggestion())
        assertEquals(setOf(200L), r.streets.current.approved)
        assertEquals("Onaylandı: Tək yön küçəsi (iki yön)", r.toasts.current?.text)
        assertNull(r.suggestion)
        assertNull(r.prompt.state.value)
        assertFalse("öneri yokken onay yok", r.volume.approveSuggestion())
        assertEquals(setOf(200L), r.streets.current.approved)
    }
}
