package com.kuryelog.app.map

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kuryelog.app.OrdersAccess
import com.kuryelog.app.OrdersSettingsScreen
import com.kuryelog.app.OrdersTab
import com.kuryelog.app.ScooterTab
import com.kuryelog.app.ShiftCard
import com.kuryelog.app.WeemTheme
import com.kuryelog.app.map.MapTestFixtures.fix
import com.kuryelog.app.map.MapTestFixtures.plan
import com.kuryelog.app.map.MapTestFixtures.report4
import com.kuryelog.app.map.MapTestFixtures.scooterLive
import com.kuryelog.app.scooter.Scooter
import com.kuryelog.logic.capture.CourierObservation
import com.kuryelog.logic.capture.ScreenStage
import com.kuryelog.logic.core.Clock
import com.kuryelog.logic.core.KuryelogPrefs
import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.core.LogStore
import com.kuryelog.logic.economics.EconomicsObserver
import com.kuryelog.logic.economics.OfferScoreBook
import com.kuryelog.logic.map.day.DayView
import com.kuryelog.logic.map.day.OrderPhase
import com.kuryelog.logic.map.day.ShiftEngine
import com.kuryelog.logic.map.nav.RouteProgress
import com.kuryelog.logic.map.ride.BatteryState
import com.kuryelog.logic.orders.CourierRecords
import com.kuryelog.logic.orders.LogOrdersSource
import com.kuryelog.logic.orders.OfferState
import com.kuryelog.logic.orders.OnlineSessions
import com.kuryelog.logic.orders.OrderFlow
import com.kuryelog.logic.orders.OrdersSource
import com.kuryelog.logic.orders.PickupRecord
import com.kuryelog.logic.orders.RecordJson
import com.kuryelog.logic.orders.TripPhase
import com.kuryelog.logic.orders.TripRecord
import com.kuryelog.logic.scooter.NinebotDashboard
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A10.4d görsel tur (isteğe bağlı): `WEEM_SHOTS=<klasör> ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*'` yeni tasarımdaki
 * ekranları Robolectric yerel grafikle PNG olarak çizer. Cihaz görüntüsünün yaklaşığıdır (harita altlığı düz renk, gölgeler ve yazı
 * yumuşatma cihazdakinden farklı olabilir); değişken yoksa atlanır (CI'da koşmaz).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-port-xhdpi")
class ScreenshotTour {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private val out = System.getenv("WEEM_SHOTS")?.let(::File)
    private val baku = ZoneId.of("Asia/Baku")
    private val min = 60_000L
    private val t0 = ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, baku).toInstant().toEpochMilli()
    private val now = MapTestFixtures.AT

    @Before fun onlyWhenAsked() = assumeTrue(out != null)

    /** Pencerenin kök görünümünü yazılım tuvaline çizer (`captureToImage` Robolectric'te çizim bekleyip zaman aşımına düşüyor). */
    private fun shot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        out!!.mkdirs()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---------- Siparişler ----------

    private class FixedClock(val now: Long, val zoneId: ZoneId) : Clock {
        override fun wallMs() = now
        override fun monoMs() = now
        override fun zone() = zoneId
    }

    private val clock by lazy { FixedClock(t0 + 5 * 60 * min, baku) }
    private val log by lazy { LogStore(File(tmp.root, "logs"), now = { clock.now }, zone = { baku }) }
    private val book by lazy {
        val env = object : EconomicsObserver.Env {
            override val log = this@ScreenshotTour.log
            override val prefs = KuryelogPrefs.Memory()
            override val clock: Clock = this@ScreenshotTour.clock
        }
        OfferScoreBook(EconomicsObserver(env), log::day, clock::wallMs)
    }

    private fun trip(id: String, pkg: String, at: Long, restaurant: String, gross: Double, done: Boolean = true): TripRecord {
        val obs = CourierObservation(pkg, at - min, ScreenStage.OFFER, true, "accessibility", emptyList(), emptyList(), gross = gross, km = 2.0, minLo = 10, minHi = 14, restaurant = restaurant)
        return TripRecord(id, pkg, OfferState("o-$id", pkg, at - min, obs), at, if (done) TripPhase.FINISHED else TripPhase.TO_CUSTOMER).also { t ->
            t.pickups.add(PickupRecord(restaurant, "Test Cad. 1", arrivedAt = at + 5 * min, pickedAt = at + 9 * min, arrivalSource = "gps_dwell"))
            if (done) t.completedAt = at + 20 * min
        }
    }

    private fun ordersSource(): OrdersSource {
        val wolt = "com.wolt.courierapp"
        val bolt = "ee.mtakso.driver"
        val db = CourierRecords.Memory()
        val s = OnlineSessions.Closed(wolt, t0, t0 + 4 * 60 * min, true, true, "offline_screen", 9, 5, 4, rejected = 3)
        File(log.dayDir(log.day(t0)).apply { mkdirs() }, OrderFlow.ONLINE_SESSIONS_FILE).appendText(OnlineSessions.json(s).toString() + "\n")
        db.saveTrip(RecordJson.trip(trip("a", wolt, t0 + 10 * min, "Pizza Mizza", 5.4)))
        db.saveTrip(RecordJson.trip(trip("b", bolt, t0 + 70 * min, "Kebab House", 4.8)))
        db.saveTrip(RecordJson.trip(trip("c", wolt, t0 + 130 * min, "Coffee Moffie", 4.9)))
        db.saveTrip(RecordJson.trip(trip("d", wolt, t0 + 200 * min, "Sushi Bar 28", 6.2, done = false)))
        db.saveTrip(RecordJson.trip(trip("e", wolt, t0 - 24 * 60 * min, "Dolma Restoran", 6.1)))
        val prefs = KuryelogPrefs.Memory().also { it.put(mapOf(KuryelogPrefs.MANUAL_POLICY to 1, KuryelogPrefs.SHIFT to true, KuryelogPrefs.MANUAL_SESSION to true)) }
        return OrdersAccess.withTools(OrdersAccess.withOfferScores(LogOrdersSource(log, db, prefs, clock), book), { setOf(wolt, bolt) }, { 0L })
    }

    @Test @Config(qualifiers = "w392dp-h2300dp-port-xhdpi")
    fun ordersDayTr() {
        compose.setContent { WeemTheme(dark = false) { OrdersTab(Lang.TR, { ordersSource() }, { book }) } }
        shot("02_siparisler_gunduz_tr")
    }

    @Test @Config(qualifiers = "w392dp-h2300dp-port-xhdpi")
    fun ordersNightAz() {
        compose.setContent { WeemTheme(dark = true) { OrdersTab(Lang.AZ, { ordersSource() }, { book }) } }
        shot("03_siparisler_gece_az")
    }

    // ---------- Hava simgeleri (S42) ----------

    /** Bütün simgeler: hap (28 dp, durağan), saat karosu (~32 dp) ve ayrıntı (52 dp) boyunda, iki temada; zemin hap ve karo rengi. */
    @Test @Config(qualifiers = "w392dp-h900dp-port-xhdpi")
    fun weatherGlyphs() {
        compose.setContent {
            androidx.compose.foundation.layout.Row {
                for (dark in listOf(false, true)) WeemTheme(dark) {
                    val p = com.kuryelog.app.ui.palette()
                    androidx.compose.foundation.layout.Column(Modifier.background(Color(p.surface)).padding(6.dp)) {
                        for (g in com.kuryelog.logic.map.weather.WeatherGlyph.entries) androidx.compose.foundation.layout.Row(Modifier.padding(2.dp)) {
                            WeatherGlyphIcon(g, 28.dp, animate = false)
                            Box(Modifier.padding(start = 6.dp).background(Color(p.raised)).padding(4.dp)) { WeatherGlyphIcon(g, 32.dp, animate = false) }
                            Box(Modifier.padding(start = 6.dp)) { WeatherGlyphIcon(g, 52.dp, animate = false) }
                        }
                    }
                }
            }
        }
        shot("30_hava_simgeleri")
    }

    // ---------- Harita ----------

    private fun day(phase: OrderPhase): DayView {
        val e = ShiftEngine().also { it.startDay(now); it.startOrder(now); it.stageFromFeed(phase, now) }
        return DayView(e.s, null)
    }

    private fun mapGround(dark: Boolean) = Modifier.fillMaxSize().background(Color(if (dark) 0xFF1A1D23 else 0xFFEDEFEA))

    @Test
    fun mapShiftClosedTr() {
        compose.setContent {
            WeemTheme(dark = false) {
                MapTab(
                    MapTabState(DayView(com.kuryelog.logic.map.day.DayState(), null), weather = WeatherView(report = report4()), nowMs = now), Lang.TR,
                    mapSurface = { Box(mapGround(false)) },
                    topCard = { ShiftCard(Lang.TR, Modifier.padding(bottom = 12.dp), floating = true) },
                )
            }
        }
        shot("01_harita_vardiya_kapali_tr")
    }

    @Test
    fun mapRideNightTr() {
        val p = plan()
        val metrics = MapMetricsState(
            plan = p, measure = RouteProgress.measure(p, fix(200.0)), battery = BatteryState(livePercent = 78.0, liveExternalPercent = 64.0),
            scooter = scooterLive(), nowMs = now,
        )
        compose.setContent {
            WeemTheme(dark = true) {
                MapTab(
                    MapTabState(day(OrderPhase.TO_CUSTOMER), metrics = metrics, weather = WeatherView(report = report4()), nowMs = now), Lang.TR,
                    mapSurface = { Box(mapGround(true)) },
                )
            }
        }
        shot("04_harita_surus_gece_tr")
    }

    // ---------- Scooter ----------

    /** Canlı kare: `Snapshot`ın kurucusu `logic/scooter`'a özel; görsel tur yansımayla kurar (yalnız bu deneme). */
    private fun liveFrame(): NinebotDashboard.Snapshot {
        val ctor = NinebotDashboard.Snapshot::class.java.declaredConstructors.single { it.parameterCount == 13 }
        ctor.isAccessible = true
        return ctor.newInstance(23.0, 310.0, 48.6, 6.4, 78.0, 31.2, 12.3, 1500.0, 34.0, 27.0, 64.0, 1234.5, SystemClock.elapsedRealtime()) as NinebotDashboard.Snapshot
    }

    /** `Scooter.frame` yazımı modüle özel; görsel tur canlı kareyi yansımayla koyar. */
    private fun setFrame(f: NinebotDashboard.Snapshot?) {
        Scooter::class.java.getDeclaredField("frame").apply { isAccessible = true }.set(null, f)
    }

    @Test
    fun scooterNightTr() {
        setFrame(liveFrame())
        compose.setContent { WeemTheme(dark = true) { ScooterTab(Lang.TR, dark = true, immersive = false, onImmersive = {}) } }
        shot("05_scooter_gece_tr")
        setFrame(null)
    }

    @Test
    fun scooterWaitingDayAz() {
        setFrame(null)
        compose.setContent { WeemTheme(dark = false) { ScooterTab(Lang.AZ, dark = false, immersive = false, onImmersive = {}) } }
        shot("06_scooter_veri_bekleniyor_gunduz_az")
    }

    // ---------- Ayar ekranı (önceki oturumda geçti) ----------

    @Test
    fun ordersSettingsTr() {
        compose.setContent { WeemTheme(dark = false) { OrdersSettingsScreen(Lang.TR) {} } }
        shot("07_siparis_ayarlari_tr")
    }
}
