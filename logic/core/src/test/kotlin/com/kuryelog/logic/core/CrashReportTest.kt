package com.kuryelog.logic.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** 16 Oct ("Rawstats opens and closes at once", nothing on the phone said why): the report the crash recorder writes and the home card shows.
 *  WEEM (CORE.md 4.1): shared text starts with "WEEM"; app frames are `com.kuryelog.app`, `com.kuryelog.logic` and the old `az.ninebot.logger`. */
class CrashReportTest {
    private fun thrown(message: String?): Throwable = try {
        throw IllegalStateException(message, IllegalArgumentException("inner " + "x".repeat(500)))
    } catch (e: Throwable) { e }

    @Test fun reportCarriesTheErrorTheThreadAndTheStack() {
        val j = CrashReport.json(1_000L, "0.25.1", "main", thrown("bad state"))
        assertEquals(1_000L, j.getLong("at"))
        assertEquals("0.25.1", j.getString("version"))
        assertEquals("main", j.getString("thread"))
        assertEquals("java.lang.IllegalStateException", j.getString("error"))
        assertEquals("bad state", j.getString("message"))
        val stack = j.getString("stack")
        assertTrue(stack.startsWith("java.lang.IllegalStateException: bad state"))
        assertTrue(stack.contains("\tat com.kuryelog.logic.core.CrashReportTest.thrown(CrashReportTest.kt:"))
        assertTrue(stack.contains("Caused by: java.lang.IllegalArgumentException: inner"))
    }

    @Test fun messagesAreCutShortTheyMayQuoteWhatWasParsed() {
        val j = CrashReport.json(1L, "v", "main", thrown("Value " + "{\"note\":\"kapı kodu 1234\"}".repeat(40) + "\nsecond line"))
        assertEquals(CrashReport.MESSAGE_CHARS, j.getString("message").length)
        assertFalse(j.getString("message").contains("second line"))
        // Header lines of the stack (the error and each cause) are cut too; frame lines stay whole.
        j.getString("stack").lines().filterNot { it.startsWith("\t") }.forEach { assertTrue(it, it.length <= CrashReport.MESSAGE_CHARS) }
        assertTrue(j.getString("stack").length <= CrashReport.STACK_CHARS)
        assertEquals("", CrashReport.json(1L, "v", "main", thrown(null)).getString("message"))
    }

    @Test fun summaryNamesTheErrorAndTheFirstAppLine() {
        val j = JSONObject().put("error", "java.lang.NullPointerException").put("message", "x was null")
            .put("stack", "java.lang.NullPointerException: x was null\n\tat android.view.View.performClick(View.java:7659)\n" +
                "\tat com.kuryelog.app.MainActivity.renderHome(MainActivity.kt:512)\n\tat com.kuryelog.app.MainActivity.render(MainActivity.kt:400)")
        assertEquals("NullPointerException: x was null (MainActivity.kt:512)", CrashReport.summary(j))
        assertEquals("ScooterLink.kt:9", CrashReport.appFrame("\tat java.util.HashMap.get(HashMap.java:1)\n\tat com.kuryelog.app.ScooterLink.install(ScooterLink.kt:9)"))
        assertEquals("SurusPaneli.java:26", CrashReport.appFrame("\tat az.ninebot.logger.SurusPaneli.startLive(SurusPaneli.java:26)"))
        assertNull(CrashReport.appFrame("\tat android.os.Looper.loop(Looper.java:1)"))
        assertEquals("RuntimeException", CrashReport.summary(JSONObject().put("error", "java.lang.RuntimeException").put("stack", "")))
    }

