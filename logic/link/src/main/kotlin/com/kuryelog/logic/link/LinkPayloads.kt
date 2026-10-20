package com.kuryelog.logic.link

import com.kuryelog.logic.core.BatterySummary
import com.kuryelog.logic.core.DaySummary
import com.kuryelog.logic.core.EnergyRequest
import com.kuryelog.logic.core.EnergyResult
import com.kuryelog.logic.core.NavTarget
import com.kuryelog.logic.core.OfferSummary
import com.kuryelog.logic.core.RouteStatus
import com.kuryelog.logic.core.ShiftState
import com.kuryelog.logic.core.TripStages
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zarf yükleri (LINK.md 4.3): `:core` mesajlarının JSON'u (plan 4.4, `toJson`/`parse`), UTF-8. Okuyucu bilinmeyen alanı yok sayar,
 * bozuk yükte null döner (ileti atılır, kanal sürer).
 *
 * **Saat:** yüklerdeki zamanlar gönderenin duvar saatidir; [Decoder] bunları `yerel = eşin saati − fark` ile bu telefonun saatine
 * çevirir (LINK.md 3.9; fark = eşin saati − benimki, ilk ölçümden önce 0).
 *
 * **S5:** giden her yük [ReceiverSafety] süzgecinden geçer: müşteri adı, telefonu, notları taşıyabilecek bir anahtar görülürse
 * yük yollanmaz ([UnsafePayload]). Tipler bu alanları zaten taşımaz; süzgeç yeni alan eklenirse sızıntıyı derlemeden sonra da yakalar.
 */
object LinkPayloads {
    const val OFFERS_SCHEMA = 1
    const val ROLE_SCHEMA = 1

    fun shift(s: ShiftState): ByteArray = bytes(s.toJson())
    fun offers(list: List<OfferSummary>): ByteArray =
        bytes(JSONObject().put("schemaVersion", OFFERS_SCHEMA).put("offers", JSONArray().apply { list.forEach { put(it.toJson()) } }))
    fun stages(s: TripStages): ByteArray = bytes(s.toJson())
    fun day(d: DaySummary): ByteArray = bytes(d.toJson())
    fun navTarget(t: NavTarget): ByteArray = bytes(t.toJson())
    fun energyRequest(r: EnergyRequest): ByteArray = bytes(r.toJson())
    fun energyResult(r: EnergyResult): ByteArray = bytes(r.toJson())
    fun battery(b: BatterySummary): ByteArray = bytes(b.toJson())
    fun route(r: RouteStatus): ByteArray = bytes(r.toJson())
    fun role(r: LinkRole): ByteArray = bytes(JSONObject().put("schemaVersion", ROLE_SCHEMA).put("role", r.key))

    private fun bytes(j: JSONObject): ByteArray {
        ReceiverSafety.check(j)
        return j.toString().toByteArray(Charsets.UTF_8)
    }

    /** Eşten gelen yükler; [offsetMs] = eşin saati − bu telefonunki. */
    class Decoder(private val offsetMs: Long) {
        private fun t(v: Long): Long = v - offsetMs
        private fun t(v: Long?): Long? = v?.let(::t)

        fun shift(b: ByteArray): ShiftState? = ShiftState.parse(text(b))?.let {
            it.copy(startedAtMs = t(it.startedAtMs), endedAtMs = t(it.endedAtMs), generatedAtMs = if (it.generatedAtMs > 0) t(it.generatedAtMs) else 0)
        }

        fun offers(b: ByteArray): List<OfferSummary>? {
            val j = obj(b) ?: return null
            if (j.opt("schemaVersion") != OFFERS_SCHEMA) return null
            val a = j.optJSONArray("offers") ?: return null
            return (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let(OfferSummary::parse)?.let {
                    it.copy(firstSeenAtMs = t(it.firstSeenAtMs), lastSeenAtMs = t(it.lastSeenAtMs), expiresAtMs = t(it.expiresAtMs))
                }
            }
        }

        fun stages(b: ByteArray): TripStages? = TripStages.parse(text(b))?.let { s ->
            TripStages(s.trips.map { tr -> tr.copy(endedAtMs = t(tr.endedAtMs), events = tr.events.map { it.copy(atMs = t(it.atMs)) }) })
        }

        fun day(b: ByteArray): DaySummary? = DaySummary.parse(text(b))
        fun navTarget(b: ByteArray): NavTarget? = NavTarget.parse(text(b))?.let { it.copy(requestedAtMs = t(it.requestedAtMs)) }
        fun energyRequest(b: ByteArray): EnergyRequest? =
            EnergyRequest.parse(text(b))?.let { it.copy(fromWallMs = t(it.fromWallMs), toWallMs = t(it.toWallMs)) }
        fun energyResult(b: ByteArray): EnergyResult? = EnergyResult.parse(text(b))
        fun battery(b: ByteArray): BatterySummary? = BatterySummary.parse(text(b))?.let { it.copy(atMs = t(it.atMs)) }
        fun route(b: ByteArray): RouteStatus? = RouteStatus.parse(text(b))?.let { r ->
            RouteStatus(r.target?.let { it.copy(requestedAtMs = t(it.requestedAtMs)) }, r.route?.let { it.copy(measuredAtMs = t(it.measuredAtMs)) }, t(r.updatedAtMs))
        }
        fun role(b: ByteArray): LinkRole? {
            val j = obj(b) ?: return null
            if (j.opt("schemaVersion") != ROLE_SCHEMA) return null
            val k = j.opt("role") as? String ?: return null
            return LinkRole.entries.firstOrNull { it.key == k }
        }

        private fun text(b: ByteArray): String? = runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString() }.getOrNull()
        private fun obj(b: ByteArray): JSONObject? = text(b)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }
}

/** Giden yükte müşteri kişisel verisi taşıyabilecek anahtar (S5; `VERI_SOZLESMESI` Bölüm 9). */
class UnsafePayload(val key: String) : IllegalArgumentException("unsafe link payload key")

/**
 * S5 süzgeci: müşteri koordinatı ve adres metni gider; müşteri adı, telefonu, notları gitmez. Anahtarlar küçük harfe çevrilip
 * [FORBIDDEN] parçaları aranır (iç içe nesne ve diziler dahil). Değerlere bakılmaz: adres metni serbest metindir.
 */
object ReceiverSafety {
    val FORBIDDEN = listOf("customername", "customerphone", "phone", "tel", "note", "comment", "instruction", "firstname", "lastname", "fullname", "recipient")

    fun check(j: Any?) {
        when (j) {
            is JSONObject -> for (k in j.keys()) {
                val low = k.lowercase()
                if (FORBIDDEN.any { low.contains(it) } || low == "name" || low == "customer") throw UnsafePayload(k)
                check(j.opt(k))
            }
            is JSONArray -> for (i in 0 until j.length()) check(j.opt(i))
        }
    }
}
