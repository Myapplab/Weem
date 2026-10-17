package com.kuryelog.logic.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureModuleTest {
    @Test
    fun moduleIdentity() {
        assertEquals("capture", CaptureModule.NAME)
        assertEquals(listOf<String>("core"), CaptureModule.dependsOn)
    }
}
