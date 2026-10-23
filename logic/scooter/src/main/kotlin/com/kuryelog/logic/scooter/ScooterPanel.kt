package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.Lang
import java.util.Locale
import com.kuryelog.logic.scooter.ScooterUiText as T

/**
 * Scooter sekmesinin saf kuralları (A5.4; SCOOTER.md 6.2): durum satırı ve noktası, eylem çubuğunun hangi düğmeleri açık, bayatlık,
 * sayaç satırı, gösterge renk eşikleri, tarama listesi, dışa aktarma seçenekleri. NB `DashboardActivity.syncToolbar`/`secondPack`/`scan`/
 * `export` ve `DashboardView`'dan aynen; Compose ekranı yalnız çizer.
 */
object ScooterPanel {
    /** Kare bundan eskiyse "VERİ BEKLENİYOR" (NB `DashboardView.STALE_MS`); değerler ara değerlenmez. */
    const val STALE_MS = 5_000L

    /** Kalp atışı: bayatlık, çubuk ve sayaç saniyede bir. */
    const val HEARTBEAT_MS = 1_000L

    /** Tarama süresi (NB `SCAN_MS`, 0.22.0: 8 → 4 sn); sonuçlar canlı. */
    const val SCAN_MS = 4_000L

    /** Bilinen cihazın listesi bu kadar dB değişince yenilenir. */
    const val RSSI_STEP = 6

    /** "30 günden eski kayıtları temizle" (NB `cleanOldRecords`). */
    const val CLEAN_MS = 30L * 24 * 60 * 60 * 1000

    fun live(frame: NinebotDashboard.Snapshot?, nowMono: Long): Boolean = frame != null && nowMono - frame.monoMs <= STALE_MS

    /** Servisin dışa açık durumu ([status], [dot], [dock] girdisi). `frame` = son kare (bağlantıda değer var). */
    data class State(
        val active: Boolean,
        val recording: Boolean,
        val hostLogging: Boolean,
        val maintenance: Boolean,
        val warning: String,
        val linked: Boolean,
        val extBatteryPct: Double?,
        val externalBoards: String,
    )

    enum class Dot { WARN, RECORDING, LINKED, ACTIVE, IDLE }

    /** Durum satırı: uyarı varsa o; bağlantıda kip + bağlı/bekleniyor (+ ikinci batarya); boşta "MAX G3 • hazır". */
    fun status(s: State, lang: Lang): String {
        if (s.warning.isNotEmpty()) return ScooterStatusText.localize(s.warning, lang)
        if (!s.active) return T.READY.get(lang)
        val mode = (if (s.recording) T.MODE_RECORDING else if (s.hostLogging) T.MODE_HOST else T.MODE_LIVE).get(lang)
        return mode + if (s.linked) T.LINKED.get(lang) + secondPack(s, lang) else T.WAITING.get(lang)
    }

    /**
     * Batarya kutusunun alt satırı: ikinci (harici) batarya görünürken "2. batarya %100" (menzil biliniyorsa " · 23.4 km"), yoksa "23.4 km menzil" (S38: ikinci
     * batarya yalnız durum satırındaydı, kutuda görünmüyordu).
     */
    fun batterySub(rangeKm: Double?, extPct: Double?, lang: Lang): String {
        val range = NinebotDashboard.oneDecimal(rangeKm)
        if (extPct == null) return T.RANGE_SUB.get(lang).format(range)
        val ext = T.EXT_SUB.get(lang).format(NinebotDashboard.whole(extPct))
        return if (rangeKm == null) ext else "$ext · $range km"
    }

    /** 0.18.0: bağlıyken ikinci (harici) bataryanın cevap verip vermediği. */
    fun secondPack(s: State, lang: Lang): String = when {
        s.extBatteryPct != null -> T.PACK_VISIBLE.get(lang).format(Math.round(s.extBatteryPct).toString())
        s.externalBoards.isEmpty() -> T.PACK_NONE.get(lang)
        else -> T.PACK_WAITING.get(lang)
    }

    fun dot(s: State): Dot = when {
        s.warning.isNotEmpty() -> Dot.WARN
        s.recording -> Dot.RECORDING
        s.active && s.linked -> Dot.LINKED
        s.active -> Dot.ACTIVE
        else -> Dot.IDLE
    }

    data class Dock(val pair: Boolean, val stop: Boolean, val scan: Boolean, val export: Boolean)

