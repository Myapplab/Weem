package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.geo.Baku
import com.kuryelog.logic.map.geo.GeoPoint

/** `address_parser.dart:5-33` (MAP.md 8.4): metinden çıkan hedef. [unitNote] = kapı notu ("Mənzil 45 · Mərtəbə 7 …"). */
sealed interface ParsedDestination {
    val raw: String
    val unitNote: String?

    data class Coordinate(override val raw: String, val point: GeoPoint, val label: String? = null, override val unitNote: String? = null) : ParsedDestination
    data class ShortLink(override val raw: String, val url: String, override val unitNote: String? = null) : ParsedDestination
    data class Address(override val raw: String, val query: String, val strong: Boolean, override val unitNote: String? = null) : ParsedDestination
}

/**
 * Wolt/Bolt adresi, harita bağlantısı, koordinat ya da Plus Code ayrıştırıcısı (saf; `address_parser.dart`).
 * Desenler Dart sonucunu JVM'de ve Android ICU'da aynı verecek biçimde yazılır (MAP.md 8.4 tablosu): `\s` → `[\s\p{Z}]`, `\d` → `[0-9]`,
 * `\b0` → `(?<![A-Za-z0-9_])0`. Listeler YolGo'dan harfiyen.
 */
object AddressParser {
    private const val S = "\\s\\p{Z}"
    private val I = setOf(RegexOption.IGNORE_CASE)

    private val url = Regex("(?:https?://|www\\.)[^$S<>\"']+", RegexOption.IGNORE_CASE)
    private val pair = Regex("(-?[0-9]{1,3}\\.[0-9]+)[$S]*,[$S]*(-?[0-9]{1,3}\\.[0-9]+)")

    // Çıplak metinde fiyat vb. ile karışmasın diye en az 3 ondalık.
    private val pairStrict = Regex("(-?[0-9]{1,2}\\.[0-9]{3,})[$S]*[,;][$S]*(-?[0-9]{1,3}\\.[0-9]{3,})")
    private val pin3d4d = Regex("!3d(-?[0-9]+\\.[0-9]+)!4d(-?[0-9]+\\.[0-9]+)")
    private val atPair = Regex("@(-?[0-9]+\\.[0-9]+),(-?[0-9]+\\.[0-9]+)")
    private val phone = Regex("(?:\\+?994|(?<![A-Za-z0-9_])0)[$S-]?\\(?[0-9]{2}\\)?[$S-]?[0-9]{3}[$S-]?[0-9]{2}[$S-]?[0-9]{2}")
    private val hasLetters = Regex("\\p{L}{2,}")
    private val trailingPunct = Regex("[).,;!?]+$")
    private val parenLabel = Regex("\\(([^)]+)\\)")

    /** Wolt biçimi: "Mehdi Hüseyn 99", "Təbriz 52, Баку" → sokak + numara. */
    private val streetNumber = Regex("^[$S]*\\p{L}[\\p{L}$S.'’-]{2,40}?[$S][0-9]{1,4}(?:[/-][0-9]{1,4})?[$S]?\\p{L}?[$S]*(?:,|$)")

    private fun kw(alt: String) = Regex("(?<!\\p{L})(?:$alt)(?!\\p{L})", I)
    private fun r(p: String) = Regex("(?<!\\p{L})(?:$p)", I)

    private val streetWord = kw(
        "küçəsi|küçə|küç\\.?|kuc\\.?|kucesi|prospekti|prospekt|pr\\.|prosp\\.?|" +
            "şossesi|şosse|şos\\.|döngəsi|döngə|dalanı|dalan|məhəlləsi|məhəllə|" +
            "mikrorayonu|mikrorayon|mkr\\.?|m/r|qəsəbəsi|qəsəbə|qesebe|bulvarı|bulvar|" +
            "meydanı|meydan|yaşayış kompleksi|kompleksi|kompleks|residence|plaza|" +
            "towers?|bina|ул\\.?|улица|пр\\.|проспект|пр-т|шоссе|переулок|мкр\\.?|" +
            "street|str\\.|st\\.|avenue|ave\\.?|road|blvd|district|" +
            "sokak|sok\\.|caddesi|cadde|mahallesi|mahalle",
    )

