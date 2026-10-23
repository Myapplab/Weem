package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.Lang
import org.junit.Assert.*
import org.junit.Test

/**
 * A5.4 (WEEM eki; NB arayüzünün testi yoktu): Scooter sekmesi ve yüzen panelin saf kuralları NB `DashboardActivity`, `DashboardView`,
 * `FloatingPanel`, `MainActivity` ile aynı; metinler iki dilde (G3, F-S4).
 */
class ScooterUiTest {
    private fun frame(ext: Double? = null, mono: Long = 10_000L, battery: Double? = 64.0) =
        NinebotDashboard.Snapshot(18.6, -783.0, 50.2, -15.6, battery, 21.4, 3.2, 600.0, 31.0, 24.0, ext, 1234.5, mono)

    private fun stats(label: String = "", wh: Double? = 12.44, pct: Double? = 3.0, avg: Double? = 15.25, km: Double = 2.0, moving: Long = 60_000) =
        TripMeter.Stats(label, 0L, wh, 0.0, 0L, 0L, pct, avg, km, moving, -783.0)

    private val idle = ScooterPanel.State(false, false, false, false, "", false, null, "")

    // ---- Metinler ----

    @Test fun everyTextHasBothLanguagesAndTheSamePlaceholders() {
        val spec = Regex("%[sd]")
        for (t in ScooterUiText.entries) {
            assertTrue(t.name, t.tr.isNotBlank() && t.az.isNotBlank())
            assertEquals(t.name, spec.findAll(t.tr).map { it.value }.toList(), spec.findAll(t.az).map { it.value }.toList())
        }
    }

    @Test fun onlyNamesAndSharedWordsAreIdenticalInBothLanguages() {
        val same = setOf(
            "TITLE", "BACK", "LATER", "DOCK_SETTINGS", "ENERGY", "STATUS_VERSION", "PROTOCOL", "SECTION_PANEL", "SECTION_SYSTEM",
            "SECTION_LINK", "SIZE_BALL", "ORDER_DOWN", "INFO_FULL_T",
        )
        assertEquals(emptySet<String>(), ScooterUiText.entries.filter { it.tr == it.az }.map { it.name }.toSet() - same)
    }

    @Test fun screenTextSaysWeemNotRawstats() {
        for (t in ScooterUiText.entries) assertFalse(t.name, "Rawstats" in t.tr || "Rawstats" in t.az)
        // Kayıt içi adlar değişmez (LogExport manifest ve dosya adı).
        assertEquals("NinebotLogger-7.zip", LogExport.fileName(7))
    }

    @Test fun infoSheetHasTheTenSectionsInOrder() {
        assertEquals(10, ScooterUiText.INFO_SECTIONS.size)
        assertEquals("Eşleştirme", ScooterUiText.INFO_SECTIONS.first().first.tr)
        assertEquals("WEEM vardiyası", ScooterUiText.INFO_SECTIONS[7].first.tr)
    }

    @Test fun panelFieldsKeepTurkishAndGainAzerbaijani() {
        for (f in PanelField.entries) {
            assertEquals(f.label, f.title(Lang.TR))
            assertEquals(f.caption(), f.caption(Lang.TR))
            assertTrue(f.name, f.title(Lang.AZ).isNotBlank() && f.caption(Lang.AZ).isNotBlank())
        }
        assertEquals("Orta sürət", PanelField.AVG_SPEED.title(Lang.AZ))
        assertEquals("Wh • gediş", PanelField.TRIP_WH.caption(Lang.AZ))
        assertEquals("W", PanelField.POWER.caption(Lang.AZ))
        assertEquals("dəq:san", PanelField.RIDE_TIME.unit(Lang.AZ))
    }

    // ---- Batarya kutusu (S38: ikinci batarya kutuda da) ----

