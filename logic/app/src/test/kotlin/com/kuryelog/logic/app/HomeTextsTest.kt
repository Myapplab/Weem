package com.kuryelog.logic.app

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RS `HomeShiftE6Test.watcherAndSettingsFollowTheRule` (ayar metni kısmı): metindeki sayılar kuralın kendisinden gelir. Rawstats'ın
 * metni F4.5b'den sonra "2 dakika" diye kalmıştı; WEEM'de süre de sabitten (30 saniye).
 */
class HomeTextsTest {
    @Test fun ruleTextFollowsTheRule() {
        val tr = HomeTexts.rule(Lang.TR)
        assertTrue(tr.contains("kuş uçuşu 50 m"))
        assertTrue(tr.contains("en az 10 km/s"))
        assertTrue(tr.contains("doğruluk ≤20 m"))
        assertTrue(tr.contains("eve (50 m) dönüp 30 saniye"))
        assertFalse(tr.contains("200 m") || tr.contains("2 dakika") || tr.contains("8 km/s"))
        assertTrue(HomeTexts.rule(Lang.AZ).contains("düz xətt 50 m"))
        assertTrue(HomeTexts.rule(Lang.AZ).contains("30 saniyə"))
    }

    @Test fun measuringAccuracyAndCoordinates() {
        assertEquals("6 saniye ölçülüyor…", HomeTexts.measuring(Lang.TR))
        assertEquals("Dəqiqlik: ±12.5 m", HomeTexts.accuracy(Lang.AZ, 12.46))
        assertTrue(HomeTexts.warning(Lang.TR).startsWith("Doğruluk 25 m'den kötü"))
        assertEquals("40.409300, 49.867100", HomeTexts.coordinates(40.4093, 49.8671))
    }
}
