package com.kuryelog.logic.map.net

import java.util.Collections

/** Sahte ağ (Dart `MockClient` karşılığı): her istek [handler]'a gider ve [requests]'e yazılır; handler hata fırlatırsa çağrı o hatayla biter. */
class FakeHttp(private val handler: suspend (HttpRequest) -> HttpResponse) : HttpPort {
    val requests: MutableList<HttpRequest> = Collections.synchronizedList(ArrayList())

    override suspend fun send(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request)
    }

    fun to(path: String) = synchronized(requests) { requests.filter { java.net.URI(it.url).path == path } }
}

/**
 * Sanal saat (debounce testleri): `delay` çağrıları [advance] ile ilerletilene dek bekler. Gerçek zaman geçmez, test deterministik kalır.
 * Kullanım: `PlaceSearch(delay = clock::delay)`.
 */
class VirtualDelay {
    private val waiters = ArrayList<Pair<Long, kotlinx.coroutines.CompletableDeferred<Unit>>>()
    private var now = 0L

    suspend fun delay(ms: Long) {
        val d = kotlinx.coroutines.CompletableDeferred<Unit>()
        synchronized(waiters) { waiters += (now + ms) to d }
        d.await()
    }

    fun advance(ms: Long) {
        val due = synchronized(waiters) {
            now += ms
            val ready = waiters.filter { it.first <= now }
            waiters.removeAll(ready.toSet())
            ready
        }
        due.forEach { it.second.complete(Unit) }
    }
}

/** [cond] doğru olana dek (en çok ~3 sn, gerçek zaman) bekler; zaman aşımında son değeri döner. */
fun waitUntil(cond: () -> Boolean): Boolean {
    for (i in 0 until 120) {
        if (cond()) return true
        Thread.sleep(25)
    }
    return cond()
}
