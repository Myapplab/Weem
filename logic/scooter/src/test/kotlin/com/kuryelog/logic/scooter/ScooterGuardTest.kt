package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException

/**
 * WEEM ekleri (SCOOTER.md 2.6, 12.5, 13.3): salt okuma, ağsızlık, `kotlin.random` yok ve oturum notlarının türleri (kayıt
 * sözleşmesi, 11.2). Eski testlerde yok; davranış değiştirmez, gerilemeyi yakalar.
 */
class ScooterGuardTest {
    /** 2.6: every command except read (0x01) and the board-4 handshake (0x5b PRE_COMM, 0x5c PAIR, 0x5d AUTH) is refused before
     *  anything is encrypted or written. */
    @Test fun sessionSendsOnlyReadAndHandshakeCommands() {
        val sent = ArrayList<ByteArray>()
        val port = object : NinebotSession.Port {
            override fun available(profile: Int) = true
            override fun send(profile: Int, wire: ByteArray) { sent.add(wire) }
            override fun state(state: String) {}
            override fun note(kind: String, detail: String) {}
            override fun fault(reason: String) {}
            override fun key(key: ByteArray, confirmed: Boolean) {}
            override fun reading(value: NinebotMetrics.Value, bytes: ByteArray) {}
        }
        val s = NinebotSession(port, "TEST-G3-000001", null, false, 0)
        val send = NinebotSession::class.java.getDeclaredMethod("send", Int::class.java, Int::class.java, Int::class.java, ByteArray::class.java)
        send.isAccessible = true
        var allowed = 0
        for (board in intArrayOf(4, 7, 0x16, 0x22, 0x3e)) for (command in 0..255) {
            val ok = command == 1 || (board == 4 && command in intArrayOf(0x5b, 0x5c, 0x5d))
            val before = sent.size
            val refused = try {
                send.invoke(s, board, command, 0, ByteArray(0)); false
            } catch (e: InvocationTargetException) {
                assertEquals("read/handshake commands only", e.cause!!.message); true
            }
            assertEquals("board $board command $command", ok, !refused)
            assertEquals(if (ok) before + 1 else before, sent.size)
            if (ok) allowed++
        }
        assertEquals(5 + 3, allowed)
    }

    /** 2.6: frames are built and encrypted only in `NinebotSession.send` (the one place the guard sits). */
    @Test fun framesAreBuiltOnlyBehindTheGuard() {
        val hits = RepoSource.scooterSources().flatMap { f ->
            RepoSource.code(f).filter { "NinebotFrames.request(" in it || Regex("""crypto\.encrypt\(""").containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" }
        }
        assertEquals(listOf("NinebotSession.kt: val wire = crypto.encrypt(NinebotFrames.request(board, command, index, payload))"), hits)
    }

    /** 12.5 (öneri): `:scooter` talks only to the scooter over Bluetooth; no network class in its code. */
    @Test fun noNetworkClassInScooterCode() {
        val files = RepoSource.scooterSources()
        assertTrue(files.any { it.name == "NinebotSession.kt" })
        val banned = Regex("""\bjava\.net\.|\bjavax\.net\.|\bokhttp3?\b|\bandroid\.net\.http\b|\bio\.ktor\b|\bretrofit2?\b|\bWebView\b|""" +
            """\bHttpURLConnection\b|\bHttpsURLConnection\b|\bURLConnection\b|\bSocket\b|\bDatagramSocket\b|\bInetAddress\b|\bURL\(|\bCronet""")
        val bad = files.flatMap { f -> RepoSource.code(f).withIndex().filter { banned.containsMatchIn(it.value) }.map { "${f.name}:${it.index + 1}: ${it.value.trim()}" } }
        assertEquals(emptyList<String>(), bad)
    }

    /** 13.3, R-S7: the reference derivation needs `java.util.Random`; `kotlin.random` would silently derive another key. */
    @Test fun noKotlinRandom() {
        val bad = RepoSource.scooterSources().flatMap { f ->
            RepoSource.code(f).filter { "kotlin.random" in it || Regex("""(?<![.\w])Random\.""").containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" }
        }
        assertEquals(emptyList<String>(), bad)
        assertTrue(RepoSource.code(RepoSource.scooterSources().single { it.name == "SessionKey.kt" }).any { "java.util.Random(seed).nextBytes(random)" in it })
    }

    /** 11.2: the session's note kinds become `ninebot-logger.db` event kinds; the set is the data contract (no kind added or lost). */
    @Test fun sessionNoteKindsAreTheRecordedOnes() {
        val text = RepoSource.code(RepoSource.scooterSources().single { it.name == "NinebotSession.kt" }).joinToString("\n")
        val kinds = (Regex("""(?:note|frequentNote)\(\s*"([a-z_]+)"""").findAll(text).map { it.groupValues[1] } +
            Regex("""dropPack\([^,]+,\s*"([a-z_]+)"""").findAll(text).map { it.groupValues[1] }).toSortedSet()
        val contract = sortedSetOf(
            "handshake_started", "pre_comm_attempt", "pre_comm_ok", "pair_started", "pair_reply", "pair_first_reply", "pair_to_auth",
            "pair_resumed", "pair_window_expired", "auth_reply", "auth_failed", "authenticated", "protocol_tx", "protocol_rejected",
            "protocol_state", "read_timeout", "read_disabled", "measurement_rejected", "battery_boards", "battery_probe_candidate",
            "battery_probe_reject", "battery_probe_timeout", "battery_probe_miss", "battery_probe_found", "battery_probe_partial",
            "battery_probe_done", "battery_quiet", "battery_quiet_timeout", "battery_back",
        )
        assertEquals(contract, kinds)
    }
}