    private val district = kw(
        "bakı|baki|baku|баку|nəsimi|nesimi|yasamal|səbail|sebail|sabail|nərimanov|" +
            "nerimanov|nizami|xətai|xetai|khatai|binəqədi|bineqedi|sabunçu|sabuncu|" +
            "suraxanı|suraxani|qaradağ|xəzər|xezer|badamdar|bayıl|bayil|" +
            "içərişəhər|icerisheher|gənclik|genclik|əhmədli|ehmedli|həzi aslanov|" +
            "neftçilər|neftciler|azadlıq|elmlər|memar əcəmi|koroğlu|qara qarayev|" +
            "ulduz|bakmil|dərnəgül|20 yanvar|28 may|sahil|inşaatçılar|" +
            "xalqlar dostluğu|8 noyabr|bakıxanov|günəşli|lökbatan|masazır|" +
            "xırdalan|mərdəkan|bilgəh|buzovna|sumqayıt|abşeron|fəvvarələr|tarqovu|" +
            "ağ şəhər|white city|port baku|montin|əmircan|qaraçuxur|keşlə|biləcəri|" +
            "az[0-9]{4}",
    )

    private val city = kw("bakı|baki|baku|баку|sumqayıt|xırdalan|abşeron")

    // Kapı detayları geokodlayıcıyı şaşırtır → ayıkla, nota yaz.
    private val unitRules: List<Pair<String, Regex>> = listOf(
        "Mənzil" to r("(?:mənzil|menzil|mənz\\.|kv\\.?|кв\\.?|квартира|apt\\.?|apartment|daire|flat)[$S]*[:№#]?[$S]*([0-9]+[a-zа-я]?)"),
        "Mərtəbə" to r("(?:mərtəbə|mertebe|этаж|floor|kat)[$S]*[:№#]?[$S]*([0-9]+)"),
        "Mərtəbə" to r("([0-9]+)[$S]*-?[$S]*(?:ci|cı|cu|cü|nci|ncı|ncu|ncü|ый|ий|й)?[$S]*(?:mərtəbə|mertebe|этаж|kat|floor)"),
        "Giriş" to r("(?:giriş|giris|подъезд|под\\.|entrance|blok|block)[$S]*[:№#]?[$S]*([0-9]+[a-zа-я]?)"),
        "Kod" to r("(?:domofon|kod|код|code|intercom)[$S]*[:№#]?[$S]*([0-9#*]{2,})"),
    )

    private val labels = r("(?:ünvan|unvan|address|adres|адрес)[$S]*:")

    private val abbr: List<Pair<Regex, String>> = listOf(
        r("küç\\.|kuc\\.") to "küçəsi",
        r("pr\\.|prosp\\.") to "prospekti",
        r("şos\\.") to "şossesi",
        r("məh\\.") to "məhəlləsi",
        r("mkr\\.|m/r") to "mikrorayon",
    )

    private val parens = Regex("\\([^)]*\\)")
    private val lineBreaks = Regex("[\\r\\n\\t]+")
    private val lineSplit = Regex("[\\r\\n]+")
    private val spaces = Regex("[$S]+")
    private val commas = Regex("(?:[$S]*,[$S]*)+")
    private val edges = Regex("^[$S,.;:–-]+|[$S,.;:–-]+$")

    /** Wolt/Bolt "navigasyon uygulaması" seçimlerinin açtığı şemalar. */
    private val appSchemes = setOf("waze", "yandexnavi", "yandexmaps", "dgis", "here-route", "here-location", "here.directions", "comgooglemaps")

    /** Kullanıcının bilerek yazdığı mekân adı panodaki rastgele metinden farklıdır: `parse` sonuç vermezse 2–250 karakterlik, ≥2 harfli, URL'siz metin. */
    fun parseExplicit(input: String): ParsedDestination? {
        val raw = input.trim()
        parse(raw)?.let { return it }
        if (raw.length < 2 || raw.length > 250 || !hasLetters.containsMatchIn(raw) || url.containsMatchIn(raw)) return null
        return ParsedDestination.Address(raw, raw, strong = true)
    }

