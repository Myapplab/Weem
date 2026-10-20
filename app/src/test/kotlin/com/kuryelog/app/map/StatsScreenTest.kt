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
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.day.StopRec
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/** İstatistik ekranı (MAP.md 12.7, 12.13 "Yeni"): bölümler, aday düğmeleri, paylaş yalnız kapalı günde, sekme etiketleri, boş metin, silme onayı (M-12-3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h2600dp-port-xxhdpi")
class StatsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneOffset.ofHours(4)
    private val t0 = Instant.parse("2026-10-02T06:00:00Z").toEpochMilli() // 10:00 Bakü
    private val now = t0 + 3_600_000

    private fun stop(kind: String, durS: Int, at: Long, long: Boolean = false, answer: String? = null) =
        StopRec(at, at + durS * 1000L, 40.4, 49.8, kind, true, answer = answer, longWait = long)

    private fun closedDay() = DayState().also { d ->
        d.startedAtMs = t0; d.endedAtMs = t0 + 3_600_000; d.distM = 12_345.0; d.movingS = 3000.0; d.startReason = "home_departure"; d.endReason = "home_arrival"
        d.stops += stop("light", 40, t0 + 10_000); d.stops += stop("light?", 150, t0 + 400_000, long = true); d.stops += stop("light?", 30, t0 + 800_000)
        d.orders += OrderRec(1, t0).also { it.restArrivedAtMs = t0 + 100_000; it.restLeftAtMs = t0 + 160_000; it.custArrivedAtMs = t0 + 400_000; it.deliveredAtMs = t0 + 430_000 }
    }

    private class Rec {
        val reviews = ArrayList<Pair<Long, Boolean>>()
        val deletes = ArrayList<String>()
        var shared = 0
    }

    private fun show(days: List<DayState>, rec: Rec, lang: Lang = Lang.TR, focus: Long? = null) = compose.setContent {
        WeemTheme(false) {
            StatsScreen(
                days, lang, now, zone, focus,
                StatsActions(
                    streetName = { _, _, _ -> "Nizami" }, onReview = { s, l -> rec.reviews += s to l }, onDelete = { d, _ -> rec.deletes += d }, onShare = { rec.shared++ },
                    lightsMap = { _, _, _ -> },
                ),
            )
        }
    }

    private fun text(s: String, substring: Boolean = false) = compose.onAllNodes(hasText(s, substring = substring))

    @Test
    fun gunSayfasiBolumleriVeAdayDugmeleri() {
        val rec = Rec()
        show(listOf(closedDay()), rec)
        compose.waitForIdle()
        text("2026-10-02").assertCountEquals(1)
        text("Evden çıkınca başladı · Eve dönünce bitti").assertCountEquals(1)
        text("TRAFİK IŞIKLARI").assertCountEquals(1)
        text("EKSİK IŞIK KONTROLÜ").assertCountEquals(1)
        text("SİPARİŞLER").assertCountEquals(1)
        text("Bugün 10:00").assertCountEquals(1)
        text("1/1").assertCountEquals(1)
        text("12.35").assertCountEquals(1)
        compose.onNodeWithTag("stats-share").assertExists()
        compose.onAllNodes(hasTestTag("stats-lights-map")).assertCountEquals(1)
        // 2 dk üstü aday "Onayla / Reddet", normal aday "Işık vardı / Işık değildi".
        compose.onNodeWithTag("stats-confirm-${t0 + 400_000}").performClick()
        compose.onNodeWithTag("stats-reject-${t0 + 800_000}").performClick()
        assertEquals(listOf(t0 + 400_000 to true, t0 + 800_000 to false), rec.reviews)
        text("Onayla").assertCountEquals(1)
        text("Işık vardı").assertCountEquals(1)
        text("? Nizami").assertCountEquals(1)
    }

    @Test
    fun surenGundePaylasYokBosGunMetniAzcaVeSilmeOnayi() {
        val rec = Rec()
        val open = closedDay().also { it.active = true; it.endedAtMs = null }
        show(listOf(open), rec, Lang.AZ)
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag("stats-share")).assertCountEquals(0)
        text("İŞIQFORLAR").assertCountEquals(1)
        compose.onNodeWithTag("stats-delete").performClick()
        compose.waitForIdle()
        text("2026-10-02 · günü sil").assertCountEquals(1)
        text("Xəritənin aktiv günü dayandırılacaq; növbə davam edir.", substring = true).assertCountEquals(1)
        compose.onNodeWithTag("stats-delete-confirm").performClick()
        assertEquals(listOf("2026-10-02"), rec.deletes)
    }

    @Test
    fun gunYoksaAciklamaVeSekmeEtiketleri() {
        show(emptyList(), Rec())
        compose.waitForIdle()
        text("Henüz gün kaydı yok.", substring = true).assertCountEquals(1)
    }

    @Test
    fun birdenCokGunSekmeleriVeOdak() {
        val old = DayState().also { it.startedAtMs = Instant.parse("2026-09-30T06:30:00Z").toEpochMilli(); it.endedAtMs = it.startedAtMs!! + 600_000 }
        show(listOf(closedDay(), old), Rec(), focus = old.startedAtMs)
        compose.waitForIdle()
        text("Bugün 10:00").assertCountEquals(1)
        text("30 Eyl 10:30").assertCountEquals(1)
        text("2026-09-30").assertCountEquals(1) // odak: başlangıcı eşleşen sekme
        compose.onNodeWithTag("stats-tab-0").performClick()
        compose.waitForIdle()
        text("2026-10-02").assertCountEquals(1)
    }
}
