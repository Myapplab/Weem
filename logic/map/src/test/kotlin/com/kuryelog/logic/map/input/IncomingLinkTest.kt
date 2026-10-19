package com.kuryelog.logic.map.input

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gelen bağlantı (MAP.md 7.15, 8.11; A6.2b): YolGo `nav_feed_test.dart` "MainActivity linki" (1) + Rawstats'tan gelen YK `NavFeedContractTest`
 * `referrerIsAnAndroidAppPackage` ve `aLinkCarriesItsActionReferrerAndTime` (2). Dart `NativeBridge` köprü testi (nav_feed_test "köprü") `LinkIntake`
 * saf kısmına uyarlanır: ilk ve sonraki bağlantı aynı `IncomingLink` alanlarıyla gelir (Android Intent → `IncomingLink` A6.6'da).
 */
class IncomingLinkTest {
    private val t0 = 1_791_295_270_000L

    @Test
    fun `VIEW navigasyon isteği, SEND paylaşılan metin, gönderen ve saat`() {
        val view = IncomingLink.parse(JSONObject("""{"text":" geo:40.4,49.8 ","action":"view","referrer":"com.wolt.courierapp","atMs":$t0}"""))!!
        assertEquals(IncomingLink("geo:40.4,49.8", true, "com.wolt.courierapp", t0), view)
        val send = IncomingLink.parse(JSONObject("""{"text":"x","action":"send","referrer":"bad pkg!","atMs":"x"}"""))!!
        assertFalse(send.view)
        assertNull(send.referrer)
        assertNull(send.at)
        val old = IncomingLink.parseLegacy("geo:1,2")!! // eski köprü yalnız metni verirdi
        assertEquals(Triple("geo:1,2", true, null), Triple(old.text, old.view, old.referrer))
        assertNull(IncomingLink.parse(JSONObject("""{"text":" "}""")))
        assertNull(IncomingLink.parse(null))
    }

    @Test
    fun `köprü teslimi ilk ve sonraki bağlantı istek bilgisiyle gelir, Paylaş metni navigasyon isteği değildir`() {
        val first = IncomingLink.parse(JSONObject("""{"text":"geo:40.4,49.8","action":"view","referrer":"com.wolt.courierapp","atMs":$t0}"""))!!
        assertEquals(Triple(true, "com.wolt.courierapp", t0), Triple(first.view, first.referrer, first.at))
        val shared = IncomingLink.parse(JSONObject("""{"text":"Nizami 5","action":"send","atMs":1}"""))!!
        assertEquals("Nizami 5", shared.text)
        assertFalse(shared.view)
        // onIntent yoksa yalnız metin (eski köprü): action yoksa VIEW sayılır
        assertEquals("geo:1,2", IncomingLink.parse(JSONObject("""{"text":"geo:1,2","action":"view"}"""))!!.text)
    }

    @Test
    fun `gönderen bir Android uygulama paketidir`() {
        assertEquals("com.wolt.courierapp", IncomingLink.referrerPackage("android-app://com.wolt.courierapp"))
        assertEquals("com.wolt.courierapp", IncomingLink.referrerPackage("android-app://com.wolt.courierapp/https/wolt.com"))
        for (bad in listOf(null, "", "https://wolt.com", "android-app://", "android-app://bad pkg", "android-app://" + "a".repeat(101))) {
            assertNull(bad, IncomingLink.referrerPackage(bad))
        }
    }

    @Test
    fun `bağlantı eylemini gönderenini ve saatini taşır`() {
        assertEquals(
            mapOf("text" to "geo:40.4,49.8", "action" to "view", "referrer" to "com.wolt.courierapp", "atMs" to t0),
            IncomingLink.payload("geo:40.4,49.8", "android.intent.action.VIEW", "com.wolt.courierapp", t0),
        )
        assertEquals("send", IncomingLink.payload("x", "android.intent.action.SEND", null, 1L)["action"])
        assertTrue(IncomingLink.payload("x", "android.intent.action.SEND", null, 1L)["referrer"] == null)
    }
}
