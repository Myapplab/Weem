package com.kuryelog.logic.remote

import com.kuryelog.logic.remote.HostMachine.Command as H
import com.kuryelog.logic.remote.HostMachine.Event as HE
import com.kuryelog.logic.remote.ViewerMachine.Event as VE
import com.kuryelog.logic.remote.ViewerMachine.PointerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uçtan uca dokunuş: izleyenin parmağı → akış → paylaşanda `Gesture` komutu (REMOTE.md 2). */
class RemoteTouchSessionTest {
    private fun playing(touch: Boolean = true) = RemoteRig().apply {
        viewer(VE.Link("192.168.43.1"))
        if (touch) host(HE.TouchAvailable(true))
        viewer(VE.Start(2400))
        host(HE.ConsentGranted(1080, 2400))
        frame(key = true)
    }

    private val RemoteRig.gestures get() = hostCommands.filterIsInstance<H.Gesture>().map { it.part }
    private fun RemoteRig.touch(kind: PointerKind, x: Int, y: Int, id: Int = 0) = viewer(VE.Pointer(kind, id, x, y, now))

    @Test fun tapOnTheVideoIsReplayedOnTheSharingPhone() {
        val r = playing()
        assertTrue(r.viewerView.control)
        assertTrue(r.hostView.touch)
        r.touch(PointerKind.DOWN, TouchLimits.NORM / 2, TouchLimits.NORM / 4)
        r.now += 50
        r.touch(PointerKind.UP, TouchLimits.NORM / 2, TouchLimits.NORM / 4)
        val s = r.gestures.single().strokes.single()
        assertEquals(listOf(Px(539, 600), Px(539, 600)), s.points)
        assertEquals(50, s.durationMs)
        assertFalse(s.willContinue)
    }

    @Test fun dragIsStreamedWhileTheFingerIsDown() {
        val r = playing()
        r.touch(PointerKind.DOWN, 1000, 1000)
        for (i in 1..12) {
            r.now += 10
            r.touch(PointerKind.MOVE, 1000 + i * 1000, 1000)
        }
        // Parmak kalkmadan ilk parça oynatıldı.
        assertEquals(1, r.gestures.size)
        assertTrue(r.gestures.single().strokes.single().willContinue)
        r.now += 10
        r.touch(PointerKind.UP, 14_000, 1000)
        assertEquals(listOf(true, false), r.gestures.map { it.strokes.single().willContinue })
    }

    @Test fun systemKeysAreForwarded() {
        val r = playing()
        r.viewer(VE.Action(GlobalAction.BACK))
        r.viewer(VE.Action(GlobalAction.HOME))
        assertEquals(listOf(GlobalAction.BACK, GlobalAction.HOME), r.hostCommands.filterIsInstance<H.Global>().map { it.action })
    }

    @Test fun withoutTheTouchServiceItIsViewOnly() {
        val r = playing(touch = false)
        assertFalse(r.viewerView.control)
        r.touch(PointerKind.DOWN, 1, 1)
        r.now += 30
        r.touch(PointerKind.UP, 1, 1)
        r.viewer(VE.Action(GlobalAction.HOME))
        assertTrue(r.gestures.isEmpty())
        assertTrue(r.hostCommands.none { it is H.Global })
        // Servis açılınca izleyen haber alır; dokunuş gider.
        r.host(HE.TouchAvailable(true))
        assertTrue(r.viewerView.control)
        r.touch(PointerKind.DOWN, 1, 1)
        r.now += 30
        r.touch(PointerKind.UP, 1, 1)
        assertEquals(1, r.gestures.size)
        // Kapanınca yine yalnız görüntü.
        r.host(HE.TouchAvailable(false))
        assertFalse(r.viewerView.control)
    }

    @Test fun nothingIsReplayedBeforePlayingOrAfterStop() {
        val r = RemoteRig().apply { viewer(VE.Link("192.168.43.1")); host(HE.TouchAvailable(true)) }
        r.viewer(VE.Start(2400))
        r.touch(PointerKind.DOWN, 1, 1)
        r.touch(PointerKind.UP, 1, 1)
        r.host(HE.ConsentGranted(1080, 2400))
        // Bağlı ama ilk kare gelmedi (oynamıyor).
        r.touch(PointerKind.DOWN, 1, 1)
        r.touch(PointerKind.UP, 1, 1)
        assertTrue(r.gestures.isEmpty())
        r.frame(key = true)
        r.viewer(VE.Stop)
        r.touch(PointerKind.DOWN, 1, 1)
        r.touch(PointerKind.UP, 1, 1)
        assertTrue(r.gestures.isEmpty())
    }

    @Test fun lostStreamLiftsTheHeldFinger() {
        val r = playing()
        r.touch(PointerKind.DOWN, 1000, 1000)
        r.now += 120
        r.touch(PointerKind.MOVE, 2000, 1000)
        assertTrue(r.gestures.last().strokes.single().willContinue)
        r.breakStream()
        val lift = r.gestures.last().strokes.single()
        assertTrue(lift.continues)
        assertFalse(lift.willContinue)
    }

    @Test fun sharingStopLiftsTheHeldFinger() {
        val r = playing()
        r.touch(PointerKind.DOWN, 1000, 1000)
        r.advance(250)
        assertTrue(r.gestures.last().strokes.single().willContinue)
        r.host(HE.StopLocal)
        assertFalse(r.gestures.last().strokes.single().willContinue)
        assertEquals(HostView.State.OFF, r.hostView.state)
    }

    @Test fun heldFingerWithNoMovesKeepsGoingViaTicks() {
        val r = playing()
        r.touch(PointerKind.DOWN, 1000, 1000)
        r.advance(2_000)
        // Uzun basış: parçalar tikle gelir, paylaşan parmağı kaldırmaz.
        assertTrue(r.gestures.size >= 7)
        assertTrue(r.gestures.all { it.strokes.single().willContinue })
        r.touch(PointerKind.UP, 1000, 1000)
        assertFalse(r.gestures.last().strokes.single().willContinue)
    }

    @Test fun markerFollowsTheSharingState() {
        val r = RemoteRig().apply { viewer(VE.Link("192.168.43.1")) }
        assertEquals(null, RemoteLines.marker(r.hostView, com.kuryelog.logic.core.Lang.TR))
        r.host(HE.ShareRequested)
        assertEquals(null, RemoteLines.marker(r.hostView, com.kuryelog.logic.core.Lang.TR))
        r.host(HE.ConsentGranted(1080, 2400))
        assertEquals(RemoteText.MARK_SHARING.tr, RemoteLines.marker(r.hostView, com.kuryelog.logic.core.Lang.TR))
        r.host(HE.TouchAvailable(true))
        r.viewer(VE.Start(2400))
        r.frame(key = true)
        assertEquals(RemoteText.MARK_CONTROLLED.tr, RemoteLines.marker(r.hostView, com.kuryelog.logic.core.Lang.TR))
    }
}
