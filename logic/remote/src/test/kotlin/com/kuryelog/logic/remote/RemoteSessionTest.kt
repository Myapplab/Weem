package com.kuryelog.logic.remote

import com.kuryelog.logic.remote.HostMachine.Command as H
import com.kuryelog.logic.remote.HostMachine.Event as HE
import com.kuryelog.logic.remote.ViewerMachine.Command as V
import com.kuryelog.logic.remote.ViewerMachine.Event as VE
import com.kuryelog.logic.remote.ViewerView.EndReason
import com.kuryelog.logic.remote.ViewerView.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uçtan uca: izleyen + paylaşan, sahte link ve sahte akış (REMOTE.md 1.8). */
class RemoteSessionTest {
    private fun rig() = RemoteRig().apply { viewer(VE.Link("192.168.43.1")) }

    /** İstek → onay → oynatma. */
    private fun playing(r: RemoteRig = rig()): RemoteRig = r.apply {
        viewer(VE.Start(2400))
        host(HE.ConsentGranted(1080, 2400))
        frame(key = true)
    }

    @Test fun askConsentThenPlay() {
        val r = rig()
        r.viewer(VE.Start(2400))
        assertEquals(State.CONSENT, r.viewerView.state)
        assertEquals(HostView.State.CONSENT, r.hostView.state)
        assertTrue(r.hostCommands.any { it is H.ConsentNotice && it.show })
        assertFalse(r.listening)
        assertNull(r.encoderOn)
        r.host(HE.ConsentGranted(1080, 2400))
        assertTrue(r.hostCommands.any { it is H.ConsentNotice && !it.show })
        assertTrue(r.listening)
        // Bağlandı, doğrulandı, kodlayıcı izleyenin ekranına göre açıldı, CONFIG çözücüyü kurdu.
        assertEquals(Geometry(576, 1280, 2_211_840), r.encoderOn)
        val conf = r.viewerCommands.filterIsInstance<V.Configure>().single()
        assertEquals(576, conf.width)
        assertEquals(State.CONNECTING, r.viewerView.state)
        // P kare anahtar kareden önce gitmez.
        r.frame(key = false)
        assertTrue(r.decoded.isEmpty())
        r.frame(key = true)
        r.frame(key = false)
        assertEquals(listOf(true, false), r.decoded.map { it.key })
        assertEquals(State.PLAYING, r.viewerView.state)
        assertTrue(r.hostView.watched)
        assertEquals(listOf("v>remote.ask", "h>remote.offer", "h>remote.offer"), r.linkLog)
    }

    @Test fun refusedConsent() {
        val r = rig()
        r.viewer(VE.Start(2400))
        r.host(HE.ConsentDenied)
        assertEquals(State.ENDED, r.viewerView.state)
        assertEquals(EndReason.REFUSED, r.viewerView.ended)
        assertEquals(HostView.OFF, r.hostView)
    }

    @Test fun consentNotAnsweredInSixtySecondsIsRefused() {
        val r = rig()
        r.viewer(VE.Start(2400))
        r.advance(59_000)
        assertEquals(State.CONSENT, r.viewerView.state)
        r.advance(1_000)
        assertEquals(EndReason.REFUSED, r.viewerView.ended)
        assertTrue(r.hostCommands.any { it is H.ConsentNotice && !it.show })
    }

    @Test fun secondViewingWhileSharingNeedsNoNewConsent() {
        val r = playing()
        r.viewer(VE.Stop)
        assertEquals(State.IDLE, r.viewerView.state)
        assertNull(r.encoderOn)
        assertFalse(r.listening)
        assertEquals(HostView(HostView.State.SHARING), r.hostView)
        val notices = r.hostCommands.count { it is H.ConsentNotice && it.show }
        r.advance(60_000)
        r.viewer(VE.Start(2400))
        assertEquals(notices, r.hostCommands.count { it is H.ConsentNotice && it.show })
        r.frame(key = true)
        assertEquals(State.PLAYING, r.viewerView.state)
    }

    @Test fun sharingStopsAfterTenIdleMinutes() {
        val r = playing()
        r.viewer(VE.Stop)
        r.advance(9 * 60_000L)
        assertEquals(HostView.State.SHARING, r.hostView.state)
        r.advance(60_000)
        assertEquals(HostView.OFF, r.hostView)
        assertTrue(r.hostCommands.contains(H.StopProjection))
    }

