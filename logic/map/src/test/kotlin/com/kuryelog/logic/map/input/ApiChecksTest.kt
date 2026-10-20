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

/** "API ve bağlantılar" kartlarının kuralları (MAP.md 8.9): Maps kartı ağa çıkmadan önce anahtar ve Play Services; ORS denetimi; rota motoru. */
class ApiCardsTest {
    @Test
    fun `Maps kartı anahtarsız ya da Play Services hazır değilken ağa çıkmaz`() = kotlinx.coroutines.runBlocking {
        var probed = 0
        val probe: suspend () -> GoogleKeyProbe = { probed++; GoogleKeyProbe(2) }
        assertTrue(ApiChecks.mapsSdk(false, 0, Lang.TR, probe).contains("anahtarı yok"))
        assertTrue(ApiChecks.mapsSdk(true, 2, Lang.AZ, probe).contains("(kod 2)"))
        assertTrue(probed == 0)
        assertTrue(ApiChecks.mapsSdk(true, 0, Lang.TR, probe).contains("geçerli"))
        assertTrue(probed == 1)
    }

    @Test
    fun `ORS denetimi anahtarsız bilgi, features dizisi başarı, aksi hata`() = kotlinx.coroutines.runBlocking {
        val urls = mutableListOf<String>()
        var reply = com.kuryelog.logic.map.net.HttpResponse(200, """{"features":[]}""")
        val http = com.kuryelog.logic.map.net.FakeHttp { urls += it.url; reply }
        assertTrue(ApiChecks.probeOrs(http, "", Lang.TR).startsWith("Anahtar yok"))
        assertTrue(urls.isEmpty())
        assertTrue(ApiChecks.probeOrs(http, "k y", Lang.TR) == "Pelias araması yanıt verdi.")
        assertTrue(urls.single() == "https://api.openrouteservice.org/geocode/autocomplete?api_key=k+y&text=Baku&size=1")
        reply = com.kuryelog.logic.map.net.HttpResponse(403, "{}")
        assertTrue(runCatching { ApiChecks.probeOrs(http, "k", Lang.TR) }.isFailure)
        assertTrue(ApiChecks.routeEngine(null, Lang.TR) == "Yol ağı yüklenmedi.")
        assertTrue(ApiChecks.routeEngine(1234, Lang.AZ) == "1234 yol hissəsi hazırdır")
    }
}
