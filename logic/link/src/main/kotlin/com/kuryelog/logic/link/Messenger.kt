package com.kuryelog.logic.link

import java.nio.ByteBuffer
import java.util.TreeMap
import java.util.UUID

/** `Messenger` sınırları (LINK.md 3.7–3.9). Testler kısaltabilir; telefon varsayılanları kullanır. */
class MessengerConfig(
    val latestMaxTtlMs: Long = DAY_MS,
    val reliableTtlMs: Long = DAY_MS,
    val reliableMaxTtlMs: Long = 7 * DAY_MS,
    /** Onay gelmezse yeniden gönderim: 3, 6, 12 … sn, üst sınır 60 sn. */
    val rtoMs: Long = 3_000,
    val rtoMaxMs: Long = 60_000,
    val maxInFlight: Int = 16,
    val maxOutbox: Int = 2_000,
    val clockSyncMs: Long = 300_000,
    val seenMarginMs: Long = 3_600_000,
    val maxSeen: Int = 4_096,
    val ackMaxIds: Int = 128,
    val seqBlock: Int = 256,
) {
    companion object {
        const val DAY_MS = 86_400_000L
    }
}

/** Atılan iletilerin sayacı (tanı; A8.3 Tanı ekranı). */
class MessengerStats internal constructor() {
    /** Teslim zorunlu tekrarı (`id` görülmüş). */
    var duplicates = 0
        internal set

    /** Son durumda eski ya da tekrar `seq`. */
    var stale = 0
        internal set

    /** TTL'si geçmiş son durum. */
    var expired = 0
        internal set

    /** Çözülemeyen zarf ya da onay. */
    var malformed = 0
        internal set
}

/**
 * Uygulamaya gelen ileti. [sentAtMs] gönderenin saatiyle; [localSentAtMs] saat farkıyla bu telefonun saatine çevrilmiş (3.9);
 * [expiresAtMs] son durumun yerel son geçerlilik anı (teslim zorunluda `null`). `toString` yükü yazmaz.
 */
class Incoming(
    val kind: EnvelopeKind,
    val type: String,
    val id: UUID,
    val seq: Long,
    val sentAtMs: Long,
    val localSentAtMs: Long,
    val expiresAtMs: Long?,
    payload: ByteArray,
    val transport: Transport,
) {
    private val body = payload.copyOf()
    val payload: ByteArray get() = body.copyOf()

    override fun toString() = "Incoming($kind, $type, seq=$seq, ${body.size} B, $transport)"
}

/**
 * Zarf ve teslim katmanı (LINK.md 3.6–3.9, 3.11), türden bağımsız: son durum (tür başına en yenisi, TTL; etkin kanal her değişince
 * yeniden), teslim zorunlu (kalıcı, `seq` sırası, en çok 16 onaysız, üstel yeniden gönderim, onayla silinir), alıcıda tekrar atma
 * (`id` kalıcı, `seq` bellekte), onay, saat farkı, eşleşme kaldırma iletisi.
 *
 * Saf ve tek iş parçacıklı; [port] makinenin şifreli gönderimi (`LinkStack` bağlar). Çağıran her girdiden sonra [pump] eder ve
 * [drain] ile çıktıları alır. Teslim zorunlu ileti uygulamaya verildikten sonra [delivered] çağrılmalı: `id` ancak o zaman kalıcı
 * görülenlere yazılır ve onaylanır (en az bir kez; LINK.md 3.8).
 */
