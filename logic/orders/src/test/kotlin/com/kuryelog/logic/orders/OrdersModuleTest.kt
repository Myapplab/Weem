package com.kuryelog.logic.orders

import org.junit.Assert.assertEquals
import org.junit.Test

class OrdersModuleTest {
    @Test
    fun moduleIdentity() {
        assertEquals("orders", OrdersModule.NAME)
        assertEquals(listOf<String>("core", "capture"), OrdersModule.dependsOn)
    }
}