    /** Adres/konum değilse null (sessizce yok sayılır): uzunluk 5–800 dışı, adres olmayan metin, harita dışı bağlantı. */
    fun parse(input: String): ParsedDestination? {
        val raw = input.trim()
        if (raw.length < 5 || raw.length > 800) return null

        // 0) Harita uygulaması şemaları (Wolt/Bolt harita düğmesi)
        parseNavUri(raw)?.let { return it }

        // 1) Linkler: WhatsApp konumu, Google / Yandex / Waze / OSM
        for (m in url.findAll(raw)) {
            var s = m.value.replace(trailingPunct, "")
            if (!s.lowercase().startsWith("http")) s = "https://$s"
            val uri = LinkUri.tryParse(s) ?: continue
            coordsFromUri(uri)?.let { return ParsedDestination.Coordinate(raw, it) }
            if (isShortMapLink(uri)) return ParsedDestination.ShortLink(raw, uri.toString())
            val q = textQueryFromUri(uri)
            if (q != null) return parse(q) ?: ParsedDestination.Address(raw, withCity(q), strong = true)
        }

        // 2) Plus Code (Bolt restoranları): "9RQW+344 Baku"
        val pc = PlusCode.pattern.find(raw)
        if (pc != null && PlusCode.looksReal(pc.groupValues[1])) {
            val p = PlusCode.recover(pc.groupValues[1], Baku.center)
            // Etiket: kodun satırı, koddan sonrası ("9RQW+344 Baku, …"). Öncesi ve öteki satırlar (ad, telefon, not) öteki telefona gitmesin (A10.2, S5).
            if (p != null && p.isInAzerbaijan) return ParsedDestination.Coordinate(raw, p, label = raw.substring(pc.range.first).lineSequence().first())
        }

        // 3) Çıplak koordinat
        pairStrict.find(raw)?.let { bare ->
            orient(bare.groupValues[1], bare.groupValues[2])?.let { return ParsedDestination.Coordinate(raw, it) }
        }

        // 4) Serbest adres metni
        if (url.containsMatchIn(raw)) return null // harita dışı link (Instagram vb.)
        val (rest, note) = extractUnit(raw)
        val query = clean(addressLines(rest))
        if (!hasLetters.containsMatchIn(query) || query.length < 4 || query.length > 250) return null
        // Yalnız adres gibi görünenler: sokak türü, semt adı ya da "Sokak 12" kalıbı (sohbet mesajı, sipariş kodu rota çizmesin).
        if (!looksLikeAddress(query)) return null
        return ParsedDestination.Address(raw, withCity(query), strong = true, unitNote = note)
    }

    /** `geo:40.37,49.83?q=…` | `google.navigation:q=…` | uygulama şemaları. */
    private fun parseNavUri(raw: String): ParsedDestination? {
        val lower = raw.lowercase()
        val colon = lower.indexOf(':')
        if (colon > 0 && lower.substring(0, colon) in appSchemes) return parseAppScheme(raw)
        if (!lower.startsWith("geo:") && !lower.startsWith("google.navigation:")) return null
        val body = raw.substring(raw.indexOf(':') + 1)
        val qi = body.indexOf('?')
        val path = if (qi >= 0) body.substring(0, qi) else body
        val query = if (qi >= 0) body.substring(qi + 1) else if (lower.startsWith("google")) body else ""
        val params = try { LinkUri.splitQueryString(query) } catch (_: IllegalArgumentException) { emptyMap() }
        val q = params["q"] ?: params["daddr"] ?: params["ll"]
        if (q != null) {
            pair.find(q)?.let { m ->
                orient(m.groupValues[1], m.groupValues[2])?.let { p ->
                    return ParsedDestination.Coordinate(raw, p, label = parenLabel.find(q)?.groupValues?.get(1))
                }
            }
            if (q.trim().length >= 4) return parse(q) ?: ParsedDestination.Address(raw, withCity(q.trim()), strong = true)
        }
        pair.find(path)?.let { m ->
            val p = orient(m.groupValues[1], m.groupValues[2])
            if (p != null && (p.lat != 0.0 || p.lon != 0.0)) return ParsedDestination.Coordinate(raw, p)
        }
        return null
    }

