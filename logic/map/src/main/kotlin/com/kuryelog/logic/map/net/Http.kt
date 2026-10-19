package com.kuryelog.logic.map.net

/** Ağ isteği (MAP.md 8.12 `HttpPort`): `:map` ağı yalnız bu kapıdan geçer; testte sahte, Android'de `AndroidHttp`. */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMs: Long = 6_000,
    /** Kısa link çözümü elle izler (`false`). */
    val followRedirects: Boolean = true,
    /** İkili indirme (gzip parça, HGT, Overpass ham baytları): uygulama gövdeyi [HttpResponse.bytes]'a yazar, [HttpResponse.body] boş. */
    val binary: Boolean = false,
)

class HttpResponse(
    val status: Int,
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
    /** İkili gövde ([HttpRequest.binary]); yoksa null. */
    val bytes: ByteArray? = null,
) {
    /** Ham gövde: [bytes] ya da [body]'nin UTF-8 baytları. */
    val raw: ByteArray get() = bytes ?: body.toByteArray(Charsets.UTF_8)
}

/** Ağ hatası: soket/istemci hatası `java.io.IOException`, zaman aşımı `java.net.SocketTimeoutException` ya da bunun alt türü fırlatır. */
interface HttpPort {
    suspend fun send(request: HttpRequest): HttpResponse
}

/** Dış sunuculara giden `User-Agent` (Overpass, SRTM, Nominatim kuralları: uygulama adı + amaç). MAP.md 2.8. */
object UserAgent {
    fun of(version: String) = "WEEM/$version (personal courier navigation)"
}
