package com.kuryelog.logic.orders

import com.kuryelog.logic.core.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

// A10.4a (S20, WEEM 1.1) — Siparişler → "Çevrimiçi oturumlar". Rawstats `MainActivity.renderSessions` metin ve sırasıyla (ORDERS.md 9.3, 10.3);
// fark: ekran Compose, bu sınıf yalnız metni ve sırayı üretir (Android'siz test edilir). Saat bölgesi cihazın yerel günüdür (`src.zone()`).

/** Bir çevrimiçi dönem: saat aralığı, sayaçlar ve o döneme bağlı siparişlerin satırları. */
data class SessionEntry(
    /** Aynı platformda iki çevrimiçi dönem arasındaki çevrimdışı boşluk ("Çevrimdışı 10:00 – 10:30 · 30 dk"); ilk dönemde ve açık dönemden sonra null. */
    val offline: String?,
    val range: String,
    val counters: String,
    val trips: List<String>,
)

/** Bir platformun bugünkü oturumları; [heading] "Wolt · bugün 2 kez · 3 sa 20 dk". */
data class SessionGroup(val platform: String, val tone: Tone, val heading: String, val entries: List<SessionEntry>)

/** "Çevrimiçi oturumlar" sayfası; oturum yoksa [empty] dolu ve [groups] boş, açıklama ([note]) null. */
data class SessionsPage(val title: String, val empty: String?, val groups: List<SessionGroup>, val note: String?)

/** Bugünün çevrimiçi oturumları (Wolt/Bolt ekranından kaydedilen, `online-sessions.jsonl` + açık dönemler). */
class OrdersSessions(private val src: OrdersSource, lang: Lang) {
    private val az = lang == Lang.AZ
    private fun tl(tr: String, az: String) = if (this.az) az else tr
    private val clockFormat by lazy { DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(src.zone()) }
    private fun clock(t: Long) = clockFormat.format(Instant.ofEpochMilli(t))
    private fun dur(m: Double) = OrderHistory.durationText(m, ::tl)

    val title = tl("Çevrimiçi oturumlar", "Onlayn sessiyalar")

    fun page(): SessionsPage {
        val sessions = src.onlineToday().sortedBy { it.startAt }
        if (sessions.isEmpty()) return SessionsPage(title,
            tl("Bugün çevrimiçi oturum görülmedi. Oturumlar vardiya açıkken Wolt/Bolt ekranından kaydedilir.", "Bu gün onlayn sessiya görünməyib. Sessiyalar növbə açıq olanda Wolt/Bolt ekranından qeyd olunur."),
            emptyList(), null)
        val trips = src.todayTrips()
        val groups = sessions.groupBy { Platform.name(it.pkg) }.map { (name, list) ->
            var previousEnd: Long? = null
            val entries = list.map { x ->
                // Offline between two online periods of the same platform.
                val offline = previousEnd?.takeIf { x.startAt > it }?.let { off ->
                    tl("Çevrimdışı ${clock(off)} – ${clock(x.startAt)} · ${dur((x.startAt - off) / 60000.0)}", "Oflayn ${clock(off)} – ${clock(x.startAt)} · ${dur((x.startAt - off) / 60000.0)}")
                }
                val start = (if (x.startObserved) "" else "≈") + clock(x.startAt)
                val end = if (x.endReason == "open") tl("devam ediyor", "davam edir") else (if (x.endObserved) "" else "≈") + clock(x.endAt)
                val mine = trips.filter { it.sessionId == x.id }.sortedBy { it.acceptedAt }.map { t ->
                    "${clock(t.acceptedAt)} · ${t.pickups.firstOrNull()?.restaurant ?: tl("Sipariş", "Sifariş")} · ${OrderHistory.status(t, ::tl)}"
                }
                previousEnd = if (x.endReason == "open") null else x.endAt
                SessionEntry(offline, "$start – $end  ·  ${dur(x.minutes)}",
                    tl("${x.offers} teklif görüldü · ${x.accepted} kabul · ${x.rejected} ret · ${x.delivered} teslim",
                        "${x.offers} təklif görüldü · ${x.accepted} qəbul · ${x.rejected} imtina · ${x.delivered} çatdırılma"), mine)
            }
            SessionGroup(name, Tone.of(name), tl("$name · bugün ${list.size} kez · ${dur(list.sumOf { it.minutes })}", "$name · bu gün ${list.size} dəfə · ${dur(list.sumOf { it.minutes })}"), entries)
        }
        return SessionsPage(title, null, groups,
            tl("≈ : başlangıç/bitiş ekranı görülmedi; ilk/son görülen çevrimiçi ekranın saati.", "≈ : başlanğıc/bitiş ekranı görünmədi; ilk/son görünən onlayn ekranın vaxtı."))
    }
}

/**
 * Sayfanın çizilecek kartları ([Card] modeli, [CardView] çizer): her platform bir kart (başlık, sonra her dönem: çevrimdışı boşluk, saat aralığı,
 * sayaçlar, siparişler); oturum yoksa tek açıklama kartı; sonda "≈" notu.
 */
fun SessionsPage.cards(): List<Card> {
    if (empty != null) return listOf(Card(listOf(Block.Line(empty, Style.MUTED))))
    val cards = groups.map { g ->
        val b = mutableListOf<Block>(Block.Heading(g.heading, g.tone))
        for (e in g.entries) {
            e.offline?.let { b += Block.Line(it, Style.MUTED) }
            b += Block.Line(e.range, Style.BOLD)
            b += Block.Line(e.counters, Style.MUTED)
            e.trips.forEach { b += Block.Line(it) }
            b += Block.Divider
        }
        Card(b.dropLast(1))
    }
    return if (note == null) cards else cards + Card(listOf(Block.Line(note, Style.MUTED)))
}
