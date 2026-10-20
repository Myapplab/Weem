package com.kuryelog.logic.map.nav

import com.kuryelog.logic.map.geo.Geo
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `assistant_test.dart` "yön filtresi" (1; A6.2b, MAP.md 7.6, 16.3): 359→1 geçişi 358 derecelik dönüş sayılmaz, bilinmeyen yön uydurulmaz. */
class HeadingFilterTest {
    @Test
    fun `yön filtresi 360 derecede sarar, bilinmeyen yönü uydurmaz`() {
        val f = HeadingFilter()
        f.update(359.0)
        f.update(1.0)
        assertTrue(Geo.angleDiff(f.value!!, 0.0) < 1)
        assertNull(f.update(Double.NaN))
    }
}