    /**
     * S37: "Kaydı başlat" yok (kayıt vardiyayla başlar); durdur yalnız vardiya dışındaki bağlantıyı (eşleştirme sonrası canlı panel) keser,
     * vardiyada kayıt vardiyayla biter. Dışa aktarma kayıt ve temizlik sürerken kapalı.
     */
    fun dock(s: State, exporting: Boolean, scanning: Boolean, shiftOn: Boolean = false): Dock {
        val idle = !s.active && !exporting && !s.maintenance
        return Dock(
            pair = idle,
            stop = s.active && !shiftOn,
            scan = idle && !scanning,
            export = !s.recording && !exporting && !s.maintenance,
        )
    }

    /** S37: eylem çubuğundaki kayıt durumu (eski "Kaydı başlat" düğmesinin yeri). [tone] renk: hazır yeşil, kayıt kırmızı, eksik turuncu; null nötr. */
    enum class Badge(val text: T, val tone: Tone?) {
        PROVIDER(T.BADGE_PROVIDER, null),
        NO_SCOOTER(T.BADGE_NO_SCOOTER, null),
        NOT_PAIRED(T.BADGE_NOT_PAIRED, Tone.WARN),
        PAIRING(T.BADGE_PAIRING, Tone.ACCENT),
        READY(T.BADGE_READY, Tone.POSITIVE),
        RECORDING(T.BADGE_RECORDING, Tone.DANGER),
        WAITING(T.BADGE_WAITING, Tone.DANGER),
        NOT_CONNECTED(T.BADGE_NOT_CONNECTED, Tone.WARN),
    }

    /**
     * Kayıt durumu: Provider'da scooter Receiver'da; kayıt açıksa veri gelip gelmediği; seçilmemiş, eşleşmemiş (bağlantı açıkken
     * "eşleştiriliyor"); vardiya açık ama kayıt yoksa uyarı; yoksa "Eşleşildi · kayda hazır".
     */
    fun badge(s: State, selected: Boolean, paired: Boolean, shiftOn: Boolean, providerRole: Boolean): Badge = when {
        providerRole -> Badge.PROVIDER
        s.recording -> if (s.linked) Badge.RECORDING else Badge.WAITING
        !selected -> Badge.NO_SCOOTER
        !paired -> if (s.active) Badge.PAIRING else Badge.NOT_PAIRED
        shiftOn -> Badge.NOT_CONNECTED
        else -> Badge.READY
    }

    /** Ekran açık: bağlantı varken ve `keep_screen_on` (varsayılan true). */
    fun keepScreenOn(active: Boolean, pref: Boolean): Boolean = active && pref

    /** Sipariş sayacı sıfırladıysa (etiket = sipariş kimliği) "BU SİPARİŞ", yoksa "BU SÜRÜŞ". */
    fun scope(t: TripMeter.Stats?, lang: Lang): String = (if (t != null && t.label.isNotEmpty()) T.SCOPE_ORDER else T.SCOPE_RIDE).get(lang)

    /** Göstergenin altındaki satır: kapsam + dokunma ipucu. */
    fun hint(t: TripMeter.Stats?, immersive: Boolean, lang: Lang): String = scope(t, lang) + "  •  " + (if (immersive) T.TAP_MENU else T.TAP_FULL).get(lang)

    /** NB `DashboardView.tripLine`: Wh, kullanılan pil, ortalama hız; hiçbiri yoksa null. */
    fun tripLine(t: TripMeter.Stats?, lang: Lang): String? {
        if (t == null || t.wh == null && t.avgMovingKmh == null) return null
        val b = StringBuilder(scope(t, lang)).append("  ")
        b.append(if (t.wh == null) NinebotDashboard.DASH else String.format(Locale.ROOT, "%.1f", t.wh)).append(" Wh")
        if (t.pctUsed != null) b.append(String.format(Locale.ROOT, "  •  %%%.0f", t.pctUsed))
        b.append("  •  ").append(T.AVG_SHORT.get(lang)).append(' ')
            .append(if (t.avgMovingKmh == null) NinebotDashboard.DASH else String.format(Locale.ROOT, "%.1f", t.avgMovingKmh)).append(" km/h")
        return b.toString()
    }

    /** Kadran doluluğu 0–1; değer yoksa null (yay çizilmez). */
    fun frac(v: Double?, lo: Double, hi: Double): Double? = if (v == null) null else Math.max(0.0, Math.min(1.0, (v - lo) / (hi - lo)))

    enum class Tone { POSITIVE, ACCENT, POWER, WARN, DANGER }

