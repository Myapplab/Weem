package com.kuryelog.logic.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteModuleTest {
    @Test
    fun moduleIdentity() {
        assertEquals("remote", RemoteModule.NAME)
        assertEquals(listOf<String>("core", "link"), RemoteModule.dependsOn)
    }
}
