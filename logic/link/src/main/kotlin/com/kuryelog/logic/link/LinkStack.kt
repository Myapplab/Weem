package com.kuryelog.logic.link

import java.security.SecureRandom
import java.util.UUID

/**
 * Makine + `Messenger` tek kilit altında (LINK.md 3.13): `LinkRuntime` ve testler aynı bağlamayı kullanır. Çözülmüş `DATA`
 * `Messenger`'a, durum değişimleri ona da gider; `Messenger`'ın gönderimleri makineden şifreli çıkar. Uyanma ikisinin en erkeni.
 *
 * [store] `null` ise `Messenger` yok: `DATA` ham olarak [LinkCommand.Deliver] çıkar (A8.1 taşıma testleri).
 * Çağıran, [LinkCommand.Message] dinleyicilerini çalıştırdıktan sonra [delivered] der (teslim zorunlu onayı ancak o zaman gider).
 */
class LinkStack(
    cfg: LinkConfig = LinkConfig(),
    store: MessengerStore?,
    mcfg: MessengerConfig = MessengerConfig(),
    random: () -> Double = Math::random,
    secureRandom: SecureRandom = SecureRandom(),
    ids: () -> UUID = UUID::randomUUID,
) {
    val machine = LinkMachine(cfg, random, secureRandom)
    val messenger: Messenger? = store?.let { Messenger(it, mcfg, ids) }

    private val out = ArrayList<LinkCommand>()
    private var nowMs = 0L
    private var wallMs = 0L
    private var machineWake: Long? = null
    private var lastWake: Long? = null
    private var wakeSent = false

    init {
        messenger?.port = { b -> machine.send(b, nowMs) }
    }

    val status: LinkStatus get() = machine.status

    fun handle(e: LinkEvent, nowMs: Long, wallMs: Long) {
        at(nowMs, wallMs)
        machine.handle(e, nowMs)
        settle()
    }

    /** Ham gönderim; yalnız `Messenger`'sız yığında. */
    fun send(payload: ByteArray, nowMs: Long, wallMs: Long): SendResult {
        check(messenger == null) { "messages go through publish" }
        at(nowMs, wallMs)
        val r = machine.send(payload, nowMs)
        settle()
        return r
    }

    fun publishLatest(type: String, payload: ByteArray, ttlMs: Long, nowMs: Long, wallMs: Long): UUID {
        val m = messenger ?: error("no messenger")
        at(nowMs, wallMs)
        val id = m.publishLatest(type, payload, ttlMs, nowMs, wallMs)
        settle()
        return id
    }

    fun publishReliable(type: String, payload: ByteArray, ttlMs: Long, nowMs: Long, wallMs: Long): UUID {
        val m = messenger ?: error("no messenger")
        at(nowMs, wallMs)
        val id = m.publishReliable(type, payload, ttlMs, nowMs, wallMs)
        settle()
        return id
    }

    /** Uygulama gelen iletileri işledi: teslim zorunluların `id`'leri yazılır, onay gider (LINK.md 3.8). */
    fun delivered(nowMs: Long, wallMs: Long) {
        val m = messenger ?: return
        at(nowMs, wallMs)
        m.delivered(nowMs, wallMs)
        settle()
    }

    /** Eşleşmeyi kaldır (3.11): bağlıysa eşe söyle, makineyi durdur, kuyrukları sil. Kaydı silmek çağıranın işi. */
    fun unpair(nowMs: Long, wallMs: Long) {
        at(nowMs, wallMs)
        messenger?.unpair(nowMs, wallMs)
        machine.handle(LinkEvent.Disable, nowMs)
        messenger?.reset()
        settle()
    }

    fun drain(): List<LinkCommand> {
        val r = ArrayList(out)
        out.clear()
        return r
    }

    private fun at(now: Long, wall: Long) {
        nowMs = now
        wallMs = wall
    }

    private fun settle() {
        var rounds = 0
        do {
            machine.drain().forEach(::route)
            val m = messenger
            if (m != null) {
                m.pump(nowMs, wallMs)
                for (c in m.drain()) {
                    out += c
                    if (c === LinkCommand.Unpaired) {
                        // Eş eşleşmeyi kaldırdı: bu telefon da durur (kaydı çağıran siler).
                        machine.handle(LinkEvent.Disable, nowMs)
                        m.reset()
                    }
                }
            }
        } while (machine.pending && ++rounds < MAX_ROUNDS)
        emitWake()
    }

    private fun route(c: LinkCommand) {
        val m = messenger
        when (c) {
            is LinkCommand.Deliver -> if (m == null) out += c else m.onData(c.payload, c.transport, nowMs, wallMs)
            is LinkCommand.Status -> {
                m?.onStatus(c.status, nowMs, wallMs)
                out += c
            }
            is LinkCommand.Paired -> {
                // Yeni eş: eskisinin kuyrukları geçersiz; kayıt eşleşme anıyla (duvar saati) çıkar.
                m?.reset()
                out += LinkCommand.Paired(PeerRecord(c.peer.id, c.peer.publicKey, c.peer.name, wallMs))
            }
            is LinkCommand.Wake -> machineWake = c.atMs
            else -> out += c
        }
    }

    private fun emitWake() {
        val w = listOfNotNull(machineWake, messenger?.nextWakeMs()).minOrNull()
        if (wakeSent && w == lastWake) return
        wakeSent = true
        lastWake = w
        out += LinkCommand.Wake(w)
    }

    private companion object {
        const val MAX_ROUNDS = 64
    }
}