    @Test fun batteryTileShowsTheSecondPack() {
        assertEquals("– km menzil", ScooterPanel.batterySub(null, null, Lang.TR))
        assertEquals("23.4 km menzil", ScooterPanel.batterySub(23.44, null, Lang.TR))
        assertEquals("2. batarya %100", ScooterPanel.batterySub(null, 99.6, Lang.TR))
        assertEquals("2. batarya %96 · 23.4 km", ScooterPanel.batterySub(23.44, 96.0, Lang.TR))
        assertEquals("2-ci batareya %96", ScooterPanel.batterySub(null, 96.0, Lang.AZ))
    }

    // ---- Durum satırı, nokta, eylem çubuğu ----

    @Test fun statusLineFollowsTheDashboard() {
        assertEquals("MAX G3 • hazır", ScooterPanel.status(idle, Lang.TR))
        val live = idle.copy(active = true)
        assertEquals("Canlı panel • kayıt yok • scooter verisi bekleniyor", ScooterPanel.status(live, Lang.TR))
        assertEquals("Canlı panel • kayıt yok • scooter bağlı • ikinci batarya bulunmadı", ScooterPanel.status(live.copy(linked = true), Lang.TR))
        assertEquals("Canlı panel • WEEM vardiyası kaydediyor • scooter bağlı • ikinci batarya bekleniyor",
            ScooterPanel.status(live.copy(linked = true, hostLogging = true, externalBoards = "06"), Lang.TR))
        assertEquals("Kayıt açık • scooter bağlı • ikinci batarya görünür (%88)",
            ScooterPanel.status(live.copy(linked = true, recording = true, extBatteryPct = 87.6), Lang.TR))
        assertEquals("Kayıt durdu: x", ScooterPanel.status(live.copy(warning = "Kayıt durdu: x"), Lang.TR))
        assertEquals("Canlı panel • qeyd yoxdur • skuter qoşulub • ikinci batareya tapılmadı", ScooterPanel.status(live.copy(linked = true), Lang.AZ))
    }

    @Test fun dotColourOrder() {
        assertEquals(ScooterPanel.Dot.IDLE, ScooterPanel.dot(idle))
        assertEquals(ScooterPanel.Dot.ACTIVE, ScooterPanel.dot(idle.copy(active = true)))
        assertEquals(ScooterPanel.Dot.LINKED, ScooterPanel.dot(idle.copy(active = true, linked = true)))
        assertEquals(ScooterPanel.Dot.RECORDING, ScooterPanel.dot(idle.copy(active = true, linked = true, recording = true)))
        assertEquals(ScooterPanel.Dot.WARN, ScooterPanel.dot(idle.copy(active = true, recording = true, warning = "w")))
    }

    @Test fun dockEnablesWhatTheStateAllows() {
        assertEquals(ScooterPanel.Dock(pair = true, stop = false, scan = true, export = true), ScooterPanel.dock(idle, false, false))
        assertFalse(ScooterPanel.dock(idle, false, scanning = true).scan)
        // Kayıtsız canlı panel (eşleştirme sonrası): eşleştir ve ara kapalı, kesilebilir, dışa aktarma açık.
        assertEquals(ScooterPanel.Dock(pair = false, stop = true, scan = false, export = true), ScooterPanel.dock(idle.copy(active = true), false, false))
        // S37: vardiya kaydı vardiyayla biter; durdur kapalı, dışa aktarma kapalı.
        assertEquals(ScooterPanel.Dock(pair = false, stop = false, scan = false, export = false),
            ScooterPanel.dock(idle.copy(active = true, recording = true), false, false, shiftOn = true))
        // Temizlik ya da dışa aktarma sürerken hiçbir şey başlamaz.
        assertEquals(ScooterPanel.Dock(pair = false, stop = false, scan = false, export = false), ScooterPanel.dock(idle.copy(maintenance = true), false, false))
        assertFalse(ScooterPanel.dock(idle, exporting = true, scanning = false).pair)
        assertTrue(ScooterPanel.keepScreenOn(true, true))
        assertFalse(ScooterPanel.keepScreenOn(false, true) || ScooterPanel.keepScreenOn(true, false))
    }