    /** Batarya kadranı: <15 kırmızı, <40 sarı, yoksa yeşil. */
    fun batteryTone(pct: Double?): Tone = if (pct == null) Tone.POSITIVE else if (pct < 15) Tone.DANGER else if (pct < 40) Tone.WARN else Tone.POSITIVE

    /** Sıcaklık kadranı: ≥55 °C kırmızı. */
    fun tempTone(c: Double?): Tone = if (c != null && c >= 55) Tone.DANGER else Tone.ACCENT

    /** Ekran okuyucu metni (NB `DashboardView.onInitializeAccessibilityNodeInfo`). */
    fun accessibility(s: NinebotDashboard.Snapshot?, lang: Lang): String = T.PANEL_A11Y.get(lang).format(
        NinebotDashboard.speed(s?.speedKmh), NinebotDashboard.watts(s?.powerW), NinebotDashboard.whole(s?.batteryPct),
        NinebotDashboard.oneDecimal(s?.rangeKm), NinebotDashboard.oneDecimal(s?.voltageV), NinebotDashboard.oneDecimal(s?.currentA),
        NinebotDashboard.whole(s?.tempC),
    )

    /** ZIP içeriği seçenekleri (SCOOTER.md 7.4; NB `export`: 3 → tam, >0 → konum, ≠2 → son kayıt). */
    enum class ExportChoice(val text: T, val location: Boolean, val latest: Boolean, val full: Boolean) {
        LATEST(T.EXPORT_LATEST, false, true, false),
        LATEST_GPS(T.EXPORT_LATEST_GPS, true, true, false),
        ALL_GPS(T.EXPORT_ALL_GPS, true, false, false),
        FULL(T.EXPORT_FULL, true, true, true),
        ;

        fun options(): LogExport.Options = LogExport.Options(location, latest, full)

        /** Onay penceresi (başlık, metin, devam düğmesi); konumsuz son kayıt onaysız. */
        fun warning(): Triple<T?, T, T>? = when {
            full -> Triple(T.FULL_TITLE, T.FULL_TEXT, T.FULL_GO)
            location -> Triple(null, T.GPS_TEXT, T.GO_ON)
            else -> null
        }

        fun done(): T = if (full) T.EXPORT_DONE_FULL else if (location) T.EXPORT_DONE_GPS else T.EXPORT_DONE
    }

    /**
     * Canlı tarama listesi (NB `scan`, `showFound`): adres başına bir satır; ad ilk gelişte yazılır; sıra kayıtlı cihaz, adlı, adsız ve
     * grup içinde ilk görülme (satırlar parmağın altında kaymaz, yalnız dBm değişir). Ana iş parçacığında kullanılır.
     */
    class ScanList {
        class Found internal constructor(val address: String) {
            var name: String? = null
                internal set
            var rssi = 0
                internal set
            val named: Boolean get() = !name.isNullOrEmpty()
        }

        private val found = LinkedHashMap<String, Found>()

        /** Bir tarama sonucu; true = gösterilen liste yenilenmeli (yeni cihaz, ad geldi ya da RSSI ≥ [RSSI_STEP] dB oynadı). */
        fun seen(address: String, name: String?, rssi: Int): Boolean {
            var f = found[address]
            val fresh = f == null
            if (f == null) { f = Found(address); found[address] = f }
            val gotName = !name.isNullOrEmpty() && !f.named
            if (gotName) f.name = name
            val before = f.rssi
            f.rssi = rssi
            return fresh || gotName || Math.abs(before - rssi) >= RSSI_STEP
        }

        operator fun get(address: String): Found? = found[address]
        val size: Int get() = found.size
        fun isEmpty(): Boolean = found.isEmpty()
        fun clear() = found.clear()

        fun ordered(saved: String): List<Found> =
            found.values.sortedBy { if (it.address == saved) 0 else if (it.named) 1 else 2 }

        companion object {
            /** "<ad | İsimsiz BLE cihazı> • <adres> • <rssi> dBm". */
            fun label(f: Found, lang: Lang): String = (if (f.named) f.name!! else T.UNNAMED.get(lang)) + " • " + f.address + " • " + f.rssi + " dBm"

            /** `device_name` tercihine yazılan etiket: veri (servis ilk parçasını ad olarak kullanır), her dilde Sürüş Paneli metni. */
            fun storedLabel(f: Found): String = label(f, Lang.TR)

            fun subtitle(f: Found, saved: Boolean, lang: Lang): String = (if (saved) T.SAVED_SCOOTER.get(lang) else "") + f.address + " • " + f.rssi + " dBm"

            fun shortName(f: Found): String = if (f.named) f.name!! else f.address
        }
    }
}
