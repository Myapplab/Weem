package com.kuryelog.logic.scooter

/**
 * Bounded receive reassembly. One instance per notification characteristic (SCOOTER.md 2.2; NB `NinebotFrames.java`, line by
 * line). A stale half frame (>3 s), a chunk over 2,048 bytes and a remainder over 268 bytes are dropped; `5a a5` and `5a b5`
 * start a frame of `LEN + 13` bytes.
 */
class NinebotFrames(private val reject: Reject? = null) {
    fun interface Reject {
        fun rejected(raw: ByteArray, reason: String)
    }

    private var buffer = ByteArray(0)
    private var last = 0L

    private fun dropped(raw: ByteArray, reason: String) {
        if (reject != null && raw.isNotEmpty()) reject.rejected(raw, reason)
    }

    fun clear() {
        buffer = ByteArray(0)
    }

    fun feed(chunk: ByteArray, now: Long): List<ByteArray> {
        if (now - last > 3000) { dropped(buffer, "stale incomplete frame"); clear() }
        last = now
        val frames = ArrayList<ByteArray>()
        if (chunk.size > 2048) { dropped(chunk, "chunk exceeds reassembly bound"); clear(); return frames }
        val joined = buffer.copyOf(buffer.size + chunk.size)
        System.arraycopy(chunk, 0, joined, buffer.size, chunk.size)
        var at = 0
        var noise = 0
        while (at + 1 < joined.size) {
            val second = joined[at + 1].toInt() and 255
            if ((joined[at].toInt() and 255) != 0x5a || (second != 0xa5 && second != 0xb5)) { at++; continue }
            if (at > noise) dropped(joined.copyOfRange(noise, at), "bytes before supported sync")
            noise = at
            if (at + 2 >= joined.size) break
            val length = (joined[at + 2].toInt() and 255) + 13
            if (joined.size - at < length) break
            frames.add(joined.copyOfRange(at, at + length))
            at += length
            noise = at
        }
        if (at > noise) dropped(joined.copyOfRange(noise, at), "bytes before supported sync")
        buffer = joined.copyOfRange(at, joined.size)
        if (buffer.size > 268) { dropped(buffer, "incomplete frame exceeds bound"); clear() }
        return frames
    }

    companion object {
        /** Plain frame `5a a5 LEN 3e BOARD CMD INDEX DATA` (`3e` = the app as source). */
        fun request(board: Int, cmd: Int, index: Int, data: ByteArray): ByteArray {
            if (data.size > 255) throw IllegalArgumentException("payload")
            val p = ByteArray(data.size + 7)
            p[0] = 0x5a
            p[1] = 0xa5.toByte()
            p[2] = data.size.toByte()
            p[3] = 0x3e
            p[4] = board.toByte()
            p[5] = cmd.toByte()
            p[6] = index.toByte()
            System.arraycopy(data, 0, p, 7, data.size)
            return p
        }

        /** A reply from [board] to the app (`3e`) with [cmd], [index] and exactly [size] data bytes. */
        fun reply(p: ByteArray, board: Int, cmd: Int, index: Int, size: Int): Boolean =
            p.size == size + 7 && (p[3].toInt() and 255) == board && (p[4].toInt() and 255) == 0x3e &&
                (p[5].toInt() and 255) == cmd && (p[6].toInt() and 255) == index
    }
}
