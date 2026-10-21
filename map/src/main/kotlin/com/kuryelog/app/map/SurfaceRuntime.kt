package com.kuryelog.app.map

import android.content.Context
import android.os.SystemClock
import com.kuryelog.app.core.ShiftHub
import com.kuryelog.logic.map.day.DayState
import com.kuryelog.logic.map.nav.NavController
import com.kuryelog.logic.map.surface.SurfaceFeed
import com.kuryelog.logic.map.surface.SurfaceFeedConfig
import com.kuryelog.logic.map.surface.SurfaceLeg
import com.kuryelog.logic.map.surface.SurfaceProcessor
import com.kuryelog.logic.map.surface.SurfaceProfile
import com.kuryelog.logic.map.surface.SurfaceRow
import com.kuryelog.logic.map.surface.SurfaceStore
import com.kuryelog.logic.map.surface.SurfaceSummary
import com.kuryelog.logic.map.surface.SurfaceView
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Y13 yüzey ve titreşim ölçümünün çalışma zamanı (A10.4b; MAP.md 9, YolGo `SurfaceController` + `TelemetryService`): gün açıkken sensörler [SurfaceSensors]
 * ile açılır, satırlar [SurfaceFeed] → [SurfaceProcessor] → [SurfaceStore] (`files/surface-v2/`). Etap kimliği [SurfaceLeg] (gün + sipariş aşaması + gerçek
 * navigasyon hedefi; rotasız sürüş "serbest" etapta). Hareket kapısı yalnız scooterla yolda giderken ölçer; yürüme, başka araç ve tekerlek dururken telefon
 * hareketi elenir. Duraklatma günün kimliğine bağlıdır. Hiçbir siparişi ilerletmez, Wolt/Bolt'a dokunmaz. Cihazda denenmedi.
 */
class SurfaceRuntime internal constructor(
    private val app: Context,
    private val nav: NavController,
    private val scope: CoroutineScope,
    private val vehicleId: () -> String,
) {
    val store = SurfaceStore(File(app.filesDir, "surface-v2"))
    private val prefs = MapPrefsStore(app)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "weem-surface-io").apply { isDaemon = true } }

    private val processor = SurfaceProcessor(
        store, { MapData.controller(app).state.value.graph }, { BatteryRuntime.get(app).live.value?.scooter?.speedKmh },
    )
    private val feed: SurfaceFeed = SurfaceFeed(
        boot = UUID.randomUUID().toString(), emit = ::onRow, elapsedRealtimeNs = SystemClock::elapsedRealtimeNanos, utcNow = System::currentTimeMillis,
        activityAvailable = { sensors.stepsAvailable() },
    )
    private val sensors: SurfaceSensors = SurfaceSensors(app, feed)

    private val viewFlow = MutableStateFlow(SurfaceView())
    private val summariesFlow = MutableStateFlow<List<SurfaceSummary>>(emptyList())

    /** Hareket kapısının kararı, tamponda bekleyen ve kaydedilen pencere sayısı. */
    val view: StateFlow<SurfaceView> get() = viewFlow

    /** Kesim özetleri (50 m parçalar; her değişimde). */
    val summaries: StateFlow<List<SurfaceSummary>> get() = summariesFlow

    /** Etiket: "smooth" (bilinen düzgün yüzey) / "rough" (bilinen bozuk yüzey) / null — kontrollü kalibrasyon; normal sürüşler referansı değiştirmez. */
    @Volatile var calibrationLabel: String? = null
        private set

    @Volatile private var day: String? = null
    @Volatile private var lastConfig: SurfaceFeedConfig? = null

    val paused: Boolean get() = processor.gate.manualPaused

    init {
        summariesFlow.value = store.summaries
        store.onChanged { summariesFlow.value = it }
        DayDeletionRuntime.get(app).deleteSurface = ::deleteDate
        scope.launch {
            while (isActive) {
                evaluate()
                delay(EVAL_MS)
            }
        }
    }

    private fun onRow(row: SurfaceRow) {
        io.execute {
            runCatching { processor.process(listOf(row)) }
            viewFlow.value = processor.view
        }
    }

    private fun currentDay(): DayState? = DayRuntime.get(app).view.value.state.takeIf { it.active }

    /** Gün, etap, duraklama ve profil değişince hattı yeniden ayarlar; gün kapanınca sensörler kapanır. */
    @Synchronized
    private fun evaluate() {
        val d = currentDay()
        val dayId = d?.startedAtMs?.let { "yolgo-$it" }
        if (dayId != day) {
            day = dayId; calibrationLabel = null
            processor.pause(dayId != null && prefs.getString(PAUSED_DAY) == dayId)
        }
        val navState = nav.state.value
        val leg = d?.let { SurfaceLeg.actual(it, navState.activeStop, navState.hasRoute) }
        val vehicle = vehicleId()
        var profile = store.profile
        if (profile.vehicleId != vehicle) {
            // Araç değişti: yeni kurulum = yeni profil (eski ölçümler korunur).
            profile = SurfaceProfile(id = "$vehicle-${System.currentTimeMillis()}", name = "$vehicle · yeni montaj", vehicleId = vehicle)
            runCatching { store.saveProfile(profile) }
        }
        val config = SurfaceFeedConfig(day = dayId, leg = leg, eligible = leg != null, paused = processor.gate.manualPaused, calibrationLabel = calibrationLabel, profile = profile)
        if (config != lastConfig) {
            lastConfig = config
            feed.configure(config)
        }
        sensors.setActive(dayId != null && ShiftHub.state.value.active && leg != null && !config.paused)
    }

    /** Ölçümü duraklat/sürdür (günün kimliğine bağlı; yeni gün duraklamayı taşımaz). */
    fun pause(paused: Boolean) {
        val id = day
        processor.pause(paused)
        if (paused && id != null) prefs.setString(PAUSED_DAY, id) else prefs.remove(PAUSED_DAY)
        evaluate()
    }

    /** Kontrollü kalibrasyon etiketi: "smooth", "rough" ya da null (kapalı). */
    fun setCalibrationLabel(label: String?) {
        calibrationLabel = label?.takeIf { it == "smooth" || it == "rough" }
        evaluate()
    }

    /** Montaj yönü: telefon sabit gidonda ve scooter dururken son güvenilir yönelim (dünya "yukarı" vektörü); koşul sağlanmıyorsa null. */
    fun mountCandidate(): DoubleArray? = feed.mountCandidate()

    /** "Montaj yönünü kaydet": doğrulanan yönle yeni bir kurulum profili açar (eski ölçümler korunur); doğrulanamazsa false. */
    fun saveMount(): Boolean {
        val up = mountCandidate() ?: return false
        val p = store.profile
        store.saveProfile(SurfaceProfile.fromJson(p.toJson().put("id", "${vehicleId()}-mount-${System.currentTimeMillis()}").put("mount", org.json.JSONArray(up.toList())).put("version", 1).put("bands", org.json.JSONObject())))
        evaluate()
        return true
    }

    /** Gün silindi: tampondaki ve kayıtlı o güne ait ölçümler gider. */
    fun deleteDate(date: String): Set<String> {
        val ids = runCatching { store.deleteDate(date) }.getOrDefault(emptyList())
        ids.forEach { processor.dropDay(it) }
        return ids.toSet()
    }

    private companion object {
        const val PAUSED_DAY = "surface.paused.day"
        const val EVAL_MS = 1_000L
    }
}
