package com.kuryelog.logic.remote

import com.kuryelog.logic.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTextTest {
    @Test fun everyTextIsInBothLanguagesWithSamePlaceholders() {
        val ph = Regex("%[ds]")
        for (t in RemoteText.entries) {
            assertTrue(t.name, t.tr.isNotBlank() && t.az.isNotBlank())
            assertEquals(t.name, ph.findAll(t.tr).count(), ph.findAll(t.az).count())
        }
    }

    @Test fun everyStateHasALine() {
        assertNull(RemoteLines.viewer(ViewerView.IDLE, Lang.TR))
        for (s in ViewerView.State.entries.filter { it != ViewerView.State.IDLE && it != ViewerView.State.ENDED }) {
            assertTrue(RemoteLines.viewer(ViewerView(s), Lang.AZ)!!.isNotBlank())
        }
        val ends = ViewerView.EndReason.entries.map { RemoteLines.viewer(ViewerView(ViewerView.State.ENDED, ended = it), Lang.TR) }
        assertEquals(ends.size, ends.toSet().size)
        assertEquals("576×1280 · 30 kare/sn · 2200 kb/sn", RemoteLines.viewer(ViewerView(ViewerView.State.PLAYING, 576, 1280, 30, 2200), Lang.TR))
        assertEquals("Ekran paylaşılıyor · öteki telefon izliyor · 900 kb/sn", RemoteLines.host(HostView(HostView.State.SHARING, true, 900), Lang.TR))
        assertEquals(RemoteText.H_OFF.tr, RemoteLines.host(HostView.OFF, Lang.TR))
        assertTrue(RemoteLines.canView(ViewerView.IDLE))
        assertTrue(!RemoteLines.canView(ViewerView(ViewerView.State.CONSENT)))
    }
}
