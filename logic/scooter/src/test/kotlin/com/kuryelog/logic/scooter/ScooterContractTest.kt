package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.NotificationIds
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * A5.3 kimlik/veri sözleşmesi ve bağlama denetimleri (SCOOTER.md 13.3, VERI_SOZLESMESI 5.2, 7.1, 7.2; kaynak taraması): dosya ve tercih adları,
 * veritabanı adı, servis türü, bildirim numaraları yalnız tablodan, vardiya dinleyici sırası ve modül sınırı (`:orders` `:scooter`'ı tanımaz).
 */
class ScooterContractTest {
    private fun file(path: String) = File(RepoSource.root, path)
    private fun text(path: String) = file(path).readText()

    @Test fun namesOfTheDataContractAreFrozen() {
        assertEquals("economics-scooter.jsonl", ScooterSamples.FILE)
        assertEquals("scooter.autoConnect", ScooterLinkRules.PREF_AUTO)
        assertEquals("scooter.shiftLink", ScooterLinkRules.PREF_OWNED)
        assertEquals("scooter_link", ScooterLinkRules.EVENT_TYPE)
        assertEquals("NinebotLogger-1760000000000.zip", LogExport.fileName(1_760_000_000_000L))
        assertEquals("pairing-v1", PairingVault.PREFS)
        assertEquals("NinebotLogger.Pairing.v1", PairingVault.ALIAS)
        val store = text("scooter/src/main/kotlin/com/kuryelog/app/scooter/LogStore.kt")
        assertTrue(store.contains("const val NAME = \"ninebot-logger.db\""))
        assertTrue(store.contains("SQLiteOpenHelper(context, database, null, 1)")) // sürüm 1
        assertTrue(text("scooter/src/main/kotlin/com/kuryelog/app/scooter/LoggerPreferences.kt").contains("const val NAME = \"logger\""))
        assertTrue(text("scooter/src/main/kotlin/com/kuryelog/app/scooter/ScooterShiftLink.kt").contains("const val PREF_SCOOTER = \"economics.scooterId\""))
    }

    @Test fun serviceTypeIsLocationAndConnectedDevice() {
        val manifest = text("scooter/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:name=\"com.kuryelog.app.scooter.ScooterService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"location|connectedDevice\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
    }

    @Test fun notificationNumbersComeOnlyFromTheTable() {
        assertEquals(7, NotificationIds.SCOOTER_FGS); assertEquals(8, NotificationIds.PAIRING)
        assertEquals(4902, NotificationIds.SCOOTER_LINK); assertEquals(5102, NotificationIds.SCOOTER_AUTO_START)
        val literal = Regex("""(notify|cancel|startForeground)\(\s*\d""")
        val bad = RepoSource.scooterSources().filter { "/logic/" !in it.invariantSeparatorsPath }.flatMap { f ->
            RepoSource.code(f).filter { literal.containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" }
        }
        assertTrue(bad.toString(), bad.isEmpty())
    }

    @Test fun shiftListenerComesAfterOrdersAndBeforeScreenReading() {
        val app = text("app/src/main/kotlin/com/kuryelog/app/WeemApplication.kt")
        val orders = app.indexOf("add(\"orders\"")
        val scooter = app.indexOf("add(\"scooter\"")
        val screen = app.indexOf("add(\"screen_reading\"")
        assertTrue("$orders $scooter $screen", orders in 0 until scooter && scooter < screen)
        assertTrue(app.contains("Orders.bindScooter(this, Scooter.bridge)"))
    }

    @Test fun ordersModuleKnowsOnlyTheBridgeInterface() {
        val bad = file("orders/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readText().let { "com.kuryelog.app.scooter" in it || "com.kuryelog.logic.scooter" in it } }.map { it.name }.toList()
        assertTrue(bad.toString(), bad.isEmpty())
        assertTrue(text("orders/src/main/kotlin/com/kuryelog/app/orders/Orders.kt").contains("override val scooter: ScooterBridge get() = Orders.scooter"))
    }
}
