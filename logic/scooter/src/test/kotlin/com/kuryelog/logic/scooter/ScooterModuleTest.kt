package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test

class ScooterModuleTest {
    @Test
    fun moduleIdentity() {
        assertEquals("scooter", ScooterModule.NAME)
        assertEquals(listOf<String>("core"), ScooterModule.dependsOn)
    }
}
