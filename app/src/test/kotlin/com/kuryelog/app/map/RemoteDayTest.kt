package com.kuryelog.app.map

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.day.DaySnapshot
import com.kuryelog.logic.map.day.DayStep
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.OrderRec
import com.kuryelog.logic.map.stats.DayReportFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A10.4i (S35 #17, #18): Provider'da gün Receiver'dan gelir; görev/hatırlatıcı dinleyicileri teslimi duyar; gelen gün raporu İstatistik'e girer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RemoteDayTest {
    private val t0 = 1_792_000_000_000L

    private fun day(delivered: Int) = DayState().apply {
        active = true; startedAtMs = t0; distM = 8_000.0
        repeat(delivered) { orders += OrderRec(it + 1, t0 + it * 60_000L).apply { deliveredAtMs = startedAtMs + 30_000 } }
        this.delivered = delivered
    }

    @Test fun providerShowsTheReceiversDay() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val rt = DayRuntime.get(app)
        val remoteSteps = ArrayList<DayStep>()
        val localSteps = ArrayList<DayStep>()
        rt.addStepListener({ remoteSteps += it }, remoteToo = true)
        rt.addStepListener { localSteps += it }
        rt.setRemote(true)
        assertFalse(rt.view.value.state.active)
        rt.showRemote(DaySnapshot.decode(DaySnapshot.encode(DayView(day(1), null), t0))!!)
        rt.showRemote(DaySnapshot.decode(DaySnapshot.encode(DayView(day(2), null), t0 + 60_000))!!)
        assertTrue(rt.view.value.state.active)
        assertEquals(8_000.0, rt.view.value.state.distM, 0.6)
        assertEquals(t0, rt.activeDay()!!.startedAtMs)
        assertEquals(listOf("delivered"), remoteSteps.flatMap { s -> s.events.map { it.type } })
        assertTrue("report writer and ride log do not hear the remote day", localSteps.none { it.view.state.startedAtMs == t0 })
        rt.setRemote(false)
        assertFalse(rt.remote)
    }

    @Test fun incomingReportLandsInTheStatsHistory() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val w = DayReportWriter.get(app)
        val name = "2026-10-21_0930.gun.json"
        val (n, text) = DaySnapshot.readReport(DaySnapshot.report(name, DayReportFile.encode(day(3).apply { active = false; endedAtMs = t0 + 3_600_000 }, t0, 0))!!)!!
        w.accept(n, text)
        w.waitIdle()
        assertTrue(File(app.filesDir, "map/logs/$name").isFile)
        assertTrue(w.files().any { it.startedAtMs == t0 && it.delivered == 3 })
    }
}
