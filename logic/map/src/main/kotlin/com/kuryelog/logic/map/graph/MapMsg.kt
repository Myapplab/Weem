package com.kuryelog.logic.map.graph

import com.kuryelog.logic.core.Bilingual

/**
 * Harita verisi ilerleme, günlük ve hata metinleri (MAP.md 2.7–2.9, 2.15). YolGo bunları Türkçe üretip `trMsg` alt dize
 * değişimiyle çevirirdi (AZ karışık çıkardı); WEEM her mesajı kodlu tutar, iki dil burada bütün hâlinde. TR metinler harfiyen aynı.
 */
sealed class MapMsg : Bilingual {
    private class Fixed(override val tr: String, override val az: String) : MapMsg()

    /** Ayna adıyla kurulan Overpass günlüğü ve son hata. */
    class Mirror(host: String, trTail: String, azTail: String) : MapMsg() {
        override val tr = "$host$trTail"
        override val az = "$host$azTail"
    }

    class Text(override val tr: String, override val az: String) : MapMsg()

    companion object {
        val STARTING: MapMsg = Fixed("Başlıyor…", "Başlayır…")
        val ELEVATION: MapMsg = Fixed("Yükseklik verisi (SRTM)", "Hündürlük məlumatı (SRTM)")
        val PREBUILT_SEARCH: MapMsg = Fixed("Hazır yol verisi aranıyor", "Hazır yol məlumatı axtarılır")
        val SAVING: MapMsg = Fixed("Kaydediliyor", "Yadda saxlanılır")
        val DONE: MapMsg = Fixed("Hazır", "Hazırdır")
        val UNPACKING: MapMsg = Fixed("Gömülü harita açılıyor", "Daxili xəritə açılır")
        val NO_ROADS: MapMsg = Fixed("Bu bölgede yol bulunamadı", "Bu ərazidə yol tapılmadı")
        val OLD_FORMAT: MapMsg = Fixed("Harita dosyası eski sürüm", "Xəritə faylı köhnə versiyadır")

        fun demDownloading(name: String): MapMsg = Text("Yükseklik: $name indiriliyor", "Hündürlük: $name yüklənir")
        fun demFailed(name: String): MapMsg =
            Text("Yükseklik: $name indirilemedi, düz kabul edilecek", "Hündürlük: $name yüklənmədi, düz qəbul ediləcək")

        fun prebuiltPart(i: Int, n: Int): MapMsg =
            Text("Yol verisi $i/$n (hazır veri sunucusu)", "Yol məlumatı $i/$n (hazır məlumat serveri)")

        fun tile(i: Int, n: Int): MapMsg = Text("Yol verisi $i/$n", "Yol məlumatı $i/$n")
        fun tileLog(i: Int, n: Int, m: MapMsg): MapMsg = Text("Yol verisi $i/$n · ${m.tr}", "Yol məlumatı $i/$n · ${m.az}")
        fun buildingGraph(ways: Int): MapMsg = Text("Yol ağı kuruluyor ($ways yol)", "Yol şəbəkəsi qurulur ($ways yol)")

        fun incomplete(host: String): MapMsg =
            Mirror(host, " eksik veri verdi, başka sunucu deneniyor", " natamam məlumat verdi, başqa server yoxlanılır")

        fun busy(host: String, code: Int): MapMsg =
            Mirror(host, " meşgul ($code), başka sunucu deneniyor", " məşğuldur ($code), başqa server yoxlanılır")

        fun noAnswer(host: String): MapMsg = Mirror(host, " yanıt vermedi, başka sunucu deneniyor", " cavab vermədi, başqa server yoxlanılır")
        fun connError(host: String): MapMsg = Mirror(host, " bağlantı hatası, başka sunucu deneniyor", " bağlantı xətası, başqa server yoxlanılır")
        fun allBusy(sec: Int): MapMsg = Text("Tüm sunucular meşgul, $sec sn sonra tekrar", "Bütün serverlər məşğuldur, $sec san sonra yenidən")

        /** Son ayna hatası; [problem] Overpass sorun metni ya da istisna (çevrilmez, YolGo'daki gibi). */
        fun mirrorProblem(host: String, problem: String): MapMsg = Mirror(host, ": $problem", ": $problem")
        fun mirrorHttp(host: String, code: Int): MapMsg = Mirror(host, ": HTTP $code", ": HTTP $code")
        fun mirrorNoAnswer(host: String): MapMsg = Mirror(host, ": yanıt yok", ": cavab yoxdur")

        fun downloadFailed(last: MapMsg?): MapMsg =
            Text("Yol verisi indirilemedi (${last?.tr ?: "null"})", "Yol məlumatı yüklənmədi (${last?.az ?: "null"})")

        /** İndirme hatasına eklenen satır: `0 < saved < total` iken. */
        fun partsSaved(error: MapMsg, saved: Int, total: Int): MapMsg = Text(
            "${error.tr}\n$saved/$total parça kaydedildi. Tekrar \"İndir\"e bas, kaldığı yerden devam eder.",
            "${error.az}\n$saved/$total hissə saxlanıldı. Yenidən \"Yüklə\" bas, qaldığı yerdən davam edəcək.",
        )

        fun readFailed(e: MapMsg): MapMsg =
            Text("Harita dosyası okunamadı, yeniden indir (${e.tr})", "Xəritə faylı oxunmadı, yenidən yüklə (${e.az})")

        fun updateFailed(e: MapMsg): MapMsg = Text("Güncelleme başarısız: ${e.tr}", "Yeniləmə alınmadı: ${e.az}")

        /** Beklenmeyen istisna: metni çevrilmeden (Dart `'$e'` gibi; `Exception: ` öneki yok). */
        fun of(e: Throwable): MapMsg = when (e) {
            is MapDataException -> e.msg
            is FormatException -> if (e.message == OLD_FORMAT.tr) OLD_FORMAT else raw(e.message ?: e.toString())
            else -> raw(e.message ?: e.toString())
        }

        fun raw(s: String): MapMsg = Text(s, s)
    }
}

/** Kodlu harita verisi hatası (ekranda dile göre). */
class MapDataException(val msg: MapMsg) : Exception(msg.tr)
