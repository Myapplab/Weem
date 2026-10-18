package com.kuryelog.logic.link

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * `Messenger`'ın kalıcı durumu (LINK.md 3.7–3.8, 3.12): teslim zorunlu zarflar, işlenmiş `id`'ler, `seq` bloğu.
 * [clear] kuyruğu ve görülenleri siler (eşleşme değişti ya da kalktı); `seq` bloğu kalır (gönderenin `seq`'i hep artar).
 */
interface MessengerStore {
    /** (seq, kodlanmış zarf), sıra önemsiz. */
    fun outbox(): List<Pair<Long, ByteArray>>
    fun putOutbox(seq: Long, envelope: ByteArray)
    fun removeOutbox(seq: Long)
    fun seen(): ByteArray?
    fun putSeen(bytes: ByteArray)

    /** Sıradaki ayrılmamış `seq` (0 = hiç). */
    fun seqBlock(): Long
    fun putSeqBlock(next: Long)
    fun clear()
}

/** Testler ve geçici kullanım için bellek deposu (nesne "yeniden başlatmadan" sağ kalır). */
class MemoryMessengerStore : MessengerStore {
    private val out = LinkedHashMap<Long, ByteArray>()
    private var seenBytes: ByteArray? = null
    private var block = 0L

    @Synchronized override fun outbox() = out.map { it.key to it.value.copyOf() }
    @Synchronized override fun putOutbox(seq: Long, envelope: ByteArray) {
        out[seq] = envelope.copyOf()
    }
    @Synchronized override fun removeOutbox(seq: Long) {
        out.remove(seq)
    }
    @Synchronized override fun seen() = seenBytes?.copyOf()
    @Synchronized override fun putSeen(bytes: ByteArray) {
        seenBytes = bytes.copyOf()
    }
    @Synchronized override fun seqBlock() = block
    @Synchronized override fun putSeqBlock(next: Long) {
        block = next
    }
    @Synchronized override fun clear() {
        out.clear()
        seenBytes = null
    }
}

/**
 * `files/link/` (VERI_SOZLESMESI 7.4): `outbox/<seq, 20 hane>.env`, `seq` (ondalık metin), `seen`. Yazma geçici dosya + `fsync` +
 * yeniden adlandırma (yarım dosya okunmaz); tanınmayan ya da bozuk dosya atlanır.
 */
class FileMessengerStore(private val dir: File) : MessengerStore {
    private val outDir = File(dir, "outbox")
    private val seqFile = File(dir, "seq")
    private val seenFile = File(dir, "seen")

    @Synchronized override fun outbox(): List<Pair<Long, ByteArray>> {
        val files = outDir.listFiles() ?: return emptyList()
        val r = ArrayList<Pair<Long, ByteArray>>()
        for (f in files) {
            val m = NAME.matchEntire(f.name) ?: continue
            val seq = m.groupValues[1].toLongOrNull() ?: continue
            val b = try {
                f.readBytes()
            } catch (e: IOException) {
                continue
            }
            r += seq to b
        }
        return r
    }

    @Synchronized override fun putOutbox(seq: Long, envelope: ByteArray) = atomic(File(outDir, name(seq)), envelope)

    @Synchronized override fun removeOutbox(seq: Long) {
        File(outDir, name(seq)).delete()
    }

    @Synchronized override fun seen(): ByteArray? = if (seenFile.isFile) {
        try {
            seenFile.readBytes()
        } catch (e: IOException) {
            null
        }
    } else {
        null
    }

    @Synchronized override fun putSeen(bytes: ByteArray) = atomic(seenFile, bytes)

    @Synchronized override fun seqBlock(): Long = try {
        if (seqFile.isFile) seqFile.readText().trim().toLongOrNull()?.coerceAtLeast(0) ?: 0 else 0
    } catch (e: IOException) {
        0
    }

    @Synchronized override fun putSeqBlock(next: Long) = atomic(seqFile, next.toString().toByteArray(Charsets.US_ASCII))

    @Synchronized override fun clear() {
        outDir.listFiles()?.forEach { it.delete() }
        seenFile.delete()
    }

    private fun atomic(target: File, bytes: ByteArray) {
        val parent = target.parentFile
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) throw IOException("link store dir")
        val tmp = File(parent, target.name + ".tmp")
        FileOutputStream(tmp).use {
            it.write(bytes)
            it.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("link store rename")
        }
    }

    private companion object {
        val NAME = Regex("(\\d{20})\\.env")

        fun name(seq: Long) = String.format(Locale.ROOT, "%020d.env", seq)
    }
}
