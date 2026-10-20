package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.graph.FormatException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** YolGo `place_search_test.dart` Places hata ve ayrıştırma testleri (2; A6.2b, MAP.md 8.8, 8.11). Paket beklentisi `az.duzyol.app` → `com.kuryelog.app`. */
class PlacesRestTest {
    private fun error(code: Int, status: String, reason: String) = JSONObject().put(
        "error",
        JSONObject().put("code", code).put("status", status).put("message", "secret project 123 key AIzaTEST")
            .put("details", org.json.JSONArray().put(JSONObject().put("@type", "type.googleapis.com/google.rpc.ErrorInfo").put("reason", reason))),
    )

    @Test
    fun `Google hata nedenleri ayrılır ve ham mesaj (anahtar proje) gösterilmez`() {
        val cases = listOf(
            Triple(error(400, "INVALID_ARGUMENT", "API_KEY_INVALID"), 400, PlacesFailureKind.KEY_INVALID),
            Triple(error(403, "PERMISSION_DENIED", "API_KEY_ANDROID_APP_BLOCKED"), 403, PlacesFailureKind.APP_BLOCKED),
            Triple(error(403, "PERMISSION_DENIED", "SERVICE_DISABLED"), 403, PlacesFailureKind.API_DISABLED),
            Triple(error(403, "PERMISSION_DENIED", "API_KEY_SERVICE_BLOCKED"), 403, PlacesFailureKind.API_BLOCKED),
            Triple(error(403, "PERMISSION_DENIED", "BILLING_DISABLED"), 403, PlacesFailureKind.BILLING),
            Triple(error(429, "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED"), 429, PlacesFailureKind.QUOTA),
        )
        for ((body, code, kind) in cases) {
            val failure = PlacesRest.classify(code, body.toString())
            assertEquals(kind, failure.kind)
            for (lang in Lang.entries) for (text in listOf(placesFailureShort(failure, lang), placesFailureDetail(failure, lang))) {
                assertFalse(text, text.contains("AIzaTEST"))
                assertFalse(text, text.contains("secret project"))
            }
        }
        assertEquals(PlacesFailureKind.UNAVAILABLE, PlacesRest.classify(500, "not json").kind)
        val blocked = PlacesRest.classify(403, error(403, "PERMISSION_DENIED", "API_KEY_ANDROID_APP_BLOCKED").toString())
        assertTrue(placesFailureDetail(blocked, Lang.TR).contains("com.kuryelog.app"))
    }

    @Test
    fun `details koordinatsız ya da bozuk yanıt hata olur, restoran türü tanınır`() {
        try {
            PlacesRest.parseDetails(JSONObject("""{"id":"x"}"""))
            fail("FormatException bekleniyordu")
        } catch (_: FormatException) {
        }
        val d = PlacesRest.parseDetails(JSONObject("""{"location":{"latitude":40.4,"longitude":49.8},"types":["cafe"]}"""))
        assertTrue(d.restaurant)
        assertEquals(49.8, d.point.lon, 0.0)
        // queryPrediction ve bozuk öğe atlanır
        val items = PlacesRest.parseAutocomplete(JSONObject("""{"suggestions":[{"queryPrediction":{"text":{"text":"q"}}},"junk"]}"""))
        assertTrue(items.isEmpty())
    }
}