    @Test fun badgeReplacesTheStartButtonAndFollowsShiftAndPairing() {
        fun b(s: ScooterPanel.State = idle, selected: Boolean = true, paired: Boolean = true, shift: Boolean = false, provider: Boolean = false) =
            ScooterPanel.badge(s, selected, paired, shift, provider)
        assertEquals(ScooterPanel.Badge.READY, b())
        assertEquals("Eşleşildi · kayda hazır", b().text.get(Lang.TR))
        assertEquals(ScooterPanel.Tone.POSITIVE, b().tone)
        // Eşleştirme sonrası canlı panel kayıt değildir.
        assertEquals(ScooterPanel.Badge.READY, b(idle.copy(active = true, linked = true)))
        assertEquals(ScooterPanel.Badge.PAIRING, b(idle.copy(active = true), paired = false))
        assertEquals(ScooterPanel.Badge.NOT_PAIRED, b(paired = false))
        assertEquals(ScooterPanel.Badge.NO_SCOOTER, b(selected = false, paired = false))
        assertEquals(ScooterPanel.Badge.NOT_CONNECTED, b(shift = true))
        assertEquals(ScooterPanel.Badge.WAITING, b(idle.copy(active = true, recording = true), shift = true))
        assertEquals(ScooterPanel.Badge.RECORDING, b(idle.copy(active = true, recording = true, linked = true), shift = true))
        assertEquals("Qeyd açıqdır · növbə ilə bitir", ScooterPanel.Badge.RECORDING.text.get(Lang.AZ))
        assertEquals(ScooterPanel.Badge.PROVIDER, b(shift = true, provider = true))
    }

    // ---- Gösterge ----

    @Test fun staleAfterFiveSeconds() {
        assertTrue(ScooterPanel.live(frame(mono = 10_000), 15_000))
        assertFalse(ScooterPanel.live(frame(mono = 10_000), 15_001))
        assertFalse(ScooterPanel.live(null, 0))
    }

    @Test fun tripLineAndScope() {
        assertNull(ScooterPanel.tripLine(null, Lang.TR))
        assertNull(ScooterPanel.tripLine(stats(wh = null, avg = null), Lang.TR))
        assertEquals("BU SÜRÜŞ  12.4 Wh  •  %3  •  ort. 15.3 km/h", ScooterPanel.tripLine(stats(avg = 15.26), Lang.TR))
        assertEquals("BU SİPARİŞ  – Wh  •  ort. 15.3 km/h", ScooterPanel.tripLine(stats(label = "T1", wh = null, pct = null, avg = 15.26), Lang.TR))
        assertEquals("BU SİFARİŞ  12.4 Wh  •  %3  •  orta – km/h", ScooterPanel.tripLine(stats(label = "T1", avg = null), Lang.AZ))
        assertEquals("BU SÜRÜŞ  •  Dokun: tam ekran", ScooterPanel.hint(null, false, Lang.TR))
        assertEquals("BU SİPARİŞ  •  Dokun: menüyü göster", ScooterPanel.hint(stats(label = "T1"), true, Lang.TR))
    }

    @Test fun dialFractionsAndTones() {
        assertNull(ScooterPanel.frac(null, 0.0, 1.0))
        assertEquals(0.0, ScooterPanel.frac(-5.0, 0.0, 30.0)!!, 0.0)
        assertEquals(1.0, ScooterPanel.frac(55.0, 30.0, 55.0)!!, 0.0)
        assertEquals(0.5, ScooterPanel.frac(15.0, 0.0, 30.0)!!, 1e-9)
        assertEquals(ScooterPanel.Tone.DANGER, ScooterPanel.batteryTone(14.9))
        assertEquals(ScooterPanel.Tone.WARN, ScooterPanel.batteryTone(15.0))
        assertEquals(ScooterPanel.Tone.POSITIVE, ScooterPanel.batteryTone(40.0))
        assertEquals(ScooterPanel.Tone.POSITIVE, ScooterPanel.batteryTone(null))
        assertEquals(ScooterPanel.Tone.DANGER, ScooterPanel.tempTone(55.0))
        assertEquals(ScooterPanel.Tone.ACCENT, ScooterPanel.tempTone(54.9))
    }