class Messenger(
    private val store: MessengerStore,
    private val cfg: MessengerConfig = MessengerConfig(),
    private val ids: () -> UUID = UUID::randomUUID,
) {
    /** Şifreli gönderim (etkin kanal). */
    var port: (ByteArray) -> SendResult = { SendResult.NO_LINK }

    val stats = MessengerStats()
    val clock = ClockSync()

    private class Pending(val env: Envelope, val bytes: ByteArray) {
        /** En son hangi kanalda gönderildi. */
        var channel: Int? = null
        var attempts = 0
        var nextAtMs = 0L
        var waitWifi = false
        val expiresAtWall: Long get() = env.sentAtMs + env.ttlMs
    }

    private val latest = LinkedHashMap<String, Pending>()
    private val reliable = TreeMap<Long, Pending>()
    private val bySeqId = HashMap<UUID, Long>()
    private val seen = LinkedHashMap<UUID, Long>()
    private val handed = LinkedHashMap<UUID, Long>()
    private val lastSeq = HashMap<String, Long>()
    private val acks = ArrayList<UUID>()
    private val timeReplies = ArrayList<LongArray>()
    private val timeRequests = ArrayDeque<Long>()
    private val out = ArrayList<LinkCommand>()

    private var linked = false
    private var channel: Int? = null
    private var transport: Transport? = null
    private var lastClockAtMs: Long? = null
    private var nextSeq = 0L
    private var seqLimit = 0L
    private var lastNow = 0L
    private var lastWall = 0L

    init {
        load()
    }

    fun drain(): List<LinkCommand> {
        val r = ArrayList(out)
        out.clear()
        return r
    }

    // --- yayınlama ---

    /** Son durum: aynı türün eskisinin yerine geçer; [ttlMs] 1 sn'den 24 sa'e (yerinden düşer). */
    fun publishLatest(type: String, payload: ByteArray, ttlMs: Long, nowMs: Long, wallMs: Long): UUID {
        require(ttlMs in 1..cfg.latestMaxTtlMs) { "latest ttl" }
        clocks(nowMs, wallMs)
        val env = Envelope(EnvelopeKind.LATEST, type, ids(), allocSeq(), wallMs, ttlMs, payload)
        val bytes = env.encode()
        require(bytes.size <= MAX_PLAINTEXT) { "payload too big" }
        latest[type] = Pending(env, bytes)
        return env.id
    }

    /** Teslim zorunlu: dönmeden diske yazılır; onaya ya da TTL'ye kadar yeniden gönderilir. */
    fun publishReliable(type: String, payload: ByteArray, ttlMs: Long = cfg.reliableTtlMs, nowMs: Long, wallMs: Long): UUID {
        require(ttlMs in 1..cfg.reliableMaxTtlMs) { "reliable ttl" }
        clocks(nowMs, wallMs)
        val env = Envelope(EnvelopeKind.RELIABLE, type, ids(), allocSeq(), wallMs, ttlMs, payload)
        val bytes = env.encode()
        require(bytes.size <= MAX_PLAINTEXT) { "payload too big" }
        while (reliable.size >= cfg.maxOutbox) drop(reliable.firstEntry().value, expired = true)
        store.putOutbox(env.seq, bytes)
        reliable[env.seq] = Pending(env, bytes)
        bySeqId[env.id] = env.seq
        return env.id
    }

    /** Eşleşme kaldırılıyor (3.11): bağlıysa eşe söylenir (en iyi çaba). */
    fun unpair(nowMs: Long, wallMs: Long) {
        clocks(nowMs, wallMs)
        if (linked) port(Envelope(EnvelopeKind.CONTROL, UNPAIR, ids(), 0, wallMs, 0, Noise.EMPTY).encode())
    }

    /** Eş değişti ya da eşleşme kalktı: kuyruklar, görülenler ve saat örnekleri silinir (`seq` artmayı sürdürür). */
    fun reset() {
        latest.clear()
        reliable.clear()
        bySeqId.clear()
        seen.clear()
        handed.clear()
        lastSeq.clear()
        acks.clear()
        timeReplies.clear()
        timeRequests.clear()
        clock.reset()
        lastClockAtMs = null
        store.clear()
    }

    // --- bağlantı ---

    fun onStatus(s: LinkStatus, nowMs: Long, wallMs: Long) {
        clocks(nowMs, wallMs)
        linked = s.state == LinkStatus.State.LINKED
        val ch = if (linked) s.channel else null
        val t = if (linked) s.transport else null
        if (ch != channel) {
            channel = ch
            if (ch != null) {
                // Yeni etkin kanal: son durumların hepsi ve onaysızlar hemen yeniden; saat yeniden ölçülür.
                latest.values.forEach { it.channel = null }
                reliable.values.forEach {
                    it.channel = null
                    it.attempts = 0
                }
                lastClockAtMs = null
            }
        }
        if (t != transport) {
            transport = t
            latest.values.forEach { it.waitWifi = false }
            reliable.values.forEach { it.waitWifi = false }
        }
    }

    fun onData(bytes: ByteArray, transport: Transport, nowMs: Long, wallMs: Long) {
        clocks(nowMs, wallMs)
        val e = Envelope.decode(bytes)
        if (e == null) {
            stats.malformed++
            return
        }
        when (e.kind) {
            EnvelopeKind.LATEST -> onLatest(e, transport, wallMs)
            EnvelopeKind.RELIABLE -> onReliable(e, transport, wallMs)
            EnvelopeKind.ACK -> onAck(e.payload)
            EnvelopeKind.CONTROL -> when (e.type) {
                TIME -> onTime(e.payload, wallMs)
                UNPAIR -> out += LinkCommand.Unpaired
                else -> Unit // yeni sürümün denetimi: yok sayılır
            }
        }
    }

    private fun onLatest(e: Envelope, transport: Transport, wallMs: Long) {
        val last = lastSeq[e.type]
        if (last != null && e.seq <= last) {
            stats.stale++
            return
        }
        val local = e.sentAtMs - (clock.offsetMs ?: 0)
        val expires = local + e.ttlMs
        if (wallMs > expires) {
            stats.expired++
            return
        }
        lastSeq[e.type] = e.seq
        out += LinkCommand.Message(Incoming(EnvelopeKind.LATEST, e.type, e.id, e.seq, e.sentAtMs, local, expires, e.payload, transport))
    }

    private fun onReliable(e: Envelope, transport: Transport, wallMs: Long) {
        val keep = seen[e.id]
        if (keep != null && keep >= wallMs) {
            stats.duplicates++
            acks += e.id
            return
        }
        if (handed.containsKey(e.id)) {
            // Uygulamada işleniyor: onay [delivered]'da.
            stats.duplicates++
            return
        }
        handed[e.id] = wallMs + e.ttlMs + cfg.seenMarginMs
        val local = e.sentAtMs - (clock.offsetMs ?: 0)
        out += LinkCommand.Message(Incoming(EnvelopeKind.RELIABLE, e.type, e.id, e.seq, e.sentAtMs, local, null, e.payload, transport))
    }

    /** Verilen teslim zorunlu iletiler işlendi: `id`'ler kalıcı görülenlere, sonra onay (3.8). */
    fun delivered(nowMs: Long, wallMs: Long) {
        clocks(nowMs, wallMs)
        if (handed.isEmpty()) return
        for ((id, keep) in handed) {
            seen[id] = keep
            acks += id
        }
        handed.clear()
        trimSeen(wallMs)
        persistSeen()
    }

    private fun onAck(p: ByteArray) {
        if (p.size < 2) {
            stats.malformed++
            return
        }
        val b = ByteBuffer.wrap(p)
        val n = b.short.toInt() and 0xffff
        if (p.size != 2 + 16 * n) {
            stats.malformed++
            return
        }
        repeat(n) {
            val id = UUID(b.long, b.long)
            val seq = bySeqId[id] ?: return@repeat
            val pending = reliable[seq] ?: return@repeat
            drop(pending, expired = false)
            out += LinkCommand.Acked(pending.env.type, id)
        }
    }

    private fun onTime(p: ByteArray, wallMs: Long) {
        if (p.isEmpty()) return
        val b = ByteBuffer.wrap(p)
        when (b.get().toInt()) {
            1 -> if (p.size >= 9) timeReplies += longArrayOf(b.long, wallMs)
            2 -> if (p.size >= 25) {
                val t1 = b.long
                val t2 = b.long
                val t3 = b.long
                if (timeRequests.remove(t1) && clock.sample(t1, t2, t3, wallMs)) {
                    out += LinkCommand.Clock(clock.offsetMs!!, clock.uncertaintyMs!!)
                }
            }
        }
    }

    // --- gönderim ---

    fun pump(nowMs: Long, wallMs: Long) {
        clocks(nowMs, wallMs)
        latest.values.removeAll { wallMs > it.expiresAtWall }
        for (p in reliable.values.toList()) if (wallMs > p.expiresAtWall) drop(p, expired = true)
        val ch = channel
        if (!linked || ch == null) return
        while (acks.isNotEmpty()) {
            val batch = acks.take(cfg.ackMaxIds)
            val payload = ByteBuffer.allocate(2 + 16 * batch.size).putShort(batch.size.toShort())
            batch.forEach { payload.putLong(it.mostSignificantBits).putLong(it.leastSignificantBits) }
            if (port(Envelope(EnvelopeKind.ACK, ACK, ids(), 0, wallMs, 0, payload.array()).encode()) != SendResult.SENT) return
            acks.subList(0, batch.size).clear()
        }
        while (timeReplies.isNotEmpty()) {
            val (t1, t2) = timeReplies[0].let { it[0] to it[1] }
            val reply = ByteBuffer.allocate(25).put(2).putLong(t1).putLong(t2).putLong(wallMs).array()
            if (port(Envelope(EnvelopeKind.CONTROL, TIME, ids(), 0, wallMs, 0, reply).encode()) != SendResult.SENT) return
            timeReplies.removeAt(0)
        }
        val lastClock = lastClockAtMs
        if (lastClock == null || nowMs - lastClock >= cfg.clockSyncMs) {
            val req = ByteBuffer.allocate(9).put(1).putLong(wallMs).array()
            if (port(Envelope(EnvelopeKind.CONTROL, TIME, ids(), 0, wallMs, 0, req).encode()) != SendResult.SENT) return
            lastClockAtMs = nowMs
            timeRequests.addLast(wallMs)
            while (timeRequests.size > 4) timeRequests.removeFirst()
        }
        for (p in latest.values) {
            if (p.channel == ch || p.waitWifi) continue
            when (port(p.bytes)) {
                SendResult.SENT -> p.channel = ch
                SendResult.TOO_BIG_FOR_BT -> p.waitWifi = true
                else -> return
            }
        }
        var inFlight = reliable.values.count { it.channel == ch }
        for (p in reliable.values) {
            if (p.waitWifi) continue
            val fresh = p.channel != ch
            if (fresh && inFlight >= cfg.maxInFlight) continue
            if (!fresh && nowMs < p.nextAtMs) continue
            when (port(p.bytes)) {
                SendResult.SENT -> {
                    if (fresh) {
                        inFlight++
                        p.attempts = 0
                    }
                    p.channel = ch
                    p.attempts++
                    p.nextAtMs = nowMs + rto(p.attempts)
                }
                SendResult.TOO_BIG_FOR_BT -> p.waitWifi = true
                else -> return
            }
        }
    }

    /** Bir sonraki iş zamanı (monoton ms): yeniden gönderim, saat ölçümü, TTL. */
    fun nextWakeMs(): Long? {
        val t = ArrayList<Long>()
        val ch = channel
        if (linked && ch != null) {
            // Ölçüm kanal değişince [pump]'ta hemen yapılır; burada yalnız 5 dk'lık yenileme.
            lastClockAtMs?.let { t += it + cfg.clockSyncMs }
            for (p in reliable.values) if (p.channel == ch && !p.waitWifi) t += p.nextAtMs
        }
        // TTL duvar saatiyle; uyanma monoton saate çevrilir (geçtikten 1 ms sonra).
        for (p in latest.values) t += lastNow + (p.expiresAtWall - lastWall) + 1
        for (p in reliable.values) t += lastNow + (p.expiresAtWall - lastWall) + 1
        return t.minOrNull()?.coerceAtLeast(lastNow + 1)
    }

    // --- iç ---

    private fun rto(attempts: Int): Long {
        var r = cfg.rtoMs
        repeat(attempts - 1) { r = minOf(r * 2, cfg.rtoMaxMs) }
        return minOf(r, cfg.rtoMaxMs)
    }

    private fun drop(p: Pending, expired: Boolean) {
        reliable.remove(p.env.seq)
        bySeqId.remove(p.env.id)
        store.removeOutbox(p.env.seq)
        if (expired) out += LinkCommand.Expired(p.env.type, p.env.id)
    }

    /** `seq` 256'lık bloklarla ayrılır; blok başı diske yazılır (yeniden başlatmada da artar, 3.7). */
    private fun allocSeq(): Long {
        if (nextSeq == 0L || nextSeq >= seqLimit) {
            val start = maxOf(nextSeq, store.seqBlock(), 1L)
            seqLimit = start + cfg.seqBlock
            store.putSeqBlock(seqLimit)
            nextSeq = start
        }
        return nextSeq++
    }

    private fun clocks(nowMs: Long, wallMs: Long) {
        lastNow = nowMs
        lastWall = wallMs
    }

    private fun trimSeen(wallMs: Long) {
        seen.entries.removeAll { it.value < wallMs }
        if (seen.size > cfg.maxSeen) {
            val drop = seen.entries.sortedBy { it.value }.take(seen.size - cfg.maxSeen).map { it.key }
            drop.forEach(seen::remove)
        }
    }

    private fun persistSeen() {
        val b = ByteBuffer.allocate(SEEN_ENTRY * seen.size)
        for ((id, keep) in seen) b.putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).putLong(keep)
        store.putSeen(b.array())
    }

    private fun load() {
        for ((seq, bytes) in store.outbox()) {
            val e = Envelope.decode(bytes)
            if (e == null || e.kind != EnvelopeKind.RELIABLE || e.seq != seq) {
                store.removeOutbox(seq)
                continue
            }
            reliable[seq] = Pending(e, bytes)
            bySeqId[e.id] = seq
        }
        val s = store.seen() ?: return
        val b = ByteBuffer.wrap(s)
        while (b.remaining() >= SEEN_ENTRY) seen[UUID(b.long, b.long)] = b.long
    }

    companion object {
        const val ACK = "link.ack"
        const val TIME = "link.time"
        const val UNPAIR = "link.unpair"

        /** Şifreli `DATA`'nın düz metin sınırı (Wi-Fi, LINK.md 3.5); zarf başlığı dahil. */
        const val MAX_PLAINTEXT = LinkConfig.WIFI_MAX_PAYLOAD - Noise.TAG
        private const val SEEN_ENTRY = 24
    }
}
