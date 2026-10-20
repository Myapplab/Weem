package com.kuryelog.logic.map.roads

import com.kuryelog.logic.core.Lang
import com.kuryelog.logic.map.input.tl
import com.kuryelog.logic.map.route.QualityBand
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** Kalite renk sınıfı (`qualityColor`): iyi yeşil, orta turuncu, kötü kırmızı. */
enum class QualityTone { GOOD, FAIR, BAD }

/** Ters yön satırı oran rengi (MAP.md 9.8.4): veri yok soluk, ≥ eşik kırmızı, ≥ eşik/2 turuncu, aksi yeşil. */
enum class WwTone { NONE, RED, ORANGE, GREEN }

/** Ters yön sekmesi satırı. */
class WwRowView(val way: Long, val name: String, val subtitle: String, val ratioText: String, val tone: WwTone, val avoided: Boolean, val approved: Boolean)

/**
 * Yollarım ve uzun basış Y12 metinleri (TR/AZ aynen; `roads_screen.dart`, `nav_screen.dart:250-270`, `settings_screen.dart:243-306`; MAP.md 9.4–9.8).
 * Biçimler Dart'la aynı: `fmtKm` (< 1000 m "N m", aksi noktalı "2.0 km"), `fmtPct` ("%N"), dur-kalk/km 1 ondalık noktalı. SORU-Q3 (b) uygulandı:
 * "Trafik kayıtları" / "Rotayı etkilemez" ve ses tuşu metnine "Ses+ ×2 kalite".
 */