    @Test fun accessibilityTextUsesDashesForMissingValues() {
        assertEquals("Hız – km/h. Güç – W (V×A). Batarya – yüzde. Menzil – km. Voltaj – V. Akım – A. Sıcaklık – derece.", ScooterPanel.accessibility(null, Lang.TR))
        assertTrue(ScooterPanel.accessibility(frame(), Lang.TR).startsWith("Hız 18.6 km/h. Güç -783 W"))
    }

    // ---- Tarama ----

    @Test fun scanListRefreshesOnlyOnNewsAndKeepsRowsInPlace() {
        val l = ScooterPanel.ScanList()
        assertTrue(l.seen("AA", null, -70))
        assertFalse(l.seen("AA", null, -74))
        assertTrue(l.seen("AA", null, -80))
        assertTrue(l.seen("BB", "MAX G3", -60))
        assertFalse(l.seen("BB", "Other", -61))
        assertEquals("MAX G3", l["BB"]!!.name)
        assertTrue(l.seen("AA", "late name", -80))
        l.seen("CC", null, -50)
        l.seen("DD", "Saved", -90)
        assertEquals(listOf("DD", "AA", "BB", "CC"), l.ordered("DD").map { it.address })
        assertEquals(listOf("AA", "BB", "DD", "CC"), l.ordered("").map { it.address })
        val cc = l["CC"]!!
        assertEquals("İsimsiz BLE cihazı • CC • -50 dBm", ScooterPanel.ScanList.label(cc, Lang.TR))
        assertEquals("Adsız BLE cihazı • CC • -50 dBm", ScooterPanel.ScanList.label(cc, Lang.AZ))
        assertEquals("İsimsiz BLE cihazı • CC • -50 dBm", ScooterPanel.ScanList.storedLabel(cc))
        assertEquals("Kayıtlı scooter • DD • -90 dBm", ScooterPanel.ScanList.subtitle(l["DD"]!!, true, Lang.TR))
        assertEquals("CC", ScooterPanel.ScanList.shortName(cc))
        assertEquals("Saved", ScooterPanel.ScanList.shortName(l["DD"]!!))
        l.clear()
        assertTrue(l.isEmpty())
    }

    // ---- Dışa aktarma ----

    @Test fun exportChoicesMatchTheOldDialog() {
        val c = ScooterPanel.ExportChoice.entries
        assertEquals(listOf(false, true, true, true), c.map { it.location })
        assertEquals(listOf(true, true, false, true), c.map { it.latest })
        assertEquals(listOf(false, false, false, true), c.map { it.full })
        assertNull(ScooterPanel.ExportChoice.LATEST.warning())
        assertEquals(ScooterUiText.GPS_TEXT, ScooterPanel.ExportChoice.ALL_GPS.warning()!!.second)
        assertEquals(ScooterUiText.FULL_TITLE, ScooterPanel.ExportChoice.FULL.warning()!!.first)
        assertTrue(ScooterPanel.ExportChoice.FULL.options().includeLocation)
        assertFalse(ScooterPanel.ExportChoice.ALL_GPS.options().latestOnly)
        assertEquals(ScooterUiText.EXPORT_DONE, ScooterPanel.ExportChoice.LATEST.done())
        assertEquals(ScooterUiText.EXPORT_DONE_GPS, ScooterPanel.ExportChoice.LATEST_GPS.done())
        assertEquals(ScooterUiText.EXPORT_DONE_FULL, ScooterPanel.ExportChoice.FULL.done())
    }

    // ---- Yüzen panel ----

