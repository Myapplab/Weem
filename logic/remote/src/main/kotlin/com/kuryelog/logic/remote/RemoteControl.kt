package com.kuryelog.logic.remote

import org.json.JSONObject
import java.util.Base64
import java.util.UUID

/** İzleyenin isteği (`remote.ask`): oturum, geçici P-256 açık anahtarı (65 bayt), istenen en uzun kenar. */
class RemoteAsk(val session: UUID, pub: ByteArray, val maxSide: Int) {
    val pub: ByteArray = pub.copyOf()
}

/** Paylaşanın yanıtı (`remote.offer`). `READY`'de [pub] ve [port] dolu. */
class RemoteOffer(val session: UUID, val state: State, pub: ByteArray? = null, val port: Int? = null) {
    val pub: ByteArray? = pub?.copyOf()

    enum class State(val key: String) {
        CONSENT("consent"), READY("ready"), REFUSED("refused"), UNAVAILABLE("unavailable");

        companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } }
    }
}

/** Oturumu bitiren (`remote.end`), iki yönde. */
class RemoteEnd(val session: UUID, val reason: Reason) {
    enum class Reason(val key: String) {
        /** İzleyen kapattı. */
        VIEWER("viewer"),
        /** Paylaşan "Paylaşımı bitir" dedi. */
        HOST("host"),
        /** Sistem paylaşımı durdurdu. */
        STOPPED("stopped"),
        /** 10 dk izleyensiz paylaşım kapandı. */
        IDLE("idle"),
        ERROR("error");

        companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: ERROR }
    }
}

/**
 * Oturum denetimi iletileri (REMOTE.md 1.3): link üstünden teslim zorunlu, TTL [TTL_MS]. JSON, `schemaVersion` 1; bilinmeyen alan yok
 * sayılır, bozuk yükte null.
 */
object RemoteControl {
    const val ASK = "remote.ask"
    const val OFFER = "remote.offer"
    const val END = "remote.end"
    const val PREFIX = "remote."
    const val SCHEMA = 1
    const val TTL_MS = 20_000L
    /** P-256 sıkıştırılmamış nokta. */
    const val PUB_SIZE = 65

    fun ask(a: RemoteAsk): ByteArray = bytes(base(a.session).put("pub", b64(a.pub)).put("maxSide", a.maxSide))

    fun offer(o: RemoteOffer): ByteArray = bytes(base(o.session).put("state", o.state.key).apply {
        o.pub?.let { put("pub", b64(it)) }
        o.port?.let { put("port", it) }
    })

    fun end(e: RemoteEnd): ByteArray = bytes(base(e.session).put("reason", e.reason.key))

    fun parseAsk(b: ByteArray): RemoteAsk? = parse(b) { j, s ->
        val pub = pub(j) ?: return@parse null
        val side = j.optInt("maxSide", 0)
        if (side < MIN_SIDE) null else RemoteAsk(s, pub, side)
    }

    fun parseOffer(b: ByteArray): RemoteOffer? = parse(b) { j, s ->
        val st = RemoteOffer.State.of(j.optString("state", "")) ?: return@parse null
        if (st != RemoteOffer.State.READY) return@parse RemoteOffer(s, st)
        val pub = pub(j) ?: return@parse null
        val port = j.optInt("port", 0)
        if (port !in 1..65535) null else RemoteOffer(s, st, pub, port)
    }

    fun parseEnd(b: ByteArray): RemoteEnd? = parse(b) { j, s -> RemoteEnd(s, RemoteEnd.Reason.of(j.optString("reason", ""))) }

    /** İstenebilecek en kısa "en uzun kenar". */
    const val MIN_SIDE = 320

    private fun base(s: UUID) = JSONObject().put("schemaVersion", SCHEMA).put("session", s.toString())

    private fun <T> parse(b: ByteArray, f: (JSONObject, UUID) -> T?): T? = try {
        val j = JSONObject(String(b, Charsets.UTF_8))
        if (j.optInt("schemaVersion", 0) < 1) null else f(j, UUID.fromString(j.getString("session")))
    } catch (e: Exception) {
        null
    }

    private fun pub(j: JSONObject): ByteArray? = try {
        Base64.getDecoder().decode(j.getString("pub")).takeIf { it.size == PUB_SIZE }
    } catch (e: Exception) {
        null
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun bytes(j: JSONObject) = j.toString().toByteArray(Charsets.UTF_8)
}
