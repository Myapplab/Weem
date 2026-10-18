package com.kuryelog.logic.economics

import org.json.JSONObject
import com.kuryelog.logic.core.*
import com.kuryelog.logic.orders.*
import java.util.Locale

// A4.2 — gün sonu raporu, net kazanç (N4) ve günlük hedef (N8); WEEM'de yeni (Rawstats'ta yok). Kural S8 (WEEM_PLANI.md Bölüm 8):
// net = brüt + bahşiş − filo komisyonu − enerji maliyeti (Wh × kWh fiyatı / şarj verimi). Brüt, bahşiş ve komisyon
// `:orders`'ın sipariş kayıtlarından ([TripRecord.earnings], [TripRecord.tip], [TripRecord.commissionPct]); enerji Wh'si A5.3'ün
// sipariş enerjisinden ([TripRecord.usedWh], `OrderFlow.scooterEnergy`) ve `economics-energy.jsonl`'in boşta dönemlerinden.
// Ölçülmemiş enerjinin maliyeti yok sayılır ve rapor bunu açıkça yazar; uydurulmuş değer yok.

/** Net ve hedef ayarları (`kuryelog` dosyası, VERI_SOZLESMESI Bölüm 5.1; üçü de WEEM'de yeni). */
object NetSettings {
    /** Float, ₼/kWh. Yok = enerji maliyeti hesaba katılmaz. */
    const val KWH_PRICE = "economics.kwhPrice"
    /** Float, %. Yok ya da aralık dışı = [DEFAULT_EFFICIENCY_PCT]. */
    const val CHARGE_EFFICIENCY = "economics.chargeEfficiencyPct"
    /** Float, ₼ (günlük net hedef). Yok ya da 0 = hedef yok. */
    const val DAILY_GOAL = "economics.dailyGoalAzn"

    const val DEFAULT_EFFICIENCY_PCT = 90.0
    const val MIN_EFFICIENCY_PCT = 50.0
    const val MAX_EFFICIENCY_PCT = 100.0

    data class Prices(val kwhPrice: Double?, val chargeEfficiencyPct: Double, val dailyGoal: Double?) {
        /** Şebekeden çekilen enerji başına maliyet çarpanı: ₼/Wh (fiyat / verim); fiyat yoksa null. */
        fun aznPerWh(): Double? = kwhPrice?.let { it / 1000.0 / (chargeEfficiencyPct / 100.0) }
    }

    private fun opt(p: KuryelogPrefs.Values, key: String): Double? =
        // Float → String → Double: 0.12f ikilik yakınlığı (0.11999999…) görünmesin.
        p.getFloat(key, Float.NaN).takeIf { it.isFinite() }?.toString()?.toDouble()

    fun read(p: KuryelogPrefs.Values): Prices {
        val price = opt(p, KWH_PRICE)?.takeIf { it >= 0.0 }
        val eff = opt(p, CHARGE_EFFICIENCY)?.takeIf { it in MIN_EFFICIENCY_PCT..MAX_EFFICIENCY_PCT } ?: DEFAULT_EFFICIENCY_PCT
        val goal = opt(p, DAILY_GOAL)?.takeIf { it > 0.0 }
        return Prices(price, eff, goal)
    }

    /** Ayar ekranının yazdığı değer: boş/geçersiz = null (anahtar silinir). Virgül ondalık olabilir. */
    fun parse(text: String, min: Double, max: Double): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max }
}

/** Enerji maliyetinin durumu: raporda neden bu sayı olduğunu yazmak için. */
enum class EnergyState {
    /** Hiç Wh ölçülmedi: maliyet yok sayıldı. */
    NOT_MEASURED,
    /** Wh ölçüldü ama kWh fiyatı girilmedi: maliyet yok sayıldı. */
    NO_PRICE,
    /** Maliyet net'ten düşüldü. */
    COSTED,
}

