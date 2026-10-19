package com.kuryelog.app.map

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Lang
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `dataset_screen_test.dart` 5 test (döngüyle 8; A6.2b, MAP.md 13.8, 13.18): başarılı Yenile eski okuma hatasını temizler · toplama hatası başarılı liste okumasında
 * kaybolmaz · açıklama (3. test UYARLANIR: M-13-2 — yoklama aralıkları yok; TR, AZ) · gün özeti eksikleri ve platform sayıları (kapalıyken görünmez; "Bilinmiyor"; "Haftalık
 * ödeme") · 380 dp'de günler kapalı, bağımsız açılır, yenile/topla açık günü korur (TR/AZ × açık/koyu; dil × tema döngüsü tek çalışmaya indirildi — Compose testinde tek `setContent`). Kanal sahtesi yerine sahte [DatasetRuntime].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w380dp-h1800dp-port-xxhdpi")
class DatasetScreenTest {
    @get:Rule val compose = createComposeRule()

    private class FakeRuntime(var rows: List<DatasetDayInfo> = emptyList()) : DatasetRuntime {
        var failList = false
        var failCollect = false
        val calls = ArrayList<String>()
        override suspend fun list(): List<DatasetDayInfo> {
            calls += "list"
            if (failList) error("READ_FAILED")
            return rows
        }

        override suspend fun collect(day: String) {
            calls += "collect:$day"
            if (failCollect) error("COLLECT_FAILED")
        }
    }

    private fun show(rt: DatasetRuntime, lang: Lang = Lang.TR, dark: Boolean = false, shiftDay: String? = null) =
        compose.setContent { WeemTheme(dark) { DatasetScreen(rt, lang, shiftDay) } }

    private fun text(s: String, substring: Boolean = false) = compose.onAllNodes(hasText(s, substring = substring))

    @Test
    fun basariliYenileEskiOkumaHatasiniTemizler() {
        val rt = FakeRuntime().also { it.failList = true }
        show(rt)
        compose.waitForIdle()
        text("Gün listesi okunamadı. Tekrar dene.").assertCountEquals(1)
        rt.failList = false
        compose.onNodeWithContentDescription("Yenile").performClick()
        compose.waitForIdle()
        text("Gün listesi okunamadı. Tekrar dene.").assertCountEquals(0)
    }

    @Test
    fun toplamaHatasiBasariliListeOkumasindaKaybolmaz() {
        val rt = FakeRuntime().also { it.failCollect = true }
        show(rt, shiftDay = "2026-10-13")
        compose.waitForIdle()
        compose.onNodeWithTag("dataset-collect-shift").performClick()
        compose.waitForIdle()
        text("İşlem tamamlanamadı.", substring = true).assertCountEquals(1)
    }

    @Test
    fun aciklamaVardiyaAcikkenTekliflerAnindaKaydedilirIkiDilde() {
        val expected = mapOf(
            Lang.TR to "Vardiya açıkken teklifler görüldükleri anda kaydedilir. Paketlerde rota, yükseklik, mevcut hava bilgisi ve WEEM gün kayıtları bir araya gelir.",
            Lang.AZ to "Növbə açıq olanda təkliflər göründüyü anda qeyd olunur. Paketlərdə marşrut, yüksəklik, mövcud hava məlumatı və WEEM gün qeydləri bir araya gəlir.",
        )
        var lang by androidx.compose.runtime.mutableStateOf(Lang.TR)
        val rt = FakeRuntime()
        compose.setContent { WeemTheme(false) { DatasetScreen(rt, lang, null) } }
        for ((l, line) in expected) {
            lang = l
            compose.waitForIdle()
            text(line).assertCountEquals(1)
            text("saniyede bir", substring = true).assertCountEquals(0) // yoklama aralıkları yok
        }
        assertEquals("yalnız ilk açılışta liste okunur", listOf("list"), rt.calls)
    }

    @Test
    fun gunOzetiEksikleriVeAyriPlatformSayilariniAcikGosterir() {
        val rt = FakeRuntime(
            listOf(
                DatasetDayInfo(
                    "2026-10-10", "2026-10-10_Cumartesi", wolt = 12, bolt = 3, matched = 10, onlyDayLog = 3, onlyLive = 2, routeComputedRatio = .5, emptyFieldRatio = .4,
                    scooterCoverageRatio = null, collectionComplete = false, missingManual = listOf("weekly_payout", "shift_note"),
                ),
            ),
        )
        show(rt)
        compose.waitForIdle()
        text("2026-10-10 · Cumartesi").assertCountEquals(1)
        text("Teklifler: Wolt 12 · Bolt 3").assertCountEquals(0)
        compose.onNodeWithTag("dataset-day-header-2026-10-10").performClick()
        compose.waitForIdle()
        text("Teklifler: Wolt 12 · Bolt 3").assertCountEquals(1)
        text("Scooter kaydı kapsaması: Bilinmiyor").assertCountEquals(1)
        text("Haftalık ödeme", substring = true).assertCountEquals(1)
        assertEquals(listOf("list"), rt.calls)
        // M-13-2: açıklama WEEM akışını anlatır, eski sürekli 7 sn yoklama yok.
        text("teklifler görüldükleri anda kaydedilir", substring = true).assertCountEquals(1)
        text("7 saniyede bir", substring = true).assertCountEquals(0)
    }

    @Test
    fun gunlerKapaliBagimsizAcilirYenileToplaAcikGunuKorur() {
        fun rows() = listOf("2026-10-15", "2026-10-14").map { DatasetDayInfo(it, "${it}_uzun_klasor_adi_icin_380_px_deneme", wolt = if (it.endsWith("15")) 15 else 14, bolt = 2) }
        val rt = FakeRuntime(rows())
        show(rt)
        compose.waitForIdle()
        compose.onNodeWithTag("dataset-day-2026-10-15").assertExists()
        compose.onNodeWithTag("dataset-day-2026-10-14").assertExists()
        text("Wolt 15", substring = true).assertCountEquals(0)
        text("Wolt 14", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("dataset-day-header-2026-10-15").performClick()
        compose.onNodeWithTag("dataset-day-header-2026-10-14").performClick()
        compose.waitForIdle()
        text("Wolt 15", substring = true).assertCountEquals(1)
        text("Wolt 14", substring = true).assertCountEquals(1)
        compose.onNodeWithTag("dataset-day-header-2026-10-14").performClick()
        compose.waitForIdle()
        text("Wolt 14", substring = true).assertCountEquals(0)
        rt.rows = rows().reversed()
        compose.onNodeWithContentDescription("Yenile").performClick()
        compose.waitForIdle()
        text("Wolt 15", substring = true).assertCountEquals(1)
        text("Wolt 14", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("dataset-sync-2026-10-15").performClick()
        compose.waitForIdle()
        assertEquals(1, rt.calls.count { it.startsWith("collect") })
        assertEquals("collect:2026-10-15", rt.calls.single { it.startsWith("collect") })
        text("Wolt 15", substring = true).assertCountEquals(1)
        compose.onNodeWithTag("dataset-day-header-2026-10-15").performClick()
        compose.waitForIdle()
        text("Wolt 15", substring = true).assertCountEquals(0)
    }
}
