package com.kuryelog.logic.orders

import com.kuryelog.logic.core.*
import org.junit.Assert.*
import org.junit.Test

/**
 * A10.4a (S20, WEEM 1.1) — Siparişler → Araçlar. Rawstats `YolGoButtonE5Test.homeToolsHaveNoYolGoEvaluateButton` Araçlar kartının
 * bölünmüş ekran ve iptal gözlemi satırlarını kaynak taramasıyla denetliyordu; WEEM'de aynı denetim modelin çıktısına yazıldı. Bölünmüş
 * ekranın hedef seçimi ve metinleri Rawstats `openSplitScreen`'den satır satır.
 */
class OrdersToolsTest {
    private val wolt = "com.wolt.courierapp"
    private val boltPkg = "ee.mtakso.driver"
    private val boltAlt = "com.bolt.courier"

    private class Src(val shift: Boolean = true, val picked: Set<String> = emptySet(), val seen: Map<String, Long> = emptyMap(), val saves: Boolean = true,
                      base: OrdersSource = base()) : OrdersSource by base {
        val marked = ArrayList<String>()
        override fun shiftOn() = shift
        override fun targets() = picked
        override fun lastSeen(pkg: String) = seen[pkg] ?: 0L
        override fun markCancellationObservation(pkg: String): Boolean { if (saves) marked += pkg; return saves }
    }

    private companion object {
        val baku: java.time.ZoneId = java.time.ZoneId.of("Asia/Baku")
        val clock = object : Clock { override fun wallMs() = 0L; override fun monoMs() = 0L; override fun zone() = baku }
        fun base(): OrdersSource {
            val dir = java.nio.file.Files.createTempDirectory("tools").toFile().also { it.deleteOnExit() }
            return LogOrdersSource(LogStore(dir, now = { 0L }, zone = { baku }), CourierRecords.Memory(), KuryelogPrefs.Memory(), clock)
        }
    }

    @Test fun toolsAreShownOnlyWhileTheShiftRuns() {
        assertTrue(OrdersTools(Src(shift = true), Lang.TR).visible)
        assertFalse(OrdersTools(Src(shift = false), Lang.TR).visible)
    }

    @Test fun splitScreenNeedsBothPlatformsPicked() {
        val miss = "Ayarlar → Uygulamaları seç bölümünde Wolt Partner ve Bolt Food Courier'ı seç."
        assertEquals(SplitTargets.Missing(miss), OrdersTools(Src(picked = emptySet()), Lang.TR).splitTargets())
        assertEquals(SplitTargets.Missing(miss), OrdersTools(Src(picked = setOf(wolt)), Lang.TR).splitTargets())
        assertEquals(SplitTargets.Missing(miss), OrdersTools(Src(picked = setOf(boltPkg)), Lang.TR).splitTargets())
        assertEquals(SplitTargets.Ready(wolt, boltPkg), OrdersTools(Src(picked = setOf(boltPkg, wolt, "com.android.chrome")), Lang.TR).splitTargets())
        // Bolt kuryesi iki paketle gelebilir (`bolt` ya da `mtakso`); ikisi de Bolt Food sayılır, sıralı ilki seçilir.
        assertEquals(SplitTargets.Ready(wolt, boltAlt), OrdersTools(Src(picked = setOf(boltPkg, boltAlt, wolt)), Lang.TR).splitTargets())
        assertEquals("Ayarlar → Tətbiqləri seç bölməsində Wolt Partner və Bolt Food Courier-i seç.", (OrdersTools(Src(), Lang.AZ).splitTargets() as SplitTargets.Missing).message)
    }

