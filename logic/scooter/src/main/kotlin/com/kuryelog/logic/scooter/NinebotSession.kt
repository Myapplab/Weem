package com.kuryelog.logic.scooter

import java.util.Collections
import java.util.Locale

/**
 * Single-threaded, bounded authentication/read state machine. No vehicle settings. (SCOOTER.md 2.4–2.6, 9.1–9.2; NB
 * `NinebotSession.java`, line by line.) PRE_COMM → (PAIR →) AUTH → READING with one outstanding request and a 1.2 s reply window;
 * `fail` stays connected without reading, `Port.fault` asks the link to reconnect (SCOOTER.md 5.1).
 *
 * Constructor defaults are the original overloads: [mode] `GEN2_FW`, [window] 300 s, [clock] wall time, no sweep, and without
 * [newKey] the reference key derivation (fixtures only; the app passes `SessionKey::random`).
 */
class NinebotSession(
    private val port: Port,
    deviceName: String?,
    key: ByteArray?,
    private val pair: Boolean,
    now: Long,
    private val mode: NinebotMode = NinebotMode.GEN2_FW,
    window: Long = PAIR_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    sweep: NinebotMetrics.Sweep? = null,
    private val newKey: (() -> ByteArray?)? = null,
) {
    interface Port {
        fun available(profile: Int): Boolean
        fun send(profile: Int, wire: ByteArray)
        fun state(state: String)
        fun note(kind: String, detail: String)
        fun fault(reason: String)
        fun key(key: ByteArray, confirmed: Boolean)
        fun reading(value: NinebotMetrics.Value, bytes: ByteArray)
        fun pairPrompt(visible: Boolean) {}

        /** Per-packet diagnostics: the detail text is built only when the recorder keeps it (detailed mode). */
        fun frequentNote(kind: String, detail: () -> String) {
            note(kind, detail())
        }

        fun authExhausted() {}

        /** A probed board answered as a battery pack: plausible voltage and a percent reading. Persist it for later connections. */
        fun batteryFound(board: Int) {}

        /** The opt-in external pack probe ended, with or without a pack. */
        fun batteryProbeDone(found: Boolean) {}
    }

    enum class Stage { PRE_COMM, NEEDS_KEY, PAIR, AUTH, READING, FAILED, CLOSED }

    private val name: String = deviceName.orEmpty()
    private var sessionKey: ByteArray? = key?.clone()
    private val pairWindowMs: Long = maxOf(0L, minOf(window, PAIR_WINDOW_MS))
    private lateinit var crypto: NinebotCrypto
    var stage = Stage.PRE_COMM
        private set
    private var preAttempts = 0
    private var authAttempts = 0
    private var deadline = 0L
    private var due = 0L
    private val pairUntil: Long = now + pairWindowMs
    private var serial: ByteArray? = null
    private var challenge: ByteArray? = null
    val metrics = NinebotMetrics()
    private val reads: MutableList<NinebotMetrics.Read> = NinebotMetrics.vehicle()
    private var pairReplySeen = false
    private var payloadAuthTried = false
    private var authTrigger = "stored_key"
    private val detectedBms = HashSet<Int>()
    private var pending: NinebotMetrics.Read? = null
    private var fastPos = 0
    private var slowPos = 0
    private var rarePos = 0
    private var sweepPos = 0
    private var passTail = 0
    private var passCount = 0
    private var misses = 0
    private val answered = HashSet<NinebotMetrics.Read>()
    private val unanswered = HashMap<NinebotMetrics.Read, Int>()
    private val fastReads = ArrayList<NinebotMetrics.Read>()
    private val slowReads = ArrayList<NinebotMetrics.Read>()
    private val rareReads = ArrayList<NinebotMetrics.Read>()
    private val sweepReads = ArrayList<NinebotMetrics.Read>()
    private var speedRead: NinebotMetrics.Read? = null
    private var scheduleDirty = true
    private var lastSpeedRead = Long.MIN_VALUE
    private var sinceSpeed = 0
    private var readGapMs = VISIBLE_GAP_MS
    private var speedEveryMs = VISIBLE_SPEED_MS
    private val probeReads = HashSet<NinebotMetrics.Read>()
    private val probeLane = ArrayList<NinebotMetrics.Read>()
    private var probePos = 0
    var probing = false
        private set
    private val probeCandidates = LinkedHashSet<Int>()
    private val probeFound = LinkedHashSet<Int>()
    private val lostStreak = HashMap<Int, Int>()
    private val quietNext = LinkedHashMap<Int, Long>()

    /** External packs read only sparsely right now (unknown values), for the panel and tests. A live, read-only view. */
    val quietPacks: Set<Int> = Collections.unmodifiableSet(quietNext.keys)

    /** Only Nordic UART "profile 0" exists. */
    val profile: Int get() = 0

    // Last on purpose: Java runs every field initializer before the constructor body; here every property above is set first.
    init {
        for (b in intArrayOf(7, 0x22, 0x23)) reads.add(NinebotMetrics.voltage(b))
        if (sweep != null) reads.addAll(NinebotMetrics.sweep(sweep))
        if (name.isEmpty()) {
            fail("Bluetooth cihaz adı okunamadı; GPS kaydı sürüyor")
        } else {
            port.note(
                "handshake_started",
                "profile=0; mode=${mode.setting}; sync=5aa5; pair_requested=$pair; local_key=${sessionKey != null}; " +
                    "timeout_origin=notifications_ready; pair_window_ms=$pairWindowMs; " +
                    "sweep=${if (sweep == null) "off" else "${sweep.board}:${sweep.from}-${sweep.to}"}",
            )
            pre(now)
        }
    }

    /**
     * Reads external packs found on earlier connections and, when asked, probes candidate boards once for the external pack:
     * one voltage read per pass after the slow and sweep reads, at most PROBE_TRIES timeouts per board. A plausible voltage
     * makes the board a candidate (its pack registers join the normal reads); its percent register confirms it. Read-only:
     * only command 1 is sent. Call before AUTH completes.
     */
    fun batteryBoards(known: IntArray, probe: IntArray) {
        if (known.isEmpty() && probe.isEmpty()) return
        val present = HashSet<Int>()
        for (r in reads) if (r.key.endsWith("_voltage_v")) present.add(r.board)
        val knownText = StringBuilder()
        val probeText = StringBuilder()
        // Known packs go right after the internal pack's voltage, ahead of the always-silent 0x22/0x23 on a G3.
        var at = 0
        for (i in reads.indices) if (reads[i].board == NinebotMetrics.PRIMARY_BMS && reads[i].key.endsWith("_voltage_v")) at = i + 1
        for (b in known) if (b != 0x16 && b != 4 && present.add(b)) {
            reads.add(at++, NinebotMetrics.voltage(b))
            knownText.append(if (knownText.isNotEmpty()) "," else "").append(String.format(Locale.ROOT, "%02x", b))
        }
        for (b in probe) if (b != 0x16 && b != 4 && present.add(b)) {
            val r = NinebotMetrics.voltage(b)
            reads.add(r)
            probeReads.add(r)
            probeText.append(if (probeText.isNotEmpty()) "," else "").append(String.format(Locale.ROOT, "%02x", b))
        }
        probing = probeReads.isNotEmpty()
        scheduleDirty = true
        port.note("battery_boards", "known=$knownText; probe=$probeText; tries=$PROBE_TRIES")
        if (probe.isNotEmpty() && !probing) port.batteryProbeDone(false)
    }

    private fun fail(message: String) {
        stage = Stage.FAILED
        port.pairPrompt(false)
        port.state(message)
        port.note("protocol_state", message)
    }

    private fun pre(now: Long) {
        if (!port.available(0) || preAttempts >= 3) {
            fail("G3 başlangıç yanıtı yok; yalnız Nordic profile 0 denendi. Bağlantı ZIP'ini gönder")
            return
        }
        preAttempts++
        crypto = NinebotCrypto(name.toByteArray(Charsets.UTF_8), NinebotCrypto.FW, true)
        stage = Stage.PRE_COMM
        port.note("pre_comm_attempt", "profile=0; mode=${mode.setting}; attempt=$preAttempts")
        send(4, 0x5b, 0, ByteArray(0))
        deadline = now + 5000
        port.state("G3 şifreli bağlantısı hazırlanıyor • ${mode.setting}")
    }

    // Read-only guard: only read-register (command 1) and the standard pairing/handshake frames
    // (board 4, 0x5b/0x5c/0x5d) may ever be transmitted. Any setting/control/write command is
    // rejected here. Explicit PAIR (0x5c) does change its stored pairing credential; READ does not.
    private fun send(board: Int, command: Int, index: Int, payload: ByteArray) {
        if (command != 1 && !(board == 4 && (command == 0x5b || command == 0x5c || command == 0x5d))) {
            throw IllegalArgumentException("read/handshake commands only")
        }
        val wire = crypto.encrypt(NinebotFrames.request(board, command, index, payload))
        val at = stage
        val detail = {
            "profile=0; mode=${mode.setting}; stage=$at; board=$board; command=$command; index=$index; bytes=${wire.size}; " +
                "counter=${counter(wire)}; wire_hex=${Timing.hex(wire)}"
        }
        if (at == Stage.READING) port.frequentNote("protocol_tx", detail) else port.note("protocol_tx", detail())
        port.send(0, wire)
    }

    private fun rejected(raw: ByteArray, reason: String?) {
        port.note(
            "protocol_rejected",
            "profile=0; mode=${mode.setting}; stage=$stage; reason=$reason; bytes=${raw.size}; counter=${counter(raw)}; wire_hex=${Timing.hex(raw)}",
        )
    }

    private fun auth(now: Long) {
        stage = Stage.AUTH
        crypto.setKey(sessionKey!!, challenge!!.copyOf(16))
        authAttempts++
        send(4, 0x5d, 0, serial!!)
        deadline = now + 5000
        port.state("G3 kimlik doğrulanıyor • deneme $authAttempts/3")
    }

    private fun pairAuth(now: Long, trigger: String, index: Int, length: Int) {
        authTrigger = trigger
        authAttempts = 0
        port.note(
            "pair_to_auth",
            "reason=$trigger; index=$index; payload_length=$length; experimental=${trigger == "nonempty_payload"}; " +
                "pairing_confirmed=false; remaining_seconds=${remaining(now)}",
        )
        auth(now)
    }

    private fun remaining(now: Long): Long = maxOf(0L, (pairUntil - now) / 1000)

    private fun expired(now: Long): Boolean {
        if (pair && now >= pairUntil) {
            port.note("pair_window_expired", "deadline_mono_ms=$pairUntil; remaining_seconds=0")
            fail("Scooter eşleştirme süresi doldu (300 sn pencere); GPS kaydı sürüyor. Kaydı durdurup ZIP'i gönder")
            return true
        }
        return false
    }

    private fun recoverPair(now: Long, reason: String) {
        port.note("auth_failed", "reason=$reason; trigger=$authTrigger; existing_key_preserved=true; remaining_seconds=${remaining(now)}")
        if (pair && !expired(now)) {
            stage = Stage.PAIR
            crypto.setKey(name.toByteArray(Charsets.UTF_8), challenge)
            authAttempts = 0
            port.note("pair_resumed", "reason=$reason; same_candidate=true; deadline_mono_ms=$pairUntil; remaining_seconds=${remaining(now)}")
            send(4, 0x5c, 0, sessionKey!!)
            deadline = now + 2000
            port.state("AUTH kabul edilmedi • eşleştirme onayı bekleniyor (${remaining(now)} sn)")
        } else if (stage != Stage.FAILED) {
            port.authExhausted()
            fail("G3 doğrulaması başarısız; mevcut anahtar korunuyor. GPS kaydı sürüyor")
        }
    }

    private fun pairReply(p: ByteArray, raw: ByteArray, now: Long): Boolean {
        if ((p[5].toInt() and 255) != 0x5c) return false
        val src = p[3].toInt() and 255
        val dst = p[4].toInt() and 255
        val index = p[6].toInt() and 255
        val length = p[2].toInt() and 255
        val payload = p.copyOfRange(7, p.size)
        val route = NinebotFrames.reply(p, 4, 0x5c, index, length)
        val status = when {
            !route -> "invalid_source_or_length"
            stage != Stage.PAIR -> "unexpected_stage"
            payload.isNotEmpty() -> "nonempty_unconfirmed"
            index == 1 -> "accepted"
            index == 0 -> "waiting"
            else -> "unknown_status"
        }
        port.note(
            "pair_reply",
            "src=$src; src_hex=${hex2(src)}; dst=$dst; dst_hex=${hex2(dst)}; cmd=92; cmd_hex=0x5c; index=$index; index_hex=${hex2(index)}; " +
                "length=$length; payload_length=${payload.size}; payload_hex=${Timing.hex(payload)}; wire_length=${raw.size}; " +
                "counter=${counter(raw)}; stage=$stage; status=$status; mac_valid=true; remaining_seconds=${remaining(now)}",
        )
        if (!route || stage != Stage.PAIR) {
            rejected(raw, if (!route) "PAIR source/destination/length" else "PAIR received outside PAIR stage")
            return true
        }
        if (expired(now)) return true
        if (!pairReplySeen) {
            pairReplySeen = true
            port.note("pair_first_reply", "remaining_seconds=${remaining(now)}; prompt=ŞİMDİ güç düğmesine bir kez kısa bas")
            port.pairPrompt(true)
        }
        if (payload.isNotEmpty() && !payloadAuthTried) {
            payloadAuthTried = true
            pairAuth(now, "nonempty_payload", index, payload.size)
        } else if (index == 1) {
            pairAuth(now, "index_1", index, payload.size)
        } else if (payload.isNotEmpty()) {
            port.state("PAIR veri yanıtının anlamı bilinmiyor • onay bekleniyor (${remaining(now)} sn)")
        } else if (index == 0) {
            port.state("Scooter yanıt verdi • eşleştirme onayı bekleniyor (${remaining(now)} sn). Güç düğmesine bir kez kısa bas")
        } else {
            rejected(raw, "unknown SET_PWD status $index; not a documented scooter rejection")
        }
        return true
    }

    fun receive(raw: ByteArray, now: Long) {
        if (stage == Stage.CLOSED || stage == Stage.FAILED || stage == Stage.NEEDS_KEY) { rejected(raw, "inactive stage"); return }
        val p: ByteArray
        try {
            p = crypto.decrypt(raw)
        } catch (e: IllegalArgumentException) {
            rejected(raw, e.message)
            return
        }
        val index = p[6].toInt() and 255
        if (stage == Stage.PRE_COMM) {
            if (!NinebotFrames.reply(p, 4, 0x5b, index, p[2].toInt() and 255) || p.size < 37) {
                rejected(raw, "PRE_COMM source/command/length")
                return
            }
            val challenge = p.copyOfRange(7, 23).also { this.challenge = it }
            val serial = p.copyOfRange(23, 37).also { this.serial = it }
            for (b in serial) if (b.toInt() != 0 && ((b.toInt() and 255) < 32 || (b.toInt() and 255) > 126)) {
                rejected(raw, "PRE_COMM serial format")
                fail("G3 cihaz kimliği beklenen biçimde değil")
                return
            }
            port.note("pre_comm_ok", "profile=0; mode=${mode.setting}; stored_key=${index != 0}; local_key=${sessionKey != null}")
            crypto.start(challenge)
            if (pair && now < pairUntil) {
                sessionKey?.fill(0)
                val fresh = if (newKey == null) SessionKey.derive(challenge, clock()) else newKey.invoke()
                sessionKey = fresh
                if (fresh == null || fresh.size != 16) throw IllegalStateException("16-byte session key required")
                port.key(fresh.clone(), false)
                crypto.setKey(name.toByteArray(Charsets.UTF_8), challenge)
                stage = Stage.PAIR
                port.note(
                    "pair_started",
                    "deadline_mono_ms=$pairUntil; retry_ms=2000; generator=${if (newKey == null) "reference_java_lcg_sha256" else "secure_random"}",
                )
                send(4, 0x5c, 0, fresh)
                deadline = now + 2000
                port.state("Scooter güç düğmesine kısa bas • eşleştirme onayı bekleniyor (${maxOf(0L, (pairUntil - now) / 1000)} sn)")
            } else if (sessionKey == null || index == 0 || pair) {
                stage = Stage.NEEDS_KEY
                port.state("G3 yanıt verdi • ölçümler için G3 ile eşleştir düğmesini kullan")
            } else {
                auth(now)
            }
            return
        }
        if (pairReply(p, raw, now)) return
        if (stage == Stage.AUTH && NinebotFrames.reply(p, 4, 0x5d, index, 0)) {
            port.note(
                "auth_reply",
                "src=${p[3].toInt() and 255}; dst=${p[4].toInt() and 255}; cmd=93; cmd_hex=0x5d; index=$index; payload_length=0; " +
                    "counter=${counter(raw)}; status=${if (index == 1) "accepted" else if (index == 0) "rejected" else "unknown_status"}",
            )
            if (expired(now)) return
            if (index != 1) {
                rejected(raw, "AUTH status $index")
                recoverPair(now, "AUTH status $index")
                return
            }
            port.key(sessionKey!!.clone(), true)
            stage = Stage.READING
            port.pairPrompt(false)
            due = now
            port.state("G3 bağlı • deneysel ölçüm okuma")
            port.note("authenticated", "profile=0; mode=${mode.setting}; counter=${counter(raw)}; trigger=$authTrigger")
            return
        }
        val read = pending
        if (stage == Stage.READING && read != null && NinebotFrames.reply(p, read.board, 4, read.index, read.size)) {
            val data = p.copyOfRange(7, p.size)
            pending = null
            due = now + readGapMs
            if (probeReads.contains(read) && !probeVoltage(read, data)) return
            val v = metrics.accept(read, data, now)
            if (v != null) {
                misses = 0
                answered.add(read)
                lostStreak.remove(read.board)
                if (quietNext.remove(read.board) != null) {
                    scheduleDirty = true
                    fastPos = 0
                    passTail = 0
                    port.note("battery_back", "board=${read.board}; register=${read.index}")
                }
                port.reading(v, data)
                if (read.board != 0x16 && detectedBms.add(read.board)) {
                    val pack = NinebotMetrics.battery(read.board)
                    reads.addAll(reads.indexOf(read) + 1, pack.subList(1, pack.size))
                    scheduleDirty = true
                }
                if (read.index == 0x8f && probeCandidates.contains(read.board)) probeConfirmed(read.board)
            } else {
                port.note("measurement_rejected", "board=${read.board}; index=${read.index}")
                rejected(raw, "measurement outside register bounds")
                if (read.index == 0x8f && probeCandidates.remove(read.board)) {
                    dropPack(read.board, "battery_probe_partial", "reason=percent_out_of_range")
                    maybeProbeDone()
                }
            }
            return
        }
        rejected(raw, "unexpected source/command/index/length for $stage")
    }

    fun tick(now: Long) {
        if (stage == Stage.PRE_COMM && now >= deadline) {
            pre(now)
        } else if ((stage == Stage.PAIR || stage == Stage.AUTH) && expired(now)) {
            return
        } else if (stage == Stage.PAIR && now >= deadline) {
            send(4, 0x5c, 0, sessionKey!!)
            deadline = now + 2000
        } else if (stage == Stage.AUTH && now >= deadline) {
            if (authAttempts < 3) auth(now) else recoverPair(now, "AUTH timeout after 3 attempts")
        } else if (stage == Stage.READING) {
            val lost = pending
            if (lost != null && now >= deadline) {
                pending = null
                due = now
                if (probeReads.contains(lost)) {
                    probeTimeout(lost)
                } else if (quietNext.containsKey(lost.board) && !lost.sweep) {
                    port.frequentNote("battery_quiet_timeout") { "board=${lost.board}; index=${lost.index}" }
                } else {
                    val wasSweep = lost.sweep
                    port.note("read_timeout", "board=${lost.board}; index=${lost.index}; sweep=$wasSweep")
                    if (!wasSweep && !answered.contains(lost) && unanswered.merge(lost, 1, Int::plus)!! >= MAX_UNANSWERED) {
                        reads.remove(lost)
                        scheduleDirty = true
                        if (NinebotMetrics.fast(lost)) fastPos = maxOf(0, fastPos - 1)
                        else if (NinebotMetrics.rare(lost)) rarePos = maxOf(0, rarePos - 1)
                        else slowPos = maxOf(0, slowPos - 1)
                        port.note("read_disabled", "board=${lost.board}; index=${lost.index}; reason=never_answered; timeouts=$MAX_UNANSWERED")
                        if (lost.index == 0x8f && probeCandidates.remove(lost.board)) {
                            dropPack(lost.board, "battery_probe_partial", "reason=percent_silent")
                            maybeProbeDone()
                        }
                    }
                    // An external pack that answered before but now stays silent was probably detached: stop polling it for this link.
                    if (!wasSweep && answered.contains(lost) && lost.board != NinebotMetrics.PRIMARY_BMS && lost.board != 0x16 &&
                        lostStreak.merge(lost.board, 1, Int::plus)!! >= MAX_UNANSWERED
                    ) quiet(lost.board, now)
                    if (!wasSweep && ++misses >= 12) {
                        stage = Stage.FAILED
                        metrics.clear()
                        port.fault("Ölçüm yanıtı kesildi; yeniden bağlanıyor")
                        return
                    }
                }
            }
            if (pending == null && now >= due) {
                val next = next(now) ?: return
                pending = next
                if (next === speedRead) {
                    lastSpeedRead = now
                    sinceSpeed = 0
                } else {
                    sinceSpeed++
                }
                try {
                    send(next.board, 1, next.index, byteArrayOf(next.size.toByte()))
                    deadline = now + 1200
                } catch (e: IllegalStateException) {
                    stage = Stage.FAILED
                    metrics.clear()
                    port.fault("Şifreli oturum yenileniyor")
                }
            }
        }
    }

    /** One pass = every fast register (power inputs, speed), one slow panel register, a rare counter on every RARE_EVERY-th pass,
     *  then one sweep and one probe register. */
    private fun next(now: Long): NinebotMetrics.Read? {
        if (scheduleDirty) {
            fastReads.clear()
            slowReads.clear()
            rareReads.clear()
            sweepReads.clear()
            probeLane.clear()
            speedRead = null
            for (r in reads) {
                if (!r.sweep && quietNext.containsKey(r.board)) continue
                val lane = when {
                    r.sweep -> sweepReads
                    probeReads.contains(r) -> probeLane
                    NinebotMetrics.fast(r) -> fastReads
                    NinebotMetrics.rare(r) -> rareReads
                    else -> slowReads
                }
                lane.add(r)
                if (r.key == "scooter_speed_kmh") speedRead = r
            }
            scheduleDirty = false
        }
        // D12: a quiet external pack's voltage, once per QUIET_RETRY_MS, ahead of the pass (the pass cursors do not move).
        for (q in quietNext.entries) if (now >= q.value) {
            q.setValue(now + QUIET_RETRY_MS)
            val v = quietRead(q.key)
            if (v != null) return v
        }
        // Insert a known speed read between other reads without advancing their cursors: no starvation.
        // Keep one outstanding request and the full 1.2 s reply window for slow firmware.
        val speed = speedRead
        if (speed != null && answered.contains(speed) && sinceSpeed >= 2 && now - lastSpeedRead >= speedEveryMs) return speed
        val fast = fastReads
        val slow = slowReads
        val rare = rareReads
        val swept = sweepReads
        for (guard in 0 until 6) {
            if (fastPos < fast.size) return fast[fastPos++]
            if (passTail == 0) {
                passTail = 1
                if (slow.isNotEmpty()) { slowPos %= slow.size; return slow[slowPos++] }
            }
            if (passTail == 1) {
                passTail = 2
                if (rare.isNotEmpty() && (passCount % RARE_EVERY == 0 || slow.isEmpty())) { rarePos %= rare.size; return rare[rarePos++] }
            }
            if (passTail == 2) {
                passTail = 3
                if (swept.isNotEmpty()) { sweepPos %= swept.size; return swept[sweepPos++] }
            }
            if (passTail == 3) {
                passTail = 4
                if (probeLane.isNotEmpty()) { probePos %= probeLane.size; return probeLane[probePos++] }
            }
            fastPos = 0
            passTail = 0
            passCount++
        }
        return null
    }

    /** A probe reply counts only as a plausible pack voltage; anything else removes that board from the probe. */
    private fun probeVoltage(read: NinebotMetrics.Read, data: ByteArray): Boolean {
        misses = 0
        probeReads.remove(read)
        scheduleDirty = true
        val v = NinebotMetrics.decode(read, data)
        if (v == null || v < NinebotMetrics.PACK_MIN_V || v > NinebotMetrics.PACK_MAX_V) {
            reads.remove(read)
            port.note("battery_probe_reject", "board=${read.board}; raw_hex=${Timing.hex(data)}")
            maybeProbeDone()
            return false
        }
        probeCandidates.add(read.board)
        port.note("battery_probe_candidate", "board=${read.board}; voltage_v=${String.format(Locale.ROOT, "%.2f", v)}")
        return true
    }

    private fun probeTimeout(lost: NinebotMetrics.Read) {
        val tries = unanswered.merge(lost, 1, Int::plus)!!
        port.note("battery_probe_timeout", "board=${lost.board}; try=$tries")
        if (tries < PROBE_TRIES) return
        reads.remove(lost)
        probeReads.remove(lost)
        scheduleDirty = true
        port.note("battery_probe_miss", "board=${lost.board}; tries=$tries")
        maybeProbeDone()
    }

    /** Percent answered: the candidate is a pack. One external pack is expected, so the remaining probe stops here. */
    private fun probeConfirmed(board: Int) {
        probeCandidates.remove(board)
        probeFound.add(board)
        port.note("battery_probe_found", "board=$board" + String.format(Locale.ROOT, "; board_hex=%02x", board))
        port.batteryFound(board)
        for (r in probeReads) reads.remove(r)
        probeReads.clear()
        for (other in ArrayList(probeCandidates)) dropPack(other, "battery_probe_partial", "reason=other_pack_confirmed")
        probeCandidates.clear()
        scheduleDirty = true
        maybeProbeDone()
    }

    private fun maybeProbeDone() {
        if (!probing || probeReads.isNotEmpty() || probeCandidates.isNotEmpty()) return
        probing = false
        val found = probeFound.isNotEmpty()
        val boards = StringBuilder()
        for (b in probeFound) boards.append(if (boards.isNotEmpty()) "," else "").append(String.format(Locale.ROOT, "%02x", b))
        port.note("battery_probe_done", "found=$found; boards=$boards")
        port.batteryProbeDone(found)
        port.state(if (found) "G3 bağlı • harici batarya bulundu (kart $boards)" else "G3 bağlı • harici batarya bulunamadı")
    }

    /** D12: the pack stays known but unknown-valued; see QUIET_RETRY_MS. */
    private fun quiet(board: Int, now: Long) {
        lostStreak.remove(board)
        if (quietNext.containsKey(board)) return
        quietNext[board] = now + QUIET_RETRY_MS
        scheduleDirty = true
        fastPos = 0
        passTail = 0
        port.note("battery_quiet", "board=$board; timeouts=$MAX_UNANSWERED; retry_ms=$QUIET_RETRY_MS")
    }

    private fun quietRead(board: Int): NinebotMetrics.Read? {
        var any: NinebotMetrics.Read? = null
        for (r in reads) if (r.board == board && !r.sweep) {
            if (r.index == 0x8c) return r
            if (any == null) any = r
        }
        return any
    }

    private fun dropPack(board: Int, kind: String, reason: String) {
        quietNext.remove(board)
        reads.removeAll { it.board == board && !it.sweep }
        detectedBms.remove(board)
        lostStreak.remove(board)
        scheduleDirty = true
        fastPos = 0
        passTail = 0
        port.note(kind, "board=$board; $reason")
    }

    /** Fastest cadence while somebody can see the values, a lighter one otherwise (BleCapture.updatePriority, once a second). */
    fun panelVisible(visible: Boolean) {
        readGapMs = if (visible) VISIBLE_GAP_MS else BACKGROUND_GAP_MS
        speedEveryMs = if (visible) VISIBLE_SPEED_MS else BACKGROUND_SPEED_MS
    }

    /** Sleep until the actual protocol deadline instead of waking ten times a second during AUTH. */
    fun nextDelayMs(now: Long): Long {
        if (stage == Stage.CLOSED || stage == Stage.FAILED || stage == Stage.NEEDS_KEY) return 1000
        var next = if (stage == Stage.READING) (if (pending == null) due else deadline) else deadline
        if (stage == Stage.PAIR || stage == Stage.AUTH && pair) next = minOf(next, pairUntil)
        return maxOf(1L, minOf(1000L, next - now))
    }

    fun close() {
        stage = Stage.CLOSED
        port.pairPrompt(false)
        metrics.clear()
        sessionKey?.fill(0)
    }

    companion object {
        const val PAIR_WINDOW_MS = 300000L

        /** Values on screen (live panel, its picture-in-picture window, or the floating ball while the screen is on): next read 5 ms
         *  after a reply, speed at most every 150 ms with two other reads between (the original comment said 180 ms; the code and
         *  its tests use 150, SCOOTER.md R-S1). Nobody watching (screen off): 20 ms (was 80 ms) and speed every 400 ms (was 600 ms),
         *  so the 1 Hz energy samples get power inputs younger than a second even with an external pack (two more fast reads per
         *  pass). One request stays outstanding, so the scooter's ~70 ms reply time is the ceiling and it can never be flooded;
         *  nothing is pipelined. (The official app averaged ~7 reads/s in the 2026-10-04 HCI capture.) */
        const val VISIBLE_GAP_MS = 5L
        const val VISIBLE_SPEED_MS = 150L
        const val BACKGROUND_GAP_MS = 20L
        const val BACKGROUND_SPEED_MS = 400L

        /** Counters nobody watches live (odometer, lifetime seconds, pack reg 0x8a) are read on every RARE_EVERY-th pass only. */
        const val RARE_EVERY = 3

        /** A register that never answered this many timeouts in a row is dropped for this connection (G3 has no BMS 0x22/0x23). */
        const val MAX_UNANSWERED = 3

        /** Each probed board gets this many 1.2 s reply windows; probe timeouts never count as a lost link. */
        const val PROBE_TRIES = 2

        /** D12 (13 Ekim, saha 11–12 Ekim): an external pack that answered before and then stayed silent for MAX_UNANSWERED reads in a
         *  row is not dropped any more (it came back only after a Bluetooth reconnect: 240–290 times a day, seen in 8–10% of the
         *  samples, in 3 s pieces even at 53 V). It counts as unknown (its values go stale) and only its voltage is read, once every
         *  QUIET_RETRY_MS, outside the normal pass; its first answer puts all its registers back. A quiet read never counts towards
         *  a lost link. The single outstanding request and the 1.2 s reply window stay as they are. */
        const val QUIET_RETRY_MS = 15000L

        private fun counter(wire: ByteArray): Int =
            if (wire.size < 2) -1 else ((wire[wire.size - 2].toInt() and 255) shl 8) or (wire[wire.size - 1].toInt() and 255)

        private fun hex2(v: Int): String = String.format(Locale.ROOT, "0x%02x", v)
    }
}