    @Test fun ballShowsOnlyWithNoOwnScreenAndEverythingAllowed() {
        assertTrue(FloatRules.show(0, active = true, enabled = true, permitted = true))
        assertFalse(FloatRules.show(1, true, true, true))
        assertFalse(FloatRules.show(0, false, true, true))
        assertFalse(FloatRules.show(0, true, false, true))
        assertFalse(FloatRules.show(0, true, true, false))
        assertTrue(FloatRules.pip(enabled = true, permitted = false, supported = true))
        assertFalse(FloatRules.pip(true, true, true))
        assertFalse(FloatRules.pip(false, false, true))
        assertFalse(FloatRules.pip(true, false, false))
        val s = FloatRules.Screens()
        s.started(); s.started()
        assertEquals(1, s.stopped())
        assertEquals(0, s.stopped())
        assertEquals(0, s.stopped())
    }

    @Test fun redrawAtMostEvery80ms() {
        assertEquals(80L, FloatRules.MIN_FRAME_MS)
        assertEquals(30L, FloatRules.redrawWait(1_050, 1_000))
        assertTrue(FloatRules.redrawWait(1_080, 1_000) <= 0)
        assertEquals(400L, FloatRules.SHOW_DELAY_MS)
    }

    @Test fun positionDefaultsSettleAndStayOnScreen() {
        // 1080 × 2400, top 300 px, kenar 36 px.
        assertEquals(1080 - 300 - 36, FloatRules.initialX(null, 1080, 300, 36))
        assertEquals(800, FloatRules.initialY(null, 2400, 300, 36))
        assertEquals(1080 - 300 - 36, FloatRules.initialX(5000, 1080, 300, 36))
        assertEquals(2400 - 300 - 36, FloatRules.initialY(5000, 2400, 300, 36))
        assertEquals(40, FloatRules.initialX(40, 1080, 300, 36))
        assertEquals(36, FloatRules.settleX(200, 300, 1080, 36))
        assertEquals(1080 - 300 - 36, FloatRules.settleX(400, 300, 1080, 36))
        assertEquals(36, FloatRules.settleY(0, 300, 2400, 36))
        assertEquals(2400 - 300 - 36, FloatRules.settleY(2300, 300, 2400, 36))
        assertEquals(0, FloatRules.dragTo(10, -50f, 300, 1080))
        assertEquals(780, FloatRules.dragTo(700, 500f, 300, 1080))
    }

    @Test fun closeTargetAndArrows() {
        assertTrue(FloatRules.nearClose(540, 2000, 540, 2070, 64))
        assertFalse(FloatRules.nearClose(540, 2000, 540, 2071, 64))
        // Sağ yarıda, üstte: yatay ok solda (180°), dikey ok altta (90°).
        val a = FloatRules.arrows(744, 800, 300, 100, 6, 1080, 2400)
        assertEquals(FloatGeometry.Direction.LEFT, a.side)
        assertEquals(744 - 6 - 100, a.sideX)
        assertEquals(800 + 100, a.sideY)
        assertEquals(180f, a.sideAngle)
        assertEquals(FloatGeometry.Direction.DOWN, a.cross)
        assertEquals(800 + 300 + 6, a.crossY)
        // Sol yarıda, alt üçte birde: yatay ok sağda, dikey ok üstte; ekrandan taşmaz.
        val b = FloatRules.arrows(36, 2100, 300, 100, 6, 1080, 2400)
        assertEquals(FloatGeometry.Direction.RIGHT, b.side)
        assertEquals(36 + 300 + 6, b.sideX)
        assertEquals(FloatGeometry.Direction.UP, b.cross)
        assertEquals(270f, b.crossAngle)
        assertEquals(2100 - 6 - 100, b.crossY)
        val edge = FloatRules.arrows(-50, 2400, 300, 100, 6, 1080, 2400)
        assertTrue(edge.crossY <= 2300 && edge.crossX >= 0)
    }

