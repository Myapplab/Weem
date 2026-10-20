package com.kuryelog.logic.map.route

import com.kuryelog.logic.map.MemoryMapPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Y8 sokak kararları (MAP.md 4.2, 4.6 "Yeni"; YolGo `street_prefs.dart` karakterizasyonu, eski testi yok): işlem tablosu satır satır,
 * `streets.*` kodeği (bozuk öğeler atlanır), yazma sırası ve "Geri al" dizisinin (`nav_controller.dart:706–750`) önceki durumu
 * aynen kurması. Göç (`street_prefs_migration_test`) çevrilmez: S1.
 */
class StreetPrefsTest {
    private fun store(initial: Map<String, Any> = emptyMap()): Pair<StreetPrefsStore, MemoryMapPrefs> {
        val prefs = MemoryMapPrefs(initial)
        return StreetPrefsStore(prefs) to prefs
    }

    @Test
    fun `onay kara listeden çıkarır, eğim ve saat kalır, kapatma onayı kaldırır ama saati silmez`() {
        val (s, _) = store()
        s.markSteep(1, "Dik")
        s.setHours(1, ReverseHours(60, 120))
        s.block(1, "")
        assertEquals(setOf(1L), s.current.blocked)
        s.approve(1, "")
        assertEquals(setOf(1L), s.current.approved)
        assertTrue(s.current.blocked.isEmpty())
        assertEquals(setOf(1L), s.current.steep)
        s.block(1, "")
        assertTrue(s.current.approved.isEmpty())
        assertEquals(ReverseHours(60, 120), s.current.reverseHours[1L]) // block saati silmez
        s.remove(StreetList.BLOCKED, 1)
        assertEquals(ReverseHours(60, 120), s.current.reverseHours[1L]) // kara listeden çıkmak da
        assertEquals(mapOf(1L to "Dik"), s.current.names) // boş ad yazılmaz
    }

    @Test
    fun `clearWay üç listeyi ve saati temizler, adı bırakır`() {
        val (s, _) = store()
        s.approve(5, "Beş")
        s.markSteep(5, "Beş")
        s.setHours(5, ReverseHours(23 * 60, 0))
        s.clearWay(5)
        assertEquals(StreetPrefs(names = mapOf(5L to "Beş")), s.current)
    }

    @Test
    fun `remove ve clearAll yalnız kendi listesine dokunur, onaylılarla saatler gider`() {
        val (s, _) = store()
        s.approve(1, "A")
        s.approve(2, "B")
        s.setHours(1, ReverseHours(0, 60))
        s.setHours(2, ReverseHours(0, 60))
        s.block(3, "C")
        s.markSteep(4, "D")
        s.remove(StreetList.APPROVED, 1)
        assertEquals(setOf(2L), s.current.approved)
        assertEquals(setOf(2L), s.current.reverseHours.keys)
        s.remove(StreetList.STEEP, 4)
        assertTrue(s.current.steep.isEmpty())
        assertEquals(setOf(3L), s.current.blocked)
        s.markSteep(4, "D")
        s.clearAll(StreetList.BLOCKED)
        assertTrue(s.current.blocked.isEmpty())
        assertEquals(setOf(2L), s.current.reverseHours.keys)
        s.clearAll(StreetList.APPROVED)
        assertTrue(s.current.approved.isEmpty())
        assertTrue(s.current.reverseHours.isEmpty())
        assertEquals(setOf(4L), s.current.steep)
        assertEquals(mapOf(1L to "A", 2L to "B", 3L to "C", 4L to "D"), s.current.names)
    }

    @Test
    fun `setHours geçersiz aralığı yok sayar, null siler`() {
        val (s, _) = store()
        s.setHours(7, ReverseHours(10, 10))
        assertTrue(s.current.reverseHours.isEmpty())
        s.setHours(7, ReverseHours(10, 20))
        s.setHours(7, ReverseHours(-1, 20))
        assertEquals(ReverseHours(10, 20), s.current.reverseHours[7L])
        s.setHours(7, null)
        assertTrue(s.current.reverseHours.isEmpty())
    }

    @Test
    fun `her işlem beş anahtarı ekleme sırasıyla yazar`() {
        val (s, prefs) = store()
        s.approve(30, "Otuz")
        s.approve(10, "On|kenar") // ad | içerebilir
        s.approve(30, "Otuz yeni") // var olan yerinde kalır
        s.setHours(10, ReverseHours(23 * 60, 7 * 60))
        assertEquals(listOf("30", "10"), prefs.getStringList("streets.approved"))
        assertEquals(emptyList<String>(), prefs.getStringList("streets.blocked"))
        assertEquals(emptyList<String>(), prefs.getStringList("streets.steep"))
        assertEquals(listOf("30|Otuz yeni", "10|On|kenar"), prefs.getStringList("streets.names"))
        assertEquals(listOf("10|1380|420"), prefs.getStringList("streets.hours.v1"))
        assertEquals(s.current, StreetPrefsStore(prefs).current) // yeniden okuma aynı durumu kurar
        assertFalse(prefs.getBool("streets.blocked.migrated.v2") == true) // göç işareti yazılmaz (S1)
    }

    @Test
    fun `kodek bozuk öğeleri atlar`() {
        val (s, _) = store(
            mapOf(
                "streets.approved" to listOf("12", "x", "", "-3"),
                "streets.blocked" to listOf("7.5", "8"),
                "streets.names" to listOf("|başsız", "12|On iki", "yok", "x|Harf"),
                "streets.hours.v1" to listOf("12|60|120", "13|60", "14|a|120", "15|0|1440", "16|5|5", "17|1|2|3"),
            ),
        )
        assertEquals(setOf(12L, -3L), s.current.approved)
        assertEquals(setOf(8L), s.current.blocked)
        assertEquals(mapOf(12L to "On iki"), s.current.names)
        assertEquals(mapOf(12L to ReverseHours(60, 120)), s.current.reverseHours)
    }

    @Test
    fun `Geri al dizisi önceki listeleri ve saati aynen kurar`() {
        val (s, _) = store()
        s.approve(9, "Dokuz")
        s.markSteep(9, "Dokuz")
        s.setHours(9, ReverseHours(22 * 60, 6 * 60))
        val before = s.current
        // Kullanıcı kapatır, sonra "Geri al" (NavController: clearWay + önceki onay/kapalı/eğim + saat).
        s.block(9, "Dokuz")
        s.clearWay(9)
        if (9L in before.approved) s.approve(9, "Dokuz")
        if (9L in before.blocked) s.block(9, "Dokuz")
        if (9L in before.steep) s.markSteep(9, "Dokuz")
        before.reverseHours[9L]?.let { s.setHours(9, it) }
        assertEquals(before, s.current)
    }
}
