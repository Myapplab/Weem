package com.kuryelog.logic.link

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** LINK.md 4.10: durum satırları ve iki dilli metinler. */
class LinkViewTest {
    private val peer = PeerInfo(PeerId.of("22222222-2222-4222-8222-222222222222")!!, 1, 0, 40, "1.2.0", "Poco")

    @Test
    fun everyTextHasBothLanguagesAndSamePlaceholders() {
        val spec = Regex("%[sd]")
        for (t in LinkText.entries) {
            assertTrue(t.name, t.tr.isNotBlank() && t.az.isNotBlank())
            assertEquals(t.name, spec.findAll(t.tr).map { it.value }.toList(), spec.findAll(t.az).map { it.value }.toList())
        }
    }

    @Test
    fun statusLines() {
        val lang = Lang.TR
        assertEquals(listOf("Eşleşme yok: QR ile eşleştirin"), LinkView.lines(LinkStatus.OFF, false, LinkRole.PROVIDER, null, null, "1.2.0", lang))
        val linked = LinkStatus(LinkStatus.State.LINKED, Transport.WIFI, rttMs = 12, lan = true, btOn = true, btPhones = true)
        assertEquals(listOf("Bağlı · Wi-Fi · 12 ms"), LinkView.lines(linked, true, LinkRole.PROVIDER, LinkRole.RECEIVER, peer, "1.2.0", lang))
        assertEquals(listOf("Bağlı · Bluetooth", "İki telefon da Provider rolünde: birini değiştirin", "Öteki telefonda WEEM 1.2.0, bu telefonda 1.1.0"),
            LinkView.lines(linked.copy(transport = Transport.BT, rttMs = null), true, LinkRole.PROVIDER, LinkRole.PROVIDER, peer, "1.1.0", lang))
        val searching = LinkStatus(LinkStatus.State.SEARCHING, lan = false, btOn = true, btPhones = false, issue = LinkIssue.VERSION)
        assertEquals(listOf("Öteki telefon aranıyor", "Öteki telefondaki WEEM uyumsuz (1.2.0); ikisini de güncelleyin", "Yerel ağ yok (hotspot kapalı)",
            "Bluetooth'ta eşleşmiş telefon yok"), LinkView.lines(searching, true, LinkRole.RECEIVER, null, peer, "1.2.0", lang))
        assertEquals("Digər telefon tanınmadı: yenidən cütləşdirin",
            LinkView.lines(searching.copy(issue = LinkIssue.AUTH, lan = true, btPhones = true), true, LinkRole.RECEIVER, null, null, "1", Lang.AZ)[1])
        assertEquals("4:59", LinkView.remaining(298_500))
        assertEquals("0:00", LinkView.remaining(-5))
        assertEquals("Receiver — scooter telefonu", LinkView.role(LinkRole.RECEIVER, lang))
    }
}
