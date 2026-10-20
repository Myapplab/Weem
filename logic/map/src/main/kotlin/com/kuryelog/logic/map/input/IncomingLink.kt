package com.kuryelog.logic.map.input

import org.json.JSONObject

/**
 * Dışarıdan gelen bağlantı (MAP.md 8.2): VIEW (kurye uygulamasının navigasyon isteği) ya da SEND (paylaşılan metin).
 * [at] = bağlantının geliş anı (epoch ms), bilinmiyorsa null.
 */
data class IncomingLink(val text: String, val view: Boolean, val referrer: String?, val at: Long?) {
    companion object {
        private val pkg = Regex("[A-Za-z0-9_.]{1,100}")

        /** `{text, action: view|send|other, referrer, atMs}`; metin kırpılmış ve dolu olmalı, `referrer` paket deseninde değilse null. */
        fun parse(json: JSONObject?): IncomingLink? {
            if (json == null) return null
            val text = json.opt("text") as? String ?: return null
            if (text.isBlank()) return null
            val referrer = (json.opt("referrer") as? String)?.takeIf { pkg.matches(it) }
            val at = when (val a = json.opt("atMs")) {
                is Int -> a.toLong()
                is Long -> a
                else -> null
            }?.takeIf { it > 0 }
            return IncomingLink(text.trim(), json.opt("action") == "view", referrer, at)
        }

        /** Eski köprü yalnız metni verirdi: kırpılmış metin, `view = true`. */
        fun parseLegacy(text: String?): IncomingLink? = text?.trim()?.takeIf { it.isNotEmpty() }?.let { IncomingLink(it, true, null, null) }

        /** `android-app://<paket>[/…]` → paket; desene (`[A-Za-z0-9_.]{1,100}`) uymazsa null. */
        fun referrerPackage(referrer: String?): String? {
            if (referrer == null || !referrer.startsWith("android-app://")) return null
            return referrer.removePrefix("android-app://").substringBefore('/').takeIf { pkg.matches(it) }
        }

        /** Intent'ten yük (`MainActivity.linkOf`): eylem `android.intent.action.VIEW` → "view", `SEND` → "send", diğer → "other". */
        fun payload(text: String, action: String?, referrerPackage: String?, atMs: Long): Map<String, Any?> = mapOf(
            "text" to text,
            "action" to when (action) {
                "android.intent.action.VIEW" -> "view"
                "android.intent.action.SEND" -> "send"
                else -> "other"
            },
            "referrer" to referrerPackage?.takeIf { pkg.matches(it) },
            "atMs" to atMs,
        )

        /** Intent'ten doğrudan (Android `LinkIntake`): [view] VIEW eylemi; gönderen ve saat aynı süzgeçten. */
        fun of(text: String?, view: Boolean, referrerPackage: String?, atMs: Long): IncomingLink? =
            text?.trim()?.takeIf { it.isNotEmpty() }?.let { IncomingLink(it, view, referrerPackage?.takeIf { p -> pkg.matches(p) }, atMs.takeIf { a -> a > 0 }) }
    }
}