    /**
     * `waze://?ll=…` | `yandexnavi://build_route_on_map?lat_to=…&lon_to=…` | `yandexmaps://…?rtext=~lat,lon` |
     * `dgis://2gis.ru/routeSearch/…/to/lon,lat` | `here-route://mylocation/lat,lon,Ad` | `comgooglemaps://?daddr=…`
     */
    private fun parseAppScheme(raw: String): ParsedDestination? {
        val uri = LinkUri.tryParse(raw)
        val qp = try { uri?.queryParameters ?: emptyMap() } catch (_: IllegalArgumentException) { emptyMap() }
        var p: GeoPoint? = null
        val latTo = qp["lat_to"]
        val lonTo = qp["lon_to"]
        if (latTo != null && lonTo != null) p = orient(latTo, lonTo)
        if (p == null) p = lastPair(qp["rtext"]?.split('~')?.last()) // Yandex: başlangıç~hedef
        for (k in listOf("ll", "daddr", "q", "destination", "to", "pt")) if (p == null) p = lastPair(qp[k])
        if (p == null) p = lastPair(uri?.path) // 2GIS / HERE: koordinat yolda, son çift hedef
        if (p == null) p = lastPair(raw)
        if (p != null) return ParsedDestination.Coordinate(raw, p)
        val text = (qp["q"] ?: qp["text"] ?: qp["daddr"])?.trim()
        if (text != null && text.length >= 4) return parse(text) ?: ParsedDestination.Address(raw, withCity(text), strong = true)
        return null
    }

    /** Metindeki son (Azerbaycan içi) koordinat çifti: rotalarda son nokta hedeftir. */
    private fun lastPair(s: String?): GeoPoint? {
        if (s.isNullOrEmpty()) return null
        var found: GeoPoint? = null
        for (m in pair.findAll(s)) found = orient(m.groupValues[1], m.groupValues[2]) ?: found
        return found
    }

    /** Google yol tarifi (`/maps/dir/A/B/…`) durakları; `@` görünüm ve `data=` hariç. */
    private fun waypoints(uri: LinkUri): List<String> {
        val segs = uri.pathSegments.filter { it.isNotEmpty() && !it.startsWith("@") && !it.startsWith("data=") }
        val i = segs.indexOf("dir")
        return if (i < 0) emptyList() else segs.subList(i + 1, segs.size)
    }

