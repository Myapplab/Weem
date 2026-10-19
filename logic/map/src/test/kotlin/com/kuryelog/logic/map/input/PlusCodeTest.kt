package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.geo.Baku
import org.junit.Assert.assertEquals
import org.junit.Test

/** YolGo `address_parser_test.dart` "Plus Code" 2 test (A6.2b, MAP.md 8.11). Uygulama: A6.6. */
class PlusCodeTest {
    @Test
    fun `tam kod çözülür`() {
        val p = PlusCode.decode("8HGF9RQW+344")!!
        assertEquals(40.38764, p.lat, 0.0001)
        assertEquals(49.84533, p.lon, 0.0001)
    }

    @Test
    fun `kısa kod Bakü referansıyla tamamlanır (Bolt restoran adresi)`() {
        val p = PlusCode.recover("9RQW+344", Baku.center)!!
        assertEquals(40.38764, p.lat, 0.0001)
        assertEquals(49.84533, p.lon, 0.0001)
    }
}
