package com.kuryelog.logic.link

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkModuleTest {
    @Test
    fun moduleIdentity() {
        assertEquals("link", LinkModule.NAME)
        assertEquals(listOf<String>("core"), LinkModule.dependsOn)
    }
}