object RoadsText {
    fun km(m: Double): String = if (m < 1000) "${m.roundToLong()} m" else "${fixed1(m / 1000)} km"
    fun pct(v: Double): String = "%${(v * 100).roundToLong()}"
    fun fixed1(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

    fun tone(q: Double): QualityTone = if (q >= QualityBand.GOOD) QualityTone.GOOD else if (q >= QualityBand.FAIR) QualityTone.FAIR else QualityTone.BAD

    fun unnamed(lang: Lang) = tl(lang, "isimsiz yol", "adsız yol")

    /** Sarsıntı (`_bump`): 1.0'da titreşim ölçülmez → "?". */
    fun bump(lang: Lang, vib: Double?): String = when {
        vib == null -> tl(lang, "sarsıntı ?", "silkələnmə ?")
        vib < 2.5 -> tl(lang, "sarsıntı az", "silkələnmə az")
        vib < 4 -> tl(lang, "sarsıntı orta", "silkələnmə orta")
        else -> tl(lang, "sarsıntı çok", "silkələnmə çox")
    }

    // ---------------------------------------------------------------- Uzun basış

    /** Elle puan satırının alt yazısı. */
    fun rateSubtitle(lang: Lang, st: WayStats?): String =
        if (st == null || st.m < 50) {
            tl(lang, "Fiziksel ölçüm ayrıntıları Yollarım → Kalite bölümünde", "Fiziki ölçmə detalları Yollarım → Keyfiyyət bölməsində")
        } else {
            tl(lang, "${fixed1(st.stopsPerKm)} dur-kalk/km · ${km(st.m)} sürüldü", "${fixed1(st.stopsPerKm)} dur-qalx/km · ${km(st.m)} sürülüb")
        }

    /** 👍/👎 sonrası şerit: `r ≥ 50` yeşil "Kaliteli yol", aksi turuncu "— mümkünse kaçınılır". */
    fun rated(lang: Lang, name: String, r: Int): String =
        if (r >= 50) tl(lang, "Kaliteli yol: $name", "Keyfiyyətli yol: $name")
        else tl(lang, "Kalitesiz yol: $name — mümkünse kaçınılır", "Keyfiyyətsiz yol: $name — mümkün qədər yan keçiləcək")

    fun ghostToast(lang: Lang, name: String, all: Boolean): String {
        val n = name.ifEmpty { unnamed(lang) }
        return tl(lang, "Yol yok: $n${if (all) " (tamamı)" else ""} — rotaya girmez", "Yol yoxdur: $n${if (all) " (bütün küçə)" else ""} — marşruta düşməyəcək")
    }

    fun wwSegToast(lang: Lang, name: String, allow: Boolean): String {
        val n = name.ifEmpty { unnamed(lang) }
        return if (allow) tl(lang, "Bu kesimde ters yön serbest: $n", "Bu hissədə əks istiqamət açıqdır: $n")
        else tl(lang, "Ters yön izni kaldırıldı: $n", "Əks istiqamət icazəsi silindi: $n")
    }

    fun undo(lang: Lang) = tl(lang, "Geri al", "Geri qaytar")

    // ---------------------------------------------------------------- Yollarım

    fun title() = "Yollarım"
    fun tabs(lang: Lang) = listOf(tl(lang, "Kalite", "Keyfiyyət"), tl(lang, "Ters yön", "Əks istiqamət"), tl(lang, "Yol yok", "Yol yoxdur"), tl(lang, "Bisiklet", "Velosiped"))

    fun manualHeader(lang: Lang) = tl(lang, "Elle girilen puanlar", "Əl ilə yazılan ballar")

    /** Elle puan satırı alt yazısı. */
    fun manualRow(lang: Lang, st: WayStats?): String =
        if (st == null) tl(lang, "Elle girildi", "Özün yazmısan")
        else tl(
            lang,
            "${fixed1(st.stopsPerKm)} dur-kalk/km · ${bump(lang, st.vib)} · ${km(st.m)} · ${st.passes} geçiş",
            "${fixed1(st.stopsPerKm)} dur-qalx/km · ${bump(lang, st.vib)} · ${km(st.m)} · ${st.passes} keçid",
        )

    fun tolerance(lang: Lang) = tl(lang, "Kalite toleransı", "Keyfiyyət tolerantlığı")
    fun spotsHeader(lang: Lang) = tl(lang, "Elle işaretlenen yüzeyler", "Əl ilə qeyd olunan səthlər")
    fun spotTitle(lang: Lang, name: String) = "${name.ifEmpty { unnamed(lang) }} · ${tl(lang, "kötü nokta ±25 m", "pis nöqtə ±25 m")}"
    fun spotSubtitle(lang: Lang, removed: Boolean) = if (removed) tl(lang, "Kaldırıldı", "Silinib") else tl(lang, "Kullanıcı kaydı", "İstifadəçi qeydi")
    fun trafficHeader(lang: Lang) = tl(lang, "Trafik kayıtları", "Trafik qeydləri")
    fun trafficSubtitle(lang: Lang) = tl(lang, "Rotayı etkilemez", "Marşruta təsir etmir")
    fun trafficRow(st: WayStats, lang: Lang) = "${km(st.m)} · ${fixed1(st.stopsPerKm)} ${tl(lang, "dur-kalk/km", "dur-qalx/km")}"
    fun emptyList(lang: Lang) = tl(lang, "Liste boş", "Siyahı boşdur")

    /** Kalite sayfası alt yazısı. */
    fun sheetSubtitle(lang: Lang, q: Double, st: WayStats?): String =
        if (st == null) tl(lang, "Kalite ${pct(q)} (tahmin)", "Keyfiyyət ${pct(q)} (təxmini)")
        else tl(
            lang,
            "Kalite ${pct(q)} · ${fixed1(st.stopsPerKm)} dur-kalk/km · ${bump(lang, st.vib)} · ${km(st.m)} sürüldü",
            "Keyfiyyət ${pct(q)} · ${fixed1(st.stopsPerKm)} dur-qalx/km · ${bump(lang, st.vib)} · ${km(st.m)} sürülüb",
        )

    fun sheetGood(lang: Lang) = tl(lang, "Kaliteli yüzey · kullanıcı puanı", "Hamar səth · istifadəçi balı")
    fun sheetBad(lang: Lang) = tl(lang, "Bozuk yüzey · kullanıcı puanı", "Nahamar səth · istifadəçi balı")
    fun sheetPercent(lang: Lang) = tl(lang, "Kaliteyi % olarak gir", "Keyfiyyəti faizlə yaz")
    fun sheetClear(lang: Lang) = tl(lang, "Elle puanı kaldır", "Yazdığım faizi sil")
    fun sheetReset(lang: Lang) = tl(lang, "Öğrenileni sıfırla", "Öyrəniləni sıfırla")

    fun wwInfoTitle(lang: Lang) = tl(lang, "Ters yönde sorun oranı", "Əks istiqamətdə problem faizi")
    fun wwInfo(lang: Lang) = tl(
        lang,
        "Onaylı sokaklarda ters yöndeki her geçiş sayılır. Sert fren ya da durma olursa \"Sorun yaşadın mı?\" diye sorulur (Ses+ evet · Ses− hayır); " +
            "\"Hayır\" deyince o geçişin bütün parçaları onaylı (yeşil) olur. Ekrandaki \"Sorun yaşadım\" ile de eklenir. Oran eşiği aşan sokakta " +
            "(en az 3 geçiş) rota ters yöne girmez.",
        "Təsdiqli küçələrdə əks istiqamətdə hər keçid sayılır. Qəfil əyləc və ya dayanma olsa \"Problem oldu?\" deyə soruşulur (Səs+ hə · Səs− yox); " +
            "\"Xeyr\" desən, həmin keçidin bütün hissələri təsdiqli (yaşıl) olur. Ekrandakı \"Problem oldu\" düyməsi ilə də əlavə olunur. Faizi həddi " +
            "keçən küçəyə (ən az 3 keçid) marşrut əks istiqamətdə girmir.",
    )
    fun wwThreshold(lang: Lang) = tl(lang, "Sorun eşiği", "Problem həddi")
    fun wwAskAlways(lang: Lang) = tl(lang, "Her geçişten sonra sor", "Hər keçiddən sonra soruş")
    fun wwAskAlwaysSub(lang: Lang) = tl(lang, "Kapalı: sadece sert fren / durma algılanınca sorar", "Söndürülüb: yalnız qəfil əyləc / dayanma olanda soruşur")
    fun wwEmpty(lang: Lang) = tl(
        lang,
        "Onaylı sokak yok. Tek yönlü bir sokakta ters giderken Ses+ ile onaylarsın; ana caddelerde onay kapalı.",
        "Təsdiqli küçə yoxdur. Birtərəfli küçədə əks istiqamətdə gedərkən Səs+ ilə təsdiqləyirsən; əsas küçələrdə təsdiq olmur.",
    )
    fun wwAddProblem(lang: Lang) = tl(lang, "Sorunlu geçiş ekle", "Problemli keçid əlavə et")
    fun wwAddClean(lang: Lang) = tl(lang, "Sorunsuz geçiş ekle", "Problemsiz keçid əlavə et")
    fun wwReset(lang: Lang) = tl(lang, "Sayaçları sıfırla", "Sayğacları sıfırla")
    fun wwUnapprove(lang: Lang) = tl(lang, "Onayı kaldır (ters yön kapalı)", "Təsdiqi götür (əks istiqamət bağlı)")

    /**
     * Ters yön sekmesi listesi (MAP.md 9.8.4): onaylı ∪ ww kaydı olan sokaklar; ham orana göre azalan (kayıt yok = −1), eşitlikte geçişe göre azalan.
     * Ad: Y8 `streets.names` önce, sonra `nameOf`.
     */
    fun wwRows(lang: Lang, k: RoadKnowledge, approved: Set<Long>, streetNames: Map<Long, String>, thresholdPct: Double): List<WwRowView> {
        val thr = thresholdPct / 100
        val ids = LinkedHashSet<Long>().apply { addAll(approved); addAll(k.ww.keys) }.toMutableList()
        fun ratio(id: Long) = k.ww[id]?.ratio ?: -1.0
        ids.sortWith { a, b -> ratio(b).compareTo(ratio(a)).takeIf { it != 0 } ?: (k.ww[b]?.passes ?: 0).compareTo(k.ww[a]?.passes ?: 0) }
        return ids.map { id ->
            val st = k.ww[id]
            val name = streetNames[id]?.takeIf { it.isNotEmpty() } ?: k.nameOf(id, lang)
            val passes = st?.passes ?: 0
            val r = st?.ratio ?: 0.0
            val probs = st?.problems ?: 0.0
            val probsText = if (probs == Math.rint(probs)) "${probs.roundToLong()}" else fixed1(probs)
            val avoided = passes >= 3 && r >= thr
            val isApproved = id in approved
            val tone = when {
                passes == 0 -> WwTone.NONE
                r >= thr -> WwTone.RED
                r >= thr / 2 -> WwTone.ORANGE
                else -> WwTone.GREEN
            }
            val status = when {
                !isApproved -> tl(lang, "onay kaldırılmış", "təsdiq götürülüb")
                avoided -> tl(lang, "kaçınılıyor", "yan keçilir")
                passes == 0 -> tl(lang, "veri yok", "məlumat yoxdur")
                else -> tl(lang, "kullanılıyor", "istifadə olunur")
            }
            WwRowView(
                id, name, tl(lang, "$passes geçiş · $probsText sorun · $status", "$passes keçid · $probsText problem · $status"),
                if (passes == 0) "—" else pct(r), tone, avoided, isApproved,
            )
        }
    }

    fun ghostInfoTitle(lang: Lang) = tl(lang, "Haritada var, gerçekte yok", "Xəritədə var, əslində yoxdur")
    fun ghostInfo(lang: Lang) = tl(
        lang,
        "İşaretlenen yol parçası rotaya hiçbir koşulda girmez (kapalı kapı, duvar, inşaat, haritadaki hata). Ekleme: haritada yola uzun bas → " +
            "\"Burada yol yok\", ya da rota sırasında alttaki \"Yol yok\" düğmesi.",
        "İşarələnən yol hissəsi marşruta heç vaxt düşmür (bağlı darvaza, divar, tikinti, xəritədəki səhv). Əlavə etmək: xəritədə yolun üstündə " +
            "barmağını saxla → \"Burada yol yoxdur\", və ya marşrut zamanı aşağıdakı \"Yol yoxdur\" düyməsi.",
    )

    /** "Sokağın tamamı" / "Bir parça" · gg.aa.yyyy (WEEM tek saat dilimi). */
    fun ghostRow(lang: Lang, m: GhostMark, zone: ZoneId): String =
        "${if (m.all) tl(lang, "Sokağın tamamı", "Bütün küçə") else tl(lang, "Bir parça", "Bir hissə")} · " +
            DateTimeFormatter.ofPattern("dd.MM.yyyy").format(Instant.ofEpochMilli(m.at).atZone(zone))
    fun ghostDelete(lang: Lang) = tl(lang, "Sil (yol varmış)", "Sil (yol varmış)")
    fun ghostClearAll(lang: Lang) = tl(lang, "Tümünü sil", "Hamısını sil")
    fun ghostClearConfirm(lang: Lang) = tl(lang, "Tüm \"yol yok\" işaretleri silinsin mi?", "Bütün \"yol yoxdur\" qeydləri silinsin?")
    fun cancel(lang: Lang) = tl(lang, "Vazgeç", "Ləğv et")
    fun delete() = "Sil"

    // ---------------------------------------------------------------- Ayarlar (Harita ayarları → Yollarım)

    fun settingsGroup() = "Yollarım"

    /** S26 ana anahtarı. */
    fun learningSwitch(lang: Lang) = tl(lang, "Yol kalitesi öğrenme", "Yol keyfiyyətini öyrənmə")
    fun learningSwitchSub(lang: Lang) = tl(
        lang,
        "Kapalıyken sürüşten öğrenme, ters yön soruları ve ses tuşları çalışmaz; girilen yol bilgileri rotada kullanılmaya devam eder",
        "Söndürüləndə sürüşdən öyrənmə, əks istiqamət sualları və səs düymələri işləmir; daxil edilən yol məlumatları marşrutda istifadə olunmağa davam edir",
    )
    fun volumeKeys(lang: Lang) = tl(
        lang,
        "Ses tuşları (Ses+ onay · Ses+ ×2 kalite · Ses− kötü nokta ±25 m · Ses− uzun: sil · Ses− ×2 aşırı eğimli)",
        "Səs düymələri (Səs+ təsdiq · Səs+ ×2 keyfiyyət · Səs− pis nöqtə ±25 m · Səs− uzun: sil · Səs− ×2 çox dik)",
    )
    fun volumeKeysSub(lang: Lang) = tl(lang, "Kapalıyken tuşlar normal ses ayarı yapar", "Söndürüləndə düymələr adi səsi tənzimləyir")
    fun viewRoads(lang: Lang) = tl(lang, "Yolları görüntüle", "Yollara bax")
    fun viewRoadsSub(lang: Lang) = tl(lang, "Kalite · ters yön · yol yok · bisiklet", "Keyfiyyət · əks istiqamət · yol yoxdur · velosiped")
}
