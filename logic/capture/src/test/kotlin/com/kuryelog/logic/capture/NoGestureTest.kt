package com.kuryelog.logic.capture

import org.junit.Assert.*
import org.junit.Test

/**
 * Hiçbir şeye basmama (CAPTURE.md 7.4, plan Bölüm 5): WEEM Wolt/Bolt'ta kendiliğinden hiçbir şeye basmaz. `:remote` dışındaki
 * bütün modüllerin kaynağında dokunuş/hareket/ayar yazma çağrısı yok; erişilebilirlik yapılandırması hareket istemez; `:capture`
 * panoya bakmaz. Uzaktan kullanımın dokunuşu ayrı serviste (plan 4.6, A9 kendi kuralını yazar).
 */
class NoGestureTest {
    /** Yorum satırları sayılmaz (açıklamalarda bu adlar geçebilir). */
    private fun code(text: String) = text.lineSequence().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
        .joinToString("\n")
    private fun sources() = RepoSource.mainKotlin().filter { val p = it.invariantSeparatorsPath; "/remote/" !in p }

    @Test fun noModuleTapsSwipesOrWritesIntoAnotherApp() {
        val forbidden = listOf("performAction(", "dispatchGesture(", "performGlobalAction(", "GestureDescription", "AccessibilityNodeInfo.ACTION_",
            "AccessibilityAction", "ACTION_SET_TEXT", "ACTION_CLICK")
        val found = sources().flatMap { f -> val c = code(f.readText()); forbidden.filter { it in c }.map { "${f.invariantSeparatorsPath.substringAfter(RepoSource.root.invariantSeparatorsPath)}: $it" } }
        assertEquals(emptyList<String>(), found)
        assertTrue("WatchService yok", sources().any { it.name == "WatchService.kt" })
    }

    @Test fun accessibilityConfigAsksForNoGestures() {
        val xml = RepoSource.text("capture/src/main/res/xml/accessibility_config.xml")
        assertFalse(xml.contains("canPerformGestures"))
        assertFalse(xml.contains("flagRequestTouchExploration"))
        assertTrue(xml.contains("android:canRetrieveWindowContent=\"true\""))
    }

    @Test fun screenReadingNeverReadsTheClipboard() {
        val capture = sources().filter { val p = it.invariantSeparatorsPath; "/capture/src/main/" in p }
        assertTrue(capture.isNotEmpty())
        for (f in capture) assertFalse(f.name, code(f.readText()).contains("ClipboardManager"))
    }

    /** RS `OfferNetLineTest.accessibilityDescriptionSaysTheNumberStaysOnThePhone`: the permission text tells what stays where. */
    @Test fun accessibilityDescriptionSaysTheNumberStaysOnThePhone() {
        val xml = RepoSource.text("capture/src/main/res/values/strings.xml")
        val desc = Regex("<string name=\"acc_desc\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(xml)!!.groupValues[1]
        assertFalse(desc, desc.contains("kaydetmez"))
        assertTrue(desc, desc.contains("yalnızca bu telefondaki sipariş veritabanında saklar"))
        assertTrue(desc, desc.contains("Sipariş kabul veya ret düğmelerine basmaz."))
    }
}
