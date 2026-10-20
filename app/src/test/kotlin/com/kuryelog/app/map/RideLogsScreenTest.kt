package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.logs.RideLogEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneOffset

/** Sürüş kayıtları ekranı (MAP.md 12.10, 12.13 "Yeni"): gruplar, etkin kayıt silinemez, metinler, anahtar, silme onayları. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h2600dp-port-xxhdpi")
class RideLogsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun entry(name: String, day: String, kind: String, started: Long? = null) = RideLogEntry(File(name), day, kind, 1_572_864, started)

    private val list = listOf(
        entry("2026-10-02_ride_1790900000000.jsonl", "2026-10-02", "ride", 1_790_900_000_000),
        entry("2026-10-02.jsonl", "2026-10-02", "day"),
        entry("2026-10-01.gun.json", "2026-10-01", "report", 1_790_800_000_000),
    )

    private class Rec {
        val deleted = ArrayList<String>()
        val deletedDays = ArrayList<String>()
        var logging: Boolean? = null
        val saves = ArrayList<String>()
    }

    private fun show(rec: Rec, logging: Boolean = true, dayActive: Boolean = true, lang: Lang = Lang.TR, entries: List<RideLogEntry>? = list, error: String? = null) = compose.setContent {
        WeemTheme(false) {
            RideLogsScreen(
                entries, logging, dayActive, lang, ZoneOffset.ofHours(4), active = { it.name == "2026-10-02.jsonl" },
                actions = RideLogActions(
                    onLoggingChange = { rec.logging = it }, onDeleteOne = { rec.deleted += it.name }, onDeleteDay = { rec.deletedDays += it },
                    onSave = { e, d, all -> rec.saves += "${e?.name}|$d|$all" },
                ),
                error = error,
            )
        }
    }

    private fun text(s: String, substring: Boolean = false) = compose.onAllNodes(hasText(s, substring = substring))

    @Test
    fun gruplarIlkGunAcikDurumVeAciklamalar() {
        show(Rec())
        compose.waitForIdle()
        text("Sürüş kayıtları").assertCountEquals(1)
        text("● Kayıt sürüyor").assertCountEquals(1)
        text("02.10.2026").assertCountEquals(1)
        text("01.10.2026").assertCountEquals(1)
        text("1 sürüş · 2 kayıt").assertCountEquals(1)
        text("Günlük hareket kaydı").assertCountEquals(1) // ilk gün açık
        text("Gün özeti", substring = true).assertCountEquals(0) // ikinci gün kapalı
        text("1.50 MB · kayıt sürüyor").assertCountEquals(1)
        compose.onNodeWithTag("ridelogs-day-header-2026-10-01").performClick()
        compose.waitForIdle()
        text("Gün özeti", substring = true).assertCountEquals(1)
    }

    @Test
    fun etkinKayitSilinemezOtekiSilinirOnayIle() {
        val rec = Rec()
        show(rec)
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag("ridelogs-entry-delete-2026-10-02.jsonl")).assertCountEquals(0)
        compose.onNodeWithTag("ridelogs-entry-menu-2026-10-02_ride_1790900000000.jsonl").performClick()
        compose.onNodeWithTag("ridelogs-entry-delete-2026-10-02_ride_1790900000000.jsonl").performClick()
        compose.waitForIdle()
        text("Bu kaydı sil?").assertCountEquals(1)
        compose.onNodeWithTag("ridelogs-delete-one-confirm").performClick()
        assertEquals(listOf("2026-10-02_ride_1790900000000.jsonl"), rec.deleted)
    }

    @Test
    fun gunSilmeOnayiAktifGunUyarisiVeAnahtar() {
        val rec = Rec()
        show(rec)
        compose.waitForIdle()
        compose.onNodeWithTag("ridelogs-day-menu-2026-10-02").performClick()
        compose.onNodeWithTag("ridelogs-day-delete-2026-10-02").performClick()
        compose.waitForIdle()
        text("02.10.2026 · Günü sil?").assertCountEquals(1)
        text("Bu gün aktif: kayıt ve bildirimler durdurulacak.", substring = true).assertCountEquals(1)
        compose.onNodeWithTag("ridelogs-delete-day-confirm").performClick()
        assertEquals(listOf("2026-10-02"), rec.deletedDays)
        compose.onNodeWithTag("ridelogs-switch").performClick()
        assertEquals(false, rec.logging)
    }

    @Test
    fun bosVeHataMetinleriAzcaDurum() {
        show(Rec(), logging = false, lang = Lang.AZ, entries = emptyList())
        compose.waitForIdle()
        text("Qeyd bağlıdır").assertCountEquals(1)
        text("Hələ sürüş qeydi yoxdur").assertCountEquals(1)
    }

    @Test
    fun okunamayanKayitlardaHataGorunur() {
        show(Rec(), entries = null, error = "Kayıtlar okunamadı")
        compose.waitForIdle()
        text("Kayıtlar okunamadı").assertCountEquals(1)
    }
}
