package com.kuryelog.logic.map.net

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * `java.net.HttpURLConnection` ile [HttpPort] (JVM ve Android'de aynı; Android `AndroidHttp` bunu kullanır, CI harita aracı da).
 * Bağlantı ve okuma zaman aşımı [HttpRequest.timeoutMs]; zaman aşımı `SocketTimeoutException`.
 */
class UrlHttp : HttpPort {
    override suspend fun send(request: HttpRequest): HttpResponse = runInterruptible(Dispatchers.IO) {
        val c = URL(request.url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = request.method
            c.connectTimeout = request.timeoutMs.toInt()
            c.readTimeout = request.timeoutMs.toInt()
            c.instanceFollowRedirects = request.followRedirects
            request.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            request.body?.let { body ->
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            val stream = if (status >= 400) c.errorStream else c.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val headers = c.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(",") }
            if (request.binary) HttpResponse(status, headers = headers, bytes = bytes)
            else HttpResponse(status, String(bytes, Charsets.UTF_8), headers)
        } finally {
            c.disconnect()
        }
    }
}