    @Test fun oneCancellationRowPerPickedPlatformWithTheFreshestPackage() {
        val tools = OrdersTools(Src(picked = setOf(wolt, boltPkg, boltAlt, "com.android.chrome"), seen = mapOf(boltPkg to 5_000L, boltAlt to 2_000L)), Lang.TR)
        // Ada göre sıralı paketlerin platform sırası (Rawstats `platforms()`): sıralı ilk paket belirler (com.android…, com.bolt…, com.wolt…).
        assertEquals(listOf("chrome", "Bolt Food", "Wolt"), tools.cancellations().map { it.platform })
        assertEquals(boltPkg, tools.cancellations()[1].pkg)
        assertEquals("Bolt Food: iptal gözlemi kaydet", tools.cancellations()[1].label)
        assertEquals("Wolt: ləğv müşahidəsini qeyd et", OrdersTools(Src(picked = setOf(wolt)), Lang.AZ).cancellations().single().label)
        // Hiç görülmediyse ilk paket.
        assertEquals(boltAlt, OrdersTools(Src(picked = setOf(boltPkg, boltAlt)), Lang.TR).cancellations().single().pkg)
    }

    @Test fun cancellationIsAStatisticsNoteAndSaysSo() {
        val tools = OrdersTools(Src(), Lang.TR)
        assertEquals("Bu yalnızca istatistik notudur. Platformda iptal işlemi yapmaz.", tools.cancelMessage)
        assertEquals("Bu yalnız statistika qeydidir. Platformada ləğv əməliyyatı etmir.", OrdersTools(Src(), Lang.AZ).cancelMessage)
    }

    @Test fun markingSavesOnlyWhenTheSourceRecordsIt() {
        val ok = Src(); assertEquals("Gözlem kaydedildi", OrdersTools(ok, Lang.TR).markCancellation(wolt)); assertEquals(listOf(wolt), ok.marked)
        val no = Src(saves = false); assertEquals("Gözlem kaydedilemedi. Vardiya açık olmalı.", OrdersTools(no, Lang.TR).markCancellation(wolt)); assertTrue(no.marked.isEmpty())
    }

    @Test fun noYolGoEvaluateButtonInTools() {
        // E5: Teklif kazancı düğmesi yok; Araçlar yalnız bölünmüş ekran ve iptal gözlemi.
        val t = OrdersTools(Src(picked = setOf(wolt, boltPkg)), Lang.TR)
        val labels = listOf(t.splitLabel) + t.cancellations().map { it.label }
        assertEquals(listOf("Wolt + Bolt bölünmüş ekranı aç", "Wolt: iptal gözlemi kaydet", "Bolt Food: iptal gözlemi kaydet"), labels)
        assertTrue(labels.none { it.contains("YolGo", true) })
    }

    @Test fun flowRefusesTheNoteWithoutAShiftAndWritesItWithOne() {
        // Gerçek akış: vardiya kapalıysa hiçbir şey yazılmaz ve false döner (toast "kaydedilemedi").
        val off = env(shift = false)
        assertFalse(OrderFlow(off).also { it.start() }.markCancellationObservation(wolt))
        assertFalse(events(off).contains("cancellation_user_reported"))
        val on = env(shift = true)
        assertTrue(OrderFlow(on).also { it.start() }.markCancellationObservation(wolt))
        val row = events(on).lines().filter { it.isNotBlank() }.map { org.json.JSONObject(it) }.single { it.optString("type") == "cancellation_user_reported" }
        assertEquals(wolt, row.getString("pkg")); assertFalse(row.getBoolean("platformActionPerformed"))
    }

    private fun events(env: OrderFlow.Env): String = java.io.File(env.log.dayDir(env.log.day(0L)), "events.jsonl").takeIf { it.exists() }?.readText().orEmpty()

    private fun env(shift: Boolean): OrderFlow.Env {
        val prefs = KuryelogPrefs.Memory().also {
            if (shift) it.put(mapOf(KuryelogPrefs.MANUAL_POLICY to 1, KuryelogPrefs.SHIFT to true, KuryelogPrefs.MANUAL_SESSION to true, OrderFlow.MIGRATION_07 to true))
        }
        val dir = java.nio.file.Files.createTempDirectory("tools").toFile().also { it.deleteOnExit() }
        return object : OrderFlow.Env {
            override val prefs = prefs
            override val db: CourierRecords = CourierRecords.Memory()
            override val clock: Clock = Companion.clock
            override val log = LogStore(dir, now = { 0L }, zone = { baku })
        }
    }
}
