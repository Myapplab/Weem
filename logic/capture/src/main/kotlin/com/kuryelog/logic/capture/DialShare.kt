package com.kuryelog.logic.capture

/**
 * S44 (kullanıcı: "bir telefonda telefon uygulaması açılırsa numara öteki telefonda kopyalansın"): 2 telefon modunda Provider'da (Wolt/Bolt
 * telefonu, vardiyada ekran okunurken) arama uygulaması bir numarayla açılınca (Wolt/Bolt "Ara" düğmesi `tel:` açar) numara Receiver'a gider,
 * orada panoya kopyalanır. Okuma yalnız arama uygulamasının penceresi öne geldikten sonraki [windowMs] içinde ve yalnız numara alanından
 * (`…:id/digits` ya da düzenlenebilir alan; çağıran verir) yapılır: arka plandaki arama geçmişi ve pencere açıldıktan sonra elle yazılan
 * numara gitmez. Aynı numara [repeatMs] içinde bir kez. Numara hiçbir yere yazılmaz (kayıt, log, hata iletisi). Saf; süreler ms.
 */
class DialShare(private val windowMs: Long = WINDOW_MS, private val repeatMs: Long = REPEAT_MS) {
    private var openedAt = Long.MIN_VALUE / 2
    private var last: String? = null
    private var lastAt = Long.MIN_VALUE / 2

    /** Arama uygulamasının penceresi öne geldi (erişilebilirlik `TYPE_WINDOW_STATE_CHANGED`). */
    fun opened(now: Long) { openedAt = now }

    /** Pencere yeni açıldı: numara alanı okunabilir. */
    fun reading(now: Long): Boolean = now - openedAt in 0..windowMs

    /** Numara alanlarının metni; paylaşılacak numara ya da null. Bulununca pencere kapanır (aynı açılışta ikinci numara yok). */
    fun read(fields: List<String>, now: Long): String? {
        if (!reading(now)) return null
        val n = fields.firstNotNullOfOrNull(::number) ?: return null
        openedAt = Long.MIN_VALUE / 2
        if (n == last && now - lastAt < repeatMs) return null
        last = n; lastAt = now
        return n
    }

    companion object {
        const val WINDOW_MS = 3_000L
        /** İçerik olaylarından sonra okuma bu kadar bekler (alan dolsun, olaylar birleşsin). */
        const val READ_DELAY_MS = 250L
        /** Alan boşsa pencere süresince bu aralıkla yeniden okunur (içerik olayı gelmese de). */
        const val RETRY_MS = 400L
        const val REPEAT_MS = 30_000L
        const val MIN_DIGITS = 7
        const val MAX_DIGITS = 20

        /**
         * Numara alanının metninden aranacak dizi: boşluk, tire, nokta, parantez ve yön işaretleri atılır; `+` yalnız başta, `,` `;` (bekleme,
         * dahili numara), `*` `#` korunur. Harf (ipucu metni, "Kişilere ekle") ya da ana numarada [MIN_DIGITS]–[MAX_DIGITS] dışı rakam: null.
         */
        fun number(raw: String): String? {
            val s = raw.filterNot { it.isWhitespace() || it in "-.()/" || it in '‎'..'‏' || it in '‪'..'‮' || it in '⁦'..'⁩' }
            if (s.isEmpty()) return null
            for ((i, c) in s.withIndex()) if (!(c.isAsciiDigit() || c in "*#,;" || c == '+' && i == 0)) return null
            val main = s.substringBefore(',').substringBefore(';').count { it.isAsciiDigit() }
            if (main !in MIN_DIGITS..MAX_DIGITS) return null
            return s
        }

        private fun Char.isAsciiDigit() = this in '0'..'9'
    }
}
