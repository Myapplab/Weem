package com.kuryelog.logic.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapModuleTest {
    @Test
    fun moduleIdentity() {
        assertEquals("map", MapModule.NAME)
        assertEquals(listOf<String>("core"), MapModule.dependsOn)
    }
}