    @Test fun hostStopEndsViewer() {
        val r = playing()
        r.host(HE.StopLocal)
        assertEquals(State.ENDED, r.viewerView.state)
        assertEquals(EndReason.HOST, r.viewerView.ended)
        assertTrue(r.hostCommands.contains(H.StopProjection))
        assertTrue(r.viewerCommands.contains(V.Release))
        assertFalse(r.listening)
    }

    @Test fun systemStopDoesNotAskToStopAgain() {
        val r = playing()
        r.host(HE.ProjectionStopped)
        assertFalse(r.hostCommands.contains(H.StopProjection))
        assertEquals(EndReason.HOST, r.viewerView.ended)
    }

    @Test fun noWifiMeansNoRequest() {
        val r = RemoteRig()
        r.viewer(VE.Link(null))
        r.viewer(VE.Start(2400))
        assertEquals(EndReason.NO_WIFI, r.viewerView.ended)
        assertTrue(r.linkLog.isEmpty())
    }

    @Test fun noAnswerAfterFifteenSeconds() {
        val r = rig()
        r.linkUp = false
        r.viewer(VE.Start(2400))
        r.advance(14_000)
        assertEquals(State.ASKING, r.viewerView.state)
        r.advance(1_000)
        assertEquals(EndReason.NO_ANSWER, r.viewerView.ended)
    }

    @Test fun staleAskIsIgnored() {
        val r = rig()
        val ask = RemoteAsk(java.util.UUID.randomUUID(), ByteArray(65), 1280)
        r.host(HE.Ask(ask, 21_000))
        assertTrue(r.hostCommands.isEmpty())
        // Geçersiz açık anahtar: yanıt "unavailable", paylaşım açılmaz.
        r.host(HE.Ask(ask, 0))
        assertEquals(HostView.OFF, r.hostView)
        assertTrue(r.linkLog.contains("h>remote.offer"))
    }

    @Test fun streamLossReconnectsAndResumesWithKeyFrame() {
        val r = playing()
        r.frame(key = false)
        val before = r.decoded.size
        r.breakStream()
        assertEquals(State.CONNECTING, r.viewerView.state)
        assertNull(r.encoderOn)
        r.advance(1_000)
        // Yeniden bağlandı: kodlayıcı yeniden açıldı, yeni CONFIG, anahtar kare beklenir.
        assertTrue(r.encoderOn != null)
        r.frame(key = false)
        assertEquals(before, r.decoded.size)
        r.frame(key = true)
        assertEquals(before + 1, r.decoded.size)
        assertEquals(State.PLAYING, r.viewerView.state)
    }

    @Test fun silentStreamIsDeadAfterFiveSeconds() {
        val r = playing()
        r.holdStream = true
        r.advance(4_750)
        assertEquals(State.PLAYING, r.viewerView.state)
        r.advance(500)
        assertEquals(State.CONNECTING, r.viewerView.state)
    }

    @Test fun reconnectsThreeTimesThenAsksAgainThenGivesUp() {
        val r = playing()
        // Paylaşan telefonda süreç öldü, link de yok.
        r.linkUp = false
        r.killHost()
        assertEquals(State.CONNECTING, r.viewerView.state)
        r.advance(1_000 + 2_000 + 3_750)
        assertEquals(State.CONNECTING, r.viewerView.state)
        r.advance(250)
        // 1, 2, 4 sn arayla üç deneme düştü: istek yenilenir.
        assertEquals(State.ASKING, r.viewerView.state)
        assertEquals(2, r.linkLog.count { it == "v>remote.ask" })
        r.advance(15_000)
        assertEquals(EndReason.NO_ANSWER, r.viewerView.ended)
    }

    @Test fun congestionDropsFramesAsksKeyFrameAndLowersBitrate() {
        val r = playing()
        val keys = r.keyRequests
        // İzleyene hiçbir şey ulaşmıyor (onay da yok): yolda bayt sınırı dolar, P kareler atılır.
        r.holdStream = true
        repeat(300) { r.frame(key = false, size = 2_000) }
        assertTrue(r.keyRequests > keys)
        assertTrue(r.hostCommands.any { it is H.SetBitrate && it.bps < 2_211_840 })
        r.holdStream = false
        r.release()
        val n = r.decoded.size
        assertTrue(n in 100..200)
        r.advance(1_000)
        // Anahtar kareye dek P kare gitmez; anahtar kareyle akış sürer.
        r.frame(key = false)
        assertEquals(n, r.decoded.size)
        r.frame(key = true)
        r.frame(key = false)
        assertEquals(n + 2, r.decoded.size)
    }

