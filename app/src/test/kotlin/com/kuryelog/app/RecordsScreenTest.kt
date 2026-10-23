package com.kuryelog.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.core.app.ApplicationProvider
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.DailyRecords
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.scooter.ScooterPanel
import com.kuryelog.logic.orders.CourierRecords
import com.kuryelog.logic.orders.LogOrdersSource
import com.kuryelog.logic.orders.OrdersSource
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A3.8: Kayıtlar gün sayfaları (eski günler ve arşiv) JVM'de çizilir: etkin gün, "Arşivi aç" ile arşivdeki gün, "Arşivle" ile günün
 * arşive geçmesi. Canlı akış bağlı olmadığı için "Sil" kapalıdır (courier.db yeniden kurulamaz).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class RecordsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val baku = ZoneId.of("Asia/Baku")
    private val now = ZonedDateTime.of(2026, 10, 16, 14, 0, 0, 0, baku).toInstant().toEpochMilli()

    private class FixedClock(val now: Long, val zoneId: ZoneId) : Clock {
        override fun wallMs() = now
        override fun monoMs() = now
        override fun zone() = zoneId
    }

    private fun records(): DailyRecords {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return DailyRecords(Store.logsRoot(ctx))
    }

    private fun seed() {
        val root = ApplicationProvider.getApplicationContext<Context>().let(Store::logsRoot)
        // Aynı JVM'deki önceki denemeler (kabuğu açan AcceptanceTest) bugünün gününü yazmış olabilir: yalnız bu tohum kalsın.
        root.listFiles()?.forEach { it.deleteRecursively() }
        for (day in listOf("2026-10-14", "2026-10-13")) File(root, day).apply { mkdirs() }.let { File(it, "events.jsonl").writeText("{\"type\":\"shift_end\",\"min\":60,\"km\":5}\n") }
        records().archive("2026-10-13", enabled = true)
    }

    private val source: (Context) -> OrdersSource = { ctx ->
        LogOrdersSource(Store.log(ctx), CourierRecords.Memory(), KuryelogPrefs.Memory(), FixedClock(now, baku))
    }

    private fun reveal(text: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(text)
        repeat(8) {
            if (runCatching { node.assertIsDisplayed() }.isSuccess) return node
            compose.onRoot().performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.2f) }
            compose.waitForIdle()
        }
        return node.also { it.assertIsDisplayed() }
    }

    @Test fun activeDaysThenTheArchiveAndArchivingMovesADay() {
        seed()
        compose.setContent { WeemTheme(dark = false) { RecordsScreen(Lang.TR, onBack = {}, source = source) } }
        // Etkin: 14 Ekim kayıt günü; arşivdeki 13 Ekim görünmez.
        reveal("14 Ekim 2026 · Çarşamba")
        assertEquals(0, compose.onAllNodesWithText("13 Ekim 2026", substring = true).fetchSemanticsNodes().size)
        // "Sil" vardiya yokken de kapalı: canlı akış bağlı değil.
        compose.onAllNodesWithText("Sil").onFirst().assertIsNotEnabled()
        // Arşivle: gün arşive geçer, etkin listede kalmaz.
        reveal("Arşivle").performClick()
        compose.waitForIdle()
        assertTrue(records().days().first { it.date == "2026-10-14" }.archived)
        // Arşivi aç: iki gün da arşivde, yeniden eskiye.
        reveal("Arşivi aç").performClick()
        compose.waitForIdle()
        reveal("Günlük kayıtlara dön")
        reveal("14 Ekim 2026 · Çarşamba")
        reveal("1 / 2 gün")
        reveal("Arşivden çıkar")
    }

    @Test fun everythingInOneZipAndScooterRecordsAreInRecords() {
        seed()
        compose.setContent { WeemTheme(dark = false) { RecordsScreen(Lang.TR, onBack = {}, source = source) } }
        // S37: tüm kayıtlar tek ZIP'te ve scooter kayıtları (Sürüş Paneli ZIP'leri) Kayıtlar'da.
        reveal("Tüm kayıtlar tek ZIP'te")
        reveal("Sipariş kayıtlarını paylaş (.zip)")
        reveal("Scooter kayıtları")
        for (c in ScooterPanel.ExportChoice.entries) reveal(c.text.get(Lang.TR))
    }
}
