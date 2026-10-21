package com.kuryelog.logic.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uzaktan dokunuşun kuralı (REMOTE.md 2.3, plan 4.6; `NoGestureTest` `:remote`'u buraya bırakır): dokunuş ve sistem tuşu çağrıları
 * yalnız ayrı servis `TouchService`'te; o servis ekranı okumaz, düğüme basmaz, olay dinlemez; okuma servisi dokunuş yetkisi almaz;
 * servis yalnız `RemoteHub`'ın verdiğini oynatır.
 */
class TouchRuleTest {
    private fun code(text: String) = text.lineSequence().filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
        .joinToString("\n")
    private fun remote() = RepoSource.mainKotlin().filter { "/remote/src/main/" in it.invariantSeparatorsPath && "/logic/" !in it.invariantSeparatorsPath }
    /** XML açıklamaları sayılmaz. */
    private fun xml(path: String) = RepoSource.text(path).replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    private val touch = "remote/src/main/kotlin/com/kuryelog/app/remote/TouchService.kt"

    @Test fun gestureCallsOnlyInTouchService() {
        val apis = listOf("dispatchGesture(", "performGlobalAction(", "GestureDescription")
        val found = remote().filter { f -> val c = code(f.readText()); apis.any { it in c } }.map { it.name }
        assertEquals(listOf("TouchService.kt"), found)
    }

    @Test fun touchServiceReadsNothingAndPressesNoNode() {
        val c = code(RepoSource.text(touch))
        for (f in listOf("AccessibilityNodeInfo", "rootInActiveWindow", "getRootInActiveWindow", "windows", "performAction(", "takeScreenshot",
            "ACTION_SET_TEXT", "ClipboardManager", "java.net", "Socket")) {
            assertFalse(f, Regex("\\b${Regex.escape(f)}").containsMatchIn(c))
        }
        // Olay dinlemez.
        assertTrue(c.contains("override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit"))
    }

    @Test fun onlyRemoteHubFeedsTheTouchService() {
        val callers = RepoSource.mainKotlin().filter { f -> val c = code(f.readText()); "TouchService.current?.play(" in c || "TouchService.current?.global(" in c }
        assertEquals(listOf("RemoteHub.kt"), callers.map { it.name })
        // RemoteHub dokunuşu yalnız HostMachine komutundan verir (paylaşım açık, izleyen doğrulanmış; HostMachine denetler).
        val hub = code(RepoSource.text("remote/src/main/kotlin/com/kuryelog/app/remote/RemoteHub.kt"))
        assertTrue(hub.contains("is HostMachine.Command.Gesture -> TouchService.current?.play(c.part)"))
        assertTrue(hub.contains("is HostMachine.Command.Global -> TouchService.current?.global(c.action)"))
    }

    @Test fun touchConfigAsksOnlyForGestures() {
        val xml = xml("remote/src/main/res/xml/remote_touch_config.xml")
        assertTrue(xml.contains("android:canPerformGestures=\"true\""))
        for (f in listOf("canRetrieveWindowContent", "canTakeScreenshot", "accessibilityEventTypes", "flagRequestTouchExploration", "flagRetrieveInteractiveWindows",
            "flagRequestFilterKeyEvents", "canRequestFilterKeyEvents")) assertFalse(f, xml.contains(f))
        // Okuma servisi dokunuş yetkisi almaz.
        assertFalse(xml("capture/src/main/res/xml/accessibility_config.xml").contains("canPerformGestures"))
    }

    @Test fun touchServiceIsSeparateFromWatchService() {
        val m = xml("remote/src/main/AndroidManifest.xml")
        val decl = RepoSource.body(m, "android:name=\"com.kuryelog.app.remote.TouchService\"", "</service>")
        assertTrue(decl.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertTrue(decl.contains("@xml/remote_touch_config"))
        assertFalse(m.contains("WatchService"))
    }
}