    /** Linkten koordinat. Öncelik: gerçek pin (`!3d!4d`) > sorgu parametresi > yol > `@` görünüm merkezi. Bozuk `%` kodlaması → null. */
    fun coordsFromUri(uri: LinkUri): GeoPoint? = try {
        coordsFromUriOrThrow(uri)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun coordsFromUriOrThrow(uri: LinkUri): GeoPoint? {
        val s = LinkUri.decodeFull(uri.toString())
        pin3d4d.find(s)?.let { pin -> orient(pin.groupValues[1], pin.groupValues[2])?.let { return it } }
        val qp = uri.queryParameters
        val mlat = qp["mlat"]
        val mlon = qp["mlon"] // OSM
        if (mlat != null && mlon != null) orient(mlat, mlon)?.let { return it }
        // Yandex rota: rtext=başlangıç~hedef | lat_to/lon_to
        lastPair(qp["rtext"]?.split('~')?.last())?.let { return it }
        val latTo = qp["lat_to"]
        val lonTo = qp["lon_to"]
        if (latTo != null && lonTo != null) orient(latTo, lonTo)?.let { return it }
        // WhatsApp: q= | Waze: ll= | Yandex: pt=/ll= (boylam,enlem; orient çevirir)
        for (key in listOf("q", "query", "destination", "daddr", "ll", "pt", "center", "sll")) {
            val v = qp[key] ?: continue
            val m = pair.find(v) ?: continue
            orient(m.groupValues[1], m.groupValues[2])?.let { return it }
        }
        // Google yol tarifi: hedef SON duraktır. Metinse geokodlanır; başlangıç ya da @görünüm merkezi hedef sanılmaz.
        if ("dir" in uri.pathSegments) {
            val wp = waypoints(uri)
            return if (wp.isEmpty()) null else lastPair(wp.last())
        }
        // 2GIS (/to/boylam,enlem) · HERE (/r/enlem,boylam): yoldaki son çift
        for (seg in uri.pathSegments.asReversed()) {
            if (seg.startsWith("@")) continue
            lastPair(seg)?.let { return it }
        }
        return atPair.find(s)?.let { orient(it.groupValues[1], it.groupValues[2]) }
    }

    fun isShortMapLink(u: LinkUri): Boolean {
        val h = u.host
        return h == "maps.app.goo.gl" || (h == "goo.gl" && u.path.startsWith("/maps")) || h == "g.co" || (h.contains("yandex") && u.path.contains("/-/"))
    }

    /** `google.com/maps?q=Nizami+küç...` ya da `/maps/place/<ad>/...`; yalnız Google, Waze, Yandex. */
    fun textQueryFromUri(u: LinkUri): String? {
        val host = u.host
        val allowed = host == "waze.com" || host.endsWith(".waze.com") ||
            host in setOf("yandex.az", "yandex.ru", "yandex.com", "maps.yandex.ru", "maps.yandex.com") ||
            host == "google.com" || host.endsWith(".google.com")
        if (!allowed) return null
        return try {
            val qp = u.queryParameters
            for (k in listOf("q", "query", "destination", "daddr", "text")) {
                val v = qp[k]?.trim()
                if (v != null && v.length >= 4 && !v.startsWith("loc:")) return v
            }
            val wp = waypoints(u)
            if (wp.isNotEmpty()) return wp.last().replace('+', ' ')
            val segs = u.pathSegments
            val i = segs.indexOf("place")
            if (i >= 0 && i + 1 < segs.size) segs[i + 1].replace('+', ' ') else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Azerbaycan dışını reddeder; Yandex/2GIS'in (boylam, enlem) sırasını çevirir. */
    private fun orient(a: String, b: String): GeoPoint? {
        val x = a.toDoubleOrNull() ?: return null
        val y = b.toDoubleOrNull() ?: return null
        val p = GeoPoint(x, y)
        if (p.isInAzerbaijan) return p
        val swapped = GeoPoint(y, x)
        return if (swapped.isInAzerbaijan) swapped else null
    }

    private fun extractUnit(text: String): Pair<String, String?> {
        var rest = text.replace("İ", "i") // YolGo'da Unicode harf eşlemesi İ'yi eşlemiyordu; aynı sonuç için korunur
        val parts = LinkedHashMap<String, String>()
        for ((label, rule) in unitRules) {
            rest = rule.replace(rest) { m ->
                parts.getOrPut(label) { m.groupValues[1] }
                " "
            }
        }
        val note = if (parts.isEmpty()) null else parts.entries.joinToString(" · ") { "${it.key} ${it.value}" }
        return rest to note
    }

    /**
     * A10.2: çok satırlı metinde (mesajdan kopyalanan) yalnız adres gibi görünen satırlar kalır; ad ve not satırları geokod sorgusuna
     * (Google, ORS, Nominatim) ve etikete girmez. Hiçbir satır tek başına adres değilse metin olduğu gibi kalır.
     */
    private fun addressLines(text: String): String {
        val lines = text.split(lineSplit).filter { it.isNotBlank() }
        if (lines.size < 2) return text
        val kept = lines.filter { looksLikeAddress(clean(it)) }
        return if (kept.isEmpty()) text else kept.joinToString("\n")
    }

    private fun looksLikeAddress(q: String) = streetWord.containsMatchIn(q) || district.containsMatchIn(q) || streetNumber.containsMatchIn(q)

    private fun clean(s: String): String {
        var t = s.replace(phone, " ").replace(labels, " ").replace(parens, " ").replace(lineBreaks, ", ")
        for ((re, rep) in abbr) t = t.replace(re, rep)
        t = t.replace(spaces, " ").replace(commas, ", ")
        return t.replace(edges, "")
    }

    private fun withCity(q: String) = if (city.containsMatchIn(q)) q else "$q, Bakı"
}
