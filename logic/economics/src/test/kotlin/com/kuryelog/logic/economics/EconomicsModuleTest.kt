package com.kuryelog.logic.economics

import org.junit.Assert.assertEquals
import org.junit.Test

class EconomicsModuleTest {
    @Test
    fun moduleIdentity() {
        assertEquals("economics", EconomicsModule.NAME)
        assertEquals(listOf<String>("core", "orders"), EconomicsModule.dependsOn)
    }
}
