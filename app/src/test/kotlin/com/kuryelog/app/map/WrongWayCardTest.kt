package com.kuryelog.app.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kuryelog.app.WeemTheme
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.roads.LearnFix
import com.kuryelog.logic.map.roads.RoadBook
import com.kuryelog.logic.map.roads.RoadFiles
import com.kuryelog.logic.map.route.StreetPrefsStore
import com.kuryelog.logic.map.settings.MapPrefs
import com.kuryelog.logic.map.settings.MapSettings
import com.kuryelog.logic.map.ui.Toasts
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * YolGo `ww_no_problem_green_test.dart` son test (TR/AZ döngüsü; A6.11, MAP.md 9.11): kart "Hayır"/"Xeyr" dokunuşu iki parçayı onaylar, toast metni.
 * Saf akış `WwAnswerFlowTest`'te; burada Compose kartı + gerçek `RoadBook`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WrongWayCardTest {
    @get:Rule val compose = createComposeRule()

    private class Prefs : MapPrefs {
        val m = HashMap<String, Any>()
        @Suppress("UNCHECKED_CAST") override fun getStringList(key: String) = m[key] as List<String>?
        override fun setStringList(key: String, value: List<String>) { m[key] = value }
        override fun getBool(key: String) = m[key] as Boolean?
        override fun setBool(key: String, value: Boolean) { m[key] = value }
        override fun getString(key: String) = m[key] as String?
        override fun setString(key: String, value: String) { m[key] = value }
        override fun getDouble(key: String) = m[key] as Double?
        override fun setDouble(key: String, value: Double) { m[key] = value }
        override fun getLong(key: String) = m[key] as Long?
        override fun setLong(key: String, value: Long) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
    }

    private class Files : RoadFiles {
        val m = HashMap<String, String>()
        override fun read(name: String) = m[name]
        override fun write(name: String, text: String) { m[name] = text }
    }

    private class FixedClock : Clock {
        var now = 1_790_000_000_000L
        override fun wallMs() = now
        override fun monoMs() = now
        override fun zone(): ZoneId = ZoneOffset.ofHours(4)
    }

    @Test
    fun hayirXeyrDokunusuIkiParcayiOnaylarVeToastMetni() {
        var lang by mutableStateOf(Lang.TR)
        var view by mutableStateOf(WrongWayView())
        var answer: (Boolean) -> Unit = {}
        compose.setContent {
            WeemTheme(false) { WrongWayCard(view, lang, onAnswer = { answer(it) }, onReport = {}) }
        }
        for (l in listOf(Lang.TR, Lang.AZ)) {
            val clock = FixedClock()
            val streets = StreetPrefsStore(Prefs())
            val toasts = Toasts()
            val book = RoadBook(Files(), streets, toasts, { MapSettings(wwAskAlways = true) }, clock, { l })
            book.startDay("yolgo-${clock.now}")
            streets.approve(100, "Test küçəsi")
            var north = 0.0
            fun feed(way: Long, seconds: Int, wrongWay: Boolean = true, approved: Boolean = false) = repeat(seconds) {
                clock.now += 1000
                north += 5
                book.onFix(LearnFix(clock.now, 40.4 + north / 111320, 49.85, 18.0, way = way, name = if (way == 102L) "Yan küçə" else "Test küçəsi", wrongWay = wrongWay, approved = approved))
            }
            feed(101, 20)
            feed(100, 20, approved = true)
            feed(102, 1, wrongWay = false)
            val q = book.state.question!!
            answer = { book.answer(it); view = WrongWayView() }
            lang = l
            view = WrongWayView(true, q.pass.name, q.pass.incidents, 20)
            compose.waitForIdle()
            compose.onAllNodes(hasText(if (l == Lang.AZ) "Əks istiqamətdə problem oldu?" else "Ters yönde sorun yaşadın mı?")).assertCountEquals(1)
            compose.onAllNodes(hasText(if (l == Lang.AZ) "Səs+ hə / Səs− yox" else "Ses+ evet / Ses− hayır", substring = true)).assertCountEquals(1)
            compose.onAllNodes(hasText(if (l == Lang.AZ) "Xeyr" else "Hayır")).assertCountEquals(1)
            compose.onNodeWithTag("ww-no").performClick()
            compose.waitForIdle()
            assertNull(book.state.question)
            assertEquals(setOf(100L, 101L), streets.current.approved)
            assertEquals(if (l == Lang.AZ) "Təsdiqləndi: Test küçəsi (iki tərəfli)" else "Onaylandı: Test küçəsi (iki yön)", toasts.current?.text)
            compose.onAllNodes(hasText(if (l == Lang.AZ) "Xeyr" else "Hayır")).assertCountEquals(0)
        }
    }
}
