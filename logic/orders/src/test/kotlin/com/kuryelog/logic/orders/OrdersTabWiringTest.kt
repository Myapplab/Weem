package com.kuryelog.logic.orders

import com.kuryelog.logic.capture.RepoSource
import org.junit.Assert.*
import org.junit.Test

/**
 * A3.8 bağlantısı: Siparişler sekmesi ve Kayıtlar gün sayfaları süreçteki tek [OrderFlow]'dan okur (A3.7 `Orders`); ana sayfa
 * adlarını gösterir, rozet penceresi adsız kalır (K35). Compose ekranları JVM'de `:app` testlerinde çizilir (`OrdersTabTest`); burası
 * kaynak bağlantısını kilitler (Rawstats `OverlayBadgeK35Test`/`AverageListE1Test` tarama kalıbı).
 */
class OrdersTabWiringTest {
    private val access = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/OrdersAccess.kt")
    private val shell = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/WeemApp.kt")
    private val source = RepoSource.text("logic/orders/src/main/kotlin/com/kuryelog/logic/orders/OrdersSource.kt")

    @Test fun theTabAndTheRecordsPageReadTheOneFlow() {
        assertTrue(access.contains("Orders.flow(ctx)"))
        assertTrue(access.contains("Orders.records(ctx)"))
        assertTrue(access.contains("LogOrdersSource("))
        assertTrue(shell.contains("Tab.ORDERS -> OrdersTab(lang)"))
        assertTrue(shell.contains("RecordsScreen(lang, onBack = { records = false })"))
    }

    @Test fun theHomeKeepsTheNamesWhileTheOverlayDropsThem() {
        // Bugün kartının "şu an" metni adlı satırlar (varsayılan names = true); rozet penceresi `badgeLines(names = false)` (OrdersHost).
        assertTrue(source.contains("flow?.badgeLines()"))
        assertFalse(source.contains("names = false"))
        assertTrue(RepoSource.text("orders/src/main/kotlin/com/kuryelog/app/orders/OrdersHost.kt").contains("badgeLines(names = false)"))
    }

    @Test fun theTabNeverTouchesTheCourierApps() {
        for (f in listOf("OrdersTab.kt", "OrdersBlocks.kt", "OrdersAccess.kt", "RecordsScreen.kt")) {
            val text = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/$f")
            for (banned in listOf("performAction", "dispatchGesture", "AccessibilityNodeInfo", "ACTION_CLICK")) assertFalse("$f $banned", text.contains(banned))
        }
    }

    @Test fun deleteIsOffUnlessTheFlowCanRebuildTheDatabase() {
        val records = RepoSource.text("app/src/main/kotlin/com/kuryelog/app/RecordsScreen.kt")
        assertTrue(records.contains("canDelete = src.canAct"))
        assertTrue(records.contains("enabled = canDelete"))
        assertTrue(records.contains("OrdersAccess.reload(ctx)"))
    }
}
