package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** YolGo `api_settings_test.dart` (1; A6.2b, MAP.md 8.9, 8.11): Maps denetimi anahtar sorununu haritaya bağlar; Places kapalıysa anahtarı suçlamaz. */
class ApiChecksTest {
    @Test
    fun `Maps kontrolü anahtar sorununu haritaya bağlar, Places kapalıysa anahtarı suçlamaz`() {
        assertTrue(ApiChecks.mapsKeyVerdict(GoogleKeyProbe(3), Lang.TR).contains("geçerli"))
        for (kind in listOf(PlacesFailureKind.KEY_INVALID, PlacesFailureKind.APP_BLOCKED)) {
            assertTrue(ApiChecks.mapsKeyVerdict(GoogleKeyProbe(0, PlacesFailure(kind)), Lang.TR).contains("harita da yüklenmez"))
        }
        val disabled = ApiChecks.mapsKeyVerdict(GoogleKeyProbe(0, PlacesFailure(PlacesFailureKind.API_DISABLED)), Lang.TR)
        assertTrue(disabled.contains("tanınıyor"))
        assertFalse(disabled.contains("geçersiz"))
    }
}
