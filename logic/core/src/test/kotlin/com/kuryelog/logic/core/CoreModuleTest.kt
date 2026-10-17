package com.kuryelog.logic.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreModuleTest {
    @Test
    fun moduleIdentity() {
        assertEquals("core", CoreModule.NAME)
        assertEquals(listOf<String>(), CoreModule.dependsOn)
    }
}
