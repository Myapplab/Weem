package com.kuryelog.logic.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** LINK.md 3.7, 3.12: `files/link/` — `outbox/<seq>.env`, `seq`, `seen`; bozuk dosya atlanır, yazma atomik. */
class MessengerStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun stores(): List<MessengerStore> = listOf(MemoryMessengerStore(), FileMessengerStore(tmp.newFolder()))

    @Test
    fun outboxPutRemoveLoad() {
        for (s in stores()) {
            assertTrue(s.outbox().isEmpty())
            s.putOutbox(7, byteArrayOf(7))
            s.putOutbox(300, byteArrayOf(3, 0, 0))
            s.putOutbox(7, byteArrayOf(7, 7))
            assertEquals(listOf(7L to listOf<Byte>(7, 7), 300L to listOf<Byte>(3, 0, 0)), s.outbox().map { it.first to it.second.toList() }.sortedBy { it.first })
            s.removeOutbox(7)
            s.removeOutbox(12345)
            assertEquals(listOf(300L), s.outbox().map { it.first })
        }
    }

    @Test
    fun seenAndSeqBlock() {
        for (s in stores()) {
            assertNull(s.seen())
            assertEquals(0L, s.seqBlock())
            s.putSeen(byteArrayOf(1, 2, 3))
            s.putSeqBlock(512)
            assertArrayEquals(byteArrayOf(1, 2, 3), s.seen())
            assertEquals(512L, s.seqBlock())
            s.clear()
            assertNull(s.seen())
            assertTrue(s.outbox().isEmpty())
            // seq bloğu silinmez: yeni eşe de aynı gönderenin seq'i artarak gider.
            assertEquals(512L, s.seqBlock())
        }
    }

    @Test
    fun fileLayoutAndSurvivesReopen() {
        val dir = tmp.newFolder()
        val s = FileMessengerStore(dir)
        s.putOutbox(42, byteArrayOf(4, 2))
        s.putSeqBlock(256)
        s.putSeen(byteArrayOf(9))
        assertTrue(File(dir, "outbox/00000000000000000042.env").isFile)
        assertEquals("256", File(dir, "seq").readText().trim())
        assertTrue(File(dir, "seen").isFile)
        val again = FileMessengerStore(dir)
        assertEquals(listOf(42L), again.outbox().map { it.first })
        assertEquals(256L, again.seqBlock())
        assertArrayEquals(byteArrayOf(9), again.seen())
    }

    @Test
    fun strayAndBrokenFilesAreIgnored() {
        val dir = tmp.newFolder()
        val s = FileMessengerStore(dir)
        s.putOutbox(1, byteArrayOf(1))
        File(dir, "outbox/zz.env").writeText("x")
        File(dir, "outbox/00000000000000000002.env.tmp").writeText("yarım")
        File(dir, "seq").writeText("bozuk")
        assertEquals(listOf(1L), s.outbox().map { it.first })
        assertEquals(0L, s.seqBlock())
    }
}
