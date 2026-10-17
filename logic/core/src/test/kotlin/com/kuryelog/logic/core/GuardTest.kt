package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** CORE.md 4.2: tek koruma yardımcısı. Rawstats `GpsService.guarded` ve `MainActivity.safely` ile aynı ilke (A2.3, ek saf test). */
class GuardTest {
    private val seen = mutableListOf<Pair<String, Throwable>>()
    private val sink = FailureSink { source, e -> seen += source to e }

    @Test fun resultPassesThroughAndNothingIsRecorded() {
        assertEquals(7, guarded("x", sink) { 7 })
        assertTrue(seen.isEmpty())
    }

    @Test fun exceptionsAndLinkageErrorsAreRecordedNotThrown() {
        // 16 Oct: a class failing to load (ExceptionInInitializerError, then NoClassDefFoundError) is not an Exception.
        val failures = listOf(IllegalStateException("bad"), ExceptionInInitializerError("init"), NoClassDefFoundError("NavDistance"), AssertionError("a"))
        for (f in failures) assertNull(guarded("page_map", sink) { throw f })
        assertEquals(failures, seen.map { it.second })
        assertTrue(seen.all { it.first == "page_map" })
    }

    @Test fun virtualMachineErrorsCloseTheProcessSoTheCrashIsReported() {
        assertThrows(OutOfMemoryError::class.java) { guarded("import", sink) { throw OutOfMemoryError() } }
        assertThrows(StackOverflowError::class.java) { guarded("tap", sink) { throw StackOverflowError() } }
        assertTrue(seen.isEmpty())
    }

    @Test fun aFailingRecorderDoesNotThrowEither() {
        val broken = FailureSink { _, _ -> throw IllegalStateException("disk full") }
        assertNull(guarded("gps", broken) { throw IllegalArgumentException() })
        assertThrows(OutOfMemoryError::class.java) { guarded("gps", FailureSink { _, _ -> throw OutOfMemoryError() }) { error("x") } }
    }
}
