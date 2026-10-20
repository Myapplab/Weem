package com.kuryelog.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kuryelog.logic.core.Lang

/** Veri seti gün özeti (MAP.md 13.8; `DatasetPack.summary` alanları): platform sayıları, eşleşme, eksikler. Oranlar null = bilinmiyor. */
data class DatasetDayInfo(
    val day: String,
    val folder: String,
    val wolt: Int,
    val bolt: Int,
    val matched: Int = 0,
    val onlyDayLog: Int = 0,
    val onlyLive: Int = 0,
    val routeComputedRatio: Double? = null,
    val emptyFieldRatio: Double? = null,
    val scooterCoverageRatio: Double? = null,
    val collectionComplete: Boolean? = null,
    val missingManual: List<String> = emptyList(),
)

/** `:orders` `DatasetRuntime` çağrıları (kanal sahtesi yerine sahte uygulama testte); hata fırlatır. */
interface DatasetRuntime {
    suspend fun list(): List<DatasetDayInfo>
    suspend fun collect(day: String)
}

/**
 * Teklif veri seti ekranı (MAP.md 13.8): üstte açıklama (M-13-2), yenile (`dataset-refresh`, içerik açıklaması "Yenile"), vardiya gününü topla (`dataset-collect-shift`);
 * günler kapalı başlar (`dataset-day-<gün>`; başlık dokununca açılır, bağımsız); açık gün "Teklifler: Wolt N · Bolt M", eksikler; gün başına topla (`dataset-sync-<gün>`);
 * hata metinleri "Gün listesi okunamadı. Tekrar dene." / "İşlem tamamlanamadı." (başarılı yenile okuma hatasını temizler, toplama hatası liste okumasında kaybolmaz).
 * İskelet (A6.2b): gövde A6.9'da.
 */
@Composable
fun DatasetScreen(runtime: DatasetRuntime, lang: Lang, shiftDay: String?, modifier: Modifier = Modifier) {
    TODO("A6.9")
}