/** Bir günün N4/N8 sonucu. Para birimi ₼. */
data class DayReport(
    val date: String,
    val orders: Int,
    val gross: Double,
    val tips: Double,
    val commission: Double,
    /** Enerji maliyeti düşülmeden net (brüt + bahşiş − komisyon); `OrderHistory.Day.net` ile aynı. */
    val netBeforeEnergy: Double,
    /** Ölçülen toplam Wh: siparişlerin enerjisi + boşta dönemleri. */
    val energyWh: Double,
    val orderWh: Double,
    val idleWh: Double,
    /** Enerjisi ölçülen / tüm teslim edilen sipariş kayıtları. */
    val measuredTrips: Int,
    val deliveredTrips: Int,
    val energyState: EnergyState,
    /** Net'ten düşülen maliyet; [EnergyState.COSTED] değilse 0. */
    val energyCost: Double,
    val prices: NetSettings.Prices,
    /** Fiyatı okunamayan teslimler (brüt/net toplamına girmedi). */
    val unpriced: Int,
    /** Komisyon hiçbir teslimde girilmemiş ve brüt var: net = brüt + bahşiş. */
    val commissionMissing: Boolean,
) {
    val net get() = netBeforeEnergy - energyCost
    val goal get() = prices.dailyGoal
    /** Hedefe kalan (net üzerinden); hedef yoksa null, aşıldıysa 0. */
    val remaining get() = goal?.let { (it - net).coerceAtLeast(0.0) }
    val goalReached get() = goal?.let { net >= it } ?: false
    /** Hedefin yüzdesi (0–999); hedef yoksa null. */
    val goalPct get() = goal?.let { Math.round(net / it * 100.0).coerceIn(0, 999).toInt() }

    companion object {
        /** `economics-energy.jsonl` satırlarından boşta dönemlerinin Wh'si: dönem başına son satır, ölçülmemiş (null) ve negatif (şarj) sayılmaz. */
        fun idleWh(rows: List<JSONObject>): Double {
            val bySpell = LinkedHashMap<String, JSONObject>()
            for (r in rows) if (r.optString("segment") == "idle") r.optString("spellId").takeIf { it.isNotEmpty() }?.let { bySpell[it] = r }
            return bySpell.values.sumOf { r ->
                val e = r.optJSONObject("energy")
                if (e == null || e.isNull("wh")) 0.0 else e.optDouble("wh", 0.0).takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            }
        }

        /**
         * [day] bir günün siparişleri ([OrderHistory.Day]); [energyRows] aynı günün `economics-energy.jsonl` satırları.
         * Sipariş enerjisi ölçüt olarak [OrderHistory.energyOf] (D10: harici batarya çoğunlukla okunmadıysa o sipariş ölçülmemiş sayılır).
         */
        fun build(day: OrderHistory.Day, energyRows: List<JSONObject>, prices: NetSettings.Prices): DayReport {
            val delivered = day.delivered
            val priced = delivered.filter { it.earnings != null }
            val gross = priced.sumOf { it.earnings ?: 0.0 }
            val commission = priced.sumOf { (it.earnings ?: 0.0) * it.commissionPct / 100.0 }
            val tips = priced.sumOf { it.tip }
            val orderWh = delivered.sumOf { OrderHistory.energyOf(it)?.coerceAtLeast(0.0) ?: 0.0 }
            val measured = delivered.count { OrderHistory.energyOf(it) != null }
            val idle = idleWh(energyRows)
            val wh = orderWh + idle
            val perWh = prices.aznPerWh()
            val state = when {
                wh <= 0.0 && measured == 0 -> EnergyState.NOT_MEASURED
                perWh == null -> EnergyState.NO_PRICE
                else -> EnergyState.COSTED
            }
            return DayReport(
                day.date, day.orders, gross, tips, commission, gross + tips - commission, wh, orderWh, idle, measured, delivered.size,
                state, if (state == EnergyState.COSTED) wh * (perWh ?: 0.0) else 0.0, prices, day.unpriced,
                commissionMissing = gross > 0.0 && priced.none { it.commissionPct > 0.0 },
            )
        }
    }
}
