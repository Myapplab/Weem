package com.kuryelog.logic.map.input

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Dart `Uri`'nin ayrıştırıcının kullandığı kadarı (MAP.md 8.4 sonu). `java.net.URI` boşluk, `|`, `{` gibi karakterleri reddettiği için
 * kullanılmaz. [host] küçük harf; [path] ham; [pathSegments] `%`-çözülmüş (`+`'ya dokunmaz); [queryParameters] `+` → boşluk, `%XX` UTF-8,
 * aynı anahtarda son değer. Bozuk kodlama [queryParameters]/[pathSegments]/[decodeFull]'da `IllegalArgumentException` fırlatır.
 */
class LinkUri private constructor(
    val scheme: String,
    val host: String,
    val path: String,
    val query: String,
    private val raw: String,
) {
    val pathSegments: List<String>
        get() {
            val p = if (path.startsWith("/")) path.substring(1) else path
            return if (p.isEmpty()) emptyList() else p.split('/').map { decode(it, plusToSpace = false) }
        }

    val queryParameters: Map<String, String>
        get() = splitQueryString(query)

    override fun toString() = raw

    companion object {
        private val SHAPE = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#.*)?$", RegexOption.DOT_MATCHES_ALL)

        /** Dart `Uri.tryParse`: şemasız ya da bozuk kapılı (port) metin null. */
        fun tryParse(s: String): LinkUri? {
            val m = SHAPE.matchEntire(s) ?: return null
            val authority = m.groupValues[2]
            val hostPort = authority.substringAfterLast('@')
            val colon = hostPort.lastIndexOf(':')
            val host = if (colon >= 0) {
                val port = hostPort.substring(colon + 1)
                if (port.any { !it.isDigit() }) return null
                hostPort.substring(0, colon)
            } else {
                hostPort
            }
            return LinkUri(m.groupValues[1].lowercase(), host.lowercase(), m.groupValues[3], m.groupValues[4], s)
        }

        /** Dart `Uri.splitQueryString`: `&` ile böl, `=`'siz öğe boş değerli, `=` ile başlayan atlanır; son değer kazanır. */
        fun splitQueryString(query: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (e in query.split('&')) {
                val i = e.indexOf('=')
                if (i == -1) {
                    if (e.isNotEmpty()) out[decode(e, plusToSpace = true)] = ""
                } else if (i != 0) {
                    out[decode(e.substring(0, i), plusToSpace = true)] = decode(e.substring(i + 1), plusToSpace = true)
                }
            }
            return out
        }

        /** Dart `Uri.decodeFull`: bütün `%XX` çözülür, `+` kalır. */
        fun decodeFull(s: String): String = decode(s, plusToSpace = false)

        private fun decode(s: String, plusToSpace: Boolean): String {
            if (s.indexOf('%') < 0 && !(plusToSpace && s.indexOf('+') >= 0)) return s
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '%' -> {
                        val bytes = ByteArrayOutputStream()
                        while (i < s.length && s[i] == '%') {
                            require(i + 2 < s.length) { "Truncated URI" }
                            val hi = Character.digit(s[i + 1], 16)
                            val lo = Character.digit(s[i + 2], 16)
                            require(hi >= 0 && lo >= 0) { "Illegal percent encoding in URI" }
                            bytes.write(hi * 16 + lo)
                            i += 3
                        }
                        sb.append(utf8Strict(bytes.toByteArray()))
                    }
                    c == '+' && plusToSpace -> { sb.append(' '); i++ }
                    else -> { sb.append(c); i++ }
                }
            }
            return sb.toString()
        }

        private fun utf8Strict(b: ByteArray): String = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Invalid UTF-8 in URI", e)
        }
    }
}
