package com.kuryelog.logic.link

import org.json.JSONObject

/**
 * S44 (kullanıcı kararı; LINK.md 4.2 `DialNumber`): Provider'da arama uygulaması bir numarayla açıldı; Receiver numarayı panoya kopyalar ve
 * "Ara" bildirimi gösterir. **S5 istisnası:** müşteri telefonu yalnız bu türde ve yalnız "Aranan numarayı öteki telefona kopyala" açıkken
 * öteki telefona gider; orada kayda, loga ya da başka bir yüke yazılmaz. [id] tekrar atmayı ayıklar.
 */
data class DialNumber(val id: String, val number: String, val atMs: Long) {
    fun toJson(): JSONObject = JSONObject().put("schemaVersion", SCHEMA).put("id", id).put("number", number).put("atMs", atMs)

    companion object {
        const val SCHEMA = 1

        fun parse(j: JSONObject): DialNumber? {
            if (j.opt("schemaVersion") != SCHEMA) return null
            val id = (j.opt("id") as? String)?.takeIf { it.isNotEmpty() } ?: return null
            val number = (j.opt("number") as? String)?.takeIf { it.isNotBlank() && it.length <= MAX_LENGTH } ?: return null
            val at = (j.opt("atMs") as? Number)?.toLong() ?: return null
            return DialNumber(id, number, at)
        }

        const val MAX_LENGTH = 40
    }
}
