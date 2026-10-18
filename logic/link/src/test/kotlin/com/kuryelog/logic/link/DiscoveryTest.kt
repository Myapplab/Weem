package com.kuryelog.logic.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {
    private val a = PeerId.of("0f0e0d0c-0b0a-4908-8706-050403020100")!!

    @Test
    fun beaconRoundTrip() {
        val b = Beacon.encode(a, 47310)
        assertEquals(Beacon.SIZE, b.size)
        val p = Beacon.decode(b)!!
        assertEquals(a, p.peer)
        assertEquals(47310, p.tcpPort)
        val big = Beacon.decode(Beacon.encode(a, 65535))!!
        assertEquals(65535, big.tcpPort)
    }

    @Test
    fun beaconRejectsForeignAndOld() {
        assertNull(Beacon.decode(ByteArray(24)))
        assertNull(Beacon.decode(Beacon.encode(a, 47310), 23))
        assertNull(Beacon.decode(Beacon.encode(a, 47310) + byteArrayOf(0)))
        val v2 = Beacon.encode(a, 47310).also { it[4] = 2 }
        assertNull(Beacon.decode(v2))
        val kind = Beacon.encode(a, 47310).also { it[5] = 9 }
        assertNull(Beacon.decode(kind))
        assertNull(Beacon.decode(Beacon.encode(a, 0)))
        val buf = Beacon.encode(a, 1) + ByteArray(40)
        assertEquals(1, Beacon.decode(buf, 24)!!.tcpPort)
    }

    @Test
    fun broadcastAddresses() {
        assertEquals("192.168.43.255", LanTargets.broadcast("192.168.43.1", 24))
        assertEquals("10.0.0.15", LanTargets.broadcast("10.0.0.9", 28))
        assertEquals("172.20.255.255", LanTargets.broadcast("172.20.3.4", 16))
        assertNull(LanTargets.broadcast("300.1.1.1", 24))
        assertNull(LanTargets.broadcast("1.2.3", 24))
        assertNull(LanTargets.broadcast("1.2.3.4", 33))
    }

    @Test
    fun interfaceFilter() {
        val ifs = listOf(
            NetIf("wlan0", "192.168.43.77", 24),
            NetIf("ap0", "10.120.55.1", 24),
            NetIf("swlan0", "192.168.201.1", 24),
            NetIf("rmnet_data0", "10.31.2.4", 30),
            NetIf("ccmni1", "10.1.1.1", 24),
            NetIf("v4-rmnet_data0", "192.0.0.4", 29),
            NetIf("tun0", "10.8.0.2", 24),
            NetIf("lo", "127.0.0.1", 8, loopback = true),
            NetIf("wlan1", "100.70.1.2", 24),
            NetIf("wlan2", "192.168.9.9", 24, up = false),
            NetIf("wlan3", "192.168.1.5", 31),
            NetIf("eth0", "172.32.1.1", 24),
        )
        assertEquals(listOf("10.120.55.255", "192.168.201.255", "192.168.43.255"), LanTargets.beaconTargets(ifs))
        assertTrue(LanTargets.beaconTargets(listOf(NetIf("wlan0", "192.168.43.77", 24), NetIf("wlan0", "192.168.43.78", 24))).size == 1)
        assertTrue(LanTargets.beaconTargets(emptyList()).isEmpty())
    }

    @Test
    fun privateRanges() {
        fun p(s: String) = LanTargets.isPrivate(LanTargets.parse(s)!!)
        assertTrue(p("10.0.0.1"))
        assertTrue(p("172.16.0.1"))
        assertTrue(p("172.31.255.1"))
        assertTrue(p("192.168.0.1"))
        assertFalse(p("172.15.0.1"))
        assertFalse(p("172.32.0.1"))
        assertFalse(p("8.8.8.8"))
        assertEquals("1.2.3.4", LanTargets.format(LanTargets.parse("1.2.3.4")!!))
        assertNull(LanTargets.parse("1.2.3.04444"))
    }

    @Test
    fun btTargets() {
        val bonded = listOf(
            BondedDevice("aa:00:00:00:00:02", isPhone = true),
            BondedDevice("AA:00:00:00:00:01", isPhone = true),
            BondedDevice("CC:00:00:00:00:09", isPhone = false),
        )
        assertEquals(listOf("AA:00:00:00:00:01", "AA:00:00:00:00:02"), BtTargets.choose(bonded, null))
        assertEquals(listOf("AA:00:00:00:00:02"), BtTargets.choose(bonded, "aa:00:00:00:00:02"))
        // Öğrenilen adres artık eşleşmiş değilse (Android'den silindi) telefon listesine dönülür.
        assertEquals(2, BtTargets.choose(bonded, "DD:00:00:00:00:00").size)
        assertTrue(BtTargets.choose(listOf(BondedDevice("CC:00:00:00:00:09", false)), null).isEmpty())
        // Öğrenilen adres telefon sınıfında görünmese de (bazı telefonlar sınıfı farklı bildirir) eşleşmişse kullanılır.
        assertEquals(listOf("CC:00:00:00:00:09"), BtTargets.choose(bonded, "cc:00:00:00:00:09"))
    }
}