    @Test fun ballFieldFallsBackToTheFirstValue() {
        assertEquals(PanelField.BATTERY, FloatRules.ballField("battery", PanelField.DEFAULT))
        assertEquals(PanelField.POWER, FloatRules.ballField("", PanelField.DEFAULT))
        assertEquals(PanelField.POWER, FloatRules.ballField("nope", PanelField.DEFAULT))
    }

    // ---- Ayarlar ----

    @Test fun sizeSlidersUseFloatGeometryLimits() {
        val s = ScooterSettings.SIZES
        assertEquals(listOf("float_ball_dp", "float_thick_pct", "float_h_pct", "float_v_pct", "float_text_port_pct", "float_text_land_pct"), s.map { it.key })
        assertEquals(listOf(100, 125, 80, 60, 100, 100), s.map { it.default })
        assertEquals(64, s[0].clamp(10))
        assertEquals(200, s[1].clamp(999))
        assertEquals("Top: 100 dp", s[0].label(100, Lang.TR))
        assertEquals("Bar kalınlığı (topa göre): %125", s[1].label(125, Lang.TR))
        assertEquals("Top 100 dp • kalınlık %125 • yatay %80 • dikey %60 • yazı dik %100 / yatay %100", ScooterSettings.sizeLabel(s.map { it.default }, Lang.TR))
    }

    @Test fun settingSubtitles() {
        assertEquals("Güç, Bu sürüş enerji, Batarya, Ortalama hız", ScooterSettings.fieldsLabel(PanelField.DEFAULT, Lang.TR))
        assertEquals("Bir kerelik, salt-okunur arama", ScooterSettings.probeLabel("", Lang.TR))
        assertEquals("Bulunan kart: 06 • her kayıtta okunur", ScooterSettings.probeLabel("06", Lang.TR))
        assertEquals(ScooterUiText.FLOATING_NEEDS.tr, ScooterSettings.floatingLabel(false, Lang.TR))
        assertEquals("MAX G3", ScooterSettings.selectedTitle("MAX G3 • AA:BB • -60 dBm", Lang.TR))
        assertEquals(ScooterUiText.NOT_SELECTED.tr, ScooterSettings.selectedTitle("", Lang.TR))
        assertEquals(ScooterUiText.KEY_NONE, ScooterSettings.keyState(false, false, false))
        assertEquals(ScooterUiText.KEY_PENDING, ScooterSettings.keyState(false, true, false))
        assertEquals(ScooterUiText.KEY_CONFIRMED, ScooterSettings.keyState(true, true, true))
        assertEquals(ScooterUiText.KEY_UNCONFIRMED, ScooterSettings.keyState(true, false, false))
    }

    @Test fun keyMustBeExactlySixteenHexBytes() {
        assertArrayEquals(ByteArray(16) { it.toByte() }, ScooterSettings.parseKey("00010203 04050607\n08090a0b0C0D0E0F"))
        assertNull(ScooterSettings.parseKey("0001"))
        assertNull(ScooterSettings.parseKey("zz".repeat(16)))
        assertNull(ScooterSettings.parseKey("0".repeat(31)))
        assertNull(ScooterSettings.parseKey("00".repeat(17)))
    }

    @Test fun fieldPickerCapsAtFourAndFallsBackToDefault() {
        val cur = PanelField.DEFAULT.toMutableList()
        assertFalse(ScooterSettings.toggle(cur, PanelField.SPEED, true))
        assertTrue(ScooterSettings.toggle(cur, PanelField.POWER, false))
        assertTrue(ScooterSettings.toggle(cur, PanelField.SPEED, true))
        assertEquals(listOf(PanelField.TRIP_WH, PanelField.BATTERY, PanelField.AVG_SPEED, PanelField.SPEED), cur)
        assertEquals("trip_wh,battery,avg_speed,speed", ScooterSettings.fieldsToSave(cur))
        assertEquals("power,trip_wh,battery,avg_speed", ScooterSettings.fieldsToSave(emptyList()))
    }
}