    @Test fun logicModuleFramesAreAppFrames() {
        val stack = "java.lang.IllegalStateException: x\n\tat java.util.HashMap.get(HashMap.java:1)\n" +
            "\tat com.kuryelog.logic.core.HomeShift.observe(HomeShift.kt:88)\n\tat com.kuryelog.app.MainActivity.render(MainActivity.kt:400)"
        assertEquals("HomeShift.kt:88", CrashReport.appFrame(stack))
        assertEquals("IllegalStateException: x (HomeShift.kt:88)",
            CrashReport.summary(JSONObject().put("error", "java.lang.IllegalStateException").put("message", "x").put("stack", stack)))
        assertNull(CrashReport.appFrame("\tat com.kuryelogger.Other.run(Other.kt:1)")) // only our own packages
    }

    @Test fun textToSendHasVersionTimeThreadAndStack() {
        val j = JSONObject().put("version", "0.25.1").put("thread", "main").put("stack", "java.lang.X: y\n\tat a.b(c:1)")
        assertEquals("WEEM 0.25.1 · 16 Eki 21:40 · main\njava.lang.X: y\n\tat a.b(c:1)", CrashReport.text(j, "16 Eki 21:40"))
    }

    @Test fun cardShowsUntilHiddenAndANewerCrashShowsAgain() {
        val crash = JSONObject().put("at", 2_000L)
        assertTrue(CrashReport.unseen(crash, 0L))
        assertFalse(CrashReport.unseen(crash, 2_000L))
        assertTrue(CrashReport.unseen(JSONObject().put("at", 3_000L), 2_000L))
        assertFalse(CrashReport.unseen(null, 0L))
    }

    /** A native crash in OCR or an ANR never reaches a Java handler; Android's own exit record (API 30) is shown instead. */
    @Test fun androidExitRecordsShownForNativeCrashAnrAndFailedStartOnly() {
        val now = 10L * 86_400_000L
        assertTrue(CrashReport.exitShown(CrashReport.EXIT_NATIVE, now - 60_000L, now))
        assertTrue(CrashReport.exitShown(CrashReport.EXIT_ANR, now - 86_400_000L, now))
        assertTrue(CrashReport.exitShown(CrashReport.EXIT_INIT_FAILURE, now, now))
        // A Java crash (4) is in our own report; low memory (3), user swipe (10), update (11) are not crashes.
        for (reason in listOf(3, 4, 10, 11, 0)) assertFalse("$reason", CrashReport.exitShown(reason, now, now))
        // Older than three days: an old record from before the update.
        assertFalse(CrashReport.exitShown(CrashReport.EXIT_NATIVE, now - 4 * 86_400_000L, now))
        val j = CrashReport.exitJson(now, "?", CrashReport.EXIT_ANR, "Input dispatching timed out\nmore", "com.kuryelog.app")
        assertEquals("ANR: Input dispatching timed out", CrashReport.summary(j))
        assertEquals("android", j.getString("thread"))
        assertTrue(CrashReport.text(j, "t").contains("Android exit reason 6 (ANR) · com.kuryelog.app"))
        assertEquals("NativeCrash", CrashReport.summary(CrashReport.exitJson(now, "?", CrashReport.EXIT_NATIVE, null, null)))
    }

    @Test fun crashFileKeepsTheNewestReportsOnceItGrows() {
        val small = List(5) { "r$it" }
        assertEquals(small, CrashReport.trim(small))
        val big = List(40) { "r$it" + "x".repeat(9_000) }
        val kept = CrashReport.trim(big)
        assertEquals(10, kept.size)
        assertEquals(big.last(), kept.last())
    }

    /** A10.2: çökme kaydı paylaşılır; mesajdaki ve neden satırlarındaki telefon gizlenir. Yapay numara. */
    @Test fun crashReportHidesPhoneNumbers() {
        val e = RuntimeException("wrap", NumberFormatException("For input string: \"+994 50 123 45 67\""))
        val j = CrashReport.json(1L, "v", "main", NumberFormatException("For input string: \"0501234567\"").apply { initCause(e) })
        assertEquals("For input string: \"[tel]\"", j.getString("message"))
        assertFalse(j.getString("stack"), j.getString("stack").contains("123 45 67"))
        assertFalse(j.getString("stack").contains("0501234567"))
    }
}
