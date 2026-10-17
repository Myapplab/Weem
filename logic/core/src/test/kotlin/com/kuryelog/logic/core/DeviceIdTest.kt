package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** CORE.md 1.10: WEEM'in yeni cihaz kimliği (A2.4 biçimi sabitler). */
class DeviceIdTest {
    @Test fun createdOnceAsALowercaseUuidAndKept() {
        val p = KuryelogPrefs.Memory()
        val id = DeviceId.ensure(p) { UUID.fromString("0F8FAD5B-D9CB-469F-A165-70867728950E") }
        assertEquals("0f8fad5b-d9cb-469f-a165-70867728950e", id)
        assertEquals(id, p.map["weem.deviceId"])
        assertEquals(id, DeviceId.ensure(p) { error("not again") })
        assertEquals(36, DeviceId.ensure(KuryelogPrefs.Memory()).length)
        assertNotEquals(DeviceId.ensure(KuryelogPrefs.Memory()), DeviceId.ensure(KuryelogPrefs.Memory()))
    }

    @Test fun aBrokenValueIsReplaced() {
        for (bad in listOf("", "abc", "0F8FAD5B-D9CB-469F-A165-70867728950E", "0f8fad5b-d9cb-469f-a165-70867728950e ")) {
            val p = KuryelogPrefs.Memory(mutableMapOf("weem.deviceId" to bad))
            val id = DeviceId.ensure(p)
            assertTrue(bad, DeviceId.valid(id))
            assertNotEquals(bad, id)
        }
    }
}