    @Test fun decoderErrorAsksKeyFrame() {
        val r = playing()
        val keys = r.keyRequests
        r.advance(1_000)
        r.viewer(VE.DecoderError)
        assertTrue(r.keyRequests > keys)
        r.frame(key = false)
        val n = r.decoded.size
        r.frame(key = false)
        assertEquals(n, r.decoded.size)
    }

    @Test fun rogueConnectionWithoutKeyIsClosedAndGetsNothing() {
        val r = playing()
        val h = r.rogueConnect()
        // Oturumu bilmeyen HELLO: hemen kapanır.
        r.host(HE.Bytes(h, StreamFrames.encode(StreamFrames.HELLO, StreamHello(java.util.UUID.randomUUID(), ByteArray(16)).encode())))
        assertTrue(r.hostCommands.any { it is H.Close && it.conn == h })
        // Hiçbir şey yollamayan: 5 sn sonra kapanır; izleyen etkilenmez.
        val g = r.rogueConnect()
        r.advance(5_000)
        assertTrue(r.hostCommands.any { it is H.Close && it.conn == g })
        assertFalse(r.hostCommands.any { it is H.Write && (it.conn == g || it.conn == h) })
        assertEquals(State.PLAYING, r.viewerView.state)
    }

    @Test fun rotationReconfiguresEncoderAndDecoder() {
        val r = playing()
        r.host(HE.ScreenSize(2400, 1080))
        assertEquals(Geometry(1280, 576, 2_211_840), r.encoderOn)
        val confs = r.viewerCommands.filterIsInstance<V.Configure>()
        assertEquals(2, confs.size)
        assertEquals(1280, confs.last().width)
        r.frame(key = false)
        val n = r.decoded.size
        r.frame(key = true)
        assertEquals(n + 1, r.decoded.size)
    }

    @Test fun rotationMidStreamKeepsAcksCountingAndFramesFlowing() {
        // Döndürmeden sonra izleyenin ACK toplamı bağlantı başından sayar; paylaşan sayacı sıfırlarsa her onay reddedilir,
        // yolda bayt sınırı dolar ve akış donar.
        val r = playing()
        repeat(100) {
            r.frame(key = false, size = 5_000)
            r.advance(33, step = 33)
        }
        r.host(HE.ScreenSize(2400, 1080))
        r.frame(key = true, size = 5_000)
        val n = r.decoded.size
        repeat(200) {
            r.frame(key = false, size = 5_000)
            r.advance(33, step = 33)
        }
        assertEquals(n + 200, r.decoded.size)
        assertFalse(r.hostCommands.any { it is H.SetBitrate && it.bps < 2_211_840 })
    }

    @Test fun encoderFailureFallsBackToSmallerSizesThenUnavailable() {
        val r = rig()
        r.autoConfig = false
        r.viewer(VE.Start(2400))
        r.host(HE.ConsentGranted(1080, 2400))
        val sizes = mutableListOf(r.encoderOn!!.height)
        repeat(3) {
            r.host(HE.EncoderFailed)
            sizes += r.encoderOn!!.height
        }
        assertEquals(listOf(1280, 1024, 800, 640), sizes)
        r.host(HE.EncoderFailed)
        assertEquals(EndReason.UNAVAILABLE, r.viewerView.ended)
    }

    @Test fun shareFromHostPhoneWithoutViewer() {
        val r = rig()
        r.host(HE.ShareRequested)
        assertTrue(r.hostCommands.contains(H.OpenConsent))
        r.host(HE.ConsentGranted(1080, 2400))
        assertEquals(HostView(HostView.State.SHARING), r.hostView)
        assertFalse(r.listening)
        r.viewer(VE.Start(1080))
        // İzleyenin ekranı küçük: en uzun kenar 1024 (1080 → 1024 yedeği).
        assertEquals(1024, r.encoderOn!!.height)
    }

    @Test fun bothDirectionsAtOnce() {
        // Pixel Poco'yu izlerken Poco da Pixel'i izler: her telefonda iki bağımsız makine.
        val a = playing()
        val b = playing()
        a.frame(key = false)
        b.frame(key = false)
        assertEquals(State.PLAYING, a.viewerView.state)
        assertEquals(State.PLAYING, b.viewerView.state)
    }

    @Test fun ackFeedsFlowControlAndBitrateRecovers() {
        val r = playing()
        repeat(100) {
            r.frame(key = false, size = 5_000)
            r.advance(33, step = 33)
        }
        // Onaylar geliyor: atma yok, bit hızı düşmedi.
        assertFalse(r.hostCommands.any { it is H.SetBitrate && it.bps < 2_211_840 })
        assertEquals(101, r.decoded.size)
    }
}
