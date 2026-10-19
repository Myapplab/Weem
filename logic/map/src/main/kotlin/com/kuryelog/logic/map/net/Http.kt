package com.kuryelog.logic.map.net

/** Ağ isteği (MAP.md 8.12 `HttpPort`): `:map` ağı yalnız bu kapıdan geçer; testte sahte, Android'de `AndroidHttp` (A6.6). */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMs: Long = 6_000,
    /** Kısa link çözümü elle izler (`false`). */
    val followRedirects: Boolean = true,
)

data class HttpResponse(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap())

/** Ağ hatası: soket/istemci hatası `java.io.IOException`, zaman aşımı `java.net.SocketTimeoutException` ya da bunun alt türü fırlatır. */
interface HttpPort {
    suspend fun send(request: HttpRequest): HttpResponse
}
