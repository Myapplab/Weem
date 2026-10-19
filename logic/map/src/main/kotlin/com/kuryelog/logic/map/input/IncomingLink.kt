package com.kuryelog.logic.map.input

import org.json.JSONObject

/**
 * Dışarıdan gelen bağlantı (MAP.md 8.2): VIEW (kurye uygulamasının navigasyon isteği) ya da SEND (paylaşılan metin).
 * [at] = bağlantının geliş anı (epoch ms), bilinmiyorsa null.
 */
data class IncomingLink(val text: String, val view: Boolean, val referrer: String?, val at: Long?) {
    companion object {
        /** `{text, action: view|send|other, referrer, atMs}`; metin kırpılmış ve dolu olmalı, `referrer` paket deseninde değilse null. */
        fun parse(json: JSONObject?): IncomingLink? = TODO("A6.6")

        /** Eski köprü yalnız metni verirdi: kırpılmış metin, `view = true`. */
        fun parseLegacy(text: String?): IncomingLink? = TODO("A6.6")

        /** `android-app://<paket>[/…]` → paket; desene (`[A-Za-z0-9_.]{1,100}`) uymazsa null. */
        fun referrerPackage(referrer: String?): String? = TODO("A6.6")

        /** Intent'ten yük (`MainActivity.linkOf`): eylem `android.intent.action.VIEW` → "view", `SEND` → "send", diğer → "other". */
        fun payload(text: String, action: String?, referrerPackage: String?, atMs: Long): Map<String, Any> = TODO("A6.6")
    }
}
